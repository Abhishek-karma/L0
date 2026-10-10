package com.assistant.app.data

import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Content handed to L0 by another app through the system share sheet.
 *
 * Shared text is never logged: it can be a private message, a document excerpt,
 * or a link the user considers sensitive.
 */
data class SharedContent(
    val text: String? = null,
    val uris: List<Uri> = emptyList(),
) {
    val isEmpty: Boolean get() = text.isNullOrBlank() && uris.isEmpty()
}

object SharedIntent {

    /** Keeps a single oversized share from being pasted into the composer as-is. */
    const val MAX_TEXT_CHARS = 100_000

    fun read(intent: Intent?): SharedContent? {
        if (intent == null) return null
        val text = readText(intent)
        val uris = readUris(intent)
        if (text == null && uris.isEmpty()) return null
        return SharedContent(text = text, uris = uris)
    }

    private fun readText(intent: Intent): String? {
        val shared = listOfNotNull(
            intent.textExtra(Intent.EXTRA_TEXT),
            intent.textExtra(Intent.EXTRA_SUBJECT),
            intent.webLink(),
        ).map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        if (shared.length <= MAX_TEXT_CHARS) return shared
        return shared.take(MAX_TEXT_CHARS).trimEnd() + "…"
    }

    /**
     * Browsers and readers share links through the intent data. Only web links
     * are read here; a `content://` data URI is a file and belongs in the
     * attachment pipeline, never in the composer text.
     */
    private fun Intent.webLink(): String? {
        val data = data ?: return null
        if (data.scheme != "http" && data.scheme != "https") return null
        if (hasExtra(Intent.EXTRA_TEXT) || hasExtra(Intent.EXTRA_STREAM)) return null
        return data.toString()
    }

    private fun readUris(intent: Intent): List<Uri> {
        val streams = when (intent.action) {
            Intent.ACTION_SEND -> intent.streamExtra() + intent.streamListExtra()
            Intent.ACTION_SEND_MULTIPLE -> intent.streamListExtra() + intent.streamExtra()
            else -> emptyList()
        }
        return (streams + clipUris(intent)).distinct()
    }

    private fun clipUris(intent: Intent): List<Uri> {
        val clip = intent.clipData ?: return emptyList()
        return buildList {
            for (index in 0 until clip.itemCount) {
                clip.getItemAt(index).uri?.let { add(it) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.textExtra(name: String): String? = runCatching {
        (getCharSequenceExtra(name) ?: getStringExtra(name))?.toString()
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun Intent.streamExtra(): List<Uri> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOfNotNull(getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
        } else {
            listOfNotNull(getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    private fun Intent.streamListExtra(): List<Uri> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }.getOrDefault(emptyList())
}