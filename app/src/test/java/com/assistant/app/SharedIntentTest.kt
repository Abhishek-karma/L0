package com.assistant.app

import android.content.Intent
import android.net.Uri
import com.assistant.app.data.SharedIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedIntentTest {

    @Test
    fun `plain text share is read`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Explain this simply.")

        val shared = SharedIntent.read(intent)

        assertEquals("Explain this simply.", shared?.text)
        assertTrue(shared?.uris.isNullOrEmpty())
    }

    @Test
    fun `shared url is read as text`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/article")

        assertEquals("https://example.com/article", SharedIntent.read(intent)?.text)
    }

    @Test
    fun `url delivered as intent data is read`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            data = Uri.parse("https://example.com/page")
        }

        assertEquals("https://example.com/page", SharedIntent.read(intent)?.text)
    }

    @Test
    fun `content uri in the intent data is not treated as text`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            data = Uri.parse("content://docs/document/4")
        }

        val shared = SharedIntent.read(intent)

        assertTrue(shared == null || shared.text == null)
    }

    @Test
    fun `single file share is read`() {
        val uri = Uri.parse("content://docs/document/1")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, uri)

        assertEquals(listOf(uri), SharedIntent.read(intent)?.uris)
    }

    @Test
    fun `image share is read`() {
        val uri = Uri.parse("content://media/image/7")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, uri)

        assertEquals(listOf(uri), SharedIntent.read(intent)?.uris)
    }

    @Test
    fun `multiple file share is read`() {
        val first = Uri.parse("content://docs/document/1")
        val second = Uri.parse("content://docs/document/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))

        assertEquals(listOf(first, second), SharedIntent.read(intent)?.uris)
    }

    @Test
    fun `clip data items are read when no stream extra is present`() {
        val uri = Uri.parse("content://docs/document/9")
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").apply {
            clipData = android.content.ClipData.newRawUri("doc", uri)
        }

        assertEquals(listOf(uri), SharedIntent.read(intent)?.uris)
    }

    @Test
    fun `subject is used when no text extra is present`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Weekly report")

        assertEquals("Weekly report", SharedIntent.read(intent)?.text)
    }

    @Test
    fun `empty share is rejected`() {
        assertNull(SharedIntent.read(Intent(Intent.ACTION_SEND).setType("text/plain")))
    }

    @Test
    fun `blank text share is rejected`() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "   ")

        assertNull(SharedIntent.read(intent))
    }

    @Test
    fun `null intent is rejected`() {
        assertNull(SharedIntent.read(null))
    }

    @Test
    fun `non-share intent is rejected`() {
        assertNull(SharedIntent.read(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun `malformed stream extra does not crash the parse`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, "not-a-uri")

        val shared = SharedIntent.read(intent)

        assertTrue(shared == null || shared.uris.isEmpty())
    }

    @Test
    fun `oversized text is truncated instead of blocking`() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "x".repeat(SharedIntent.MAX_TEXT_CHARS + 500))

        val text = SharedIntent.read(intent)?.text

        assertEquals(SharedIntent.MAX_TEXT_CHARS, text?.length)
        assertTrue(text!!.endsWith("…"))
    }

    @Test
    fun `text and files in one share are both read`() {
        val uri = Uri.parse("content://docs/document/3")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("*/*")
            .putExtra(Intent.EXTRA_TEXT, "Summarize this document.")
            .putExtra(Intent.EXTRA_STREAM, uri)

        val shared = SharedIntent.read(intent)

        assertEquals("Summarize this document.", shared?.text)
        assertEquals(listOf(uri), shared?.uris)
    }
}