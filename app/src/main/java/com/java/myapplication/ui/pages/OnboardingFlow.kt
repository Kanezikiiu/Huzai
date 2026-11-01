package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import com.java.myapplication.data.HupuCategory
import com.java.myapplication.ui.components.ChipAutoCenterRow
import com.java.myapplication.ui.components.ChipSlot
import com.java.myapplication.ui.components.rememberChipAutoCenter
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.LiquidIconButton
import com.java.myapplication.ui.glass.FrostedHeaderLayout
import com.java.myapplication.data.HupuMatchApi
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuTopicInfo
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.LiquidSlider
import com.java.myapplication.ui.glass.GlassSwitch
import com.java.myapplication.ui.components.LiquidBackButton
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.theme.ACCENT_DYNAMIC
import com.java.myapplication.ui.theme.AccentPalettes
import com.java.myapplication.ui.theme.accentPaletteOf
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.java.myapplication.ui.theme.isDynamicColorAvailable
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlin.math.roundToInt

/**
 * 1.284 首次使用引导（共 4 步）：外观 → 阅读字号 → 首页频道 → 评分频道。
 *
 * 设计语汇（真机反馈后重做）：
 *  · **iOS 风格**：分组列表（圆角卡片 + 行间细分割线 + 左侧文字、右侧选择标记），
 *    顶部是纯文字导航按钮（「上一步」/「跳过」），**不用 Material 的填充色按钮**——
 *    主按钮走项目统一的玻璃中性底 + 描边 + 主题色文字（与设置页、详情页按钮同源）。
 *  · **跳过分级**：第 1 步「跳过」= 跳过整个流程；其余步骤「跳过」= 只跳过当前这一步。
 *  · 第 2 步起顶部左侧有「上一步」。
 *  · 每一步的修改**即时持久化**（与设置页同一套 pref），中途退出不丢。
 *  · 新用户才进入本流程：老用户由 HupuPrefs 的静默迁移标记为已完成。
 */
@Composable
fun OnboardingFlow(onFinish: () -> Unit) {
    val pageCount = 5
    var step by remember { mutableStateOf(0) }
    val dark = isAppDarkTheme()
    // 1.290：收尾状态。点「完成 / 跳过全部」→ 按钮转圈 → 整页淡出 → 才切主页（避免硬切）。
    var finishing by remember { mutableStateOf(false) }
    val exitAlpha = remember { Animatable(1f) }
    LaunchedEffect(finishing) {
        if (!finishing) return@LaunchedEffect
        kotlinx.coroutines.delay(420)
        exitAlpha.animateTo(0f, tween(300))
        onFinish()
    }

    PredictiveBackHandler { events ->
        events.collect { }
        if (step > 0) step -= 1
    }

    // 1.287：整个引导包在「浮空顶栏 + 渐进模糊」骨架里——
    // 外观页拨动「顶栏渐进模糊」开关可**当场看到**顶栏模糊效果（订阅 progressiveHeaderVersion）。
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = exitAlpha.value
                val sc = 0.985f + 0.015f * exitAlpha.value
                scaleX = sc
                scaleY = sc
            },
    ) {
    FrostedHeaderLayout(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        header = {
            Column(Modifier.statusBarsPadding()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (step > 0) {
                        LiquidBackButton(onClick = { step -= 1 }, size = 40.dp, iconSize = 20.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    // 1.288：**取消顶栏的「跳过」**——它取消/前进与「下一步」效果完全一致，
                    // 属于重复入口。现在只有封面页底部那个按钮能跳过整个流程。
                }
            }
        },
    ) { topInset ->
        // 1.288：内容层必须自己铺**不透明**底色。
        // 顶栏的渐进模糊是拿「内容层」当采样源（layerBackdrop）的；
        // 之前底色画在 FrostedHeaderLayout 外层，采样层里只有文字、没有底，
        // 模糊结果被 85% 底色吃掉 → 真机看到的就是「开关没反应」。
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
        ) {
            // 1.288：**不要**在内容区上用 padding(top = topInset)——
            // 那会把内容裁剪在顶栏下方，永远不可能穿到栏下面，
            // 顶栏的模糊采样区就永远是空的（真机表现为「开关没反应」）。
            // 改为把 topInset 当作各页**滚动内容里的第一段空白**，
            // 这样上滑时条目会真的从顶栏下方穿过去。
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(260)) { -it / 4 } + fadeOut(tween(150)))
                        } else {
                            (slideInHorizontally(tween(260)) { -it / 4 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(260)) { it / 4 } + fadeOut(tween(150)))
                        }
                    },
                    label = "onboarding",
                ) { s ->
                    when (s) {
                        0 -> CoverStep(topInset, onStart = { step += 1 })
                        1 -> AppearanceStep(topInset)
                        2 -> FontStep(topInset)
                        3 -> HomeChannelStep(topInset)
                        else -> ScoreChannelStep(topInset)
                    }
                }
            }

            // ---------- 页码点 ----------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(pageCount) { i ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (i == step) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
                            ),
                    )
                }
            }

            // ---------- 底部按钮：封面页 = 跳过全部；其余 = 下一步 / 完成 ----------
            // 1.289：封面页的「跳过全部」是**次要动作**，不该比「开始设置」更抢眼。
            // 改成居中的小胶囊：半透明玻璃底 + 次要文字色 + 更小的字。
            val skipAll = step == 0
            val last = step == pageCount - 1
            // 1.290：高度固定 52dp 的外框 —— 封面的跳过胶囊(42dp)在其中居中，
            // 切换步骤时底部行高不变，页面不会“上移一下再切换”。
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 22.dp),
                contentAlignment = Alignment.Center,
            ) {
                LiquidButton(
                onClick = {
                        when {
                            skipAll -> finishing = true   // 封面：跳过整个流程
                            last -> finishing = true      // 完成
                            else -> step += 1
                        }
                    },
                fill = if (skipAll) buttonFill(dark).copy(alpha = 0.55f) else buttonFill(dark),
                border = buttonBorder(dark),
                height = if (skipAll) 42.dp else 52.dp,
                contentPadding = if (skipAll) 20.dp else 16.dp,
                modifier = if (skipAll) Modifier else Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(if (skipAll) 21.dp else 16.dp),
            ) {
                if (finishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                Text(
                    when {
                        skipAll -> "跳过全部，直接使用"
                        last -> "完成"
                        else -> "下一步"
                    },
                    fontSize = if (skipAll) 13.5.sp else 16.sp,
                    fontWeight = if (skipAll) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (skipAll) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                )
            }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    }
}

// ---------------------------------------------------------------------------
// ① 外观
// ---------------------------------------------------------------------------
@Composable
private fun AppearanceStep(topInset: Dp) {
    var mode by remember { mutableStateOf(HupuPrefs.loadThemeMode()) }
    var accentId by remember { mutableStateOf(HupuPrefs.loadColorTheme()) }
    // 1.288：两个开关状态提升到本页顶层 —— 开启渐进模糊时本页会自动上滑一点，
    // 让内容穿到顶栏下方，这样拨开关就能**当场看到**模糊效果。
    var progressive by remember { mutableStateOf(HupuPrefs.loadProgressiveHeader()) }
    var autoHide by remember { mutableStateOf(HupuPrefs.loadAutoHideBar()) }
    val pageScroll = rememberScrollState()
    // 1.289：按真机要求**取消「开启时自动上滑」**——开关一拨页面就跑一段距离很突兀。
    val options = listOf(
        Triple(HupuPrefs.THEME_SYSTEM, "跟随系统", "跟随手机的深色 / 浅色设置"),
        Triple(HupuPrefs.THEME_LIGHT, "浅色", "始终使用浅色"),
        Triple(HupuPrefs.THEME_DARK, "深色", "始终使用深色"),
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(pageScroll)
            .padding(horizontal = 22.dp),
        // 1.289：模块间距 10dp → 6dp。真机反馈：一屏放不下，最下面的「底栏」分组
        // 完全看不见，容易被误以为没有这项设置。
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Spacer(Modifier.height(topInset))
        StepHead("外观", "之后可在「我的 → 显示与阅读」里修改")
        Group {
            options.forEachIndexed { i, (m, title, desc) ->
                CheckRow(
                    title = title,
                    subtitle = desc,
                    on = mode == m,
                    trailing = "radio",
                ) {
                    mode = m
                    HupuPrefs.saveThemeMode(m)
                }
                if (i != options.lastIndex) Hairline()
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "色彩主题",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Group {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (isDynamicColorAvailable()) {
                        AccentDot(
                            label = "动态",
                            selected = accentId == ACCENT_DYNAMIC,
                            color = null,
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF4285F4), Color(0xFF34A853),
                                    Color(0xFFFBBC05), Color(0xFFEA4335),
                                    Color(0xFF4285F4),
                                ),
                            ),
                            checkTint = Color.White,
                        ) {
                            accentId = ACCENT_DYNAMIC
                            HupuPrefs.saveColorTheme(ACCENT_DYNAMIC)
                        }
                    }
                    AccentPalettes.forEach { p ->
                        AccentDot(
                            label = p.label,
                            selected = accentId == p.id,
                            color = p.swatch,
                            checkTint = if (p.swatch.luminance() > 0.6f) Color(0xFF1A1A1A) else Color.White,
                        ) {
                            accentId = p.id
                            HupuPrefs.saveColorTheme(p.id)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (accentId == ACCENT_DYNAMIC) "动态取色 · 跟随壁纸配色（Android 12+）"
                    else "当前色彩：" + accentPaletteOf(accentId).label,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "顶栏",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Group {
            CheckRow(
                title = "顶栏渐进模糊",
                subtitle = "滚动时顶栏逐渐模糊，内容从下方透出",
                on = progressive,
                trailing = "switch",
            ) {
                progressive = !progressive
                HupuPrefs.saveProgressiveHeader(progressive)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "底栏",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Group {
            CheckRow(
                title = "滚动时自动隐藏底栏",
                subtitle = "向上滑动时收起底部导航，下滑时重新出现",
                on = autoHide,
                trailing = "switch",
            ) {
                autoHide = !autoHide
                HupuPrefs.saveAutoHideBar(autoHide)
            }
        }
        // 1.288：留出可滚动的余量。
        // 顶栏的渐进模糊只有在「内容穿到顶栏下方」时才看得见；
        // 这一页原本恰好一屏多一点，往上滑不了多少 → 拨开关时看不出差别。
        // 多留一段空白后，上滑即可把条目推到顶栏下方，效果当场可辨。
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------------------
// ② 阅读字号
// ---------------------------------------------------------------------------

/**
 * 字号本地状态 + 松手才写盘。
 *
 * 关键（真机反馈「滑条拖不动」）：数值**不能**提升到 OnboardingFlow 顶层——
 * 那样每拖动一帧都会让上层（AnimatedContent 的调用方）重组，滑块手势被打断，表现为「滑不动」。
 * 这里与「显示与阅读」页完全同构：状态留在本步内部，松手时 saveFontScale。
 */
@Composable
private fun FontStep(topInset: Dp) {
    var scale by remember { mutableFloatStateOf(HupuPrefs.loadFontScale()) }
    val sliderSurface = MaterialTheme.colorScheme.surface
    val sliderBackdrop = rememberCanvasBackdrop { drawRect(sliderSurface) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(topInset))
        StepHead("阅读字号", "只影响帖子详情页的正文与评论，随时可调")
        // 预览
        Group {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "这是帖子标题的样子",
                    fontSize = (17 * scale).sp,
                    lineHeight = (24 * scale).sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "这是正文与评论的样子。拖动下方滑条即可实时预览，松手自动保存。",
                    fontSize = (15 * scale).sp,
                    lineHeight = (24 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "12楼 · 亮 56",
                    fontSize = (12 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 滑条
        Group {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("小", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${(scale * 100).roundToInt()}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.weight(1f))
                    Text("大", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // 1.285：整条轨道可拖 / 可点，且**保留 kyant 的按压动效**。
                //
                // LiquidSlider 的拖拽手势只挂在 40×24dp 的玻璃扣上（必须精确按住它才能拖，
                // 真机表现为「滑条拖不动」）。之前的覆盖层写法把扣子也盖住了，
                // 于是按下/拖动时扣子变成纯色块、没有镜头动效 —— 真机反馈已确认不可接受。
                //
                // 现在把手势挂在**祖先容器**上（不是盖在上面的兄弟节点）：
                //  · 按到玻璃扣 → 子节点先拿到事件并消费 → 祖先的检测器自动取消，扣子动效照常；
                //  · 按到轨道空白 → 子节点没消费 → 祖先负责换算数值。
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val trackW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                    val span = HupuPrefs.FONT_SCALE_MAX - HupuPrefs.FONT_SCALE_MIN
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pointerInput(trackW) {
                                detectTapGestures { pos ->
                                    scale = (HupuPrefs.FONT_SCALE_MIN + span * (pos.x / trackW))
                                        .coerceIn(HupuPrefs.FONT_SCALE_MIN, HupuPrefs.FONT_SCALE_MAX)
                                    HupuPrefs.saveFontScale(scale)
                                }
                            }
                            .pointerInput(trackW) {
                                var startVal = 0f
                                var acc = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        startVal = scale
                                        acc = 0f
                                    },
                                    onDragEnd = { HupuPrefs.saveFontScale(scale) },
                                    onDragCancel = { HupuPrefs.saveFontScale(scale) },
                                ) { change, dragAmount ->
                                    change.consume()
                                    acc += dragAmount
                                    scale = (startVal + span * acc / trackW)
                                        .coerceIn(HupuPrefs.FONT_SCALE_MIN, HupuPrefs.FONT_SCALE_MAX)
                                }
                            },
                    ) {
                        LiquidSlider(
                            value = { scale },
                            onValueChange = { scale = it },
                            valueRange = HupuPrefs.FONT_SCALE_MIN..HupuPrefs.FONT_SCALE_MAX,
                            backdrop = sliderBackdrop,
                            onValueChangeFinished = { HupuPrefs.saveFontScale(scale) },
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                Row {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "恢复默认",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scale = 1.0f
                                HupuPrefs.saveFontScale(1.0f)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------------------------------------------------------------------
// ③ 首页频道
// ---------------------------------------------------------------------------
@Composable
private fun HomeChannelStep(topInset: Dp) {
    val repo = remember { HupuRepository() }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var hot by remember { mutableStateOf<List<HupuTopicInfo>>(emptyList()) }
    var cats by remember { mutableStateOf<List<HupuCategory>>(emptyList()) }
    var selected by remember { mutableStateOf<List<HupuTopicInfo>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var cateId by remember { mutableStateOf<String?>(null) } // null = 全部

    LaunchedEffect(tick) {
        loading = true
        failed = false
        val b = runCatching { repo.board() }.getOrNull()
        val c = runCatching { repo.categories() }.getOrDefault(emptyList())
        val hotList = b?.hotTopics ?: emptyList()
        hot = hotList
        cats = c
        if (hotList.isEmpty() && c.isEmpty()) {
            failed = true
        } else {
            selected = if (HupuPrefs.hasCustomHomeTopics()) HupuPrefs.loadHomeTopics() else hotList
        }
        loading = false
    }

    // 候选全集：分类里的全部话题 + 官方热门（按 url 去重，保留首次出现的顺序）
    val candidates = remember(cats, hot) {
        val out = ArrayList<HupuTopicInfo>()
        val seen = HashSet<String>()
        (hot + cats.flatMap { it.topics }).forEach { t ->
            if (t.url.isNotBlank() && seen.add(t.url)) out += t
        }
        out
    }
    // 分类 tab：只保留含有话题的分类
    val cateTabs = remember(cats) { cats.filter { it.topics.isNotEmpty() } }
    val shown = remember(candidates, cats, cateId, query) {
        val base = if (cateId == null) candidates
        else cats.firstOrNull { it.cateId == cateId }?.topics ?: emptyList()
        val q = query.trim()
        if (q.isEmpty()) base
        else base.filter { it.name.contains(q, ignoreCase = true) || it.desc.contains(q, ignoreCase = true) }
    }
    val full = selected.size >= HupuPrefs.MAX_HOME_TOPICS

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(topInset))
        StepHead(
            "首页频道",
            "选择首页顶部横滑条要显示哪些频道（最多 ${HupuPrefs.MAX_HOME_TOPICS} 个），默认全部显示",
        )
        when {
            loading -> LoadingCard("正在获取频道…")
            failed -> HintCard("暂时拿不到频道列表，可稍后在「我的 → 首页频道自定义」里设置")
            else -> {
                // 搜索框
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索频道",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Text(
                            "清除",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { query = "" }
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                }
                // 分类 tab（1.289：选中项自动居中，与首页话题条/专区同款弹簧）
                val cateCenter = rememberChipAutoCenter()
                val cateSelIdx = if (cateId == null) 0
                else (cateTabs.indexOfFirst { it.cateId == cateId } + 1).coerceAtLeast(0)
                ChipAutoCenterRow(center = cateCenter, selectedIndex = cateSelIdx) {
                    ChipSlot(cateCenter, 0) {
                        Chip(text = "全部", selected = cateId == null) { cateId = null }
                    }
                    cateTabs.forEachIndexed { i, c ->
                        ChipSlot(cateCenter, i + 1) {
                            Chip(text = c.name, selected = cateId == c.cateId) { cateId = c.cateId }
                        }
                    }
                }
                // 列表
                Group(Modifier.weight(1f)) {
                    if (shown.isEmpty()) {
                        Text(
                            "没有匹配的频道",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    } else {
                        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(shown, key = { it.url }) { t ->
                                val on = selected.any { it.url == t.url }
                                CheckRow(title = t.name.ifBlank { t.desc }, subtitle = null, on = on) {
                                    val next = if (on) selected.filterNot { it.url == t.url }
                                    else if (selected.size < HupuPrefs.MAX_HOME_TOPICS) selected + t
                                    else selected
                                    if (next.size != selected.size) {
                                        selected = next
                                        HupuPrefs.saveHomeTopics(next)
                                    }
                                }
                                Hairline()
                            }
                        }
                    }
                }
                Text(
                    if (full) "已选 ${selected.size} 个 · 已达上限 ${HupuPrefs.MAX_HOME_TOPICS} 个，取消一个才能再选"
                    else "已选 ${selected.size} 个 · 共 ${shown.size} 个可选项",
                    fontSize = 12.sp,
                    color = if (full) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ④ 评分频道
// ---------------------------------------------------------------------------
@Composable
private fun ScoreChannelStep(topInset: Dp) {
    val all = remember { HupuMatchApi.GAMES }
    var selected by remember {
        mutableStateOf(
            if (HupuPrefs.hasCustomScoreGames()) HupuPrefs.loadScoreGames().toSet()
            else all.map { it.first }.toSet()
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Spacer(Modifier.height(topInset))
        StepHead(
            "评分频道",
            "选择评分页顶部横滑条要显示的项目（最多 ${HupuPrefs.MAX_SCORE_GAMES} 个），默认全部显示",
        )
        Group(Modifier.weight(1f)) {
            LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                items(all, key = { it.first }) { (id, name) ->
                    val on = id in selected
                    CheckRow(title = name, subtitle = null, on = on) {
                        val next = when {
                            on && selected.size == 1 -> selected // 至少保留一个
                            on -> selected - id
                            selected.size < HupuPrefs.MAX_SCORE_GAMES -> selected + id
                            else -> selected
                        }
                        if (next != selected) {
                            selected = next
                            // 顺序沿用官方频道顺序，避免用户自定义后排序错乱
                            HupuPrefs.saveScoreGames(all.map { it.first }.filter { it in next })
                        }
                    }
                    Hairline()
                }
            }
        }
        Text(
            if (selected.size == 1) "至少保留 1 个频道 · 已选 1 个"
            else "已选 ${selected.size} 个 · 看不清可在「我的」里调整",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// iOS 风格基础件
// ---------------------------------------------------------------------------

/**
 * 1.285 封面页：不放图标与软件介绍，只决定「开始设置」还是「跳过整个流程」。
 *
 * 两个按钮都是 kyant 玻璃控件：主按钮在底部通栏（「开始设置」），
 * 页面内放一个次级「跳过，直接使用」——它是唯一能跳过**整个流程**的入口。
 */
@Composable
private fun CoverStep(topInset: Dp, onStart: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(topInset))
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth()) {
            Text(
                "欢迎使用",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "接下来是几步基本设置：外观、阅读字号、首页频道与评分频道，都可以稍后在「我的」里修改。",
                fontSize = 13.5.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        // 开始设置：玻璃圆形图标钮，位于下半屏居中
        LiquidIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = "开始设置",
            onClick = onStart,
            size = 64.dp,
            iconSize = 26.dp,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.weight(1.3f))
    }
}

/** 步骤大标题 + 一行说明（iOS 大标题，左对齐）。 */
@Composable
private fun StepHead(title: String, desc: String) {
    Column(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(desc, fontSize = 12.5.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** iOS inset-grouped 卡片容器：圆角 + 单一底色（行间靠 Hairline 分隔）。 */
@Composable
private fun Group(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) { content() }
}

@Composable
private fun Hairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
    )
}

/**
 * iOS 列表行：左侧标题（可带副标题），右侧选择标记。
 *
 * [trailing] = "radio" 时用**圆点**（单选语义），否则用**对勾**（多选语义）——
 * 与 iOS「设置项 / 多选列表」的两种标准形态一致。
 */
@Composable
private fun CheckRow(
    title: String,
    subtitle: String?,
    on: Boolean,
    trailing: String = "check",
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        if (trailing == "switch") {
            // 开关：kyant 玻璃开关（与「显示与阅读」页同源）
            GlassSwitch(checked = on, onCheckedChange = { onClick() })
        } else if (trailing == "radio") {
            // 单选：实心圆 = 已选，空心圆环 = 未选
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .then(
                        if (on) Modifier.background(MaterialTheme.colorScheme.primary)
                        else Modifier.border(
                            1.5.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                            CircleShape,
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (on) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
        } else {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = if (on) MaterialTheme.colorScheme.primary
                else Color.Transparent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun LoadingCard(text: String) {
    Group {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Group {
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun AccentDot(
    label: String,
    selected: Boolean,
    color: Color?,
    brush: Brush? = null,
    checkTint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 2.dp),
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .then(
                    when {
                        brush != null -> Modifier.background(brush)
                        color != null -> Modifier.background(color)
                        else -> Modifier
                    }
                )
                .then(
                    if (selected) Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        CircleShape,
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = checkTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}