package com.vansid.panda.app.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the game makes any noise, remembered across launches (T-51).
 *
 * **Two switches, not one.** They fail differently: the music is the thing somebody
 * turns off to play with their own on, and the effects are the thing they turn off in a
 * waiting room. Folding both into one control means the player who wants either has to
 * give up both.
 *
 * `SharedPreferences` rather than DataStore, for the reason [LocaleStore] gives and one
 * more. The language has to be known before the first composition or the wrong one shows
 * and blinks; the music has to be known before the first composition or it starts and is
 * then cut off, which is the same fault with a worse symptom — a visual blink is missed,
 * half a second of unwanted music in a quiet room is not.
 *
 * The values are Compose state, so the settings screen, the sound bank and the music all
 * read one source of truth and a toggle takes effect on the frame it is made rather than
 * at the next launch.
 *
 * ### `commit`, where the language uses `apply`
 *
 * `apply` schedules the write and returns, so a process killed before the flush loses
 * it — which is not theoretical: killing the emulator mid-test lost exactly this and
 * brought back an older pair of values. A toggle is a rare, deliberate tap and the file
 * is two booleans, so the few milliseconds of synchronous I/O buy durability at a price
 * nobody can feel. It is the opposite trade from a write on a hot path, which is the
 * case `apply` is for.
 */
class AudioSettings(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private var musicState by mutableStateOf(prefs.getBoolean(KEY_MUSIC, true))
    private var soundState by mutableStateOf(prefs.getBoolean(KEY_SOUND, true))

    /** The looping background theme. */
    var music: Boolean
        get() = musicState
        set(value) {
            musicState = value
            prefs.edit().putBoolean(KEY_MUSIC, value).commit()
        }

    /** The throw, the blast, and the two end-of-match stings. */
    var sound: Boolean
        get() = soundState
        set(value) {
            soundState = value
            prefs.edit().putBoolean(KEY_SOUND, value).commit()
        }

    private companion object {
        /** The same file the language uses: one settings file, not one per setting. */
        const val FILE = "settings"
        const val KEY_MUSIC = "music"
        const val KEY_SOUND = "sound"
    }
}

/**
 * The audio settings, for the screens far from where they are created.
 *
 * A composition local rather than four more parameters threaded through the nav host and
 * every destination between it and the playfield. This is the case the mechanism exists
 * for: one value, read in two distant places, that nothing in between cares about — and
 * the same mechanism the language and the density override already use.
 *
 * `static`, because it changes only if the whole app is recreated: the booleans inside
 * are the observable part, so recomposition follows the settings rather than the holder.
 */
val LocalAudioSettings =
    staticCompositionLocalOf<AudioSettings> {
        error("No AudioSettings provided. Wrap the tree in WithAudioSettings.")
    }

/** Creates the settings once and publishes them to everything composed inside. */
@Composable
fun WithAudioSettings(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { AudioSettings(context) }
    CompositionLocalProvider(LocalAudioSettings provides settings, content = content)
}
