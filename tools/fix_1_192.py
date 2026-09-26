#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192：
   ① 表情图变大：网格里从「被单元格宽约束的 44dp」改为「填满格子并保持正方形」；
      「我的表情」的 +格 与搜索面板「最近使用」行同步放大。
   ② 评分分布条加质感：加高 + 圆角 + 渐变条身 + 生长动画 + 最小可见宽度。
   ③ 检查更新弹窗：更新说明不再按 8 行截断成省略号，改为「限高 + 可滚动」。
   全程带命中数断言。"""
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

# ---------------- ① 表情变大 ----------------
# 1-a 帖子详情「我的表情」网格
patch(B + "ui/pages/ThreadDetailReply.kt",
      "                modifier = Modifier\n                    .size(44.dp)\n",
      "                modifier = Modifier\n"
      "                    // 1.192: 网格里 .size(44.dp) 会被单元格宽度约束撑成「单元格宽 x 44」的矩形，\n"
      "                    // 图片 Fit 居中后只有 44dp 见方，带字表情几乎看不清 → 改为填满格子并保持正方形\n"
      "                    .fillMaxWidth()\n"
      "                    .aspectRatio(1f)\n", 1)

# 1-b 选手评分页「我的表情」网格
patch(B + "ui/pages/PlayerDetailScore.kt",
      "                modifier = Modifier\n                    .size(44.dp)\n",
      "                modifier = Modifier\n"
      "                    // 1.192: 同上——填满格子并保持正方形，表情更大更清晰\n"
      "                    .fillMaxWidth()\n"
      "                    .aspectRatio(1f)\n", 1)
patch(B + "ui/pages/PlayerDetailScore.kt",
      "import androidx.compose.foundation.layout.fillMaxWidth\n",
      "import androidx.compose.foundation.layout.fillMaxWidth\n"
      "import androidx.compose.foundation.layout.aspectRatio\n", 1)

# 1-c 搜索面板结果格子
patch(B + "ui/components/StickerSearch.kt",
      "        Modifier\n            .size(44.dp)\n",
      "        Modifier\n"
      "            // 1.192: 填满格子并保持正方形（原 44dp 在网格里偏小，带字表情看不清）\n"
      "            .fillMaxWidth()\n"
      "            .aspectRatio(1f)\n", 1)
# 1-d 搜索面板「最近使用」行
patch(B + "ui/components/StickerSearch.kt",
      "                        Modifier\n                            .size(44.dp)\n",
      "                        Modifier\n                            .size(56.dp)\n", 1)
patch(B + "ui/components/StickerSearch.kt",
      "import androidx.compose.foundation.layout.fillMaxWidth\n",
      "import androidx.compose.foundation.layout.fillMaxWidth\n"
      "import androidx.compose.foundation.layout.aspectRatio\n", 1)

# 1-e 「+」添加格自适应成正方形（与表情格同尺寸）
patch(B + "ui/components/StickerAddCell.kt",
      "    modifier: Modifier = Modifier,\n    size: Dp = 44.dp,\n    corner: Dp = 8.dp,\n",
      "    modifier: Modifier = Modifier,\n    corner: Dp = 8.dp,\n", 1)
patch(B + "ui/components/StickerAddCell.kt",
      " * @param size 格子边长（与表情格同尺寸，默认 44dp）\n", "", 1)
patch(B + "ui/components/StickerAddCell.kt",
      "            modifier = Modifier\n                .requiredSize(size)\n                .clip(shape)\n",
      "            modifier = Modifier\n"
      "                // 1.192: 与表情格子同策略——填满单元格宽度并保持正方形（不再是固定 44dp）\n"
      "                .fillMaxWidth()\n"
      "                .aspectRatio(1f)\n"
      "                .clip(shape)\n", 1)
patch(B + "ui/components/StickerAddCell.kt",
      "                    modifier = Modifier.size(size * 0.42f),\n",
      "                    modifier = Modifier.fillMaxSize(0.42f),\n", 1)
patch(B + "ui/components/StickerAddCell.kt",
      "                    modifier = Modifier.size(size * 0.52f),\n",
      "                    modifier = Modifier.fillMaxSize(0.52f),\n", 1)
patch(B + "ui/components/StickerAddCell.kt",
      "import androidx.compose.foundation.layout.fillMaxWidth\n",
      "import androidx.compose.foundation.layout.fillMaxWidth\n"
      "import androidx.compose.foundation.layout.aspectRatio\n"
      "import androidx.compose.foundation.layout.fillMaxSize\n", 1)

# ---------------- ② 评分分布条质感 ----------------
patch(B + "ui/pages/PlayerDetailCards.kt",
      "                        Box(\n"
      "                            Modifier\n"
      "                                .fillMaxWidth(count.toFloat() / maxCount)\n"
      "                                .fillMaxHeight()\n"
      "                                .clip(RoundedCornerShape(4.dp))\n"
      "                                .background(scoreColor(\"$level.0\")),\n"
      "                        )\n",
      "                        // 1.192: 渐变条身 + 生长动画 + 最小可见宽度，让分布条更有质感\n"
      "                        val barColor = scoreColor(\"$level.0\")\n"
      "                        val frac by animateFloatAsState(\n"
      "                            targetValue = if (count <= 0L) 0f\n"
      "                                else (count.toFloat() / maxCount).coerceAtLeast(0.05f),\n"
      "                            animationSpec = tween(550, easing = FastOutSlowInEasing),\n"
      "                            label = \"distBar\",\n"
      "                        )\n"
      "                        Box(\n"
      "                            Modifier\n"
      "                                .fillMaxWidth(frac)\n"
      "                                .fillMaxHeight()\n"
      "                                .clip(RoundedCornerShape(5.dp))\n"
      "                                .background(\n"
      "                                    Brush.horizontalGradient(\n"
      "                                        listOf(barColor.copy(alpha = 0.7f), barColor),\n"
      "                                    ),\n"
      "                                ),\n"
      "                        )\n", 1)
patch(B + "ui/pages/PlayerDetailCards.kt",
      "                            .height(8.dp)\n                            .clip(RoundedCornerShape(4.dp))\n",
      "                            .height(10.dp)\n                            .clip(RoundedCornerShape(5.dp))\n", 1)
patch(B + "ui/pages/PlayerDetailCards.kt",
      "import androidx.compose.animation.core.Animatable\n",
      "import androidx.compose.animation.core.Animatable\n"
      "import androidx.compose.animation.core.animateFloatAsState\n"
      "import androidx.compose.animation.core.FastOutSlowInEasing\n", 1)
patch(B + "ui/pages/PlayerDetailCards.kt",
      "import androidx.compose.ui.graphics.Color\n",
      "import androidx.compose.ui.graphics.Brush\nimport androidx.compose.ui.graphics.Color\n", 1)

# ---------------- ③ 检查更新弹窗不截断 ----------------
patch(B + "ui/pages/AboutPage.kt",
      "        if (info.changelog.isNotBlank()) {\n"
      "            Text(\n"
      "                info.changelog,\n"
      "                Modifier.padding(24.dp, 14.dp, 24.dp, 0.dp),\n"
      "                fontSize = 13.sp,\n"
      "                lineHeight = 20.sp,\n"
      "                color = contentColor,\n"
      "                maxLines = 8,\n"
      "                overflow = TextOverflow.Ellipsis,\n"
      "            )\n"
      "        }\n",
      "        if (info.changelog.isNotBlank()) {\n"
      "            // 1.192: 更新说明过长时不再截断成省略号——改为「限高 + 可滚动」，完整内容都能看到\n"
      "            Box(\n"
      "                Modifier\n"
      "                    .padding(start = 24.dp, end = 24.dp, top = 14.dp)\n"
      "                    .heightIn(max = 280.dp)\n"
      "                    .verticalScroll(rememberScrollState()),\n"
      "            ) {\n"
      "                Text(\n"
      "                    info.changelog,\n"
      "                    fontSize = 13.sp,\n"
      "                    lineHeight = 20.sp,\n"
      "                    color = contentColor,\n"
      "                )\n"
      "            }\n"
      "        }\n", 1)
patch(B + "ui/pages/AboutPage.kt",
      "import androidx.compose.foundation.verticalScroll\n",
      "import androidx.compose.foundation.verticalScroll\n"
      "import androidx.compose.foundation.layout.heightIn\n", 1)
patch(B + "ui/pages/AboutPage.kt",
      "import androidx.compose.ui.text.style.TextOverflow\n", "", 1)

print("FIX 1.192 OK")