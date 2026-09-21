#!/usr/bin/env python3
"""Synthesis and WAV writing, shared by the sound generators.

What `ega.py` is to the sprites, this is to the audio: the primitives, so that
`gen_sfx.py` and `gen_music.py` describe *what* they are making rather than how
a filter works.

Nothing here uses a library. The standard library's `wave` module writes the
container and the rest is arithmetic; adding numpy would break the
one-dependency rule in AGENTS.md for a few seconds of audio.
"""

import math
import struct
import wave

RATE = 22050


class Lcg:
    """The generator the skyline uses, so 'random' means one thing in this repo."""

    def __init__(self, seed: int):
        self.s = seed & 0xFFFFFFFF

    def next(self) -> int:
        self.s = (1664525 * self.s + 1013904223) & 0xFFFFFFFF
        return self.s

    def bipolar(self) -> float:
        """White noise in -1..1."""
        return self.next() / 0x7FFFFFFF - 1.0


def sine(t: float, freq: float) -> float:
    return math.sin(2.0 * math.pi * freq * t)


def square(t: float, freq: float) -> float:
    """A square wave, which is what a 16-colour game should sound like."""
    return 1.0 if (t * freq) % 1.0 < 0.5 else -1.0


def triangle(t: float, freq: float) -> float:
    """Softer than a square: the same family, without the upper harmonics."""
    p = (t * freq) % 1.0
    return 4.0 * abs(p - 0.5) - 1.0


def lowpass(samples, cutoff: float):
    """One-pole lowpass. Crude, and exactly right for taking the fizz off noise."""
    a = 1.0 - math.exp(-2.0 * math.pi * cutoff / RATE)
    out, prev = [], 0.0
    for s in samples:
        prev += a * (s - prev)
        out.append(prev)
    return out


def highpass(samples, cutoff: float):
    """One-pole highpass, as the complement of the above."""
    low = lowpass(samples, cutoff)
    return [s - l for s, l in zip(samples, low)]


def fade(samples, ms: float = 4.0):
    """Ramp both ends to zero.

    A buffer that starts or stops mid-swing puts a step in the speaker, and that
    step is a click audible over the sound itself. Four milliseconds is below
    the threshold of being heard as a fade and above the threshold of clicking.
    """
    n = max(1, int(RATE * ms / 1000.0))
    out = list(samples)
    for i in range(min(n, len(out))):
        g = i / n
        out[i] *= g
        out[-1 - i] *= g
    return out


def normalise(samples, peak: float):
    high = max((abs(s) for s in samples), default=0.0)
    if high == 0.0:
        return samples
    k = peak / high
    return [s * k for s in samples]


def mix(buffers, length: int):
    """Sum several buffers of any length into one of exactly [length]."""
    out = [0.0] * length
    for buf in buffers:
        for i, s in enumerate(buf):
            if i >= length:
                break
            out[i] += s
    return out


def add_at(target, source, start: int, gain: float = 1.0) -> None:
    """Mix [source] into [target] at a sample offset, in place, clipping the tail.

    Wrapping instead of clipping would be wrong even for a looping track: a note
    struck near the end should be cut by the loop point exactly as the loop cuts
    it, not fold back over the downbeat.
    """
    for i, s in enumerate(source):
        j = start + i
        if j >= len(target):
            break
        target[j] += s * gain


def write_wav(path: str, samples) -> None:
    """16-bit mono PCM at [RATE]. `SoundPool` and `MediaPlayer` both read it natively."""
    frames = b"".join(
        struct.pack("<h", max(-32768, min(32767, int(round(s * 32767.0)))))
        for s in samples
    )
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)
