package com.v2ray.ang.srvx

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.v2ray.ang.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fills the remaining-data + remaining-time bar on the main screen from the
 * backend's /api/configs (which returns live usage pulled from the panel).
 * Safe: never throws, hides the bar on any failure.
 */
object SrvxStatus {

    fun refresh(activity: Activity) {
        val bar = activity.findViewById<LinearLayout>(R.id.srvx_status) ?: return
        val dataView = activity.findViewById<TextView>(R.id.srvx_data_value) ?: return
        val timeView = activity.findViewById<TextView>(R.id.srvx_time_value) ?: return
        val token = SrvxSession.token(activity) ?: return

        CoroutineScope(Dispatchers.Main).launch {
            val info = withContext(Dispatchers.IO) {
                try { SrvxApi.fetchUsage(token) } catch (e: Throwable) { null }
            } ?: return@launch

            dataView.text = if (info.totalBytes <= 0L) "نامحدود"
                            else formatBytes(info.remainingBytes)
            timeView.text = if (info.expiryTs <= 0L) "نامحدود"
                            else formatRemainingDays(info.expiryTs)
            bar.visibility = View.VISIBLE
        }
    }

    private fun formatBytes(b: Long): String {
        if (b <= 0) return "0"
        val gb = b / (1024.0 * 1024.0 * 1024.0)
        if (gb >= 1.0) return String.format("%.1f GB", gb)
        val mb = b / (1024.0 * 1024.0)
        return String.format("%.0f MB", mb)
    }

    private fun formatRemainingDays(expiryTs: Long): String {
        val ms = expiryTs - System.currentTimeMillis()
        if (ms <= 0) return "منقضی"
        val days = ms / 86400000L
        if (days >= 1) return "$days روز"
        val hours = ms / 3600000L
        return "$hours ساعت"
    }
}
