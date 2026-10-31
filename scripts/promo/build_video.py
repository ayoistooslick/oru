"""Build the ORU promo video.

Renders every frame with Pillow at 1920x1080, pipes the frames straight into
ffmpeg, and writes the soundtrack with numpy. The product shots are the real
screenshots from public/screenshots, so the film shows the actual app.

Run with:  .venv/bin/python scripts/promo/build_video.py
"""

import math
import os
import subprocess
import sys
import wave
from pathlib import Path
from urllib.request import urlopen

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / ".promo" / "fonts"
SHOTS = ROOT / "public" / "screenshots"
OUT = ROOT / "public" / "promo"
WORK = ROOT / ".promo"

W, H, FPS = 1920, 1080, 30
DURATION = 34.0
FRAMES = int(DURATION * FPS)

INK = (255, 255, 255)
SOFT = (179, 179, 179)
FAINT = (122, 122, 122)
GREEN = (30, 215, 96)
BLACK = (8, 8, 8)
CARD = (24, 24, 24)
CREAM = (244, 241, 236)
BRAND_RED = (210, 59, 24)

FFMPEG = ROOT / "node_modules" / "ffmpeg-static" / "ffmpeg"


# ---------------------------------------------------------------- easing ----
def clamp(value, low=0.0, high=1.0):
    return max(low, min(high, value))


def progress(t, start, end):
    if end <= start:
        return 1.0
    return clamp((t - start) / (end - start))


def ease_out(t):
    return 1 - (1 - t) ** 3


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
FONT_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/figtree/Figtree%5Bwght%5D.ttf"


def ensure_font() -> Path:
    """Figtree is the app's typeface; fetch it once if it is not there yet."""
    target = FONTS / "Figtree-var.ttf"
    if not target.exists():
        FONTS.mkdir(parents=True, exist_ok=True)
        print("downloading Figtree")
        with urlopen(FONT_URL) as response, target.open("wb") as handle:
            handle.write(response.read())
    return target


class Fonts:
    """One variable Figtree file, switched to the weight each line needs."""

    AXES = {"black": 800, "bold": 700, "semi": 600, "med": 500, "reg": 400}

    def __init__(self):
        self.cache = {}
        self.path = ensure_font()

    def get(self, weight, size):
        key = (weight, size)
        if key not in self.cache:
            font = ImageFont.truetype(str(self.path), size)
            try:
                font.set_variation_by_axes([self.AXES[weight]])
            except Exception:
                pass
            self.cache[key] = font
        return self.cache[key]


FON = Fonts()


def text(draw, xy, value, weight="semi", size=48, fill=INK, tracking=0.0, anchor="la"):
    font = FON.get(weight, size)
    x, y = xy
    if tracking == 0:
        draw.text((x, y), value, font=font, fill=fill, anchor=anchor)
        return font.getlength(value)
    width = sum(font.getlength(ch) for ch in value) + tracking * max(len(value) - 1, 0)
    if anchor[0] == "c":
        x -= width / 2
    elif anchor[0] == "r":
        x -= width
    cursor = x
    for ch in value:
        draw.text((cursor, y), ch, font=font, fill=fill, anchor="l" + anchor[1])
        cursor += font.getlength(ch) + tracking
    return width


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


_BACKGROUNDS = {}
_GLOWS = {}


def radial_background(size, glow=(20, 90, 60), strength=1.0):
    key = (size, glow, strength)
    if key in _BACKGROUNDS:
        return _BACKGROUNDS[key].copy()
    w, h = size
    small = Image.new("RGB", (w // 8, h // 8), BLACK)
    draw = ImageDraw.Draw(small)
    for radius in range(min(small.size), 0, -6):
        ratio = radius / min(small.size)
        colour = tuple(int(lerp(BLACK[i], glow[i] * strength, (1 - ratio) ** 2.2)) for i in range(3))
        draw.ellipse((-radius, -radius, radius, radius), fill=colour)
    image = small.resize(size, Image.LANCZOS)
    _BACKGROUNDS[key] = image
    return image.copy()


def glow_layer(size):
    if size not in _GLOWS:
        layer = Image.new("RGBA", size, (0, 0, 0, 0))
        draw = ImageDraw.Draw(layer)
        draw.ellipse((size[0] / 2 - 420, size[1] / 2 - 420, size[0] / 2 + 420, size[1] / 2 + 420), fill=(30, 215, 96, 70))
        _GLOWS[size] = layer.filter(ImageFilter.GaussianBlur(160))
    return _GLOWS[size].copy()


def halo_sprite(diameter=900):
    key = ("halo", diameter)
    if key not in _GLOWS:
        layer = Image.new("RGBA", (diameter, diameter), (0, 0, 0, 0))
        ImageDraw.Draw(layer).ellipse((0, 0, diameter - 1, diameter - 1), fill=(30, 215, 96, 40))
        _GLOWS[key] = layer.filter(ImageFilter.GaussianBlur(90))
    return _GLOWS[key]


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
            self.cache[height] = self.original.resize((width, height), Image.LANCZOS)
        return self.cache[height]

    def crop(self, box):
        return self.original.crop(box)


PHONE = Shot(SHOTS / "home-phone.png")
DESKTOP = Shot(SHOTS / "home-desktop.png")


_DEVICES = {}


def device_phone(image_height=880, tilt=0.0, screen_radius=52):
    key = (int(image_height / 4), round(tilt / 0.5))
    if key in _DEVICES:
        return _DEVICES[key]
    image_height = key[0] * 4
    tilt = key[1] * 0.5
    shot = PHONE.sized(image_height)
    bezel = 16
    frame = Image.new("RGBA", (shot.width + bezel * 2, shot.height + bezel * 2), (0, 0, 0, 0))
    draw = ImageDraw.Draw(frame)
    draw.rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), radius=screen_radius + bezel, fill=(12, 12, 12, 255),
                           outline=(60, 60, 60, 255), width=3)
    mask = rounded_mask(shot.size, screen_radius)
    frame.paste(shot, (bezel, bezel), mask)
    # Status pill so the shot reads as a phone rather than a flat crop.
    draw.rounded_rectangle((frame.width / 2 - 46, bezel + 12, frame.width / 2 + 46, bezel + 34), radius=11, fill=(6, 6, 6, 255))
    if tilt:
        frame = frame.rotate(tilt, resample=Image.BICUBIC, expand=True)
    _DEVICES[key] = frame
    return frame


_DESKTOPS = {}


def device_desktop(width=1500, radius=22):
    width = int(width / 20) * 20
    if width in _DESKTOPS:
        return _DESKTOPS[width]
    shot = DESKTOP.sized(int(width * DESKTOP.original.height / DESKTOP.original.width))
    chrome = 34
    frame = Image.new("RGBA", (shot.width, shot.height + chrome), (0, 0, 0, 0))
    draw = ImageDraw.Draw(frame)
    draw.rounded_rectangle((0, 0, frame.width - 1, frame.height - 1), radius=radius, fill=(18, 18, 18, 255),
                           outline=(70, 70, 70, 255), width=2)
    for index, colour in enumerate([(240, 70, 60), (245, 190, 70), (60, 200, 95)]):
        draw.ellipse((22 + index * 26, 12, 32 + index * 26, 22), fill=colour)
    draw.rounded_rectangle((140, 8, 420, 26), radius=9, fill=(40, 40, 40))
    text(draw, (158, 10), "oru.freebuff.app", "med", 12, (150, 150, 150))
    mask = rounded_mask(shot.size, 12)
    frame.paste(shot, (0, chrome), mask)
    _DESKTOPS[width] = frame
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
    """The ORU mark, drawn from public/mark.svg geometry."""
    scale = 4
    canvas = Image.new("RGBA", (size * scale, size * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)
    unit = size * scale / 64
    draw.ellipse((1 * unit, 1 * unit, 63 * unit, 63 * unit), fill=BRAND_RED)
    draw.ellipse((1 * unit, 1 * unit, 63 * unit, 63 * unit), outline=(23, 19, 15), width=int(4 * unit))
    draw.polygon([(27 * unit, 23 * unit), (45 * unit, 32 * unit), (27 * unit, 41 * unit)], fill=CREAM,
                 outline=CREAM, width=int(5 * unit))
    canvas = canvas.resize((size, size), Image.LANCZOS)
    return canvas.rotate(rotation, resample=Image.BICUBIC, expand=True) if rotation else canvas


def bubble(canvas, box, radius, fill, tail="left"):
    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(box, radius=radius, fill=fill)
    if tail == "left":
        x, y = box[0] + 6, box[3] - 18
        draw.polygon([(x, y), (x + 26, y + 20), (x + 4, y + 24)], fill=fill)
    elif tail == "right":
        x, y = box[2] - 6, box[3] - 18
        draw.polygon([(x, y), (x - 26, y + 20), (x - 4, y + 24)], fill=fill)


def heart(size, colour=GREEN, filled=True):
    layer = Image.new("RGBA", (size * 4, size * 4), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    s = size * 4
    draw.polygon([
        (s * 0.5, s * 0.86), (s * 0.12, s * 0.46), (s * 0.12, s * 0.27), (s * 0.26, s * 0.14),
        (s * 0.42, s * 0.16), (s * 0.5, s * 0.3), (s * 0.58, s * 0.16), (s * 0.74, s * 0.14),
        (s * 0.88, s * 0.27), (s * 0.88, s * 0.46),
    ], fill=colour if filled else None, outline=colour, width=int(size * 0.16) if not filled else 0)
    return layer.resize((size, size), Image.LANCZOS)


def play_triangle(draw, cx, cy, size, colour=(0, 0, 0)):
    draw.polygon([(cx - size * 0.32, cy - size * 0.46), (cx + size * 0.46, cy), (cx - size * 0.32, cy + size * 0.46)],
                 fill=colour)


# ----------------------------------------------------------------- grain ----
RNG = np.random.default_rng(7)
GRAIN = [RNG.normal(0, 1.7, (H, W, 1)).astype(np.float32) for _ in range(6)]
_YY, _XX = np.mgrid[0:H, 0:W]
_NX = (_XX - W / 2) / (W / 2)
_NY = (_YY - H / 2) / (H / 2)
VIGNETTE = (1 - 0.55 * np.clip((_NX * _NX + _NY * _NY) * 0.42, 0, 1))[..., None].astype(np.float32)


def finish(frame, timecode, grain=1.0):
    array = np.asarray(frame.convert("RGB")).astype(np.float32)
    array += GRAIN[int(timecode * 30) % len(GRAIN)] * grain
    array *= VIGNETTE
    return Image.fromarray(np.clip(array, 0, 255).astype(np.uint8), "RGB")


# ---------------------------------------------------------------- scenes ----
def scene_chat(t):
    """Treasure tells Success about ORU. iMessage rhythm, ORU colours."""
    base = radial_background((W, H), glow=(16, 70, 50), strength=0.9)
    veil = Image.new("RGBA", (W, H), (6, 8, 7, 235))
    base = Image.alpha_composite(base.convert("RGBA"), veil).convert("RGB")
    canvas = base.convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    panel_w, panel_h = 980, 800
    px, py = (W - panel_w) / 2, (H - panel_h) / 2 + 20

    # The conversation sits on its own sheet, so it reads as a chat and not as
    # loose text floating on the background.
    draw.rounded_rectangle((px - 26, py - 26, px + panel_w + 26, py + panel_h + 26), radius=42,
                           fill=(17, 18, 18, 240), outline=(44, 45, 46, 255), width=2)

    # Header
    head = Image.new("RGBA", (panel_w, 96), (0, 0, 0, 0))
    hdraw = ImageDraw.Draw(head)
    hdraw.rounded_rectangle((0, 0, panel_w - 1, 96), radius=28, fill=(26, 26, 28, 255))
    hdraw.ellipse((26, 24, 74, 72), fill=(58, 58, 62, 255))
    text(hdraw, (50, 48), "T", "bold", 24, (255, 255, 255), anchor="mm")
    text(hdraw, (92, 32), "Treasure", "semi", 26, (245, 245, 245))
    text(hdraw, (92, 60), "iMessage", "reg", 17, (130, 130, 135))
    canvas.paste(head, (int(px), int(py)), head)

    messages = [
        ("left", "guy you don hear of that", 0.35, 1.55),
        ("left", "underground musician for oru?", 1.55, 2.75),
        ("right", "No, which one be oru again?", 3.05, 4.05),
        ("link", "", 4.35, 6.3),
    ]

    y = py + 150
    left_x, right_x = px + 20, px + panel_w - 20
    for kind, label, start, end in messages:
        if t < start:
            break
        appear = ease_out(progress(t, start, start + 0.35))
        if kind == "left":
            font_size = 30
            width = FON.get("med", font_size).getlength(label)
            box = (left_x, y, left_x + width + 52, y + 68)
            layer = Image.new("RGBA", (int(box[2] - box[0]) + 40, 110), (0, 0, 0, 0))
            bubble(layer, (30, 8, 30 + (box[2] - box[0]), 70), 26, (44, 44, 46, 255), "left")
            draw_layer = ImageDraw.Draw(layer)
            text(draw_layer, (56, 26), label, "med", font_size, (245, 245, 245))
            paste_center(canvas, layer, ((box[0] + box[2]) / 2 + 6, (box[1] + box[3]) / 2), appear)
            y += 96
        elif kind == "right":
            font_size = 30
            width = FON.get("med", font_size).getlength(label)
            box_right = right_x
            box_left = box_right - (width + 52)
            layer = Image.new("RGBA", (int(width) + 90, 110), (0, 0, 0, 0))
            bubble(layer, (20, 8, 20 + (width + 52), 70), 26, (52, 199, 89, 255), "right")
            text(ImageDraw.Draw(layer), (46, 26), label, "med", font_size, (250, 255, 250))
            paste_center(canvas, layer, ((box_left + box_right) / 2, y + 39), appear)
            y += 96
        else:
            # Link bubble with the real ORU address
            card_w, card_h = 560, 300
            layer = Image.new("RGBA", (card_w + 40, card_h + 40), (0, 0, 0, 0))
            ldraw = ImageDraw.Draw(layer)
            ldraw.rounded_rectangle((20, 20, 20 + card_w, 20 + card_h), radius=26, fill=(38, 38, 42, 255))
            preview = Image.new("RGBA", (card_w - 32, 170), (0, 0, 0, 0))
            pdraw = ImageDraw.Draw(preview)
            pdraw.rounded_rectangle((0, 0, card_w - 33, 169), radius=18, fill=(14, 22, 17, 255))
            for radius, alpha in ((150, 26), (100, 34), (60, 44)):
                pdraw.ellipse((card_w / 2 - radius, 84 - radius, card_w / 2 + radius - 32, 84 + radius),
                              fill=(30, 215, 96, alpha))
            logo = mark(86)
            preview.alpha_composite(logo, (int((card_w - 32) / 2 - 43), 40))
            layer.alpha_composite(preview, (36, 36))
            text(ldraw, (36, 218), "oru.freebuff.app", "semi", 30, (90, 170, 255))
            text(ldraw, (36, 258), "ORU, music from new artists", "reg", 22, (150, 150, 155))
            # The tap: a ring that grows where the finger lands.
            tap = progress(t, 5.55, 6.3)
            if 0 < tap < 1:
                ring = Image.new("RGBA", (card_w + 40, card_h + 40), (0, 0, 0, 0))
                rdraw = ImageDraw.Draw(ring)
                radius = 20 + ease_out(tap) * 90
                rdraw.ellipse((300 - radius, 300 - radius, 300 + radius, 300 + radius),
                              outline=(30, 215, 96, int(200 * (1 - tap))), width=5)
                layer.alpha_composite(ring)
            slide = (1 - appear) * 40
            paste_center(canvas, layer, (px + 220 + (card_w / 2), y + 130 + slide), appear)
            y += card_h - 40

    # Typing dots while the other person is about to write.
    for start, end in ((0.0, 0.4), (2.75, 3.05), (4.05, 4.35)):
        if start <= t < end:
            dots = Image.new("RGBA", (110, 60), (0, 0, 0, 0))
            ddraw = ImageDraw.Draw(dots)
            ddraw.rounded_rectangle((0, 0, 109, 59), radius=26, fill=(44, 44, 46, 255))
            for index in range(3):
                bob = math.sin((t * 6) - index * 0.7) * 4
                ddraw.ellipse((20 + index * 26, 22 + bob, 36 + index * 26, 38 + bob), fill=(150, 150, 155, 255))
            canvas.paste(dots, (int(left_x), int(y)), dots)

    # Time stamp fades out before the zoom transition.
    text(draw, (W / 2, py + panel_h - 6), "Today  21:42", "reg", 22, (90, 90, 95), anchor="mm")

    out = canvas.convert("RGB")
    # Push in on the link as the scene ends.
    zoom = 1 + 0.22 * ease_in(progress(t, 5.6, 6.6))
    if zoom > 1.001:
        out = zoom_center(out, zoom)
    fade = ease_out(progress(t, 0.0, 0.5)) * (1 - progress(t, 6.2, 6.6))
    return blend_black(out, fade)


def zoom_center(image, factor):
    w, h = image.size
    nw, nh = int(w / factor), int(h / factor)
    box = ((w - nw) // 2, (h - nh) // 2, (w + nw) // 2, (h + nh) // 2)
    return image.crop(box).resize((w, h), Image.LANCZOS)


def blend_black(image, amount):
    if amount >= 0.999:
        return image
    return Image.blend(Image.new("RGB", image.size, (0, 0, 0)), image, clamp(amount))


def scene_logo(t):
    """The app arrives: mark, name, promise."""
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    canvas.alpha_composite(glow_layer((W, H)))
    draw = ImageDraw.Draw(canvas)

    pop = ease_back(progress(t, 0.05, 0.85))
    logo = mark(int(300 * (0.6 + 0.4 * pop)), rotation=(1 - pop) * -12)
    paste_center(canvas, logo, (W / 2, 430), clamp(pop))

    # Ring pulse behind the mark
    pulse = progress(t, 0.2, 1.6)
    if 0 < pulse < 1:
        ring = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        rdraw = ImageDraw.Draw(ring)
        radius = 150 + ease_out(pulse) * 420
        rdraw.ellipse((W / 2 - radius, 430 - radius, W / 2 + radius, 430 + radius),
                      outline=(30, 215, 96, int(120 * (1 - pulse))), width=6)
        canvas.alpha_composite(ring)

    name_in = ease_out(progress(t, 0.5, 1.1))
    text(draw, (W / 2, 620 + (1 - name_in) * 26), "ORU", "black", 156, INK, tracking=14, anchor="ma")
    promise = "MUSIC FROM ARTISTS WHO ARE JUST STARTING"
    text(draw, (W / 2, 800), promise, "semi", 26, (150, 150, 155), tracking=6, anchor="ma")
    text(draw, (W / 2, 852 + (1 - ease_out(progress(t, 1.0, 1.6))) * 18), "oru.freebuff.app", "med", 24,
         (90, 170, 255), tracking=1, anchor="ma")
    return canvas.convert("RGB")


def scene_phone(t):
    """Real home screen in a phone, with kinetic type beside it."""
    canvas = radial_background((W, H), glow=(22, 96, 62), strength=1.2).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    slide = ease_out(progress(t, 0.0, 1.0))
    phone = device_phone(880, tilt=-3 + 3 * slide)
    x = lerp(W * 0.72, W * 0.68, slide)
    y = H / 2 + (1 - slide) * 220
    # Slow drift so the shot feels alive without inventing new screens.
    y -= math.sin(t * 0.8) * 4
    paste_center(canvas, phone, (x, y), slide)

    # Glow behind the device
    halo = halo_sprite(760)
    paste_center(canvas, halo, (x, y), 1.0)

    left = W * 0.085
    top = 330
    first = ease_out(progress(t, 0.25, 0.95))
    second = ease_out(progress(t, 0.6, 1.35))
    text(draw, (left, top - (1 - first) * 40), "Underground.", "black", 96, INK, tracking=-2)
    text(draw, (left, top + 110 - (1 - second) * 40), "Found.", "black", 96, GREEN, tracking=-2)

    body = ease_out(progress(t, 1.1, 1.7))
    lines = wrap("New artists put a song up in minutes. You hear it first.", "reg", 34, 560)
    for index, line in enumerate(lines):
        text(draw, (left, top + 260 + index * 44 + (1 - body) * 24), line, "reg", 34, (200, 200, 200))

    pill = ease_out(progress(t, 1.5, 2.1))
    if pill > 0:
        width = 330
        draw.rounded_rectangle((left, top + 400, left + width, top + 468), radius=34,
                               fill=(30, 215, 96, int(255 * pill)))
        text(draw, (left + width / 2, top + 434), "Listen free", "bold", 26, (0, 0, 0), anchor="mm")
    return canvas.convert("RGB")


def scene_studio(t):
    """Desktop app, with the three steps that matter."""
    canvas = radial_background((W, H), glow=(18, 70, 55), strength=0.9).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    slide = ease_out(progress(t, 0.0, 1.0))
    window = device_desktop(1400)
    center = (W / 2, 470 + (1 - slide) * 160)
    paste_center(canvas, window, center, slide)

    # Two bars of light sweeping across the window as it settles.
    sheen = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(sheen)
    sx = lerp(-600, W + 600, ease_in_out(progress(t, 0.2, 1.6)))
    sdraw.polygon([(sx, 0), (sx + 260, 0), (sx - 120, H), (sx - 380, H)], fill=(255, 255, 255, 16))
    canvas.alpha_composite(sheen)

    # One strip under the window carries the three steps, so nothing covers the
    # product itself.
    bar = ease_out(progress(t, 0.55, 1.15))
    if bar > 0:
        strip = Image.new("RGBA", (1640, 104), (0, 0, 0, 0))
        sdraw = ImageDraw.Draw(strip)
        sdraw.rounded_rectangle((0, 0, 1639, 103), radius=28, fill=(16, 16, 16, 236), outline=(56, 56, 56, 255), width=2)
        steps = [("Record or upload", 0.75), ("Trim and fade it", 1.05), ("Publish when ready", 1.35)]
        slots = [410, 1030, 1500]
        for index, (label, start) in enumerate(steps):
            appear = ease_out(progress(t, start, start + 0.45))
            if appear <= 0:
                continue
            cx = slots[index]
            sdraw.ellipse((cx - 300, 32, cx - 260, 72), fill=(30, 215, 96, int(255 * appear)))
            sdraw.line([(cx - 290, 52), (cx - 282, 61), (cx - 270, 43)], fill=(0, 0, 0), width=5, joint="curve")
            text(sdraw, (cx - 246, 52), label, "semi", 28, (240, 240, 240), anchor="lm")
        paste_center(canvas, strip, (W / 2, 990), bar)

    return canvas.convert("RGB")


def scene_listen(t):
    """Zoom on the real player, then like the track."""
    canvas = Image.new("RGBA", (W, H), (9, 9, 9, 255))
    # Big blurred version of the artwork colour field behind.
    if "listen-backdrop" not in _GLOWS:
        blurred = PHONE.sized(700).filter(ImageFilter.GaussianBlur(38)).resize((W, H), Image.LANCZOS)
        _GLOWS["listen-backdrop"] = blurred
    canvas.paste(_GLOWS["listen-backdrop"], (0, 0))
    canvas.alpha_composite(Image.new("RGBA", (W, H), (8, 8, 8, 200)))

    draw = ImageDraw.Draw(canvas)
    zoom = ease_in_out(progress(t, 0.0, 1.4))
    phone = device_phone(700 + int(180 * zoom))
    paste_center(canvas, phone, (W * 0.32, H * 0.52), 1.0)

    left = W * 0.58
    text(draw, (left, 330), "Press play.", "black", 84, INK, tracking=-2)
    text(draw, (left, 434), "Stay a while.", "black", 84, (140, 140, 145), tracking=-2)

    # One real player bar, drawn to the app's spec.
    bar_y = 610
    draw.rounded_rectangle((left, bar_y, left + 660, bar_y + 150), radius=22, fill=(20, 20, 20))
    art = PHONE.sized(600).crop((40, 210, 340, 510)).resize((110, 110), Image.LANCZOS)
    canvas.paste(art, (int(left + 20), int(bar_y + 20)))
    text(draw, (left + 148, bar_y + 32), "Now playing", "semi", 24, (240, 240, 240))
    text(draw, (left + 148, bar_y + 64), "ORU", "reg", 20, (150, 150, 155))
    draw.ellipse((left + 570, bar_y + 44, left + 640, bar_y + 114), fill=(255, 255, 255))
    play_triangle(draw, left + 600, bar_y + 79, 42, (0, 0, 0))

    # Progress fills up in real time.
    fill = ease_in_out(progress(t, 0.6, 3.4))
    draw.rounded_rectangle((left + 20, bar_y + 132, left + 640, bar_y + 138), radius=3, fill=(77, 77, 77))
    draw.rounded_rectangle((left + 20, bar_y + 132, left + 20 + max(10, 620 * fill), bar_y + 138), radius=3, fill=GREEN)

    like = ease_back(progress(t, 3.2, 3.7))
    if like > 0:
        paste_center(canvas, heart(96), (left + 690, 690), clamp(like))
        text(draw, (left + 746, 690), "Liked", "semi", 26, GREEN, anchor="lm")
    return canvas.convert("RGB")


def scene_share(t):
    """Two real surfaces, side by side, with the community story."""
    canvas = radial_background((W, H), glow=(24, 84, 60), strength=1.0).convert("RGBA")
    draw = ImageDraw.Draw(canvas)

    soon = ease_in_out(progress(t, 0.0, 4.2))
    phone = device_phone(560 + int(60 * soon))
    desk = device_desktop(900)

    paste_center(canvas, phone, (W * 0.2, H * 0.55 - 16 * math.sin(t)), 1.0)
    paste_center(canvas, desk, (W * 0.68, H * 0.5 + 18 * math.sin(t * 0.9)), 1.0)

    words = [("Like it.", GREEN), ("Save it.", INK), ("Send it on.", INK)]
    for index, (word, colour) in enumerate(words):
        appear = ease_out(progress(t, 0.25 + index * 0.28, 0.75 + index * 0.28))
        size = 74
        x = W * 0.5
        y = 880 + index * 0
        # three words, stacked tight under each other is too tall; place as a row
        offsets = [(-330, 0), (0, 0), (330, 0)]
        tx = W / 2 + offsets[index][0]
        if appear > 0:
            text(draw, (tx, 940 + (1 - appear) * 26), word, "black", size, colour, tracking=-1, anchor="ma")
    return canvas.convert("RGB")


def scene_end(t):
    """Brand close and call to action."""
    canvas = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    canvas.alpha_composite(glow_layer((W, H)))
    draw = ImageDraw.Draw(canvas)

    appear = ease_back(progress(t, 0.05, 0.8))
    logo = mark(int(190 * (0.8 + 0.2 * appear)))
    paste_center(canvas, logo, (W / 2, 330), clamp(appear))

    text(draw, (W / 2, 440 + (1 - ease_out(progress(t, 0.25, 0.9))) * 20), "ORU", "black", 132, INK, tracking=12, anchor="ma")

    line = ease_out(progress(t, 0.5, 1.1))
    text(draw, (W / 2, 608 + (1 - line) * 16), "Put your first song up tonight.", "med", 40, (215, 215, 215), anchor="ma")
    text(draw, (W / 2, 668), "Listen free. Upload in one take.", "reg", 28, (140, 140, 145), anchor="ma")

    cta = ease_out(progress(t, 0.9, 1.5))
    if cta > 0:
        width, height = 520, 96
        box = (W / 2 - width / 2, 760, W / 2 + width / 2, 760 + height)
        draw.rounded_rectangle(box, radius=48, fill=(30, 215, 96, int(255 * cta)))
        text(draw, (W / 2, 808), "oru.freebuff.app", "bold", 34, (0, 0, 0), anchor="mm")

    credits = ease_out(progress(t, 1.3, 1.9))
    text(draw, (W / 2, 906), "MUSIC FROM ARTISTS WHO ARE JUST STARTING", "semi", 20, (110, 110, 115),
         tracking=5, anchor="ma")
    text(draw, (W / 2, 986), "by Ayodele Ayokunle David", "reg", 18, (80, 80, 85), anchor="ma")
    return canvas.convert("RGB")


def blur_transition(image, amount):
    if amount <= 0:
        return image
    return image.filter(ImageFilter.GaussianBlur(amount))


# --------------------------------------------------------------- timeline ----
SCENES = [
    (0.0, 6.6, scene_chat),
    (6.6, 8.9, scene_logo),
    (8.9, 14.4, scene_phone),
    (14.4, 20.4, scene_studio),
    (20.4, 24.8, scene_listen),
    (24.8, 29.4, scene_share),
    (29.4, 34.0, scene_end),
]
XFADE = 0.45


def render_frame(time):
    current = None
    for start, end, fn in SCENES:
        if start <= time < end:
            current = (start, end, fn)
            break
    if current is None:
        start, end, fn = SCENES[-1]
        return finish(fn(end - start), time)
    start, end, fn = current
    frame = finish(fn(time), time)

    # Crossfade into the next scene near the boundary.
    if end - time < XFADE and end < DURATION:
        index = SCENES.index(current)
        nxt_start, nxt_end, nxt_fn = SCENES[index + 1]
        mix = ease_in_out(1 - (end - time) / XFADE)
        upcoming = finish(nxt_fn(0.0001), time)
        frame = Image.blend(frame, upcoming, mix * 0.7)
    return frame


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
        return (np.sin(phase) * env(length, 0.02, length * 0.5, 1.6) * gain).astype(np.float32)

    bpm = 96
    beat = 60 / bpm

    # Pad bed
    chords = [(146.83, [1.0, 1.26, 1.5, 2.0]), (130.81, [1.0, 1.19, 1.5, 1.78]),
              (164.81, [1.0, 1.25, 1.5, 2.0]), (110.0, [1.0, 1.2, 1.5, 1.8])]
    for index in range(0, int(DURATION / (beat * 4)) + 1):
        at = index * beat * 4
        root, ratios = chords[index % len(chords)]
        for ratio in ratios:
            add(tone(root * ratio, beat * 4.4, attack=0.5, decay=2.6, harmonics=(0.7, 0.2, 0.08)), at, gain=0.06)
        add(tone(root / 2, beat * 4.2, attack=0.12, decay=1.6, harmonics=(1.0, 0.2)), at, gain=0.1)

    # Beat from 8.9s until the close
    beat_start, beat_end = 8.9, 29.4
    step = beat / 2
    index = 0
    while beat_start + index * step < beat_end:
        at = beat_start + index * step
        if index % 2 == 0:
            add(tone(58, 0.36, attack=0.002, decay=0.13, harmonics=(1.0, 0.3)), at, gain=0.5)
            add(noise(0.03, 0.015), at, gain=0.16)
        else:
            add(noise(0.06, 0.035), at, gain=0.1, pan=0.25)
        if index % 4 == 2:
            add(noise(0.16, 0.09), at, gain=0.09, pan=-0.2)
        index += 1

    # Arpeggio sparkle
    arp_notes = [440.0, 523.25, 659.25, 783.99, 659.25, 523.25]
    index = 0
    while beat_start + index * beat < beat_end - 1:
        at = beat_start + index * beat
        note = arp_notes[index % len(arp_notes)]
        add(tone(note, 0.5, attack=0.004, decay=0.16, harmonics=(0.8, 0.3, 0.12)), at, gain=0.055,
            pan=math.sin(index) * 0.4)
        index += 1

    # Typing clicks, message chime, tap and whooshes
    for start in (0.35, 0.75, 1.15, 1.55, 3.05, 3.35, 3.65, 4.35):
        add(noise(0.02, 0.01), start, gain=0.05, pan=0.1)
    add(tone(880, 0.5, attack=0.005, decay=0.22, harmonics=(1.0, 0.5, 0.25)), 4.35, gain=0.16)
    add(tone(1320, 0.45, attack=0.005, decay=0.2, harmonics=(1.0, 0.3)), 4.42, gain=0.08)
    add(sweep(300, 1800, 0.35, 0.35), 5.55, gain=0.12)
    add(tone(120, 0.6, attack=0.005, decay=0.2, harmonics=(1.0, 0.5)), 6.6, gain=0.35)
    add(sweep(1600, 200, 0.9, 0.5), 6.6, gain=0.14)

    for at in (8.9, 14.4, 20.4, 24.8):
        add(sweep(220, 2600, 0.5, 0.4), at - 0.18, gain=0.1)

    # Like pop
    add(tone(1046.5, 0.4, attack=0.004, decay=0.18, harmonics=(1.0, 0.5)), 20.4 + 3.25, gain=0.14)

    # Closing hit with a long shimmer
    add(tone(87.31, 2.4, attack=0.01, decay=1.2, harmonics=(1.0, 0.5, 0.25)), 29.4, gain=0.5)
    add(tone(349.23, 3.2, attack=0.02, decay=1.6, harmonics=(0.6, 0.25, 0.1)), 29.4, gain=0.16)
    add(tone(523.25, 3.4, attack=0.03, decay=2.0, harmonics=(0.5, 0.2, 0.1)), 29.45, gain=0.1)
    add(noise(1.2, 0.4), 29.4, gain=0.1)

    # Gentle room tail
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


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    WORK.mkdir(parents=True, exist_ok=True)
    audio_path = WORK / "soundtrack.wav"

    # Rendering is slow, so the film is built in chunks and stitched after.
    first = int(sys.argv[1]) if len(sys.argv) > 1 else 0
    last = int(sys.argv[2]) if len(sys.argv) > 2 else FRAMES
    chunk = WORK / f"chunk-{first:04d}.mp4"
    if not audio_path.exists():
        print("writing soundtrack")
        synth_audio(audio_path)

    video_path = chunk
    command = [
        str(FFMPEG), "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
        "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
        "-movflags", "+faststart", str(video_path),
    ]
    process = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    try:
        for index in range(first, last):
            time = index / FPS
            frame = render_frame(time)
            process.stdin.write(frame.tobytes())
            if index % 60 == 0:
                print(f"frame {index}/{FRAMES}", flush=True)
        process.stdin.close()
    except BrokenPipeError:
        pass
    error = process.stderr.read().decode("utf-8", "replace")
    code = process.wait()
    if code != 0:
        print(error[-3000:], file=sys.stderr)
        raise SystemExit(f"ffmpeg failed with {code}")
    print("wrote", video_path, round(video_path.stat().st_size / (1024 * 1024), 1), "MB")


if __name__ == "__main__" and os.environ.get("ORU_PROMO_NO_MAIN") != "1":
    main()

