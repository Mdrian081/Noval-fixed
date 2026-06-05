package com.noval.assistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noval.assistant.services.CallMonitorService
import com.noval.assistant.services.WakeWordService
import com.noval.assistant.utils.PreferenceHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            // Start WakeWord Service
            if (PreferenceHelper.isWakeWordEnabled(context)) {
                val wakeIntent = Intent(context, WakeWordService::class.java)
                context.startForegroundService(wakeIntent)
            }

            // Start Call Monitor
            val callIntent = Intent(context, CallMonitorService::class.java)
            context.startForegroundService(callIntent)
        }
    }
}
