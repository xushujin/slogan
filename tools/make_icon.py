#!/usr/bin/env python3
"""生成 Slogan 的拟态图标（柔光 / 触感极简）。

设计要点
- 喇叭口必须是凸出的椭圆边：直边会让轮廓读成旗子或箭头。
  锥体 = 梯形 + 两端椭圆求并集，天然得到圆润收口。
- 全画面只有一个光源（左上）。体积全部由模糊遮罩的偏移叠加塑造，
  不描边、不分割、不发光。
- 明度差要够、轮廓要够大，缩到 48px 仍能认出；这是拟态最容易翻车的地方。

输出
- ic_launcher_foreground.png  透明底，供 adaptive-icon 前景层
- ic_launcher_monochrome.png  纯剪影，供 Android 13+ 主题图标
"""
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageChops

CANVAS = 108.0
SS = 4

BG         = (196, 190, 180)
OBJECT_TOP = (243, 239, 231)
OBJECT_BOT = (218, 212, 202)
SHADOW     = (116, 109, 99)
HIGHLIGHT  = (253, 251, 247)
MOUTH_TOP  = (146, 139, 128)
MOUTH_BOT  = (166, 159, 148)
INNER_DARK = (196, 190, 180)

DENSITIES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def sc(v, s):
    return v / CANVAS * s


def build_masks(s):
    body = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(body)

    # 梯形锥体
    d.polygon([
        (sc(30, s), sc(45, s)),
        (sc(72, s), sc(30, s)),
        (sc(72, s), sc(78, s)),
        (sc(30, s), sc(63, s)),
    ], fill=255)
    # 窄端圆头
    d.ellipse([sc(25, s), sc(45, s), sc(35, s), sc(63, s)], fill=255)
    # 宽端椭圆口
    d.ellipse([sc(64, s), sc(30, s), sc(80, s), sc(78, s)], fill=255)
    # 手柄（收在 66dp 安全区内）
    d.rounded_rectangle([sc(34, s), sc(60, s), sc(44, s), sc(82, s)], radius=sc(5, s), fill=255)

    body = body.filter(ImageFilter.GaussianBlur(sc(1.2, s)))
    body = body.point(lambda v: 255 if v >= 128 else 0)

    mouth = Image.new("L", (s, s), 0)
    dm = ImageDraw.Draw(mouth)
    dm.ellipse([sc(66, s), sc(34, s), sc(78, s), sc(74, s)], fill=255)
    mouth = mouth.filter(ImageFilter.GaussianBlur(sc(1.0, s)))
    mouth = ImageChops.multiply(mouth, body)
    return body, mouth


def vertical_gradient(s, top, bottom, gamma=0.8):
    img = Image.new("RGB", (s, s))
    d = ImageDraw.Draw(img)
    for y in range(s):
        t = (y / (s - 1)) ** gamma
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line([(0, y), (s, y)], fill=c)
    return img


def render(s):
    body, mouth = build_masks(s)
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    # 外阴影
    sh = ImageChops.offset(
        body.filter(ImageFilter.GaussianBlur(sc(3.4, s))),
        int(sc(1.6, s)), int(sc(2.6, s)),
    )
    layer = Image.new("RGBA", (s, s), SHADOW)
    layer.putalpha(sh.point(lambda v: int(v * 0.70)))
    canvas = Image.alpha_composite(canvas, layer)

    # 外高光（只留主体外缘）
    gl = ImageChops.offset(
        body.filter(ImageFilter.GaussianBlur(sc(2.4, s))),
        int(-sc(1.2, s)), int(-sc(1.8, s)),
    )
    ring = ImageChops.subtract(gl, body)
    layer = Image.new("RGBA", (s, s), HIGHLIGHT)
    layer.putalpha(ring.point(lambda v: int(v * 0.62)))
    canvas = Image.alpha_composite(canvas, layer)

    # 主体
    obj = vertical_gradient(s, OBJECT_TOP, OBJECT_BOT)

    # 内阴影（右下）
    inner = ImageChops.subtract(
        body,
        ImageChops.offset(body, int(-sc(1.8, s)), int(-sc(2.4, s))),
    ).filter(ImageFilter.GaussianBlur(sc(1.8, s)))
    obj = Image.composite(
        Image.new("RGB", (s, s), INNER_DARK), obj,
        inner.point(lambda v: int(v * 0.55)),
    )

    # 内高光（左上缘）
    inner_hl = ImageChops.subtract(
        body,
        ImageChops.offset(body, int(sc(1.6, s)), int(sc(2.0, s))),
    ).filter(ImageFilter.GaussianBlur(sc(1.4, s)))
    obj = Image.composite(
        Image.new("RGB", (s, s), HIGHLIGHT), obj,
        inner_hl.point(lambda v: int(v * 0.65)),
    )

    # 喇叭口：上暗下亮，表现内壁受光
    obj = Image.composite(
        vertical_gradient(s, MOUTH_TOP, MOUTH_BOT, gamma=1.0), obj,
        mouth.point(lambda v: int(v * 0.92)),
    )

    obj_rgba = obj.convert("RGBA")
    obj_rgba.putalpha(body)
    return Image.alpha_composite(canvas, obj_rgba)


def monochrome(s):
    body, _ = build_masks(s)
    img = Image.new("RGBA", (s, s), (255, 255, 255, 0))
    img.putalpha(body)
    return img


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "--preview"

    if mode == "--preview":
        out = render(432)
        out.save("/tmp/icon_final.png")
        # 模拟圆形遮罩
        mask = Image.new("L", (432, 432), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, 431, 431], fill=255)
        circle = Image.new("RGBA", (432, 432), BG + (255,))
        circle.paste(out, (0, 0), out)
        circle.putalpha(mask)
        circle.save("/tmp/icon_final_circle.png")
        out.resize((48, 48), Image.LANCZOS).save("/tmp/icon_final_48.png")
        print("saved preview")
        return

    res = sys.argv[2]
    for name, px in DENSITIES.items():
        d = f"{res}/mipmap-{name}"
        render(px).save(f"{d}/ic_launcher_foreground.png")
        monochrome(px).save(f"{d}/ic_launcher_monochrome.png")
    print("saved densities")


if __name__ == "__main__":
    main()
