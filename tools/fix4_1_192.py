#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第四批（真机反馈）：
   ① 固定频道卡片整行可点（原来只有右侧 Switch 可点，点卡片主体没反应）；
   ② 卡片圆角 14dp → 22dp，与「自定义首页频道」页下方搜索框一致。
   带命中数断言。"""
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

patch(B + "ui/components/FeedUi.kt",
      "            .clip(RoundedCornerShape(14.dp))\n"
      "            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))\n"
      "            .padding(horizontal = 14.dp, vertical = 10.dp),\n",
      "            // 1.192: 圆角与「自定义首页频道」页的搜索框保持一致（22dp）\n"
      "            .clip(RoundedCornerShape(22.dp))\n"
      "            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))\n"
      "            // 1.192: 整行可点（与「滚动时自动隐藏底栏」等开关行同款手感），\n"
      "            // 不再只有右侧那个小 Switch 可点——之前点卡片主体没有任何反应\n"
      "            .clickable(enabled = enabled) { onCheckedChange(!checked) }\n"
      "            .padding(horizontal = 14.dp, vertical = 10.dp),\n", 1)

print("FIX4 1.192 OK")