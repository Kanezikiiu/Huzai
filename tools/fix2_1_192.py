#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第二批（按真机反馈）：
   ① 「最近使用」格子与搜索结果网格同尺寸（之前只到 56dp，相对放大的网格仍偏小）；
   ② 分布条生长动画改为 Animatable 从 0 起播（animateFloatAsState 首帧不生长，所以看不到）；
   ③ 毛玻璃弹窗卡片本体吞掉点击，点弹窗内空白处不再关闭。
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

# ---------- ① 最近使用：与网格同尺寸 ----------
patch(B + "ui/components/StickerSearch.kt",
      "import androidx.compose.ui.platform.LocalContext\n",
      "import androidx.compose.ui.platform.LocalContext\n"
      "import androidx.compose.ui.platform.LocalConfiguration\n", 1)
patch(B + "ui/components/StickerSearch.kt",
      "    val version = HupuPrefs.stickerRecentVersion\n"
      "    val recents = remember(version) { HupuPrefs.loadStickerRecent() }\n",
      "    val version = HupuPrefs.stickerRecentVersion\n"
      "    // 1.192: 「最近使用」格子与搜索结果网格同一尺寸\n"
      "    // （网格：5 列、水平 padding 共 24dp、列间距共 40dp → 格子 =（屏宽 - 64）/ 5）\n"
      "    val screenW = LocalConfiguration.current.screenWidthDp\n"
      "    val recentCell = ((screenW - 64) / 5).coerceAtLeast(48).dp\n"
      "    val recents = remember(version) { HupuPrefs.loadStickerRecent() }\n", 1)
patch(B + "ui/components/StickerSearch.kt",
      "                        Modifier\n                            .size(56.dp)\n",
      "                        Modifier\n                            .size(recentCell)\n", 1)

# ---------- ② 分布条生长动画：Animatable 从 0 起播 ----------
patch(B + "ui/pages/PlayerDetailCards.kt",
      "                        val frac by animateFloatAsState(\n"
      "                            targetValue = if (count <= 0L) 0f\n"
      "                                else (count.toFloat() / maxCount).coerceAtLeast(0.05f),\n"
      "                            animationSpec = tween(550, easing = FastOutSlowInEasing),\n"
      "                            label = \"distBar\",\n"
      "                        )\n",
      "                        val target = if (count <= 0L) 0f\n"
      "                            else (count.toFloat() / maxCount).coerceAtLeast(0.05f)\n"
      "                        // animateFloatAsState 首帧不会从 0 生长（初值即目标值）→ 看不到动画；\n"
      "                        // 这里用 Animatable 显式从 0 播到目标，进场才能看到「条撑开」\n"
      "                        val frac = remember { Animatable(0f) }\n"
      "                        LaunchedEffect(target) {\n"
      "                            frac.animateTo(target, tween(550, easing = FastOutSlowInEasing))\n"
      "                        }\n", 1)
patch(B + "ui/pages/PlayerDetailCards.kt",
      "                                .fillMaxWidth(frac)\n",
      "                                .fillMaxWidth(frac.value)\n", 1)
patch(B + "ui/pages/PlayerDetailCards.kt",
      "import androidx.compose.animation.core.animateFloatAsState\n", "", 1)

# ---------- ③ 弹窗内空白处点击不关闭 ----------
patch(B + "ui/glass/GlassDialog.kt",
      "import com.java.myapplication.ui.theme.isAppDarkTheme\n",
      "import com.java.myapplication.ui.components.tapGuard\n"
      "import com.java.myapplication.ui.theme.isAppDarkTheme\n", 1)
patch(B + "ui/glass/GlassDialog.kt",
      "                    onDrawSurface = { drawRect(containerColor) },\n"
      "                )\n"
      "                .fillMaxWidth(),\n",
      "                    onDrawSurface = { drawRect(containerColor) },\n"
      "                )\n"
      "                .fillMaxWidth()\n"
      "                // 1.192: 卡片本体吞掉点击——否则点弹窗内空白处会穿透到下层遮罩、把弹窗关掉\n"
      "                .tapGuard(),\n", 1)

print("FIX2 1.192 OK")