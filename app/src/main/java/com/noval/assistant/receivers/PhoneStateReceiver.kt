package com.noval.assistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.noval.assistant.utils.ActionHandler
import com.noval.assistant.utils.TextToSpeechHelper

class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.PHONE_STATE") {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER) ?: ""
            if (state == TelephonyManager.EXTRA_STATE_RINGING && number.isNotEmpty()) {
                val name = ActionHandler.resolveContactName(context, number) ?: "Unknown"
                TextToSpeechHelper.speak("Incoming call from $name")
            }
        }
    }
}
