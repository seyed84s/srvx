package com.v2ray.ang.srvx

import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.enums.EConfigType
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages the generation, registration, and tuning of the 3 built-in Aether Free profiles:
 * 1. ⚡ Aether — سرور ۱ (سریع)
 * 2. 🛡️ Aether — سرور ۲ (پایدار)
 * 3. 🚀 Aether — سرور ۳ (ذخیره)
 *
 * All profiles use Cloudflare WARP WireGuard with genuine registered keys.
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

    // Ports known to pass through Iranian DPI
    private val PORTS = listOf("894", "2408", "854", "908", "943", "1387", "500")

    private const val AETHER_REMARK_PREFIX = "Aether —"

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
     *
     * @return true if profiles were successfully created/verified, false if registration failed
     */
    suspend fun ensureFreeConfigs(forceRefresh: Boolean = false): Boolean {
        return withContext(Dispatchers.IO) {
            buildProfilesInternal(forceRefresh)
        }
    }

    /**
     * Internal: runs on IO thread.
     * 1. Removes any old Aether profiles
     * 2. Registers with Cloudflare WARP API (or uses cached account)
     * 3. Creates 3 fresh profiles in default server list
     */
    private fun buildProfilesInternal(forceRefresh: Boolean): Boolean {
        // Check if profiles already exist and are valid (skip re-creation if not forced)
        if (!forceRefresh && hasValidProfiles()) {
            return true
        }

        // If force refresh, clear cached WARP account so we get fresh keys
        if (forceRefresh) {
            WarpRegistrar.clearAccount()
        }

        // Step 1: Remove ALL old Aether profiles
        removeOldAetherProfiles()

        // Step 2: Register with Cloudflare WARP API
        val account = WarpRegistrar.register() ?: return false

        val privateKey = account.privateKey
        val localAddr = "${account.localAddressV4}, ${account.localAddressV6}"
        val reserved = account.reserved

        // Step 3: Create 3 profiles in the DEFAULT subscription (so they're visible in server list)
        // Profile 1: ⚡ Fast server
        val p1 = ProfileItem.create(EConfigType.WIREGUARD).apply {
            subscriptionId = ""  // DEFAULT group → always visible
            remarks = "⚡ $AETHER_REMARK_PREFIX سرور ۱ (سریع)"
            server = ENDPOINTS[0]
            serverPort = PORTS[0]
            secretKey = privateKey
            publicKey = CF_PUBLIC_KEY
            localAddress = localAddr
            this.reserved = reserved
            mtu = 1280
        }
        val guid1 = MmkvManager.encodeServerConfig("", p1)

        // Profile 2: 🛡️ Stable server
        val p2 = ProfileItem.create(EConfigType.WIREGUARD).apply {
            subscriptionId = ""
            remarks = "🛡️ $AETHER_REMARK_PREFIX سرور ۲ (پایدار)"
            server = ENDPOINTS[3]
            serverPort = PORTS[1]
            secretKey = privateKey
            publicKey = CF_PUBLIC_KEY
            localAddress = localAddr
            this.reserved = reserved
            mtu = 1280
        }
        MmkvManager.encodeServerConfig("", p2)

        // Profile 3: 🚀 Backup server
        val p3 = ProfileItem.create(EConfigType.WIREGUARD).apply {
            subscriptionId = ""
            remarks = "🚀 $AETHER_REMARK_PREFIX سرور ۳ (ذخیره)"
            server = ENDPOINTS[1]
            serverPort = PORTS[2]
            secretKey = privateKey
            publicKey = CF_PUBLIC_KEY
            localAddress = localAddr
            this.reserved = reserved
            mtu = 1280
        }
        MmkvManager.encodeServerConfig("", p3)

        // Auto-select the first profile if nothing is selected
        if (MmkvManager.getSelectServer().isNullOrEmpty()) {
            MmkvManager.setSelectServer(guid1)
        }

        return true
    }

    /**
     * Checks if 3 valid Aether profiles already exist.
     */
    private fun hasValidProfiles(): Boolean {
        var count = 0
        MmkvManager.decodeAllServerList().forEach { guid ->
            val cfg = MmkvManager.decodeServerConfig(guid)
            if (cfg != null && cfg.remarks.contains(AETHER_REMARK_PREFIX)) {
                // Also verify keys are present
                if (!cfg.secretKey.isNullOrEmpty() && !cfg.reserved.isNullOrEmpty() && cfg.reserved != "0,0,0") {
                    count++
                }
            }
        }
        return count >= 3
    }

    /**
     * Removes all existing Aether profiles from MMKV.
     */
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
    }
}
