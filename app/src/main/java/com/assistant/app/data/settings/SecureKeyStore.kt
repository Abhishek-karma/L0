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

    fun setApiKey(id: Long, value: String?): Boolean

    fun legacyApiKey(): String?

    fun deleteLegacyApiKey()

    val isAvailable: Boolean

    val needsCredentialReentry: Boolean
}

class EncryptedSecureKeyStore(context: Context) : SecureKeyStore {

    private val appContext = context.applicationContext

    internal class Opened(val preferences: SharedPreferences, val recoveredFromCorruption: Boolean)

    private val opened: Opened? by lazy {
        createWithRecovery(::createEncrypted)
    }

    override val isAvailable: Boolean get() = opened != null

    override val needsCredentialReentry: Boolean get() = opened?.recoveredFromCorruption == true

    override fun apiKey(id: Long): String? = opened?.preferences?.getString(keyFor(id), null)

    @SuppressLint("ApplySharedPref")
    override fun setApiKey(id: Long, value: String?): Boolean {
        val prefs = opened?.preferences ?: return false
        prefs.edit().apply {
            if (value == null) remove(keyFor(id)) else putString(keyFor(id), value)
        }.commit()
        return true
    }

    override fun legacyApiKey(): String? = opened?.preferences?.getString(KEY_API_KEY, null)

    @SuppressLint("ApplySharedPref")
    override fun deleteLegacyApiKey() {
        opened?.preferences?.edit()?.remove(KEY_API_KEY)?.commit()
    }

    private fun keyFor(id: Long): String = "${KEY_API_KEY}_$id"

    internal fun createWithRecovery(create: () -> SharedPreferences): Opened? =
        try {
            Opened(create(), recoveredFromCorruption = false)
        } catch (e: Exception) {
            if (e is GeneralSecurityException || e is IOException) {
                quarantineAndRetry(e, create)
            } else {
                throw e
            }
        }

    // The unreadable file is quarantined, not deleted: the ciphertext stays on disk
    // for inspection while the app gets a fresh, working store and re-enters credentials.
    private fun quarantineAndRetry(first: Exception, create: () -> SharedPreferences): Opened? {

        Log.w(TAG, "Encrypted preferences unreadable (${first.javaClass.simpleName}); quarantining the unreadable file")
        quarantine()
        return try {
            Opened(create(), recoveredFromCorruption = true)
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

    internal fun quarantine() {
        val prefs = prefsFile()
        if (!prefs.exists()) return
        val target = File(prefs.parentFile, "$PREFS_FILE.xml.corrupt")
        if (target.exists()) target.delete()
        prefs.renameTo(target)
    }

    internal fun prefsFile(): File =
        File(appContext.applicationInfo.dataDir, "shared_prefs/$PREFS_FILE.xml")

    private companion object {
        const val TAG = "EncryptedKeyStore"
        const val PREFS_FILE = "provider_secure_prefs"
        const val KEY_API_KEY = "api_key"
    }
}
