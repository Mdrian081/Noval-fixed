package com.noval.assistant.activities

import com.noval.assistant.R
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.noval.assistant.api.ClaudeApiClient
import com.noval.assistant.databinding.ActivityMainBinding
import com.noval.assistant.services.CallMonitorService
import com.noval.assistant.services.WakeWordService
import com.noval.assistant.utils.ActionHandler
import com.noval.assistant.utils.NotificationHelper
import com.noval.assistant.utils.PreferenceHelper
import com.noval.assistant.utils.TextToSpeechHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListeningForCommand = false
    private val handler = Handler(Looper.getMainLooper())

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WakeWordService.ACTION_WAKE_DETECTED -> {
                    if (PreferenceHelper.isNovalActive(this@MainActivity)) activateNoval()
                }
                "com.noval.INCOMING_CALL" -> {
                    val name = intent.getStringExtra("caller_name") ?: "Unknown"
                    val number = intent.getStringExtra("caller_number") ?: ""
                    showCallOverlay(name, number)
                }
            }
        }
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        private const val PERMISSION_REQUEST_CODE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        TextToSpeechHelper.init(this)
        NotificationHelper.init(this)

        // First time setup - show API key screen
        if (!PreferenceHelper.isSetupDone(this)) {
            showSetupScreen()
        } else {
            showMainScreen()
        }

        checkPermissions()
        startServices()
        registerReceivers()
        updateClock()

        if (intent.getBooleanExtra("wake_word_triggered", false)) {
            handler.postDelayed({ activateNoval() }, 800)
        }
        if (intent.getBooleanExtra("incoming_call", false)) {
            showCallOverlay(
                intent.getStringExtra("caller_name") ?: "Unknown",
                intent.getStringExtra("caller_number") ?: ""
            )
        }
    }

    // ===== SETUP SCREEN (First launch) =====
    private fun showSetupScreen() {
        binding.setupLayout.visibility = View.VISIBLE
        binding.mainLayout.visibility = View.GONE

        // AI mode toggle
        binding.switchAiMode.setOnCheckedChangeListener { _, checked ->
            binding.tvAiModeLabel.text = if (checked) "ChatGPT Mode" else "Claude Mode"
            binding.tilApiKey.hint = if (checked) "ChatGPT API Key (sk-...)" else "Claude API Key (sk-ant-...)"
        }

        binding.btnActivateNoval.setOnClickListener {
            val apiKey = binding.etSetupApiKey.text.toString().trim()
            if (apiKey.isEmpty()) {
                Toast.makeText(this, "API Key দিন", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val isChatGpt = binding.switchAiMode.isChecked
            if (isChatGpt) {
                PreferenceHelper.setChatGptApiKey(this, apiKey)
                PreferenceHelper.setAiMode(this, "chatgpt")
            } else {
                PreferenceHelper.setApiKey(this, apiKey)
                PreferenceHelper.setAiMode(this, "claude")
            }
            PreferenceHelper.setSetupDone(this)
            showMainScreen()
            TextToSpeechHelper.speak("আমি প্রস্তুত Sir. আমি NovaL, আপনার ব্যক্তিগত সহকারী।")
        }
    }

    // ===== MAIN SCREEN =====
    private fun showMainScreen() {
        binding.setupLayout.visibility = View.GONE
        binding.mainLayout.visibility = View.VISIBLE
        updateNovalStatus()

        // Mic button
        binding.btnMic.setOnClickListener {
            if (!PreferenceHelper.isNovalActive(this)) {
                Toast.makeText(this, "NovaL বন্ধ আছে। Settings এ চালু করুন।", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isListeningForCommand) stopListening() else activateNoval()
        }

        // Settings
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Clear chat
        binding.btnClear.setOnClickListener {
            ClaudeApiClient.clearHistory()
            binding.tvConversation.text = ""
            binding.tvConversation.visibility = View.GONE
        }

        // Emergency STOP button
        binding.btnEmergencyStop.setOnClickListener {
            val isActive = PreferenceHelper.isNovalActive(this)
            PreferenceHelper.setNovalActive(this, !isActive)
            updateNovalStatus()
            if (!isActive) {
                TextToSpeechHelper.speak("NovaL সক্রিয় হয়েছে Sir.")
                startServices()
            } else {
                TextToSpeechHelper.stop()
                stopService(Intent(this, WakeWordService::class.java))
                Toast.makeText(this, "NovaL বন্ধ করা হয়েছে", Toast.LENGTH_SHORT).show()
            }
        }

        binding.callOverlay.visibility = View.GONE
        binding.btnRejectCall.setOnClickListener { binding.callOverlay.visibility = View.GONE }

        updateClock()
    }

    private fun updateNovalStatus() {
        val active = PreferenceHelper.isNovalActive(this)
        binding.btnEmergencyStop.text = if (active) "⏹ NovaL বন্ধ করুন" else "▶ NovaL চালু করুন"
        binding.tvStatus.text = if (active) "Noval বলুন বা মাইক চাপুন" else "NovaL নিষ্ক্রিয়"
        binding.tvStatus.setTextColor(
            if (active) android.graphics.Color.parseColor("#00E5FF")
            else android.graphics.Color.parseColor("#FF4444")
        )
    }

    // ===== VOICE ACTIVATION =====
    private fun activateNoval() {
        if (isListeningForCommand) return
        if (!PreferenceHelper.isNovalActive(this)) return

        WakeWordService.isActive = true
        isListeningForCommand = true
        binding.tvStatus.text = "শুনছি..."
        binding.pulseView.visibility = View.VISIBLE

        TextToSpeechHelper.speak("জ্বি Sir?")
        TextToSpeechHelper.onSpeakingDone = {
            runOnUiThread { startCommandListening() }
        }
    }

    private fun startCommandListening() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (command.isNotEmpty()) handleCommand(command) else deactivateNoval()
            }
            override fun onError(error: Int) {
                binding.tvStatus.text = "Noval বলুন বা মাইক চাপুন"
                deactivateNoval()
            }
            override fun onReadyForSpeech(params: Bundle?) { binding.tvStatus.text = "বলুন..." }
            override fun onRmsChanged(rmsdB: Float) {
                val scale = 1f + (rmsdB / 30f).coerceIn(0f, 0.5f)
                binding.pulseView.scaleX = scale
                binding.pulseView.scaleY = scale
            }
            override fun onEndOfSpeech() { binding.tvStatus.text = "প্রসেস হচ্ছে..." }
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partial: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val lang = PreferenceHelper.getLanguage(this)
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(recognizerIntent)
    }

    private fun handleCommand(command: String) {
        addToConversation("আপনি", command)
        binding.tvStatus.text = "প্রসেস হচ্ছে..."
        binding.pulseView.visibility = View.GONE
        isListeningForCommand = false

        val phoneContext = buildPhoneContext()
        val aiMode = PreferenceHelper.getAiMode(this)
        val apiKey = if (aiMode == "chatgpt")
            PreferenceHelper.getChatGptApiKey(this)
        else
            PreferenceHelper.getApiKey(this)

        if (apiKey.isEmpty()) {
            showSetupScreen()
            return
        }

        lifecycleScope.launch {
            val (response, action) = ClaudeApiClient.sendMessage(command, apiKey, aiMode, phoneContext)
            runOnUiThread {
                addToConversation("NovaL", response)
                binding.tvStatus.text = "Noval বলুন বা মাইক চাপুন"
                if (action != null) {
                    val result = ActionHandler.executeAction(this@MainActivity, action)
                    addToConversation("System", result)
                }
                TextToSpeechHelper.speak(response)
                TextToSpeechHelper.onSpeakingDone = { runOnUiThread { deactivateNoval() } }
            }
        }
    }

    private fun buildPhoneContext(): String {
        val sb = StringBuilder()
        if (CallMonitorService.isInCall) sb.append("ব্যবহারকারী এখন কলে আছেন। ")
        if (CallMonitorService.incomingCallerName.isNotEmpty())
            sb.append("ইনকামিং কল: ${CallMonitorService.incomingCallerName}। ")
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        sb.append("ব্যাটারি: ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%। ")
        sb.append("সময়: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}।")
        return sb.toString()
    }

    private fun deactivateNoval() {
        isListeningForCommand = false
        WakeWordService.isActive = false
        binding.tvStatus.text = "Noval বলুন বা মাইক চাপুন"
        binding.pulseView.visibility = View.GONE
        binding.pulseView.scaleX = 1f
        binding.pulseView.scaleY = 1f
        // Resume wake word listening
        if (PreferenceHelper.isNovalActive(this)) {
            startForegroundService(Intent(this, WakeWordService::class.java))
        }
    }

    private fun stopListening() {
        speechRecognizer?.stopListening()
        deactivateNoval()
    }

    private fun showCallOverlay(callerName: String, callerNumber: String) {
        runOnUiThread {
            binding.callOverlay.visibility = View.VISIBLE
            binding.tvCallerName.text = callerName
            binding.tvCallerNumber.text = callerNumber
        }
    }

    private fun addToConversation(role: String, text: String) {
        binding.tvConversation.visibility = View.VISIBLE
        val current = binding.tvConversation.text.toString()
        binding.tvConversation.text =
            if (current.isEmpty()) "$role: $text" else "$current\n\n$role: $text"
        binding.scrollView.post { binding.scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startServices() {
        if (PreferenceHelper.isNovalActive(this)) {
            startForegroundService(Intent(this, WakeWordService::class.java))
            startForegroundService(Intent(this, CallMonitorService::class.java))
        }
    }

    private fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(WakeWordService.ACTION_WAKE_DETECTED)
            addAction("com.noval.INCOMING_CALL")
        }
        registerReceiver(eventReceiver, filter)
    }

    private fun checkPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    private fun updateClock() {
        if (!::binding.isInitialized) return
        binding.tvTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        binding.tvDate.text = SimpleDateFormat("EEEE, d MMM", Locale("bn")).format(Date())
        handler.postDelayed({ updateClock() }, 60000)
    }

    override fun onResume() {
        super.onResume()
        if (PreferenceHelper.isSetupDone(this)) updateNovalStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        try { unregisterReceiver(eventReceiver) } catch (e: Exception) {}
        TextToSpeechHelper.shutdown()
    }
}
