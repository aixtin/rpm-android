package com.rpm.player.core

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The country and the interface language the person chose on this device in the TV's set-up, each
 * null until chosen. Null leaves the device's own: the radio's guess from the time zone
 * ([com.rpm.player.core.radio.RadioRepository.listenerCountry]) and the system language.
 *
 * Kept in their own preferences file so the activity can read the language in `attachBaseContext`, before anything
 * else is up ([wrap]).
 */
class Region(context: Context) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Two letters, upper case. */
    var country: String?
        get() = prefs.getString(KEY_COUNTRY, null)
        set(value) = prefs.edit().putString(KEY_COUNTRY, value).apply()

    /** One of [LANGUAGES]. Takes effect when the activity is next created. */
    var language: String?
        get() = prefs.getString(KEY_LANGUAGE, null)?.takeIf { it in LANGUAGES }
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).commit().let { }

    companion object {
        private const val FILE = "rpm.region"
        private const val KEY_COUNTRY = "country"
        private const val KEY_LANGUAGE = "language"

        /** The languages the interface is written in, as `scripts/strings.json` has them. */
        val LANGUAGES = listOf("en", "fr", "es", "de", "zh-Hans")

        /** [base] with the chosen language in force, or [base] itself when none was chosen. */
        fun wrap(base: Context): Context {
            val tag = base.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)?.takeIf { it in LANGUAGES } ?: return base
            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            return base.createConfigurationContext(config)
        }

        /** The language in force for [context]: one of [LANGUAGES], English when the system's is none of them. */
        fun languageOf(context: Context): String {
            val locale = context.resources.configuration.locales[0]
            return when {
                locale.language == "zh" -> "zh-Hans"
                locale.language in LANGUAGES -> locale.language
                else -> "en"
            }
        }
    }
}
