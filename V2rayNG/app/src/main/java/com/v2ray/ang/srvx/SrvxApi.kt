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

    // Returns (code, body) or null on ANY network failure — never throws.
    private fun post(path: String, body: JSONObject, bearer: String? = null): Pair<Int, String>? {
        return try {
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
            code to text
        } catch (e: Throwable) {
            null
        }
    }

    private fun get(path: String, bearer: String): Pair<Int, String>? {
        return try {
            val conn = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 15000; readTimeout = 15000
                setRequestProperty("Authorization", "Bearer $bearer")
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            code to text
        } catch (e: Throwable) {
            null
        }
    }

    fun login(username: String, password: String): String? {
        return try {
            val res = post("/api/login",
                JSONObject().put("username", username).put("password", password)) ?: return null
            val (code, text) = res
            if (code !in 200..299) return null
            JSONObject(text).optString("token").ifEmpty { null }
        } catch (e: Throwable) {
            null
        }
    }

    fun fetchConfigs(token: String): UserConfigs? {
        return try {
            val res = get("/api/configs", token) ?: return null
            val (code, text) = res
            if (code !in 200..299) return null
            val o = JSONObject(text)
            val arr = o.getJSONArray("configs")
            val items = ArrayList<ConfigItem>()
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                items.add(ConfigItem(c.optString("country"), c.optString("remark"), c.optString("link")))
            }
            UserConfigs(
                o.optString("username"),
                o.optDouble("total_gb", 0.0),
                o.optLong("expiry_ts", 0L),
                items
            )
        } catch (e: Throwable) {
            null
        }
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
