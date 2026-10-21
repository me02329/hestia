"""Draws Hestia's app icon and TV banner (Pillow) from the transparent emblem in branding/ (tools/vectorize_logo.py).
Usage: python3 tools/make_hestia_art.py"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app/assets/fonts/InterVariable.ttf")
LOGO = os.path.join(ROOT, "branding/hestia-logo.png")
OUT = os.path.join(ROOT, "app/res/drawable-nodpi")
GOLD = (233, 197, 135)


def background(w, h):
    """The emblem's night blue, with a warm hearth glow rising from the bottom."""
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = math.hypot((x - w * 0.5) / w, (y - h * 1.05) / h)
            glow = max(0.0, 1 - d / 0.8) ** 2
            px[x, y] = tuple(min(255, round(c + g * glow)) for c, g in zip((18, 13, 32), (90, 40, 20)))
    return img


def logo(height):
    img = Image.open(LOGO).convert("RGBA")
    return img.resize((round(img.width * height / img.height), height), Image.LANCZOS)


def font(size, weight):
    f = ImageFont.truetype(FONT, size)
    try:
        f.set_variation_by_axes([32, weight])
    except Exception:
        pass
    return f


def icon():
    img = background(192, 192).convert("RGBA")
    m = logo(164)
    img.alpha_composite(m, ((192 - m.width) // 2, 14))
    img.convert("RGB").save(os.path.join(OUT, "icon.png"), optimize=True)


def banner():
    w, h = 640, 360
    img = background(w, h).convert("RGBA")
    m = logo(290)
    fnt = font(104, 600)
    d = ImageDraw.Draw(img)
    tw = d.textlength("Hestia", font=fnt)
    gap = 36
    x = (w - (m.width + gap + tw)) / 2
    img.alpha_composite(m, (round(x), (h - m.height) // 2))
    d.text((x + m.width + gap, h / 2 + 2), "Hestia", font=fnt, fill=GOLD, anchor="lm")
    img.convert("RGB").save(os.path.join(OUT, "banner.png"), optimize=True)


if __name__ == "__main__":
    icon()
    banner()
