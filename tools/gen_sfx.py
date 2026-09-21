#!/usr/bin/env python3
"""Generate the game's sound effects into art/sfx/.

    python3 tools/gen_sfx.py

Four sounds, synthesised rather than recorded or downloaded, for the same reason
the sprites are drawn by a script: §18 of the specification forbids reusing the
original's assets, and the Play listing promises original work throughout. A
sample pulled off a sound library would be neither.

They are also synthesised rather than played through the device's tone
generator because `SoundPool` wants files, and because a waveform written here
is a waveform that sounds the same on every phone.

Format: 22 050 Hz, mono, 16-bit PCM — see `audio.py`, which holds the synthesis
primitives this and `gen_music.py` share.

Reproducibility works the way the art's does: every random choice comes from a
fixed-seed LCG, so running this twice writes byte-identical files. One generator
is threaded through all four, so a change to an earlier sound is visible in the
later ones — the same bargain the sprite sheets make.
"""

import math
import os

from audio import RATE, Lcg, fade, highpass, lowpass, normalise, square, write_wav

AMPLITUDE = 0.82                     # peak, before the 16-bit conversion
SEED = 0xBA3B0025                    # arbitrary, but fixed on purpose

ART = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "art")


def throw(rnd: Lcg):
    """The cane leaving the panda's hand: a short rising whoosh.

    Noise rather than a tone. A thrown pole is air, not pitch, and a beep here
    read as a menu confirmation rather than as a throw. The rise is what sells
    the direction: the band it is filtered through climbs over the whole sound,
    so it reads as something departing rather than arriving.

    180 ms, because the flight that follows is a second or more and an effect
    that outlasts the arm looks dubbed.
    """
    n = int(RATE * 0.18)
    raw = [rnd.bipolar() for _ in range(n)]

    out = []
    prev_low = prev_band = 0.0
    for i, s in enumerate(raw):
        t = i / n
        # The passband sweeps 400 Hz -> 2600 Hz. Done per sample rather than by
        # filtering the whole buffer twice, because the sweep is the effect.
        cutoff = 400.0 + 2200.0 * t
        a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
        prev_low += a * (s - prev_low)
        band = prev_low - prev_band
        prev_band += a * 0.35 * (prev_low - prev_band)
        # Swell and fall, rather than a plain decay: a throw has a middle.
        env = math.sin(math.pi * t) ** 1.4
        out.append(band * env)

    return fade(normalise(out, AMPLITUDE))


def boom(rnd: Lcg):
    """The explosion: a low burst that falls away.

    Two layers, because one never sounds like an explosion. Noise carries the
    debris and a falling sine carries the weight; without the sine it is a hiss,
    without the noise it is a sad little slide whistle.

    550 ms against the art's eight frames at 40 ms. The sound deliberately
    outlasts the animation — the crater stays, so the air should still be moving
    when the last frame has gone.
    """
    n = int(RATE * 0.55)
    noise = [rnd.bipolar() for _ in range(n)]
    noise = lowpass(noise, 900.0)
    noise = highpass(noise, 60.0)

    out = []
    for i in range(n):
        t = i / n
        # Fast attack, exponential tail: the shape of anything that detonates.
        env = math.exp(-4.2 * t) * (1.0 - math.exp(-220.0 * t))
        # 150 Hz falling away to about 14 Hz over the tail. The phase is the
        # integral of that sweep, not the frequency sampled at each step: use
        # the frequency directly and the pitch is right while the phase jumps
        # every sample, which buzzes.
        phase = 150.0 * (1.0 - math.exp(-2.4 * t)) / 2.4
        out.append((noise[i] * 0.72 + math.sin(2.0 * math.pi * phase) * 0.58) * env)

    return fade(normalise(out, AMPLITUDE))


def _arpeggio(notes, note_ms: float, decay: float):
    """Square-wave notes end to end, each fading into the next."""
    out = []
    per = int(RATE * note_ms / 1000.0)
    for freq in notes:
        for i in range(per):
            t = i / RATE
            env = math.exp(-decay * (i / per))
            out.append(square(t, freq) * env)
    return fade(normalise(out, AMPLITUDE * 0.72))


def victory(_: Lcg):
    """Won the match: a major arpeggio, up.

    Square waves and a rising major triad, which is the most worn cue in games
    and is worn because it is legible in 400 ms on a phone speaker. Quieter than
    the explosion on purpose: it plays on a menu, not over a battlefield.
    """
    return _arpeggio([523.25, 659.25, 783.99, 1046.50], 105.0, 2.2)


def defeat(_: Lcg):
    """Lost the match: the same shape, minor and falling."""
    return _arpeggio([622.25, 523.25, 466.16, 349.23], 135.0, 1.9)


def main() -> None:
    out = os.path.join(ART, "sfx")
    os.makedirs(out, exist_ok=True)

    rnd = Lcg(SEED)

    for name, build in (
        ("throw.wav", throw),
        ("boom.wav", boom),
        ("victory.wav", victory),
        ("defeat.wav", defeat),
    ):
        samples = build(rnd)
        path = os.path.join(out, name)
        write_wav(path, samples)
        ms = 1000.0 * len(samples) / RATE
        print(f"{path}  {ms:.0f} ms  {os.path.getsize(path) / 1024:.1f} KB")


if __name__ == "__main__":
    main()
