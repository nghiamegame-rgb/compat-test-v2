package com.nghia.compattestv2

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val REQ_NOTIF = 42
        private const val MAX_GAPS_SHOWN = 100
        private const val MAX_CLIP_CHARS = 100_000
        private const val MAX_SHARE_TEXT_CHARS = 50_000
    }

    private lateinit var tvStatus: TextView
    private lateinit var swFgs: Switch
    private lateinit var etThreshold: EditText
    private lateinit var tvGapCount: TextView
    private lateinit var tvGaps: TextView
    private lateinit var cbImportantOnly: CheckBox
    private lateinit var tvLog: TextView

    private val bg: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        swFgs = findViewById(R.id.swFgs)
        etThreshold = findViewById(R.id.etThreshold)
        tvGapCount = findViewById(R.id.tvGapCount)
        tvGaps = findViewById(R.id.tvGaps)
        cbImportantOnly = findViewById(R.id.cbImportantOnly)
        tvLog = findViewById(R.id.tvLog)

        // Công tắc FGS: chỉ bật/tắt FGS. Không đụng tới AccessibilityService hay nhịp tim A11Y.
        swFgs.isChecked = Prefs.isFgsEnabled(this)
        swFgs.setOnCheckedChangeListener { _, checked ->
            Prefs.setFgsEnabled(this, checked)
            Logger.log(Logger.Source.APP, "FGS_SWITCH", "checked=$checked")
            if (checked) {
                FgsController.start(this, Logger.Source.APP, "user_switch_on")
            } else {
                FgsController.stop(this, Logger.Source.APP, "user_switch_off")
            }
            tvStatus.postDelayed({ if (!isDestroyed) reload() }, 500)
        }

        findViewById<Button>(R.id.btnA11ySettings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Không mở được Cài đặt Trợ năng", Toast.LENGTH_SHORT).show()
            }
        }

        etThreshold.setText(Prefs.gapThresholdSec(this).toString())
        findViewById<Button>(R.id.btnApplyThreshold).setOnClickListener {
            val v = etThreshold.text.toString().trim().toIntOrNull()
            if (v == null || v < 1) {
                Toast.makeText(this, "Ngưỡng phải là số giây ≥ 1", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setGapThresholdSec(this, v)
                reload()
            }
        }

        cbImportantOnly.isChecked = Prefs.isImportantOnly(this)
        cbImportantOnly.setOnCheckedChangeListener { _, checked ->
            Prefs.setImportantOnly(this, checked)
            reload()
        }

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { reload() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyLog() }

        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        bg.shutdownNow()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- hiển thị

    private fun statusText(): String {
        val notif = if (Build.VERSION.SDK_INT < 33) "không cần" else if (
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) "đã cấp" else "CHƯA cấp"
        val fgsPref = Prefs.isFgsEnabled(this)
        val fgsRun = BackgroundFgsService.isRunning
        return buildString {
            append("Trợ năng: ").append(StatusSnapshot.a11yText(this@MainActivity)).append('\n')
            append("FGS: công tắc=").append(if (fgsPref) "BẬT" else "TẮT")
            append(", đang chạy=").append(if (fgsRun) "CÓ" else "KHÔNG")
            if (fgsPref != fgsRun) append("  ⚠ lệch")
            append('\n')
            append("Quyền thông báo: ").append(notif).append('\n')
            append("Phiên tiến trình: ").append(Logger.sessionId)
        }
    }

    /** Đọc lại file log và tính lại mọi thứ (không giữ trong bộ nhớ). */
    private fun reload() {
        tvStatus.text = statusText()
        if (tvLog.text.isNullOrEmpty()) tvLog.setText(R.string.loading)
        val thresholdSec = Prefs.gapThresholdSec(this)
        val importantOnly = Prefs.isImportantOnly(this)
        val appCtx = applicationContext
        bg.execute {
            val lines = Logger.readAllLines(appCtx)
            val now = System.currentTimeMillis()
            val gr = LogAnalyzer.findGaps(lines, thresholdSec * 1000L, now)
            val tail = LogAnalyzer.lastLines(lines, importantOnly, 20)

            val countText = "Số khoảng đứt > ${thresholdSec}s: ${gr.closed.size}" +
                (if (gr.ongoing != null) "  (+1 đang diễn ra)" else "") +
                "  · mốc A11Y: ${gr.markers}"
            val gapsText = buildString {
                gr.ongoing?.let {
                    append("ĐANG ĐỨT từ ").append(Logger.formatWall(it.startMs))
                    append(" (").append(LogAnalyzer.formatDuration(it.durationMs)).append(")\n")
                }
                if (gr.closed.isEmpty()) {
                    append("Không có khoảng đứt đã đóng.")
                } else {
                    if (gr.closed.size > MAX_GAPS_SHOWN) {
                        append("(chỉ hiện ${MAX_GAPS_SHOWN} khoảng gần nhất)\n")
                    }
                    gr.closed.takeLast(MAX_GAPS_SHOWN).asReversed().forEachIndexed { i, g ->
                        append(i + 1).append(". ")
                        append(Logger.formatWall(g.startMs)).append(" → ")
                        append(Logger.formatWall(g.endMs)).append("  ")
                        append(LogAnalyzer.formatDuration(g.durationMs)).append('\n')
                    }
                }
            }.trimEnd()
            val logText = if (tail.isEmpty()) "(log trống)" else tail.joinToString("\n")

            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                tvGapCount.text = countText
                tvGaps.text = gapsText
                tvLog.text = logText
            }
        }
    }

    // ---------------------------------------------------------------- chia sẻ / sao chép

    private fun shareLog() {
        val appCtx = applicationContext
        val thresholdSec = Prefs.gapThresholdSec(this)
        bg.execute {
            try {
                val dir = LogFileProvider.shareDir(appCtx).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                val name = "compat-test-v2-log-$stamp.txt"
                val f = File(dir, name)
                Logger.snapshotTo(appCtx, f)
                val lines = f.readLines(Charsets.UTF_8).filter { it.isNotEmpty() }
                val gr = LogAnalyzer.findGaps(lines, thresholdSec * 1000L, System.currentTimeMillis())
                val summary = "Compat Test v2 – ${Build.MANUFACTURER} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})\n" +
                    "Số dòng log: ${lines.size}; khoảng đứt A11Y > ${thresholdSec}s: ${gr.closed.size}" +
                    (if (gr.ongoing != null) " (+1 đang diễn ra)" else "") + "\n" +
                    "File đầy đủ đính kèm: $name\n\n"
                val body = lines.joinToString("\n")
                val text = summary + if (body.length > MAX_SHARE_TEXT_CHARS) {
                    "[… chỉ kèm ${MAX_SHARE_TEXT_CHARS} ký tự cuối trong nội dung tin; xem file đính kèm …]\n" +
                        body.takeLast(MAX_SHARE_TEXT_CHARS)
                } else body

                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    val uri = LogFileProvider.uriFor(appCtx, name)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, name)
                        putExtra(Intent.EXTRA_TEXT, text)
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = ClipData.newRawUri(name, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        startActivity(Intent.createChooser(send, getString(R.string.share_chooser)))
                        Logger.log(Logger.Source.APP, "LOG_SHARE", "result=OK file=$name bytes=${f.length()}")
                    } catch (e: Exception) {
                        Logger.log(Logger.Source.APP, "LOG_SHARE", "result=FAIL error=${e.javaClass.name}")
                        Toast.makeText(this, "Không chia sẻ được: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Logger.log(Logger.Source.APP, "LOG_SHARE", "result=FAIL error=${e.javaClass.name} msg=${e.message ?: "N/A"}")
                runOnUiThread {
                    if (!isDestroyed) Toast.makeText(this, "Lỗi chuẩn bị log: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun copyLog() {
        val appCtx = applicationContext
        bg.execute {
            val body = Logger.readAllLines(appCtx).joinToString("\n")
            val truncated = body.length > MAX_CLIP_CHARS
            val text = if (truncated) {
                "[… log dài ${body.length} ký tự, chỉ sao chép ${MAX_CLIP_CHARS} ký tự cuối; dùng \"Chia sẻ log\" để lấy đủ …]\n" +
                    body.takeLast(MAX_CLIP_CHARS)
            } else body
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                try {
                    val cm = getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("compat-test-v2 log", text))
                    Logger.log(Logger.Source.APP, "LOG_COPY", "result=OK chars=${text.length} truncated=$truncated")
                    Toast.makeText(
                        this,
                        if (truncated) "Đã sao chép phần cuối log" else "Đã sao chép log",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Logger.log(Logger.Source.APP, "LOG_COPY", "result=FAIL error=${e.javaClass.name}")
                    Toast.makeText(this, "Không sao chép được: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---------------------------------------------------------------- quyền thông báo

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Logger.log(Logger.Source.APP, "NOTIF_PERMISSION_REQUEST")
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIF) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Logger.log(Logger.Source.APP, "NOTIF_PERMISSION_RESULT", "granted=$granted")
            tvStatus.text = statusText()
        }
    }
}
