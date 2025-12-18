package io.github.mlmgames.settings.core.backup

import kotlinx.serialization.Serializable

@Serializable
data class SettingsBundle(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val schemaVersion: Int,
    val appId: String,
    val exportedAt: Long,
    val deviceInfo: DeviceInfo? = null,
    val settings: Map<String, String>,
    val checksum: String,
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class DeviceInfo(
    val platform: String,
    val osVersion: String,
    val appVersion: String,
)