"""Render docs/icon.svg geometry to PNG for README preview.

The shipped launcher icon is an Android VectorDrawable built from the same
numbers, so this script only exists to produce a preview image without a
native SVG rasteriser.
"""
import sys
from PIL import Image, ImageDraw
import numpy as np

S = 8                      # supersample factor
U = 108                    # viewBox units
N = U * S

# ---- background: diagonal 3-stop gradient ----
stops = [(0.0, (0x20, 0x11, 0x2B)), (0.55, (0x12, 0x12, 0x1A)), (1.0, (0x07, 0x08, 0x0C))]
yy, xx = np.mgrid[0:N, 0:N]
t = (xx + yy) / (2.0 * (N - 1))
bg = np.zeros((N, N, 3), dtype=np.float64)
for i in range(len(stops) - 1):
    t0, c0 = stops[i]
    t1, c1 = stops[i + 1]
    m = (t >= t0) & (t <= t1)
    f = ((t - t0) / (t1 - t0))[m]
    for ch in range(3):
        bg[..., ch][m] = c0[ch] + (c1[ch] - c0[ch]) * f

# rounded-rect mask (radius 24 units)
r = 24 * S
mask = Image.new("L", (N, N), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, N - 1, N - 1], radius=r, fill=255)
mask_a = np.asarray(mask, dtype=np.float64) / 255.0

img = Image.fromarray(bg.astype(np.uint8), "RGB").convert("RGBA")

# ---- accent circle (alpha 0.10) ----
cx, cy, cr = 54 * S, 56 * S, 30 * S
acc = Image.new("RGBA", (N, N), (0, 0, 0, 0))
ImageDraw.Draw(acc).ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=(0xFF, 0x2D, 0x55, 26))
img = Image.alpha_composite(img, acc)


def stroke(draw, pts, width, color, cap_round=True):
    w = width * S
    draw.line(pts, fill=color, width=int(round(w)), joint="curve")
    if cap_round:
        for (x, y) in pts:
            draw.ellipse([x - w / 2, y - w / 2, x + w / 2, y + w / 2], fill=color)


layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
d = ImageDraw.Draw(layer)

# skip-forward chevrons
chev = [[(36 * S, 30 * S), (54 * S, 54 * S), (36 * S, 78 * S)],
        [(54 * S, 30 * S), (72 * S, 54 * S), (54 * S, 78 * S)]]
for pts in chev:
    stroke(d, pts, 8.5, (255, 255, 255, 255))

# "no ads" slash
stroke(d, [(27 * S, 25 * S), (81 * S, 83 * S)], 7.5, (0xFF, 0x3B, 0x52, 255))

img = Image.alpha_composite(img, layer)

# apply rounded-rect transparency last so strokes are clipped to the tile
alpha = np.asarray(img)[:, :, 3].astype(np.float64) * mask_a
arr = np.asarray(img).copy()
arr[:, :, 3] = alpha.astype(np.uint8)
img = Image.fromarray(arr, "RGBA")

out = img.resize((512, 512), Image.LANCZOS)
out.save(sys.argv[1] if len(sys.argv) > 1 else "icon.png")
print("written", sys.argv[1] if len(sys.argv) > 1 else "icon.png")
