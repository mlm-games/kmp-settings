package io.github.mlmgames.settings.core.fields

import io.github.mlmgames.settings.core.SettingField

class ConfiguredSettingField<T, V>(
    private val delegate: SettingField<T, V>,
    override val isResettable: Boolean = delegate.isResettable,
    override val resetConfirmation: String? = delegate.resetConfirmation,
) : SettingField<T, V> by delegate
