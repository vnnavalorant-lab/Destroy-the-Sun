#!/usr/bin/env python3
"""
Gera os ícones da GUI "Melhorias" do Upgrader em
src/main/resources/assets/sunmod/textures/gui/upgrades/<id>.png  (colorido)
src/main/resources/assets/sunmod/textures/gui/upgrades/<id>_dark.png (silhueta preta, usada enquanto
a arma/habilidade ainda não foi comprada).

Uso:  python tools_gen_upgrade_icons.py      (precisa de Pillow)
Para trocar um ícone por arte sua, basta substituir o PNG (128x128, fundo transparente) e gerar o
*_dark.png com o mesmo alfa (ou rodar este script com o novo PNG na pasta).
"""
import json
import math
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, "src/main/resources/assets/sunmod/textures/gui/upgrades")
ICE_ICON = os.path.join(ROOT, "src/main/resources/assets/sunmod/textures/item/ice_cube_icon.png")

S = 512          # tela de desenho (supersampling)
FINAL = 128      # tamanho final
OUTLINE = (18, 24, 38, 255)


def canvas():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def finish(im, name):
    os.makedirs(OUT, exist_ok=True)
    small = im.resize((FINAL, FINAL), Image.LANCZOS)
    dark = Image.new("RGBA", small.size, (0, 0, 0, 0))
    dark.putalpha(small.getchannel("A"))
    for suffix, img in ((".png", small), ("_dark.png", dark)):
        path = os.path.join(OUT, name + suffix)
        img.save(path)
        with open(path + ".mcmeta", "w") as f:  # filtro suave ao reduzir
            json.dump({"texture": {"blur": True, "clamp": True}}, f, indent=2)
    print("ok", name)


def outlined(draw_fn, width=14):
    """Desenha a forma e depois um contorno escuro ao redor (dilatação do alfa)."""
    im, d = canvas()
    draw_fn(d)
    alpha = im.getchannel("A")
    grown = alpha.filter(ImageFilter.MaxFilter(width * 2 + 1))
    base = Image.new("RGBA", (S, S), OUTLINE)
    base.putalpha(grown)
    base.alpha_composite(im)
    return base


def bolt(d, ox=0, oy=0, k=1.0):
    pts = [(300, 20), (150, 250), (240, 250), (190, 420), (380, 180), (285, 180), (345, 20)]
    pts = [(ox + x * k, oy + y * k) for x, y in pts]
    d.polygon(pts, fill=(255, 205, 40, 255))
    d.polygon([(ox + 300 * k, oy + 20 * k), (ox + 345 * k, oy + 20 * k), (ox + 285 * k, oy + 180 * k), (ox + 250 * k, oy + 120 * k)],
              fill=(255, 238, 120, 255))


def icon_attack_speed():
    base, _ = canvas()
    if os.path.exists(ICE_ICON):
        cube = Image.open(ICE_ICON).convert("RGBA").resize((360, 360), Image.LANCZOS)
        base.alpha_composite(cube, (140, 140))
    else:
        d = ImageDraw.Draw(base)
        d.regular_polygon((320, 330, 170), 6, fill=(120, 200, 255, 255))
    bolt_layer = outlined(lambda d: bolt(d, 0, 0, 0.8), 10)
    base.alpha_composite(bolt_layer)
    finish(base, "attack_speed")


def icon_cooling_power():
    def draw(d):
        # haste do termômetro
        d.rounded_rectangle((200, 40, 290, 340), 45, fill=(235, 250, 255, 255))
        d.ellipse((140, 270, 350, 480), fill=(60, 190, 255, 255))
        d.rounded_rectangle((224, 150, 266, 330), 20, fill=(60, 190, 255, 255))
        d.ellipse((170, 300, 320, 450), fill=(100, 215, 255, 255))
        d.ellipse((195, 320, 245, 370), fill=(220, 248, 255, 255))
        # marcações
        for y in (80, 130, 180):
            d.rounded_rectangle((300, y, 350, y + 16), 8, fill=(60, 190, 255, 255))
    base = outlined(draw)
    finish(base, "cooling_power")


def icon_backpack():
    def draw(d):
        d.rounded_rectangle((150, 70, 360, 190), 70, fill=(150, 80, 40, 255))       # topo
        d.rounded_rectangle((110, 130, 400, 470), 70, fill=(175, 95, 48, 255))      # corpo
        d.rounded_rectangle((160, 300, 350, 440), 36, fill=(140, 70, 34, 255))      # bolso
        d.rounded_rectangle((190, 330, 320, 355), 10, fill=(95, 45, 22, 255))
        d.rounded_rectangle((200, 230, 310, 262), 14, fill=(230, 190, 90, 255))     # fivela
        d.rounded_rectangle((135, 150, 190, 440), 28, fill=(205, 120, 62, 255))     # brilho
    finish(outlined(draw), "backpack_capacity")


def icon_move_speed():
    def draw(d):
        d.polygon([(90, 190), (250, 150), (330, 270), (440, 300), (460, 400), (90, 400)], fill=(235, 55, 70, 255))
        d.rounded_rectangle((70, 380, 470, 450), 30, fill=(250, 250, 250, 255))
        d.polygon([(250, 150), (330, 270), (290, 290), (210, 170)], fill=(255, 130, 140, 255))
        for i in range(3):
            d.line((200 + i * 45, 275 + i * 6, 250 + i * 45, 255 + i * 6), fill=(255, 255, 255, 255), width=14)
        d.rectangle((90, 340, 440, 380), fill=(200, 35, 50, 255))
    finish(outlined(draw), "move_speed")


def icon_ice_cube():
    base, _ = canvas()
    if os.path.exists(ICE_ICON):
        cube = Image.open(ICE_ICON).convert("RGBA").resize((470, 470), Image.LANCZOS)
        base.alpha_composite(cube, (21, 21))
    finish(base, "ice_cube")


def icon_ice_cannon():
    def draw(d):
        # corpo diagonal
        pts = [(80, 360), (150, 430), (360, 220), (290, 150)]
        d.polygon(pts, fill=(150, 165, 190, 255))
        d.polygon([(110, 340), (150, 380), (330, 200), (290, 160)], fill=(185, 200, 225, 255))
        d.polygon([(270, 130), (360, 60), (455, 155), (385, 245)], fill=(120, 135, 160, 255))   # cano
        d.polygon([(330, 70), (380, 40), (470, 130), (420, 175)], fill=(60, 185, 245, 255))     # ponta azul
        d.rounded_rectangle((90, 380, 190, 470), 22, fill=(105, 120, 145, 255))                  # cabo
        d.polygon([(150, 380), (210, 330), (240, 360), (180, 410)], fill=(60, 185, 245, 255))
    finish(outlined(draw), "ice_cannon")


def icon_cryo_beam():
    def draw(d):
        d.rounded_rectangle((60, 190, 330, 330), 40, fill=(150, 165, 190, 255))
        d.rounded_rectangle((90, 200, 320, 240), 20, fill=(190, 205, 230, 255))
        d.rounded_rectangle((100, 310, 190, 470), 28, fill=(105, 120, 145, 255))
        d.rectangle((330, 225, 400, 295), fill=(60, 185, 245, 255))
        d.polygon([(400, 240), (500, 260), (400, 280)], fill=(210, 245, 255, 255))
    finish(outlined(draw), "cryo_beam")


def icon_comet_catcher():
    def draw(d):
        # chave inglesa
        d.polygon([(130, 400), (400, 130), (450, 180), (180, 450)], fill=(95, 105, 125, 255))
        d.polygon([(150, 380), (390, 140), (410, 160), (170, 400)], fill=(150, 160, 180, 255))
        d.ellipse((310, 40, 470, 200), fill=(95, 105, 125, 255))
        d.rectangle((390, 40, 470, 120), fill=(0, 0, 0, 0))
        d.ellipse((90, 340, 200, 450), fill=(95, 105, 125, 255))
        d.ellipse((115, 365, 175, 425), fill=(0, 0, 0, 0))
    finish(outlined(draw), "comet_catcher")


def icon_adrenaline():
    def draw(d):
        # seringa na diagonal (desenhada reta e rotacionada depois)
        pass
    im, d = canvas()
    # seringa em pé e rotaciona 40 graus
    tmp = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    t = ImageDraw.Draw(tmp)
    t.rounded_rectangle((220, 110, 292, 330), 14, fill=(235, 245, 250, 255))   # corpo
    t.rectangle((228, 200, 284, 320), fill=(255, 90, 110, 255))                 # líquido
    t.rounded_rectangle((170, 100, 342, 130), 12, fill=(160, 175, 195, 255))    # aba
    t.rounded_rectangle((240, 20, 272, 110), 8, fill=(160, 175, 195, 255))      # êmbolo
    t.rounded_rectangle((205, 5, 307, 30), 10, fill=(120, 135, 160, 255))
    t.rectangle((248, 330, 264, 480), fill=(210, 220, 235, 255))                # agulha
    t.polygon([(248, 480), (264, 480), (256, 510)], fill=(210, 220, 235, 255))
    tmp = tmp.rotate(-38, resample=Image.BICUBIC, center=(256, 256))
    alpha = tmp.getchannel("A").filter(ImageFilter.MaxFilter(29))
    base = Image.new("RGBA", (S, S), OUTLINE)
    base.putalpha(alpha)
    base.alpha_composite(tmp)
    finish(base, "adrenaline")


def icon_instant_reload():
    def draw(d):
        cx, cy = 256, 256
        pts = [(cx + 215 * math.cos(math.radians(a)), cy + 215 * math.sin(math.radians(a))) for a in range(30, 390, 60)]
        d.polygon(pts, fill=(60, 190, 255, 255))
        pts2 = [(cx + 175 * math.cos(math.radians(a)), cy + 175 * math.sin(math.radians(a))) for a in range(30, 390, 60)]
        d.polygon(pts2, fill=(40, 150, 230, 255))
        # seta circular
        d.arc((150, 150, 362, 362), 40, 320, fill=(255, 255, 255, 255), width=28)
        d.polygon([(330, 130), (400, 190), (320, 215)], fill=(255, 255, 255, 255))
    finish(outlined(draw, 10), "instant_reload")


def icon_critical_offense():
    def draw(d):
        cx, cy = 256, 256
        d.ellipse((50, 50, 462, 462), fill=(255, 70, 70, 255))
        d.ellipse((110, 110, 402, 402), fill=(255, 255, 255, 255))
        d.ellipse((160, 160, 352, 352), fill=(255, 70, 70, 255))
        d.ellipse((215, 215, 297, 297), fill=(255, 255, 255, 255))
        for box in ((236, 10, 276, 130), (236, 382, 276, 502), (10, 236, 130, 276), (382, 236, 502, 276)):
            d.rectangle(box, fill=(255, 70, 70, 255))
    finish(outlined(draw, 10), "critical_offense")


if __name__ == "__main__":
    icon_attack_speed()
    icon_cooling_power()
    icon_backpack()
    icon_move_speed()
    icon_ice_cube()
    icon_ice_cannon()
    icon_cryo_beam()
    icon_comet_catcher()
    icon_adrenaline()
    icon_instant_reload()
    icon_critical_offense()
