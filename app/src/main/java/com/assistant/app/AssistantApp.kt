package com.assistant.app

import android.app.Application

class AssistantApp : Application() {
    val container: AppContainer = AppContainer(this)
}
