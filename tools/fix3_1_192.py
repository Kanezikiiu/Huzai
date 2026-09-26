#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第三批：生长动画只在进入页面这一次播放（LazyColumn 回收后再滚回来不重播）。带命中数断言。"""
import io

def patch(path, old, new, expect):
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    n = s.count(old)
    assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:60], n)
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s.replace(old, new))
    print("OK %s: %d hit(s)" % (path, n))

B = "app/src/main/java/com/java/myapplication/"

patch(B + "ui/pages/PlayerDetailCards.kt",
      "            val maxCount = d.distribution.maxOf { it.second }.coerceAtLeast(1L)\n"
      "            d.distribution.forEach { (level, count) ->\n",
      "            val maxCount = d.distribution.maxOf { it.second }.coerceAtLeast(1L)\n"
      "            // 1.192: 生长动画只在「进入本页的第一次」播放——LazyColumn 回收该 item 后再滚回来不重播。\n"
      "            // played 用 rememberSaveable：item 的 saved state 由 LazyList 按 key 保存/恢复，回收也记得住。\n"
      "            val played = rememberSaveable { mutableStateOf(false) }\n"
      "            val animateOnce = remember { !played.value }\n"
      "            LaunchedEffect(Unit) { played.value = true }\n"
      "            d.distribution.forEach { (level, count) ->\n", 1)

patch(B + "ui/pages/PlayerDetailCards.kt",
      "                        val frac = remember { Animatable(0f) }\n"
      "                        LaunchedEffect(target) {\n"
      "                            frac.animateTo(target, tween(550, easing = FastOutSlowInEasing))\n"
      "                        }\n",
      "                        val frac = remember { Animatable(0f) }\n"
      "                        LaunchedEffect(target, animateOnce) {\n"
      "                            if (animateOnce) {\n"
      "                                frac.animateTo(target, tween(550, easing = FastOutSlowInEasing))\n"
      "                            } else {\n"
      "                                // 非首次进入（含数据刷新）：直接到位，不重播动画\n"
      "                                frac.snapTo(target)\n"
      "                            }\n"
      "                        }\n", 1)

print("FIX3 1.192 OK")