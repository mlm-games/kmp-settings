package io.github.mlmgames.settings.core.resources

import android.content.Context
import io.github.mlmgames.settings.core.R

class AndroidStringResourceProvider(
    private val context: Context,
    private val keyResolver: ((String) -> Int)?,
) : StringResourceProvider {
    constructor(context: Context) : this(context, null)
    override fun getString(resId: Int): String =
        if (resId != 0) context.getString(resId) else ""

    override fun getString(resId: Int, vararg formatArgs: Any): String =
        if (resId != 0) context.getString(resId, *formatArgs) else ""

    override fun getStringArray(resId: Int): List<String> =
        if (resId != 0) context.resources.getStringArray(resId).toList() else emptyList()

    override fun getString(key: String): String = getString(resolveKey(key))

    override fun getString(key: String, vararg formatArgs: Any): String =
        getString(resolveKey(key), *formatArgs)

    override fun getStringArray(key: String): List<String> = getStringArray(resolveKey(key))

    private fun resolveKey(key: String): Int = keyResolver?.invoke(key)?.takeIf { it != 0 }
        ?: resolveBuiltinKey(key)

    companion object {
        private fun resolveBuiltinKey(key: String): Int = when (key) {
            SettingsTextKeys.UNKNOWN -> R.string.settings_unknown
            SettingsTextKeys.NOT_SET -> R.string.settings_not_set
            SettingsTextKeys.EMPTY -> R.string.settings_empty
            SettingsTextKeys.CLEAR -> R.string.settings_clear
            SettingsTextKeys.CANCEL -> R.string.settings_cancel
            SettingsTextKeys.CONFIRM -> R.string.settings_confirm
            SettingsTextKeys.OK -> R.string.settings_ok
            SettingsTextKeys.APPLY -> R.string.settings_apply
            SettingsTextKeys.SELECT -> R.string.settings_select
            SettingsTextKeys.RUN -> R.string.settings_run
            SettingsTextKeys.RUN_ACTION -> R.string.settings_run_action
            SettingsTextKeys.RUN_ACTION_MESSAGE -> R.string.settings_run_action_message
            SettingsTextKeys.RESET -> R.string.settings_reset
            SettingsTextKeys.DONE -> R.string.settings_done
            SettingsTextKeys.SHARE -> R.string.settings_share
            SettingsTextKeys.CLOSE -> R.string.settings_close
            SettingsTextKeys.AM -> R.string.settings_am
            SettingsTextKeys.PM -> R.string.settings_pm
            SettingsTextKeys.SETTING -> R.string.settings
            SettingsTextKeys.CATEGORY -> R.string.settings_category
            SettingsTextKeys.UNSUPPORTED_SETTING_TYPE -> R.string.settings_unsupported_type
            SettingsTextKeys.SETTING_UPDATE_FAILED -> R.string.settings_update_failed
            SettingsTextKeys.SETTING_DISABLED -> R.string.settings_disabled
            SettingsTextKeys.SETTING_CANNOT_BE_CLEARED -> R.string.settings_cannot_be_cleared
            SettingsTextKeys.ACTION_FAILED -> R.string.settings_action_failed
            SettingsTextKeys.ACTION_UNAVAILABLE -> R.string.settings_action_unavailable
            SettingsTextKeys.INVALID_SELECTION -> R.string.settings_invalid_selection
            SettingsTextKeys.INVALID_VALUE -> R.string.settings_invalid_value
            SettingsTextKeys.SCHEMA_CHANGED -> R.string.settings_schema_changed
            SettingsTextKeys.ACTION_NO_LONGER_AVAILABLE -> R.string.settings_action_no_longer_available
            SettingsTextKeys.SETTING_NO_LONGER_AVAILABLE -> R.string.settings_setting_no_longer_available
            SettingsTextKeys.VALIDATION_UNAVAILABLE -> R.string.settings_validation_unavailable
            SettingsTextKeys.VALUE_OUT_OF_RANGE -> R.string.settings_value_out_of_range
            SettingsTextKeys.INVALID_LENGTH -> R.string.settings_invalid_length
            SettingsTextKeys.INVALID_FORMAT -> R.string.settings_invalid_format
            SettingsTextKeys.REQUIRED -> R.string.settings_required
            SettingsTextKeys.CONFIRM_CHANGE -> R.string.settings_confirm_change
            SettingsTextKeys.CONFIRM_CHANGE_MESSAGE -> R.string.settings_confirm_change_message
            SettingsTextKeys.CONFIRM_RESET -> R.string.settings_confirm_reset
            SettingsTextKeys.CONFIRM_RESET_MESSAGE -> R.string.settings_confirm_reset_message
            SettingsTextKeys.RESET_SETTINGS -> R.string.settings_reset_settings
            SettingsTextKeys.CHOOSE_RESET -> R.string.settings_choose_reset
            SettingsTextKeys.SELECT_CATEGORY -> R.string.settings_select_category
            SettingsTextKeys.UI_ONLY -> R.string.settings_ui_only
            SettingsTextKeys.UI_ONLY_DESCRIPTION -> R.string.settings_ui_only_description
            SettingsTextKeys.CATEGORY_ONLY -> R.string.settings_category_only
            SettingsTextKeys.CATEGORY_ONLY_DESCRIPTION -> R.string.settings_category_only_description
            SettingsTextKeys.ALL_SETTINGS -> R.string.settings_all
            SettingsTextKeys.ALL_SETTINGS_DESCRIPTION -> R.string.settings_all_description
            SettingsTextKeys.RESET_FAILED -> R.string.settings_reset_failed
            SettingsTextKeys.RESET_PARTIAL -> R.string.settings_reset_partial
            SettingsTextKeys.RESET_UNEXPECTED -> R.string.settings_reset_unexpected
            SettingsTextKeys.RESET_COMPLETE -> R.string.settings_reset_complete
            SettingsTextKeys.RESET_CALLBACK_FAILED -> R.string.settings_reset_callback_failed
            SettingsTextKeys.NO_RESETTABLE_SETTINGS -> R.string.settings_no_resettable
            SettingsTextKeys.SET_PIN -> R.string.settings_set_pin
            SettingsTextKeys.ENTER_PIN -> R.string.settings_enter_pin
            SettingsTextKeys.PIN -> R.string.settings_pin
            SettingsTextKeys.CONFIRM_PIN -> R.string.settings_confirm_pin
            SettingsTextKeys.PIN_LENGTH -> R.string.settings_pin_length
            SettingsTextKeys.PIN_MATCH -> R.string.settings_pin_match
            SettingsTextKeys.SET_PIN_FAILED -> R.string.settings_set_pin_failed
            SettingsTextKeys.INVALID_PIN -> R.string.settings_invalid_pin
            SettingsTextKeys.PIN_OPERATION_FAILED -> R.string.settings_pin_operation_failed
            SettingsTextKeys.EXPORT_SETTINGS -> R.string.settings_export
            SettingsTextKeys.IMPORT_SETTINGS -> R.string.settings_import
            SettingsTextKeys.IMPORT_ACTION -> R.string.settings_import_action
            SettingsTextKeys.EXPORT_SUCCESS -> R.string.settings_export_success
            SettingsTextKeys.EXPORT_SIZE -> R.string.settings_export_size
            SettingsTextKeys.APPLIED -> R.string.settings_applied
            SettingsTextKeys.SKIPPED -> R.string.settings_skipped
            SettingsTextKeys.FAILED_COUNT -> R.string.settings_failed_count
            SettingsTextKeys.READY_TO_IMPORT -> R.string.settings_ready_to_import
            SettingsTextKeys.IMPORT_SUCCESS -> R.string.settings_import_success
            SettingsTextKeys.IMPORT_VALIDATION_ISSUES -> R.string.settings_import_validation_issues
            SettingsTextKeys.SHARE_SUCCESS -> R.string.settings_share_success
            SettingsTextKeys.EXPORT_FAILED -> R.string.settings_export_failed
            SettingsTextKeys.IMPORT_FAILED -> R.string.settings_import_failed
            SettingsTextKeys.VALIDATION_FAILED -> R.string.settings_validation_failed
            SettingsTextKeys.IMPORT_CALLBACK_FAILED -> R.string.settings_import_callback_failed
            SettingsTextKeys.EXPORT_CALLBACK_FAILED -> R.string.settings_export_callback_failed
            else -> 0
        }
    }
}
