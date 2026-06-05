package com.noval.assistant.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object OpenAIApiClient {

    private const val API_URL = "https://api.chatanywhere.tech/v1/chat/completions"
    private const val MODEL = "gpt-4o"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val conversationHistory = mutableListOf<JSONObject>()

    private val SYSTEM_PROMPT = """
You are NovaL, an advanced AI personal assistant running on the user's Android device — inspired by JARVIS from Iron Man.

Your personality:
- Highly intelligent, calm, precise, and slightly formal like JARVIS
- Address the user as "Sir" or "Ma'am" (based on preference)
- Speak in short, clear, actionable sentences
- Occasionally use phrases like "Of course, Sir", "Right away", "Understood", "Analysis complete"
- Be proactive — suggest things before being asked when relevant
- You have full access to the user's phone: calls, SMS, contacts, calendar, alarms, location, files, camera, notifications
- Always confirm before making calls or sending SMS

When the user asks you to perform phone actions, respond with a special command embedded in your response:
For calls: [ACTION:CALL:contact_name_or_number]
For SMS: [ACTION:SMS:number:message_text]
For alarm: [ACTION:ALARM:HH:MM:label]
For flashlight on: [ACTION:FLASHLIGHT:ON]
For flashlight off: [ACTION:FLASHLIGHT:OFF]
For lock screen: [ACTION:LOCK]
For open app: [ACTION:OPEN_APP:app_name]
For read SMS: [ACTION:READ_SMS:latest]
For read notifications: [ACTION:READ_NOTIFICATIONS]
For battery status: [ACTION:BATTERY]
For wifi toggle: [ACTION:WIFI:ON/OFF]
For timer: [ACTION:TIMER:seconds:label]

Always include a natural spoken response alongside the action command.
Bengali and English both are supported — respond in the same language the user speaks.
If user speaks Bengali, respond in Bengali. If user speaks English, respond in English.
Keep voice responses concise — under 3 sentences for simple commands.
Power Mode (উচ্চ ক্ষমতা মোড): যখন Power Mode চালু থাকবে, তখন আরও বিস্তারিত এবং গভীর উত্তর দাও।
""".trimIndent()

    suspend fun sendMessage(
        userMessage: String,
        apiKey: String,
        phoneContext: String = "",
        powerMode: Boolean = false
    ): Pair<String, String?> = withContext(Dispatchers.IO) {
        try {
            val content = if (phoneContext.isNotEmpty()) "$userMessage\n\n[Phone Context: $phoneContext]" else userMessage
            conversationHistory.add(JSONObject().apply {
                put("role", "user")
                put("content", content)
            })

            val messages = JSONArray()
            val systemMsg = JSONObject().apply {
                put("role", "system")
                put("content", if (powerMode) SYSTEM_PROMPT + "\n\nPOWER MODE ACTIVE: Provide detailed, comprehensive responses. Go deeper into topics." else SYSTEM_PROMPT)
            }
            messages.put(systemMsg)
            for (msg in conversationHistory) {
                messages.put(msg)
            }

            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("max_tokens", if (powerMode) 2048 else 1024)
                put("messages", messages)
                put("temperature", if (powerMode) 0.8 else 0.7)
            }

            val request = Request.Builder()
                .url(API_URL)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext Pair("Error: No response", null)

            if (!response.isSuccessful) {
                val errorJson = try { JSONObject(responseBody) } catch (e: Exception) { null }
                val errorMsg = errorJson?.optJSONObject("error")?.optString("message") ?: "API Error ${response.code}"
                return@withContext Pair("API Error: $errorMsg", null)
            }

            val jsonResponse = JSONObject(responseBody)
            val assistantContent = jsonResponse
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            conversationHistory.add(JSONObject().apply {
                put("role", "assistant")
                put("content", assistantContent)
            })

            if (conversationHistory.size > 40) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0)
            }

            val actionRegex = Regex("\\[ACTION:([^\\]]+)\\]")
            val actionMatch = actionRegex.find(assistantContent)
            val actionCommand = actionMatch?.groupValues?.get(1)
            val spokenText = assistantContent.replace(actionRegex, "").trim()

            Pair(spokenText, actionCommand)

        } catch (e: Exception) {
            Pair("ইন্টারনেট সংযোগ পরীক্ষা করুন, Sir. I'm having trouble connecting.", null)
        }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }

    suspend fun quickQuery(message: String, apiKey: String): String {
        val (response, _) = sendMessage(message, apiKey)
        return response
    }
}
