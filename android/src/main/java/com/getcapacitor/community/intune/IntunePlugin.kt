package com.getcapacitor.community.intune

import android.content.Context
import android.widget.Toast
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Logger
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginException
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalIntuneAppProtectionPolicyRequiredException
import com.microsoft.identity.client.exception.MsalUserCancelException
import com.microsoft.intune.mam.client.MAMSDKVersion
import com.microsoft.intune.mam.client.app.MAMComponents
import com.microsoft.intune.mam.client.identity.MAMPolicyManager
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.microsoft.intune.mam.policy.appconfig.MAMAppConfig
import com.microsoft.intune.mam.policy.appconfig.MAMAppConfigManager

@CapacitorPlugin(name = "IntuneMAM")
public class IntunePlugin : Plugin() {
    private var userAccount: AppAccount? = null

    private var registeredEnrollmentManager: MAMEnrollmentManager? = null

    // MAMComponents.get returns null while the MAM SDK is not wired into the app. Using the manager in that
    // state was a NullPointerException in the Java implementation and still is.
    private val enrollmentManager: MAMEnrollmentManager
        get() = registeredEnrollmentManager!!

    private var lastEnrollCall: PluginCall? = null

    override fun load() {
        super.load()
        registeredEnrollmentManager = MAMComponents.get(MAMEnrollmentManager::class.java)

        val prefs = context.getSharedPreferences(SETTINGS_PATH, Context.MODE_PRIVATE)
        userAccount = AppAccount.readFromSettings(prefs)
    }

    private fun acquireToken(call: PluginCall, interactive: Boolean) {
        lastEnrollCall = call

        val accountId = call.getString("accountId")

        val forcePrompt = call.getBoolean("forcePrompt", false) ?: false
        val forceRefresh = call.getBoolean("forceRefresh", false) ?: false

        val scopes: Array<String> =
            try {
                checkNotNull(call.getArray("scopes")).toList<Any?>().map { it as String }.toTypedArray()
            } catch (ex: Exception) {
                throw PluginException("Must provide scopes list", cause = ex)
            }

        // initiate the MSAL authentication on a background thread
        Thread {
            Logger.info("Starting " + (if (interactive) "interactive" else "silent") + " auth")

            try {
                if (interactive) {
                    val loginHint = if (forcePrompt) null else userAccount?.accountId
                    MSALUtil.acquireToken(activity, scopes, loginHint, forcePrompt, AuthCallback())
                } else {
                    MSALUtil.acquireTokenSilent(activity, accountId, scopes, AuthCallback(), forceRefresh)
                }
            } catch (e: MsalException) {
                reportAuthenticationException(e)
            } catch (e: InterruptedException) {
                reportAuthenticationException(e)
            }
        }.start()
    }

    private fun reportAuthenticationException(e: Exception) {
        Logger.error("Authentication exception occurred", e)
        showMessage("Authentication exception occurred - check logcat for more details.")
    }

    @PluginMethod
    public fun acquireToken(call: PluginCall) {
        acquireToken(call, true)
    }

    @PluginMethod
    public fun acquireTokenSilent(call: PluginCall) {
        acquireToken(call, false)
    }

    @PluginMethod
    public fun registerAndEnrollAccount(call: PluginCall) {
        val account = userAccount ?: throw PluginException("No user account. Call acquireToken first")
        enrollmentManager.registerAccountForMAM(account.accountId, account.aadid, account.tenantID, account.authority)
        call.resolve()
    }

    @PluginMethod
    public fun loginAndEnrollAccount(call: PluginCall) {
        lastEnrollCall = call

        val forcePrompt = call.getBoolean("forcePrompt", false) ?: false

        // initiate the MSAL authentication on a background thread
        Thread {
            Logger.info("Starting interactive auth")

            try {
                MSALUtil.acquireToken(activity, MSAL_SCOPES, userAccount?.accountId, forcePrompt, AuthCallback())
            } catch (e: MsalException) {
                reportAuthenticationException(e)
            } catch (e: InterruptedException) {
                reportAuthenticationException(e)
            }
        }.start()
    }

    @PluginMethod
    public fun enrolledAccount(call: PluginCall) {
        val data = JSObject()
        data.put("accountId", userAccount?.accountId ?: "")
        call.resolve(data)
    }

    @PluginMethod
    public fun deRegisterAndUnenrollAccount(call: PluginCall) {
        // Initiate an MSAL sign out on a background thread.
        val account = userAccount

        Thread {
            // Without a signed in account this was a NullPointerException on the worker thread in the Java
            // implementation and still is.
            val effectiveAccount = account!!
            var didSignout = false
            try {
                MSALUtil.signOutAccount(context, effectiveAccount.aadid)
                didSignout = true
            } catch (e: MsalException) {
                rejectSignOut(call, effectiveAccount, e)
            } catch (e: InterruptedException) {
                rejectSignOut(call, effectiveAccount, e)
            }

            enrollmentManager.unregisterAccountForMAM(effectiveAccount.accountId)

            val prefs = context.getSharedPreferences(SETTINGS_PATH, Context.MODE_PRIVATE)
            AppAccount.clearFromSettings(prefs)

            userAccount = null

            if (didSignout) {
                call.resolve()
            }
        }.start()
    }

    @PluginMethod
    public fun logoutOfAccount(call: PluginCall) {
        val effectiveAccount = userAccount

        if (effectiveAccount == null) {
            call.resolve()
            return
        }

        Thread {
            try {
                MSALUtil.signOutAccount(context, effectiveAccount.aadid)
                call.resolve()
            } catch (e: MsalException) {
                rejectSignOut(call, effectiveAccount, e)
            } catch (e: InterruptedException) {
                rejectSignOut(call, effectiveAccount, e)
            }
            enrollmentManager.unregisterAccountForMAM(effectiveAccount.accountId)
            userAccount = null
        }.start()
    }

    private fun rejectSignOut(call: PluginCall, account: AppAccount, e: Exception) {
        call.reject("Unable to log user out", ex = e)
        Logger.error("Failed to sign out user " + account.aadid, e)
    }

    @PluginMethod
    public fun appConfig(call: PluginCall) {
        val accountId = call.getString("accountId") ?: throw PluginException("No accountId provided")

        val data = JSObject()
        data.put("fullData", JSArray(appConfigFor(accountId).fullData))
        call.resolve(data)
    }

    @PluginMethod
    public fun groupName(call: PluginCall) {
        val accountId = call.getString("accountId") ?: throw PluginException("No accountId provided")

        val data = appConfigFor(accountId)

        val groupNameKey = "GroupName"

        val groupName =
            if (!data.hasConflict(groupNameKey)) {
                data.getStringForKey(groupNameKey, MAMAppConfig.StringQueryType.Any)
            } else {
                data.getStringForKey(groupNameKey, MAMAppConfig.StringQueryType.Max)
            }

        call.resolve(JSObject().put("value", groupName))
    }

    // As with the enrollment manager, a missing MAMAppConfigManager was a NullPointerException in Java.
    private fun appConfigFor(accountId: String): MAMAppConfig = MAMComponents.get(MAMAppConfigManager::class.java)!!.getAppConfig(accountId)

    @PluginMethod
    @Suppress("unused")
    public fun getPolicy(call: PluginCall) {
        val policy = MAMPolicyManager.getPolicy(activity)

        val data = JSObject()
        data.put("contactSyncAllowed", policy.isContactSyncAllowed)
        data.put("pinRequired", policy.isPinRequired)
        data.put("managedBrowserRequired", policy.isManagedBrowserRequired)
        data.put("screenCaptureAllowed", policy.isScreenCaptureAllowed)
        call.resolve(data)
    }

    @PluginMethod
    public fun sdkVersion(call: PluginCall) {
        val data = JSObject()
        data.put("version", "${MAMSDKVersion.VER_MAJOR}.${MAMSDKVersion.VER_MINOR}.${MAMSDKVersion.VER_PATCH}")
        call.resolve(data)
    }

    @PluginMethod
    public fun displayDiagnosticConsole(call: PluginCall) {
        MAMPolicyManager.showDiagnostics(context)
        call.resolve()
    }

    private fun showMessage(message: String) {
        activity.runOnUiThread { Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }

    private inner class AuthCallback : AuthenticationCallback {
        override fun onError(exc: MsalException) {
            Logger.error("authentication failed", exc)

            lastEnrollCall?.reject("Authentication error", ex = exc)
            lastEnrollCall = null

            when (exc) {
                is MsalIntuneAppProtectionPolicyRequiredException -> {
                    // Note: An app that has enabled APP CA with Policy Assurance would need to pass these values to `remediateCompliance`.
                    // For more information, see https://docs.microsoft.com/en-us/mem/intune/developer/app-sdk-android#app-ca-with-policy-assurance
                    val accountId: String? = exc.accountUpn
                    val aadid: String? = exc.accountUserId
                    val tenantId: String? = exc.tenantId
                    val authorityURL: String? = exc.authorityUrl

                    // The user cannot be considered "signed in" at this point, so don't save it to the settings.
                    // MSAL does not annotate these getters; an account without an ID or authority is of no use to the calls above.
                    if (accountId != null && aadid != null && authorityURL != null) {
                        userAccount = AppAccount(accountId, aadid, tenantId, authorityURL)
                    }

                    showMessage("Intune App Protection Policy required.")

                    Logger.info("MsalIntuneAppProtectionPolicyRequiredException received.")
                    Logger.info(
                        "Data from broker: Account ID: $accountId; AAD ID: $aadid; Tenant ID: $tenantId; Authority: $authorityURL"
                    )
                }

                is MsalUserCancelException -> showMessage("User cancelled sign-in request")

                else -> showMessage("Exception occurred - check logcat")
            }
        }

        override fun onSuccess(result: IAuthenticationResult) {
            val account = result.account

            val accountId = account.id
            val aadId = account.id
            val tenantId: String? = account.tenantId
            val authorityURL = account.authority
            val token = result.accessToken
            val idToken = account.idToken

            Logger.info("Authentication succeeded for user $accountId")

            // Save the user account in the settings, since the user is now "signed in".
            val signedIn = AppAccount(accountId, aadId, tenantId, authorityURL)
            userAccount = signedIn

            val prefs = context.getSharedPreferences(SETTINGS_PATH, Context.MODE_PRIVATE)
            signedIn.saveToSettings(prefs)

            // Register the account for MAM.
            enrollmentManager.registerAccountForMAM(accountId, aadId, tenantId, authorityURL)

            lastEnrollCall?.let { call ->
                val data = JSObject()
                data.put("accountId", accountId)
                data.put("accessToken", token)
                data.put("idToken", idToken)
                data.put("accountIdentifier", accountId)
                call.resolve(data)
                lastEnrollCall = null
            }
        }

        override fun onCancel() {
            showMessage("User cancelled auth attempt")
        }
    }

    public companion object {
        private const val SETTINGS_PATH = "com.outsystems.intunedemo"

        @JvmField
        public val MSAL_SCOPES: Array<String> = arrayOf("https://graph.microsoft.com/User.Read")
    }
}
