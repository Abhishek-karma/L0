package com.assistant.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EncryptedSecureKeyStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun quarantinedFile(): File =
        File(context.applicationInfo.dataDir, "shared_prefs/provider_secure_prefs.xml.corrupt")

    @Test
    fun `permanently unavailable storage behaves as empty instead of crashing`() {
        val store = EncryptedSecureKeyStore(context)

        assertNull(store.apiKey(id = 1))
        assertNull(store.legacyApiKey())
        store.setApiKey(id = 1, value = "sk-ignored")
        store.setApiKey(id = 2, value = null)
        assertNull(store.apiKey(id = 1))
        assertFalse(store.isAvailable)
        assertFalse(store.needsCredentialReentry)
    }

    @Test
    fun `corrupt file is quarantined instead of deleted when opening fails`() {
        val store = EncryptedSecureKeyStore(context)
        val corrupt = store.prefsFile()
        corrupt.parentFile?.mkdirs()
        corrupt.writeText("corrupt-ciphertext")

        assertNull(store.apiKey(id = 1))

        assertFalse(corrupt.exists())
        assertTrue(quarantinedFile().exists())
        assertEquals("corrupt-ciphertext", quarantinedFile().readText())
    }

    @Test
    fun `recovery quarantines the unreadable file before retrying and reports the reentry need`() {
        val store = EncryptedSecureKeyStore(context)
        val corrupt = store.prefsFile()
        corrupt.parentFile?.mkdirs()
        corrupt.writeText("corrupt-ciphertext")

        var attempts = 0
        var quarantinedBeforeRetry = false
        val opened = store.createWithRecovery {
            attempts += 1
            if (attempts == 1) throw GeneralSecurityException("simulated corrupt keyset")
            quarantinedBeforeRetry = quarantinedFile().exists() &&
                quarantinedFile().readText() == "corrupt-ciphertext"
            context.getSharedPreferences("recovery_test_prefs", Context.MODE_PRIVATE)
        }

        assertEquals(2, attempts)
        assertTrue(quarantinedBeforeRetry)
        assertFalse(corrupt.exists())
        assertTrue(quarantinedFile().exists())
        assertTrue(opened!!.recoveredFromCorruption)
        opened.preferences.edit().putString("probe", "ok").commit()
        assertEquals("ok", opened.preferences.getString("probe", null))
    }

    @Test
    fun `a store that opens without recovery needs no credential reentry`() {
        val store = EncryptedSecureKeyStore(context)

        val opened = store.createWithRecovery {
            context.getSharedPreferences("recovery_test_prefs", Context.MODE_PRIVATE)
        }

        assertTrue(opened != null)
        assertFalse(opened!!.recoveredFromCorruption)
    }
}
