package com.assistant.app

import com.assistant.app.data.PromptTemplate
import com.assistant.app.data.PromptTemplates
import com.assistant.app.ui.chat.QuickAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionTest {

    @Test
    fun `each action builds its own instruction`() {
        assertEquals(
            "Summarize the following clearly and briefly:\n\nSome article text.",
            QuickAction.Summarize.compose("Some article text."),
        )
        assertEquals(
            "Explain the following in simple terms:\n\nSome article text.",
            QuickAction.Explain.compose("Some article text."),
        )
        assertEquals(
            "Rewrite the following to be clearer and better written, keeping the meaning:\n\nSome article text.",
            QuickAction.ImproveWriting.compose("Some article text."),
        )
        assertEquals(
            "Translate the following, and say which language you translated it into:\n\nSome article text.",
            QuickAction.Translate.compose("Some article text."),
        )
        assertEquals(
            "Compare the following and explain the differences:\n\nSome article text.",
            QuickAction.Compare.compose("Some article text."),
        )
        assertEquals(
            "List the key points from the following as short bullets:\n\nSome article text.",
            QuickAction.KeyPoints.compose("Some article text."),
        )
    }

    @Test
    fun `every action produces a prompt that carries the source text`() {
        QuickAction.entries.forEach { action ->
            val prompt = action.compose("source material")
            assertTrue(action.name, prompt.contains("source material"))
        }
    }

    @Test
    fun `action without source stays an instruction the user can complete`() {
        val prompt = QuickAction.Summarize.compose(null)

        assertTrue(prompt.startsWith("Summarize"))
        assertFalse(prompt.contains("null"))
    }

    @Test
    fun `blank source behaves like no source`() {
        assertEquals(QuickAction.Summarize.compose(null), QuickAction.Summarize.compose("   "))
    }

    @Test
    fun `source text is trimmed before it is embedded`() {
        assertEquals(
            "Summarize the following clearly and briefly:\n\ntext",
            QuickAction.Summarize.compose("  text  "),
        )
    }
}

class PromptTemplatesTest {

    @Test
    fun `text placeholder is replaced by the source`() {
        val filled = PromptTemplates.fill("Summarize {{text}} in one line.", "the article")

        assertEquals("Summarize the article in one line.", filled.text)
        assertTrue(filled.missingValues.isEmpty())
    }

    @Test
    fun `missing source leaves the placeholder for the user to fill`() {
        val filled = PromptTemplates.fill("Summarize {{text}}.", null)

        assertEquals("Summarize {{text}}.", filled.text)
        assertEquals(listOf("{{text}}"), filled.missingValues)
    }

    @Test
    fun `unknown placeholders are reported back to the user`() {
        val filled = PromptTemplates.fill("Explain {{topic}} to {{audience}}.", "recursion")

        assertEquals(listOf("{{topic}}", "{{audience}}"), filled.missingValues)
    }

    @Test
    fun `placeholder matching ignores spacing`() {
        val filled = PromptTemplates.fill("Write about {{ text }}.", "kotlin")

        assertEquals("Write about kotlin.", filled.text)
    }

    @Test
    fun `template without placeholders is used verbatim`() {
        val filled = PromptTemplates.fill("Draft a standup update.", "ignored")

        assertEquals("Draft a standup update.", filled.text)
        assertTrue(filled.missingValues.isEmpty())
    }

    @Test
    fun `missing values on a template are derived from its body`() {
        val template = PromptTemplate(title = "T", body = "Do {{thing}}")

        assertEquals(listOf("{{thing}}"), template.missingValues)
    }

    @Test
    fun `blank titles and bodies are rejected`() {
        assertFalse(PromptTemplates.isValid("", "body"))
        assertFalse(PromptTemplates.isValid("title", "   "))
        assertTrue(PromptTemplates.isValid("title", "body"))
    }

    @Test
    fun `oversized bodies are rejected`() {
        val tooLong = "x".repeat(PromptTemplates.MAX_BODY_CHARS + 1)

        assertFalse(PromptTemplates.isValid("title", tooLong))
    }

    @Test
    fun `title is derived from the first line and truncated`() {
        assertEquals("Draft an email", PromptTemplates.titleFromBody("Draft an email\n\nfor the team"))

        val long = "x".repeat(80)
        val title = PromptTemplates.titleFromBody(long)
        assertTrue(title.length <= 41)
        assertTrue(title.endsWith("…"))
    }

    @Test
    fun `title from an empty body is empty rather than a placeholder`() {
        assertEquals("", PromptTemplates.titleFromBody("   "))
    }
}