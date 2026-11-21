package com.nghia.compattestv2

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Foreground Service kiểu specialUse. Khi chạy, phát thêm nhịp tim riêng gắn nguồn FGS.
 * Nhịp tim của AccessibilityService KHÔNG phụ thuộc dịch vụ này.
 */
class BackgroundFgsService : Service() {

    companion object {
        const val CHANNEL_ID = "compat_fgs"
        const val NOTIF_ID = 1001

        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var heartbeat: Heartbeat? = null

    override fun onCreate() {
        super.onCreate()
        Logger.log(Logger.Source.FGS, "FGS_ON_CREATE")
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.log(
            Logger.Source.FGS, "FGS_ON_START_COMMAND",
            "startId=$startId flags=$flags intentNull=${intent == null}"
        )
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, buildNotification())
            }
            isRunning = true
            Logger.log(
                Logger.Source.FGS, "FGS_START_FOREGROUND",
                "result=OK type=specialUse notifPermission=${notifPermissionText()}"
            )
        } catch (e: Exception) {
            isRunning = false
            Logger.log(
                Logger.Source.FGS, "FGS_START_FOREGROUND",
                "result=FAIL error=${FgsController.errorName(e)} msg=${e.message ?: "N/A"}"
            )
            stopSelf()
            return START_NOT_STICKY
        }
        if (heartbeat == null) {
            heartbeat = Heartbeat(this, Logger.Source.FGS).also { it.start() }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.log(Logger.Source.FGS, "FGS_TASK_REMOVED")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        heartbeat?.stop()
        heartbeat = null
        isRunning = false
        Logger.log(Logger.Source.FGS, "FGS_ON_DESTROY")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureChannel() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID, getString(R.string.fgs_channel_name), NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.fgs_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        } catch (e: Exception) {
            Logger.log(
                Logger.Source.FGS, "FGS_CHANNEL",
                "result=FAIL error=${e.javaClass.name} msg=${e.message ?: "N/A"}"
            )
        }
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setContentTitle(getString(R.string.fgs_notif_title))
            .setContentText(getString(R.string.fgs_notif_text))
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return b.build()
    }

    private fun notifPermissionText(): String =
        if (Build.VERSION.SDK_INT < 33) {
            "not_required"
        } else if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            "granted"
        } else {
            "denied"
        }
}
