package com.assistant.app.data

import java.util.UUID

/**
 * A user-authored prompt the user can reuse. Templates are never sent on their
 * own: applying one only fills the composer, so it stays a normal user message.
 */
data class PromptTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val body: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

object PromptTemplates {

    /** Longest a pasted template body may be, so the editor stays responsive. */
    const val MAX_BODY_CHARS = 4_000

    private val PLACEHOLDER = Regex("""\{\{\s*([a-zA-Z0-9_]+)\s*}}""")

    /**
     * Substitutes [text] for `{{text}}` and reports the placeholders left over,
     * so the caller can ask the user to fill them instead of sending `{{topic}}`.
     */
    fun fill(body: String, text: String?): FilledTemplate {
        val source = text?.trim().orEmpty()
        val missing = mutableListOf<String>()
        val filled = PLACEHOLDER.replace(body) { match ->
            val name = match.groupValues[1]
            if (name.equals("text", ignoreCase = true) && source.isNotEmpty()) {
                source
            } else {
                missing += match.value
                match.value
            }
        }
        return FilledTemplate(filled, missing.distinct())
    }

    fun titleFromBody(body: String): String {
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return if (firstLine.length <= DEFAULT_TITLE_LENGTH) {
            firstLine
        } else {
            firstLine.take(DEFAULT_TITLE_LENGTH).trimEnd() + "…"
        }
    }

    fun isValid(title: String, body: String): Boolean =
        title.isNotBlank() && body.isNotBlank() && body.length <= MAX_BODY_CHARS

    private const val DEFAULT_TITLE_LENGTH = 40
}

data class FilledTemplate(val text: String, val missingValues: List<String>)