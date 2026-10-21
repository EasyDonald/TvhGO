from PIL import Image, ImageDraw, ImageFilter
import os

S = 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

# 背景：斜向渐变（青绿 -> 深青）
grad = Image.new("RGB", (S, S))
d = ImageDraw.Draw(grad)
c1 = (38, 198, 218)   # cyan 400
c2 = (0, 105, 92)     # teal 800
for y in range(S):
    for_x = y / (S - 1)
    r = int(c1[0] + (c2[0] - c1[0]) * for_x)
    g = int(c1[1] + (c2[1] - c1[1]) * for_x)
    b = int(c1[2] + (c2[2] - c1[2]) * for_x)
    d.line([(0, y), (S, y)], fill=(r, g, b))

mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=110, fill=255)
img.paste(grad, (0, 0), mask)

d = ImageDraw.Draw(img)

# 屏幕（白色圆角矩形，略微上移）
sw, sh = 300, 190
sx, sy = (S - sw) // 2, 150
d.rounded_rectangle([sx, sy, sx + sw, sy + sh], radius=34, fill=(255, 255, 255, 245))

# 屏幕里的播放三角（青绿色）
cx, cy = sx + sw // 2, sy + sh // 2
tri = [(cx - 30, cy - 44), (cx - 30, cy + 44), (cx + 52, cy)]
d.polygon(tri, fill=(0, 137, 123, 255))

# 底座
d.rounded_rectangle([S // 2 - 70, sy + sh + 26, S // 2 + 70, sy + sh + 46], radius=10, fill=(255, 255, 255, 235))
# 天线/信号弧线（右下角点缀）
d.arc([sx + sw - 96, sy - 54, sx + sw + 24, sy + 66], start=300, end=360, fill=(255, 255, 255, 210), width=12)

# 外圈淡淡的高光（提升质感）
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).rounded_rectangle([6, 6, S - 7, S - 7], radius=104, outline=(255, 255, 255, 55), width=6)
img = Image.alpha_composite(img, glow)

out = r"C:\Users\Administrator\Documents\deepseek-harness\default-workspace\tvhclient\android\res\drawable\ic_launcher.png"
img.resize((192, 192), Image.LANCZOS).save(out)
print("icon saved", out, os.path.getsize(out))
