"""Draw ORU's PWA icons, link-preview cards and store screenshots.

Run from the project root after changing the brand colours or the tagline:

    python3 scripts/generate_images.py

Everything it writes lands in public/ so the Vite build copies it into dist/.
Pillow is the only dependency (pip install pillow) and the Figtree files it
loads are vendored in scripts/fonts/.
"""

from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
FONTS = ROOT / "scripts" / "fonts"
PUBLIC = ROOT / "public"

GREEN = (30, 215, 96)
BLACK = (0, 0, 0)
PANEL = (18, 18, 18)
CARD = (24, 24, 24)
CARD_HOVER = (38, 38, 38)
WHITE = (255, 255, 255)
SOFT = (179, 179, 179)
FAINT = (110, 110, 110)
LINE = (42, 42, 42)


def font(weight: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / f"Figtree-{weight}.ttf"), size)


def mark(size: int) -> Image.Image:
    """The ORU record mark: green disc, white ring, dark play notch."""
    scale = 8
    s = size * scale
    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    pad = int(s * 0.031)  # stroke (4 of 64) sits outside the r=30 circle
    box = (pad, pad, s - pad, s - pad)
    d.ellipse(box, fill=GREEN + (255,), outline=WHITE + (255,), width=int(s * 0.062))

    unit = s / 64
    tri = [(27 * unit, 23 * unit), (45 * unit, 32 * unit), (27 * unit, 41 * unit)]
    d.polygon(tri, fill=BLACK + (255,))
    # Round the joins the way SVG stroke-linejoin: round does.
    radius = unit * 2.5
    for x, y in tri:
        d.ellipse((x - radius, y - radius, x + radius, y + radius), fill=BLACK + (255,))
    return layer.resize((size, size), Image.LANCZOS)


def composite(img: Image.Image, stamp: Image.Image, xy: tuple[int, int]) -> None:
    """Alpha-blend a mark onto an RGB canvas without changing its mode."""
    base = img.convert("RGBA")
    base.alpha_composite(stamp.convert("RGBA"), xy)
    img.paste(base.convert("RGB"), (0, 0))


def wordmark(img: Image.Image, x: int, y: int, size: int, colour=WHITE) -> None:
    d = ImageDraw.Draw(img)
    d.text((x, y), "ORU", font=font("ExtraBold", size), fill=colour, anchor="lm")


def wrap(draw: ImageDraw.ImageDraw, text: str, face: ImageFont.FreeTypeFont, width: int) -> list[str]:
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=face) <= width:
            line = trial
        else:
            if line:
                lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def paragraph(
    img: Image.Image,
    text: str,
    face: ImageFont.FreeTypeFont,
    box: tuple[int, int, int, int],
    fill=WHITE,
    spacing: float = 1.25,
) -> None:
    d = ImageDraw.Draw(img)
    x, y, width, _ = box
    for line in wrap(d, text, face, width):
        d.text((x, y), line, font=face, fill=fill)
        y = int(y + face.size * spacing)


def rounded(draw: ImageDraw.ImageDraw, box, radius, fill, outline=None, width=1) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def gradient_background(size: tuple[int, int], corner=(10, 14, 11)) -> Image.Image:
    """A near-black vertical wash with a faint green bloom in one corner."""
    w, h = size
    img = Image.new("RGB", size, BLACK)
    d = ImageDraw.Draw(img)
    for step in range(h):
        t = step / max(h - 1, 1)
        shade = int(6 + 16 * t)
        d.line((0, step, w, step), fill=(shade, shade + 2, shade))
    bloom = Image.new("RGBA", size, (0, 0, 0, 0))
    bd = ImageDraw.Draw(bloom)
    radius = int(max(size) * 0.7)
    bd.ellipse((-radius, -radius, radius, radius), fill=(30, 215, 96, 55))
    bd.ellipse((w - radius, h - radius, w + radius, h + radius), fill=(30, 215, 96, 35))
    return Image.alpha_composite(img.convert("RGBA"), bloom).convert("RGB")


def icon(path: Path, size: int, maskable: bool, background=BLACK) -> None:
    img = Image.new("RGBA", (size, size), background + (255,))
    # Maskable icons must keep art inside the inner 80% (Chrome's safe zone).
    art = int(size * (0.5 if maskable else 0.7))
    if maskable:
        art = int(size * 0.44)
    stamp = mark(art)
    offset = (size - art) // 2
    img.alpha_composite(stamp, (offset, offset))
    img.convert("RGB").save(path, "PNG")


def shortcut_icon(path: Path, glyph: str) -> None:
    size = 96
    img = Image.new("RGBA", (size, size), BLACK + (255,))
    d = ImageDraw.Draw(img)
    if glyph == "search":
        d.ellipse((22, 20, 58, 56), outline=GREEN, width=7)
        d.line((54, 52, 72, 72), fill=GREEN, width=8)
        d.line((54, 52, 72, 72), fill=GREEN, width=8)
    elif glyph == "studio":
        rounded(d, (38, 16, 58, 50), 10, GREEN)
        d.arc((26, 34, 70, 74), 0, 180, fill=GREEN, width=7)
        d.line((48, 74, 48, 84), fill=GREEN, width=7)
        d.line((36, 84, 60, 84), fill=GREEN, width=7)
    else:  # heart
        cx, cy = 48, 52
        r = 20
        d.ellipse((cx - r, cy - r, cx, cy), fill=GREEN)
        d.ellipse((cx, cy - r, cx + r, cy), fill=GREEN)
        d.polygon([(cx - r - 1, cy - 4), (cx + r + 1, cy - 4), (cx, cy + r + 12)], fill=GREEN)
    img.save(path, "PNG")


# --------------------------------------------------------------------------
# Store screenshots: a simplified mock of the running app, dark theme included.
# --------------------------------------------------------------------------


def phone_shot(path: Path) -> None:
    w, h = 1080, 1920
    img = Image.new("RGB", (w, h), BLACK)
    d = ImageDraw.Draw(img)
    stamp = mark(76)
    composite(img, stamp, (48, 56))
    wordmark(img, 136, 96, 54)

    d.text((48, 200), "Good evening", font=font("ExtraBold", 66), fill=WHITE)
    tiles = ["Liked songs", "New songs", "Your library", "Radio"]
    for i, label in enumerate(tiles):
        x = 48 + (i % 2) * 500
        y = 300 + (i // 2) * 116
        rounded(d, (x, y, x + 468, y + 96), 8, CARD)
        rounded(d, (x, y, x + 96, y + 96), 8, GREEN)
        d.text((x + 124, y + 48), label, font=font("Bold", 34), fill=WHITE, anchor="lm")

    d.text((48, 576), "New songs", font=font("ExtraBold", 50), fill=WHITE)
    rail = ["First Light", "Night Bus", "Low Tide", "Green Room"]
    for i, title in enumerate(rail):
        x = 48 + i * 256
        rounded(d, (x, 648, x + 224, 872), 10, CARD)
        art = Image.new("RGB", (224, 224), CARD)
        ad = ImageDraw.Draw(art)
        ad.rectangle((0, 0, 224, 224), fill=[(47, 86, 112), (55, 98, 79), (140, 58, 68), (168, 118, 31)][i % 4])
        ad.ellipse((72, 72, 152, 152), fill=(0, 0, 0, 40))
        art = art.resize((224, 168))
        img.paste(art, (x, 648))
        d.text((x + 16, 836), title, font=font("Bold", 30), fill=WHITE)

    d.text((48, 940), "All songs", font=font("ExtraBold", 50), fill=WHITE)
    rows = [("First Light", "Ade Omolara"), ("Night Bus to Yaba", "Tobi Kestrel"), ("Low Tide", "Mira Sun"), ("Green Room", "Kayo Beats")]
    for i, (title, artist) in enumerate(rows):
        y = 1012 + i * 116
        rounded(d, (48, y, 1032, y + 100), 8, CARD if i % 2 == 0 else (21, 21, 21))
        rounded(d, (64, y + 12, 148, y + 96), 6, [CARD_HOVER, (47, 86, 112), (55, 98, 79), (140, 58, 68)][i % 4])
        d.text((172, y + 38), title, font=font("Bold", 34), fill=WHITE, anchor="lm")
        d.text((172, y + 72), artist, font=font("SemiBold", 28), fill=SOFT, anchor="lm")
        d.ellipse((940, y + 30, 992, y + 82), fill=GREEN)
        d.polygon([(960, y + 44), (976, y + 56), (960, y + 68)], fill=BLACK)

    # Player bar
    rounded(d, (32, 1592, 1048, 1740), 14, CARD)
    rounded(d, (56, 1616, 160, 1720), 8, (47, 86, 112))
    d.text((184, 1644), "First Light", font=font("Bold", 34), fill=WHITE)
    d.text((184, 1686), "Ade Omolara", font=font("SemiBold", 28), fill=SOFT)
    d.ellipse((920, 1632, 1016, 1728), fill=GREEN)
    d.polygon([(956, 1656), (986, 1680), (956, 1704)], fill=BLACK)
    d.line((56, 1756, 1024, 1756), fill=LINE, width=6)
    d.line((56, 1756, 430, 1756), fill=GREEN, width=6)
    d.ellipse((420, 1746, 440, 1766), fill=WHITE)

    tabs = ["Home", "Search", "Library", "Studio"]
    for i, label in enumerate(tabs):
        cx = 135 + i * 270
        d.ellipse((cx - 22, 1808, cx + 22, 1852), fill=GREEN if i == 0 else FAINT)
        d.text((cx, 1878), label, font=font("Bold", 28), fill=WHITE if i == 0 else FAINT, anchor="ma")
    img.save(path, "PNG")


def wide_shot(path: Path) -> None:
    w, h = 1440, 900
    img = Image.new("RGB", (w, h), BLACK)
    d = ImageDraw.Draw(img)
    stamp = mark(64)
    composite(img, stamp, (40, 36))
    wordmark(img, 116, 68, 44)

    nav = ["Home", "Search", "Your Library", "Studio", "Reports"]
    for i, label in enumerate(nav):
        y = 150 + i * 64
        rounded(d, (32, y, 320, y + 52), 6, CARD_HOVER if i == 0 else (0, 0, 0, 0))
        d.text((56, y + 26), label, font=font("Bold", 30), fill=WHITE if i == 0 else SOFT, anchor="lm")
    rounded(d, (32, 500, 320, 760), 10, CARD)
    d.text((56, 528), "Playlists", font=font("Bold", 26), fill=SOFT)
    for i, label in enumerate(["Late nights", "Morning run", "Afro picks"]):
        d.text((56, 580 + i * 52), label, font=font("SemiBold", 28), fill=WHITE)

    # Rounded main panel
    rounded(d, (340, 24, 1416, 876), 14, PANEL)
    d.text((392, 92), "Good evening", font=font("ExtraBold", 56), fill=WHITE)
    for i, label in enumerate(["Liked songs", "New songs", "Your library", "Radio"]):
        x = 392 + (i % 2) * 330
        y = 148 + (i // 2) * 92
        rounded(d, (x, y, x + 306, y + 76), 8, CARD)
        rounded(d, (x, y, x + 76, y + 76), 8, GREEN)
        d.text((x + 100, y + 38), label, font=font("Bold", 30), fill=WHITE, anchor="lm")

    d.text((392, 372), "New songs", font=font("ExtraBold", 44), fill=WHITE)
    for i, title in enumerate(["First Light", "Night Bus", "Low Tide", "Green Room"]):
        x = 392 + i * 250
        rounded(d, (x, 432, x + 218, 646), 10, CARD)
        art = Image.new("RGB", (218, 218), [CARD_HOVER, (47, 86, 112), (55, 98, 79), (140, 58, 68)][i])
        img.paste(art, (x, 432))
        d.text((x + 14, 622), title, font=font("Bold", 28), fill=WHITE)

    rounded(d, (392, 682, 1364, 848), 10, CARD)
    for i, (title, artist) in enumerate([("First Light", "Ade Omolara"), ("Night Bus to Yaba", "Tobi Kestrel"), ("Low Tide", "Mira Sun")]):
        y = 706 + i * 46
        d.text((416, y), title, font=font("Bold", 28), fill=WHITE, anchor="lm")
        d.text((760, y), artist, font=font("SemiBold", 26), fill=SOFT, anchor="lm")
        d.ellipse((1300, y - 16, 1332, y + 16), fill=GREEN)
        d.polygon([(1311, y - 7), (1324, y), (1311, y + 7)], fill=BLACK)

    # Bottom player across the window
    rounded(d, (24, 796, 1416, 884), 12, CARD)
    img.save(path, "PNG")


def share_card(path: Path, size=(1200, 630)) -> None:
    w, h = size
    img = gradient_background((w, h))
    stamp = int(min(w, h) * 0.26)
    composite(img, mark(stamp), (72, (h - stamp) // 2))
    left = 72 + stamp + 56
    d = ImageDraw.Draw(img)
    d.text((left, int(h * 0.34)), "ORU", font=font("ExtraBold", int(min(w, h) * 0.2)), fill=WHITE)
    d.text((left, int(h * 0.62)), "New music from artists", font=font("Bold", int(min(w, h) * 0.075)), fill=GREEN)
    d.text((left, int(h * 0.74)), "who are just starting", font=font("Bold", int(min(w, h) * 0.075)), fill=GREEN)
    d.text((72, h - 56), "Upload, publish, get heard. Free on any phone.", font=font("SemiBold", int(min(w, h) * 0.045)), fill=SOFT)
    img.convert("RGB").save(path, "PNG")


def share_square(path: Path) -> None:
    size = 512
    img = Image.new("RGB", (size, size), BLACK)
    d = ImageDraw.Draw(img)
    art = int(size * 0.56)
    composite(img, mark(art), ((size - art) // 2, int(size * 0.1)))
    d = ImageDraw.Draw(img)
    d.text((size // 2, int(size * 0.82)), "ORU", font=font("ExtraBold", 104), fill=WHITE, anchor="mm")
    d.rectangle((int(size * 0.32), int(size * 0.93), int(size * 0.68), int(size * 0.95)), fill=GREEN)
    img.save(path, "PNG")


def main() -> None:
    (PUBLIC / "icons").mkdir(parents=True, exist_ok=True)
    (PUBLIC / "screenshots").mkdir(parents=True, exist_ok=True)

    icon(PUBLIC / "icons" / "icon-192.png", 192, maskable=False)
    icon(PUBLIC / "icons" / "icon-512.png", 512, maskable=False)
    icon(PUBLIC / "icons" / "icon-maskable-192.png", 192, maskable=True)
    icon(PUBLIC / "icons" / "icon-maskable-512.png", 512, maskable=True)
    icon(PUBLIC / "icons" / "apple-touch-icon.png", 180, maskable=False)
    icon(PUBLIC / "icons" / "favicon-32.png", 32, maskable=False)
    icon(PUBLIC / "icons" / "favicon-16.png", 16, maskable=False)

    shortcut_icon(PUBLIC / "icons" / "shortcut-search.png", "search")
    shortcut_icon(PUBLIC / "icons" / "shortcut-studio.png", "studio")
    shortcut_icon(PUBLIC / "icons" / "shortcut-liked.png", "heart")

    share_card(PUBLIC / "og.png", (1200, 630))
    share_card(PUBLIC / "og-square.png", (512, 512))
    share_card(PUBLIC / "og-wide.png", (1200, 600))

    phone_shot(PUBLIC / "screenshots" / "home-phone.png")
    wide_shot(PUBLIC / "screenshots" / "home-desktop.png")

    print("icons, share cards and screenshots written to public/")


if __name__ == "__main__":
    main()
