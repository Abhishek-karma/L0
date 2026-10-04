package com.assistant.app.voice

fun speakableText(markdown: String): String {
    var text = markdown.replace(Regex("```[\\s\\S]*?```"), " ")
    text = text.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    text = text.replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    text = text.replace(Regex("`([^`]*)`"), "$1")
    text = text.replace(Regex("(\\*\\*|__)(.*?)\\1"), "$2")
    text = text.replace(Regex("(\\*|_)(.*?)\\1"), "$2")
    text = text.replace(Regex("(?m)^#{1,6}\\s+"), "")
    text = text.replace(Regex("(?m)^[ \\t]*([-*+]|\\d+\\.)[ \\t]+"), "")
    text = text.replace(Regex("\\s+"), " ")
    return text.trim()
}
