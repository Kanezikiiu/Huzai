package com.java.myapplication.ui.pages

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.theme.ACCENT_DYNAMIC
import com.java.myapplication.ui.theme.AccentPalettes
import com.java.myapplication.ui.theme.accentPaletteOf
import com.java.myapplication.ui.theme.isDynamicColorAvailable
import kotlin.coroutines.cancellation.CancellationException

/**
 * 1.130 主题模式设置页（盖入式二级页）：跟随系统 / 浅色 / 深色。
 * 点选即保存并即时生效（订阅 HupuPrefs.themeModeVersion），无需重启。
 */
@Composable
fun ThemeSettingsPage(onClose: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            SecondaryPage.exit()
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

    var mode by remember { mutableStateOf(HupuPrefs.loadThemeMode()) }
    // 1.179: 色彩主题（含动态取色的伪 id）
    var accentId by remember { mutableStateOf(HupuPrefs.loadColorTheme()) }
    val options = listOf(
        Triple(HupuPrefs.THEME_SYSTEM, "跟随系统", "跟随手机系统的深色 / 浅色设置"),
        Triple(HupuPrefs.THEME_LIGHT, "浅色", "始终使用浅色主题"),
        Triple(HupuPrefs.THEME_DARK, "深色", "始终使用深色主题"),
    )

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { closing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "主题",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                // 1.179: 色彩主题——只换强调色（中性底色不变），色块圆点横向滚动，点选即时生效
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
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

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

/** 1.179: 分组标题（把「深浅模式」与「色彩主题」分开，避免语义混淆）。 */
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
 * 1.179: 色彩主题色块（圆形）。
 * [brush] 用于「动态取色」的彩虹渐变；固定主题传 [color]。
 * 选中态：主题色描边 + 对勾（勾的颜色按底色调明暗，保证可见）。
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
                // 1.187: 选中描边不再取主题色。此前「亮色填充 + 主题色描边」会让同一颗圆点上
                // 出现两个蓝（如蓝套：#0A84FF 填充 + #00639A 描边），像并存两套配色；
                // 现统一中性描边，选中态只靠加粗 + 对勾表达。
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