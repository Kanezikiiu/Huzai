#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191 第三批：
   1) 深色档弹窗透明度与浅色档视觉等价（容器 alpha 0.34 -> 0.5、模糊 8dp -> 16dp）
   2) 阅读字号页示例用户名 虎飞 -> 猪猪侠 GGBond（含头像字母 虎 -> 猪）
   3) 信息流设置页输入框圆角 12dp -> 16dp（与外层卡片一致）
   全程带命中数断言，防止静默失效。"""
import io

def patch(path, old, new, expect):
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    n = s.count(old)
    assert n == expect, "%s: expect %d hit(s) of %r, got %d" % (path, expect, old, n)
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s.replace(old, new))
    print("OK %s: %d hit(s)" % (path, n))

B = "app/src/main/java/com/java/myapplication/"

# 1-a 深色档容器色
patch(B + "ui/glass/GlassDialog.kt",
      "else Color(0xFF121212).copy(0.34f)",
      "else Color(0xFF121212).copy(0.5f)", 1)

# 1-b 深色档模糊与浅色档一致
patch(B + "ui/glass/GlassDialog.kt",
      "blur(if (isLight) 16f.dp.toPx() else 8f.dp.toPx())",
      "blur(16f.dp.toPx())", 1)

# 2  示例用户名（源码为 \uXXXX 转义写法）
patch(B + "ui/pages/TextSizeSettingsPage.kt",
      '"\\u864e\\u98de"',
      '"\\u732a\\u732a\\u4fa0 GGBond"', 1)
patch(B + "ui/pages/TextSizeSettingsPage.kt",
      '"\\u864e"',
      '"\\u732a"', 1)

# 3  信息流设置页输入框圆角
patch(B + "ui/pages/FilterSettingsPage.kt",
      "shape = RoundedCornerShape(12.dp),",
      "shape = RoundedCornerShape(16.dp),", 1)

print("FIX3 OK")
