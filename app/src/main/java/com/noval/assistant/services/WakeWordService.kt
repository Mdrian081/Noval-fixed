package com.noval.assistant.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.app.NotificationCompat
import com.noval.assistant.activities.MainActivity
import com.noval.assistant.utils.PreferenceHelper

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "noval_wake_channel"
        const val NOTIF_ID = 1001
        const val WAKE_WORD = "noval"
        const val WAKE_WORD_BN = "নোভেল"
        const val ACTION_WAKE_DETECTED = "com.noval.WAKE_DETECTED"
        var isListening = false
        var isActive = false
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        if (!isRunning) {
            isRunning = true
            handler.postDelayed({ startWakeWordListening() }, 300)
        }
        return START_STICKY
    }

    private fun startWakeWordListening() {
        if (!PreferenceHelper.isWakeWordEnabled(this)) {
            Log.d("WakeWordService", "Wake word disabled in settings")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("WakeWordService", "Speech recognition not available — retrying in 5s")
            scheduleRestart(5000)
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: android.os.Bundle?) {
                isListening = true
                Log.d("WakeWord", "Listening for wake word...")
            }

            override fun onResults(results: android.os.Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.lowercase() ?: ""
                Log.d("WakeWord", "Heard: $text")
                isListening = false

                if (containsWakeWord(text)) {
                    onWakeWordDetected()
                } else if (isRunning && !isActive) {
                    scheduleRestart(300)
                }
            }

            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                if (containsWakeWord(partial) && !isActive) {
                    onWakeWordDetected()
                }
            }

            override fun onError(error: Int) {
                isListening = false
                Log.d("WakeWord", "Error: $error")
                if (isRunning && !isActive) {
                    val delay = when (error) {
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 2000L
                        SpeechRecognizer.ERROR_NETWORK -> 5000L
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 300L
                        else -> 1000L
                    }
                    scheduleRestart(delay)
                }
            }

            override fun onEndOfSpeech() { isListening = false }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
        })

        doStartListening()
    }

    private fun containsWakeWord(text: String): Boolean {
        return text.contains(WAKE_WORD) ||
               text.contains(WAKE_WORD_BN) ||
               text.contains("novel") ||
               text.contains("naval") ||
               text.contains("নোভাল")
    }

    private fun doStartListening() {
        if (!isRunning || isActive) return
        val lang = PreferenceHelper.getLanguage(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("WakeWord", "Start listening failed: ${e.message}")
            scheduleRestart(2000)
        }
    }

    private fun scheduleRestart(delayMs: Long) {
        restartRunnable?.let { handler.removeCallbacks(it) }
        restartRunnable = Runnable {
            if (isRunning && !isActive) doStartListening()
        }
        handler.postDelayed(restartRunnable!!, delayMs)
    }

    private fun onWakeWordDetected() {
        if (isActive) return
        isActive = true
        isListening = false
        Log.d("WakeWord", "WAKE WORD DETECTED!")

        speechRecognizer?.stopListening()
        restartRunnable?.let { handler.removeCallbacks(it) }

        val intent = Intent(ACTION_WAKE_DETECTED)
        sendBroadcast(intent)

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            putExtra("wake_word_triggered", true)
        }
        startActivity(mainIntent)

        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(NOTIF_ID, buildNotification(active = true))
    }

    fun resumeWakeWordListening() {
        isActive = false
        scheduleRestart(1500)
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(NOTIF_ID, buildNotification(active = false))
    }

    private fun buildNotification(active: Boolean = false): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (active) "NovaL সক্রিয়" else "NovaL প্রস্তুত")
            .setContentText(if (active) "আপনার আদেশ শুনছি..." else "\"Noval\" বলুন")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "NovaL Wake Word Service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "NovaL সর্বদা wake word এর জন্য অপেক্ষা করছে"
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isListening = false
        restartRunnable?.let { handler.removeCallbacks(it) }
        speechRecognizer?.destroy()
        speechRecognizer = null
        // Auto-restart
        handler.postDelayed({
            val restartIntent = Intent(this, WakeWordService::class.java)
            startForegroundService(restartIntent)
        }, 1000)
    }
}
