package com.v2ray.ang.srvx

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SrvxSyncHook {
    fun run(ctx: Context, onDone: (() -> Unit)? = null) {
        CoroutineScope(Dispatchers.Main).launch {
            val res = withContext(Dispatchers.IO) { SrvxSync.syncNow(ctx) }
            Toast.makeText(ctx, res.message, Toast.LENGTH_SHORT).show()
            if (res.ok) onDone?.invoke()
        }
    }
}
