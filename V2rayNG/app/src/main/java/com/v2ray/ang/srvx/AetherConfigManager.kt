package com.v2ray.ang.srvx

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the generation of the 3 built-in Aether Free profiles.
 * Since we are now using the native Aether Rust Engine (libaether.so),
 * these profiles are simple SOCKS5 clients pointing to the engine's local proxy (127.0.0.1:1819).
 * The engine itself handles MASQUE, WireGuard, and WARP-on-WARP connections.
 */
object AetherConfigManager {

    const val AETHER_REMARK_PREFIX = "Aether —"

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

    suspend fun ensureFreeConfigs(forceRefresh: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            buildProfilesInternal(forceRefresh)
        }
    }

    private fun buildProfilesInternal(forceRefresh: Boolean): Boolean {
        if (!forceRefresh && hasValidProfiles()) {
            return true
        }

        // Remove old profiles
        removeOldAetherProfiles()

        // 1. ⚡ Aether — MASQUE (HTTP/3 / QUIC)
        val p1 = ProfileItem.create(EConfigType.SOCKS).apply {
            subscriptionId = ""
            remarks = "⚡ $AETHER_REMARK_PREFIX MASQUE (HTTP/3 / QUIC)"
            server = "127.0.0.1"
            serverPort = AetherEngine.SOCKS_PORT.toString()
        }
        val guid1 = MmkvManager.encodeServerConfig("", p1)

        // 2. 🛡️ Aether — WireGuard (Direct Tunnel)
        val p2 = ProfileItem.create(EConfigType.SOCKS).apply {
            subscriptionId = ""
            remarks = "🛡️ $AETHER_REMARK_PREFIX WireGuard (Direct Tunnel)"
            server = "127.0.0.1"
            serverPort = AetherEngine.SOCKS_PORT.toString()
        }
        MmkvManager.encodeServerConfig("", p2)

        // 3. 🚀 Aether — WARP*2 (Double Hop Anti-DPI)
        val p3 = ProfileItem.create(EConfigType.SOCKS).apply {
            subscriptionId = ""
            remarks = "🚀 $AETHER_REMARK_PREFIX WARP*2 (Double Hop Anti-DPI)"
            server = "127.0.0.1"
            serverPort = AetherEngine.SOCKS_PORT.toString()
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
                // Ensure it's pointing to our engine's port
                if (cfg.serverPort == AetherEngine.SOCKS_PORT.toString()) {
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

        // Clean up hidden hop from previous proxy chaining implementation if it exists
        val oldHop1 = MmkvManager.decodeSettingsString("pref_aether_hidden_hop1")
        if (!oldHop1.isNullOrEmpty()) {
            MmkvManager.removeServer(oldHop1)
            MmkvManager.encodeSettings("pref_aether_hidden_hop1", "")
        }
    }
}
