package com.assistant.app.data.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

interface SecureKeyStore {

    fun apiKey(id: Long): String?

    fun setApiKey(id: Long, value: String?)

    fun legacyApiKey(): String?

    fun deleteLegacyApiKey()
}

class EncryptedSecureKeyStore(context: Context) : SecureKeyStore {

    private val appContext = context.applicationContext

    private val preferences: SharedPreferences? by lazy {
        createWithRecovery(::createEncrypted)
    }

    override fun apiKey(id: Long): String? = preferences?.getString(keyFor(id), null)

    @SuppressLint("ApplySharedPref")
    override fun setApiKey(id: Long, value: String?) {
        val prefs = preferences ?: return
        prefs.edit().apply {
            if (value == null) remove(keyFor(id)) else putString(keyFor(id), value)
        }.commit()
    }

    override fun legacyApiKey(): String? = preferences?.getString(KEY_API_KEY, null)

    @SuppressLint("ApplySharedPref")
    override fun deleteLegacyApiKey() {
        preferences?.edit()?.remove(KEY_API_KEY)?.commit()
    }

    private fun keyFor(id: Long): String = "${KEY_API_KEY}_$id"

    internal fun createWithRecovery(create: () -> SharedPreferences): SharedPreferences? =
        try {
            create()
        } catch (e: Exception) {
            if (e is GeneralSecurityException || e is IOException) {
                resetAndRetry(e, create)
            } else {
                throw e
            }
        }

    private fun resetAndRetry(first: Exception, create: () -> SharedPreferences): SharedPreferences? {

        Log.w(TAG, "Encrypted preferences unreadable (${first.javaClass.simpleName}); resetting")
        prefsFile().delete()
        return try {
            create()
        } catch (e: Exception) {
            if (e is GeneralSecurityException || e is IOException) {
                Log.w(TAG, "Encrypted preferences unavailable (${e.javaClass.simpleName}); disabled until next launch")
                null
            } else {
                throw e
            }
        }
    }

    private fun createEncrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    internal fun prefsFile(): File =        File(appContext.applicationInfo.dataDir, "shared_prefs/$PREFS_FILE.xml")

    private companion object {
        const val TAG = "EncryptedKeyStore"
        const val PREFS_FILE = "provider_secure_prefs"
        const val KEY_API_KEY = "api_key"
    }
}
