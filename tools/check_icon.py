# -*- coding: utf-8 -*-
"""图标自检：几何安全区校验 + 字符画预览。
用法：python3 tools/check_icon.py
"""
from PIL import Image
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(os.path.dirname(HERE), 'app', 'src', 'main', 'res')


def geo(path, label, limit=36.0, warn=33.0):
    im = Image.open(path).convert('RGBA')
    w, h = im.size
    px = im.load()
    sc = 108.0 / w
    n = 0
    x0 = y0 = 1e9
    x1 = y1 = -1e9
    rmax = 0.0
    for y in range(h):
        for x in range(w):
            if px[x, y][3] > 8:
                n += 1
                X, Y = x * sc, y * sc
                x0 = min(x0, X); y0 = min(y0, Y)
                x1 = max(x1, X); y1 = max(y1, Y)
                r = math.hypot(X - 54.0, Y - 54.0)
                if r > rmax:
                    rmax = r
    if n == 0:
        print('%-26s EMPTY!' % label)
        return
    ok = rmax < limit
    tag = 'OK' if ok else 'CLIPPED!'
    if ok and rmax > warn:
        tag = 'OK but outside 33dp safe zone'
    print('%-26s %dpx  ink=%dpx  bbox=(%.1f,%.1f)-(%.1f,%.1f)  maxR=%.2fdp  %s'
          % (label, w, n, x0, y0, x1, y1, rmax, tag))


def show(im, cols, title, mask=False):
    rows = int(cols * 0.5)
    sm = im.resize((cols, rows), Image.LANCZOS).convert('RGBA')
    print('--- %s ---' % title)
    for y in range(rows):
        line = ''
        for x in range(cols):
            if mask:
                dx = (x + 0.5) / cols * 108 - 54
                dy = (y + 0.5) / rows * 108 - 54
                if dx * dx + dy * dy > 36 * 36:
                    line += '.'
                    continue
            p = sm.getpixel((x, y))
            if p[3] < 60:
                line += '.'
            elif p[0] > 225 and p[1] > 225 and p[2] > 225:
                line += 'W'
            else:
                v = (p[0] + p[1] + p[2]) / 3.0
                line += 'A' if v > 170 else ('B' if v > 140 else 'C')
        print(line)


def main():
    geo(R + '/mipmap-xxxhdpi/ic_launcher_fg.png', 'adaptive_fg')
    geo(R + '/mipmap-xxxhdpi/ic_launcher_mono.png', 'mono')
    bg = Image.open(R + '/mipmap-xxxhdpi/ic_launcher_bg.png').convert('RGBA')
    fg = Image.open(R + '/mipmap-xxxhdpi/ic_launcher_fg.png').convert('RGBA')
    show(Image.open(R + '/mipmap-xxxhdpi/ic_launcher.png').convert('RGBA'),
         72, 'legacy squircle')
    show(Image.alpha_composite(bg, fg), 72, 'adaptive, circle-masked 72dp', True)
    show(Image.open(R + '/mipmap-xxxhdpi/ic_launcher_mono.png').convert('RGBA'),
         40, 'monochrome')


if __name__ == '__main__':
    main()