package com.java.myapplication.ui.pages

import android.app.Activity
import android.view.Display
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuRefresh
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.CenteredTopBar
import com.java.myapplication.ui.components.LiquidBackButton
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tabSwipeSwitch
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.glass.LiquidSlider
import com.java.myapplication.ui.theme.ACCENT_DYNAMIC
import com.java.myapplication.ui.theme.AccentPalettes
import com.java.myapplication.ui.theme.accentPaletteOf
import com.java.myapplication.ui.theme.isDynamicColorAvailable
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import kotlin.coroutines.cancellation.CancellationException

/**
 * 「显示与阅读」合并页（盖入式二级页）。
 *
 * 把原先三个语义相近、内容都较短的独立二级页合并为「我的」里的单一入口：
 *   ① 界面设置（深浅模式 / 色彩主题 / 底栏）
 *   ② 阅读字号（实时预览 + 液态滑条）
 *   ③ 屏幕刷新率（设备真实档位）
 * 三段各自保留原有交互与持久化，互不影响；共用同一个滚动容器与顶栏。
 */
@Composable
fun DisplaySettingsPage(onClose: () -> Unit) {
    val progress = remember { Animatable(0f) }
    // 计数 flag 门控：多页叠加 / 重挂载时不会多减，离开组合时兜底回收
    var pageEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pageEntered = true
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    DisposableEffect(Unit) {
        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            if (pageEntered) { pageEntered = false; SecondaryPage.exit() }
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        try {
            events.collect { event -> progress.snapTo(1f - event.progress) }
            progress.animateTo(0f, tween(120))
            closing = true
        } catch (e: CancellationException) {
            progress.animateTo(1f, tween(200))
            throw e
        }
    }
    // ---------- 二级 Tab：0 = 外观，1 = 阅读字号，2 = 屏幕刷新率 ----------
    var tab by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            CenteredTopBar(
                title = "显示与阅读",
                onBack = { closing = true },
            )
            // 二级 Tab 条（复用三大页顶部横滑条同款玻璃胶囊 Chip）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(text = "外观", selected = tab == 0, onClick = { tab = 0 })
                Chip(text = "阅读字号", selected = tab == 1, onClick = { tab = 1 })
                Chip(text = "屏幕刷新率", selected = tab == 2, onClick = { tab = 2 })
            }
            // 内容区：左右滑动切换 Tab（与三大页同款 tabSwipeSwitch）
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .tabSwipeSwitch(
                        onPrevious = { if (tab > 0) tab-- },
                        onNext = { if (tab < 2) tab++ },
                    ),
            ) {
                when (tab) {
                    // ① 外观（原「界面设置」）
                    0 -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppearanceSection()
                        Spacer(Modifier.height(24.dp))
                    }
                    // ② 阅读字号（原「阅读字号」）
                    1 -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextSizeSection()
                        Spacer(Modifier.height(24.dp))
                    }
                    // ③ 屏幕刷新率（原「屏幕刷新率」）
                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RefreshRateSection()
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ① 外观：深浅模式 / 色彩主题 / 底栏（原 ThemeSettingsPage 内容）
// ---------------------------------------------------------------------------

@Composable
private fun AppearanceSection() {
    var mode by remember { mutableStateOf(HupuPrefs.loadThemeMode()) }
    var accentId by remember { mutableStateOf(HupuPrefs.loadColorTheme()) }
    val options = listOf(
        Triple(HupuPrefs.THEME_SYSTEM, "跟随系统", "跟随手机系统的深色 / 浅色设置"),
        Triple(HupuPrefs.THEME_LIGHT, "浅色", "始终使用浅色主题"),
        Triple(HupuPrefs.THEME_DARK, "深色", "始终使用深色主题"),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GroupLabel("深浅模式")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            options.forEachIndexed { i, (m, title, desc) ->
                ThemeOptionRow(
                    title = title,
                    subtitle = desc,
                    selected = mode == m,
                    onClick = {
                        mode = m
                        HupuPrefs.saveThemeMode(m)
                    },
                )
                if (i != options.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        GroupLabel("色彩主题")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
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
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color(0xFF4285F4), Color(0xFF34A853),
                                    Color(0xFFFBBC05), Color(0xFFEA4335),
                                    Color(0xFF4285F4),
                                )
                            ),
                            selected = accentId == ACCENT_DYNAMIC,
                            checkTint = Color.White,
                            onClick = {
                                accentId = ACCENT_DYNAMIC
                                HupuPrefs.saveColorTheme(ACCENT_DYNAMIC)
                            },
                        )
                    }
                    AccentPalettes.forEach { p ->
                        AccentDot(
                            label = p.label,
                            color = p.swatch,
                            selected = accentId == p.id,
                            checkTint = if (p.swatch.luminance() > 0.6f) Color(0xFF1A1A1A) else Color.White,
                            onClick = {
                                accentId = p.id
                                HupuPrefs.saveColorTheme(p.id)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (accentId == ACCENT_DYNAMIC) {
                        "动态取色 · 跟随壁纸自动配色（Android 12+）"
                    } else {
                        "当前色彩：" + accentPaletteOf(accentId).label
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        GroupLabel("底栏")
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            var autoHideBar by remember { mutableStateOf(HupuPrefs.loadAutoHideBar()) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        autoHideBar = !autoHideBar
                        HupuPrefs.saveAutoHideBar(autoHideBar)
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "滚动时自动隐藏底栏",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "向上滚动（往下看）时收起底栏，向下滚动时重新出现",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoHideBar,
                    onCheckedChange = {
                        autoHideBar = it
                        HupuPrefs.saveAutoHideBar(it)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// ② 阅读字号（原 TextSizeSettingsPage 内容）
// ---------------------------------------------------------------------------

@Composable
private fun TextSizeSection() {
    var scale by remember { mutableFloatStateOf(HupuPrefs.loadFontScale()) }
    // 滑条拇指的玻璃采样源：位于 surface 卡片上，用卡片底色做「画布背景层」即可
    val sliderSurface = MaterialTheme.colorScheme.surface
    val sliderBackdrop = rememberCanvasBackdrop { drawRect(sliderSurface) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 实时预览卡：按当前缩放渲染样例
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "示例：这是帖子标题",
                fontSize = (17 * scale).sp,
                lineHeight = (24 * scale).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size((36 * scale).dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "猪",
                        fontSize = (13 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "猪猪侠 GGBond",
                    fontSize = (13 * scale).sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "10分钟前",
                    fontSize = (12 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "这是正文示例文字。拖动下方滑块实时预览，松手后自动保存，仅应用于帖子详情页。",
                fontSize = (15 * scale).sp,
                lineHeight = (24 * scale).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("12楼", fontSize = (11 * scale).sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    "亮 56",
                    fontSize = (12 * scale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 滑条卡
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "小",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    (scale * 100).toInt().toString() + "%",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "大",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LiquidSlider(
                value = { scale },
                onValueChange = { scale = it },
                valueRange = HupuPrefs.FONT_SCALE_MIN..HupuPrefs.FONT_SCALE_MAX,
                backdrop = sliderBackdrop,
                onValueChangeFinished = { HupuPrefs.saveFontScale(scale) },
                modifier = Modifier.padding(vertical = 10.dp),
            )
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
}

// ---------------------------------------------------------------------------
// ③ 屏幕刷新率（原 RefreshRateSettingsPage 内容）
// ---------------------------------------------------------------------------

@Composable
private fun RefreshRateSection() {
    val context = LocalContext.current
    val display: Display = context.display
    val modes = remember {
        HupuRefresh.buildModes(
            display.supportedModes.map {
                HupuRefresh.ModeInfo(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
            },
        )
    }
    val systemModeId = remember { display.mode.modeId }
    var selected by remember { mutableIntStateOf(HupuPrefs.loadRefreshMode()) }

    fun choose(id: Int) {
        selected = id
        HupuPrefs.saveRefreshMode(id)
        (context as? Activity)?.let { HupuRefresh.apply(it, id) }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "档位来自设备真实支持的显示模式，选择后即时生效。「自动」跟随系统调度（LTPO 动态刷新率），更省电。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            RateRow(
                title = "自动",
                subtitle = "跟随系统调度",
                selected = selected <= 0,
                onClick = { choose(-1) },
            )
            modes.forEach { m ->
                RateRow(
                    title = "${Math.round(m.refresh)} Hz",
                    subtitle = "${m.width} × ${m.height}" + if (m.modeId == systemModeId) " · 系统" else "",
                    selected = selected == m.modeId,
                    onClick = { choose(m.modeId) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 复用小组件（原 ThemeSettingsPage / RefreshRateSettingsPage 私有件，随合并搬入）
// ---------------------------------------------------------------------------

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 分组标题（把「深浅模式」「色彩主题」「底栏」等分开，避免语义混淆）。 */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

/**
 * 色彩主题色块（圆形）。[brush] 用于「动态取色」的彩虹渐变；固定主题传 [color]。
 * 选中态：中性描边加粗 + 对勾（勾的颜色按底色调明暗，保证可见）。
 */
@Composable
private fun AccentDot(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color? = null,
    brush: Brush? = null,
    checkTint: Color = Color.White,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .then(
                    if (brush != null) Modifier.background(brush)
                    else Modifier.background(color ?: Color.Gray)
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    shape = CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "已选中",
                    tint = checkTint,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RateRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .then(
                    if (selected) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier
                        .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                ),
        )
    }
}
