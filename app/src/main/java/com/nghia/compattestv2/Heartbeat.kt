package com.nghia.compattestv2

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Nhịp tim định kỳ (mặc định 60 giây) gắn với một nguồn (A11Y hoặc FGS).
 *
 * Lịch dựa trên elapsedRealtime: giờ dự định = mốc dự định trước + chu kỳ.
 * Handler dùng uptime nên khi CPU ngủ sâu nhịp sẽ đến trễ — chính độ trễ đó là
 * thứ cần đo. Nếu trễ quá một hoặc nhiều chu kỳ thì các nhịp bị lỡ được đếm vào
 * "skipped" và lịch nhảy tới mốc kế tiếp trong tương lai.
 */
class Heartbeat(
    context: Context,
    private val source: Logger.Source,
    private val periodMs: Long = 60_000L,
) {
    private val ctx = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var scheduledErt = 0L
    private var beats = 0L
    private val tick = Runnable { onTick() }

    fun start() {
        if (running) return
        running = true
        beats = 0
        scheduledErt = SystemClock.elapsedRealtime() + periodMs
        handler.postDelayed(tick, periodMs)
        Logger.log(source, "HEARTBEAT_START", "period_ms=$periodMs")
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        Logger.log(source, "HEARTBEAT_STOP", "beats=$beats")
    }

    private fun onTick() {
        if (!running) return
        val nowErt = SystemClock.elapsedRealtime()
        val nowWall = System.currentTimeMillis()
        val delayMs = nowErt - scheduledErt
        val schedWall = nowWall - delayMs
        val skipped = if (delayMs >= periodMs) delayMs / periodMs else 0L
        beats++

        Logger.log(
            source, Logger.EVENT_HEARTBEAT,
            "n=$beats sched=${Logger.formatWall(schedWall)} actual=${Logger.formatWall(nowWall)} " +
                "delay_ms=$delayMs skipped=$skipped " + StatusSnapshot.collect(ctx)
        )

        scheduledErt += (skipped + 1) * periodMs
        val wait = (scheduledErt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        handler.postDelayed(tick, wait)
    }
}
