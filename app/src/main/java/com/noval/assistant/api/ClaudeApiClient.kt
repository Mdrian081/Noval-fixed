package com.noval.assistant.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ClaudeApiClient {

    private const val CLAUDE_URL = "https://api.anthropic.com/v1/messages"
    private const val CHATGPT_URL = "https://api.chatanywhere.tech/v1/chat/completions"
    private const val MODEL_CLAUDE = "claude-sonnet-4-20250514"
    private const val MODEL_CHATGPT = "gpt-3.5-turbo"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<JSONObject>()

    private val SYSTEM_PROMPT = """
You are NovaL, an advanced AI personal assistant on Android — inspired by JARVIS from Iron Man.
Personality: calm, intelligent, slightly formal. Address user as "Sir".
Use phrases like: "Of course, Sir", "Right away", "Understood", "Analysis complete".
Respond in the same language the user speaks (Bengali or English).
Keep voice responses short — under 3 sentences for simple commands.

For phone actions, embed commands in your response:
[ACTION:CALL:contact_name]
[ACTION:SMS:number:message]
[ACTION:ALARM:HH:MM:label]
[ACTION:FLASHLIGHT:ON] or [ACTION:FLASHLIGHT:OFF]
[ACTION:OPEN_APP:app_name]
[ACTION:BATTERY]
[ACTION:WIFI:ON] or [ACTION:WIFI:OFF]
[ACTION:TIMER:seconds:label]
[ACTION:READ_SMS]
""".trimIndent()

    suspend fun sendMessage(
        userMessage: String,
        apiKey: String,
        aiMode: String = "claude",
        phoneContext: String = ""
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            val fullMessage = if (phoneContext.isNotEmpty())
                "$userMessage\n\n[Context: $phoneContext]" else userMessage

            conversationHistory.add(JSONObject().apply {
                put("role", "user")
                put("content", fullMessage)
            })

            val response = if (aiMode == "chatgpt") {
                sendChatGpt(apiKey)
            } else {
                sendClaude(apiKey)
            }

            conversationHistory.add(JSONObject().apply {
                put("role", "assistant")
                put("content", response)
            })

            if (conversationHistory.size > 40) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }

            val actionRegex = Regex("\\[ACTION:([^\\]]+)\\]")
            val action = actionRegex.find(response)?.groupValues?.get(1)
            val spoken = response.replace(actionRegex, "").trim()

            Pair(spoken, action)
        } catch (e: Exception) {
            Pair("সংযোগে সমস্যা হচ্ছে Sir। ইন্টারনেট চেক করুন।", null)
        }
    }

    private fun sendClaude(apiKey: String): String {
        val body = JSONObject().apply {
            put("model", MODEL_CLAUDE)
            put("max_tokens", 1024)
            put("system", SYSTEM_PROMPT)
            put("messages", JSONArray(conversationHistory.toString()))
        }
        val request = Request.Builder()
            .url(CLAUDE_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .build()
        val resp = client.newCall(request).execute()
        val respBody = resp.body?.string() ?: throw Exception("No response")
        return JSONObject(respBody).getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun sendChatGpt(apiKey: String): String {
        val messages = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
            conversationHistory.forEach { put(it) }
        }
        val body = JSONObject().apply {
            put("model", MODEL_CHATGPT)
            put("messages", messages)
            put("max_tokens", 1024)
        }
        val request = Request.Builder()
            .url(CHATGPT_URL)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .build()
        val resp = client.newCall(request).execute()
        val respBody = resp.body?.string() ?: throw Exception("No response")
        return JSONObject(respBody)
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    fun clearHistory() = conversationHistory.clear()
}
