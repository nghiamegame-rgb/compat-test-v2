package com.nghia.compattestv2

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build

/** Bật/tắt Foreground Service, bọc try/catch và ghi kết quả vào log. */
object FgsController {

    fun start(ctx: Context, caller: Logger.Source, reason: String): Boolean {
        val intent = Intent(ctx, BackgroundFgsService::class.java)
        return try {
            val cn = ctx.startForegroundService(intent)
            Logger.log(caller, "FGS_START_REQUEST", "result=OK reason=$reason component=${cn?.shortClassName ?: "null"}")
            true
        } catch (e: Exception) {
            Logger.log(
                caller, "FGS_START_REQUEST",
                "result=FAIL reason=$reason error=${errorName(e)} msg=${e.message ?: "N/A"}"
            )
            false
        }
    }

    fun stop(ctx: Context, caller: Logger.Source, reason: String): Boolean {
        val intent = Intent(ctx, BackgroundFgsService::class.java)
        return try {
            val wasRunning = ctx.stopService(intent)
            Logger.log(caller, "FGS_STOP_REQUEST", "result=OK reason=$reason wasRunning=$wasRunning")
            true
        } catch (e: Exception) {
            Logger.log(
                caller, "FGS_STOP_REQUEST",
                "result=FAIL reason=$reason error=${errorName(e)} msg=${e.message ?: "N/A"}"
            )
            false
        }
    }

    fun errorName(e: Throwable): String =
        if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) {
            "ForegroundServiceStartNotAllowedException"
        } else {
            e.javaClass.name
        }
}
