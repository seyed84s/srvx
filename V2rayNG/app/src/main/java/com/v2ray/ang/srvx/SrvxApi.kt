package com.v2ray.ang.srvx

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SrvxApi {
    // Your backend domain (HTTPS)
    const val BASE_URL = "https://api.smcx.ir"

    data class ConfigItem(val country: String, val remark: String, val link: String)
    data class UserConfigs(
        val username: String,
        val totalGb: Double,
        val expiryTs: Long,
        val configs: List<ConfigItem>
    )

    private fun post(path: String, body: JSONObject, bearer: String? = null): Pair<Int, String> {
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        return code to text
    }

    private fun get(path: String, bearer: String): Pair<Int, String> {
        val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
            setRequestProperty("Authorization", "Bearer $bearer")
        }
        val code = conn.responseCode
        val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.readText() ?: ""
        conn.disconnect()
        return code to text
    }

    fun login(username: String, password: String): String? {
        val (code, text) = post("/api/login",
            JSONObject().put("username", username).put("password", password))
        if (code !in 200..299) return null
        return JSONObject(text).optString("token").ifEmpty { null }
    }

    fun fetchConfigs(token: String): UserConfigs? {
        val (code, text) = get("/api/configs", token)
        if (code !in 200..299) return null
        val o = JSONObject(text)
        val arr = o.getJSONArray("configs")
        val items = ArrayList<ConfigItem>()
        for (i in 0 until arr.length()) {
            val c = arr.getJSONObject(i)
            items.add(ConfigItem(c.optString("country"), c.optString("remark"), c.optString("link")))
        }
        return UserConfigs(
            o.optString("username"),
            o.optDouble("total_gb", 0.0),
            o.optLong("expiry_ts", 0L),
            items
        )
    }
}

object SrvxSession {
    private const val PREF = "srvx_session"
    fun save(ctx: Context, token: String, username: String) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString("token", token).putString("username", username).apply()
    }
    fun token(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("token", null)
    fun username(ctx: Context): String? =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("username", null)
    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
