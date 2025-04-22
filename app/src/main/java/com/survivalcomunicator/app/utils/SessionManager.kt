package com.survivalcomunicator.app.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Gestiona la sesión del usuario y preferencias de la aplicación
 */
class SessionManager(private val context: Context) {
    private val PREF_NAME = "SurvivalCommunicatorPrefs"
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    companion object {
        // Claves para los datos de usuario
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_PUBLIC_KEY = "public_key"
        private const val KEY_LOCAL_MODE = "local_mode_enabled"
        private const val KEY_LAST_SERVER = "last_server"
        private const val KEY_LAST_PORT = "last_port"
    }
    
    // Métodos para gestionar el ID del usuario
    fun setUserId(userId: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_USER_ID, userId)
        editor.apply()
    }
    
    fun getUserId(): String? {
        return sharedPreferences.getString(KEY_USER_ID, null)
    }
    
    // Métodos para gestionar el nombre de usuario
    fun setUsername(username: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_USERNAME, username)
        editor.apply()
    }
    
    fun getUsername(): String? {
        return sharedPreferences.getString(KEY_USERNAME, null)
    }
    
    // Métodos para gestionar la clave pública
    fun setUserPublicKey(publicKey: String) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_PUBLIC_KEY, publicKey)
        editor.apply()
    }
    
    fun getUserPublicKey(): String? {
        return sharedPreferences.getString(KEY_PUBLIC_KEY, null)
    }
    
    // Métodos para gestionar el modo local
    fun setLocalModeEnabled(enabled: Boolean) {
        val editor = sharedPreferences.edit()
        editor.putBoolean(KEY_LOCAL_MODE, enabled)
        editor.apply()
    }
    
    fun isLocalModeEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_LOCAL_MODE, false)
    }
    
    // Métodos para la última conexión al servidor
    fun setLastServerInfo(address: String, port: Int) {
        val editor = sharedPreferences.edit()
        editor.putString(KEY_LAST_SERVER, address)
        editor.putInt(KEY_LAST_PORT, port)
        editor.apply()
    }
    
    fun getLastServerAddress(): String? {
        return sharedPreferences.getString(KEY_LAST_SERVER, null)
    }
    
    fun getLastServerPort(): Int {
        return sharedPreferences.getInt(KEY_LAST_PORT, -1)
    }
    
    // Método para borrar todos los datos (logout)
    fun clearSession() {
        val editor = sharedPreferences.edit()
        editor.clear()
        editor.apply()
    }
    
    // Verificar si hay una sesión activa
    fun isLoggedIn(): Boolean {
        return !getUserId().isNullOrEmpty() && !getUsername().isNullOrEmpty()
    }
}