"""One-off: regenerate ORU brand assets in the clay + ember identity.

Draws the mark (an ember disc with a cream play triangle, ringed in deep clay)
geometrically and anti-aliases it by supersampling, so no fonts are needed.
Overwrites the favicons, PWA icons, maskable icons and the social previews.
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
PUBLIC = ROOT / "public"

# Clay + ember palette.
EMBER = (210, 59, 24, 255)    # disc
CREAM = (244, 241, 236, 255)  # play triangle + wordmark
CLAY = (23, 19, 15, 255)      # deep field / ring
CARD = (33, 26, 20, 255)      # shortcut tile field
SOFT = (203, 192, 178, 255)   # muted wordmark bar
SS = 4                        # supersample factor


def _triangle(draw: ImageDraw.ImageDraw, box, fill):
    """A rounded play triangle pointing right, inscribed in box (l, t, r, b)."""
    l, t, r, b = box
    w, h = r - l, b - t
    pts = [
        (l + w * 0.20, t + h * 0.16),
        (l + w * 0.20, t + h * 0.84),
        (l + w * 0.86, t + h * 0.50),
    ]
    draw.polygon(pts, fill=fill)


def mark(size: int, *, ring=True) -> Image.Image:
    """An ember disc with a cream play triangle, rendered at `size`px."""
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pad = max(2, int(s * 0.03)) if ring else 0
    d.ellipse([pad, pad, s - pad, s - pad], fill=EMBER)
    if ring:
        d.ellipse([pad, pad, s - pad, s - pad], outline=CLAY, width=max(2, int(s * 0.045)))
    inset = int(s * 0.30)
    _triangle(d, (inset, inset, s - inset, s - inset), CREAM)
    return img.resize((size, size), Image.LANCZOS)


def og_wide() -> None:
    """1200x630 social card: clay field, ember mark, cream wordmark block."""
    s = SS
    W, H = 1200 * s, 630 * s
    img = Image.new("RGBA", (W, H), CLAY)
    d = ImageDraw.Draw(img)
    # A faint ember halo for warmth.
    d.ellipse([int(W * 0.68), int(-H * 0.28), int(W * 1.18), int(H * 0.70)],
              outline=(48, 34, 26, 255), width=3 * s)
    m = int(H * 0.34)
    mx, my = int(W * 0.12), int(H * 0.5 - m / 2)
    img.alpha_composite(mark(m), (mx, my))
    # Wordmark bars as a stand-in (no fonts on host).
    bx = mx + m + int(W * 0.06)
    by = int(H * 0.40)
    bw = int(W * 0.30)
    bh = int(H * 0.075)
    d.rectangle([bx, by, bx + bw, by + bh], fill=CREAM)
    d.rectangle([bx, by + bh + int(H * 0.05), bx + int(bw * 0.62),
                 by + bh + int(H * 0.05) + int(bh * 0.55)], fill=SOFT)
    img.convert("RGB").resize((1200, 630), Image.LANCZOS).save(PUBLIC / "og.png")


def og_square() -> None:
    s = SS
    S = 512 * s
    img = Image.new("RGBA", (S, S), CLAY)
    m = int(S * 0.62)
    img.alpha_composite(mark(m), ((S - m) // 2, (S - m) // 2))
    img.convert("RGB").resize((512, 512), Image.LANCZOS).save(PUBLIC / "og-square.png")


def main() -> None:
    icons = PUBLIC / "icons"
    specs = {
        "favicon-32.png": 32,
        "favicon-16.png": 16,
        "apple-touch-icon.png": 180,
        "icon-192.png": 192,
        "icon-512.png": 512,
    }
    for name, size in specs.items():
        mark(size).save(icons / name)

    # Maskable icons need the mark inside the safe zone on a full-bleed field.
    for name, size in {"icon-maskable-192.png": 192, "icon-maskable-512.png": 512}.items():
        s = SS
        S = size * s
        img = Image.new("RGBA", (S, S), CLAY)
        m = int(S * 0.62)
        img.alpha_composite(mark(m, ring=False), ((S - m) // 2, (S - m) // 2))
        img.convert("RGB").resize((size, size), Image.LANCZOS).save(icons / name)

    # Home-screen shortcuts reuse the mark on a warm card tile.
    for name in ("shortcut-liked.png", "shortcut-search.png", "shortcut-studio.png"):
        path = icons / name
        if path.exists():
            size = Image.open(path).size[0]
            img = Image.new("RGBA", (size * SS, size * SS), CARD)
            m = int(size * SS * 0.64)
            img.alpha_composite(mark(m, ring=False), ((size * SS - m) // 2, (size * SS - m) // 2))
            img.convert("RGB").resize((size, size), Image.LANCZOS).save(path)

    og_wide()
    og_square()
    print("brand assets regenerated (ember)")


if __name__ == "__main__":
    main()
