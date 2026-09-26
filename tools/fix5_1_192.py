#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第五批（真 bug）：固定频道开关「有反馈但状态不变」。
   根因：Switch 的 checked = !hidden（开=显示），但回调直接 hidden = on；
         Material 回调的是 !checked，于是 on == hidden，赋值后值不变（空操作）。
   修：回调里取反再存。带命中数断言。"""
import io

def patch(path, old, new, expect):
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    n = s.count(old)
    assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:70], n)
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s.replace(old, new))
    print("OK %s: %d hit(s)" % (path, n))

B = "app/src/main/java/com/java/myapplication/"

patch(B + "ui/pages/TopicPickerPage.kt",
      "                    ) { on ->\n"
      "                        hotHidden = on\n"
      "                        HupuPrefs.setHomeHotHidden(on)\n"
      "                    }\n",
      "                    ) { on ->\n"
      "                        // on = 开关的新状态（true=显示热帖）；存的是「是否隐藏」，需取反\n"
      "                        hotHidden = !on\n"
      "                        HupuPrefs.setHomeHotHidden(!on)\n"
      "                    }\n", 1)

patch(B + "ui/pages/ScorePickerPage.kt",
      "            ) { on ->\n"
      "                commonHidden = on\n"
      "                HupuPrefs.setScoreCommonHidden(on)\n"
      "            }\n",
      "            ) { on ->\n"
      "                // on = 开关的新状态（true=显示虎扑评分）；存的是「是否隐藏」，需取反\n"
      "                commonHidden = !on\n"
      "                HupuPrefs.setScoreCommonHidden(!on)\n"
      "            }\n", 1)

print("FIX5 1.192 OK")