#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第八批：打分面板（ScorePanelOverlay）从旧弹窗迁移到新弹窗（Liquid Glass 毛玻璃）。
   · LiquidGlassCard 新增 dismissible 参数（提交中点遮罩/返回都不关闭）
   · PlayerDetailPage：为内容层挂记录层（毛玻璃采样源），并把 backdrop 传给面板
   · 面板：旧「半透明遮罩 + 纯色居中卡」→ LiquidGlassCard；
          按钮/输入框改用 onSurface 淡底 + 胶囊，避免在毛玻璃上看不见
   原子式：先全部校验命中数，再统一写盘。"""
import io

EDITS = {}

def patch(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

B = "app/src/main/java/com/java/myapplication/"

# ================= 1) LiquidGlassCard: dismissible =================
patch(B + "ui/glass/GlassDialog.kt",
      "    zIndex: Float = 1100f,\n"
      "    onDismiss: () -> Unit,\n"
      "    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,\n"
      ") {\n",
      "    zIndex: Float = 1100f,\n"
      "    onDismiss: () -> Unit,\n"
      "    /** 1.192: false 时点遮罩 / 返回都不关闭（如「提交中」不允许关闭） */\n"
      "    dismissible: Boolean = true,\n"
      "    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,\n"
      ") {\n", 1)
patch(B + "ui/glass/GlassDialog.kt",
      "    val close: () -> Unit = { if (!closing) closing = true }\n",
      "    val close: () -> Unit = { if (!closing && dismissible) closing = true }\n", 1)

# ================= 2) PlayerDetailPage：记录层 + 传参 =================
patch(B + "ui/pages/PlayerDetailPage.kt",
      "import com.java.myapplication.ui.components.tapGuard\n",
      "import com.java.myapplication.ui.components.tapGuard\n"
      "import com.kyant.backdrop.backdrops.layerBackdrop\n"
      "import com.kyant.backdrop.backdrops.rememberLayerBackdrop\n", 1)
patch(B + "ui/pages/PlayerDetailPage.kt",
      "    Box(\n"
      "        Modifier\n"
      "            .fillMaxSize()\n"
      "            .zIndex(3f)\n"
      "            .graphicsLayer { translationX = (1f - progress.value) * size.width }\n",
      "    // 1.192: 打分面板改用 Liquid Glass——需要给「面板背后的内容」挂记录层（毛玻璃采样源）。\n"
      "    // 先铺一层不透明页面底色再画内容：记录层若透明，卡片会显得非常透。\n"
      "    val dialogBg = MaterialTheme.colorScheme.background\n"
      "    val backdrop = rememberLayerBackdrop {\n"
      "        drawRect(dialogBg)\n"
      "        drawContent()\n"
      "    }\n"
      "    Box(\n"
      "        Modifier\n"
      "            .fillMaxSize()\n"
      "            .zIndex(3f)\n"
      "            .graphicsLayer { translationX = (1f - progress.value) * size.width }\n", 1)
patch(B + "ui/pages/PlayerDetailPage.kt",
      "        Crossfade(targetState = state, animationSpec = tween(180), label = \"playerDetailSwitch\") { s ->",
      "        Crossfade(\n"
      "            targetState = state,\n"
      "            // 1.192: 内容层挂记录层——打分面板（毛玻璃）画在本节点之后的兄弟位置\n"
      "            modifier = Modifier.layerBackdrop(backdrop),\n"
      "            animationSpec = tween(180),\n"
      "            label = \"playerDetailSwitch\",\n"
      "        ) { s ->", 1)
patch(B + "ui/pages/PlayerDetailPage.kt",
      "            ScorePanelOverlay(\n"
      "                myScore = myScore,\n",
      "            ScorePanelOverlay(\n"
      "                backdrop = backdrop,\n"
      "                myScore = myScore,\n", 1)

# ================= 3) 面板本体 =================
patch(B + "ui/pages/PlayerDetailScore.kt",
      "import com.java.myapplication.ui.components.tapGuard\n",
      "import com.java.myapplication.ui.components.tapGuard\n"
      "import com.java.myapplication.ui.glass.LiquidGlassCard\n"
      "import com.java.myapplication.ui.theme.isAppDarkTheme\n"
      "import com.kyant.backdrop.Backdrop\n", 1)
patch(B + "ui/pages/PlayerDetailScore.kt",
      "internal fun ScorePanelOverlay(\n    myScore: Int,\n",
      "internal fun ScorePanelOverlay(\n"
      "    backdrop: Backdrop,\n"
      "    myScore: Int,\n", 1)
patch(B + "ui/pages/PlayerDetailScore.kt",
      "    var picked by remember { mutableStateOf(if (myScore > 0) (myScore / 2).coerceIn(1, 5) else 0) }",
      "    val isLight = !isAppDarkTheme()\n"
      "    var picked by remember { mutableStateOf(if (myScore > 0) (myScore / 2).coerceIn(1, 5) else 0) }", 1)
patch(B + "ui/pages/PlayerDetailScore.kt",
      "    Box(\n"
      "        Modifier\n"
      "            .fillMaxSize()\n"
      "            // 1.153: 面板内新增评论输入后需要键盘避让，否则键盘会盖住确认按钮\n"
      "            .imePadding()\n"
      "            .zIndex(5f)\n"
      "            .background(Color.Black.copy(alpha = 0.25f))\n"
      "            .clickable(\n"
      "                indication = null,\n"
      "                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },\n"
      "            ) { if (!submitting) onClose() },\n"
      "    ) {\n"
      "        // 系统返回：关闭面板（盖过宿主与楼中楼的返回手势）\n"
      "        androidx.activity.compose.PredictiveBackHandler { events ->\n"
      "            events.collect { }\n"
      "            if (!submitting) onClose()\n"
      "        }\n"
      "        Column(\n"
      "            Modifier\n"
      "                .align(Alignment.Center)\n"
      "                .padding(horizontal = 40.dp)\n"
      "                // 1.191: 平板/横屏下限制卡片宽度（手机上可用宽度本就 < 400dp，无影响）\n"
      "                .widthIn(max = 400.dp)\n"
      "                .fillMaxWidth()\n"
      "                .clip(RoundedCornerShape(24.dp))\n"
      "                .background(MaterialTheme.colorScheme.surface)\n"
      "                .clickable(\n"
      "                    indication = null,\n"
      "                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },\n"
      "                ) { } // 阻断遮罩点击\n"
      "                .padding(20.dp),\n"
      "            verticalArrangement = Arrangement.spacedBy(14.dp),\n"
      "        ) {\n",
      "    // 1.192: 旧版「半透明遮罩 + 纯色居中卡」改为新弹窗（Liquid Glass 毛玻璃 + Q 弹出入场）。\n"
      "    // 毛玻璃面板不能包在 Dialog() 里，且必须是「内容记录层」之后的兄弟节点\n"
      "    // （宿主 PlayerDetailPage 已给内容层挂 .layerBackdrop(backdrop)）。\n"
      "    LiquidGlassCard(\n"
      "        backdrop = backdrop,\n"
      "        // 1.153: 面板内有评论输入 → 需要键盘避让，否则键盘会盖住确认按钮\n"
      "        modifier = Modifier.imePadding(),\n"
      "        // 提交中不允许关闭（点遮罩 / 系统返回都无效）\n"
      "        dismissible = !submitting,\n"
      "        onDismiss = { onClose() },\n"
      "    ) { close ->\n"
      "        // 系统返回：关闭面板（盖过宿主与楼中楼的返回手势）\n"
      "        androidx.activity.compose.PredictiveBackHandler { events ->\n"
      "            events.collect { }\n"
      "            close()\n"
      "        }\n"
      "        Column(\n"
      "            Modifier.padding(20.dp),\n"
      "            verticalArrangement = Arrangement.spacedBy(14.dp),\n"
      "        ) {\n", 1)
# 取消按钮：胶囊 + onSurface 淡底（毛玻璃上 surfaceVariant 几乎不可见）
patch(B + "ui/pages/PlayerDetailScore.kt",
      "                        .weight(1f)\n"
      "                        .clip(RoundedCornerShape(14.dp))\n"
      "                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))\n"
      "                        .clickable(enabled = !submitting) { onClose() }\n",
      "                        .weight(1f)\n"
      "                        // 1.192: 按钮形态对齐新弹窗（胶囊 + onSurface 淡底）\n"
      "                        .clip(RoundedCornerShape(999.dp))\n"
      "                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLight) 0.12f else 0.16f))\n"
      "                        .clickable(enabled = !submitting) { close() }\n", 1)
patch(B + "ui/pages/PlayerDetailScore.kt",
      "                    Text(\"取消\", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)\n",
      "                    Text(\"取消\", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)\n", 1)
# 确认按钮：胶囊
patch(B + "ui/pages/PlayerDetailScore.kt",
      "                        .weight(1f)\n"
      "                        .clip(RoundedCornerShape(14.dp))\n"
      "                        .background(MaterialTheme.colorScheme.primary)\n",
      "                        .weight(1f)\n"
      "                        // 1.192: 与取消同款胶囊\n"
      "                        .clip(RoundedCornerShape(999.dp))\n"
      "                        .background(MaterialTheme.colorScheme.primary)\n", 1)
# 评论输入框底色：毛玻璃上用 onSurface 淡底，避免发飘
patch(B + "ui/pages/PlayerDetailScore.kt",
      "                    .fillMaxWidth()\n"
      "                    .clip(RoundedCornerShape(14.dp))\n"
      "                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))\n"
      "                    .padding(horizontal = 14.dp, vertical = 10.dp),\n",
      "                    .fillMaxWidth()\n"
      "                    .clip(RoundedCornerShape(14.dp))\n"
      "                    // 1.192: 毛玻璃上用 onSurface 淡底，避免「看起来什么都没有」\n"
      "                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))\n"
      "                    .padding(horizontal = 14.dp, vertical = 10.dp),\n", 1)
# 取消评分行：胶囊 + 同款淡底
patch(B + "ui/pages/PlayerDetailScore.kt",
      "                        .fillMaxWidth()\n"
      "                        .clip(RoundedCornerShape(14.dp))\n"
      "                        .background(\n"
      "                            if (confirmDelete) Color(0xFFE53935).copy(alpha = 0.9f)\n"
      "                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)\n"
      "                        )\n",
      "                        .fillMaxWidth()\n"
      "                        // 1.192: 与新弹窗按钮同款胶囊 + 淡底（确认态保持红色警示）\n"
      "                        .clip(RoundedCornerShape(999.dp))\n"
      "                        .background(\n"
      "                            if (confirmDelete) Color(0xFFE53935).copy(alpha = 0.9f)\n"
      "                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)\n"
      "                        )\n", 1)
patch(B + "ui/pages/PlayerDetailScore.kt",
      "                        color = if (confirmDelete) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,\n",
      "                        color = if (confirmDelete) Color.White else MaterialTheme.colorScheme.onSurface,\n", 1)

# ---------- 原子应用 ----------
total = 0
for path, edits in EDITS.items():
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    for old, new, expect in edits:
        n = s.count(old)
        assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:70], n)
        s = s.replace(old, new)
        total += n
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print("OK %s (%d edits)" % (path, len(edits)))
print("GLASS-SCORE-PANEL 1.192 OK, %d replacements" % total)