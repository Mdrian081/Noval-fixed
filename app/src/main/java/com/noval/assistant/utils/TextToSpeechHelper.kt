package com.noval.assistant.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

object TextToSpeechHelper {

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pendingQueue = mutableListOf<String>()
    var onSpeakingDone: (() -> Unit)? = null

    fun init(context: Context) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                tts?.setSpeechRate(0.95f)
                tts?.setPitch(0.9f) // Slightly lower pitch for JARVIS feel
                isReady = true
                Log.d("TTS", "TextToSpeech initialized")

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId == "noval_spoken") {
                            onSpeakingDone?.invoke()
                        }
                    }
                    override fun onError(utteranceId: String?) {}
                })

                // Speak pending
                pendingQueue.forEach { speak(it) }
                pendingQueue.clear()
            }
        }
    }

    fun speak(text: String, id: String = "noval_spoken") {
        if (!isReady) {
            pendingQueue.add(text)
            return
        }
        val cleanText = text
            .replace(Regex("\\[ACTION:[^\\]]+\\]"), "")
            .replace("*", "")
            .trim()
        if (cleanText.isNotEmpty()) {
            tts?.speak(cleanText, TextToSpeech.QUEUE_ADD, null, id)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun shutdown() {
        tts?.shutdown()
        isReady = false
    }
}
