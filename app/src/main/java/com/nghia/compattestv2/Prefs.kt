package com.nghia.compattestv2

import android.content.Context

object Prefs {
    private const val NAME = "settings"
    private const val KEY_FGS_ENABLED = "fgs_enabled"
    private const val KEY_GAP_THRESHOLD_SEC = "gap_threshold_sec"
    private const val KEY_IMPORTANT_ONLY = "important_only"

    const val DEFAULT_GAP_THRESHOLD_SEC = 120

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun isFgsEnabled(c: Context) = sp(c).getBoolean(KEY_FGS_ENABLED, false)
    fun setFgsEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean(KEY_FGS_ENABLED, v).apply()
    }

    fun gapThresholdSec(c: Context) = sp(c).getInt(KEY_GAP_THRESHOLD_SEC, DEFAULT_GAP_THRESHOLD_SEC)
    fun setGapThresholdSec(c: Context, v: Int) {
        sp(c).edit().putInt(KEY_GAP_THRESHOLD_SEC, v).apply()
    }

    fun isImportantOnly(c: Context) = sp(c).getBoolean(KEY_IMPORTANT_ONLY, false)
    fun setImportantOnly(c: Context, v: Boolean) {
        sp(c).edit().putBoolean(KEY_IMPORTANT_ONLY, v).apply()
    }
}
