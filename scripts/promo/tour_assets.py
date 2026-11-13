"""Make the demo song used by the product tour: a short audio take and its cover.

Both are generated here so the tour uploads real files instead of placeholders:
a WAV the API accepts as audio, and a square JPEG the studio accepts as artwork.
"""

from pathlib import Path
import math
import struct
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / ".promo" / "tour"
FONTS = ROOT / ".promo" / "fonts"
OUT.mkdir(parents=True, exist_ok=True)

RATE = 44100
SECONDS = 14


def make_audio() -> Path:
    """A warm four-chord pad with a slow pulse — pleasant, not a ringtone."""
    t = np.linspace(0, SECONDS, RATE * SECONDS, endpoint=False)
    # Fmaj7 - Am7 - Dm7 - Bbmaj7, two seconds each, repeated.
    chords = [
        (174.61, 220.00, 261.63, 329.63),
        (220.00, 261.63, 329.63, 392.00),
        (146.83, 174.61, 220.00, 261.63),
        (116.54, 146.83, 174.61, 220.00),
    ]
    left = np.zeros_like(t)
    right = np.zeros_like(t)
    for index, chord in enumerate(chords * 2):
        start = index * (SECONDS / 8)
        span = SECONDS / 8
        env = np.clip((t - start) / 0.35, 0, 1) * np.clip((start + span - t) / 0.5, 0, 1)
        env = env * (t >= start) * (t < start + span)
        for voice, freq in enumerate(chord):
            detune = 1 + (0.0015 if voice % 2 else -0.0015)
            wave_l = np.sin(2 * math.pi * freq * t)
            wave_r = np.sin(2 * math.pi * freq * detune * t + 0.4)
            amp = 0.11 / (voice + 1)
            left += wave_l * env * amp
            right += wave_r * env * amp

    # A soft heartbeat underneath, low passed by its own envelope.
    kick = np.sin(2 * math.pi * 55 * t) * np.exp(-((t % 0.5) * 14)) * 0.10
    left += kick
    right += kick

    # Fade the edges so looping the bed into the film never clicks.
    fade = np.clip(t / 0.8, 0, 1) * np.clip((SECONDS - t) / 1.6, 0, 1)
    stereo = np.stack([left * fade, right * fade], axis=1)
    stereo /= max(1e-9, np.abs(stereo).max()) / 0.75
    pcm = (stereo * 32767).astype("<i2")

    path = OUT / "midnight-at-the-lagoon.wav"
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(RATE)
        handle.writeframes(pcm.tobytes())
    return path


def make_cover() -> Path:
    """Artwork in ORU's own palette, with the song's name on it."""
    size = 1200
    image = Image.new("RGB", (size, size), (13, 10, 7))
    draw = ImageDraw.Draw(image)

    # Deep lagoon gradient with an ember sun sitting low.
    for y in range(size):
        mix = y / size
        color = (
            int(10 + 26 * mix),
            int(32 + 48 * (1 - mix)),
            int(38 + 30 * (1 - mix)),
        )
        draw.line([(0, y), (size, y)], fill=color)

    glow = Image.new("RGB", (size, size), (0, 0, 0))
    glow_draw = ImageDraw.Draw(glow)
    glow_draw.ellipse([size * 0.18, size * 0.46, size * 0.82, size * 0.98], fill=(232, 85, 46))
    glow = glow.filter(ImageFilter.GaussianBlur(120))
    image = Image.blend(image, glow, 0.55)
    draw = ImageDraw.Draw(image)

    # Water line.
    for i in range(9):
        y = size * 0.66 + i * 26
        alpha = 1 - i / 10
        draw.line(
            [(size * 0.12, y), (size * 0.88, y)],
            fill=(int(210 * alpha), int(150 * alpha), int(110 * alpha)),
            width=2,
        )

    title_font = ImageFont.truetype(str(FONTS / "Fraunces[SOFT,WONK,opsz,wght].ttf"), 104)
    artist_font = ImageFont.truetype(str(FONTS / "Outfit[wght].ttf"), 46)

    title = "Midnight at\nthe Lagoon"
    draw.multiline_text((96, 120), title, font=title_font, fill=(246, 241, 236), spacing=6)
    draw.text((100, 360), "KANDY WAVES", font=artist_font, fill=(246, 120, 82))

    path = OUT / "midnight-at-the-lagoon.jpg"
    image.save(path, quality=92)
    return path


if __name__ == "__main__":
    print(make_audio())
    print(make_cover())
