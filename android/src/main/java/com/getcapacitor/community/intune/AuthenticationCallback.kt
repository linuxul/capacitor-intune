/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.getcapacitor.community.intune

import android.content.Context
import com.microsoft.identity.client.exception.MsalException
import com.microsoft.intune.mam.policy.MAMServiceAuthenticationCallback
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Implementation of the required callback for MAM integration.
 */
public class AuthenticationCallback(context: Context) : MAMServiceAuthenticationCallback {
    private val context: Context = context.applicationContext

    override fun acquireToken(accountId: String, aadId: String, resourceId: String): String? {
        try {
            // Create the MSAL scopes by using the default scope of the passed in resource id.
            val scopes = arrayOf("$resourceId/.default")
            val result = MSALUtil.acquireTokenSilentSync(context, aadId, scopes)
            if (result != null) return result.accessToken
        } catch (e: MsalException) {
            LOGGER.log(Level.SEVERE, "Failed to get token for MAM Service", e)
            return null
        } catch (e: InterruptedException) {
            LOGGER.log(Level.SEVERE, "Failed to get token for MAM Service", e)
            return null
        }

        LOGGER.warning("Failed to get token for MAM Service - no result from MSAL")
        return null
    }

    private companion object {
        private val LOGGER = Logger.getLogger(AuthenticationCallback::class.java.name)
    }
}
