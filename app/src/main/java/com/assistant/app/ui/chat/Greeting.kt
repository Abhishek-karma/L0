package com.assistant.app.ui.chat

enum class Greeting { MORNING, AFTERNOON, EVENING }

fun timeOfDayGreeting(hourOfDay: Int): Greeting = when (hourOfDay) {
    in 5..11 -> Greeting.MORNING
    in 12..16 -> Greeting.AFTERNOON
    else -> Greeting.EVENING
}
