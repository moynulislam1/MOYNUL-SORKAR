package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deviceSessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "family_apps_session"
)

data class PairedDeviceInfo(
    val pairedUid: String = "",
    val pairedEmail: String = "",
    val pairedPhone: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val appLockEnabled: Boolean = false,
    val pendingInviteCode: String = ""
)

class DeviceSessionStore(private val context: Context) {
    private object Keys {
        val PAIRED_UID = stringPreferencesKey("paired_uid")
        val PAIRED_EMAIL = stringPreferencesKey("paired_email")
        val PAIRED_PHONE = stringPreferencesKey("paired_phone")
        val PASSWORD_HASH = stringPreferencesKey("password_hash")
        val PASSWORD_SALT = stringPreferencesKey("password_salt")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val PENDING_INVITE_CODE = stringPreferencesKey("pending_invite_code")
    }

    val pairedDeviceFlow: Flow<PairedDeviceInfo> = context.deviceSessionDataStore.data.map { prefs ->
        PairedDeviceInfo(
            pairedUid = prefs[Keys.PAIRED_UID].orEmpty(),
            pairedEmail = prefs[Keys.PAIRED_EMAIL].orEmpty(),
            pairedPhone = prefs[Keys.PAIRED_PHONE].orEmpty(),
            passwordHash = prefs[Keys.PASSWORD_HASH].orEmpty(),
            passwordSalt = prefs[Keys.PASSWORD_SALT].orEmpty(),
            appLockEnabled = prefs[Keys.APP_LOCK_ENABLED] ?: false,
            pendingInviteCode = prefs[Keys.PENDING_INVITE_CODE].orEmpty()
        )
    }

    suspend fun getSnapshot(): PairedDeviceInfo = pairedDeviceFlow.first()

    suspend fun savePairedCredentials(
        uid: String,
        email: String,
        phone: String,
        passwordHash: String,
        passwordSalt: String,
        appLockEnabled: Boolean
    ) {
        context.deviceSessionDataStore.edit { prefs ->
            prefs[Keys.PAIRED_UID] = uid
            prefs[Keys.PAIRED_EMAIL] = email
            prefs[Keys.PAIRED_PHONE] = phone
            prefs[Keys.PASSWORD_HASH] = passwordHash
            prefs[Keys.PASSWORD_SALT] = passwordSalt
            prefs[Keys.APP_LOCK_ENABLED] = appLockEnabled
        }
    }

    suspend fun setPendingInviteCode(code: String) {
        context.deviceSessionDataStore.edit { prefs ->
            prefs[Keys.PENDING_INVITE_CODE] = code
        }
    }

    suspend fun consumePendingInviteCode(): String {
        val current = getSnapshot().pendingInviteCode
        if (current.isNotEmpty()) {
            context.deviceSessionDataStore.edit { prefs ->
                prefs.remove(Keys.PENDING_INVITE_CODE)
            }
        }
        return current
    }

    suspend fun clearAppLockOnly() {
        context.deviceSessionDataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = false
        }
    }
}
