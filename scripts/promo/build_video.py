"""Build the ORU promo video.

Renders every frame with Pillow at 1080x1920 (portrait), pipes the frames straight into
ffmpeg, and writes the soundtrack with numpy. The product shots are real
screen captures taken headlessly from the live site by
scripts/promo/capture.cjs, so the film shows the actual app.

Brand: warm paper surfaces, cream ink and one ember accent. Fraunces carries
the display type, Outfit carries the interface copy.

Chunks, because a whole film in one command outlives any single run:

  .venv/bin/python scripts/promo/build_video.py 0 230
  .venv/bin/python scripts/promo/build_video.py 230 460
  .venv/bin/python scripts/promo/build_video.py 460 690
  .venv/bin/python scripts/promo/build_video.py 690 915
  .venv/bin/python scripts/promo/build_video.py --still 96    # single debug frame

Then stitch with the concat command printed by --concat.
"""

import math
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / ".promo" / "fonts"
SHOTS = ROOT / ".promo" / "screens"
OUT = ROOT / "public" / "promo"
WORK = ROOT / ".promo"

W, H, FPS = 1080, 1920, 30
DURATION = 30.5
FRAMES = int(DURATION * FPS)

# The identity, straight from src/index.css.
PAPER = (23, 18, 14)
PAPER_DEEP = (13, 10, 7)
CARD = (30, 24, 19)
CARD_LINE = (64, 53, 43)
INK = (242, 238, 231)
SOFT = (199, 191, 179)
FAINT = (138, 128, 116)
EMBER = (232, 85, 46)
EMBER_NEW = (246, 120, 82)
CREAM = (244, 241, 236)

FFMPEG = None


def ffmpeg_path():
    global FFMPEG
    if FFMPEG is None:
        import imageio_ffmpeg

        FFMPEG = imageio_ffmpeg.get_ffmpeg_exe()
    return FFMPEG


# ---------------------------------------------------------------- easing ----
def clamp(value, low=0.0, high=1.0):
    return max(low, min(high, value))


def progress(t, start, end):
    if end <= start:
        return 1.0
    return clamp((t - start) / (end - start))


def ease_out(t):
    # Quintic: leaves fast, lands soft. Every entrance in the film uses it,
    # which is what keeps the shorter cuts feeling smooth instead of snatched.
    return 1 - (1 - t) ** 5


def ease_in(t):
    return t ** 3


def ease_in_out(t):
    return t * t * (3 - 2 * t)


def ease_back(t, overshoot=1.7):
    t -= 1
    return 1 + t * t * ((overshoot + 1) * t + overshoot)


def lerp(a, b, t):
    return a + (b - a) * t


# ---------------------------------------------------------------- assets ----
FRAUNCES = FONTS / "Fraunces[SOFT,WONK,opsz,wght].ttf"
OUTFIT = FONTS / "Outfit[wght].ttf"


class Fonts:
    """Variable fonts, set to the axis values each line needs.

    Fraunces axes arrive ordered [opsz, weight, soft, wonk]; Outfit is [weight].
    """

    FRA = {"display": (72, 700, 0, 0), "display_x": (110, 800, 0, 0), "display_hard": (100, 880, 0, 0)}
    OUT = {"boldest": 780, "bold": 700, "semi": 600, "med": 480, "reg": 400}

    def __init__(self):
        self.cache = {}

    def fraunces(self, style, size):
        key = ("fra", style, size)
        if key not in self.cache:
            font = ImageFont.truetype(str(FRAUNCES), size)
            try:
                font.set_variation_by_axes(list(self.FRA[style]))
            except Exception:
                pass
            self.cache[key] = font
        return self.cache[key]

    def outfit(self, weight, size, tracking=True):
        key = ("out", weight, size)
        if key not in self.cache:
            font = ImageFont.truetype(str(OUTFIT), size)
            try:
                font.set_variation_by_axes([self.OUT[weight]])
            except Exception:
                pass
            self.cache[key] = font
        return self.cache[key]

    def get(self, name, size):
        """Two-name convenience: serif styles, else Outfit weights."""
        if name in self.FRA:
            return self.fraunces(name, size)
        return self.outfit(name, size)


FON = Fonts()


def text(draw, xy, value, weight="semi", size=48, fill=INK, tracking=0.0, anchor="la"):
    font = FON.get(weight, size)
    x, y = xy
    if tracking == 0:
        draw.text((x, y), value, font=font, fill=fill, anchor=anchor)
        return font.getlength(value)
    width = sum(font.getlength(ch) for ch in value) + tracking * max(len(value) - 1, 0)
    # Pillow spells the horizontal middle anchor "m" ("mm", "ma"); "c" is
    # accepted too so either spelling centres the tracked line.
    if anchor[0] in ("c", "m"):
        x -= width / 2
    elif anchor[0] == "r":
        x -= width
    cursor = x
    for ch in value:
        draw.text((cursor, y), ch, font=font, fill=fill, anchor="l" + anchor[1])
        cursor += font.getlength(ch) + tracking
    return width


def measure(value, weight="semi", size=48, tracking=0.0):
    font = FON.get(weight, size)
    if tracking:
        return sum(font.getlength(ch) for ch in value) + tracking * max(len(value) - 1, 0)
    return font.getlength(value)


def wrap(value, weight, size, width):
    font = FON.get(weight, size)
    lines, line = [], ""
    for word in value.split():
        candidate = f"{line} {word}".strip()
        if font.getlength(candidate) <= width or not line:
            line = candidate
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


# --------------------------------------------------------------- surfaces ---
_BACKGROUNDS = {}


def radial_background(size, glow=(72, 34, 20), strength=1.0):
    """Warm paper with one soft ember-lit pocket behind the composition."""
    key = (size, glow, strength)
    if key in _BACKGROUNDS:
        return _BACKGROUNDS[key].copy()
    w, h = size
    small = Image.new("RGB", (w // 8, h // 8), (30, 24, 18))
    draw = ImageDraw.Draw(small)
    for radius in range(int(min(small.size) * 0.9), 0, -6):
        ratio = radius / min(small.size)
        colour = tuple(int(lerp((30, 24, 18)[i], glow[i] * strength, (1 - ratio) ** 2.4)) for i in range(3))
        draw.ellipse((-radius, -radius, 2 * radius, 2 * radius), fill=colour)
    image = small.resize(size, Image.LANCZOS)
    _BACKGROUNDS[key] = image
    return image.copy()


def ember_glow(size, centre, radius, colour=EMBER, alpha=52, power=2.4):
    """An ember pool with a clean radial falloff.

    This is computed rather than blurred on purpose: a Gaussian blur this wide
    starts to veil the whole frame, so the light is drawn with a falloff curve
    instead and every edge in the film stays crisp.
    """
    key = ("glow", size, centre, radius, colour, alpha, power)
    if key in _GLOWS:
        return _GLOWS[key]
    w, h = size
    dx = (np.arange(w, dtype=np.float32) - centre[0])[None, :]
    dy = (np.arange(h, dtype=np.float32) - centre[1])[:, None]
    falloff = np.clip(1 - np.hypot(dx, dy) / radius, 0, 1) ** power
    layer = np.zeros((h, w, 4), dtype=np.uint8)
    layer[..., 0], layer[..., 1], layer[..., 2] = colour
    layer[..., 3] = (falloff * alpha).astype(np.uint8)
    image = Image.fromarray(layer, "RGBA")
    _GLOWS[key] = image
    return image


def halo_sprite(diameter, colour=EMBER, alpha=52):
    key = ("halo", diameter, colour, alpha)
    if key not in _GLOWS:
        _GLOWS[key] = ember_glow((diameter, diameter), (diameter / 2, diameter / 2),
                                 diameter * 0.55, colour, alpha)
    return _GLOWS[key]


_GLOWS = {}


def full_bleed(size):
    """An ember bloom large enough to light the whole frame."""
    if "bloom" not in _GLOWS:
        _GLOWS["bloom"] = ember_glow((W, H), (W / 2, H / 2), 700.0, EMBER, 56)
    return _GLOWS["bloom"]


def rounded_mask(size, radius):
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


class Shot:
    """A screenshot resized once, reused at different scales."""

    def __init__(self, path):
        self.original = Image.open(path).convert("RGB")
        self.cache = {}

    def sized(self, height):
        if height not in self.cache:
            width = int(self.original.width * height / self.original.height)
            shot = self.original.resize((width, height), Image.LANCZOS)
            # Sharpened at the size it is finally drawn at, so the real interface
            # text stays legible instead of being softened by the downscale.
            self.cache[height] = shot.filter(
                ImageFilter.UnsharpMask(radius=1.1, percent=52, threshold=2))
        return self.cache[height]

    def crop(self, box):
        return self.original.crop(box)


def shot(name):
    return Shot(SHOTS / f"{name}.png")


SC_LISTEN_PHONE = shot("listen_phone")
SC_LANDING_PHONE = shot("landing_phone")
SC_LANDING_DESK = shot("landing_desk")
SC_ARTIST = shot("artist_desk")
SC_RELEASE = shot("release_desk") if (SHOTS / "release_desk.png").exists() else SC_ARTIST
SC_STUDIO = shot("studio_desk")
SC_SHARE = shot("share_desk")
SC_SHARES_INBOX = shot("shares_inbox_desk")
SC_LIBRARY = shot("library_phone")
SC_SEARCH = shot("search_phone")


# ----------------------------------------------------------------- device ---
_DEVICES = {}


def device_phone(image_height=900, tilt=0.0, screen_radius=46, shot=None):
    """A shot bezelled like a device, cached per rounded scale key."""
    shot = shot or SC_LANDING_PHONE
    key = ("phone", int(image_height / 4), round(tilt / 0.5), shot.original.size)
    if key in _DEVICES:
        return _DEVICES[key]
    image_height = key[1] * 4
    tilt = key[2] * 0.5
    stage = shot.sized(image_height)
    bezel = 14
    frame = Image.new("RGBA", (stage.width + bezel * 2, stage.height + bezel * 2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(frame)
    draw.rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), radius=screen_radius + bezel,
                           fill=(21, 16, 12, 255), outline=(82, 68, 55, 255), width=3)
    mask = rounded_mask(stage.size, screen_radius)
    frame.paste(stage, (bezel, bezel), mask)
    draw.rounded_rectangle((frame.width / 2 - 44, bezel + 10, frame.width / 2 + 44, bezel + 30),
                           radius=10, fill=(6, 5, 4, 255))
    if tilt:
        frame = frame.rotate(tilt, resample=Image.BICUBIC, expand=True)
    _DEVICES[key] = frame
    return frame


_DESKTOPS = {}


def device_desktop(width=1500, radius=18, shot=None, url="oru.freebuff.app"):
    """A shot framed as a browser window with the live address in the bar."""
    shot = shot or SC_LANDING_DESK
    width = int(width / 20) * 20
    key = ("desk", width, radius, url, shot.original.size)
    if key in _DESKTOPS:
        return _DESKTOPS[key]
    stage = shot.original.resize((width, int(width * shot.original.height / shot.original.width)), Image.LANCZOS)
    chrome = 36
    frame = Image.new("RGBA", (stage.width, stage.height + chrome), (0, 0, 0, 0))
    draw = ImageDraw.Draw(frame)
    draw.rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), radius=radius,
                           fill=(21, 16, 12, 255), outline=(92, 76, 62, 255), width=2)
    draw.rectangle((0, 0, frame.width - 1, chrome), fill=(28, 22, 17, 255))
    for index, colour in enumerate([(240, 90, 66), (243, 172, 84), (126, 200, 106)]):
        draw.ellipse((16 + index * 24, 13, 26 + index * 24, 23), fill=colour)
    draw.rounded_rectangle((118, 7, 360, 29), radius=11, fill=(41, 32, 25, 255))
    text(draw, (136, 18), url, "med", 13, SOFT, anchor="lm")
    mask = rounded_mask(stage.size, 10)
    frame.paste(stage, (0, chrome), mask)
    _DESKTOPS[key] = frame
    return frame


def paste_center(canvas, layer, center, alpha=1.0):
    x = int(center[0] - layer.width / 2)
    y = int(center[1] - layer.height / 2)
    if alpha >= 1:
        canvas.paste(layer, (x, y), layer if layer.mode == "RGBA" else None)
    else:
        faded = layer.copy()
        if faded.mode == "RGBA":
            r, g, b, a = faded.split()
            faded.putalpha(a.point(lambda value: int(value * alpha)))
        canvas.paste(faded, (x, y), faded if faded.mode == "RGBA" else None)


def mark(size=150, rotation=0.0):
    """The ORU mark: an ember disc with a cream play wedge."""
    scale = 4
    canvas = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    unit = size * scale / 64
    draw.ellipse((1 * unit, 1 * unit, 63 * unit, 63 * unit), fill=EMBER)
    draw.ellipse((1 * unit, 1 * unit, 63 * unit, 63 * unit), outline=PAPER_DEEP, width=int(4 * unit))
    draw.polygon([(27 * unit, 23 * unit), (45 * unit, 32 * unit), (27 * unit, 41 * unit)], fill=CREAM,
                 outline=CREAM, width=int(5 * unit))
    canvas = canvas.resize((size, size), Image.LANCZOS)
    return canvas.rotate(rotation, resample=Image.BICUBIC, expand=True) if rotation else canvas


def bubble(layer, box, radius, fill, tail="left"):
    draw = ImageDraw.Draw(layer)
    draw.rounded_rectangle(box, radius=radius, fill=fill)
    if tail == "left":
        x, y = box[0] + 6, box[3] - 18
        draw.polygon([(x, y), (x + 26, y + 20), (x + 4, y + 24)], fill=fill)
    elif tail == "right":
        x, y = box[2] - 6, box[3] - 18
        draw.polygon([(x, y), (x - 26, y + 20), (x - 4, y + 24)], fill=fill)


def heart(size, colour=EMBER, filled=True):
    layer = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    s = size * 4
    draw.polygon([
        (s * 0.5, s * 0.86), (s * 0.12, s * 0.46), (s * 0.12, s * 0.27), (s * 0.26, s * 0.14),
        (s * 0.42, s * 0.16), (s * 0.5, s * 0.3), (s * 0.58, s * 0.16), (s * 0.74, s * 0.14),
        (s * 0.88, s * 0.27), (s * 0.88, s * 0.46),
    ], fill=colour if filled else None, outline=colour, width=int(size * 0.16) if not filled else 0)
    return layer.resize((size, size), Image.LANCZOS)


def zoom_center(image, factor):
    w, h = image.size
    nw, nh = int(w / factor), int(h / factor)
    box = ((w - nw) // 2, (h - nh) // 2, (w + nw) // 2, (h + nh) // 2)
    return image.crop(box).resize((w, h), Image.LANCZOS).filter(
        ImageFilter.UnsharpMask(radius=1.0, percent=45, threshold=2))


def blend_black(image, amount):
    if amount >= 0.999:
        return image
    return Image.blend(Image.new("RGB", image.size, (0, 0, 0)), image, clamp(amount))


# ----------------------------------------------------------------- grain ----
RNG = np.random.default_rng(9)
# Trimmed from sigma 1.5: at full strength the grain was the whole bitrate, so the
# encoder spent its budget on noise and smeared the type. This keeps the texture
# and hands the bits back to actual detail.
GRAIN = [RNG.normal(0, 0.82, (H, W, 1)).astype(np.float32) for _ in range(6)]
_YY, _XX = np.mgrid[0:H, 0:W]
_NX = (_XX - W / 2) / (W / 2)
_NY = (_YY - H / 2) / (H / 2)
VIGNETTE = (1 - 0.5 * np.clip((_NX * _NX + _NY * _NY) * 0.4, 0, 1))[..., None].astype(np.float32)


BRIGHTNESS = 1.10
KNEE = 200.0


def brighten(image, gain=BRIGHTNESS):
    """Ten percent more light, applied once to the composed frame.

    Multiplicative, so black stays black and the fades keep their true black.
    Above the knee the gain rolls off instead of clamping: the cream ink
    (242,238,231) lands near 247 rather than clipping to flat white.
    """
    array = np.asarray(image.convert("RGB"), dtype=np.float32) * gain
    knee, top = KNEE * gain, 255.0 * gain
    slope = (255.0 - knee) / (top - knee)
    array = np.where(array > knee, knee + (array - knee) * slope, array)
    return Image.fromarray(np.clip(array, 0, 255).astype(np.uint8), "RGB")


def finish(frame, timecode):
    array = np.asarray(frame.convert("RGB")).astype(np.float32)
    array += GRAIN[int(timecode * FPS) % len(GRAIN)]
    array *= VIGNETTE
    return Image.fromarray(np.clip(array, 0, 255).astype(np.uint8), "RGB")


# ----------------------------------------------------------------- scenes ---
def tap_ring(layer, cx, cy, radius_max, ring_phase):
    draw = ImageDraw.Draw(layer)
    radius = 16 + ease_out(ring_phase) * radius_max
    draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius),
                 outline=(*EMBER, int(210 * (1 - ring_phase))), width=5)


def typing_dots(canvas, x, y, t, visible):
    if not visible:
        return
    dots = Image.new("RGBA", (112, 62), (0, 0, 0, 0))
    ddraw = ImageDraw.Draw(dots)
    ddraw.rounded_rectangle((0, 0, 111, 61), radius=26, fill=(44, 36, 29, 255))
    for index in range(3):
        bob = math.sin((t * 7) - index * 0.8) * 4
        ddraw.ellipse((22 + index * 27, 23 + bob, 38 + index * 27, 39 + bob), fill=(*EMBER, 255)
                      if index == int(t * 3) % 3 else (*SOFT, 210))
    canvas.alpha_composite(dots, (int(x), int(y)))


def scene_chat(t):
    """One friend finds a song on ORU and sends the link across."""
    base = radial_background((W, H), glow=(58, 27, 16), strength=1.4).convert("RGBA")
    veil = Image.new("RGBA", (W, H), (8, 6, 4, 168))
    base = Image.alpha_composite(base, veil)
    canvas = base
    draw = ImageDraw.Draw(canvas)

    panel_w, panel_h = 960, 820
    px, py = (W - panel_w) / 2, (H - panel_h) / 2 - 20
    draw.rounded_rectangle((px - 24, py - 24, px + panel_w + 24, py + panel_h + 24), radius=40,
                           fill=(32, 25, 19, 244), outline=CARD_LINE, width=2)

    # Header
    head = Image.new("RGBA", (panel_w, 92), (0, 0, 0, 0))
    hdraw = ImageDraw.Draw(head)
    hdraw.rounded_rectangle((0, 0, panel_w - 1, 92), radius=24, fill=(42, 33, 26, 255))
    hdraw.ellipse((24, 22, 70, 68), fill=(58, 45, 35, 255))
    text(hdraw, (47, 45), "M", "bold", 22, INK, anchor="mm")
    text(hdraw, (88, 30), "Moyin", "semi", 25, INK)
    text(hdraw, (88, 58), "direct message", "reg", 16, FAINT)
    canvas.alpha_composite(head, (int(px), int(py)))

    messages = [
        ("left", "found the smallest song i swear", 0.26, 4.95, "med"),
        ("left", "and nobody has heard it yet", 0.85, 4.95, "med"),
        ("right", "whats ORU again?", 1.52, 4.95, "med"),
        ("link", "", 2.26, 99, ""),
    ]

    y = py + 128
    left_x, right_x = px + 22, px + panel_w - 22
    for kind, label, start, blink_until, weight in messages:
        if t < start:
            break
        appear = ease_out(progress(t, start, start + 0.3))
        if kind in ("left", "right"):
            size = 29
            width = measure(label, "med", size)
            box_w = width + 54
            layer = Image.new("RGBA", (int(box_w) + 60, 116), (0, 0, 0, 0))
            if kind == "left":
                bubble(layer, (24, 8, 24 + int(box_w), 72), 24, (58, 47, 37, 255), "left")
                text(ImageDraw.Draw(layer), (46, 40), label, "med", size, INK, anchor="lm")
                paste_center(canvas, layer, (left_x + box_w / 2 - 8, y + 40), appear)
            else:
                bubble(layer, (24, 8, 24 + int(box_w), 72), 24, (*EMBER, 255), "right")
                text(ImageDraw.Draw(layer), (46, 40), label, "med", size, (250, 245, 240), anchor="lm")
                paste_center(canvas, layer, (right_x - box_w / 2 + 8, y + 40), appear)
            y += 88
        else:
            card_w, card_h = 500, 292
            dust = (88, 52, 34)
            layer = Image.new("RGBA", (card_w + 44, card_h + 44), (0, 0, 0, 0))
            ldraw = ImageDraw.Draw(layer)
            ldraw.rounded_rectangle((22, 22, 22 + card_w, 22 + card_h), radius=24, fill=(38, 30, 24, 255),
                                    outline=(96, 66, 48, 255), width=2)
            preview = Image.new("RGBA", (card_w - 32, 156), (0, 0, 0, 0))
            pdraw = ImageDraw.Draw(preview)
            pdraw.rounded_rectangle((0, 0, card_w - 33, 155), radius=16, fill=(56, 27, 16, 255))
            for radius, alpha in ((150, 30), (100, 42), (60, 58)):
                pdraw.ellipse((card_w / 2 - radius - 16, 78 - radius, card_w / 2 + radius - 16, 78 + radius),
                              fill=(*EMBER, alpha))
            logo = mark(76)
            preview.alpha_composite(logo, (int((card_w - 32) / 2 - 38), 40))
            layer.alpha_composite(preview, (36, 36))
            text(ldraw, (38, 206), "oru.freebuff.app", "semi", 28, INK)
            text(ldraw, (38, 244), "Discover music that nobody has heard yet", "reg", 19, FAINT)
            phase = progress(t, 3.96, 4.48)
            if 0 < phase < 1:
                tap_ring(layer, 275, 150, 96, phase)
            slide = (1 - appear) * 40
            paste_center(canvas, layer, (px + 250 + (card_w / 2), y + 120 + slide), appear)
            y += card_h - 52

    for start, end in ((0.0, 0.3), (1.9, 2.22), (3.0, 3.24)):
        typing_dots(canvas, left_x + 4, y + 8, t, start <= t < end)

    # Closing push: zoom on the link.
    zoom = 1 + 0.26 * ease_in(progress(t, 4.2, 4.9))
    out = canvas.convert("RGB")
    if zoom > 1.001:
        out = zoom_center(out, zoom)
    fade = ease_out(progress(t, 0.0, 0.5)) * (1 - progress(t, 4.6, 4.9))
    return blend_black(out, fade)


def scene_invite(t):
    """The mark arrives on paper: ORU, with one line of promise."""
    canvas = Image.new("RGBA", (W, H), (19, 15, 11))
    canvas.alpha_composite(full_bleed((W, H)))
    draw = ImageDraw.Draw(canvas)

    pop = ease_back(progress(t, 0.04, 0.6))
    logo = mark(int(286 * (0.6 + 0.4 * pop)), rotation=(1 - pop) * -14)
    paste_center(canvas, logo, (W / 2, 720), clamp(pop))

    pulse = progress(t, 0.15, 1.3)
    if 0 < pulse < 1:
        ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        rdraw = ImageDraw.Draw(ring)
        radius = 150 + ease_out(pulse) * 460
        rdraw.ellipse((W / 2 - radius, 720 - radius, W / 2 + radius, 720 + radius),
                      outline=(*EMBER, int(120 * (1 - pulse))), width=6)
        canvas.alpha_composite(ring)

    name_in = ease_out(progress(t, 0.3, 0.75))
    text(draw, (W / 2, 900 + (1 - name_in) * 26), "ORU", "display", 158, INK, tracking=16, anchor="ma")

    text(draw, (W / 2, 1140), "MUSIC FROM ARTISTS WHO ARE JUST STARTING", "med", 24, SOFT, tracking=8,
         anchor="ma")
    text(draw, (W / 2, 1210 + (1 - ease_out(progress(t, 0.9, 1.35))) * 16), "oru.freebuff.app", "med", 22,
         EMBER_NEW, tracking=1, anchor="ma")
    return canvas.convert("RGB")


def scene_listener(t):
    """The real player, played up close, with the listening story stacked above it."""
    canvas = radial_background((W, H), glow=(58, 27, 16), strength=1.15).convert("RGBA")

    slide = ease_out(progress(t, 0.0, 0.8))
    phone = device_phone(960 + int(40 * slide), tilt=-3 + 3 * slide, shot=SC_LISTEN_PHONE)
    x = W / 2
    y = 1350 + (1 - slide) * 190 - math.sin(t * 0.75) * 5
    paste_center(canvas, halo_sprite(780), (x, y))
    paste_center(canvas, phone, (x, y), slide)

    draw = ImageDraw.Draw(canvas)
    left = W * 0.07
    first = ease_out(progress(t, 0.2, 0.8))
    second = ease_out(progress(t, 0.5, 1.1))
    text(draw, (left, 300 - (1 - first) * 42), "Underground.", "display_x", 96, INK, tracking=-2)
    text(draw, (left, 412 - (1 - second) * 42), "Found.", "display_x", 96, INK, tracking=-2)

    body = ease_out(progress(t, 0.85, 1.4))
    lines = wrap("Small songs first, from artists posting their first ones. Play them, keep them,", "reg", 32, 900)
    lines2 = wrap("follow the voices you keep hearing.", "reg", 32, 900)
    for index, line in enumerate(lines):
        text(draw, (left, 530 + index * 44 + (1 - body) * 22), line, "reg", 32, SOFT)
    for index, line in enumerate(lines2):
        text(draw, (left, 530 + len(lines) * 44 + index * 44 + (1 - body) * 22), line, "reg", 32, SOFT)

    pill = ease_out(progress(t, 1.2, 1.75)) if slide > 0.9 else 0
    if pill > 0:
        width = 300
        draw.rounded_rectangle((left, 700, left + width, 766), radius=33, fill=(*EMBER, int(255 * pill)))
        text(draw, (left + width / 2, 733), "Keep listening", "bold", 24, (10, 6, 3), anchor="mm")

    # A heart floats off the phone late in the scene, real feedback or nothing.
    like = ease_back(progress(t, 2.8, 3.4)) * (1 - ease_out(progress(t, 4.2, 5.1)))
    if like > 0:
        paste_center(canvas, heart(int(104 * clamp(like))), (x - 250 + 150 * ease_out(clamp(like)),
                      1180 - 200 * ease_out(clamp(like))), clamp(like))
    return canvas.convert("RGB")


def scene_social(t):
    """Two real screens: the send form and the artist's inbox, stacked."""
    canvas = radial_background((W, H), glow=(50, 24, 14), strength=0.9).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    slideA = ease_out(progress(t, 0.05, 0.8))
    slideB = ease_out(progress(t, 0.45, 1.25))
    winA = device_desktop(980, shot=SC_SHARE)
    winB = device_desktop(840, shot=SC_SHARES_INBOX)
    centerA = (W / 2 - 30 + (1 - slideA) * 300, 940 + (1 - slideA) * 140)
    centerB = (W / 2 + 40 - (1 - slideB) * 300, 1580 - 30 * (1 - slideB))
    paste_center(canvas, winA, centerA, slideA)
    paste_center(canvas, winB, centerB, slideB)

    # Copy hops down the gap.
    fromA = (centerA[0] + winA.width * 0.18, centerA[1] + winA.height * 0.28)
    toB = (centerB[0] - winB.width * 0.16, centerB[1] - winB.height * 0.3)
    fly = ease_in_out(progress(t, 0.95, 1.7))
    if 0 < fly < 1:
        cx = lerp(fromA[0], toB[0], fly)
        cy = lerp(fromA[1], toB[1], fly) + math.sin(fly * math.pi) * 70
        note = Image.new("RGBA", (300, 96), (0, 0, 0, 0))
        ndraw = ImageDraw.Draw(note)
        ndraw.rounded_rectangle((0, 10, 299, 85), radius=22, fill=(*EMBER, 255))
        text(ndraw, (150, 47), "shared with a note", "semi", 20, (250, 244, 238), anchor="mm")
        paste_center(canvas, note, (cx, cy), clamp(fly))

    heading = ease_out(progress(t, 1.1, 1.65))
    heading2 = ease_out(progress(t, 1.25, 1.8))
    sub = ease_out(progress(t, 1.45, 1.95))
    text(draw, (W / 2, 250 + (1 - heading) * 24), "send a song", "display_hard",
         72, INK, tracking=-1, anchor="ma")
    text(draw, (W / 2, 345 + (1 - heading2) * 24), "across the room.", "display_hard",
         72, INK, tracking=-1, anchor="ma")
    text(draw, (W / 2, 470 + (1 - sub) * 18), "The artist reviews it, keeps the USG, credits stay honest.",
         "reg", 26, SOFT, anchor="ma")

    cta = ease_out(progress(t, 2.0, 2.5))
    if cta > 0:
        width = 470
        draw.rounded_rectangle((W / 2 - width / 2, 530, W / 2 + width / 2, 588), radius=29,
                               fill=(23, 17, 13, int(236 * cta)), outline=CARD_LINE)
        pill = EMBER
        x_1 = W / 2 - width / 2 + 46
        y_1 = 559
        draw.ellipse((x_1 - 16, y_1 - 16, x_1 + 16, y_1 + 16), fill=(*pill, int(255 * cta)))
        text(draw, (x_1 - 2, y_1), "shares", "semi", 18, (250, 244, 238), anchor="mm")
        text(draw, (x_1 + 36, y_1), "Live feed of real tracks, not stock radio.", "reg", 21, INK,
             anchor="lm")
    return canvas.convert("RGB")


def scene_artist(t):
    """The real studio desk with the three beats that matter."""
    canvas = radial_background((W, H), glow=(52, 25, 15), strength=1.0).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    slide = ease_out(progress(t, 0.0, 0.8))
    window = device_desktop(1000, shot=SC_STUDIO)
    center = (W / 2 + math.sin(t * 0.55) * 8, 850 + (1 - slide) * 170 + math.cos(t * 0.45) * 5)
    paste_center(canvas, window, center, slide)

    for sheen_start, sheen_end in ((0.25, 1.7), (2.4, 3.7)):
        if not (sheen_start <= t <= sheen_end):
            continue
        sheen = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        sdraw = ImageDraw.Draw(sheen)
        sx = lerp(-640, W + 640, ease_in_out(progress(t, sheen_start, sheen_end)))
        sdraw.polygon([(sx, 0), (sx + 280, 0), (sx - 130, H), (sx - 410, H)], fill=(255, 250, 244, 14))
        canvas.alpha_composite(sheen)

    heading = ease_out(progress(t, 0.3, 0.85))
    text(draw, (W / 2, 220 + (1 - heading) * 22), "the studio is a page.", "display_hard", 72, INK,
         tracking=-1, anchor="ma")

    bar = ease_out(progress(t, 0.55, 1.1))
    if bar > 0:
        strip = Image.new("RGBA", (1000, 250), (0, 0, 0, 0))
        sdraw = ImageDraw.Draw(strip)
        sdraw.rounded_rectangle((0, 0, 999, 249), radius=28, fill=(24, 19, 15, 240), outline=CARD_LINE, width=2)
        steps = [(66, "Record or upload"), (66, "Credit your guests"), (66, "Publish when it is ready")]
        rows = [62, 125, 188]
        starts = [0.7, 0.95, 1.2]
        for (cx, label), y_1, start in zip(steps, rows, starts):
            appear = ease_out(progress(t, start, start + 0.45))
            if appear <= 0:
                continue
            sdraw.ellipse((cx - 30, y_1 - 22, cx + 10, y_1 + 18), fill=(*EMBER, int(255 * appear)))
            sdraw.line([(cx - 20, y_1 - 1), (cx - 12, y_1 + 7), (cx + 0, y_1 - 9)], fill=(10, 7, 4), width=5,
                       joint="curve")
            text(sdraw, (cx + 46, y_1), label, "semi", 28, INK, anchor="lm")
        paste_center(canvas, strip, (W / 2, 1330), bar)
    return canvas.convert("RGB")


def scene_landing(t):
    """The front page, full size, with one honest line under it."""
    canvas = radial_background((W, H), glow=(46, 22, 13), strength=0.8).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    slide = ease_out(progress(t, 0.0, 0.75))
    window = device_desktop(1020, shot=SC_LANDING_DESK)
    center = (W / 2 + (1 - slide) * 60 + math.sin(t * 0.6) * 7,
              760 + (1 - slide) * 150 + math.cos(t * 0.5) * 5)
    paste_center(canvas, window, center, slide)

    heading = ease_out(progress(t, 0.3, 0.9))
    sub = ease_out(progress(t, 0.65, 1.2))
    lines = wrap("Everything you hear was someone's first try.", "display_hard", 62, 940)
    for index, line in enumerate(lines):
        text(draw, (W / 2, 1190 + index * 74 + (1 - heading) * 18), line, "display_hard",
             62, INK, tracking=-1, anchor="ma")
    text(draw, (W / 2, 1360 + (1 - sub) * 12), "Discover where new sounds start, or start them yourself.",
         "reg", 26, SOFT, anchor="ma")
    return canvas.convert("RGB")


def scene_end(t):
    """The reveal stays put. Big mark, one line, one place to go."""
    canvas = Image.new("RGBA", (W, H), (19, 15, 11))
    canvas.alpha_composite(full_bleed((W, H)))
    draw = ImageDraw.Draw(canvas)

    pop = ease_back(progress(t, 0.05, 0.7))
    logo = mark(int(240 * (0.82 + 0.18 * pop)))
    paste_center(canvas, logo, (W / 2, 660), clamp(pop))

    text(draw, (W / 2, 800 + (1 - ease_out(progress(t, 0.25, 0.8))) * 20), "ORU", "display", 150, INK,
         tracking=14, anchor="ma")

    line = ease_out(progress(t, 0.5, 1.05))
    text(draw, (W / 2, 1040 + (1 - line) * 16), "Discover the small sound. Send it on.", "med", 40, SOFT,
         anchor="ma")

    cta = ease_out(progress(t, 0.9, 1.5))
    if cta > 0:
        width, height = 560, 100
        box = (W / 2 - width / 2, 1150, W / 2 + width / 2, 1150 + height)
        draw.rounded_rectangle(box, radius=50, fill=(*EMBER, int(255 * cta)))
        text(draw, (W / 2, 1200), "oru.freebuff.app", "bold", 36, (12, 7, 3), anchor="mm")

    tagline = ease_out(progress(t, 1.3, 1.9))
    text(draw, (W / 2, 1330), "MUSIC FROM ARTISTS WHO ARE JUST STARTING", "semi", 20, FAINT,
         tracking=6, anchor="ma")
    # Hold the reveal, then one clean fade to black under the tail of the music.
    # blend_black takes the share of the real image, so it runs 1 -> 0.
    return blend_black(canvas.convert("RGB"), 1 - ease_in(progress(t, 5.7, 6.55)))


# --------------------------------------------------------------- timeline ----
SCENES = [
    (0.0, 4.9, scene_chat),
    (4.9, 6.5, scene_invite),
    (6.5, 11.6, scene_listener),
    (11.6, 16.3, scene_social),
    (16.3, 20.8, scene_artist),
    (20.8, 23.9, scene_landing),
    (23.9, 30.5, scene_end),
]
XFADE = 0.35


def scene_at(time):
    for index, (start, end, fn) in enumerate(SCENES):
        if start <= time < end:
            return index, start, end, fn
    return len(SCENES) - 1, *SCENES[-1][:2], SCENES[-1][2]


def render_frame(time):
    index, start, end, fn = scene_at(time)
    local = time if not index == len(SCENES) - 1 or start <= time < end else time - start
    if start <= time < end:
        local = time - start
    else:
        local = end - start - 0.0001
    frame = finish(fn(local if start <= time < end else end - start - 0.0001), time)

    next_index = index + 1
    if next_index < len(SCENES) and end - time < XFADE:
        next_start, next_end, next_fn = SCENES[next_index]
        mix = ease_in_out(1 - (end - time) / XFADE)
        upcoming = finish(next_fn(0.0001), time)
        # A straight cross-fade now that nothing is blurred over the seam: the
        # old version softened the whole frame by up to 6.5px to hide it.
        frame = Image.blend(frame, upcoming, mix)
    if time < 0.5:
        frame = blend_black(frame, ease_out(time / 0.5))
    return brighten(finish(frame, time))


# ---------------------------------------------------------------- audio -----
def synth_audio(path):
    rate = 44100
    total = int(DURATION * rate)
    mix = np.zeros((total, 2), dtype=np.float32)

    def add(signal, at, gain=1.0, pan=0.0):
        start = int(at * rate)
        end = min(start + len(signal), total)
        if start >= total:
            return
        chunk = signal[: end - start] * gain
        left = math.sqrt((1 - pan) / 2)
        right = math.sqrt((1 + pan) / 2)
        mix[start:end, 0] += chunk * left
        mix[start:end, 1] += chunk * right

    def env(length, attack, decay, curve=2.4):
        n = int(length * rate)
        t = np.linspace(0, length, n, endpoint=False)
        a = np.clip(t / max(attack, 1e-4), 0, 1)
        d = np.exp(-t / max(decay, 1e-4))
        return (a * d) ** (1 / curve)

    def tone(freq, length, attack=0.005, decay=0.25, harmonics=(1.0, 0.4, 0.18), detune=0.0):
        n = int(length * rate)
        t = np.arange(n) / rate
        signal = np.zeros(n, dtype=np.float32)
        for index, level in enumerate(harmonics):
            signal += level * np.sin(2 * math.pi * freq * (index + 1) * t + detune)
        return signal * env(length, attack, decay)

    def noise(length, decay=0.08):
        n = int(length * rate)
        return RNG.normal(0, 0.5, n).astype(np.float32) * env(length, 0.001, decay)

    def sweep(start_freq, end_freq, length, gain=1.0):
        n = int(length * rate)
        t = np.arange(n) / rate
        freq = np.linspace(start_freq, end_freq, n)
        phase = np.cumsum(2 * math.pi * freq / rate)
        return (np.sin(phase) * env(length, 0.02, length * 0.55, 1.6) * gain).astype(np.float32)

    bpm = 92
    beat = 60 / bpm

    # Warm pad bed, whole film long.
    chords = [(87.31, [1.0, 1.21, 1.5, 2.0]), (110.0, [1.0, 1.19, 1.5, 1.78]),
              (73.42, [1.0, 1.25, 1.5, 2.0]), (98.0, [1.0, 1.2, 1.5, 1.8])]
    bar = beat * 4
    for index in range(0, int(DURATION / bar) + 1):
        at = index * bar
        root, ratios = chords[index % len(chords)]
        for ratio in ratios:
            add(tone(root * ratio, bar * 1.2, attack=0.55, decay=2.8, harmonics=(0.65, 0.2, 0.08)), at, gain=0.055)
        add(tone(root / 2, bar * 1.1, attack=0.14, decay=1.8, harmonics=(1.0, 0.2)), at, gain=0.1)

    # Pulse from the listener section until the reveal.
    pulse_start, pulse_end = 6.5, 23.9
    step = beat / 2
    index = 0
    while pulse_start + index * step < pulse_end:
        at = pulse_start + index * step
        if index % 2 == 0:
            add(tone(56, 0.34, attack=0.002, decay=0.12, harmonics=(1.0, 0.3)), at, gain=0.45)
            add(noise(0.03, 0.015), at, gain=0.14)
        else:
            add(noise(0.055, 0.03), at, gain=0.085, pan=0.25)
        if index % 4 == 2:
            add(noise(0.14, 0.09), at, gain=0.08, pan=-0.2)
        index += 1

    # Arpeggio sparkles against the pulse.
    arp_notes = [261.63, 311.13, 392.0, 466.16, 392.0, 311.13]
    index = 0
    while pulse_start + index * beat < pulse_end - 1:
        at = pulse_start + index * beat
        note = arp_notes[index % len(arp_notes)]
        add(tone(note, 0.5, attack=0.004, decay=0.15, harmonics=(0.75, 0.3, 0.12)), at, gain=0.05,
            pan=math.sin(index) * 0.4)
        index += 1

    # Typing clicks in the chat.
    for at in (0.3, 0.54, 0.78, 1.02, 1.26, 1.5, 1.72, 1.9):
        add(noise(0.02, 0.01), at, gain=0.045, pan=0.12)
    add(tone(740, 0.5, attack=0.005, decay=0.22, harmonics=(1.0, 0.5, 0.22)), 2.26, gain=0.15)
    add(tone(1110, 0.45, attack=0.005, decay=0.2, harmonics=(1.0, 0.3)), 2.33, gain=0.07)
    add(sweep(340, 1900, 0.35, 0.32), 3.96, gain=0.11)

    # Scene whooshes on the beat - every transition lands audibly.
    for at in (4.9, 6.5, 11.6, 16.3, 20.8, 23.9):
        add(sweep(200, 2600, 0.55, 0.4), at - 0.2, gain=0.09)
    add(tone(116, 0.6, attack=0.005, decay=0.2, harmonics=(1.0, 0.5)), 4.9, gain=0.3)

    # Heart pop in the listener scene.
    add(tone(932.33, 0.4, attack=0.004, decay=0.18, harmonics=(1.0, 0.5)), 9.3, gain=0.13)

    # Closing hit with a long shimmer.
    add(tone(87.31, 2.6, attack=0.01, decay=1.3, harmonics=(1.0, 0.5, 0.25)), 23.9, gain=0.5)
    add(tone(349.23, 3.4, attack=0.02, decay=1.7, harmonics=(0.6, 0.25, 0.1)), 23.9, gain=0.15)
    add(tone(523.25, 3.6, attack=0.03, decay=2.1, harmonics=(0.5, 0.2, 0.1)), 23.95, gain=0.09)
    add(noise(1.3, 0.4), 23.9, gain=0.09)

    # Gentle room tail.
    delay = int(0.16 * rate)
    echo = mix.copy()
    for _ in range(3):
        echo[delay:] += echo[:-delay] * 0.24
    mix = mix * 0.85 + echo * 0.15
    mix = np.tanh(mix * 1.25) * 0.86
    fade_in = int(0.25 * rate)
    fade_out = int(1.4 * rate)
    mix[:fade_in] *= np.linspace(0, 1, fade_in)[:, None]
    mix[-fade_out:] *= np.linspace(1, 0, fade_out)[:, None]

    pcm = (np.clip(mix, -1, 1) * 32767).astype(np.int16)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(2)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(pcm.tobytes())


# ---------------------------------------------------------------- runner ----
def chunk(first, last):
    WORK.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    audio_path = WORK / "soundtrack.wav"
    if not audio_path.exists():
        print("writing soundtrack")
        synth_audio(audio_path)

    target = WORK / f"chunk-{first:04d}.mp4"
    command = [
        ffmpeg_path(), "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", str(target),
    ]
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        for index in range(first, last):
            time = index / FPS
            frame = render_frame(time)
            process.stdin.write(frame.tobytes())
            if index % 100 == 0:
                print(f"frame {index}/{get_last()}", flush=True)
        process.stdin.close()
    except BrokenPipeError:
        pass
    error = process.stderr.read().decode("utf-8", "replace")
    code = process.wait()
    if code != 0:
        print(error[-3000:], file=sys.stderr)
        raise SystemExit(f"ffmpeg failed with {code}")
    print("wrote", target, round(target.stat().st_size / (1024 * 1024), 1), "MB")


def get_last():
    return FRAMES


def concat():
    parts = sorted(WORK.glob("chunk-*.mp4"))
    if not parts:
        raise SystemExit("no chunks to concat")
    entries = "\n".join(f"file '{part.absolute()}'" for part in parts)
    listfile = WORK / "list.txt"
    listfile.write_text(entries)
    audio = WORK / "soundtrack.wav"
    output = OUT / "oru-promo.mp4"
    command = [
        ffmpeg_path(), "-y", "-f", "concat", "-safe", "0", "-i", str(listfile),
        "-i", str(audio), "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
        "-shortest", "-movflags", "+faststart", str(output),
    ]
    process = subprocess.run(command, capture_output=True, text=True)
    if process.returncode != 0:
        print(process.stderr[-3000:], file=sys.stderr)
        raise SystemExit(process.returncode)
    for part in parts:
        part.unlink()
    print("wrote", output, round(output.stat().st_size / (1024 * 1024), 1), "MB")


def main():
    if "--still" in sys.argv:
        index = int(sys.argv[sys.argv.index("--still") + 1])
        WORK.mkdir(parents=True, exist_ok=True)
        still = WORK / f"still-{index:05d}.png"
        render_frame(index / FPS).save(still)
        print("wrote", still)
        return
    if "--concat" in sys.argv:
        concat()
        return
    first = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    last = int(sys.argv[2]) if len(sys.argv) > 2 else FRAMES
    chunk(first, last)


if __name__ == "__main__":
    main()
