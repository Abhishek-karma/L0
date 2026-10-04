package com.assistant.app.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.security.GeneralSecurityException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun `permanently unavailable storage behaves as empty instead of crashing`() {
        val store = EncryptedSecureKeyStore(context)

        assertNull(store.apiKey(id = 1))
        assertNull(store.legacyApiKey())
        store.setApiKey(id = 1, value = "sk-ignored")
        store.setApiKey(id = 2, value = null)
        assertNull(store.apiKey(id = 1))
    }

    @Test
    fun `first failure deletes the corrupt file and retries once`() {
        val store = EncryptedSecureKeyStore(context)
        val corrupt = store.prefsFile()
        corrupt.parentFile?.mkdirs()
        corrupt.writeText("corrupt-ciphertext")

        var attempts = 0
        var corruptFileGoneBeforeRetry = false
        val recovered = store.createWithRecovery {
            attempts += 1
            if (attempts == 1) throw GeneralSecurityException("simulated corrupt keyset")
            corruptFileGoneBeforeRetry = !corrupt.exists()
            context.getSharedPreferences("recovery_test_prefs", Context.MODE_PRIVATE)
        }

        assertEquals(2, attempts)
        assertTrue(corruptFileGoneBeforeRetry)
        assertFalse(corrupt.exists())
        assertNotNull(recovered)
        recovered!!.edit().putString("probe", "ok").commit()
        assertEquals("ok", recovered.getString("probe", null))
    }
}
