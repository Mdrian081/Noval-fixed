package com.noval.assistant.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.noval.assistant.utils.ActionHandler
import com.noval.assistant.utils.NotificationHelper

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            messages?.forEach { sms ->
                val number = sms.originatingAddress ?: ""
                val body = sms.messageBody ?: ""
                val name = ActionHandler.resolveContactName(context, number) ?: number
                NotificationHelper.showNotification(
                    context,
                    "SMS from $name",
                    body,
                    "sms_$number"
                )
            }
        }
    }
}
