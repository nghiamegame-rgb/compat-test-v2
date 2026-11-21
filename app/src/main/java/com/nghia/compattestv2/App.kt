package com.nghia.compattestv2

import android.app.Application
import android.os.Build
import android.os.Process
import java.security.SecureRandom
import java.time.ZoneId

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Mã phiên tiến trình: sinh ngẫu nhiên mỗi lần process khởi động.
        val bytes = ByteArray(4).also { SecureRandom().nextBytes(it) }
        val session = bytes.joinToString("") { "%02x".format(it) }

        Logger.init(this, session)
        Logger.log(
            Logger.Source.APP, "PROCESS_START",
            "process=${na { getProcessName() }} uid=${Process.myUid()} " +
                "version=${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE}) " +
                "device=${Build.MANUFACTURER}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT} " +
                "release=${Build.VERSION.RELEASE} display=${Build.DISPLAY} " +
                "tz=${na { ZoneId.systemDefault().id }}"
        )
        ExitReasonRecorder.recordNew(this)
    }

    private inline fun na(block: () -> String?): String =
        try {
            block() ?: "N/A"
        } catch (t: Throwable) {
            "N/A"
        }
}
