package com.getcapacitor.community.intune

import android.app.Application
import android.util.Log
import com.microsoft.intune.mam.client.app.MAMComponents
import com.microsoft.intune.mam.client.notification.MAMNotificationReceiverRegistry
import com.microsoft.intune.mam.policy.MAMEnrollmentManager
import com.microsoft.intune.mam.policy.notification.MAMEnrollmentNotification
import com.microsoft.intune.mam.policy.notification.MAMNotificationType

/**
 * Specifies what happens when the app is launched and terminated.
 *
 * Registers an authentication callback for MAM.
 */
public open class IntuneApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Registers a MAMAuthenticationCallback, which will try to acquire access tokens for MAM.
        // This is necessary for proper MAM integration.
        // MAMComponents.get returns null when the SDK is not wired in; that was a NullPointerException in Java too.
        val mgr = MAMComponents.get(MAMEnrollmentManager::class.java)!!
        mgr.registerAuthenticationCallback(AuthenticationCallback(applicationContext))

        /* This section shows how to register a MAMNotificationReceiver, so you can perform custom
         * actions based on MAM enrollment notifications.
         * More information is available here:
         * https://docs.microsoft.com/en-us/intune/app-sdk-android#types-of-notifications */
        MAMComponents
            .get(MAMNotificationReceiverRegistry::class.java)!!
            .registerReceiver(
                { notification ->
                    if (notification is MAMEnrollmentNotification) {
                        Log.d("Enrollment Receiver", notification.enrollmentResult.name)
                    } else {
                        Log.d("Enrollment Receiver", "Unexpected notification type received")
                    }
                    true
                },
                MAMNotificationType.MAM_ENROLLMENT_RESULT
            )
    }
}
