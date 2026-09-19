package com.java.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuCache
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuUiSignals
import com.java.myapplication.data.HupuUpdate
import com.java.myapplication.data.HupuUpdateInfo
import com.java.myapplication.data.HupuUpdateResult
import com.java.myapplication.ui.glass.GlassBottomTabs
import com.java.myapplication.ui.glass.GlassTab
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.pages.HomePage
import com.java.myapplication.ui.pages.DisclaimerGate
import com.java.myapplication.ui.pages.ProfilePage
import com.java.myapplication.ui.pages.ScorePage
import com.java.myapplication.ui.pages.UpdateDialog
import com.java.myapplication.ui.pages.ZonePage
import com.java.myapplication.ui.theme.LedgerTheme
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 旋转屏幕等触发 Activity 重建时：全部 Compose 状态清零（二级页消失、回到主页），
        // 但 SecondaryPage 计数是进程级单例不随重建清零——必须归零，否则 Tab 栏永久隐藏
        SecondaryPage.reset()
        // 初始化虎扑数据缓存（原始 HTML 磁盘缓存，布局调试期减少真实请求）
        HupuCache.init(cacheDir.resolve("hupu_cache"))
        // 初始化用户偏好（主页频道自定义等）
        HupuPrefs.init(this)
        // 初始化登录会话（cookie 恢复 / 写端点鉴权）
        HupuAccount.init(this)
        // 1.126 本地关注集合（服务端无关注状态查询，本地维护 + 后台校准）
        com.java.myapplication.data.HupuFollowStore.init(this)
        // 应用用户选择的屏幕刷新率（-1=自动则不动，窗口前台期间强制生效）
        com.java.myapplication.data.HupuRefresh.apply(this, HupuPrefs.loadRefreshMode())
        setContent {
            // 1.130: 主题模式（跟随系统/浅色/深色）——即时生效；同时同步状态栏/导航栏图标明暗
            val dark = isAppDarkTheme()
            LaunchedEffect(dark) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
            LedgerTheme(darkTheme = dark) {
                // 1.154: 首次启动必须同意「免责声明」才能使用。不同意（点「不同意」按钮、
                // 点弹窗以外任意区域、按系统返回键）→ 退出应用；下次启动仍会拦截。
                if (HupuPrefs.disclaimerAccepted) {
                    PocketLedgerApp()
                } else {
                    DisclaimerGate(
                        onAgree = { HupuPrefs.acceptDisclaimer() },
                        onExit = {
                            finishAffinity()
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                    )
                }
            }
        }
    }

    /**
     * 1.169: 页面销毁时取消在途的角标拉取（原 HupuMsgBadge 用 GlobalScope，
     * 不受生命周期约束；现在有明确的作用域 + 取消钩子）。
     */
    override fun onDestroy() {
        com.java.myapplication.data.HupuMsgBadge.cancelAll()
        super.onDestroy()
    }
}

/**
 * 1.183: 悬浮 Tab 栏「栏底到屏幕底」的留白，按导航模式分档：
 * - 手势模式：底部没有可见 UI（只有一条极细手势条），手势区只是系统"预留的拦截区域"，
 *   部分 ROM 还会把它虚报到 ≈47dp（真机实测 safe=47.06 / gesture=47.06 / nav=22.18）。
 *   这类机型只需退到细手势条之上 → 用固定小留白，观感更贴底且各机型一致。
 * - 三键导航：底部是可见的导航按钮，必须让开 → 导航条之上再留一点间距。
 */
private val TAB_GAP_GESTURE = 20.dp
private val TAB_GAP_BUTTON_EXTRA = 12.dp

@Composable
fun PocketLedgerApp() {
    val backgroundColor = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }
    // 1.182: 默认启动页（设置页可选；冷启动读取）
    var selectedTab by remember { mutableIntStateOf(HupuPrefs.loadStartTab()) }
    // 1.181: 每次冷启动静默检查更新（10min 去重；有新版才弹窗，其余完全静默）
    val autoUpdateCtx = LocalContext.current
    var autoUpdateInfo by remember { mutableStateOf<HupuUpdateInfo?>(null) }
    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        if (!HupuPrefs.shouldAutoCheckUpdate(now)) return@LaunchedEffect
        // 先记时间：短窗口内不重复请求（避免网络异常时反复打扰）
        HupuPrefs.markUpdateChecked(now)
        when (val r = HupuUpdate.check(BuildConfig.VERSION_CODE)) {
            // 1.181: 被「忽略此版本」的不再打扰；出现更高版本 / 强制更新时才弹
            is HupuUpdateResult.Available ->
                if (r.info.forceUpdate || r.info.versionCode > HupuPrefs.ignoredUpdateVersion()) {
                    autoUpdateInfo = r.info
                }
            else -> Unit
        }
    }
    // 发现新版本弹窗（复用关于页 iOS 风格对话框）
    autoUpdateInfo?.let { info ->
        UpdateDialog(
            info = info,
            onDismiss = {
                // 1.181: 「忽略此版本」→ 记住版本号，自动弹窗不再打扰
                HupuPrefs.ignoreUpdateVersion(info.versionCode)
                autoUpdateInfo = null
            },
            onDownload = {
                runCatching {
                    autoUpdateCtx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl.ifEmpty { info.releaseUrl }))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                autoUpdateInfo = null
            },
        )
    }
    // 1.132: 切入评分页信号——页面常驻组合，评分页冷启动首屏加载失败后不会自己重试；
    // 切入时通知评分页做一次补偿重试，避免看到残留的「加载失败」
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) HupuUiSignals.enterScore()
    }
    // 1.78: IME 底部 insets(组合期读取, 键盘弹出/收起自动重组; 回复框打开时把
    // Tab 栏出屏位移量补上键盘高度, 保证彻底推出屏不卡在键盘上缘)
    val insetsDensity = androidx.compose.ui.platform.LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(insetsDensity)
    // 1.183: 底部留白不再用 safeContent（= max(导航条, 手势区, 可点区, 刘海, IME)）——
    // 手势区在部分 ROM 上虚高到 ≈47dp，会把悬浮栏顶得过高。改为按导航模式分档（见上方常量）。
    val navBottomPx = WindowInsets.navigationBars.getBottom(insetsDensity)
    val cutBottomPx = WindowInsets.displayCutout.getBottom(insetsDensity)
    val gestureBottomPx = WindowInsets.systemGestures.getBottom(insetsDensity)
    // 栏底到屏幕底的实际留白（下面 padding 用它，隐藏位移也用它，两者同源）
    val bottomPadPx = if (gestureBottomPx > 0) {
        // 手势模式：贴到细手势条之上，固定留白 → 各机型位置一致
        maxOf(with(insetsDensity) { TAB_GAP_GESTURE.roundToPx() }, cutBottomPx).toFloat()
    } else {
        // 三键导航：让开可见的导航按钮
        (maxOf(navBottomPx, cutBottomPx) + with(insetsDensity) { TAB_GAP_BUTTON_EXTRA.roundToPx() }).toFloat()
    }

    Box(Modifier.fillMaxSize()) {
        // 内容层：四页常驻组合（状态保留、切 Tab 不重建），盖入式转场
        Surface(
            Modifier.fillMaxSize().layerBackdrop(backdrop),
            color = MaterialTheme.colorScheme.background
        ) {
            val pages = listOf<@Composable () -> Unit>(
                { HomePage() },
                { ZonePage() },
                { ScorePage() },
                { ProfilePage() },
            )
            pages.forEachIndexed { index, page ->
                // 与原 AnimatedContent 同款转场：±1/6 屏宽视差滑动 + 淡入淡出（方向随 Tab 索引自动）
                // 页面常驻不销毁，动画只是 graphicsLayer 平移/透明度变化，零重组零重载
                val targetOffset = when {
                    index == selectedTab -> 0f
                    index > selectedTab -> 0.167f
                    else -> -0.167f
                }
                val targetAlpha = if (index == selectedTab) 1f else 0f
                val offset by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = tween(280),
                    label = "pageX$index",
                )
                val alpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(280),
                    label = "pageA$index",
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (index == selectedTab) 1f else 0f)
                        .graphicsLayer {
                            translationX = offset * size.width
                            this.alpha = alpha
                        }
                        // 穿透守卫：alpha=0 的后台页不可再被命中（否则点击落穿到
                        // 隐形页的卡片上，触发 SecondaryPage.enter 后无路可退）
                        .tapGuard()
                ) {
                    page()
                }
            }
        }

        // 玻璃 Tab 栏层（整体抬高 12dp，避免贴底过近）：二级页打开时弹性滑出隐藏，返回时 Q 弹回归
        val tabHidden = SecondaryPage.count > 0
        val tabProgress by animateFloatAsState(
            targetValue = if (tabHidden) 1f else 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "tabBarSpring",
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                // 只用上/水平安全区做避让；底部改为下面的精确留白（避免手势区虚高把栏顶上去）
                .windowInsetsPadding(
                    WindowInsets.safeContent.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                )
                .padding(bottom = with(insetsDensity) { bottomPadPx.toDp() })
                .graphicsLayer {
                    // 位移 = 栏高 + 实际底部留白 + 键盘高 + 30dp：任何机型都能彻底送出屏幕
                    translationY = (size.height + bottomPadPx + imeBottomPx + 30.dp.toPx()) * tabProgress
                }
        ) {
            GlassBottomTabs(
                selectedTabIndex = { selectedTab },
                onTabSelected = { selectedTab = it },
                onTabReselected = { index ->
                    // 轻点已选中 Tab → 刷新信号（首页/评分；专区/我的无列表流不参与）
                    when (index) {
                        0 -> HupuUiSignals.tapHome()
                        2 -> HupuUiSignals.tapScore()
                    }
                },
                backdrop = backdrop,
                tabsCount = 4,
                modifier = Modifier.padding(horizontal = 20.dp).widthIn(max = 480.dp)
            ) {
                val tabs = listOf(
                    Triple("首页", Icons.Rounded.Home, "首页"),
                    Triple("专区", Icons.AutoMirrored.Rounded.List, "专区"),
                    Triple("评分", Icons.Rounded.Star, "评分"),
                    Triple("我的", Icons.Rounded.Person, "我的")
                )
                tabs.forEachIndexed { index, (label, icon, desc) ->
                    GlassTab(onClick = { selectedTab = index }) {
                        // 1.131: 图标下移 2dp / 文字上移 2dp（真机验收微调）；
                        // 文字显式取 onSurface——Tab 栏不在 Surface 内，默认 LocalContentColor
                        // 会落回 Color.Black，深色模式下未选中文字仍是黑色
                        Icon(
                            icon,
                            contentDescription = desc,
                            modifier = Modifier.size(22.dp).offset(y = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.offset(y = (-2).dp),
                        )
                    }
                }
            }
        }
    }
}