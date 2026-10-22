package com.java.myapplication.ui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.layout.offset
import com.java.myapplication.data.HupuSticker
import com.java.myapplication.ui.components.HUPU_EMOJI
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.StickerAddCell
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.GrandExpandState
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuScoreComment
import kotlinx.coroutines.delay
import com.java.myapplication.data.mergeWithOptimistic
import com.java.myapplication.data.flattenWithDescendants
import com.java.myapplication.data.HupuSelfDetail
import com.java.myapplication.data.ScoreCommentState
import com.java.myapplication.ui.components.EmojiText
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.HeroBadgeAvatar

import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.normalizeImageUrl
import com.java.myapplication.ui.components.normalizeCover
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.components.LiquidBackButton

/** 顶栏：返回 + 头像/名字/均分 */
@Composable
internal fun PlayerHeader(d: HupuSelfDetail, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 0.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidBackButton(onClick = { onBack() })
        Spacer(Modifier.width(8.dp))
        HeroBadgeAvatar(avatar = d.image, heroIcon = d.heroIcon, size = 52.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                d.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatCount(d.scorePersonCount)}人评分",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // KDA（仅选手层有，对局层为空串不显示）
                if (d.kda.isNotBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        d.kda,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            d.scoreAvg,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = scoreColor(d.scoreAvg),
        )
    }
}

/** 介绍卡：评分对象介绍长文（默认 4 行，溢出可展开/收起） */
@Composable
internal fun IntroCard(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflows by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(enabled = overflows) { expanded = !expanded }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
        )
        if (overflows) {
            Text(
                if (expanded) "收起" else "展开全部",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// 1.64 我的评分行：有分回显分值，无分提示去打分；点击弹出打分面板（由 ScorePage 承载）
@Composable
internal fun MyScoreCard(myScore: Int, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "我的评分",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        if (myScore > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    for (i in 1..5) {
                        Icon(
                            Icons.Rounded.Star,
                            contentDescription = null,
                            tint = if (myScore / 2 >= i) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    "${myScore}分",
                    fontSize = 13.sp,
                    color = scoreColor(myScore.toString()),
                )
            }
        } else {
            Text(
                "点击打分",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
internal fun DistributionCard(d: HupuSelfDetail) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "评分分布",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (d.distribution.isEmpty()) {
            Text(
                "暂无评分分布数据",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp),
            )
        } else {
            // 1.226d：分布条按**总人数**算比例（与官方一致：10 分档 138/155 ≈ 89%，不会顶满）
            val totalCount = d.distribution.sumOf { it.second }.coerceAtLeast(1L)
            // 1.237：**分布条长度必须一致**，以「最长的那个人数」为准。
            // 做法：数字列宽度 = 实测「最长人数」的宽度（所有行共用一个列宽），
            // 于是轨道的剩余宽度相同 → 每行轨道终点严格对齐（以最短的轨道为准）。
            // 数字在列内**左对齐**、紧贴轨道（只留 6dp），不再出现列内空白。
            // 注意不要给列宽设大下限（之前 34dp 的下限正是「轨道和人数之间一大块空隙」的来源）。
            val density = LocalDensity.current
            val digitsStyle = TextStyle(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val measurer = rememberTextMeasurer()
            val numColWidth = remember(d.distribution, measurer) {
                val longest = (d.distribution.maxOfOrNull { it.second } ?: 0L).toString()
                with(density) { measurer.measure(longest, digitsStyle).size.width.toDp() + 2.dp }
            }
            // 1.238：**取消自适应刻度**，所有卡片统一按「本档人数 / 总人数」。
            // 上一版在「最高档占不到 65%」时把满刻度缩到 65%（等比放大），
            // 结果同一功能里出现了两套刻度：占比高的卡最高档满格、占比低的卡最高档不满
            // —— 看起来就是「有的占满、有的不占满」。口径必须全局一致，条短就短。
            // 1.192: 生长动画只在「进入本页的第一次」播放——LazyColumn 回收该 item 后再滚回来不重播。
            // played 用 rememberSaveable：item 的 saved state 由 LazyList 按 key 保存/恢复，回收也记得住。
            val played = rememberSaveable { mutableStateOf(false) }
            val animateOnce = remember { !played.value }
            LaunchedEffect(Unit) { played.value = true }
            d.distribution.forEach { (level, count) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        level,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(22.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            // 1.235：轨道底色调深一点（0.5 → 0.8）。
                            // 人数少时条很短，太淡的轨道会让右侧看着「空荡荡」。
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)),
                    ) {
                        // 1.192: 渐变条身 + 生长动画 + 最小可见宽度，让分布条更有质感
                        val barColor = scoreColor("$level.0")
                        // 1.226d：比例 = 本档人数 / 总人数（全站统一口径）；
                        // 极小的档位保留 2% 最小可见宽度，避免"明明有 1 人却是空条"。
                        val target = if (count <= 0L) 0f
                        else (count.toFloat() / totalCount).coerceIn(0.02f, 1f)
                        // animateFloatAsState 首帧不会从 0 生长（初值即目标值）→ 看不到动画；
                        // 这里用 Animatable 显式从 0 播到目标，进场才能看到「条撑开」
                        val frac = remember { Animatable(0f) }
                        LaunchedEffect(target, animateOnce) {
                            if (animateOnce) {
                                frac.animateTo(target, tween(550, easing = FastOutSlowInEasing))
                            } else {
                                // 非首次进入（含数据刷新）：直接到位，不重播动画
                                frac.snapTo(target)
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxWidth(frac.value)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(barColor.copy(alpha = 0.7f), barColor),
                                    ),
                                ),
                        )
                    }
                    // 1.237：数字列宽统一 = 最长人数宽度（轨道等长、终点对齐）；数字左对齐紧贴轨道
                    Spacer(Modifier.width(6.dp))
                    Text(
                        count.toString(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(numColWidth),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** 热评卡 */
@Composable
internal fun HottestCard(list: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "热评",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        list.take(3).forEach { t ->
            Text(
                "「$t」",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
        }
    }
}
