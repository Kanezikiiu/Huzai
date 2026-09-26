#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第七批（视觉细节）：
   ① 自定义首页频道：话题列表加条目间距，避免相邻选中项的高亮块连成一片；
   ② 自定义评分频道：被锁定的赛事 chip 描边与填充一起变浅（原来只有填充变浅、描边仍是实色）。
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

# ① 条目间距
patch(B + "ui/pages/TopicPickerPage.kt",
      "                    LazyColumn(\n"
      "                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),\n"
      "                        modifier = Modifier.fillMaxSize(),\n"
      "                    ) {\n",
      "                    LazyColumn(\n"
      "                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),\n"
      "                        // 1.192: 条目之间留出间距——否则两个相邻选中项的圆角高亮块会贴在一起、\n"
      "                        // 视觉上连成一片\n"
      "                        verticalArrangement = Arrangement.spacedBy(6.dp),\n"
      "                        modifier = Modifier.fillMaxSize(),\n"
      "                    ) {\n", 1)

# ② 锁定态描边同步变浅
patch(B + "ui/pages/ScorePickerPage.kt",
      "                                .border(\n"
      "                                    width = 1.dp,\n"
      "                                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,\n"
      "                                    shape = RoundedCornerShape(999.dp),\n"
      "                                )\n",
      "                                .border(\n"
      "                                    width = 1.dp,\n"
      "                                    // 1.192: 被锁定时描边与底色一起变浅——原来只有填充变浅，\n"
      "                                    // 描边仍是实色，看起来像「没被那层浅色盖住」\n"
      "                                    color = when {\n"
      "                                        locked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)\n"
      "                                        checked -> MaterialTheme.colorScheme.primary\n"
      "                                        else -> MaterialTheme.colorScheme.outlineVariant\n"
      "                                    },\n"
      "                                    shape = RoundedCornerShape(999.dp),\n"
      "                                )\n", 1)

print("FIX7 1.192 OK")