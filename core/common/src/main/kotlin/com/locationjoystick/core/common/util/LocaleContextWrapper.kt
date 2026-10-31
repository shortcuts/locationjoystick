package com.locationjoystick.core.common.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.locationjoystick.core.common.constants.AppConstants.LocaleConstants
import java.util.Locale

/**
 * Wraps a base [Context] with the user's chosen per-app language on API 28-32, where the
 * framework's own LocaleManager only applies to API 33+. A no-op passthrough on API 33+ (the
 * framework already handles every context there) and when no language has been chosen (falls
 * back to the base context, i.e. system language).
 */
object LocaleContextWrapper {
    // Lint checks this module alone; the app module sets bundle.language.enableSplit = false.
    @Suppress("AppBundleLocaleChanges")
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base

        val tag =
            base
                .getSharedPreferences(LocaleConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(LocaleConstants.KEY_LANGUAGE_TAG, null)
                ?: return base

        val locale = Locale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /** Persists [tag] ("en", "zh-CN", ...) as the app's chosen display language, or null for system default. */
    fun setLanguage(
        context: Context,
        tag: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            localeManager.applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            val editor = context.getSharedPreferences(LocaleConstants.PREFS_NAME, Context.MODE_PRIVATE).edit()
            if (tag == null) editor.remove(LocaleConstants.KEY_LANGUAGE_TAG) else editor.putString(LocaleConstants.KEY_LANGUAGE_TAG, tag)
            editor.apply()
        }
    }

    /** Currently chosen language tag, or null if following the system default. */
    fun getLanguage(context: Context): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.let { locales ->
                if (locales.isEmpty) null else locales[0].toLanguageTag()
            }
        } else {
            context
                .getSharedPreferences(LocaleConstants.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(LocaleConstants.KEY_LANGUAGE_TAG, null)
        }
}
