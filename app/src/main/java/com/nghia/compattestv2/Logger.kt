package com.nghia.compattestv2

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Logger DUY NHẤT của app. Mọi thao tác ghi/đọc file log đều đi qua [lock].
 *
 * - File chỉ ghi thêm (append-only) trong bộ nhớ trong: filesDir/compat_log.txt.
 * - flush() + fsync sau mỗi dòng.
 * - Định dạng mỗi dòng (phân tách bằng " | "):
 *   #seq | giờ-địa-phương-ISO+offset zoneId | ert=elapsedRealtime | pid=… | ses=mã-phiên | NGUỒN | SỰ_KIỆN | chi tiết
 */
object Logger {

    enum class Source { A11Y, FGS, APP }

    const val FILE_NAME = "compat_log.txt"
    const val SEP = " | "
    const val EVENT_HEARTBEAT = "HEARTBEAT"

    private const val TAG = "CompatTestV2"

    private val lock = Any()
    private val timeFmt: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)

    // Các trường dưới đây chỉ được truy cập khi giữ lock.
    private var file: File? = null
    private var out: FileOutputStream? = null
    private var seq = 0L

    @Volatile
    var sessionId: String = "N/A"
        private set

    fun init(context: Context, session: String) {
        synchronized(lock) {
            if (out != null) return
            sessionId = session
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            seq = try {
                readLastSeq(f)
            } catch (e: Exception) {
                Log.e(TAG, "Không đọc được seq cuối", e)
                0L
            }
            file = f
            out = FileOutputStream(f, true)
        }
    }

    fun log(source: Source, event: String, details: String = "") {
        synchronized(lock) {
            val o = out
            if (o == null) {
                Log.w(TAG, "Logger chưa init, bỏ dòng: $source $event $details")
                return
            }
            seq++
            val now = ZonedDateTime.now()
            val line = buildString {
                append('#').append(seq.toString().padStart(6, '0')).append(SEP)
                append(timeFmt.format(now)).append(' ').append(now.zone.id).append(SEP)
                append("ert=").append(SystemClock.elapsedRealtime()).append(SEP)
                append("pid=").append(Process.myPid()).append(SEP)
                append("ses=").append(sessionId).append(SEP)
                append(source.name).append(SEP)
                append(event).append(SEP)
                append(sanitize(details))
            }
            try {
                o.write((line + "\n").toByteArray(Charsets.UTF_8))
                o.flush()
                o.fd.sync()
            } catch (e: IOException) {
                Log.e(TAG, "Ghi log thất bại", e)
            }
            Log.i(TAG, line)
        }
    }

    /** Định dạng mốc epoch-ms thành giờ địa phương kèm offset (không dùng UTC trần). */
    fun formatWall(epochMs: Long): String =
        timeFmt.format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

    fun readAllLines(context: Context): List<String> {
        synchronized(lock) {
            val f = file ?: File(context.applicationContext.filesDir, FILE_NAME)
            if (!f.exists()) return emptyList()
            return f.readLines(Charsets.UTF_8).filter { it.isNotEmpty() }
        }
    }

    /** Sao chép ảnh chụp nhất quán của file log sang [dest] (dùng cho chia sẻ). */
    fun snapshotTo(context: Context, dest: File) {
        synchronized(lock) {
            val f = file ?: File(context.applicationContext.filesDir, FILE_NAME)
            if (f.exists()) f.copyTo(dest, overwrite = true) else dest.writeText("")
        }
    }

    private fun sanitize(s: String): String =
        s.replace("\r", "\\r").replace("\n", "\\n")

    private fun readLastSeq(f: File): Long {
        if (!f.exists() || f.length() == 0L) return 0L
        RandomAccessFile(f, "r").use { raf ->
            val len = raf.length()
            val start = maxOf(0L, len - 16 * 1024)
            raf.seek(start)
            val buf = ByteArray((len - start).toInt())
            raf.readFully(buf)
            val lines = String(buf, Charsets.UTF_8).split('\n')
            for (l in lines.asReversed()) {
                if (!l.startsWith("#")) continue
                val n = l.substring(1).substringBefore(SEP).trim().toLongOrNull()
                if (n != null) return n
            }
        }
        return 0L
    }
}
