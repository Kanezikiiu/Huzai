package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Check
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.StickerAddCell
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.LocalView
import android.content.pm.ActivityInfo
import android.app.Activity
import coil.compose.AsyncImage
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.combinedClickable
import com.java.myapplication.ui.components.HUPU_EMOJI
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuImage
import com.java.myapplication.data.HupuPostApi
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.isLocalStickerUrl
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuSubReply
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.data.HupuVote
import com.java.myapplication.data.HupuVoteApi
import com.java.myapplication.data.HupuVoteOption
import com.java.myapplication.data.HupuVoteResult
import com.java.myapplication.data.HupuParser
import androidx.compose.foundation.text.appendInlineContent
import com.java.myapplication.ui.components.EmojiSegment
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.normalizeCover

import com.java.myapplication.ui.components.rememberEmojiInline
import com.java.myapplication.ui.components.splitEmoji

import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.formatCount
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuSticker
import com.java.myapplication.data.HupuAuthor
import kotlinx.coroutines.launch

/**
 * 1.121: 帖子详情里的投票卡（可交互）。
 * - 打开时 GET getVoteInfo 拉实时票数（免登录）；带登录态额外回「我投了哪几项」。
 * - 未投票且未结束：选项可点选（单选互斥 / 多选限 userOptionLimit），底部「提交」按钮。
 * - 已投票 / 已结束：只读展示票数条 + 百分比，高亮我投的项。
 * - 提交响应体即投票后完整状态，直接替换本地，无需二次请求。
 */
@Composable
internal fun VoteCard(vote: HupuVote, onToast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    // 1.124: 首帧直接吃会话缓存（终态票数条，零 loading 跳变）；无缓存才走 loading 骨架
    var result by remember(vote.voteId) {
        mutableStateOf(HupuVoteApi.cachedVoteInfo(vote.voteId))
    }
    var loading by remember(vote.voteId) { mutableStateOf(result == null) }
    var picked by remember(vote.voteId) {
        mutableStateOf(HupuVoteApi.cachedVoteInfo(vote.voteId)?.myChoices?.toSet() ?: emptySet())
    }
    var submitting by remember(vote.voteId) { mutableStateOf(false) }
    var tick by remember(vote.voteId) { mutableStateOf(0) }

    LaunchedEffect(vote.voteId, tick) {
        loading = result == null
        val r = HupuVoteApi.getVoteInfo(vote.voteId)
        loading = false
        if (r == null) {
            // 强刷失败：有缓存保留旧数据（静默），无缓存才亮失败态
            if (result == null) result = null
        } else {
            // 1.124: 数据有变化才写状态 → 无变化时零重组（首帧终态渲染不被打断）
            if (result != r) result = r
            if (!r.canVote) picked = r.myChoices.toSet()
        }
    }

    val limitVal = result?.limit ?: vote.limit
    val reveal = result?.let { !it.canVote } ?: false
    val canPick = result?.canVote == true && !submitting
    val options: List<HupuVoteOption> = result?.options?.takeIf { it.isNotEmpty() }
        ?: vote.choices.mapIndexed { i, c -> HupuVoteOption(i + 1, c, 0) }
    val total = (result?.totalVotes ?: 0).coerceAtLeast(1).toFloat()

    fun submit() {
        if (!HupuAccount.isLoggedIn) {
            onToast("请先在「我的」页登录")
            return
        }
        val lst = picked.sorted()
        if (lst.isEmpty()) return
        submitting = true
        scope.launch {
            val out = ArrayList<HupuVoteResult>(1)
            val err = HupuVoteApi.submit(vote.voteId, lst, out)
            submitting = false
            if (err != null) {
                onToast(err)
                // 已投过 → 拉一次服务端真实状态（可能其他端投过）
                if (err.contains("已投")) tick++
            } else {
                out.firstOrNull()?.let { result = it; picked = it.myChoices.toSet() }
                onToast("投票成功")
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                HupuIcons.Poll,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (vote.isMulti) "多选投票" else "单选投票",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            if (vote.isMulti) {
                Text(
                    "最多可选 $limitVal 项",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            vote.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        options.forEach { opt ->
            val isPicked = picked.contains(opt.sort)
            val isMine = result?.myChoices?.contains(opt.sort) == true
            val highlight = isPicked || isMine
            val frac = if (total > 0f) (opt.voteCount / total).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .then(
                        if (canPick) Modifier.clickable {
                            picked = if (vote.isMulti) {
                                when {
                                    picked.contains(opt.sort) -> picked - opt.sort
                                    picked.size >= limitVal -> picked
                                    else -> picked + opt.sort
                                }
                            } else {
                                setOf(opt.sort)
                            }
                        } else Modifier,
                    ),
            ) {
                // 票数条（投票后/结束时才揭示）
                if (reveal) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.CenterStart) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .fillMaxHeight()
                                .padding(vertical = 1.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)),
                        )
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val indShape = if (vote.isMulti) RoundedCornerShape(4.dp) else CircleShape
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(indShape)
                            .background(
                                if (highlight) MaterialTheme.colorScheme.primary else Color.Transparent,
                            )
                            .border(
                                1.dp,
                                if (highlight) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                indShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (highlight) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Text(
                        opt.content,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (reveal) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${opt.voteCount}票 ${(frac * 100).roundToInt()}%",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // 底部：参与统计 + 状态/提交
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val res = result
            Text(
                when {
                    loading -> "加载中…"
                    res == null -> "投票数据加载失败"
                    else -> "共 ${res.userCount} 人参与 · ${res.totalVotes} 票"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            when {
                loading -> {}
                res == null -> {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { tick++ }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("重试", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                res.ended -> Text(
                    "投票已结束",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                res.canVote -> {
                    val canSubmit = picked.isNotEmpty() && !submitting
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (canSubmit) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                            )
                            .clickable(enabled = canSubmit) { submit() }
                            .padding(horizontal = 18.dp, vertical = 6.dp),
                    ) {
                        Text(
                            if (submitting) "提交中…" else "提交",
                            fontSize = 13.sp,
                            color = if (canSubmit) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> Text(
                    "已投票",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ---------- 纯 Compose HTML 渲染 ----------
