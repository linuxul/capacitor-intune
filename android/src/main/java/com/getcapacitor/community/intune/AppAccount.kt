/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.getcapacitor.community.intune

import android.content.SharedPreferences

/**
 * Represents an account that is signed in to the app.
 *
 * MSAL does not promise a tenant ID, so it may be null.
 *
 * @property accountId the account ID (ObjectID).
 * @property aadid the account ID.
 * @property tenantID the tenant ID.
 * @property authority the Authority used to sign in the account.
 */
public class AppAccount(
    public val accountId: String,
    @get:JvmName("getAADID") public val aadid: String,
    public val tenantID: String?,
    public val authority: String
) {
    /**
     * The Account should save itself to the provided SharedPreferences object.
     *
     * @param sharedPref
     * the preferences where the account should be written.
     */
    public fun saveToSettings(sharedPref: SharedPreferences) {
        sharedPref
            .edit()
            .putString(ACCOUNT_ID_KEY, accountId)
            .putString(AADID_KEY, aadid)
            .putString(TENANTID_KEY, tenantID)
            .putString(AUTHORITY_KEY, authority)
            .apply()
    }

    public companion object {
        private const val ACCOUNT_ID_KEY = "mamsampleappaccount.accountid"
        private const val AADID_KEY = "mamsampleappaccount.aadid"
        private const val TENANTID_KEY = "mamsampleappaccount.tenantid"
        private const val AUTHORITY_KEY = "mamsampleappaccount.authority"

        /**
         * Reconstitute the account object from the provided settings, where it was
         * previously saved.
         *
         * @param sharedPref
         * the preferences.
         *
         * @return the reconstituted account object, or null if insufficient data was
         * found in the settings.
         */
        @JvmStatic
        public fun readFromSettings(sharedPref: SharedPreferences): AppAccount? {
            val accountId = sharedPref.getString(ACCOUNT_ID_KEY, null) ?: return null
            val aadid = sharedPref.getString(AADID_KEY, null) ?: return null
            val tenantid = sharedPref.getString(TENANTID_KEY, null) ?: return null
            val authority = sharedPref.getString(AUTHORITY_KEY, null) ?: return null

            return AppAccount(accountId, aadid, tenantid, authority)
        }

        /**
         * Clear the saved account data from the provided settings object.
         *
         * @param sharedPref
         * the settings from which the account data should be cleared.
         */
        @JvmStatic
        public fun clearFromSettings(sharedPref: SharedPreferences) {
            sharedPref
                .edit()
                .remove(ACCOUNT_ID_KEY)
                .remove(AADID_KEY)
                .remove(TENANTID_KEY)
                .remove(AUTHORITY_KEY)
                .apply()
        }
    }
}
