package com.v2ray.ang.srvx

import android.util.Base64
import com.v2ray.ang.handler.MmkvManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class WarpAccount(
    val privateKey: String,
    val publicKey: String,
    val localAddressV4: String,
    val localAddressV6: String,
    val reserved: String,
    val token: String,
    val id: String
)

object WarpRegistrar {

    private const val PREF_WARP_PRIV_KEY = "pref_warp_priv_key"
    private const val PREF_WARP_PUB_KEY = "pref_warp_pub_key"
    private const val PREF_WARP_V4 = "pref_warp_v4"
    private const val PREF_WARP_V6 = "pref_warp_v6"
    private const val PREF_WARP_RESERVED = "pref_warp_reserved"
    private const val PREF_WARP_TOKEN = "pref_warp_token"
    private const val PREF_WARP_ID = "pref_warp_id"

    // Default fallback account if offline
    private val DEFAULT_FALLBACK = WarpAccount(
        privateKey = "aA==",
        publicKey = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
        localAddressV4 = "172.16.0.2/32",
        localAddressV6 = "2606:4700:110:87e5:24e7:7fef:a1c1:5a4a/128",
        reserved = "0,0,0",
        token = "",
        id = ""
    )

    fun getSavedAccount(): WarpAccount? {
        val priv = MmkvManager.decodeSettingsString(PREF_WARP_PRIV_KEY) ?: return null
        val pub = MmkvManager.decodeSettingsString(PREF_WARP_PUB_KEY) ?: return null
        val v4 = MmkvManager.decodeSettingsString(PREF_WARP_V4) ?: "172.16.0.2/32"
        val v6 = MmkvManager.decodeSettingsString(PREF_WARP_V6) ?: "2606:4700:110:87e5:24e7:7fef:a1c1:5a4a/128"
        val res = MmkvManager.decodeSettingsString(PREF_WARP_RESERVED) ?: "0,0,0"
        val tok = MmkvManager.decodeSettingsString(PREF_WARP_TOKEN) ?: ""
        val id = MmkvManager.decodeSettingsString(PREF_WARP_ID) ?: ""
        return WarpAccount(priv, pub, v4, v6, res, tok, id)
    }

    fun saveAccount(acc: WarpAccount) {
        MmkvManager.encodeSettings(PREF_WARP_PRIV_KEY, acc.privateKey)
        MmkvManager.encodeSettings(PREF_WARP_PUB_KEY, acc.publicKey)
        MmkvManager.encodeSettings(PREF_WARP_V4, acc.localAddressV4)
        MmkvManager.encodeSettings(PREF_WARP_V6, acc.localAddressV6)
        MmkvManager.encodeSettings(PREF_WARP_RESERVED, acc.reserved)
        MmkvManager.encodeSettings(PREF_WARP_TOKEN, acc.token)
        MmkvManager.encodeSettings(PREF_WARP_ID, acc.id)
    }

    /**
     * Registers a genuine Cloudflare WARP account via the public registration API.
     */
    fun register(): WarpAccount {
        // Return existing cached valid account if available
        getSavedAccount()?.let { return it }

        return try {
            val privBytes = Curve25519.generatePrivateKey()
            val pubBytes = Curve25519.eval(privBytes)
            val privBase64 = Base64.encodeToString(privBytes, Base64.NO_WRAP)
            val pubBase64 = Base64.encodeToString(pubBytes, Base64.NO_WRAP)

            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val nowIso = sdf.format(Date())

            val body = JSONObject().apply {
                put("install_id", "")
                put("tos", nowIso)
                put("key", pubBase64)
                put("fcm_token", "")
                put("type", "Android")
                put("locale", "en_US")
            }

            val conn = (URL("https://api.cloudflareclient.com/v0a2158/reg").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("User-Agent", "okhttp/3.12.1")
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val responseText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (code in 200..299 && responseText.isNotEmpty()) {
                val json = JSONObject(responseText)
                val id = json.optString("id")
                val token = json.optString("token")
                val config = json.optJSONObject("config")
                val iface = config?.optJSONObject("interface")
                val addrs = iface?.optJSONObject("addresses")
                val v4 = addrs?.optString("v4", "172.16.0.2/32") ?: "172.16.0.2/32"
                val v6 = addrs?.optString("v6", "2606:4700:110:87e5:24e7:7fef:a1c1:5a4a/128") ?: "2606:4700:110:87e5:24e7:7fef:a1c1:5a4a/128"

                // Extract client_id reserved bytes
                val reserved = parseReservedFromId(id)

                val account = WarpAccount(
                    privateKey = privBase64,
                    publicKey = pubBase64,
                    localAddressV4 = v4,
                    localAddressV6 = v6,
                    reserved = reserved,
                    token = token,
                    id = id
                )
                saveAccount(account)
                account
            } else {
                DEFAULT_FALLBACK.copy(privateKey = privBase64, publicKey = pubBase64)
            }
        } catch (e: Throwable) {
            val privBytes = Curve25519.generatePrivateKey()
            val pubBytes = Curve25519.eval(privBytes)
            DEFAULT_FALLBACK.copy(
                privateKey = Base64.encodeToString(privBytes, Base64.NO_WRAP),
                publicKey = Base64.encodeToString(pubBytes, Base64.NO_WRAP)
            )
        }
    }

    private fun parseReservedFromId(id: String): String {
        return try {
            val cleanId = id.replace("-", "")
            if (cleanId.length >= 6) {
                val r1 = cleanId.substring(0, 2).toInt(16)
                val r2 = cleanId.substring(2, 4).toInt(16)
                val r3 = cleanId.substring(4, 6).toInt(16)
                "$r1,$r2,$r3"
            } else {
                "0,0,0"
            }
        } catch (_: Throwable) {
            "0,0,0"
        }
    }
}
