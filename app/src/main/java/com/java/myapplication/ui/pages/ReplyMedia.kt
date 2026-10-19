package com.java.myapplication.ui.pages

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.java.myapplication.ui.player.FullscreenVideo
import com.java.myapplication.ui.player.VideoHost
import com.java.myapplication.ui.player.VideoPlayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

/**
 * 1.224p：评论里的媒体（视频 / 语音）**只做查看与播放**，不涉及发送。
 *
 * 数据形态（实测）：
 *  - 帖子评论（SSR）：`video` + `videoCover` 独立字段，`content` 为空
 *  - 楼中楼（m API）：`content` 里直接内嵌 `<video src poster>`
 *  - 评分评论：`commentAncillaryContents[]` 中 `commentContentType=AUDIO`，`commentContent` 为 mp3
 */

/**
 * 评论视频：**复用正文那套播放器**（`VideoPlayer`）—— 同一套手势
 * （单击控件 / 双击播放暂停 / 横向滑动找进度 / 纵向拖动亮度音量）、同一套控件与全屏。
 *
 * 宿主 `VideoHost` 与正文各持一个实例（正文视频与评论视频互不干扰），
 * 离开组合时释放，避免列表滚动累积解码器。
 */
@Composable
internal fun ReplyVideoCard(
    url: String,
    cover: String,
    modifier: Modifier = Modifier,
) {
    if (url.isBlank()) return
    val host = remember(url) { VideoHost() }
    var fullscreen by remember(url) { mutableStateOf(false) }

    DisposableEffect(url) {
        onDispose {
            host.player?.release()
            host.player = null
        }
    }

    // 1.224z：**自己吃掉点击**。
    // 评论卡整块挂着「打开楼中楼」的 clickable，而播放器内部有 AndroidView（PlayerView），
    // 原生视图消费的事件不会阻止 Compose 祖先的 clickable，于是点视频会顺带打开楼中楼。
    // 这里在播放器外再套一层无涟漪的 clickable（比祖先更靠近手指），把点击截住。
    val swallow = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Box(
        modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .clickable(
                interactionSource = swallow,
                indication = null,
            ) { /* 吞掉：视频区域内的点击只属于播放器 */ },
    ) {
        VideoPlayer(
            url = url,
            cover = cover,
            host = host,
            isFullscreen = false,
            onToggleFullscreen = { fullscreen = true },
        )
    }

    // 全屏：独立窗口承载（评论在 LazyColumn item 内，不能就地铺满屏幕）
    val player = host.player
    if (fullscreen && player != null) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            // 1.224q：对话框是**独立窗口**，Activity 那边的沉浸式系统栏管不到它，
            // 这里把对话框窗口也设成沉浸式（否则全屏时仍会看到状态栏/导航条）。
            DialogImmersive()
            FullscreenVideo(player = player, onExit = { fullscreen = false })
        }
    }
}

/** 让当前对话框窗口进入沉浸式（隐藏系统栏 + 刘海区域铺满） */
@Composable
private fun DialogImmersive() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val win = (view.parent as? DialogWindowProvider)?.window
        if (win != null) {
            val c = WindowInsetsControllerCompat(win, view)
            c.hide(WindowInsetsCompat.Type.systemBars())
            c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            runCatching {
                val c = view.windowInsetsController
                c?.hide(android.view.WindowInsets.Type.systemBars())
                c?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose { }
    }
}

/**
 * 评论语音条（对齐官方客户端的「波形 + 剩余时长」形态）。
 *
 * 视觉组成：
 *  - 主题色极淡底（10%）+ 极淡描边，圆角胶囊（42dp 高，比上一版矮 4dp）；
 *  - 左侧**圆角**播放三角 / 暂停双竖线（三角用 Stroke + Round join 描边，转角自然圆润）；
 *  - 中间 12 根高低错落的波形柱：已播部分实色、未播部分淡色，播放时起伏脉动；
 *  - 右侧显示**剩余时长**（随播放递减）；
 *  - 点了但还没起播（MediaPlayer 缓冲）→ 播放键位置换成环形加载动画。
 */
@Composable
internal fun VoiceBubble(
    url: String,
    durationSec: Int,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    if (url.isBlank()) return
    var playing by remember(url) { mutableStateOf(false) }
    /** 1.224r：已点击、但音频还没准备好（首次点播会有明显延迟） */
    var preparing by remember(url) { mutableStateOf(false) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var mp by remember(url) { mutableStateOf<android.media.MediaPlayer?>(null) }

    DisposableEffect(url) {
        onDispose {
            runCatching { mp?.release() }
            mp = null
        }
    }

    // 播放时推进进度（1/20 秒粒度，够顺滑又不打满主线程）
    LaunchedEffect(playing, url) {
        while (playing) {
            val p = mp
            if (p != null) {
                val dur = runCatching { p.duration }.getOrDefault(0)
                if (dur > 0) progress = (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
            }
            kotlinx.coroutines.delay(50)
        }
    }
    // 进度平滑（避免 50ms 一跳的生硬感）
    val shown by animateFloatAsState(progress, label = "voiceProgress")

    // 波形起伏：只在播放时跑动；暂停即静止在原位
    val wave = rememberInfiniteTransition(label = "voiceWave")
    val phase by wave.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "voicePhase",
    )

    val surface = MaterialTheme.colorScheme.onSurface
    // 1.224r：右侧显示**剩余**时长（初始=总时长，随播放递减；播完回到总时长）
    val remainSec = runCatching {
        ceil(durationSec * (1f - shown).toDouble()).toInt().coerceAtLeast(0)
    }.getOrDefault(durationSec)

    Row(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.14f), RoundedCornerShape(21.dp))
            .clickable {
                if (preparing) return@clickable
                val cur = mp
                if (playing) {
                    runCatching { cur?.pause() }
                    playing = false
                    return@clickable
                }
                if (cur != null) {
                    runCatching { cur.start() }
                    playing = true
                    return@clickable
                }
                val m = android.media.MediaPlayer()
                preparing = true
                runCatching {
                    m.setDataSource(url)
                    m.setOnPreparedListener {
                        preparing = false
                        it.start()
                        playing = true
                    }
                    m.setOnErrorListener { errPlayer, _, _ ->
                        preparing = false
                        playing = false
                        runCatching { errPlayer.release() }
                        mp = null
                        true
                    }
                    m.setOnCompletionListener {
                        playing = false
                        progress = 0f
                        it.seekTo(0)
                    }
                    m.prepareAsync()
                    mp = m
                }.onFailure {
                    preparing = false
                    runCatching { m.release() }
                    mp = null
                }
            }
            .padding(start = 11.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // 播放 / 暂停 / 加载（自绘，不引入图标库）
        Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
            if (preparing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = tint,
                    strokeWidth = 1.8.dp,
                )
            } else {
                Canvas(Modifier.size(14.dp)) {
                    if (playing) {
                        // 暂停：两条圆头竖线
                        val w = size.width * 0.26f
                        val r = w / 2f
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(size.width * 0.10f, 0f),
                            size = Size(w, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                        )
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(size.width * 0.60f, 0f),
                            size = Size(w, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                        )
                    } else {
                        // 播放三角：**实心**填充，但三个尖角用圆角 join 的描边"包一层"，
// 于是既保持实心的块面感，又不会有硬邦邦的刀尖（纯描边会空心，不好看）。
                        val p = Path().apply {
                            moveTo(size.width * 0.18f, 0f)
                            lineTo(size.width * 0.98f, size.height / 2f)
                            lineTo(size.width * 0.18f, size.height)
                            close()
                        }
                        drawPath(path = p, color = tint)
                        drawPath(
                            path = p,
                            color = tint,
                            style = Stroke(
                                width = size.width * 0.26f,
                                join = StrokeJoin.Round,
                                cap = StrokeCap.Round,
                            ),
                        )
                    }
                }
            }
        }
        // 波形柱：12 根，高度按基准表，已播=实色、未播=淡色；播放时整体脉动
        Row(
            Modifier.height(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for (i in WAVE_BARS.indices) {
                val base = WAVE_BARS[i]
                val played = shown * WAVE_BARS.size > i
                val factor = if (playing) {
                    // 相邻柱相位错开，形成向右流动的起伏
                    (0.62f + 0.52f * abs(sin((phase + i * 0.085f) * 2 * PI)).toFloat())
                } else {
                    1f
                }
                Box(
                    Modifier
                        .width(3.dp)
                        .height((base * 24f * factor).dp.coerceAtLeast(3.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(tint.copy(alpha = if (played) 1f else 0.30f)),
                )
            }
        }
        Text(
            if (durationSec > 0) "${remainSec}s" else "",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = surface,
        )
    }
}

/** 波形柱基准高度（0~1，乘以 24dp）——高低错落，接近官方那种"声浪"观感 */
private val WAVE_BARS = floatArrayOf(
    0.28f, 0.52f, 0.38f, 0.82f, 0.98f, 0.46f,
    0.70f, 0.90f, 0.42f, 0.60f, 0.30f, 0.48f,
)