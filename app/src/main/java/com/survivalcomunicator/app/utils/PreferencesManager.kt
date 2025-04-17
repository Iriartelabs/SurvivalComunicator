package com.survivalcomunicator.app.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PreferencesManager(context: Context) {
    
    companion object {
        private const val PREFERENCES_NAME = "survival_communicator_prefs"
        private const val KEY_USERNAME = "username"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USER_ID = "user_id"
    }
    
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    suspend fun saveUsername(username: String) {
        sharedPreferences.edit().putString(KEY_USERNAME, username).apply()
    }
    
    suspend fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }
    
    suspend fun saveServerUrl(serverUrl: String) {
        sharedPreferences.edit().putString(KEY_SERVER_URL, serverUrl).apply()
    }
    
    suspend fun getServerUrl(): String? {
        return sharedPreferences.getString(KEY_SERVER_URL, null)
    }
    
    suspend fun saveUserId(userId: String) {
        sharedPreferences.edit().putString(KEY_USER_ID, userId).apply()
    }
    
    suspend fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    suspend fun clearAllPreferences() {
        sharedPreferences.edit().clear().apply()
    }
}