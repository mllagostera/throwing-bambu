package dev.bambu.app.settings

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * The languages the game ships in.
 *
 * Each name is written in its own language: someone who has landed in the wrong one
 * needs to recognise their own, and "Spanish" is no help to a reader who only knows
 * "Español".
 */
enum class AppLocale(
    val tag: String,
    val label: String,
) {
    SYSTEM("", ""),
    ENGLISH("en", "English"),
    SPANISH("es", "Español"),
    CATALAN("ca", "Català"),
    FRENCH("fr", "Français"),
    GERMAN("de", "Deutsch"),
    ;

    companion object {
        fun fromTag(tag: String?): AppLocale = entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * Remembers the chosen language.
 *
 * `SharedPreferences` rather than DataStore, which the plan uses for the rest of the
 * settings (T-51): the language has to be known **before** the first composition, and a
 * flow that arrives one frame later would show the wrong language and then blink.
 */
class LocaleStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var locale: AppLocale
        get() = AppLocale.fromTag(prefs.getString(KEY, null))
        set(value) = prefs.edit().putString(KEY, value.tag).apply()

    private companion object {
        const val FILE = "settings"
        const val KEY = "locale"
    }
}

/**
 * Applies a language to everything composed inside.
 *
 * The alternative — `AppCompatDelegate.setApplicationLocales` — means pulling in
 * AppCompat, switching the activity's base class and its theme, and recreating the
 * activity on every change. Overriding the context here costs none of that and the
 * change is immediate: the composition simply reads from a configuration with a
 * different locale.
 */
@Composable
fun WithAppLocale(
    locale: AppLocale,
    content: @Composable () -> Unit,
) {
    if (locale == AppLocale.SYSTEM) {
        content()
        return
    }

    val context = LocalContext.current
    val localized =
        remember(locale, context) {
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(Locale.forLanguageTag(locale.tag))
            context.createConfigurationContext(configuration)
        }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
    ) {
        content()
    }
}
