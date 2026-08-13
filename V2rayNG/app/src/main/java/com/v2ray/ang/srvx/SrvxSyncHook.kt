package com.v2ray.ang.srvx

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SrvxSyncHook {
    // Sync only once per app process, so reconnecting VPN / re-entering
    // MainActivity doesn't fire network calls again (which crashed on the
    // main network stack while VPN was rerouting).
    @Volatile
    private var alreadySynced = false

    fun run(ctx: Context, onDone: (() -> Unit)? = null) {
        if (alreadySynced) return
        alreadySynced = true
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    try { SrvxSync.syncNow(ctx) }
                    catch (e: Throwable) { SrvxSync.SyncResult(false, "خطای شبکه", null) }
                }
                Toast.makeText(ctx, res.message, Toast.LENGTH_SHORT).show()
                if (res.ok) onDone?.invoke()
            } catch (e: Throwable) {
                // never crash the app because of sync
            }
        }
    }
}
