package com.noval.assistant.utils

import android.content.Context
import android.content.SharedPreferences

object PreferenceHelper {

    private const val PREF_NAME = "noval_prefs"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_CHATGPT_API_KEY = "chatgpt_api_key"
    private const val KEY_AI_MODE = "ai_mode" // "claude" or "chatgpt"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_SALUTATION = "salutation"
    private const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
    private const val KEY_NOVAL_ACTIVE = "noval_active" // master on/off
    private const val KEY_LANGUAGE = "language"
    private const val KEY_SETUP_DONE = "setup_done"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Claude API Key
    fun getApiKey(context: Context): String = prefs(context).getString(KEY_API_KEY, "") ?: ""
    fun setApiKey(context: Context, key: String) = prefs(context).edit().putString(KEY_API_KEY, key).apply()

    // ChatGPT API Key
    fun getChatGptApiKey(context: Context): String = prefs(context).getString(KEY_CHATGPT_API_KEY, "") ?: ""
    fun setChatGptApiKey(context: Context, key: String) = prefs(context).edit().putString(KEY_CHATGPT_API_KEY, key).apply()

    // AI Mode
    fun getAiMode(context: Context): String = prefs(context).getString(KEY_AI_MODE, "claude") ?: "claude"
    fun setAiMode(context: Context, mode: String) = prefs(context).edit().putString(KEY_AI_MODE, mode).apply()

    // User name & salutation
    fun getUserName(context: Context): String = prefs(context).getString(KEY_USER_NAME, "Sir") ?: "Sir"
    fun setUserName(context: Context, name: String) = prefs(context).edit().putString(KEY_USER_NAME, name).apply()
    fun getSalutation(context: Context): String = prefs(context).getString(KEY_SALUTATION, "Sir") ?: "Sir"
    fun setSalutation(context: Context, s: String) = prefs(context).edit().putString(KEY_SALUTATION, s).apply()

    // Wake word
    fun isWakeWordEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_WAKE_WORD_ENABLED, true)
    fun setWakeWordEnabled(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_WAKE_WORD_ENABLED, v).apply()

    // Master active toggle (Emergency on/off)
    fun isNovalActive(context: Context): Boolean = prefs(context).getBoolean(KEY_NOVAL_ACTIVE, true)
    fun setNovalActive(context: Context, v: Boolean) = prefs(context).edit().putBoolean(KEY_NOVAL_ACTIVE, v).apply()

    // Language
    fun getLanguage(context: Context): String = prefs(context).getString(KEY_LANGUAGE, "bn-BD") ?: "bn-BD"
    fun setLanguage(context: Context, lang: String) = prefs(context).edit().putString(KEY_LANGUAGE, lang).apply()

    // First time setup
    fun isSetupDone(context: Context): Boolean = prefs(context).getBoolean(KEY_SETUP_DONE, false)
    fun setSetupDone(context: Context) = prefs(context).edit().putBoolean(KEY_SETUP_DONE, true).apply()
    fun setSetupDone(context: Context, value: Boolean) = prefs(context).edit().putBoolean(KEY_SETUP_DONE, value).apply()
}
