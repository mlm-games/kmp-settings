package io.github.mlmgames.settings.core.locale

import io.github.mlmgames.settings.core.resources.SettingsTextKeys
import io.github.mlmgames.settings.core.resources.StringResourceProvider
import io.github.mlmgames.settings.core.resources.getStringOrDefault
import kotlinx.serialization.Serializable

@Serializable
enum class AppLanguage(val languageTag: String?, val endonym: String) {
    System(null, ""),
    English("en", "English"),
    Spanish("es", "Español"),
    Arabic("ar", "العربية"),
    Czech("cs", "Čeština"),
    German("de", "Deutsch"),
    Greek("el", "Ελληνικά"),
    Persian("fa", "فارسی"),
    Finnish("fi", "Suomi"),
    French("fr", "Français"),
    Croatian("hr", "Hrvatski"),
    Hungarian("hu", "Magyar"),
    Indonesian("id", "Bahasa Indonesia"),
    Italian("it", "Italiano"),
    Hebrew("he", "עברית"),
    Japanese("ja", "日本語"),
    Korean("ko", "한국어"),
    Dutch("nl", "Nederlands"),
    Polish("pl", "Polski"),
    Portuguese("pt", "Português"),
    Russian("ru", "Русский"),
    Swedish("sv", "Svenska"),
    Turkish("tr", "Türkçe"),
    Ukrainian("uk", "Українська"),
    Vietnamese("vi", "Tiếng Việt"),
    ChineseSimplified("zh-CN", "简体中文"),
    ChineseTraditional("zh-TW", "繁體中文"),
}

internal fun <E : Enum<E>> Array<E>.filterLanguages(allowed: List<String>): List<E> =
    if (allowed.isEmpty()) toList()
    else filter { it !is AppLanguage || it.languageTag == null || it.languageTag in allowed }

internal fun <E : Enum<E>> E.languageLabel(provider: StringResourceProvider): String? =
    (this as? AppLanguage)?.let { language ->
        if (language.languageTag == null) {
            provider.getStringOrDefault(SettingsTextKeys.LANGUAGE_SYSTEM, "System")
        } else {
            language.endonym
        }
    }
