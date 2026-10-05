package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.REPLY_SORT_OPTIONS
import com.java.myapplication.data.SCORE_SORT_OPTIONS
import com.java.myapplication.data.SYNC_START_TAB_NAMES
import com.java.myapplication.data.TOPIC_SORT_TITLE_OPTIONS
import com.java.myapplication.ui.components.CenteredTopBar
import com.java.myapplication.ui.components.HuzaiToast
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.glass.LiquidGlassButton
import com.java.myapplication.ui.glass.LiquidGlassCard
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlin.coroutines.cancellation.CancellationException

/**
 * 「默认事项」二级页（盖入式）：统一设置四类默认值 ——
 *  ① 默认启动页：下次**冷启动**生效（与底部 Tab 对应）
 *  ② 专区 / 首页话题流排序：按**排序 tab 标题**匹配（各专区 tab 集合不同，命中不了则回退服务器默认）
 *  ③ 帖子回复排序：默认 / 最新 / 最热（最热是本地按点亮降序，只作用于已加载的回复）
 *  ④ 评分评论（含楼中楼）排序：最亮 / 最晚 / 最早
 *
 * 排序类默认值都在「打开页面的那一刻」读取，改完下次打开即生效、无需重启。
 */
@Composable
internal fun DefaultSortPage(onClose: () -> Unit) {
    val progress = remember { Animatable(0f) }
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

    // 当前值（本地态，选中即时刷新副标题；同时写回 prefs）
    var startTab by remember { mutableIntStateOf(HupuPrefs.loadStartTab()) }
    var topicTitle by remember { mutableStateOf(HupuPrefs.loadDefaultTopicSortTitle()) }
    var replyMode by remember { mutableIntStateOf(HupuPrefs.loadDefaultReplySort()) }
    var scoreKey by remember { mutableStateOf(HupuPrefs.loadDefaultScoreSort()) }
    /** 0=无 / 1=默认启动页 / 2=专区 / 3=帖子回复 / 4=评分评论 */
    var picking by remember { mutableIntStateOf(0) }

    val dialogBg = MaterialTheme.colorScheme.background
    val backdrop = rememberLayerBackdrop {
        drawRect(dialogBg)
        drawContent()
    }

    Box(Modifier.fillMaxSize().zIndex(2f)) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = (1f - progress.value) * size.width }
                .background(MaterialTheme.colorScheme.background)
                .tapGuard()
                .layerBackdrop(backdrop),
        ) {
            CenteredTopBar(title = "默认事项", onBack = { closing = true })
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 4.dp, bottom = 120.dp),
            ) {
                Text(
                    "三项排序默认值在下次打开对应页面时生效；页面内仍可随时手动切换，手动选择不会改动这里的默认值。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    SortSettingRow(
                        title = "默认启动页",
                        subtitle = SYNC_START_TAB_NAMES.getOrElse(startTab) { "首页" },
                        onClick = { picking = 1 },
                    )
                    SortSettingRow(
                        title = "专区默认排序",
                        subtitle = topicTitle.ifBlank { "跟随服务器默认" },
                        onClick = { picking = 2 },
                    )
                    SortSettingRow(
                        title = "帖子回复默认排序",
                        subtitle = REPLY_SORT_OPTIONS.firstOrNull { it.first == replyMode }?.second ?: "默认",
                        onClick = { picking = 3 },
                    )
                    SortSettingRow(
                        title = "评分评论默认排序",
                        subtitle = SCORE_SORT_OPTIONS.firstOrNull { it.first == scoreKey }?.second ?: "最亮",
                        onClick = { picking = 4 },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "说明：「最热」是本地按点亮数排序，只作用于已加载的回复，翻页新加载的回复不会重新插入排序；" +
                        "「专区默认排序」按排序名匹配，若当前专区没有该排序则自动沿用服务器默认。",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (picking != 0) {
            val options: List<Pair<String, String>> = when (picking) {
                1 -> SYNC_START_TAB_NAMES.mapIndexed { i, n -> i.toString() to n }
                2 -> listOf("" to "跟随服务器默认") + TOPIC_SORT_TITLE_OPTIONS.map { it to it }
                3 -> REPLY_SORT_OPTIONS.map { it.first.toString() to it.second }
                else -> SCORE_SORT_OPTIONS
            }
            val current = when (picking) {
                1 -> startTab.toString()
                2 -> topicTitle
                3 -> replyMode.toString()
                else -> scoreKey
            }
            OptionPickerDialog(
                backdrop = backdrop,
                title = when (picking) {
                    1 -> "默认启动页"
                    2 -> "专区默认排序"
                    3 -> "帖子回复默认排序"
                    else -> "评分评论默认排序"
                },
                options = options,
                selected = current,
                onPick = { v ->
                    when (picking) {
                        1 -> {
                            val i = v.toIntOrNull() ?: 0
                            startTab = i
                            HupuPrefs.saveStartTab(i)
                        }
                        2 -> { topicTitle = v; HupuPrefs.saveDefaultTopicSortTitle(v) }
                        3 -> {
                            val m = v.toIntOrNull() ?: 0
                            replyMode = m
                            HupuPrefs.saveDefaultReplySort(m)
                        }
                        else -> { scoreKey = v; HupuPrefs.saveDefaultScoreSort(v) }
                    }
                    HuzaiToast.show("已保存")
                    picking = 0
                },
                onDismiss = { picking = 0 },
            )
        }
    }
}

/** 设置行：标题 + 当前值 + 箭头 */
@Composable
private fun SortSettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** iOS 风格单选项弹窗（与「默认启动页」选择弹窗同款语汇） */
@Composable
private fun OptionPickerDialog(
    backdrop: Backdrop,
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val isLight = !isAppDarkTheme()
    val contentColor = if (isLight) Color.Black else Color.White
    LiquidGlassCard(
        backdrop = backdrop,
        onDismiss = onDismiss,
    ) { _ ->
        Text(
            title,
            Modifier.padding(24.dp, 24.dp, 24.dp, 8.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
        Column(
            Modifier
                .padding(horizontal = 12.dp)
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEach { (value, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(value) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, Modifier.weight(1f), fontSize = 15.sp, color = contentColor)
                    if (value == selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .padding(24.dp, 8.dp, 24.dp, 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LiquidGlassButton(
                text = "取消",
                accent = false,
                modifier = Modifier.weight(1f),
                contentColor = contentColor,
            ) { onDismiss() }
        }
    }
}