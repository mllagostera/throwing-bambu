package com.vansid.panda.app.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "SoundBank"

/**
 * How many effects may overlap. A throw's tail can still be sounding when the blast
 * starts, and in a hot-seat match the next turn begins immediately, so three is the
 * shallowest pool that never cuts one of those short.
 */
private const val MAX_STREAMS = 3

/** Effects are mixed at less than full scale so the loudest never clips the mix. */
private const val VOLUME = 0.85f

/**
 * The game's sound effects, decoded once and played by name.
 *
 * `SoundPool` rather than `MediaPlayer` (T-50): these are four short clips that have to
 * start on the frame they are asked for, may overlap, and are played over and over. That
 * is the case `SoundPool` exists for, and the case `MediaPlayer` — one stream, a
 * preparation step, and latency measured in frames — is worst at.
 *
 * ### Loading is asynchronous and the game does not wait for it
 *
 * `SoundPool.load` returns an id immediately and decodes on a worker. Playing an id that
 * is not ready yet is a no-op, not an error, which is the behaviour wanted here: a throw
 * in the first half-second after launch is silent rather than stalling the animation to
 * wait for a 8 KB file. [ready] tracks what has finished so a miss can be counted rather
 * than guessed at.
 *
 * ### Silent mode
 *
 * Checked at play time, not at construction. A phone can be silenced during a match, and
 * a bank that read the ringer mode once would keep playing into a meeting. `USAGE_GAME`
 * puts the effects on the media stream, which is what a game should use and what the
 * volume keys reach during play; the ringer check is what makes the silent switch mean
 * silent anyway.
 */
internal class SoundBank private constructor(
    private val pool: SoundPool,
    private val audio: AudioManager?,
    private val ids: Map<Sfx, Int>,
    private val ready: MutableSet<Int>,
) {
    /** Turned off by the settings toggle when T-51 wires one; silent mode is separate. */
    var enabled: Boolean = true

    /**
     * Plays one effect, or does nothing.
     *
     * Every reason to stay quiet — muted by the player, silenced by the phone, not
     * decoded yet — ends here rather than at each call site, so a caller never has to ask
     * whether sound is a good idea right now.
     */
    fun play(sfx: Sfx) {
        if (!enabled || silenced()) return
        val id = ids[sfx] ?: return
        if (id !in ready) return
        // Zero means the pool refused it — every stream busy with a higher priority, or
        // the sample gone. Worth a line, because a game that has quietly stopped making
        // noise gives no other sign that anything is wrong.
        if (pool.play(id, VOLUME, VOLUME, 1, 0, 1f) == 0) {
            Log.w(TAG, "pool refused to play $sfx")
        }
    }

    /** Releases the decoder and its streams. */
    fun release() {
        ready.clear()
        pool.release()
    }

    /**
     * True when the phone is on silent or vibrate.
     *
     * `RINGER_MODE_SILENT` alone is not enough: a phone set to vibrate is a phone whose
     * owner has said no sound, and treating vibrate as permission to play is the bug
     * every player notices once, in a quiet room.
     */
    private fun silenced(): Boolean =
        when (audio?.ringerMode) {
            AudioManager.RINGER_MODE_SILENT, AudioManager.RINGER_MODE_VIBRATE -> true
            else -> false
        }

    companion object {
        fun create(context: Context): SoundBank {
            val attributes =
                AudioAttributes
                    .Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

            val pool =
                SoundPool
                    .Builder()
                    .setMaxStreams(MAX_STREAMS)
                    .setAudioAttributes(attributes)
                    .build()

            // Before the first load, and that order is the whole of it. SoundPool
            // decodes on a worker and reports through this one listener; a completion
            // that lands before the listener is installed is dropped, not queued. These
            // files are small enough to decode in the gap, which leaves every sound
            // permanently "not ready yet" and the game permanently silent.
            val ready: MutableSet<Int> = ConcurrentHashMap.newKeySet()
            pool.setOnLoadCompleteListener { _, id, status ->
                if (status == 0) {
                    ready += id
                } else {
                    Log.w(TAG, "decode failed for sound id $id, status $status")
                }
            }

            val assets = context.assets
            val ids = Sfx.entries.mapNotNull { sfx -> load(pool, assets, sfx)?.let { sfx to it } }.toMap()

            return SoundBank(
                pool = pool,
                audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager,
                ids = ids,
                // Written on SoundPool's worker and read on whichever thread plays, so
                // it cannot be a plain MutableSet.
                ready = ready,
            )
        }

        /**
         * A missing or unreadable file costs that one effect, not the game.
         *
         * The assets are build output of a script that may not have been run, and a game
         * that refuses to start because a 8 KB whoosh is absent would be trading
         * something that matters for something that does not.
         */
        private fun load(
            pool: SoundPool,
            assets: AssetManager,
            sfx: Sfx,
        ): Int? =
            runCatching {
                assets.openFd("sfx/${sfx.asset}").use { pool.load(it, 1) }
            }.onFailure {
                Log.w(TAG, "could not load sfx/${sfx.asset}", it)
            }.getOrNull()
    }
}
