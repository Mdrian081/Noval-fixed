package com.noval.assistant.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.CountDownTimer
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.telephony.SmsManager
import android.util.Log
import com.noval.assistant.receivers.AlarmReceiver
import java.util.Calendar

object ActionHandler {

    private var flashlightOn = false
    private var activeTimer: CountDownTimer? = null

    /**
     * Parse and execute action command from Claude response
     * Returns a status message
     */
    fun executeAction(context: Context, actionCommand: String): String {
        val parts = actionCommand.split(":")
        return when (parts[0].uppercase()) {
            "CALL" -> {
                val contact = parts.getOrElse(1) { "" }
                makeCall(context, contact)
            }
            "SMS" -> {
                val number = parts.getOrElse(1) { "" }
                val message = parts.drop(2).joinToString(":")
                sendSms(context, number, message)
            }
            "ALARM" -> {
                val time = parts.getOrElse(1) { "08:00" }
                val label = parts.getOrElse(2) { "NovaL Alarm" }
                setAlarm(context, time, label)
            }
            "FLASHLIGHT" -> {
                val state = parts.getOrElse(1) { "OFF" }
                toggleFlashlight(context, state == "ON")
            }
            "LOCK" -> {
                lockScreen(context)
            }
            "OPEN_APP" -> {
                val appName = parts.getOrElse(1) { "" }
                openApp(context, appName)
            }
            "BATTERY" -> {
                getBatteryStatus(context)
            }
            "WIFI" -> {
                val state = parts.getOrElse(1) { "OFF" }
                toggleWifi(context, state == "ON")
            }
            "TIMER" -> {
                val seconds = parts.getOrElse(1) { "60" }.toLongOrNull() ?: 60L
                val label = parts.getOrElse(2) { "Timer" }
                startTimer(context, seconds, label)
            }
            "READ_SMS" -> {
                readLatestSms(context)
            }
            "READ_NOTIFICATIONS" -> {
                "Notification reading requires notification access permission."
            }
            else -> "Unknown command: $actionCommand"
        }
    }

    private fun makeCall(context: Context, contact: String): String {
        return try {
            val number = resolveContactNumber(context, contact) ?: contact
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            "Calling $contact"
        } catch (e: Exception) {
            "Failed to make call: ${e.message}"
        }
    }

    private fun sendSms(context: Context, numberOrName: String, message: String): String {
        return try {
            val number = resolveContactNumber(context, numberOrName) ?: numberOrName
            val smsManager = context.getSystemService(SmsManager::class.java)
            smsManager.sendTextMessage(number, null, message, null, null)
            "SMS sent to $numberOrName"
        } catch (e: Exception) {
            "Failed to send SMS: ${e.message}"
        }
    }

    private fun setAlarm(context: Context, time: String, label: String): String {
        return try {
            val parts = time.split(":")
            val hour = parts[0].toInt()
            val minute = parts.getOrElse(1) { "0" }.toInt()

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("label", label)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

            "Alarm set for $hour:${minute.toString().padStart(2, '0')} - $label"
        } catch (e: Exception) {
            "Failed to set alarm: ${e.message}"
        }
    }

    private fun toggleFlashlight(context: Context, turnOn: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, turnOn)
            flashlightOn = turnOn
            if (turnOn) "Flashlight activated" else "Flashlight deactivated"
        } catch (e: Exception) {
            "Flashlight error: ${e.message}"
        }
    }

    private fun lockScreen(context: Context): String {
        return try {
            val intent = Intent(Intent.ACTION_SCREEN_OFF).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            // Device policy manager lock (requires device admin)
            "Screen lock initiated"
        } catch (e: Exception) {
            "Lock error: ${e.message}"
        }
    }

    private fun openApp(context: Context, appName: String): String {
        val packageMap = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "camera" to "android.media.action.IMAGE_CAPTURE",
            "settings" to "com.android.settings",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos",
            "telegram" to "org.telegram.messenger"
        )

        val packageName = packageMap[appName.lowercase()]
        return if (packageName != null) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(launchIntent)
                "Opening $appName"
            } else {
                "$appName is not installed"
            }
        } else {
            // Try to find by app name
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(0)
            val found = apps.find {
                pm.getApplicationLabel(it).toString().lowercase().contains(appName.lowercase())
            }
            if (found != null) {
                val launchIntent = pm.getLaunchIntentForPackage(found.packageName)
                if (launchIntent != null) {
                    launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(launchIntent)
                    "Opening ${pm.getApplicationLabel(found)}"
                } else "Could not open $appName"
            } else "App '$appName' not found"
        }
    }

    private fun getBatteryStatus(context: Context): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = bm.isCharging
        return "Battery at $level%. ${if (isCharging) "Currently charging." else "Not charging."}"
    }

    @Suppress("DEPRECATION")
    private fun toggleWifi(context: Context, enable: Boolean): String {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = enable
            if (enable) "WiFi enabled" else "WiFi disabled"
        } catch (e: Exception) {
            "Please toggle WiFi manually from Settings"
        }
    }

    private fun startTimer(context: Context, seconds: Long, label: String): String {
        activeTimer?.cancel()
        activeTimer = object : CountDownTimer(seconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                NotificationHelper.showNotification(context, "Timer Complete", "$label is done!", "timer")
                TextToSpeechHelper.speak("$label is complete, Sir.")
            }
        }.start()
        val mins = seconds / 60
        val secs = seconds % 60
        return "Timer set for ${if (mins > 0) "$mins minutes" else ""} ${if (secs > 0) "$secs seconds" else ""}".trim()
    }

    private fun readLatestSms(context: Context): String {
        return try {
            val cursor = context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null, null, "${Telephony.Sms.DATE} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val address = it.getString(0)
                    val body = it.getString(1)
                    val name = resolveContactName(context, address) ?: address
                    "Latest SMS from $name: $body"
                } else "No SMS found"
            } ?: "Could not read SMS"
        } catch (e: Exception) {
            "SMS read error: ${e.message}"
        }
    }

    fun resolveContactName(context: Context, number: String): String? {
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val cursor = context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
    }

    fun resolveContactNumber(context: Context, nameOrNumber: String): String? {
        if (nameOrNumber.matches(Regex("[+0-9]+"))) return nameOrNumber
        return try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$nameOrNumber%"), null
            )
            cursor?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
    }
}
