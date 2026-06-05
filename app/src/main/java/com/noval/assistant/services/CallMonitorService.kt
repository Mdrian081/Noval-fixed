package com.noval.assistant.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.noval.assistant.activities.MainActivity
import com.noval.assistant.utils.ActionHandler
import com.noval.assistant.utils.TextToSpeechHelper

class CallMonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "noval_call_channel"
        const val NOTIF_ID = 1002
        var incomingCallerName: String = ""
        var incomingCallerNumber: String = ""
        var isInCall: Boolean = false
    }

    private lateinit var telephonyManager: TelephonyManager

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        registerCallListener()
        return START_STICKY
    }

    private fun registerCallListener() {
        // Modern API (Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            telephonyManager.registerTelephonyCallback(
                mainExecutor,
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state, telephonyManager.line1Number ?: "")
                    }
                }
            )
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(object : PhoneStateListener() {
                @Suppress("DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state, phoneNumber ?: "")
                }
            }, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun handleCallState(state: Int, number: String) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                incomingCallerNumber = number
                val name = ActionHandler.resolveContactName(this, number)
                incomingCallerName = name ?: "Unknown"
                isInCall = false
                Log.d("CallMonitor", "Incoming call from: $incomingCallerName ($number)")

                // Announce incoming call via TTS
                val announcement = "Incoming call from $incomingCallerName"
                TextToSpeechHelper.speak(announcement)

                // Notify MainActivity
                val intent = Intent("com.noval.INCOMING_CALL").apply {
                    putExtra("caller_name", incomingCallerName)
                    putExtra("caller_number", number)
                }
                sendBroadcast(intent)

                // Activate NovaL if not already active
                if (!WakeWordService.isActive) {
                    val mainIntent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        putExtra("incoming_call", true)
                        putExtra("caller_name", incomingCallerName)
                        putExtra("caller_number", number)
                    }
                    startActivity(mainIntent)
                }
            }

            TelephonyManager.CALL_STATE_OFFHOOK -> {
                isInCall = true
                Log.d("CallMonitor", "Call connected")
            }

            TelephonyManager.CALL_STATE_IDLE -> {
                if (isInCall || incomingCallerName.isNotEmpty()) {
                    Log.d("CallMonitor", "Call ended")
                    isInCall = false
                    incomingCallerName = ""
                    incomingCallerNumber = ""
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NovaL Call Monitor")
            .setContentText("Monitoring calls...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "NovaL Call Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        val restartIntent = Intent(this, CallMonitorService::class.java)
        startForegroundService(restartIntent)
    }
}
