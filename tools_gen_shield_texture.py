"""Gera textures/entity/sun_shield.png (mapa equirretangular) a partir do orbe holográfico.

Cada ponto da esfera usa a projeção ortográfica do orbe (x,y), espelhada no hemisfério de trás,
então vista de frente a barreira fica igual à imagem original. O interior escuro vira quase
transparente (dá para ver o Sol através), as linhas hexagonais brilhantes ficam opacas.
Uso: python tools_gen_shield_texture.py
"""
import numpy as np
from PIL import Image

SRC = "source_models/orbe_holografico_azul_neon.png"
OUT = "src/main/resources/assets/sunmod/textures/entity/sun_shield.png"
W, H = 1024, 512   # final (o Minecraft não usa mipmap em texturas de entidade: menor = menos ruído)
SS = 3             # supersampling 3x3 (anti-aliasing)
K = 0.85          # fração do raio do orbe usada (evita o brilho do limbo virar uma faixa)
BASE_ALPHA = 0.10 # opacidade do "vidro" escuro entre as linhas (quase invisível: o Sol aparece)

im = np.asarray(Image.open(SRC).convert("RGBA")).astype(np.float32)
ih, iw = im.shape[:2]
ys, xs = np.where(im[..., 3] > 20)
cx, cy = (xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2
rx, ry = (xs.max() - xs.min()) / 2, (ys.max() - ys.min()) / 2

u = (np.arange(W * SS) + 0.5) / (W * SS)
v = (np.arange(H * SS) + 0.5) / (H * SS)
lon = -np.pi + 2 * np.pi * u[None, :]
lat = np.pi / 2 - np.pi * v[:, None]
x = np.cos(lat) * np.sin(lon)   # mesmas fórmulas de ShieldMesh.java
y = np.sin(lat) * np.ones_like(lon)
px = cx + x * rx * K
py = cy - y * ry * K

x0 = np.clip(np.floor(px).astype(int), 0, iw - 2); y0 = np.clip(np.floor(py).astype(int), 0, ih - 2)
fx = (px - x0)[..., None]; fy = (py - y0)[..., None]
s = (im[y0, x0] * (1 - fx) * (1 - fy) + im[y0, x0 + 1] * fx * (1 - fy)
     + im[y0 + 1, x0] * (1 - fx) * fy + im[y0 + 1, x0 + 1] * fx * fy)

rgb = s[..., :3]
bright = (rgb[..., 1] * 0.6 + rgb[..., 2] * 0.4) / 255.0
t = np.clip((bright - 0.45) / 0.5, 0, 1)
t = t * t * (3 - 2 * t)
alpha = BASE_ALPHA + (1 - BASE_ALPHA) * t
rgb = np.clip(rgb * 1.2, 0, 255)

# downsample SS x SS com média ponderada pelo alpha (sem halo escuro)
pm = np.dstack([rgb * alpha[..., None], alpha])
pm = pm.reshape(H, SS, W, SS, 4).mean(axis=(1, 3))
a = pm[..., 3:4]
rgb = np.where(a > 1e-4, pm[..., :3] / np.maximum(a, 1e-4), 0)
out = np.dstack([np.clip(rgb, 0, 255), a * 255]).round().astype(np.uint8)
Image.fromarray(out, "RGBA").save(OUT)
print("ok", OUT, out.shape)
