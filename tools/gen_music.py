#!/usr/bin/env python3
"""Generate the background theme into art/music/.

    python3 tools/gen_music.py

A soft lounge loop — walking bass, electric-piano comping, a sparse lead and
brushes — in the idiom of the late-eighties adventure games this one is a
descendant of.

### It is an original tune, and that is not an accident

The obvious reference is a specific composition with a named author, and §18
already draws this line for `GORILLA.BAS`: mechanics and idiom are free, the
work itself is not. So the harmony here is the most common progression in
popular music — a ii-V-I with turnarounds, which nobody owns — and the melody
over it is written for this game. Nothing is transcribed.

### Why it is written rather than recorded

The same reason the sprites are drawn by a script: the listing promises
original work throughout, and a loop synthesised from arithmetic is provably
ours. It also costs no dependency, and `audio.py` already had most of it.

### Shape

16 bars at 92 BPM with swung eighths, about 42 seconds, ending on the dominant
so the loop turns over without a seam. Four voices, each quiet enough that the
sound effects sit on top without anything having to duck:

    bass     triangle, one octave down, walking in quarters
    piano    stacked sines, comped off the beat
    lead     triangle, sparse, mostly holding
    brushes  filtered noise, barely there, on the swing

Reproducible from a fixed-seed LCG, like everything else under `art/`.
"""

import math
import os

from audio import RATE, Lcg, add_at, fade, highpass, normalise, sine, triangle, write_wav

BPM = 92.0
BEAT = 60.0 / BPM                    # seconds per quarter note
BARS = 16
BEATS_PER_BAR = 4

# Swing: the off-beat eighth lands two thirds of the way through the beat, not
# halfway. This one number is most of what separates lounge from a music box.
SWING = 2.0 / 3.0

PEAK = 0.72                          # quieter than the effects, which play over it
SEED = 0x10465E11                    # arbitrary, but fixed on purpose

ART = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "art")


def midi(n: int) -> float:
    """Equal temperament from A4 = 440 Hz."""
    return 440.0 * (2.0 ** ((n - 69) / 12.0))


# The changes. A ii-V-I and its turnarounds, the most worn progression there is,
# which is exactly why it reads as "lounge" within two bars. Each entry is the
# root as a MIDI number and the chord tones above it in semitones.
MAJ7 = (0, 4, 7, 11)
MIN7 = (0, 3, 7, 10)
DOM7 = (0, 4, 7, 10)

C, D, E, F, G, A, B = 60, 62, 64, 65, 67, 69, 71

CHORDS = [
    (C, MAJ7), (A, MIN7), (D, MIN7), (G, DOM7),
    (C, MAJ7), (A, DOM7), (D, MIN7), (G, DOM7),
    (F, MAJ7), (F, MIN7), (C, MAJ7), (A, DOM7),
    (D, MIN7), (G, DOM7), (C, MAJ7), (G, DOM7),
]

# The tune, written for this game. One list per bar of (beat, note, beats held).
# Sparse on purpose: background music that keeps talking stops being background.
MELODY = [
    [(0.0, E + 12, 1.5), (2.0, G + 12, 1.5)],
    [(0.0, A + 12, 2.0), (2.5, G + 12, 1.0)],
    [(0.0, F + 12, 1.5), (2.0, A + 12, 1.5)],
    [(0.0, G + 12, 1.0), (1.5, F + 12, 0.5), (2.0, E + 12, 1.5)],
    [(0.0, C + 12, 2.0), (2.5, E + 12, 1.0)],
    [(0.0, C + 13, 1.5), (2.0, A + 12, 1.5)],
    [(0.0, D + 12, 1.5), (2.0, F + 12, 1.5)],
    [(0.0, E + 12, 1.0), (1.5, D + 12, 0.5), (2.0, C + 12, 2.0)],
    [(0.0, A + 12, 2.0), (2.5, C + 24 - 12, 1.0)],
    [(0.0, G + 12, 1.5), (2.0, F + 12, 1.5)],
    [(0.0, E + 12, 2.5)],
    [(0.0, C + 13, 1.5), (2.0, A + 12, 1.0)],
    [(0.0, F + 12, 1.5), (2.0, D + 12, 1.5)],
    [(0.0, B, 1.0), (1.5, A + 12, 0.5), (2.0, G + 12, 1.5)],
    [(0.0, C + 12, 3.0)],
    [(2.0, D + 12, 1.0), (3.0, E + 12, 1.0)],
]


def _piano(freq: float, beats: float):
    """An electric-piano-ish note: three partials over an exponential decay.

    Not a real Rhodes and not trying to be. Three sines with the upper two well
    down is enough to read as "keys" rather than "tone", and anything richer
    fights the effects it has to sit under.
    """
    n = int(RATE * beats * BEAT)
    out = []
    for i in range(n):
        t = i / RATE
        env = math.exp(-2.6 * (i / n)) * (1.0 - math.exp(-400.0 * t))
        body = sine(t, freq) + 0.32 * sine(t, freq * 2.0) + 0.14 * sine(t, freq * 3.01)
        out.append(body * env)
    return out


def _bass(freq: float, beats: float):
    """Walking bass: a triangle, plucked short so the line steps rather than smears."""
    n = int(RATE * beats * BEAT)
    out = []
    for i in range(n):
        t = i / RATE
        env = math.exp(-3.4 * (i / n)) * (1.0 - math.exp(-260.0 * t))
        out.append(triangle(t, freq) * env)
    return out


def _lead(freq: float, beats: float):
    """The tune. A triangle with a slow attack, so it sings instead of plucking."""
    n = int(RATE * beats * BEAT)
    out = []
    for i in range(n):
        t = i / RATE
        p = i / n
        env = min(1.0, t / 0.045) * (1.0 - p) ** 0.6
        # A little vibrato, late, the way a player leans on a held note.
        vib = 1.0 + 0.004 * math.sin(2.0 * math.pi * 5.2 * t) * min(1.0, p * 2.2)
        out.append(triangle(t, freq * vib) * env)
    return out


def _brush(rnd: Lcg, beats: float):
    """A brushed cymbal tick: noise, high, and almost inaudible on purpose."""
    n = int(RATE * beats * BEAT)
    raw = [rnd.bipolar() for _ in range(n)]
    raw = highpass(raw, 3200.0)
    return [s * math.exp(-13.0 * (i / n)) for i, s in enumerate(raw)]


def _walk(bar: int, root: int, quality) -> list:
    """Four notes a bar: root, a chord tone, the fifth, then a step to the next root.

    The approach note on beat four is what makes a bass line walk rather than
    sit — it is the one that resolves, and it is chosen against the chord that
    is coming, not the one being played.
    """
    third, fifth = root + quality[1], root + quality[2]
    next_root = CHORDS[(bar + 1) % BARS][0]
    approach = next_root - 1 if next_root >= root else next_root + 1
    return [root - 12, third - 12, fifth - 12, approach - 12]


def build():
    rnd = Lcg(SEED)
    total = int(RATE * BARS * BEATS_PER_BAR * BEAT)
    bass = [0.0] * total
    piano = [0.0] * total
    lead = [0.0] * total
    brush = [0.0] * total

    for bar in range(BARS):
        root, quality = CHORDS[bar]
        bar_start = bar * BEATS_PER_BAR * BEAT

        for beat, note in enumerate(_walk(bar, root, quality)):
            at = int(RATE * (bar_start + beat * BEAT))
            add_at(bass, _bass(midi(note), 0.9), at)

        # Comped on the second half of beats two and four — the lazy push that
        # makes the style. On the beat it would sound like a hymn.
        voicing = [root + s for s in quality]
        for beat in (1, 3):
            at = int(RATE * (bar_start + (beat + SWING) * BEAT))
            for n, note in enumerate(voicing):
                add_at(piano, _piano(midi(note), 1.6), at, 0.62 if n else 0.5)

        for beat, note, held in MELODY[bar]:
            at = int(RATE * (bar_start + beat * BEAT))
            add_at(lead, _lead(midi(note), held), at)

        for beat in range(BEATS_PER_BAR):
            at = int(RATE * (bar_start + beat * BEAT))
            add_at(brush, _brush(rnd, 0.35), at, 0.5)
            off = int(RATE * (bar_start + (beat + SWING) * BEAT))
            add_at(brush, _brush(rnd, 0.3), off, 0.3)

    # The balance. The lead sits just above the piano, the bass carries weight
    # without mud, and the brushes are barely present — turn them up and the
    # loop starts sounding like a drum machine rather than a room.
    out = [
        0.62 * b + 0.42 * p + 0.50 * l + 0.10 * s
        for b, p, l, s in zip(bass, piano, lead, brush)
    ]
    # A short fade on the seam only. The loop is written to turn over on the
    # dominant, so this is insurance against a click, not a musical decision.
    return fade(normalise(out, PEAK), ms=8.0)


def main() -> None:
    out = os.path.join(ART, "music")
    os.makedirs(out, exist_ok=True)
    samples = build()
    path = os.path.join(out, "theme.wav")
    write_wav(path, samples)
    seconds = len(samples) / RATE
    print(f"{path}  {seconds:.1f} s  {os.path.getsize(path) / 1024:.0f} KB  {BPM:.0f} BPM")


if __name__ == "__main__":
    main()
