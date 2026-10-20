package com.assistant.app.ui.components

import android.content.Context
import android.content.Intent

fun openShareSheet(context: Context, text: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching {
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
