"""Gera textures/entity/sun_shield.png (mapa equirretangular 1024x512) a partir do painel de colmeia.

O painel é retangular, então é mapeado com a projeção SENOIDAL (sinusoidal), que não "espreme" os
hexágonos nos polos: x_img = |lon| * cos(lat), y_img = lat. A imagem é espelhada no meridiano central
(e no meridiano de trás), então não existe emenda: o espelho cai no centro de uma coluna de hexágonos.
A moldura do painel é cortada (CROP). O alpha original da imagem (centros dos hexágonos quase
transparentes, linhas opacas) é mantido.
Uso: python tools_gen_shield_texture.py
"""
import numpy as np
from PIL import Image

SRC = "source_models/escudo_energia_colmeia.png"
OUT = "src/main/resources/assets/sunmod/textures/entity/sun_shield.png"
W, H = 1024, 512   # o Minecraft não usa mipmap em texturas de entidade: não exagere no tamanho
SS = 3             # supersampling 3x3 (anti-aliasing)
CROP = 52          # px cortados de cada lado (moldura do painel)
FILL_ALPHA = 0.10  # piso de opacidade (deixa o "vidro" azul levemente visível)

im = np.asarray(Image.open(SRC).convert("RGBA")).astype(np.float32)
ih, iw = im.shape[:2]
# pré-multiplicado para interpolar sem halo
pm = im.copy(); pm[..., :3] *= pm[..., 3:4] / 255.0

x0, x1, y0, y1 = CROP, iw - CROP, CROP, ih - CROP
cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
hw, hh = (x1 - x0) / 2, (y1 - y0) / 2

u = (np.arange(W * SS) + 0.5) / (W * SS)
v = (np.arange(H * SS) + 0.5) / (H * SS)
lon = -np.pi + 2 * np.pi * u[None, :]
lat = np.pi / 2 - np.pi * v[:, None]

s = np.abs(lon) / np.pi * np.cos(lat)          # 0..1 (espelhado no meridiano 0)
px = cx + s * hw
py = cy - (lat / (np.pi / 2)) * hh * np.ones_like(lon)

xi = np.clip(np.floor(px).astype(int), 0, iw - 2); yi = np.clip(np.floor(py).astype(int), 0, ih - 2)
fx = (px - xi)[..., None]; fy = (py - yi)[..., None]
sm = (pm[yi, xi] * (1 - fx) * (1 - fy) + pm[yi, xi + 1] * fx * (1 - fy)
      + pm[yi + 1, xi] * (1 - fx) * fy + pm[yi + 1, xi + 1] * fx * fy)

a = sm[..., 3:4] / 255.0
rgb = np.where(a > 1e-4, sm[..., :3] / np.maximum(a, 1e-4) / 255.0 * 1.0, 0.0)  # de-premultiplica (0..1)
rgb = np.where(a > 1e-4, rgb, np.array([0.0, 0.5, 1.0]))
a = FILL_ALPHA + (1 - FILL_ALPHA) * a

# downsample SS x SS ponderando pelo alpha
pm2 = np.dstack([rgb * a, a]).reshape(H, SS, W, SS, 4).mean(axis=(1, 3))
a2 = pm2[..., 3:4]
rgb2 = np.where(a2 > 1e-4, pm2[..., :3] / np.maximum(a2, 1e-4), 0)
out = np.dstack([np.clip(rgb2, 0, 1) * 255, a2 * 255]).round().astype(np.uint8)
Image.fromarray(out, "RGBA").save(OUT)
print("ok", OUT, out.shape)
