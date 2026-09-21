package com.vansid.panda.app.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Builds the sound bank once and releases it when the screen goes.
 *
 * The sprites get away with a plain `remember` because a decoded bitmap is only memory,
 * and memory the collector will take back. A [SoundBank] holds a `SoundPool`, which owns
 * decoder threads and audio streams the runtime will not reclaim on its own — so this is
 * a `DisposableEffect` and not a one-liner.
 */
@Composable
internal fun rememberSoundBank(): SoundBank {
    val context = LocalContext.current
    val bank = remember(context) { SoundBank.create(context) }
    DisposableEffect(bank) {
        onDispose { bank.release() }
    }
    return bank
}
