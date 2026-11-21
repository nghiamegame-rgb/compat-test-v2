package com.nghia.compattestv2

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context

/**
 * Đọc ActivityManager.getHistoricalProcessExitReasons và CHỈ ghi các bản ghi có
 * timestamp mới hơn mốc đã ghi lần trước (mốc lưu trong SharedPreferences).
 */
object ExitReasonRecorder {

    private const val PREFS = "exit_reasons"
    private const val KEY_LAST_TS = "last_logged_timestamp"

    fun recordNew(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTs = prefs.getLong(KEY_LAST_TS, 0L)

        val all: List<ApplicationExitInfo> = try {
            val am = context.getSystemService(ActivityManager::class.java)
            am.getHistoricalProcessExitReasons(context.packageName, 0, 0)
        } catch (t: Throwable) {
            Logger.log(
                Logger.Source.APP, "EXIT_INFO_ERROR",
                "error=${t.javaClass.name} msg=${t.message ?: "N/A"}"
            )
            return
        }

        val fresh = all.filter { it.timestamp > lastTs }.sortedBy { it.timestamp }
        Logger.log(
            Logger.Source.APP, "EXIT_INFO_SCAN",
            "total=${all.size} new=${fresh.size} " +
                "since=${if (lastTs == 0L) "none" else Logger.formatWall(lastTs)}"
        )

        for (info in fresh) {
            Logger.log(
                Logger.Source.APP, "EXIT_INFO",
                "reason=${reasonName(info.reason)}(${info.reason}) " +
                    "description=\"${info.description ?: "N/A"}\" " +
                    "timestamp=${Logger.formatWall(info.timestamp)} " +
                    "importance=${importanceName(info.importance)}(${info.importance}) " +
                    "status=${info.status} pid=${info.pid} " +
                    "process=${info.processName} pss_kb=${info.pss} rss_kb=${info.rss}"
            )
        }

        if (fresh.isNotEmpty()) {
            prefs.edit().putLong(KEY_LAST_TS, fresh.last().timestamp).commit()
        }
    }

    // Dùng số nguyên trực tiếp để không phụ thuộc hằng số chỉ có ở API mới.
    private fun reasonName(r: Int): String = when (r) {
        0 -> "UNKNOWN"
        1 -> "EXIT_SELF"
        2 -> "SIGNALED"
        3 -> "LOW_MEMORY"
        4 -> "CRASH"
        5 -> "CRASH_NATIVE"
        6 -> "ANR"
        7 -> "INITIALIZATION_FAILURE"
        8 -> "PERMISSION_CHANGE"
        9 -> "EXCESSIVE_RESOURCE_USAGE"
        10 -> "USER_REQUESTED"
        11 -> "USER_STOPPED"
        12 -> "DEPENDENCY_DIED"
        13 -> "OTHER"
        14 -> "FREEZER"
        15 -> "PACKAGE_STATE_CHANGE"
        16 -> "PACKAGE_UPDATED"
        else -> "REASON_$r"
    }

    private fun importanceName(i: Int): String = when (i) {
        100 -> "FOREGROUND"
        125 -> "FOREGROUND_SERVICE"
        150 -> "TOP_SLEEPING_PRE26"
        200 -> "VISIBLE"
        230 -> "PERCEPTIBLE"
        300 -> "SERVICE"
        325 -> "TOP_SLEEPING"
        350 -> "CANT_SAVE_STATE"
        400 -> "CACHED"
        1000 -> "GONE"
        else -> "IMPORTANCE_$i"
    }
}
