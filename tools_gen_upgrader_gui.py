#!/usr/bin/env python3
"""
Gera as texturas de fundo da GUI "Melhorias" (Upgrader) em
src/main/resources/assets/sunmod/textures/gui/upgrader/:
  title_bar.png (800x72), card_stats.png / card_weapons.png / card_abilities.png (240x144), arrow_up.png (96x112)
Cada textura tem 2x a resolução da GUI virtual (a tela desenha com escala 0.5) e um .png.mcmeta com blur.

Uso:  python tools_gen_upgrader_gui.py      (precisa de Pillow)
Ajuste as cores em CARD_COLORS / TITLE_COLORS para mudar o visual.
"""
import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, "src/main/resources/assets/sunmod/textures/gui/upgrader")

BLACK = (10, 12, 18, 255)
TITLE_COLORS = ((41, 214, 250), (18, 160, 240))          # topo -> base
CARD_COLORS = {
    "stats": ((236, 196, 44), (214, 160, 20)),            # amarelo
    "weapons": ((44, 200, 226), (26, 150, 206)),          # ciano
    "abilities": ((70, 206, 70), (36, 150, 48)),          # verde
}


def save(im, name):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, name + ".png")
    im.save(path)
    with open(path + ".mcmeta", "w") as f:
        json.dump({"texture": {"blur": True, "clamp": True}}, f, indent=2)
    print("ok", name)


def gradient(w, h, top, bottom):
    im = Image.new("RGBA", (w, h))
    px = im.load()
    for y in range(h):
        t = y / max(1, h - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)) + (255,)
        for x in range(w):
            px[x, y] = c
    return im


def pattern(im, x0, y0, x1, y1, step=14, size=6, alpha=46):
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    for yy in range(y0, y1 - size, step):
        for xx in range(x0 + ((yy // step) % 2) * 0, x1 - size, step):
            d.rectangle((xx, yy, xx + size, yy + size), outline=(255, 255, 255, alpha), width=2)
    im.alpha_composite(layer)


def card(name, top, bottom):
    w, h = 240, 144
    im = gradient(w, h, top, bottom)
    # faixa diagonal de brilho (canto inferior direito), como no vídeo
    shine = Image.new("RGBA", im.size, (0, 0, 0, 0))
    ImageDraw.Draw(shine).polygon([(w, 30), (w, h), (60, h)], fill=(255, 255, 255, 30))
    im.alpha_composite(shine)
    pattern(im, 8, 8, w - 8, h - 8)
    # brilho no topo e sombra na base
    d = ImageDraw.Draw(im)
    d.rectangle((4, 4, w - 5, 8), fill=(255, 255, 255, 60))
    d.rectangle((4, h - 10, w - 5, h - 5), fill=(0, 0, 0, 40))
    # borda preta grossa (4 px = 2 unidades da GUI virtual)
    d.rectangle((0, 0, w - 1, h - 1), outline=BLACK, width=4)
    save(im, "card_" + name)


def title_bar():
    w, h = 800, 72
    im = gradient(w, h, *TITLE_COLORS)
    pattern(im, 6, 6, w - 6, h - 12, step=16, size=7, alpha=50)
    d = ImageDraw.Draw(im)
    d.rectangle((6, 6, w - 7, 12), fill=(255, 255, 255, 70))
    d.rectangle((0, h - 6, w - 1, h - 1), fill=BLACK)                 # separador inferior
    d.rectangle((0, 0, w - 1, h - 1), outline=BLACK, width=4)
    save(im, "title_bar")


def arrow_up():
    S = 4
    im = Image.new("RGBA", (96 * S, 112 * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pts = [(48, 4), (92, 52), (66, 52), (66, 108), (30, 108), (30, 52), (4, 52)]
    pts = [(x * S, y * S) for x, y in pts]
    d.polygon(pts, fill=(14, 20, 30, 255))
    inner = [(48, 16), (78, 50), (58, 50), (58, 100), (38, 100), (38, 50), (18, 50)]
    inner = [(x * S, y * S) for x, y in inner]
    d.polygon(inner, fill=(60, 205, 70, 255))
    d.polygon([(48 * S, 16 * S), (78 * S, 50 * S), (58 * S, 50 * S), (48 * S, 38 * S)], fill=(120, 240, 120, 255))
    d.rectangle((38 * S, 50 * S, 46 * S, 100 * S), fill=(120, 240, 120, 255))
    save(im.resize((96, 112), Image.LANCZOS), "arrow_up")


if __name__ == "__main__":
    title_bar()
    for k, (t, b) in CARD_COLORS.items():
        card(k, t, b)
    arrow_up()
