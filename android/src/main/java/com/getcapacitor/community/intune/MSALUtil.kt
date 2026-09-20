/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.getcapacitor.community.intune

import android.app.Activity
import android.content.Context
import androidx.annotation.WorkerThread
import com.microsoft.identity.client.AcquireTokenParameters
import com.microsoft.identity.client.AcquireTokenSilentParameters
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAccount
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IMultipleAccountPublicClientApplication
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.Logger as MsalLogger
import com.microsoft.identity.client.Prompt
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.identity.client.exception.MsalUiRequiredException
import java.util.logging.Logger

/**
 * A utility class for methods required by MSAL.
 */
public object MSALUtil {
    private val LOGGER = Logger.getLogger(MSALUtil::class.java.name)

    private var msalClientApplication: IPublicClientApplication? = null

    /**
     * Acquire a token for the requested scopes.  Will be interactive.
     *
     * @param fromActivity
     * the Activity from which the auth request is made.
     * @param scopes
     * Scopes for the requested token.
     * @param loginHint
     * a prompt for the login dialog, can be null if unused.
     * @param callback
     * callback to receive the result of the auth attempt.
     *
     * @throws MsalException
     * MSAL error occurred.
     * @throws InterruptedException
     * Thread was interrupted.
     */
    @JvmStatic
    @WorkerThread
    @Throws(MsalException::class, InterruptedException::class)
    public fun acquireToken(
        fromActivity: Activity,
        scopes: Array<String>,
        loginHint: String?,
        forcePrompt: Boolean,
        callback: AuthenticationCallback
    ) {
        val application = initializeMsalClientApplication(fromActivity.applicationContext)

        val paramsBuilder =
            AcquireTokenParameters
                .Builder()
                .withScopes(scopes.asList())
                .withCallback(callback)
                .startAuthorizationFromActivity(fromActivity)

        if (!loginHint.isNullOrEmpty()) {
            paramsBuilder.withLoginHint(loginHint)
        }

        paramsBuilder.withPrompt(
            when {
                forcePrompt -> Prompt.LOGIN
                !loginHint.isNullOrEmpty() -> Prompt.WHEN_REQUIRED
                else -> Prompt.SELECT_ACCOUNT
            }
        )

        application.acquireToken(paramsBuilder.build())
    }

    /**
     * Acquire a token for the requested scopes.  Will not be interactive.
     *
     * @param appContext
     * A Context used to initialize the MSAL context, if needed.
     * @param aadId
     * Id of the user.
     * @param scopes
     * Scopes for the requested token.
     * @param callback
     * callback to receive the result of the auth attempt.
     * @param forceRefresh
     * force refreshing the token.
     *
     * @throws MsalException
     * MSAL error occurred.
     * @throws InterruptedException
     * Thread was interrupted.
     */
    @JvmStatic
    @WorkerThread
    @Throws(MsalException::class, InterruptedException::class)
    public fun acquireTokenSilent(
        appContext: Context,
        aadId: String?,
        scopes: Array<String>,
        callback: AuthenticationCallback,
        forceRefresh: Boolean
    ) {
        val application = initializeMsalClientApplication(appContext.applicationContext)

        val account = getAccount(aadId)
        if (account == null) {
            LOGGER.severe("Failed to acquire token: no account found for $aadId")
            callback.onError(MsalUiRequiredException(MsalUiRequiredException.NO_ACCOUNT_FOUND, "no account found for $aadId"))
            return
        }

        val params =
            AcquireTokenSilentParameters
                .Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(scopes.asList())
                .withCallback(callback)
                .forceRefresh(forceRefresh)
                .build()

        application.acquireTokenSilentAsync(params)
    }

    /**
     * Acquire a token silently for the given resource and user.  This is synchronous.
     *
     * @param appContext
     * the application context.
     * @param aadId
     * Id of the user.
     * @param scopes
     * Scopes to request the token for.
     *
     * @return the authentication result, or null if it fails.
     *
     * @throws MsalException
     * MSAL error occurred.
     * @throws InterruptedException
     * Thread was interrupted.
     */
    @JvmStatic
    @WorkerThread
    @Throws(MsalException::class, InterruptedException::class)
    public fun acquireTokenSilentSync(appContext: Context, aadId: String, scopes: Array<String>): IAuthenticationResult? {
        val application = initializeMsalClientApplication(appContext)
        val account = getAccount(aadId)
        if (account == null) {
            LOGGER.severe("Failed to acquire token: no account found for $aadId")
            throw MsalUiRequiredException(MsalUiRequiredException.NO_ACCOUNT_FOUND, "no account found for $aadId")
        }

        val params =
            AcquireTokenSilentParameters
                .Builder()
                .forAccount(account)
                .fromAuthority(account.authority)
                .withScopes(scopes.asList())
                .build()

        return application.acquireTokenSilent(params)
    }

    /**
     * Sign out the given account from MSAL.
     *
     * @param appContext
     * the application context.
     * @param aadId
     * Id of the user.
     *
     * @throws MsalException
     * MSAL error occurred.
     * @throws InterruptedException
     * Thread was interrupted.
     */
    @JvmStatic
    @Throws(MsalException::class, InterruptedException::class)
    public fun signOutAccount(appContext: Context, aadId: String) {
        val application = initializeMsalClientApplication(appContext)
        val account = getAccount(aadId)

        if (account == null) {
            LOGGER.warning("Failed to sign out account: No account found for $aadId")
            return
        }

        if (application is IMultipleAccountPublicClientApplication) {
            application.removeAccount(account)
        } else {
            (application as ISingleAccountPublicClientApplication).signOut()
        }
    }

    @Throws(InterruptedException::class, MsalException::class)
    private fun getAccount(aadId: String?): IAccount? {
        val application = msalClientApplication
        if (application is IMultipleAccountPublicClientApplication) {
            // MSAL requires an identifier here. Without one there is no account to find, which is also what the
            // single account branch below answers.
            return aadId?.let { application.getAccount(it) }
        }

        // make sure this is the correct user
        return (application as ISingleAccountPublicClientApplication?)
            ?.currentAccount
            ?.currentAccount
            ?.takeIf { it.id == aadId }
    }

    @Synchronized
    @Throws(MsalException::class, InterruptedException::class)
    private fun initializeMsalClientApplication(appContext: Context): IPublicClientApplication {
        msalClientApplication?.let { return it }

        val msalLogger = MsalLogger.getInstance()
        msalLogger.setEnableLogcatLog(true)
        msalLogger.setLogLevel(MsalLogger.LogLevel.VERBOSE)
        msalLogger.setEnablePII(true)

        val authConfig = appContext.resources.getIdentifier("auth_config", "raw", appContext.packageName)
        return PublicClientApplication.create(appContext, authConfig).also { msalClientApplication = it }
    }
}
