package com.nghia.compattestv2

import java.time.OffsetDateTime

/**
 * Phân tích file log. Mọi kết quả được tính lại từ danh sách dòng mỗi lần gọi,
 * không giữ trạng thái trong bộ nhớ.
 */
object LogAnalyzer {

    data class Entry(val raw: String, val epochMs: Long?, val source: String, val event: String)

    data class Gap(val startMs: Long, val endMs: Long, val ongoing: Boolean) {
        val durationMs: Long get() = endMs - startMs
    }

    data class GapResult(val closed: List<Gap>, val ongoing: Gap?, val markers: Int)

    fun parse(line: String): Entry? {
        if (!line.startsWith("#")) return null
        val parts = line.split(Logger.SEP, limit = 8)
        if (parts.size < 7) return null
        val time = parts[1].substringBefore(' ')
        val epoch = try {
            OffsetDateTime.parse(time).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
        return Entry(line, epoch, parts[5].trim(), parts[6].trim())
    }

    /** "Quan trọng" = mọi dòng trừ nhịp tim định kỳ (HEARTBEAT). Dòng không đọc được vẫn giữ. */
    fun isImportant(line: String): Boolean {
        val e = parse(line) ?: return true
        return e.event != Logger.EVENT_HEARTBEAT
    }

    fun lastLines(lines: List<String>, importantOnly: Boolean, n: Int = 20): List<String> =
        (if (importantOnly) lines.filter { isImportant(it) } else lines).takeLast(n)

    /**
     * Khoảng đứt nhịp tim của AccessibilityService: khoảng cách giữa hai mốc "còn sống"
     * liên tiếp của nguồn A11Y vượt ngưỡng. Mốc còn sống = HEARTBEAT, HEARTBEAT_START,
     * HEARTBEAT_STOP của nguồn A11Y. Nếu từ mốc cuối đến hiện tại vượt ngưỡng thì báo
     * là khoảng đứt đang diễn ra (không cộng vào bộ đếm khoảng đã đóng).
     */
    fun findGaps(lines: List<String>, thresholdMs: Long, nowMs: Long): GapResult {
        val gaps = ArrayList<Gap>()
        var prev: Long? = null
        var markers = 0
        for (line in lines) {
            val e = parse(line) ?: continue
            if (e.source != Logger.Source.A11Y.name) continue
            if (!e.event.startsWith(Logger.EVENT_HEARTBEAT)) continue
            val t = e.epochMs ?: continue
            markers++
            val p = prev
            if (p != null && t - p > thresholdMs) gaps.add(Gap(p, t, false))
            if (p == null || t > p) prev = t
        }
        val last = prev
        val ongoing = if (last != null && nowMs - last > thresholdMs) Gap(last, nowMs, true) else null
        return GapResult(gaps, ongoing, markers)
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%dh%02dm%02ds".format(h, m, s) else "%dm%02ds".format(m, s)
    }
}
