package com.v2ray.ang.srvx

import android.content.Context
import com.v2ray.ang.handler.AngConfigManager

/**
 * Fetches the logged-in user's configs from the backend and imports them into
 * v2rayNG's default server list (subid = "") so they show in the main list.
 * append = false replaces previous default-group imports each sync.
 */
object SrvxSync {
    fun syncNow(ctx: Context): SyncResult {
        val token = SrvxSession.token(ctx) ?: return SyncResult(false, "لاگین نشده", null)
        val data = SrvxApi.fetchConfigs(token)
            ?: return SyncResult(false, "دریافت کانفیگ ناموفق", null)
        if (data.configs.isEmpty())
            return SyncResult(false, "کانفیگی برای این کاربر یافت نشد", data)

        val serverText = data.configs.joinToString("\n") { it.link }
        val count = try {
            val res = AngConfigManager.importBatchConfig(serverText, "", false)
            res.first + res.second
        } catch (e: Throwable) {
            return SyncResult(false, "import ناموفق: ${e.message}", data)
        }
        return SyncResult(true, "بروزرسانی شد ($count کانفیگ)", data)
    }

    data class SyncResult(val ok: Boolean, val message: String, val data: SrvxApi.UserConfigs?)
}
