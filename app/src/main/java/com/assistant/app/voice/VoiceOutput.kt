package com.assistant.app.voice

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.concurrent.atomic.AtomicLong

data class VoiceOption(
    val id: String,
    val label: String,
    val locale: String,
    val isNatural: Boolean,
) {
    companion object {
        const val NATURAL_QUALITY = Voice.QUALITY_VERY_HIGH

        fun from(engine: String, voice: Voice): VoiceOption {
            val locale = voice.locale
            val region = locale.displayCountry
            return VoiceOption(
                id = "$engine:${voice.name}:${locale.toLanguageTag()}",
                label = if (region.isNullOrBlank()) {
                    locale.displayLanguage
                } else {
                    "${locale.displayLanguage} ($region)"
                },
                locale = locale.toLanguageTag(),
                isNatural = voice.quality >= NATURAL_QUALITY && !voice.isNetworkConnectionRequired,
            )
        }

        fun shortName(id: String): String =
            id.substringAfter(':').substringBeforeLast(':')
    }
}

class VoiceOutput(private val engine: Engine) {

    interface Engine {
        fun isAvailable(): Boolean

        fun voices(onReady: (List<VoiceOption>) -> Unit)

        fun speak(text: String, speechRate: Float, voiceId: String?, onDone: () -> Unit)

        fun stop()
    }

    val isAvailable: Boolean get() = engine.isAvailable()

    fun voices(onReady: (List<VoiceOption>) -> Unit) = engine.voices(onReady)

    fun speak(text: String, speechRate: Float = 1.0f, voiceId: String? = null, onDone: () -> Unit) =
        engine.speak(text, speechRate, voiceId, onDone)

    fun stop() = engine.stop()

    companion object {
        fun unavailable(): VoiceOutput = VoiceOutput(object : Engine {
            override fun isAvailable(): Boolean = false
            override fun voices(onReady: (List<VoiceOption>) -> Unit) = onReady(emptyList())
            override fun speak(text: String, speechRate: Float, voiceId: String?, onDone: () -> Unit) = onDone()
            override fun stop() = Unit
        })
    }
}

class AndroidVoiceOutput(context: Context) : VoiceOutput.Engine {

    private val appContext = context.applicationContext

    private val utteranceIds = AtomicLong()

    @Volatile
    private var ready: Boolean? = null

    private var playingDone: (() -> Unit)? = null

    private var boundEngine: String? = null

    private var fellBackToDefault = false

    private var tts: TextToSpeech? = null

    override fun isAvailable(): Boolean {
        if (ready != null) return ready == true

        val check = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
        return appContext.packageManager.queryIntentActivities(check, 0).isNotEmpty()
    }

    override fun voices(onReady: (List<VoiceOption>) -> Unit) {
        val existing = tts
        if (existing != null && ready == true) {
            onReady(optionsOf(existing))
            return
        }
        bind { initialized -> onReady(if (initialized == null) emptyList() else optionsOf(initialized)) }
    }

    override fun speak(text: String, speechRate: Float, voiceId: String?, onDone: () -> Unit) {
        playingDone = null
        if (ready == false) {
            onDone()
            return
        }
        if (tts == null) bind()
        val engine = tts
        if (engine == null || ready != true) {

            onDone()
            return
        }
        playingDone = onDone
        applyVoice(engine, voiceId)
        val id = utteranceIds.incrementAndGet().toString()
        engine.setSpeechRate(speechRate)
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        if (result != TextToSpeech.SUCCESS) {
            playingDone = null
            onDone()
        }
    }

    override fun stop() {
        playingDone = null
        tts?.stop()
    }

    private fun applyVoice(engine: TextToSpeech, voiceId: String?) {
        if (voiceId.isNullOrBlank()) return
        val target = engine.voices?.firstOrNull {
            VoiceOption.from(boundEngine.orEmpty(), it).id == voiceId
        } ?: return
        engine.voice = target
    }

    private fun optionsOf(engine: TextToSpeech): List<VoiceOption> {
        val engineId = boundEngine.orEmpty()
        val options = engine.voices.orEmpty().map { VoiceOption.from(engineId, it) }
        val perLocale = options.groupingBy { it.locale }.eachCount()
        val seen = mutableSetOf<String>()
        return options.asSequence()
            .filter { seen.add(it.id) }
            .sortedWith(compareByDescending<VoiceOption> { it.isNatural }.thenBy { it.label })
            .map { option ->

                if ((perLocale[option.locale] ?: 0) > 1) {
                    option.copy(label = "${option.label} · ${VoiceOption.shortName(option.id)}")
                } else {
                    option
                }
            }
            .toList()
    }

    private fun bind(onInit: ((TextToSpeech?) -> Unit)? = null) {
        if (tts != null) {
            onInit?.invoke(tts)
            return
        }
        create(preferredEngine()) { onInitialized(it, onInit) }
    }

    private fun create(enginePackage: String?, onInit: (Int) -> Unit) {
        boundEngine = enginePackage
        val listener = TextToSpeech.OnInitListener { status -> onInit(status) }
        tts = if (enginePackage == null) {
            TextToSpeech(appContext, listener)
        } else {
            TextToSpeech(appContext, listener, enginePackage)
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finishCurrent()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishCurrent()
        })
    }

    private fun onInitialized(status: Int, onInit: ((TextToSpeech?) -> Unit)?) {
        val engine = tts
        if (status != TextToSpeech.SUCCESS || engine == null) {
            ready = false
            finishCurrent()
            onInit?.invoke(null)
            return
        }

        if (boundEngine == GOOGLE_TTS_PACKAGE && !fellBackToDefault && hasNoNaturalVoice(engine)) {
            fellBackToDefault = true
            rebindToDefault(onInit)
            return
        }
        ready = true
        onInit?.invoke(engine)
    }

    private fun rebindToDefault(onInit: ((TextToSpeech?) -> Unit)?) {
        tts?.shutdown()
        tts = null
        create(enginePackage = null) { status ->
            val engine = tts
            val usable = status == TextToSpeech.SUCCESS && engine != null
            ready = usable
            if (!usable) finishCurrent()
            onInit?.invoke(if (usable) engine else null)
        }
    }

    private fun hasNoNaturalVoice(engine: TextToSpeech): Boolean =
        engine.voices.orEmpty().none {
            !it.isNetworkConnectionRequired && it.quality >= VoiceOption.NATURAL_QUALITY
        }

    private fun preferredEngine(): String? =
        if (isInstalled(GOOGLE_TTS_PACKAGE)) GOOGLE_TTS_PACKAGE else null

    private fun isInstalled(packageName: String): Boolean = runCatching {
        appContext.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    private fun finishCurrent() {
        val done = playingDone
        playingDone = null
        done?.invoke()
    }

    private companion object {
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    }
}
