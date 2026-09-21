package com.vansid.panda.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val TAG = "Music"

private const val ASSET = "music/theme.wav"

/**
 * Well under the effects, which are mixed at 0.85. The tune is furniture: it should be
 * noticed when somebody stops to listen and not before, and it must never be the reason
 * a throw or a blast is hard to hear.
 */
private const val VOLUME = 0.32f

/**
 * The looping background theme (§13b).
 *
 * `MediaPlayer` rather than the [SoundBank]'s `SoundPool`, which is the opposite call to
 * the one made for the effects and for the opposite reasons. `SoundPool` decodes whole
 * samples into memory and is built for short clips fired repeatedly; this is a 1.8 MB
 * file played once and looped forever, which is precisely what `MediaPlayer` streams
 * without holding it all at once.
 *
 * Looping is `isLooping`, not a completion listener restarting it. The gap a restart
 * leaves is small, audible, and lands in the same place every 42 seconds.
 */
internal class Music private constructor(
    private val player: MediaPlayer?,
    private val audio: AudioManager?,
) {
    /** Turned off by the settings toggle when T-51 wires one; silent mode is separate. */
    var enabled: Boolean = true
        set(value) {
            field = value
            if (value) resume() else pause()
        }

    /**
     * Starts or continues the tune, unless something says not to.
     *
     * Silent mode is checked here rather than once at construction, so a phone silenced
     * during a match goes quiet at the next resume rather than at the next launch.
     */
    fun resume() {
        val player = player ?: return
        if (!enabled || silenced()) return
        runCatching { if (!player.isPlaying) player.start() }
            .onFailure { Log.w(TAG, "could not start the theme", it) }
    }

    /** Stops without losing the position, so returning to the app does not restart it. */
    fun pause() {
        val player = player ?: return
        runCatching { if (player.isPlaying) player.pause() }
            .onFailure { Log.w(TAG, "could not pause the theme", it) }
    }

    fun release() {
        runCatching { player?.release() }
            .onFailure { Log.w(TAG, "could not release the theme", it) }
    }

    /** Vibrate counts as silent, for the reason spelled out in [SoundBank]. */
    private fun silenced(): Boolean =
        when (audio?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE -> true
            else -> false
        }

    companion object {
        /**
         * A missing or unplayable theme costs the music, not the game.
         *
         * The asset is build output of a script that may not have been run, and there is
         * no version of this worth a crash on launch for.
         */
        fun create(context: Context): Music {
            val player =
                runCatching {
                    MediaPlayer().apply {
                        context.assets.openFd(ASSET).use {
                            setDataSource(it.fileDescriptor, it.startOffset, it.length)
                        }
                        setAudioAttributes(
                            AudioAttributes
                                .Builder()
                                .setUsage(AudioAttributes.USAGE_GAME)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build(),
                        )
                        isLooping = true
                        setVolume(VOLUME, VOLUME)
                        prepare()
                    }
                }.onFailure { Log.w(TAG, "no background theme: $ASSET", it) }.getOrNull()

            return Music(
                player = player,
                audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
            )
        }
    }
}

/**
 * Plays the theme for as long as the app is in front, and not a moment longer.
 *
 * Tied to the lifecycle rather than to the composition. A game whose music kept playing
 * from the recents screen, or over somebody's phone call, is the kind of thing that gets
 * uninstalled — so `ON_PAUSE` stops it and `ON_RESUME` picks it up where it left off.
 */
@Composable
internal fun rememberMusic(): Music {
    val context = LocalContext.current
    val music = remember(context) { Music.create(context) }
    val owner = LocalLifecycleOwner.current

    DisposableEffect(owner, music) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> music.resume()
                    Lifecycle.Event.ON_PAUSE -> music.pause()
                    else -> Unit
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            music.release()
        }
    }
    return music
}
