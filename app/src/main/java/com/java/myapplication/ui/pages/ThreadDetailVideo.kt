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

/** 视频 host：ExoPlayer 实例跨小窗/全屏两个 PlayerView 共享（切换时进度无缝续播） */
internal class VideoHost {
    var player: ExoPlayer? = null
}

/** 视频播放器：封面+播放按钮 → 点击起播（ExoPlayer，退出组合 release）。
 * 全屏：PlayerView 官方全屏按钮（setFullscreenButtonClickListener，设置后按钮自动出现）
 * → 页面级盖入式全屏 overlay（FullscreenVideo），同一 ExoPlayer 实例跨 PlayerView 移动。 */
@Composable
internal fun VideoPlayer(
    url: String,
    cover: String,
    host: VideoHost,
    isFullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    DisposableEffect(url) {
        onDispose {
            host.player?.release()
            host.player = null
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        if (playing && !isFullscreen) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        setFullscreenButtonClickListener { onToggleFullscreen(true) }
                    }
                },
                update = { view ->
                    if (host.player == null) {
                        val p = ExoPlayer.Builder(context).build()
                        p.setMediaItem(MediaItem.fromUri(url))
                        p.prepare()
                        p.playWhenReady = true
                        host.player = p
                    }
                    if (view.player != host.player) view.player = host.player
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!playing) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { playing = true },
            )
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "播放",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(12.dp),
            )
        }
        // isFullscreen 时小窗 view 从组合移除，player 由全屏 overlay 接管
    }
}

/** 全屏视频 overlay：黑底满屏 + 传感器横屏 + 沉浸式系统栏（返回键由页面级 handler 分流退出）。
 * 播放器实例由宿主传入（与小窗共享），退出时通过官方全屏按钮或返回键恢复竖屏。 */
@Composable
internal fun FullscreenVideo(player: ExoPlayer, onExit: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val w = activity?.window
        if (w != null) {
            val c = WindowInsetsControllerCompat(w, view)
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            // 1.191: 全屏播放期间保持屏幕常亮，避免看视频时自动熄屏
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if (w != null) {
                WindowInsetsControllerCompat(w, view).show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(3f)
            .background(Color.Black)
            .tapGuard(),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    setFullscreenButtonClickListener { onExit() }
                }
            },
            update = { v -> if (v.player != player) v.player = player },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ---------- 回复 ----------
