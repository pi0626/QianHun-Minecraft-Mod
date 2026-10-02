#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""千魂 mod 纹理生成器。

逐像素绘制 Minecraft 1.21.1 / 26.2 资源纹理，禁止缩放插值与渐变，
输出固定为 RGBA、调色板受控的 PNG。

用法：
    <venv>/Scripts/python.exe gen_textures.py

输出到两个工程：
    qianhun_1211/src/main/resources/assets/qianhun/textures/
    qianhun_262/src/main/resources/assets/qianhun/textures/
"""

from __future__ import annotations

import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PROJECTS = ("qianhun_1211", "qianhun_262")
TEX_REL = os.path.join("src", "main", "resources", "assets", "qianhun", "textures")

# 颜色统一用 (r, g, b, a) 存取，a=0 表示全透明
T = (0, 0, 0, 0)

# ---- 千魂书调色板（黑封皮 + 金色镶边 + 书页） ----
COVER_DARK = (11, 10, 15, 255)
COVER = (20, 18, 26, 255)
COVER_HI = (33, 30, 42, 255)
COVER_EDGE = (46, 42, 56, 255)
GOLD = (200, 164, 78, 255)
GOLD_DARK = (138, 111, 46, 255)
PAGE = (217, 210, 190, 255)
PAGE_SHADE = (176, 168, 148, 255)

# ---- 心脏温血调色板（玻璃瓶 + 暗红液体 + 木塞） ----
GLASS = (110, 122, 133, 255)
GLASS_HI = (168, 180, 190, 255)
GLASS_SHADE = (74, 84, 94, 255)
BLOOD = (122, 14, 18, 255)
BLOOD_HI = (163, 24, 31, 255)
BLOOD_DARK = (78, 8, 11, 255)
CORK = (138, 90, 43, 255)
CORK_DARK = (99, 63, 30, 255)


class Canvas:
    """固定尺寸的像素画布。所有绘制都落在整数格子上。"""

    def __init__(self, w: int, h: int) -> None:
        self.w = w
        self.h = h
        self.px = [[T for _ in range(w)] for _ in range(h)]

    def set(self, x: int, y: int, c) -> None:
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def rect(self, x0: int, y0: int, x1: int, y1: int, c) -> None:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c)

    def frame(self, x0: int, y0: int, x1: int, y1: int, c) -> None:
        for x in range(x0, x1 + 1):
            self.set(x, y0, c)
            self.set(x, y1, c)
        for y in range(y0, y1 + 1):
            self.set(x0, y, c)
            self.set(x1, y, c)

    def save(self, path: str) -> None:
        img = Image.new("RGBA", (self.w, self.h))
        img.putdata([self.px[y][x] for y in range(self.h) for x in range(self.w)])
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path, "PNG", optimize=True)

    def audit(self, path: str) -> list:
        """校验：尺寸、RGBA、透明背景、边缘不得有半透明像素。"""
        problems = []
        img = Image.open(path)
        if img.mode != "RGBA":
            problems.append("模式不是 RGBA")
        if img.size != (self.w, self.h):
            problems.append(f"尺寸异常 {img.size}")
        alphas = list(img.tobytes()[3::4])
        if 0 not in alphas:
            problems.append("背景不是透明")
        if any(0 < a < 255 for a in alphas):
            problems.append("存在半透明像素，mipmap 会渗色")
        return problems


def qianhun_book() -> Canvas:
    """千魂书：黑色羊皮封皮、封面无字、页面边缘一圈金色镶边（原著 L4957）。"""
    c = Canvas(16, 16)
    # 书体
    c.rect(3, 2, 13, 13, PAGE)          # 右侧书页
    c.rect(11, 2, 13, 13, PAGE_SHADE)   # 书页厚度阴影
    c.frame(3, 2, 13, 13, COVER_DARK)
    # 封皮
    c.rect(3, 3, 10, 12, COVER)
    c.rect(3, 3, 4, 12, COVER_DARK)     # 书脊
    c.rect(3, 3, 10, 3, COVER_HI)       # 顶部高光
    c.rect(3, 12, 10, 12, COVER_DARK)   # 底部阴影
    # 金色镶边（一圈）
    c.frame(5, 4, 9, 11, GOLD)
    c.rect(6, 10, 8, 10, GOLD_DARK)
    # 四角
    for (x, y) in ((5, 4), (9, 4), (5, 11), (9, 11)):
        c.set(x, y, GOLD_DARK)
    # 封皮描边
    c.rect(2, 2, 2, 13, COVER_EDGE)
    return c


def heart_blood() -> Canvas:
    """心脏温血：玻璃瓶盛暗红液体，木塞封口（原著 L1286）。"""
    c = Canvas(16, 16)
    # 瓶颈与木塞
    c.rect(6, 1, 9, 3, CORK)
    c.rect(6, 3, 9, 3, CORK_DARK)
    c.rect(6, 4, 9, 5, GLASS)
    # 瓶身
    c.rect(4, 5, 11, 13, GLASS)
    c.frame(4, 5, 11, 13, GLASS_SHADE)
    c.rect(5, 6, 10, 12, BLOOD)
    c.rect(5, 6, 10, 7, BLOOD_HI)
    c.rect(5, 11, 10, 12, BLOOD_DARK)
    # 高光
    c.rect(5, 6, 5, 10, GLASS_HI)
    c.set(6, 6, GLASS_HI)
    c.set(7, 6, GLASS_HI)
    # 瓶底
    c.rect(4, 13, 11, 13, GLASS_SHADE)
    return c


def _box(c: Canvas, u: int, v: int, w: int, h: int, d: int, face, top, bottom, side_dark, side_light) -> None:
    """按 Minecraft 标准展开方式给一个立方体贴面。

    face/top/bottom/side_dark/side_light 均为单像素颜色，或 "sample" 表示用回调填。
    """
    # front / back
    c.rect(u + d, v + d, u + d + w - 1, v + d + h - 1, face)
    c.rect(u + d + w + d, v + d, u + d + w + d + w - 1, v + d + h - 1, face)
    # left / right
    c.rect(u, v + d, u + d - 1, v + d + h - 1, side_dark)
    c.rect(u + d + w, v + d, u + d + w + d - 1, v + d + h - 1, side_light)
    # top / bottom
    c.rect(u + d, v, u + d + w - 1, v + d - 1, top)
    c.rect(u + d + w, v, u + d + w + w - 1, v + d - 1, bottom)


def _hash(x: int, y: int) -> int:
    """确定性伪随机，保证同一张图每次生成完全一致。"""
    n = (x * 73856093) ^ (y * 19349663)
    return (n >> 3) & 0xFFFF


def youzhi() -> Canvas:
    """游尸（兜底人形）。

    原著外观依据：呆滞、腐烂、恶臭、眼睛模糊一团没有黑白分明
    （L6743 / L6811 / L7202）。采用原版人形 64x64 展开，配色为
    灰绿尸斑 + 深色眼窝，整体压暗，方便渲染时再叠一层暗色。
    """
    c = Canvas(64, 64)

    SKIN = (96, 102, 88, 255)
    SKIN_DARK = (74, 80, 68, 255)
    SKIN_LIGHT = (108, 114, 99, 255)
    ROT = (61, 66, 52, 255)
    BONE = (146, 143, 124, 255)
    CLOTH = (54, 52, 50, 255)
    CLOTH_DARK = (40, 38, 37, 255)
    CLOTH_LIGHT = (66, 64, 61, 255)
    SOCKET = (24, 22, 24, 255)

    # 头 8x8x8 @ (0,0)
    _box(c, 0, 0, 8, 8, 8, SKIN, SKIN_LIGHT, SKIN_DARK, SKIN_DARK, SKIN_LIGHT)
    # 躯干 8x12x4 @ (16,16)
    _box(c, 16, 16, 8, 12, 4, CLOTH, CLOTH_LIGHT, CLOTH_DARK, CLOTH_DARK, CLOTH_LIGHT)
    # 右臂 4x12x4 @ (40,16)   左臂 4x12x4 @ (32,48)
    _box(c, 40, 16, 4, 12, 4, CLOTH, CLOTH_LIGHT, CLOTH_DARK, CLOTH_DARK, CLOTH_LIGHT)
    _box(c, 32, 48, 4, 12, 4, CLOTH, CLOTH_LIGHT, CLOTH_DARK, CLOTH_DARK, CLOTH_LIGHT)
    # 右腿 4x12x4 @ (0,16)    左腿 4x12x4 @ (16,48)
    _box(c, 0, 16, 4, 12, 4, CLOTH_DARK, CLOTH, CLOTH_DARK, CLOTH_DARK, CLOTH)
    _box(c, 16, 48, 4, 12, 4, CLOTH_DARK, CLOTH, CLOTH_DARK, CLOTH_DARK, CLOTH)

    # 面部：眼窝压黑，没有黑白分明（原著 L6811）
    for ex in (9, 12):
        c.rect(ex, 12, ex + 1, 13, SOCKET)
    # 嘴：裂口
    c.rect(10, 14, 13, 14, SOCKET)
    c.set(11, 14, BONE)

    # 暴露的骨头与尸斑：只在皮肤区域打点，确定性分布
    skin_zones = [(8, 8, 15, 15), (24, 8, 31, 15), (0, 8, 7, 15),
                  (16, 8, 23, 15), (0, 0, 7, 7), (8, 0, 15, 7)]
    for (x0, y0, x1, y1) in skin_zones:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                h = _hash(x, y) % 100
                if h < 12:
                    c.set(x, y, ROT)
                elif h < 16:
                    c.set(x, y, BONE)
                elif h < 24:
                    c.set(x, y, SKIN_DARK)
    return c


def main() -> int:
    assets = {
        os.path.join("item", "qianhun_book.png"): qianhun_book(),
        os.path.join("item", "heart_blood.png"): heart_blood(),
        os.path.join("entity", "youzhi.png"): youzhi(),
    }

    failures = 0
    for project in PROJECTS:
        base = os.path.join(ROOT, project, TEX_REL)
        for rel, canvas in assets.items():
            path = os.path.join(base, rel)
            canvas.save(path)
            problems = canvas.audit(path)
            status = "OK" if not problems else "FAIL " + "; ".join(problems)
            print(f"{project}/{rel}  {canvas.w}x{canvas.h}  {status}")
            failures += len(problems)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
