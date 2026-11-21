package com.nghia.compattestv2

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings

/** Ảnh chụp trạng thái hệ thống cho mỗi nhịp tim. Trường nào đọc lỗi thì ghi N/A. */
object StatusSnapshot {

    fun collect(ctx: Context): String {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val am = ctx.getSystemService(ActivityManager::class.java)
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val bm = ctx.getSystemService(BatteryManager::class.java)

        return listOf(
            "a11y=" + na { if (isA11yEnabled(ctx)) "ON" else "OFF" },
            "interactive=" + na { pm!!.isInteractive.toString() },
            "deviceIdle=" + na { pm!!.isDeviceIdleMode.toString() },
            "powerSave=" + na { pm!!.isPowerSaveMode.toString() },
            "ignoreBattOpt=" + na { pm!!.isIgnoringBatteryOptimizations(ctx.packageName).toString() },
            "bgRestricted=" + na { am!!.isBackgroundRestricted.toString() },
            "bucket=" + na { bucketName(usm!!.appStandbyBucket) },
            "battery=" + na {
                val c = bm!!.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (c < 0 || c > 100) throw IllegalStateException("capacity=$c")
                "$c%"
            },
            "charging=" + na { bm!!.isCharging.toString() },
            "fgs=" + if (BackgroundFgsService.isRunning) "ON" else "OFF",
        ).joinToString(" ")
    }

    /** Đọc Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, tìm đúng component của app. */
    fun isA11yEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, KeepAliveA11yService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == me }
    }

    fun a11yText(ctx: Context): String = na { if (isA11yEnabled(ctx)) "ON" else "OFF" }

    private fun bucketName(b: Int): String = when (b) {
        5 -> "EXEMPTED(5)"
        10 -> "ACTIVE(10)"
        20 -> "WORKING_SET(20)"
        30 -> "FREQUENT(30)"
        40 -> "RARE(40)"
        45 -> "RESTRICTED(45)"
        50 -> "NEVER(50)"
        else -> "BUCKET($b)"
    }

    private inline fun na(block: () -> String): String =
        try {
            block()
        } catch (t: Throwable) {
            "N/A"
        }
}
