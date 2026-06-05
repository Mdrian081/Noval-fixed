package com.noval.assistant.activities

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.noval.assistant.databinding.ActivitySettingsBinding
import com.noval.assistant.utils.PreferenceHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()

        binding.btnSave.setOnClickListener { saveSettings() }
        binding.btnBack.setOnClickListener { finish() }

        // Reset setup (show API key screen again)
        binding.btnResetSetup.setOnClickListener {
            PreferenceHelper.setSetupDone(this, false)
            Toast.makeText(this, "Setup reset করা হয়েছে। App restart করুন।", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSettings() {
        // API Keys
        binding.etClaudeApiKey.setText(PreferenceHelper.getApiKey(this))
        binding.etChatGptApiKey.setText(PreferenceHelper.getChatGptApiKey(this))

        // AI Mode
        val aiMode = PreferenceHelper.getAiMode(this)
        binding.radioClaude.isChecked = aiMode == "claude"
        binding.radioChatGpt.isChecked = aiMode == "chatgpt"

        // User
        binding.etUserName.setText(PreferenceHelper.getUserName(this))

        // Salutation
        val salutation = PreferenceHelper.getSalutation(this)
        binding.radioSir.isChecked = salutation == "Sir"
        binding.radioMaam.isChecked = salutation == "Ma'am"
        binding.radioCustom.isChecked = salutation != "Sir" && salutation != "Ma'am"
        if (binding.radioCustom.isChecked) binding.etCustomSalutation.setText(salutation)

        // Wake word
        binding.switchWakeWord.isChecked = PreferenceHelper.isWakeWordEnabled(this)

        // Master active
        binding.switchNovalActive.isChecked = PreferenceHelper.isNovalActive(this)

        // Language
        val lang = PreferenceHelper.getLanguage(this)
        binding.radioBengali.isChecked = lang == "bn-BD"
        binding.radioEnglish.isChecked = lang == "en-US"
    }

    private fun saveSettings() {
        val claudeKey = binding.etClaudeApiKey.text.toString().trim()
        val chatGptKey = binding.etChatGptApiKey.text.toString().trim()
        val userName = binding.etUserName.text.toString().trim()
        val wakeEnabled = binding.switchWakeWord.isChecked
        val novalActive = binding.switchNovalActive.isChecked

        val aiMode = if (binding.radioChatGpt.isChecked) "chatgpt" else "claude"
        val salutation = when {
            binding.radioSir.isChecked -> "Sir"
            binding.radioMaam.isChecked -> "Ma'am"
            else -> binding.etCustomSalutation.text.toString().trim().ifEmpty { "Sir" }
        }
        val language = if (binding.radioEnglish.isChecked) "en-US" else "bn-BD"

        PreferenceHelper.setApiKey(this, claudeKey)
        PreferenceHelper.setChatGptApiKey(this, chatGptKey)
        PreferenceHelper.setAiMode(this, aiMode)
        PreferenceHelper.setUserName(this, userName)
        PreferenceHelper.setSalutation(this, salutation)
        PreferenceHelper.setWakeWordEnabled(this, wakeEnabled)
        PreferenceHelper.setNovalActive(this, novalActive)
        PreferenceHelper.setLanguage(this, language)

        Toast.makeText(this, "সেটিংস সংরক্ষণ হয়েছে!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
