# -*- coding: utf-8 -*-
"""虎仔 App 图标生成器（1.141：结构参照贴吧图标，配色沿用本项目）。

结构（以 108dp 画布为基准）：
  - 底：squircle（圆角 ≈ 22%）/ 圆形，垂直蓝渐变（本项目一贯配色）；
  - 前景：白色圆角对话气泡（内含深蓝文字「JR」）；
  - 左下角：再叠一个浅蓝气泡（白色 42% 叠在渐变上），带朝左下的小尾巴。

字体：tools/huzai_mark.otf（Noto Sans CJK SC 子集，含 虎/仔/H/Z/J/R），描边模拟粗体。

输出：传统方形/圆形位图 + 自适应分层（背景=渐变，前景=气泡组）+ 主题图标（剪影）。
用法：python3 tools/genicon.py   自检：python3 tools/check_icon.py
"""
from PIL import Image, ImageDraw, ImageFont
import os

HERE = os.path.dirname(os.path.abspath(__file__))
W = os.path.join(os.path.dirname(HERE), 'app', 'src', 'main', 'res')
FONT_PATH = os.path.join(HERE, 'huzai_mark.otf')

GRAD = [
    (0.00, (66, 137, 198)),
    (0.55, (34, 104, 181)),
    (1.00, (17, 80, 166)),
]
WHITE = (255, 255, 255)
BACK_ALPHA = 107                    # 浅蓝气泡 = 白色 42% 叠在渐变上
TEXT_RGB = (23, 89, 171)            # 气泡内文字：深蓝

SS = 4
DENS = [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]

LABEL = 'JR'
S_LEGACY = 76.0     # 传统方形/圆形：不参与遮罩，可放大
S_ADAPT = 52.0      # 自适应前景：整体外接半径 ≈ 31dp < 33dp 安全区
LEGACY_CORNER = 23.8


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def grad_color(t):
    for i in range(len(GRAD) - 1):
        p0, c0 = GRAD[i]
        p1, c1 = GRAD[i + 1]
        if p0 <= t <= p1:
            return lerp(c0, c1, (t - p0) / (p1 - p0) if p1 > p0 else 0.0)
    return GRAD[-1][1]


def make_gradient(S):
    small = Image.new('RGB', (1, 256))
    px = small.load()
    for y in range(256):
        px[0, y] = grad_color(y / 255.0)
    return small.resize((S, S), Image.BILINEAR)


def layout(S):
    """按比例算出两个气泡，并使整体包围盒中心落在画布中心 (54,54)。"""
    wf, hf = 0.62 * S, 0.59 * S      # 前景白气泡
    wb, hb = 0.68 * S, 0.62 * S      # 背后浅蓝气泡
    dx, dy = 0.20 * S, 0.24 * S      # 后者相对前者的偏移（左下）
    xf = 54.0 + (dx + wb / 2 - wf / 2) / 2
    yf = 54.0 - (dy + hb / 2 - hf / 2) / 2
    return dict(S=S, wf=wf, hf=hf, xf=xf, yf=yf,
                wb=wb, hb=hb, xb=xf - dx, yb=yf + dy)


def font_fit(d, text, target_h, sw_ratio=0.05):
    size = 100.0
    sw = 0
    for _ in range(5):
        sw = int(round(size * sw_ratio / 2.0))
        f = ImageFont.truetype(FONT_PATH, max(1, int(round(size))))
        b = d.textbbox((0, 0), text, font=f, stroke_width=sw)
        h = b[3] - b[1]
        if h <= 0:
            break
        size = size * target_h / h
    sw = int(round(size * sw_ratio / 2.0))
    f = ImageFont.truetype(FONT_PATH, max(1, int(round(size))))
    b = d.textbbox((0, 0), text, font=f, stroke_width=sw)
    return f, b, sw


def draw_mark(layer, sc, cfg, mono=False):
    """在透明图层上画出「浅蓝气泡 + 白色气泡 + 文字」。"""
    d = ImageDraw.Draw(layer)

    def rect(cx, cy, w, h, radius, fill):
        d.rounded_rectangle([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2],
                            radius=radius, fill=fill)

    xb, yb, wb, hb = cfg['xb'] * sc, cfg['yb'] * sc, cfg['wb'] * sc, cfg['hb'] * sc
    xf, yf, wf, hf = cfg['xf'] * sc, cfg['yf'] * sc, cfg['wf'] * sc, cfg['hf'] * sc

    back_fill = (255, 255, 255, 255) if mono else (255, 255, 255, BACK_ALPHA)
    text_fill = (0, 0, 0, 0) if mono else TEXT_RGB + (255,)

    # 1) 背后的浅蓝气泡 + 左下小尾巴
    rect(xb, yb, wb, hb, 0.22 * wb, back_fill)
    d.polygon([(xb - 0.24 * wb, yb + hb / 2 - 1),
               (xb + 0.02 * wb, yb + hb / 2 - 1),
               (xb - 0.22 * wb, yb + hb / 2 + 0.10 * cfg['S'] * sc)], fill=back_fill)

    # 2) 前景白色气泡 + 右下角小尾巴
    rect(xf, yf, wf, hf, 0.22 * wf, (255, 255, 255, 255))
    d.polygon([(xf - 0.02 * wf, yf + hf / 2 - 1),
               (xf + 0.24 * wf, yf + hf / 2 - 1),
               (xf + 0.22 * wf, yf + hf / 2 + 0.10 * cfg['S'] * sc)],
              fill=(255, 255, 255, 255))

    # 3) 气泡内文字（mono 时为镂空）
    f, b, sw = font_fit(d, LABEL, 0.24 * cfg['S'] * sc)
    d.text((xf - (b[0] + b[2]) / 2.0, yf - (b[1] + b[3]) / 2.0), LABEL,
           font=f, fill=text_fill, stroke_width=sw, stroke_fill=text_fill)


def render(size, mode):
    """mode: legacy | round | adaptive_bg | adaptive_fg | mono"""
    S = int(round(size * SS))
    sc = S / 108.0

    if mode == 'adaptive_bg':
        return make_gradient(S).resize((size, size), Image.LANCZOS)

    if mode in ('legacy', 'round'):
        base = make_gradient(S).convert('RGBA')
        mask = Image.new('L', (S, S), 0)
        md = ImageDraw.Draw(mask)
        if mode == 'legacy':
            md.rounded_rectangle([0, 0, S - 1, S - 1], radius=LEGACY_CORNER * sc, fill=255)
        else:
            md.ellipse([0, 0, S - 1, S - 1], fill=255)
        base.putalpha(mask)
        layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        draw_mark(layer, sc, layout(S_LEGACY))
        base = Image.alpha_composite(base, layer)
        return base.resize((size, size), Image.LANCZOS)

    layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    draw_mark(layer, sc, layout(S_ADAPT), mono=(mode == 'mono'))
    return layer.resize((size, size), Image.LANCZOS)


def save(path, img):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return os.path.getsize(path)


def main():
    total = 0
    for name, mul in DENS:
        n = int(48 * mul)
        total += save('%s/mipmap-%s/ic_launcher.png' % (W, name), render(n, 'legacy'))
        total += save('%s/mipmap-%s/ic_launcher_round.png' % (W, name), render(n, 'round'))
        m = int(108 * mul)
        total += save('%s/mipmap-%s/ic_launcher_bg.png' % (W, name), render(m, 'adaptive_bg'))
        total += save('%s/mipmap-%s/ic_launcher_fg.png' % (W, name), render(m, 'adaptive_fg'))
        total += save('%s/mipmap-%s/ic_launcher_mono.png' % (W, name), render(m, 'mono'))
        print('%-8s legacy=%d adaptive=%d' % (name, n, m))
    print('files bytes total =', total)


if __name__ == '__main__':
    main()