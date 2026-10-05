package com.java.myapplication.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.java.myapplication.ui.components.HupuIcons
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 视频组件（1.2xx 重写版）。
 *
 * 全组件只有这几条规则，不再有"参数叠加"：
 *  1. **尺寸**：容器比例 == 内容比例（预览=封面比例，播放就绪后=视频比例）。
 *     比例一致时 Fit 就等于铺满 —— 既不裁切封面（不丢信息），也没有黑边、不会显得小。
 *     切换发生在"视频可显示"的同一帧：封面撤下与框变形同时完成，没有中间态。
 *  2. **渲染面**：media3 的 PlayerView（内部 SurfaceView）。它不受 Compose alpha/缩放影响，
 *     所以隐藏靠"上面盖一层封面"、缩放靠 graphicsLayer，不做 OPAQUE 之类 hack。
 *  3. **视频自带黑边**：抓帧检测真实画面范围（VideoContentBox.kt），再放大裁掉外圈。
 *  4. **控件**：初始不显示；缓冲时立即隐藏且不自动恢复；3s 无操作自动隐藏；点击画面唤出。
 *
 * 对外只有三个符号：VideoHost / VideoPlayer / FullscreenVideo。
 */

// ─────────────────────────────────────────────────────── 1. 常量与纯函数

/** 倍速档位 */
internal val PLAYER_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** 毫秒 → 播放器时间串（`mm:ss` / `h:mm:ss`） */
internal fun formatPlayerTime(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSec = ms / 1000L
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

/** 倍速显示串：1.0 → 1×，1.25 → 1.25× */
internal fun formatSpeed(speed: Float): String {
    val v = (speed * 100).roundToInt() / 100f
    return if (v == v.toInt().toFloat()) "${v.toInt()}×" else "${v}×"
}

/** 小窗尺寸规格（纯函数，便于单测） */
internal data class VideoFrameSpec(
    val ratio: Float,
    val heightDp: Float,
    /** 高度被上限截断 —— 此时框比内容"扁"，需要改用 Crop 才不留黑边 */
    val capped: Boolean,
)

/**
 * 小窗尺寸规则（全组件唯一一条）：
 * 容器比例 = 内容比例（钳制 0.4~2.6）；高度 = 宽度/比例，且不超过 [maxHeightDp]。
 */
internal fun videoFrameSpec(widthDp: Float, contentRatio: Float, maxHeightDp: Float): VideoFrameSpec {
    val ratio = contentRatio.coerceIn(0.4f, 2.6f)
    if (widthDp <= 0f) return VideoFrameSpec(ratio, 0f, false)
    val natural = widthDp / ratio
    val capped = natural > maxHeightDp
    return VideoFrameSpec(ratio, if (capped) maxHeightDp else natural, capped)
}

// ─────────────────────────────────────────────────────── 2. 宿主与播放状态

/**
 * 播放器宿主：实例与"是否已起播"都放在这里 —— 详情页是 LazyColumn，
 * 主楼滚出视口时 item 会被销毁，状态若放 item 内部会丢失（滚回来退回封面、进度清零）。
 */
internal class VideoHost {
    /**
     * 播放器实例的**唯一来源**（可观察）：小窗、全屏、播放按钮都读它。
     * 1.2xx（真机反馈「有的视频要点两次播放」）：此前这里是普通 var，而小窗里还维护了一份
     * 本地镜像 —— 两者一旦不同步（例如实例被另一处创建），小窗会停在"封面 + 播放按钮"分支，
     * 而 `started` 已为 true 导致 LaunchedEffect 不再重建，于是点播放毫无反应。
     */
    var player: ExoPlayer? by mutableStateOf(null)
    var started by mutableStateOf(false)
    /** 滚出视口前是否在播放：滚回来据此自动续播 */
    var resumeOnAppear = false
}

/** 播放器 UI 状态 */
@Stable
internal class HupuPlayerUi {
    var playing by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var ended by mutableStateOf(false)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var speed by mutableFloatStateOf(1f)

    /** 视频显示比例（含旋转）；未知为 null */
    var videoAspect by mutableStateOf<Float?>(null)
}

/** 把 ExoPlayer 接到 UI 状态：监听器 + 位置轮询 */
@Composable
internal fun rememberPlayerUi(player: ExoPlayer?): HupuPlayerUi {
    val ui = remember { HupuPlayerUi() }
    DisposableEffect(player) {
        if (player == null) return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                ui.playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ui.buffering = playbackState == Player.STATE_BUFFERING
                ui.ended = playbackState == Player.STATE_ENDED
                player.duration.takeIf { it != C.TIME_UNSET && it > 0L }?.let { ui.durationMs = it }
            }

            override fun onRenderedFirstFrame() {
                ui.buffering = false
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    ui.videoAspect =
                        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                ui.speed = playbackParameters.speed
            }
        }
        player.addListener(listener)
        ui.playing = player.isPlaying
        ui.buffering = player.playbackState == Player.STATE_BUFFERING
        ui.speed = player.playbackParameters.speed
        player.duration.takeIf { it != C.TIME_UNSET && it > 0L }?.let { ui.durationMs = it }
        onDispose { player.removeListener(listener) }
    }
    // 位置轮询：播放中 400ms，暂停/缓冲 1s
    LaunchedEffect(player, ui.playing) {
        if (player == null) return@LaunchedEffect
        while (true) {
            ui.positionMs = player.currentPosition
            ui.bufferedMs = player.bufferedPosition
            player.duration.takeIf { it != C.TIME_UNSET && it > 0L }?.let { ui.durationMs = it }
            delay(if (ui.playing) 400L else 1000L)
        }
    }
    return ui
}

/** 退到后台自动暂停（视频不做后台播放） */
@Composable
private fun PlayerLifecycleBinding(player: ExoPlayer?) {
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(player, activity) {
        if (player == null || activity == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP ->
                    if (player.isPlaying) player.pause()

                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
}

// ─────────────────────────────────────────────────────────────── 3. 小窗

/** 创建播放器：音频焦点（自动让路）+ 非精确 seek（避免改进度后缓存条塌回播放头） */
private fun buildPlayer(context: android.content.Context, url: String): ExoPlayer =
    ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        setSeekParameters(SeekParameters.CLOSEST_SYNC)
        setMediaItem(MediaItem.fromUri(url))
        prepare()
        playWhenReady = true
    }

/**
 * 小窗视频。
 *  · 未播放：整张封面（不裁切）+ 播放按钮
 *  · 已播放：交给 [PlayerStage]，容器比例切到视频比例
 *  · 滚出视口只暂停不释放、滚回来续播；真正释放由页面级负责
 */
@Composable
internal fun VideoPlayer(
    url: String,
    cover: String,
    host: VideoHost,
    isFullscreen: Boolean,
    onToggleFullscreen: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    // 单一来源：不再维护本地镜像（见 VideoHost.player 注释）
    val player = host.player

    /** 视频真正可显示（封面撤下）时置位 */
    var posterUp by remember(url) { mutableStateOf(true) }

    DisposableEffect(url) {
        onDispose {
            // 滚出视口只暂停、不释放（实例始终由 host 持有，进度因此保留）
            host.player?.let { p ->
                host.resumeOnAppear = p.isPlaying
                p.pause()
            }
        }
    }
    // 回到视口：离开前在播放 → 继续播
    LaunchedEffect(Unit) {
        host.player?.let { p ->
            if (host.resumeOnAppear) {
                host.resumeOnAppear = false
                p.play()
            }
        }
    }
    LaunchedEffect(host.started, url) {
        if (host.started && host.player == null) {
            host.player = buildPlayer(context, url)
        }
    }

    /** 点播放：**幂等地**确保"这一次点击就进入播放"（建实例 / 从暂停态续播都覆盖） */
    fun ensurePlaying() {
        host.started = true
        val p = host.player
        if (p == null) {
            host.player = buildPlayer(context, url)
        } else {
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
            p.play()
        }
    }

    PlayerLifecycleBinding(player)
    val ui = rememberPlayerUi(player)

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // ── 尺寸：**全程只有一个**（1.2xx 定稿）────────────────────────────────
        // 容器恒为 16:9，与内容无关 —— 打开帖子、点播放都不会有任何尺寸变化。
        //   · 封面用 Crop 填充 → 任何封面都铺满这个框
        //   · 视频用 FIT 居中 → 16:9 视频正好铺满；其它比例则左右/上下留边（不裁画面）
        // 高度仍走统一规则（含 60% 屏高上限），只是 16:9 下永远不会触到上限。
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
        val spec = videoFrameSpec(maxWidth.value, 16f / 9f, maxHeight.value)
        val posterScale = ContentScale.Crop

        Box(
            Modifier
                .fillMaxWidth()
                .height(spec.heightDp.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
        ) {
            if (isFullscreen) {
                // 全屏时渲染由页面级 overlay 接管，这里只占位避免布局跳动
            } else if (player != null) {
                PlayerStage(
                    player = player,
                    ui = ui,
                    isFullscreen = false,
                    posterUrl = cover,
                    posterScale = posterScale,
                    posterVisible = posterUp,
                    onReady = { posterUp = false },
                    onToggleFullscreen = { onToggleFullscreen(true) },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = posterScale,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().clickable { ensurePlaying() })
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
        }
    }
}

// ─────────────────────────────────────────────────────────────── 4. 全屏

/**
 * 纵向拖动（亮度 / 音量）的基准值载体。
 *  · 必须跨重组保持（见 PlayerStage 注释）；
 *  · **只在起手时读一次**，拖动过程中绝不回写 —— 因为传入的位移是「相对起手的总位移」，
 *    若每次事件都回写基准，就会变成「上次结果 + 总位移」的叠加，指数级放大
 *    （真机反馈「轻轻一划音量就 100%」正是这个原因）。
 */
private class VerticalDragBases {
    var startBrightness = 0.5f
    var startVolume = 0
    var volumeMax = 15

    /** 进入播放器时的原始窗口亮度（离开时还原） */
    var brightnessOriginal: Float? = null
}

/** 纵向拖动灵敏度：拖满整屏 = 取值区间的该比例（1.0 = 亮度 0~100% / 音量 0~max） */
private const val VERTICAL_DRAG_SPAN = 1.0f

/** 横向滑动调速的灵敏度：拖满整屏 = 时长 × 该系数。
 *  1.0 = 拖满整屏正好走完整个视频（旧值）；0.5 = 拖满整屏走一半时长，
 *  手指行程翻倍 → 更容易落到想要的秒数（真机反馈「滑动尤其不准」）。 */
private const val SEEK_DRAG_SPAN = 0.5f

/** 全屏 overlay：黑底满屏 + 沉浸式系统栏；方向跟随视频分辨率。
 *  退出走"直接置位"（与系统返回手势同路径），不做抓帧 / 退场动画的中间态。 */
@Composable
internal fun FullscreenVideo(
    player: ExoPlayer,
    onExit: () -> Unit,
    /** 新渲染面已在屏幕外出画（overlay 即将推入可见区） */
    onRevealed: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val ui = rememberPlayerUi(player)
    val exitState by rememberUpdatedState(onExit)
    val revealState by rememberUpdatedState(onRevealed)
    // 1.2xx（真机反馈）：退出全屏**直接置位** —— 与系统返回手势走同一条路径。
    // 此前"先抓帧 / 先播退场动画再退出"都会让下层内容露出（下层是黑色占位），
    // 看着就是闪一下；返回手势没有这些中间态，所以不闪。

    // 1.2xx（真机反馈：进全屏"黑一下"）：入场不再直接把 overlay 盖上来 ——
    // 先把整个 overlay 停在**屏幕外**，让新渲染面（以及可能发生的转屏）在看不见的地方
    // 完成出画，再把 overlay 推入可见区，于是那段"还没出画的空白"根本不会被人看到。
    // 计时键用屏幕尺寸：一旦发生转屏，计时重新开始，等布局稳定后再显示。
    // 注意用布局偏移（Modifier.offset）而不是 graphicsLayer —— SurfaceView 不吃图层变换。
    val cfg = LocalConfiguration.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(cfg.screenWidthDp, cfg.screenHeightDp) {
        delay(200)
        if (!revealed) {
            revealed = true
            revealState()
        }
    }
    val offscreen = cfg.screenHeightDp.dp

    val initial = player.videoSize
    var portraitVideo by remember {
        mutableStateOf(initial.width > 0 && initial.height > initial.width)
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    portraitVideo = videoSize.height > videoSize.width
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(portraitVideo) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.requestedOrientation = if (portraitVideo) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val w = activity?.window
        if (w != null) {
            val c = WindowInsetsControllerCompat(w, view)
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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
            // 屏幕外预热：推入可见区之前，整块 overlay 都停在屏幕下方
            .offset(y = if (revealed) 0.dp else offscreen)
            .background(Color.Black),
    ) {
        PlayerStage(
            player = player,
            ui = ui,
            isFullscreen = true,
            posterUrl = null,
            posterScale = ContentScale.Fit,
            posterVisible = false,
            onReady = {},
            onToggleFullscreen = { exitState() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────────────────────── 5. 播放舞台

/**
 * 渲染面 + 手势 + 控件 + HUD + 缓冲指示（小窗与全屏共用）。
 *
 * 渲染面是 media3 的 PlayerView（内部 SurfaceView），**不受 Compose alpha / 缩放影响**，
 * 因此：隐藏靠"上面盖封面"、缩放靠 graphicsLayer、空白期靠不透明黑 shutter。
 */
@Composable
private fun PlayerStage(
    player: ExoPlayer,
    ui: HupuPlayerUi,
    isFullscreen: Boolean,
    posterUrl: String?,
    posterScale: ContentScale,
    /** 封面是否压在上面（由外部控制：容器 spring 动画落定后才撤下） */
    posterVisible: Boolean,
    onReady: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // ── 控件显隐
    var controlsVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var speedPanel by remember { mutableStateOf(false) }
    // 1.2xx（真机反馈「拖进度条几秒后控件自动隐藏」）：拖动进度条期间**禁止自动隐藏**。
    // 此前只有"松手提交"才 markInteraction，拖到 3s 控件就被收起 —— 而进度条一旦从
    // 组合里移除，PointerInput 随之取消，拖动被中断、seek 也丢了（这正是"拖不准"的根因之一）。
    var scrubbing by remember { mutableStateOf(false) }

    fun markInteraction() {
        lastInteraction = System.currentTimeMillis()
    }
    LaunchedEffect(lastInteraction, speedPanel, scrubbing) {
        while (true) {
            delay(250)
            if (!speedPanel && !scrubbing &&
                controlsVisible &&
                System.currentTimeMillis() - lastInteraction >= 3000L
            ) {
                controlsVisible = false
            }
        }
    }
    // 缓冲一开始就收起控件，且不自动恢复（唤出控件只能靠点击画面/操作控件）
    LaunchedEffect(ui.buffering) {
        if (ui.buffering) controlsVisible = false
    }

    // ── 轻量提示
    var hudKind by remember { mutableIntStateOf(-1) } // 0 亮度 1 音量
    var hudValue by remember { mutableIntStateOf(0) }
    var hudTick by remember { mutableIntStateOf(0) }
    var seekFlashForward by remember { mutableStateOf<Boolean?>(null) }
    var seekFlashTick by remember { mutableIntStateOf(0) }
    var speeding by remember { mutableStateOf(false) }
    LaunchedEffect(hudTick) {
        if (hudKind >= 0) {
            delay(900)
            hudKind = -1
        }
    }
    LaunchedEffect(seekFlashTick) {
        if (seekFlashForward != null) {
            delay(650)
            seekFlashForward = null
        }
    }

    // ── 拖动预览（只改显示，松手才真正 seek）
    var seekPreviewMs by remember { mutableStateOf<Long?>(null) }

    fun setSpeed(s: Float) {
        player.setPlaybackSpeed(s)
        ui.speed = s
    }
    // 统一 seek 出口：记下 seek 前的缓冲位置，1.5s 内不让缓存条"塌回"播放头
    var stickyBufferMs by remember { mutableLongStateOf(0L) }
    var stickyTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(stickyTick) {
        if (stickyBufferMs > 0L) {
            delay(1500)
            stickyBufferMs = 0L
        }
    }
    fun seekTo(ms: Long) {
        // 1.2xx（真机反馈「拖动/滑动调进度不准」）：用户主动 seek 用 **EXACT** ——
        // 落点与所选位置一致；此前全局用 CLOSEST_SYNC 会吸附到最近的同步帧（可差 1~2 秒），
        // 表现为"松手后跳到别处"。缓存条由 stickyBufferMs 兜住，仍不会塌回播放头。
        runCatching { player.setSeekParameters(SeekParameters.EXACT) }
        stickyBufferMs = maxOf(stickyBufferMs, ui.bufferedMs)
        stickyTick++
        player.seekTo(ms)
        ui.positionMs = ms
    }

    // ── 亮度 / 音量：窗口亮度是 Activity 级状态，离开播放器必须还原
    // 注意：这些基准值必须 remember —— 若写成普通局部 var，拖动过程中 HUD 触发的每次重组
    // 都会把它们重置回初值（音量因此"每次都从 0 开始"）。
    val dragBases = remember { VerticalDragBases() }
    DisposableEffect(Unit) {
        onDispose {
            dragBases.brightnessOriginal?.let { orig ->
                activity?.window?.let { w ->
                    val lp = w.attributes
                    lp.screenBrightness = orig
                    w.attributes = lp
                }
            }
        }
    }
    fun verticalStart() {
        dragBases.startBrightness =
            activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
        val am = context.getSystemService(AudioManager::class.java)
        dragBases.volumeMax = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        dragBases.startVolume = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    }
    fun verticalDrag(dyFraction: Float, left: Boolean) {
        if (left) {
            if (dragBases.brightnessOriginal == null) {
                dragBases.brightnessOriginal = activity?.window?.attributes?.screenBrightness ?: -1f
            }
            // 由「起手值 + 总位移」直接算出目标值（不回写基准）
            val v = (dragBases.startBrightness + dyFraction * VERTICAL_DRAG_SPAN).coerceIn(0.03f, 1f)
            activity?.window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = v
                w.attributes = lp
            }
            hudKind = 0
            hudValue = (v * 100).roundToInt()
        } else {
            val am = context.getSystemService(AudioManager::class.java)
            val v = (dragBases.startVolume +
                (dyFraction * VERTICAL_DRAG_SPAN * dragBases.volumeMax).roundToInt())
                .coerceIn(0, dragBases.volumeMax)
            am?.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
            hudKind = 1
            hudValue = if (dragBases.volumeMax > 0) {
                (v.toFloat() / dragBases.volumeMax * 100).roundToInt()
            } else {
                0
            }
        }
        hudTick++
    }
    var speedBeforeHold = 1f
    fun holdSpeed(on: Boolean) {
        if (on) {
            speedBeforeHold = ui.speed
            setSpeed(2f)
            speeding = true
        } else {
            setSpeed(speedBeforeHold)
            speeding = false
        }
    }

    // ── 视频"自带黑边"检测（抓帧 → 画面范围 → 放大裁掉）
    val videoUrl = remember(player) {
        runCatching { player.getMediaItemAt(0).localConfiguration?.uri?.toString() }.getOrNull()
    }
    var boxSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    var frameInfo by remember(videoUrl) { mutableStateOf(videoUrl?.let { cachedFrameInfo(it) }) }
    var detected by remember(videoUrl) { mutableStateOf(frameInfo != null) }
    LaunchedEffect(videoUrl) {
        val url = videoUrl
        if (url == null) {
            detected = true
        } else {
            if (frameInfo == null) {
                frameInfo = withTimeoutOrNull(1500) { probeFrameInfo(url) }
            }
            detected = true
        }
    }
    // 同步换算（不进协程）：缓存命中时第一帧就是放大后的画面，划回来不闪
    val renderAspect = ui.videoAspect ?: frameInfo?.aspect
    val viewBox = remember(frameInfo, boxSize, renderAspect) {
        val b = frameInfo?.box
        val w = boxSize.width.toFloat()
        val h = boxSize.height.toFloat()
        if (b == null || renderAspect == null || w <= 0f || h <= 0f) {
            null
        } else {
            toViewBox(b, w, h, renderAspect, 1f).takeIf { it.hasPadding }
        }
    }
    // 兜底：检测完成 400ms 后仍算不出换算 → 直接显示，绝不黑屏
    var forceReveal by remember { mutableStateOf(false) }
    LaunchedEffect(detected) {
        if (detected) {
            delay(400)
            forceReveal = true
        }
    }
    val revealed = forceReveal || (detected && (frameInfo?.box == null || viewBox != null))
    val revealAlpha by animateFloatAsState(
        targetValue = if (revealed) 1f else 0f,
        animationSpec = tween(120),
        label = "videoReveal",
    )
    // 视频可显示就立刻通知外部：外部据此启动容器 spring 动画；
    // 封面由外部一直保留到动画落定（每一帧都是满的），落定后才撤下
    LaunchedEffect(revealed) {
        if (revealed) onReady()
    }

    Box(modifier.onSizeChanged { boxSize = it }) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { v -> if (v.player !== player) v.player = player },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = revealAlpha
                    val b = viewBox ?: return@graphicsLayer
                    val (k, dx, dy) = contentZoom(b)
                    scaleX = k
                    scaleY = k
                    translationX = dx * size.width * k
                    translationY = dy * size.height * k
                },
        )
        // 封面占位：盖在渲染面之上（SurfaceView 隐藏不掉，只能盖）
        if (posterVisible && posterUrl != null) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = posterScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // 手势层
        Box(
            Modifier
                .fillMaxSize()
                .playerGestureLayer(
                    durationProvider = { ui.durationMs },
                    positionProvider = { ui.positionMs },
                    controlsVisibleProvider = { controlsVisible },
                    onTap = {
                        markInteraction()
                        controlsVisible = !controlsVisible
                        speedPanel = false
                    },
                    onDoubleTap = { forward, controlsBefore ->
                        markInteraction()
                        controlsVisible = controlsBefore
                        speedPanel = false
                        val dur = ui.durationMs
                        if (dur > 0L) {
                            seekTo(
                                (ui.positionMs + if (forward) 15_000L else -15_000L)
                                    .coerceIn(0L, dur),
                            )
                            seekFlashForward = forward
                            seekFlashTick++
                        }
                    },
                    onSeekPreview = { ms ->
                        markInteraction()
                        seekPreviewMs = ms
                    },
                    onSeekCommit = { ms ->
                        markInteraction()
                        seekTo(ms)
                        seekPreviewMs = null
                    },
                    onVerticalStart = { verticalStart() },
                    onVertical = { dy, left ->
                        markInteraction()
                        verticalDrag(dy, left)
                    },
                    onLongPressSpeed = {
                        markInteraction()
                        holdSpeed(it)
                    },
                ),
        )

        // 控件层：缓冲时立即归零（不做淡出），顺序为「控件消失 → 转圈」
        val animatedControlsAlpha by animateFloatAsState(
            targetValue = if (controlsVisible && !ui.buffering) 1f else 0f,
            animationSpec = tween(180),
            label = "controls",
        )
        val controlsAlpha = if (ui.buffering) 0f else animatedControlsAlpha
        if (controlsAlpha > 0.01f) {
            PlayerControls(
                ui = ui,
                alpha = controlsAlpha,
                isFullscreen = isFullscreen,
                displayPositionMs = seekPreviewMs ?: ui.positionMs,
                realPositionMs = ui.positionMs,
                bufferedMs = maxOf(ui.bufferedMs, stickyBufferMs),
                onTogglePlay = {
                    markInteraction()
                    if (ui.playing) {
                        player.pause()
                    } else {
                        if (ui.ended) player.seekTo(0L)
                        player.play()
                    }
                },
                onSeekBy = { delta ->
                    markInteraction()
                    val dur = ui.durationMs
                    if (dur > 0L) seekTo((ui.positionMs + delta).coerceIn(0L, dur))
                },
                onSeekTo = { t ->
                    markInteraction()
                    seekTo(t)
                },
                onScrubChange = { on ->
                    // 拖动进度条：进入/退出"擦洗中"，期间不自动隐藏控件
                    if (on) markInteraction()
                    scrubbing = on
                },
                onToggleSpeedPanel = {
                    markInteraction()
                    speedPanel = !speedPanel
                },
                onToggleFullscreen = {
                    markInteraction()
                    onToggleFullscreen()
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 盲调提示：控件没呼出时横向拖动，用气泡显示「目标时间 / 总时长」，
        // 否则用户完全不知道拖到了哪里（进度条此时是隐藏的）
        if (seekPreviewMs != null && controlsAlpha <= 0.01f) {
            Text(
                "${formatPlayerTime(seekPreviewMs!!)} / ${formatPlayerTime(ui.durationMs)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        // 倍速面板：浮层（不参与布局，避免把中间控件顶上去）
        if (speedPanel) {
            SpeedPanel(
                current = ui.speed,
                onPick = { s ->
                    markInteraction()
                    setSpeed(s)
                    speedPanel = false
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 54.dp),
            )
        }

        seekFlashForward?.let { forward ->
            SeekFlashOverlay(forward = forward, modifier = Modifier.fillMaxSize())
        }

        if (speeding) {
            Text(
                "2× 播放中",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }

        if (hudKind >= 0) {
            HudOverlay(kind = hudKind, value = hudValue, modifier = Modifier.align(Alignment.Center))
        }

        if (ui.buffering) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 2.5.dp,
                    color = Color.White,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────── 6. 手势

/**
 * 手势层：
 *  · 单击 → 控件显隐（立即反馈）
 *  · 双击左/右 → ∓15s（若第一次点击改了控件显隐，一并还原）
 *  · 横向拖动 → **相对当前位置**调整进度（以越过 slop 的那一刻为锚点，不会"按下即跳"）
 *  · 纵向拖动 → 左半屏亮度 / 右半屏音量
 *  · 长按 → 2× 临时加速
 *  · 起手落在按钮/进度条上（已被消费）→ 直接忽略
 */
@Composable
private fun Modifier.playerGestureLayer(
    durationProvider: () -> Long,
    positionProvider: () -> Long,
    controlsVisibleProvider: () -> Boolean,
    onTap: () -> Unit,
    onDoubleTap: (Boolean, Boolean) -> Unit,
    onSeekPreview: (Long?) -> Unit,
    onSeekCommit: (Long) -> Unit,
    onVerticalStart: () -> Unit,
    onVertical: (Float, Boolean) -> Unit,
    onLongPressSpeed: (Boolean) -> Unit,
): Modifier {
    val durationState = rememberUpdatedState(durationProvider)
    val positionState = rememberUpdatedState(positionProvider)
    val controlsState = rememberUpdatedState(controlsVisibleProvider)
    val tapState = rememberUpdatedState(onTap)
    val doubleTapState = rememberUpdatedState(onDoubleTap)
    val previewState = rememberUpdatedState(onSeekPreview)
    val commitState = rememberUpdatedState(onSeekCommit)
    val verticalStartState = rememberUpdatedState(onVerticalStart)
    val verticalState = rememberUpdatedState(onVertical)
    val longPressState = rememberUpdatedState(onLongPressSpeed)

    return this.pointerInput(Unit) {
        var lastTapUptime = 0L
        var lastTapRight = false
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val startX = down.position.x
            val startY = down.position.y
            val downTime = down.uptimeMillis
            val onRightHalf = startX > size.width / 2f
            val slop = viewConfiguration.touchSlop

            var mode = 0 // 0 未定 / 1 横向进度 / 2 纵向亮度音量 / 3 长按倍速
            var upTime = downTime
            var longPressOn = false
            var seekTarget = 0L
            var seekAnchorX = 0f
            var seekBaseMs = 0L
            var verticalAnchorY = 0f
            /** 是否已越过 slop（越过就不算单击） */
            var moved = false
            var pointerUp = false

            // ── ① 长按倍速（主流做法：**按住不动**即触发，松手恢复）
            // 关键：手指静止时不会产生任何指针事件，所以必须用「超时」判定。
            // 旧实现是在事件回调里比较 `now - downTime >= 450ms`，而静止期没有事件可比较
            // → 必须滑一下才会触发倍速，还与滑动手势互相打架（真机反馈）。
            val longPressFired = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                while (true) {
                    val e = awaitPointerEvent()
                    val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) {
                        upTime = ch.uptimeMillis
                        pointerUp = true
                        break
                    }
                    if (abs(ch.position.x - startX) > slop || abs(ch.position.y - startY) > slop) {
                        moved = true
                        break
                    }
                }
                true
            } == null

            if (longPressFired) {
                mode = 3
                longPressOn = true
                longPressState.value(true)
            }

            // ── ② 主循环：长按态只等松手；其余做滑动判定
            if (!pointerUp) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) {
                        upTime = change.uptimeMillis
                        break
                    }
                    val dx = change.position.x - startX
                    val dy = change.position.y - startY
                    if (mode == 0 && (abs(dx) > slop || abs(dy) > slop)) {
                        moved = true
                        mode = if (abs(dx) > abs(dy)) 1 else 2
                        if (mode == 1) {
                            seekAnchorX = change.position.x
                            seekBaseMs = positionState.value()
                        } else {
                            // 纵向也以「越过 slop 的那一刻」为锚点，识别瞬间不跳档
                            verticalAnchorY = change.position.y
                            verticalStartState.value()
                        }
                    }
                    when (mode) {
                        1 -> {
                            val dur = durationState.value()
                            if (dur > 0L) {
                                val movedBy = change.position.x - seekAnchorX
                                seekTarget = (seekBaseMs + (movedBy / size.width * dur * SEEK_DRAG_SPAN).toLong())
                                    .coerceIn(0L, dur)
                                previewState.value(seekTarget)
                            }
                            change.consume()
                        }
                        2 -> {
                            // 从锚点（而非按下点）算位移，识别瞬间不再跳档
                            verticalState.value(-(change.position.y - verticalAnchorY) / size.height, !onRightHalf)
                            change.consume()
                        }
                        3 -> change.consume()
                    }
                }
            }

            // ── ③ 收尾
            when (mode) {
                3 -> if (longPressOn) longPressState.value(false)
                1 -> {
                    if (seekTarget > 0L) commitState.value(seekTarget)
                    previewState.value(null)
                }
                0 -> {
                    // 长按 / 滑动都没成立、且没移动过 → 单击或双击
                    if (!moved) {
                        val dt = upTime - downTime
                        if (dt in 0..300L) {
                            val gap = upTime - lastTapUptime
                            if (lastTapUptime != 0L && gap < 300L && onRightHalf == lastTapRight) {
                                lastTapUptime = 0L
                                doubleTapState.value(onRightHalf, controlsState.value())
                            } else {
                                lastTapUptime = upTime
                                lastTapRight = onRightHalf
                                tapState.value()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────── 7. 控件

@Composable
private fun PlayerControls(
    ui: HupuPlayerUi,
    alpha: Float,
    isFullscreen: Boolean,
    displayPositionMs: Long,
    realPositionMs: Long,
    bufferedMs: Long,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleSpeedPanel: () -> Unit,
    onToggleFullscreen: () -> Unit,
    /** 拖动进度条开始 / 结束（用于禁止拖动期间自动隐藏控件、避免拖动被打断） */
    onScrubChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.graphicsLayer { this.alpha = alpha }) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(6.dp))

            // 中部：∓15s + 播放/暂停
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SeekButton(forward = false) { onSeekBy(-15_000L) }
                Spacer(Modifier.width(26.dp))
                PlayerIconButton(
                    icon = if (ui.playing) HupuIcons.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (ui.playing) "暂停" else "播放",
                    onClick = onTogglePlay,
                    size = 56.dp,
                    iconSize = 26.dp,
                )
                Spacer(Modifier.width(26.dp))
                SeekButton(forward = true) { onSeekBy(15_000L) }
            }

            // 底部：时间 · 进度 · 倍速 · 全屏
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatPlayerTime(displayPositionMs), fontSize = 11.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    PlayerProgressBar(
                        position = displayPositionMs,
                        buffered = bufferedMs,
                        floorPosition = realPositionMs,
                        duration = ui.durationMs,
                        onSeek = onSeekTo,
                        onScrubChange = onScrubChange,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatPlayerTime(ui.durationMs), fontSize = 11.sp, color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.42f))
                            .border(0.6.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(50))
                            .clickable(onClick = onToggleSpeedPanel)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            formatSpeed(ui.speed),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            HupuIcons.ChevronUp,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    PlayerIconButton(
                        icon = if (isFullscreen) HupuIcons.FullscreenExit else HupuIcons.Fullscreen,
                        contentDescription = if (isFullscreen) "退出全屏" else "全屏",
                        onClick = onToggleFullscreen,
                        size = 36.dp,
                        iconSize = 18.dp,
                    )
                }
            }
        }
    }
}

/** 播放器控制按钮：深色半透明圆底 + 白图标；按下立即反馈（缩放 + 变暗） */
@Composable
private fun PlayerIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val s = if (pressed) 0.9f else 1f
                scaleX = s
                scaleY = s
                alpha = if (pressed) 0.7f else 1f
            }
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun SeekButton(forward: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        PlayerIconButton(
            icon = if (forward) HupuIcons.Forward else HupuIcons.Replay,
            contentDescription = if (forward) "快进 15 秒" else "后退 15 秒",
            onClick = onClick,
            size = 44.dp,
            iconSize = 26.dp,
        )
        Text(
            "15",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * 进度条：轨道 + 已缓冲 + 已播 + 圆点；点击定位、拖动预览。
 * `position` 是显示位置（拖动时=预览目标），`floorPosition` 是真实播放位置 ——
 * 缓冲条只跟真实位置对齐（否则拖动预览会把缓冲条一起拉走）。
 */
@Composable
private fun PlayerProgressBar(
    position: Long,
    buffered: Long,
    floorPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    /** 拖动开始 / 结束（宿主据此禁止自动隐藏控件） */
    onScrubChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragTarget by remember { mutableStateOf<Long?>(null) }
    val enabled = duration > 0L

    Box(
        modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / widthPx * duration).toLong().coerceIn(0L, duration))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onScrubChange(true)
                        dragTarget = (offset.x / widthPx * duration).toLong().coerceIn(0L, duration)
                    },
                    onDragEnd = {
                        onScrubChange(false)
                        dragTarget?.let { onSeek(it) }
                        dragTarget = null
                    },
                    onDragCancel = {
                        onScrubChange(false)
                        dragTarget = null
                    },
                ) { change, _ ->
                    dragTarget = (change.position.x / widthPx * duration).toLong()
                        .coerceIn(0L, duration)
                    change.consume()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dur = duration.coerceAtLeast(1L)
            val shown = dragTarget ?: position
            val playedFrac = (shown.toFloat() / dur).coerceIn(0f, 1f)
            val bufFrac = (maxOf(buffered, floorPosition).toFloat() / dur).coerceIn(0f, 1f)
            val trackH = 3.dp.toPx()
            val top = (size.height - trackH) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.28f),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackH),
                cornerRadius = CornerRadius(trackH / 2f),
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.45f),
                topLeft = Offset(0f, top),
                size = Size(size.width * bufFrac, trackH),
                cornerRadius = CornerRadius(trackH / 2f),
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(0f, top),
                size = Size(size.width * playedFrac, trackH),
                cornerRadius = CornerRadius(trackH / 2f),
            )
            drawCircle(
                color = Color.White,
                radius = if (dragTarget != null) 7.dp.toPx() else 5.dp.toPx(),
                center = Offset(size.width * playedFrac, size.height / 2f),
            )
        }
        dragTarget?.let { t ->
            Text(
                formatPlayerTime(t),
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SpeedPanel(current: Float, onPick: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            "播放速度",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PLAYER_SPEEDS.forEach { s ->
                val selected = abs(s - current) < 0.01f
                Text(
                    formatSpeed(s),
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.Black else Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.14f))
                        .clickable { onPick(s) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/** 双击快进退反馈：被双击那侧亮一下 + 文案 */
@Composable
private fun SeekFlashOverlay(forward: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize(0.45f)
                .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
                .background(
                    Brush.horizontalGradient(
                        colors = if (forward) {
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.14f))
                        } else {
                            listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)
                        },
                    ),
                ),
        )
        Text(
            if (forward) "快进 15 秒 ▶▶" else "◀◀ 快退 15 秒",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** 亮度 / 音量 HUD */
@Composable
private fun HudOverlay(kind: Int, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (kind == 0) "亮度" else "音量",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(4.dp))
        Text("$value%", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}