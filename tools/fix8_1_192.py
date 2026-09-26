#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第九批：我的页两处交互修正
   #1 登录卡片点击卡顿 —— LoginPage 延迟挂载 WebView（先播入场动画，再创建 WebView）
   #2 退出登录改为毛玻璃弹窗确认（LiquidGlassDialog），不再行内展开
   原子式：先全部校验命中数，再统一写盘。"""
import io

EDITS = {}

def patch(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

P = "app/src/main/java/com/java/myapplication/ui/pages/ProfilePage.kt"
L = "app/src/main/java/com/java/myapplication/ui/pages/LoginPage.kt"

# ================= ProfilePage =================
# 1a. 引入 LiquidGlassDialog
patch(P,
      "import com.java.myapplication.ui.glass.LiquidGlassCard\n",
      "import com.java.myapplication.ui.glass.LiquidGlassCard\n"
      "import com.java.myapplication.ui.glass.LiquidGlassDialog\n", 1)

# 1b. 删除行内「确认退出 / 取消」展开块
patch(P,
      '                        if (logoutAsk) {\n'
      '                            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {\n'
      '                                Text(\n'
      '                                    "\\u786e\\u8ba4\\u9000\\u51fa",\n'
      '                                    fontSize = 13.sp,\n'
      '                                    color = MaterialTheme.colorScheme.error,\n'
      '                                    modifier = Modifier\n'
      '                                        .clip(RoundedCornerShape(10.dp))\n'
      '                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))\n'
      '                                        .clickable {\n'
      '                                            HupuAccount.logout()\n'
      '                                            com.java.myapplication.data.HupuFollowStore.reset()\n'
      '                                            logoutAsk = false\n'
      '                                        }\n'
      '                                        .padding(horizontal = 14.dp, vertical = 6.dp),\n'
      '                                )\n'
      '                                Spacer(Modifier.width(10.dp))\n'
      '                                Text(\n'
      '                                    "\\u53d6\\u6d88",\n'
      '                                    fontSize = 13.sp,\n'
      '                                    color = MaterialTheme.colorScheme.onSurfaceVariant,\n'
      '                                    modifier = Modifier\n'
      '                                        .clip(RoundedCornerShape(10.dp))\n'
      '                                        .clickable { logoutAsk = false }\n'
      '                                        .padding(horizontal = 14.dp, vertical = 6.dp),\n'
      '                                )\n'
      '                            }\n'
      '                        }\n', "", 1)

# 1c. 在「默认启动页」弹窗之后追加退出登录弹窗
_anchor = ('                onDismiss = { startTabAsk = false },\n'
           '            )\n'
           '        }\n')
_dialog = ('        // 1.192: 退出登录改为毛玻璃弹窗确认（不再行内展开）\n'
           '        if (logoutAsk) {\n'
           '            LiquidGlassDialog(\n'
           '                backdrop = backdrop,\n'
           '                title = "退出登录",\n'
           '                message = "退出后将清除本机登录凭据，返回未登录状态。",\n'
           '                confirmText = "退出",\n'
           '                dismissText = "取消",\n'
           '                onConfirm = {\n'
           '                    HupuAccount.logout()\n'
           '                    com.java.myapplication.data.HupuFollowStore.reset()\n'
           '                },\n'
           '                onDismiss = { logoutAsk = false },\n'
           '            )\n'
           '        }\n')
patch(P, _anchor, _anchor + _dialog, 1)

# ================= LoginPage =================
# 2a. 版本探测改为延迟计算（随 WebView 一起）
patch(L,
      '    // 1.179(B): 系统 WebView 主版本——过低是老设备「验证码加载失败」的常见根因\n'
      '    val webViewMajor = remember {\n'
      '        runCatching {\n'
      '            val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)\n'
      '            Regex("Chrome/(\\\\d+)").find(ua)?.groupValues?.get(1)?.toIntOrNull() ?: 0\n'
      '        }.getOrDefault(0)\n'
      '    }\n',
      '    // 1.192: 版本探测改为「随 WebView 一起延迟计算」——getDefaultUserAgent 首次调用\n'
      '    // 会触发 WebView 内核初始化，放在首帧会阻塞入场动画。\n'
      '    var webViewMajor by remember { mutableIntStateOf(0) }\n', 1)

# 2b. 入场动画跑完再挂载 WebView
patch(L,
      '    val progress = remember { androidx.compose.animation.core.Animatable(0f) }\n'
      '    androidx.compose.runtime.LaunchedEffect(Unit) {\n'
      '        SecondaryPage.enter()\n'
      '        progress.animateTo(1f, androidx.compose.animation.core.tween(280))\n'
      '    }\n',
      '    val progress = remember { androidx.compose.animation.core.Animatable(0f) }\n'
      '    // 1.192: 先播「页面打开」动画，再挂载 WebView。WebView 构造 + 首次加载很重，\n'
      '    // 若与首帧同帧创建会阻塞入场动画，表现为「先加载再打开」的明显延迟/卡顿。\n'
      '    var webMounted by remember { mutableStateOf(false) }\n'
      '    androidx.compose.runtime.LaunchedEffect(Unit) {\n'
      '        SecondaryPage.enter()\n'
      '        progress.animateTo(1f, androidx.compose.animation.core.tween(280))\n'
      '        webViewMajor = runCatching {\n'
      '            val ua = android.webkit.WebSettings.getDefaultUserAgent(ctx)\n'
      '            Regex("Chrome/(\\\\d+)").find(ua)?.groupValues?.get(1)?.toIntOrNull() ?: 0\n'
      '        }.getOrDefault(0)\n'
      '        webMounted = true\n'
      '    }\n', 1)

# 2c. WebView 延迟挂载 + 占位转圈
patch(L,
      '            HupuLoginWebView(\n'
      '                onLoginSuccess = { finishLogin() },\n'
      '                reloadTick = reloadTick,\n'
      '                onLoadError = { loadError = it },\n'
      '            )\n',
      '            if (webMounted) {\n'
      '                HupuLoginWebView(\n'
      '                    onLoginSuccess = { finishLogin() },\n'
      '                    reloadTick = reloadTick,\n'
      '                    onLoadError = { loadError = it },\n'
      '                )\n'
      '            } else {\n'
      '                // WebView 挂载前的占位：居中转圈，明确「已打开、正在加载」\n'
      '                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {\n'
      '                    CircularProgressIndicator()\n'
      '                }\n'
      '            }\n', 1)

# ---------- 原子应用 ----------
total = 0
for path, edits in EDITS.items():
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    for old, new, expect in edits:
        n = s.count(old)
        assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:80], n)
        s = s.replace(old, new)
        total += n
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print("OK %s (%d edits)" % (path, len(edits)))
print("FIX8 1.192 OK, %d replacements" % total)
