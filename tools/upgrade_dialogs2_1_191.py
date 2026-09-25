#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.191(第 2 批): 把两个自定义 Dialog() 弹窗也升级为 Liquid Glass 毛玻璃 + Q 弹出入场。

- ProfilePage.StartTabDialog（默认启动页选择）
- AboutPage.UpdateDialog（发现新版本；关于页手动检查 + 冷启动自动检查共用）

做法同第一批：页面根 Box 内给内容层挂 .layerBackdrop(backdrop)，弹窗作为
内容层的后续兄弟画在同一个 Box 里。
"""
import io
import os

PAGES = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "java", "com", "java", "myapplication", "ui", "pages",
)
ROOT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "..", "app", "src", "main", "java", "com", "java", "myapplication",
)


def load(path):
    with io.open(path, encoding="utf-8") as f:
        return f.read()


def save(path, s):
    with io.open(path, "w", encoding="utf-8") as f:
        f.write(s)


def sub(s, old, new, where):
    n = s.count(old)
    assert n == 1, "命中 %d 次（期望 1）: %s :: %r" % (n, where, old[:80])
    return s.replace(old, new)


GLASS_IMPORTS = (
    "import com.kyant.backdrop.Backdrop\n"
    "import com.kyant.backdrop.backdrops.layerBackdrop\n"
    "import com.kyant.backdrop.backdrops.rememberLayerBackdrop\n"
    "import com.java.myapplication.ui.glass.LiquidGlassButton\n"
    "import com.java.myapplication.ui.glass.LiquidGlassCard\n"
)

# ==========================================================================
# A) ProfilePage.kt —— 默认启动页选择
# ==========================================================================
p = os.path.join(PAGES, "ProfilePage.kt")
s = load(p)
s = sub(s, "import androidx.compose.material3.MaterialTheme\n",
        "import androidx.compose.material3.MaterialTheme\n" + GLASS_IMPORTS,
        "ProfilePage.imports")
s = sub(s, "import androidx.compose.ui.window.Dialog\n", "", "ProfilePage.dropDialogImport")

s = sub(
    s,
    "    Box(modifier.fillMaxSize()) {\n"
    "        Column(Modifier.fillMaxSize()) {\n"
    "            PageHeader(title = \"我的\", trailing = {",
    "    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）\n"
    "    val backdrop = rememberLayerBackdrop()\n"
    "    Box(modifier.fillMaxSize()) {\n"
    "        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {\n"
    "            PageHeader(title = \"我的\", trailing = {",
    "ProfilePage.column",
)

s = sub(
    s,
    "            StartTabDialog(\n"
    "                current = startTabIdx,",
    "            StartTabDialog(\n"
    "                backdrop = backdrop,\n"
    "                current = startTabIdx,",
    "ProfilePage.call",
)

old_start = (
    "/**\n"
    " * 1.182: 默认启动页选择弹窗\n"
    " * iOS 风格：大圆角卡片、标题居左、选项行右侧打勾、取消浅灰大圆角，与项目其它弹窗一致。\n"
    " */\n"
    "@Composable\n"
    "private fun StartTabDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {\n"
    "    Dialog(onDismissRequest = onDismiss) {\n"
    "        Column(\n"
    "            Modifier\n"
    "                .fillMaxWidth()\n"
    "                .clip(RoundedCornerShape(22.dp))\n"
    "                .background(MaterialTheme.colorScheme.surface)\n"
    "                .padding(20.dp),\n"
    "        ) {\n"
    "            Text(\n"
    "                \"默认启动页\",\n"
    "                fontSize = 17.sp,\n"
    "                fontWeight = FontWeight.Bold,\n"
    "                color = MaterialTheme.colorScheme.onSurface,\n"
    "            )\n"
    "            Spacer(Modifier.height(10.dp))\n"
    "            START_TAB_LABELS.forEachIndexed { i, name ->\n"
    "                val on = i == current\n"
    "                Row(\n"
    "                    Modifier\n"
    "                        .fillMaxWidth()\n"
    "                        .clip(RoundedCornerShape(12.dp))\n"
    "                        .clickable { onPick(i) }\n"
    "                        .padding(horizontal = 12.dp, vertical = 13.dp),\n"
    "                    verticalAlignment = Alignment.CenterVertically,\n"
    "                ) {\n"
    "                    Text(\n"
    "                        name,\n"
    "                        fontSize = 15.sp,\n"
    "                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,\n"
    "                        color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,\n"
    "                        modifier = Modifier.weight(1f),\n"
    "                    )\n"
    "                    if (on) {\n"
    "                        Icon(\n"
    "                            Icons.Rounded.Check,\n"
    "                            contentDescription = null,\n"
    "                            tint = MaterialTheme.colorScheme.primary,\n"
    "                            modifier = Modifier.size(20.dp),\n"
    "                        )\n"
    "                    }\n"
    "                }\n"
    "            }\n"
    "            Spacer(Modifier.height(14.dp))\n"
    "            Box(\n"
    "                Modifier\n"
    "                    .fillMaxWidth()\n"
    "                    .height(46.dp)\n"
    "                    .clip(RoundedCornerShape(14.dp))\n"
    "                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))\n"
    "                    .clickable(onClick = onDismiss),\n"
    "                contentAlignment = Alignment.Center,\n"
    "            ) {\n"
    "                Text(\n"
    "                    \"取消\",\n"
    "                    fontSize = 15.sp,\n"
    "                    fontWeight = FontWeight.Medium,\n"
    "                    color = MaterialTheme.colorScheme.onSurface,\n"
    "                )\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}\n"
)
new_start = (
    "/**\n"
    " * 1.182: 默认启动页选择弹窗\n"
    " * 1.191: 换成 Liquid Glass 毛玻璃卡片（与底部 Tab 栏同源）+ Q 弹出入场。\n"
    " */\n"
    "@Composable\n"
    "private fun StartTabDialog(\n"
    "    backdrop: Backdrop,\n"
    "    current: Int,\n"
    "    onPick: (Int) -> Unit,\n"
    "    onDismiss: () -> Unit,\n"
    ") {\n"
    "    LiquidGlassCard(backdrop = backdrop, onDismiss = onDismiss) { close ->\n"
    "        val contentColor = MaterialTheme.colorScheme.onSurface\n"
    "        Text(\n"
    "            \"默认启动页\",\n"
    "            Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),\n"
    "            fontSize = 20.sp,\n"
    "            fontWeight = FontWeight.Medium,\n"
    "            color = contentColor,\n"
    "        )\n"
    "        START_TAB_LABELS.forEachIndexed { i, name ->\n"
    "            val on = i == current\n"
    "            Row(\n"
    "                Modifier\n"
    "                    .fillMaxWidth()\n"
    "                    .clickable { onPick(i); close() }\n"
    "                    .padding(horizontal = 24.dp, vertical = 14.dp),\n"
    "                verticalAlignment = Alignment.CenterVertically,\n"
    "            ) {\n"
    "                Text(\n"
    "                    name,\n"
    "                    fontSize = 15.sp,\n"
    "                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,\n"
    "                    color = if (on) MaterialTheme.colorScheme.primary else contentColor,\n"
    "                    modifier = Modifier.weight(1f),\n"
    "                )\n"
    "                if (on) {\n"
    "                    Icon(\n"
    "                        Icons.Rounded.Check,\n"
    "                        contentDescription = null,\n"
    "                        tint = MaterialTheme.colorScheme.primary,\n"
    "                        modifier = Modifier.size(20.dp),\n"
    "                    )\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "        Row(\n"
    "            Modifier\n"
    "                .padding(24.dp, 16.dp, 24.dp, 24.dp)\n"
    "                .fillMaxWidth(),\n"
    "            verticalAlignment = Alignment.CenterVertically,\n"
    "        ) {\n"
    "            LiquidGlassButton(\n"
    "                text = \"取消\",\n"
    "                accent = false,\n"
    "                modifier = Modifier.weight(1f),\n"
    "                contentColor = contentColor,\n"
    "                onClick = close,\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}\n"
)
s = sub(s, old_start, new_start, "ProfilePage.startTabDialog")
save(p, s)

# ==========================================================================
# B) AboutPage.kt —— 发现新版本
# ==========================================================================
p = os.path.join(PAGES, "AboutPage.kt")
s = load(p)
s = sub(s, "import androidx.compose.material3.MaterialTheme\n",
        "import androidx.compose.material3.MaterialTheme\n" + GLASS_IMPORTS,
        "AboutPage.imports")
s = sub(s, "import androidx.compose.ui.window.Dialog\n", "", "AboutPage.dropDialogImport")

s = sub(
    s,
    "    Box(\n"
    "        Modifier\n"
    "            .fillMaxSize()\n"
    "            .zIndex(2f)\n"
    "            .graphicsLayer { translationX = (1f - progress.value) * size.width }\n"
    "            .background(MaterialTheme.colorScheme.background)\n"
    "            .tapGuard(),\n"
    "    ) {\n"
    "        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {",
    "    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）\n"
    "    val backdrop = rememberLayerBackdrop()\n"
    "    Box(\n"
    "        Modifier\n"
    "            .fillMaxSize()\n"
    "            .zIndex(2f)\n"
    "            .graphicsLayer { translationX = (1f - progress.value) * size.width }\n"
    "            .background(MaterialTheme.colorScheme.background)\n"
    "            .tapGuard(),\n"
    "    ) {\n"
    "        Column(\n"
    "            Modifier\n"
    "                .fillMaxSize()\n"
    "                .verticalScroll(rememberScrollState())\n"
    "                .layerBackdrop(backdrop),\n"
    "        ) {",
    "AboutPage.column",
)

s = sub(
    s,
    "            UpdateDialog(\n"
    "                info = info,",
    "            UpdateDialog(\n"
    "                backdrop = backdrop,\n"
    "                info = info,",
    "AboutPage.call",
)

old_update = (
    "/**\n"
    " * 1.177/1.179: 检查更新弹窗（关于页手动检查 + 打开软件自动检查共用）（iOS 风格：大圆角卡片、标题/正文居左、取消浅灰 + 确认蓝色大圆角按钮）。\n"
    " */\n"
    "@Composable\n"
    "fun UpdateDialog(\n"
    "    info: HupuUpdateInfo,\n"
    "    onDismiss: () -> Unit,\n"
    "    onDownload: () -> Unit,\n"
    ") {\n"
    "    Dialog(onDismissRequest = onDismiss) {\n"
    "        Column(\n"
    "            Modifier\n"
    "                .fillMaxWidth()\n"
    "                .clip(RoundedCornerShape(22.dp))\n"
    "                .background(MaterialTheme.colorScheme.surface)\n"
    "                .padding(20.dp),\n"
    "        ) {\n"
    "            Text(\n"
    "                \"发现新版本 v\" + info.versionName,\n"
    "                fontSize = 17.sp,\n"
    "                fontWeight = FontWeight.Bold,\n"
    "                color = MaterialTheme.colorScheme.onSurface,\n"
    "            )\n"
    "            Spacer(Modifier.height(6.dp))\n"
    "            Text(\n"
    "                \"当前版本 v\" + BuildConfig.VERSION_NAME,\n"
    "                fontSize = 13.sp,\n"
    "                color = MaterialTheme.colorScheme.onSurfaceVariant,\n"
    "            )\n"
    "            if (info.changelog.isNotBlank()) {\n"
    "                Spacer(Modifier.height(12.dp))\n"
    "                Text(\n"
    "                    info.changelog,\n"
    "                    fontSize = 13.sp,\n"
    "                    lineHeight = 20.sp,\n"
    "                    color = MaterialTheme.colorScheme.onSurface,\n"
    "                )\n"
    "            }\n"
    "            Spacer(Modifier.height(20.dp))\n"
    "            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {\n"
    "                DialogAction(\"忽略此版本\", primary = false, modifier = Modifier.weight(1f), onClick = onDismiss)\n"
    "                DialogAction(\"去下载\", primary = true, modifier = Modifier.weight(1f), onClick = onDownload)\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}\n"
    "\n"
    "@Composable\n"
    "private fun DialogAction(\n"
    "    text: String,\n"
    "    primary: Boolean,\n"
    "    modifier: Modifier = Modifier,\n"
    "    onClick: () -> Unit,\n"
    ") {\n"
    "    Box(\n"
    "        modifier\n"
    "            .height(46.dp)\n"
    "            .clip(RoundedCornerShape(14.dp))\n"
    "            .background(\n"
    "                if (primary) MaterialTheme.colorScheme.primary\n"
    "                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)\n"
    "            )\n"
    "            .clickable(onClick = onClick),\n"
    "        contentAlignment = Alignment.Center,\n"
    "    ) {\n"
    "        Text(\n"
    "            text,\n"
    "            fontSize = 15.sp,\n"
    "            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal,\n"
    "            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,\n"
    "        )\n"
    "    }\n"
    "}\n"
)
new_update = (
    "/**\n"
    " * 1.177/1.179: 检查更新弹窗（关于页手动检查 + 打开软件自动检查共用）。\n"
    " * 1.191: 换成 Liquid Glass 毛玻璃卡片（与底部 Tab 栏同源）+ Q 弹出入场。\n"
    " * 两个按钮都先播退场动画、动画播完才回调宿主（\"去下载\" 走 onDownload）。\n"
    " */\n"
    "@Composable\n"
    "fun UpdateDialog(\n"
    "    backdrop: Backdrop,\n"
    "    info: HupuUpdateInfo,\n"
    "    onDismiss: () -> Unit,\n"
    "    onDownload: () -> Unit,\n"
    ") {\n"
    "    var download by remember { mutableStateOf(false) }\n"
    "    LiquidGlassCard(\n"
    "        backdrop = backdrop,\n"
    "        onDismiss = { if (download) onDownload() else onDismiss() },\n"
    "    ) { close ->\n"
    "        val contentColor = MaterialTheme.colorScheme.onSurface\n"
    "        Text(\n"
    "            \"发现新版本 v\" + info.versionName,\n"
    "            Modifier.padding(24.dp, 24.dp, 24.dp, 6.dp),\n"
    "            fontSize = 20.sp,\n"
    "            fontWeight = FontWeight.Medium,\n"
    "            color = contentColor,\n"
    "        )\n"
    "        Text(\n"
    "            \"当前版本 v\" + BuildConfig.VERSION_NAME,\n"
    "            Modifier.padding(24.dp, 0.dp, 24.dp, 0.dp),\n"
    "            fontSize = 13.sp,\n"
    "            color = MaterialTheme.colorScheme.onSurfaceVariant,\n"
    "        )\n"
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
    "        }\n"
    "        Row(\n"
    "            Modifier\n"
    "                .padding(24.dp, 20.dp, 24.dp, 24.dp)\n"
    "                .fillMaxWidth(),\n"
    "            horizontalArrangement = Arrangement.spacedBy(16.dp),\n"
    "            verticalAlignment = Alignment.CenterVertically,\n"
    "        ) {\n"
    "            LiquidGlassButton(\n"
    "                text = \"忽略此版本\",\n"
    "                accent = false,\n"
    "                modifier = Modifier.weight(1f),\n"
    "                contentColor = contentColor,\n"
    "                onClick = { download = false; close() },\n"
    "            )\n"
    "            LiquidGlassButton(\n"
    "                text = \"去下载\",\n"
    "                accent = true,\n"
    "                modifier = Modifier.weight(1f),\n"
    "                onClick = { download = true; close() },\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}\n"
)
s = sub(s, old_update, new_update, "AboutPage.updateDialog")
save(p, s)

# ==========================================================================
# C) MainActivity.kt —— 冷启动自动更新的同一个弹窗
#    必须移到根 Box 内、内容层 Surface 之后（毛玻璃靠根 backdrop 采样）
# ==========================================================================
p = os.path.join(ROOT, "MainActivity.kt")
s = load(p)

old_block = (
    "    // 发现新版本弹窗（复用关于页 iOS 风格对话框）\n"
    "    autoUpdateInfo?.let { info ->\n"
    "        UpdateDialog(\n"
    "            info = info,\n"
    "            onDismiss = {\n"
    "                // 1.181: 「忽略此版本」→ 记住版本号，自动弹窗不再打扰\n"
    "                HupuPrefs.ignoreUpdateVersion(info.versionCode)\n"
    "                autoUpdateInfo = null\n"
    "            },\n"
    "            onDownload = {\n"
    "                runCatching {\n"
    "                    autoUpdateCtx.startActivity(\n"
    "                        Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl.ifEmpty { info.releaseUrl }))\n"
    "                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)\n"
    "                    )\n"
    "                }\n"
    "                autoUpdateInfo = null\n"
    "            },\n"
    "        )\n"
    "    }\n"
)
assert s.count(old_block) == 1
s = s.replace(old_block, "")

old_tail = (
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "}"
)
new_tail = (
    "                }\n"
    "            }\n"
    "        }\n"
    "        // 发现新版本弹窗（与关于页共用；1.191 起为 Liquid Glass 毛玻璃 + Q 弹出入场）。\n"
    "        // 必须画在内容层 Surface 之后（根 backdrop 记录的就是 Surface 像素）。\n"
    "        autoUpdateInfo?.let { info ->\n"
    "            UpdateDialog(\n"
    "                backdrop = backdrop,\n"
    "                info = info,\n"
    "                onDismiss = {\n"
    "                    // 1.181: 「忽略此版本」→ 记住版本号，自动弹窗不再打扰\n"
    "                    HupuPrefs.ignoreUpdateVersion(info.versionCode)\n"
    "                    autoUpdateInfo = null\n"
    "                },\n"
    "                onDownload = {\n"
    "                    runCatching {\n"
    "                        autoUpdateCtx.startActivity(\n"
    "                            Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl.ifEmpty { info.releaseUrl }))\n"
    "                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)\n"
    "                        )\n"
    "                    }\n"
    "                    autoUpdateInfo = null\n"
    "                },\n"
    "            )\n"
    "        }\n"
    "    }\n"
    "}"
)
assert s.count(old_tail) == 1, s.count(old_tail)
s = s.replace(old_tail, new_tail)
save(p, s)

print("BATCH 2 UPGRADED OK")
