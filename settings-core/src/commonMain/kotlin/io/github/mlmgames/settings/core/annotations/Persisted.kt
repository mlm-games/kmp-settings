package io.github.mlmgames.settings.core.annotations

/**
 * Marks a property as persisted but NOT shown in UI.
 *
 * Use for internal state like timestamps, counters, cached values.
 *
 * Usage:
 * ```
 * @Persisted
 * val lastSyncTime: Long = 0L
 *
 * @Persisted(key = "hidden_apps")
 * val hiddenApps: Set<String> = emptySet()
 * ```
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Persisted(
    /** Stable persistence key. If empty, defaults to snake_case of property name. */
    val key: String = ""
)