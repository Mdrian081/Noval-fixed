package com.noval.assistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noval.assistant.utils.NotificationHelper
import com.noval.assistant.utils.TextToSpeechHelper

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: "Alarm"
        TextToSpeechHelper.speak("$label. Your scheduled alarm is going off, Sir.")
        NotificationHelper.showNotification(context, "NovaL Alarm", label, "alarm")
    }
}
