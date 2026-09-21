package com.locationjoystick.core.model

/**
 * User's optional display-language override, per-device (like [ThemeMode]) — not part of
 * [AppSettings]/[ExportData]. `languageTag` is `null` for "follow the system language".
 */
enum class AppLanguage(
    val languageTag: String?,
) {
    SYSTEM_DEFAULT(null),
    ENGLISH("en"),
    CHINESE_SIMPLIFIED("zh-CN"),
    CHINESE_TRADITIONAL("zh-TW"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.languageTag == tag } ?: SYSTEM_DEFAULT
    }
}
