package com.example.niju_project.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object PrefsUtils {
    private const val PREFS_NAME = "niju_secure_prefs"

    private fun getSecurePrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun set2FAEnabled(context: Context, uid: String, enabled: Boolean) {
        getSecurePrefs(context).edit().putBoolean("2fa_enabled_$uid", enabled).apply()
    }

    fun is2FAEnabled(context: Context, uid: String): Boolean {
        // Migración silenciosa si existía en las prefs normales
        val oldPrefs = context.getSharedPreferences("niju_prefs", Context.MODE_PRIVATE)
        val key = "2fa_enabled_$uid"
        
        if (oldPrefs.contains(key)) {
            val value = oldPrefs.getBoolean(key, false)
            set2FAEnabled(context, uid, value)
            oldPrefs.edit().remove(key).apply()
            return value
        }
        
        return getSecurePrefs(context).getBoolean(key, false)
    }
}
