package com.java.myapplication.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

import androidx.compose.material3.MaterialTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.java.myapplication.ui.glass.LiquidGlassButton
import com.java.myapplication.ui.glass.LiquidGlassCard
import com.java.myapplication.ui.glass.LiquidGlassDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuMsgBadge
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.ui.components.PageHeader
import com.java.myapplication.ui.components.HupuIcons

/** 我的：设置入口（主页频道自定义等）+ 关于 */
@Composable
fun ProfilePage(modifier: Modifier = Modifier) {
    var showPicker by remember { mutableStateOf(false) }
    // 浏览记录挂载用 epoch 键：每次点击入口都保证全新实例（key 变化即重建）——
    // 退场动画中/异常残留的旧实例被直接替换销毁，点击永远不会被吞
    var historyEpoch by remember { mutableIntStateOf(0) }
    // 评分频道自定义 / 信息流设置：epoch 键控挂载（同浏览记录，防退场动画吞点击）
    var scorePickerEpoch by remember { mutableIntStateOf(0) }
    var filterEpoch by remember { mutableIntStateOf(0) }
    var textSizeEpoch by remember { mutableIntStateOf(0) }
    var refreshEpoch by remember { mutableIntStateOf(0) }
    // 1.130 主题模式设置页挂载 + 当前模式标签（订阅版本号，切主题后本行即时刷新）
    var themeEpoch by remember { mutableIntStateOf(0) }
    // 1.134 关于页挂载（epoch 键控，同其他二级页）
    var aboutEpoch by remember { mutableIntStateOf(0) }
    // 1.182 默认启动页：标准设置行 + iOS 风格选择弹窗（改动下次冷启动生效）
    var startTabAsk by remember { mutableStateOf(false) }
    var startTabIdx by remember { mutableIntStateOf(HupuPrefs.loadStartTab()) }
    val themeMode = remember(HupuPrefs.themeModeVersion) { HupuPrefs.loadThemeMode() }
    val themeModeLabel = when (themeMode) {
        HupuPrefs.THEME_LIGHT -> "浅色"
        HupuPrefs.THEME_DARK -> "深色"
        else -> "跟随系统"
    }
    // 1.179: 色彩主题标签（订阅版本号，切换后本行即时刷新）
    val themeAccent = remember(HupuPrefs.colorThemeVersion) { HupuPrefs.loadColorTheme() }
    val themeAccentLabel = if (themeAccent == com.java.myapplication.ui.theme.ACCENT_DYNAMIC) {
        "动态取色"
    } else {
        com.java.myapplication.ui.theme.accentPaletteOf(themeAccent).label
    }
    // 登录：登录页 epoch 键控挂载 + 退出确认展开态
    var loginEpoch by remember { mutableIntStateOf(0) }
    var logoutAsk by remember { mutableStateOf(false) }
    // 用户主页（点已登录账号卡片打开自己的主页）；
    // epoch 锢控挂载，euid 在点击时捕获（prof 是卡片局部作用域变量，挂载点看不见）
    // 用户主页栈：层层叠加，返回逐层弹出
    val userPageStack = remember { mutableStateListOf<String>() }
    // 消息中心：epoch 键控挂载（同其他二级页）
    var msgEpoch by remember { mutableIntStateOf(0) }
    // 1.107: 发帖页（epoch 键控挂载）+ 顶部提示（未登录 / 发布成功）
    var postEpoch by remember { mutableIntStateOf(0) }
    // 1.192: 二级页互斥——同一时刻只打开一个「我的」二级页（连点多个条目只保留最先的），
    // 避免多页叠加：顶层关闭后下层还在、Tab 栏却提前冒出
    fun profilePageOpen(): Boolean =
        showPicker || loginEpoch > 0 || postEpoch > 0 || msgEpoch > 0 ||
            historyEpoch > 0 || scorePickerEpoch > 0 || filterEpoch > 0 ||
            textSizeEpoch > 0 || refreshEpoch > 0 || themeEpoch > 0 ||
            aboutEpoch > 0 || userPageStack.isNotEmpty()
    var postToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(postToast) {
        if (postToast != null) {
            kotlinx.coroutines.delay(2200)
            postToast = null
        }
    }
    // 1.106: 铃铛未读角标——进入「我的」页拉一次 + 前台每 2 分钟轮询
    LaunchedEffect(Unit) {
        HupuMsgBadge.refresh()
        while (true) {
            kotlinx.coroutines.delay(120_000)
            HupuMsgBadge.refresh()
        }
    }
    // 1.191: 记录层——供 Liquid Glass 弹窗采样背后像素（挂在内容层上，弹窗在其后）
    // 1.191: 先铺一层不透明页面底色再画内容——与 ThreadDetailPage 的
    // actionBackdrop 同一套做法。记录层若透明，卡片会显得「非常透明」
    val dialogBg = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(dialogBg)
        drawContent()
    }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            PageHeader(title = "我的", trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 1.107: 发帖（未登录时提示，不打开编辑页）
                    IconButton(onClick = {
                        if (HupuAccount.profile == null) postToast = "请先在「我的」页登录"
                        else if (!profilePageOpen()) postEpoch++
                    }) {
                        Icon(HupuIcons.Edit, contentDescription = "发帖", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { if (!profilePageOpen()) msgEpoch++ }) {
                        androidx.compose.material3.BadgedBox(
                            badge = {
                                if (HupuMsgBadge.total > 0) {
                                    androidx.compose.material3.Badge {
                                        Text(
                                            if (HupuMsgBadge.total > 99) "99+" else HupuMsgBadge.total.toString(),
                                            fontSize = 9.sp,
                                        )
                                    }
                                }
                            },
                        ) {
                            Icon(HupuIcons.Bell, contentDescription = "消息", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            })

            // 1.63 修复：内容超过一屏后无法滚动——原为静态 Column，无任何滚动能力。
            // verticalScroll 全页单列表内容；bottom=140dp 给悬浮 Tab 栏留避让
            // （与列表页 contentPadding 一致，Tab 栏悬空不遮末行）
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 140.dp)
            ) {
                // 账号卡片：未登录 → 点击登录；已登录 → 头像昵称（点击展开退出）
                val sessionVer = remember(HupuAccount.sessionVersion) { HupuAccount.sessionVersion }
                val prof = remember(sessionVer) { HupuAccount.profile }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            if (profilePageOpen()) return@clickable
                            if (prof == null) {
                                loginEpoch++
                            } else {
                                userPageStack.add(prof.euid)
                            }
                        }
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (prof != null) {
                            AsyncImage(
                                model = prof.avatar,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(prof.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("已登录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("未登录", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                Text("登录后可点亮、评论", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 设置组
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    SettingRow(
                        icon = HupuIcons.Tune,
                        title = "首页频道自定义",
                        subtitle = "自定义首页顶部横滑条（最多 20 个）",
                        onClick = { if (!profilePageOpen()) showPicker = true },
                    )
                    SettingRow(
                        icon = HupuIcons.StarRate,
                        title = "评分频道自定义",
                        subtitle = "自定义评分页横滑条项目（最多 20 个）",
                        onClick = { if (!profilePageOpen()) scorePickerEpoch++ },
                    )
                    SettingRow(
                        icon = HupuIcons.History,
                        title = "浏览记录",
                        subtitle = "最近浏览的帖子（最多 300 条）",
                        onClick = { if (!profilePageOpen()) historyEpoch++ },
                    )
                    SettingRow(
                        icon = HupuIcons.FilterAlt,
                        title = "信息流设置",
                        subtitle = "标题/分区/评论关键词过滤",
                        onClick = { if (!profilePageOpen()) filterEpoch++ },
                    )
                    SettingRow(
                        icon = HupuIcons.FormatSize,
                        title = "\u9605\u8bfb\u5b57\u53f7",
                        subtitle = "\u5e16\u5b50\u6b63\u6587\u6587\u5b57\u5927\u5c0f\uff08\u5373\u65f6\u9884\u89c8\uff09",
                        onClick = { if (!profilePageOpen()) textSizeEpoch++ },
                    )
                    SettingRow(
                        icon = HupuIcons.Speed,
                        title = "屏幕刷新率",
                        subtitle = "档位来自设备真实支持的显示模式",
                        onClick = { if (!profilePageOpen()) refreshEpoch++ },
                    )
                    SettingRow(
                        icon = HupuIcons.DarkMode,
                        title = "界面设置",
                        subtitle = themeModeLabel + " · " + themeAccentLabel,
                        onClick = { if (!profilePageOpen()) themeEpoch++ },
                    )
                    // 1.182 默认启动页：与相邻行同构的设置行（点击弹选择框，改动下次冷启动生效）
                    SettingRow(
                        icon = Icons.Rounded.Home,
                        title = "默认启动页",
                        subtitle = START_TAB_LABELS.getOrNull(startTabIdx) ?: "首页",
                        onClick = { startTabAsk = true },
                    )
                    // 1.134 关于改为二级页入口（替代原内联关于区块）
                    SettingRow(
                        icon = HupuIcons.Info,
                        title = "关于",
                        subtitle = aboutVersionLabel() + " · 隐私协议 / 开源许可 / 免责声明",
                        onClick = { if (!profilePageOpen()) aboutEpoch++ },
                    )
                    if (prof != null) {
                        SettingRow(
                            icon = HupuIcons.Logout,
                            title = "\u9000\u51fa\u767b\u5f55",
                            subtitle = "\u6e05\u9664\u767b\u5f55\u51ed\u636e\u5e76\u8fd4\u56de\u672a\u767b\u5f55\u72b6\u6001",
                            onClick = { logoutAsk = true },
                        )
                    }
                }
        }
        }

        // 1.182 默认启动页选择弹窗
        if (startTabAsk) {
            StartTabDialog(
                backdrop = backdrop,
                current = startTabIdx,
                onPick = { i ->
                    startTabIdx = i
                    HupuPrefs.saveStartTab(i)
                    startTabAsk = false
                },
                onDismiss = { startTabAsk = false },
            )
        }
        // 1.192: 退出登录改为毛玻璃弹窗确认（不再行内展开）
        if (logoutAsk) {
            LiquidGlassDialog(
                backdrop = backdrop,
                title = "退出登录",
                message = "退出后将清除本机登录凭据，返回未登录状态。",
                confirmText = "退出",
                dismissText = "取消",
                onConfirm = {
                    HupuAccount.logout()
                    com.java.myapplication.data.HupuFollowStore.reset()
                },
                onDismiss = { logoutAsk = false },
            )
        }

        // 选版块页：从右盖入、返回滑出
        if (showPicker) {
            TopicPickerPage(onClose = { showPicker = false })
        }

        // 浏览记录页：从右盖入、返回滑出（epoch 键控——退场中被再点时立即重建重盖）
        if (historyEpoch > 0) {
            androidx.compose.runtime.key(historyEpoch) {
                HistoryPage(onClose = { historyEpoch = 0 })
            }
        }
        // 评分频道自定义页：从右盖入、返回滑出
        if (scorePickerEpoch > 0) {
            androidx.compose.runtime.key(scorePickerEpoch) {
                ScorePickerPage(onClose = { scorePickerEpoch = 0 })
            }
        }
        // 信息流设置页：从右盖入、返回滑出
        if (filterEpoch > 0) {
            androidx.compose.runtime.key(filterEpoch) {
                FilterSettingsPage(onClose = { filterEpoch = 0 })
            }
        }
        // Reading font size page: cover-in from right, slide-out on back
        // \u7528\u6237\u4e3b\u9875\u6808\uff1a\u5df2\u767b\u5f55\u8d26\u53f7\u5361\u7247\u70b9\u51fb\u6253\u5f00\uff0c\u5c42\u5c42\u53e0\u52a0\u53ef\u9010\u5c42\u8fd4\u56de
        userPageStack.forEachIndexed { si, subEuid ->
            androidx.compose.runtime.key(si) {
                UserProfilePage(euid = subEuid, onClose = { userPageStack.removeAt(si) })
            }
        }
        if (textSizeEpoch > 0) {
            androidx.compose.runtime.key(textSizeEpoch) {
                TextSizeSettingsPage(onClose = { textSizeEpoch = 0 })
            }
        }
                // 消息中心：从右盖入、返回滑出（epoch 键控，同其他二级页）
        if (msgEpoch > 0) {
            androidx.compose.runtime.key(msgEpoch) {
                MessageCenterPage(onClose = { msgEpoch = 0 })
            }
        }
        // 屏幕刷新率设置页：从右盖入、返回滑出（同其他二级页）
        if (refreshEpoch > 0) {
            androidx.compose.runtime.key(refreshEpoch) {
                RefreshRateSettingsPage(onClose = { refreshEpoch = 0 })
            }
        }
        // 1.130 主题模式设置页：从右盖入、返回滑出（同其他二级页）
        if (themeEpoch > 0) {
            androidx.compose.runtime.key(themeEpoch) {
                ThemeSettingsPage(onClose = { themeEpoch = 0 })
            }
        }
        // 1.134 关于页：从右盖入、返回滑出（同其他二级页）
        if (aboutEpoch > 0) {
            androidx.compose.runtime.key(aboutEpoch) {
                AboutPage(onClose = { aboutEpoch = 0 })
            }
        }
        // 登录页：从右盖入（epoch 键控，同其他二级页）
        if (loginEpoch > 0) {
            // 键只用 loginEpoch：会话版本不能入键（登录成功摄取会
            // ++，把登录页当场拆重建再次秒命中，形成闪烁循环）。
            // 退出登录的凭据清理由 HupuAccount.logout 负责。
            androidx.compose.runtime.key(loginEpoch) {
                LoginPage(onClose = { loginEpoch = 0 })
            }
        }
        // 1.107: 发帖页：从右盖入、返回滑出（epoch 键控，同其他二级页）
        if (postEpoch > 0) {
            androidx.compose.runtime.key(postEpoch) {
                NewPostPage(
                    onClose = { postEpoch = 0 },
                    onPosted = { _ ->
                        postEpoch = 0
                        postToast = "发布成功"
                    },
                )
            }
        }
        // 顶部提示（未登录 / 发布成功）
        postToast?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp)) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color(0xE6303030))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

/** 设置行：图标 + 标题/副标题 + 箭头 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
/** 1.182: 默认启动页可选项（与底部 Tab 一一对应） */
private val START_TAB_LABELS = listOf("首页", "专区", "评分", "我的")

/**
 * 1.182: 默认启动页选择弹窗
 * 1.191: 换成 Liquid Glass 毛玻璃卡片（与底部 Tab 栏同源）+ Q 弹出入场。
 */
@Composable
private fun StartTabDialog(
    backdrop: Backdrop,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    LiquidGlassCard(backdrop = backdrop, onDismiss = onDismiss) { close ->
        val contentColor = MaterialTheme.colorScheme.onSurface
        Text(
            "默认启动页",
            Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
        START_TAB_LABELS.forEachIndexed { i, name ->
            val on = i == current
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(i); close() }
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    fontSize = 15.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                    color = if (on) MaterialTheme.colorScheme.primary else contentColor,
                    modifier = Modifier.weight(1f),
                )
                if (on) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .padding(24.dp, 16.dp, 24.dp, 24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassButton(
                text = "取消",
                accent = false,
                modifier = Modifier.weight(1f),
                contentColor = contentColor,
                onClick = close,
            )
        }
    }
}

