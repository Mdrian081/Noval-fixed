package com.noval.assistant.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NovalNotificationService : NotificationListenerService() {

    companion object {
        val recentNotifications = mutableListOf<NotificationInfo>()
        var instance: NovalNotificationService? = null
    }

    data class NotificationInfo(
        val appName: String,
        val title: String,
        val text: String,
        val time: Long
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val appName = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) { sbn.packageName }

        if (title.isNotEmpty() || text.isNotEmpty()) {
            recentNotifications.add(0, NotificationInfo(appName, title, text, System.currentTimeMillis()))
            if (recentNotifications.size > 50) recentNotifications.removeLastOrNull()
            Log.d("NovalNotif", "[$appName] $title: $text")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun getRecentNotificationsSummary(): String {
        if (recentNotifications.isEmpty()) return "No recent notifications."
        return recentNotifications.take(5).joinToString(". ") {
            "${it.appName}: ${it.title} - ${it.text}"
        }
    }
}
