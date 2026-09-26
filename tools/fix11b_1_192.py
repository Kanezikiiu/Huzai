#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第十三批补：仅处理 AboutPage.kt（AboutPage 叶子 + AboutSubPage 嵌套）"""
import io

ap = "app/src/main/java/com/java/myapplication/ui/pages/AboutPage.kt"
DISP = ("    // 1.192: 计数兜底——页面被任何路径销毁（如被重挂载）都会 onDispose 回收，防 Tab 栏计数泄漏\n"
        "    androidx.compose.runtime.DisposableEffect(Unit) {\n"
        "        onDispose { SecondaryPage.exit() }\n"
        "    }\n")
BLOCK = "    LaunchedEffect(Unit) {\n        SecondaryPage.enter()\n        progress.animateTo(1f, tween(280))\n    }\n"

with io.open(ap, encoding='utf-8') as f:
    s = f.read()

# 1) AboutPage（叶子）：函数头 + BLOCK → 追加兜底
head = ("fun AboutPage(onClose: () -> Unit) {\n    val context = LocalContext.current\n"
        "    val progress = remember { Animatable(0f) }\n" + BLOCK)
assert s.count(head) == 1, "AboutPage head+block count=%d" % s.count(head)
s = s.replace(head, head + DISP)

# 2) AboutSubPage（嵌套）：定位函数头，仅在其后处理
marker = "private fun AboutSubPage(kind: AboutSub, context: Context, onClose: () -> Unit) {"
idx = s.index(marker)
head_s, tail = s[:idx], s[idx:]
assert tail.count(BLOCK) >= 1, "AboutSubPage BLOCK not found"
tail = tail.replace(BLOCK, BLOCK + DISP, 1)                       # 加兜底
old_exit = "        if (closing) {\n            SecondaryPage.exit()\n"
assert tail.count(old_exit) >= 1, "AboutSubPage closing exit not found"
tail = tail.replace(old_exit, "        if (closing) {\n", 1)        # 去掉 closing exit
s = head_s + tail

with io.open(ap, 'w', encoding='utf-8') as f:
    f.write(s)
print("FIX11b AboutPage.kt OK")