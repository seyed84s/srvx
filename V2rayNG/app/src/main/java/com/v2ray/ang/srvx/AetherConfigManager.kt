package com.v2ray.ang.srvx

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the generation, registration, and tuning of the 3 built-in Aether Free profiles:
 * 1. ⚡ Aether — MASQUE (HTTP/3 / QUIC)
 * 2. 🛡️ Aether — WireGuard (Direct Tunnel)
 * 3. 🚀 Aether — WARP*2 (Double Hop Anti-DPI)
 *
 * Uses genuine Cloudflare WARP API keys.
 * WARP*2 uses Proxy Chaining.
 */
object AetherConfigManager {

    const val CF_PUBLIC_KEY = "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo="

    // Clean Cloudflare endpoints for Iranian ISPs
    private val ENDPOINTS = listOf(
        "162.159.192.1",   // 0
        "162.159.193.1",   // 1
        "162.159.195.1",   // 2
        "188.114.96.1",    // 3
        "188.114.97.1",    // 4
        "188.114.98.1",    // 5
        "188.114.99.1",    // 6
        "engage.cloudflareclient.com" // 7
    )

    private val PORTS = listOf("894", "2408", "854", "908", "943", "1387", "500")

    private const val AETHER_REMARK_PREFIX = "Aether —"
    private const val PREF_HIDDEN_HOP1 = "pref_aether_hidden_hop1"

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
        return getWarpKey().isNotEmpty() ||
                (getZeroTrustTeam().isNotEmpty() && getZeroTrustToken().isNotEmpty())
    }

    /**
     * Ensures the 3 Aether profiles exist and are valid.
     * This is a SUSPEND function — it blocks until registration and profile creation are complete.
     */
    suspend fun ensureFreeConfigs(forceRefresh: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            buildProfilesInternal(forceRefresh)
        }
    }

    private fun buildProfilesInternal(forceRefresh: Boolean): Boolean {
        if (!forceRefresh && hasValidProfiles()) {
            return true
        }

        if (forceRefresh) {
            WarpRegistrar.clearAccount()
        }

        // Remove old visible and hidden profiles
        removeOldAetherProfiles()

        val account = WarpRegistrar.register() ?: return false
        val privateKey = account.privateKey
        val localAddr = "${account.localAddressV4}, ${account.localAddressV6}"
        val reserved = account.reserved

        // 1. ⚡ Aether — MASQUE (HTTP/3 / QUIC) -> Port 894, MTU 1280
        val p1 = ProfileItem.create(EConfigType.WIREGUARD).apply {
            subscriptionId = ""
            remarks = "⚡ $AETHER_REMARK_PREFIX MASQUE (HTTP/3 / QUIC)"
            server = ENDPOINTS[0]
            serverPort = "894" // Cloudflare HTTP/3 QUIC port
            secretKey = privateKey
            publicKey = CF_PUBLIC_KEY
            localAddress = localAddr
            this.reserved = reserved
            mtu = 1280
        }
        val guid1 = MmkvManager.encodeServerConfig("", p1)

        // 2. 🛡️ Aether — WireGuard (Direct Tunnel) -> Port 2408, MTU 1420
        val p2 = ProfileItem.create(EConfigType.WIREGUARD).apply {
            subscriptionId = ""
            remarks = "🛡️ $AETHER_REMARK_PREFIX WireGuard (Direct Tunnel)"
            server = ENDPOINTS[3]
            serverPort = "2408" // Standard WARP port
            secretKey = privateKey
            publicKey = CF_PUBLIC_KEY
            localAddress = localAddr
            this.reserved = reserved
            mtu = 1420 // Standard MTU for stable networks
        }
        MmkvManager.encodeServerConfig("", p2)

        // 3. 🚀 Aether — WARP*2 (Double Hop Anti-DPI) -> Proxy Chaining
        // Hop 1 (Hidden Profile)
        val hop1Guid = com.v2ray.ang.util.Utils.getUuid()
        val p3Hop1 = ProfileItem.create(EConfigType.WIREGUARD).apply {
            subscriptionId = ""
            remarks = "WARP Hop 1 (Hidden)"
            server = ENDPOINTS[7] // engage.cloudflareclient.com
            serverPort = "500"
            secretKey = privateKey
            publicKey = CF_PUBLIC_KEY
            localAddress = localAddr
            this.reserved = reserved
            mtu = 1280
        }
        // Save hidden profile directly to avoid showing it in the UI list
        MmkvManager.encodeProfileDirect(hop1Guid, com.v2ray.ang.util.JsonUtil.toJson(p3Hop1))
        MmkvManager.encodeSettings(PREF_HIDDEN_HOP1, hop1Guid)

        // Hop 2 (Visible Profile with Chain)
        val p3 = ProfileItem.create(EConfigType.WIREGUARD).apply {
            subscriptionId = ""
            remarks = "🚀 $AETHER_REMARK_PREFIX WARP*2 (Double Hop Anti-DPI)"
            server = ENDPOINTS[1]
            serverPort = "854"
            secretKey = privateKey
            publicKey = CF_PUBLIC_KEY
            localAddress = localAddr
            this.reserved = reserved
            mtu = 1280
            proxyChainProfiles = hop1Guid // CHAIN TO HOP 1
        }
        MmkvManager.encodeServerConfig("", p3)

        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            MmkvManager.setSelectServer(guid1)
        }

        return true
    }

    private fun hasValidProfiles(): Boolean {
        var count = 0
        MmkvManager.decodeAllServerList().forEach { guid ->
            val cfg = MmkvManager.decodeServerConfig(guid)
            if (cfg != null && cfg.remarks.contains(AETHER_REMARK_PREFIX)) {
                if (!cfg.secretKey.isNullOrEmpty() && !cfg.reserved.isNullOrEmpty() && cfg.reserved != "0,0,0") {
                    count++
                }
            }
        }
        return count >= 3
    }

    private fun removeOldAetherProfiles() {
        val toRemove = mutableListOf<String>()
        MmkvManager.decodeAllServerList().forEach { guid ->
            val cfg = MmkvManager.decodeServerConfig(guid)
            if (cfg != null && cfg.remarks.contains(AETHER_REMARK_PREFIX)) {
                toRemove.add(guid)
            }
        }
        toRemove.forEach { guid ->
            MmkvManager.removeServer(guid)
        }

        // Remove hidden Hop 1 if exists
        val oldHop1 = MmkvManager.decodeSettingsString(PREF_HIDDEN_HOP1)
        if (!oldHop1.isNullOrEmpty()) {
            MmkvManager.removeServer(oldHop1)
            MmkvManager.encodeSettings(PREF_HIDDEN_HOP1, "")
        }
    }
}
