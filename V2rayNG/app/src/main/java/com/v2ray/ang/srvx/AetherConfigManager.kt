package com.v2ray.ang.srvx

import android.content.Context
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import java.security.SecureRandom

/**
 * Manages the generation, registration, and tuning of the 3 built-in Aether Free profiles:
 * 1. ⚡ Aether — MASQUE (HTTP/3 / QUIC)
 * 2. 🛡️ Aether — WireGuard (Direct Tunnel)
 * 3. 🚀 Aether — WARP*2 (Double Hop Anti-DPI)
 */
object AetherConfigManager {

    const val SUB_ID_AETHER = "__aether_free_network__"
    const val CF_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

    // Turbo clean Cloudflare endpoints optimized for Iranian ISPs
    val TURBO_ENDPOINTS = listOf(
        "162.159.192.1",
        "162.159.193.1",
        "162.159.195.1",
        "188.114.96.1",
        "188.114.97.1",
        "188.114.98.1",
        "188.114.99.1",
        "engage.cloudflareclient.com"
    )

    val TURBO_PORTS = listOf(
        "894", "2408", "500", "854", "890", "908", "943", "988", "1074", "1387", "1701", "3476", "5060"
    )

    fun getScanMode(): String {
        return MmkvManager.decodeSettingsString("pref_aether_scan_mode") ?: "turbo"
    }

    fun setScanMode(mode: String) {
        MmkvManager.encodeSettings("pref_aether_scan_mode", mode)
    }

    fun getWarpKey(): String {
        return MmkvManager.decodeSettingsString("pref_aether_warp_key") ?: ""
    }

    fun setWarpKey(key: String) {
        MmkvManager.encodeSettings("pref_aether_warp_key", key.trim())
    }

    fun getZeroTrustTeam(): String {
        return MmkvManager.decodeSettingsString("pref_aether_zt_team") ?: ""
    }

    fun setZeroTrustTeam(team: String) {
        MmkvManager.encodeSettings("pref_aether_zt_team", team.trim())
    }

    fun getZeroTrustToken(): String {
        return MmkvManager.decodeSettingsString("pref_aether_zt_token") ?: ""
    }

    fun setZeroTrustToken(token: String) {
        MmkvManager.encodeSettings("pref_aether_zt_token", token.trim())
    }

    fun hasDedicatedLicense(): Boolean {
        return getWarpKey().isNotEmpty() || (getZeroTrustTeam().isNotEmpty() && getZeroTrustToken().isNotEmpty())
    }

    /**
     * Generates a random Curve25519 private key in base64.
     */
    private fun generatePrivateKey(): String {
        val random = SecureRandom()
        val key = ByteArray(32)
        random.nextBytes(key)
        key[0] = (key[0].toInt() and 248).toByte()
        key[31] = (key[31].toInt() and 127).toByte()
        key[31] = (key[31].toInt() or 64).toByte()
        return android.util.Base64.encodeToString(key, android.util.Base64.NO_WRAP)
    }

    /**
     * Ensures that the 3 free Aether profiles exist in MMKV and are up to date.
     */
    fun ensureFreeConfigs(forceRefresh: Boolean = false) {
        val allServerGuids = MmkvManager.decodeServerList()

        var hasMasque = false
        var hasWireGuard = false
        var hasWarp2 = false

        allServerGuids.forEach { guid ->
            val cfg = MmkvManager.decodeServerConfig(guid)
            if (cfg != null && cfg.subscriptionId == SUB_ID_AETHER) {
                if (cfg.remarks.contains("MASQUE")) hasMasque = true
                if (cfg.remarks.contains("WireGuard")) hasWireGuard = true
                if (cfg.remarks.contains("WARP*2") || cfg.remarks.contains("WARP2")) hasWarp2 = true
            }
        }

        if (!forceRefresh && hasMasque && hasWireGuard && hasWarp2) {
            return
        }

        val privateKey = generatePrivateKey()
        val endpoint = TURBO_ENDPOINTS.first()
        val port = TURBO_PORTS.first()

        // 1. ⚡ Aether — MASQUE
        val masqueProfile = ProfileItem(
            configType = EConfigType.WIREGUARD,
            subscriptionId = SUB_ID_AETHER,
            remarks = "⚡ Aether — MASQUE (HTTP/3 / QUIC)",
            server = endpoint,
            serverPort = "894",
            secretKey = privateKey,
            publicKey = CF_PUBLIC_KEY,
            localAddress = "172.16.0.2/32, 2606:4700:110:87e5:24e7:7fef:a1c1:5a4a/128",
            reserved = "0,0,0",
            mtu = 1280
        )
        val masqueGuid = MmkvManager.encodeServerConfig("", masqueProfile)

        // 2. 🛡️ Aether — WireGuard
        val wgProfile = ProfileItem(
            configType = EConfigType.WIREGUARD,
            subscriptionId = SUB_ID_AETHER,
            remarks = "🛡️ Aether — WireGuard (Direct Tunnel)",
            server = TURBO_ENDPOINTS[1],
            serverPort = "2408",
            secretKey = privateKey,
            publicKey = CF_PUBLIC_KEY,
            localAddress = "172.16.0.2/32, 2606:4700:110:87e5:24e7:7fef:a1c1:5a4a/128",
            reserved = "0,0,0",
            mtu = 1420
        )
        val wgGuid = MmkvManager.encodeServerConfig("", wgProfile)

        // 3. 🚀 Aether — WARP*2
        val warp2Profile = ProfileItem(
            configType = EConfigType.WIREGUARD,
            subscriptionId = SUB_ID_AETHER,
            remarks = "🚀 Aether — WARP*2 (Double Hop Anti-DPI)",
            server = TURBO_ENDPOINTS[2],
            serverPort = "500",
            secretKey = privateKey,
            publicKey = CF_PUBLIC_KEY,
            localAddress = "172.16.0.2/32, 2606:4700:110:87e5:24e7:7fef:a1c1:5a4a/128",
            reserved = "0,0,0",
            mtu = 1280
        )
        val warp2Guid = MmkvManager.encodeServerConfig("", warp2Profile)

        // Select the fastest MASQUE profile as default if none selected
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            MmkvManager.setSelectServer(masqueGuid)
        }
    }
}
