package com.assistant.app.ui.chat

/**
 * A one-tap shortcut that writes an ordinary user prompt into the composer.
 *
 * These are prompt builders only: nothing here runs a model, calls a tool, or
 * bypasses the normal send flow, so the current provider and model always apply.
 */
enum class QuickAction(
    val label: String,
    private val instruction: String,
) {
    Summarize("Summarize", "Summarize the following clearly and briefly:"),
    Explain("Explain simply", "Explain the following in simple terms:"),
    ImproveWriting("Improve writing", "Rewrite the following to be clearer and better written, keeping the meaning:"),
    Translate("Translate", "Translate the following, and say which language you translated it into:"),
    Compare("Compare", "Compare the following and explain the differences:"),
    KeyPoints("Key points", "List the key points from the following as short bullets:");

    /**
     * Builds the composer draft for this action.
     *
     * With [source] the instruction is followed by the text to act on. Without
     * it the draft is only the instruction, so an action with nothing to work on
     * stays honest about what it will ask the user for.
     */
    fun compose(source: String?): String {
        val text = source?.trim().orEmpty()
        if (text.isEmpty()) return "$instruction\n\n"
        return "$instruction\n\n$text"
    }
}