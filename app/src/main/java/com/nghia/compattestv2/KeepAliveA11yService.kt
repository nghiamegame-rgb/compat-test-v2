package com.nghia.compattestv2

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService: phát nhịp tim 60 giây (nguồn A11Y) độc lập với công tắc FGS,
 * ghi vòng đời dịch vụ và ACTION_SCREEN_ON/OFF (đăng ký động, hủy ở onDestroy).
 * Không đọc nội dung cửa sổ, không chụp màn hình.
 */
class KeepAliveA11yService : AccessibilityService() {

    private var heartbeat: Heartbeat? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Logger.log(Logger.Source.A11Y, "A11Y_CONNECTED", StatusSnapshot.collect(this))
        registerScreenReceiver()
        if (heartbeat == null) {
            heartbeat = Heartbeat(this, Logger.Source.A11Y).also { it.start() }
        }
        // Khôi phục FGS nếu công tắc đang bật mà FGS không chạy (vd. sau khi process bị giết).
        if (Prefs.isFgsEnabled(this) && !BackgroundFgsService.isRunning) {
            FgsController.start(this, Logger.Source.A11Y, "restore_on_a11y_connected")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Cố ý không làm gì: khung nền chưa có xử lý nội dung.
    }

    override fun onInterrupt() {
        Logger.log(Logger.Source.A11Y, "A11Y_INTERRUPT")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.log(Logger.Source.A11Y, "A11Y_UNBIND", "action=${intent?.action ?: "N/A"}")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        heartbeat?.stop()
        heartbeat = null
        Logger.log(Logger.Source.A11Y, "A11Y_DESTROY")
        super.onDestroy()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON ->
                        Logger.log(Logger.Source.A11Y, "SCREEN_ON", StatusSnapshot.collect(this@KeepAliveA11yService))
                    Intent.ACTION_SCREEN_OFF ->
                        Logger.log(Logger.Source.A11Y, "SCREEN_OFF", StatusSnapshot.collect(this@KeepAliveA11yService))
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(receiver, filter)
            }
            screenReceiver = receiver
            Logger.log(Logger.Source.A11Y, "SCREEN_RECEIVER_REGISTER", "result=OK")
        } catch (e: Exception) {
            Logger.log(
                Logger.Source.A11Y, "SCREEN_RECEIVER_REGISTER",
                "result=FAIL error=${e.javaClass.name} msg=${e.message ?: "N/A"}"
            )
        }
    }

    private fun unregisterScreenReceiver() {
        val r = screenReceiver ?: return
        screenReceiver = null
        try {
            unregisterReceiver(r)
            Logger.log(Logger.Source.A11Y, "SCREEN_RECEIVER_UNREGISTER", "result=OK")
        } catch (e: Exception) {
            Logger.log(
                Logger.Source.A11Y, "SCREEN_RECEIVER_UNREGISTER",
                "result=FAIL error=${e.javaClass.name} msg=${e.message ?: "N/A"}"
            )
        }
    }
}
