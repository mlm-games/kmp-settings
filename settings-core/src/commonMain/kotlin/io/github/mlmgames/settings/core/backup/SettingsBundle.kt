package io.github.mlmgames.settings.core.backup

import kotlinx.serialization.Serializable

@Serializable
data class SettingsBundle(
    val formatVersion: Int = LEGACY_FORMAT_VERSION,
    val schemaVersion: Int,
    val appId: String,
    val exportedAt: Long,
    val deviceInfo: DeviceInfo? = null,
    val settings: Map<String, String>,
    val checksum: String,
    val quarantinedSettings: Map<String, String> = emptyMap(),
) {
    constructor(
        formatVersion: Int,
        schemaVersion: Int,
        appId: String,
        exportedAt: Long,
        deviceInfo: DeviceInfo?,
        settings: Map<String, String>,
        checksum: String,
    ) : this(
        formatVersion = formatVersion,
        schemaVersion = schemaVersion,
        appId = appId,
        exportedAt = exportedAt,
        deviceInfo = deviceInfo,
        settings = settings,
        checksum = checksum,
        quarantinedSettings = emptyMap(),
    )

    companion object {
        const val LEGACY_FORMAT_VERSION = 0
        const val LEGACY_HASH_FORMAT_VERSION = 1
        const val CURRENT_FORMAT_VERSION = 2
    }
}

@Serializable
data class DeviceInfo(
    val platform: String,
    val osVersion: String,
    val appVersion: String,
    val deviceModel: String? = null,
    val locale: String? = null,
)
