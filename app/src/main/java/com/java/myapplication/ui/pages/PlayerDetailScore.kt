package com.java.myapplication.ui.pages

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.aspectRatio
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
import com.java.myapplication.ui.components.stickerImageModel
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
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
import com.java.myapplication.ui.glass.LiquidGlassCard
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.kyant.backdrop.Backdrop

// 1.64 打分面板：半透明遮罩 + 居中卡片（iOS 风格：圆角 24dp、标题正文居左、取消灰/确认蓝）
// 五星制（每星 2 分）；已打分时可修改或取消评分（取消需确认，官方语义：user/record/comment/delete）
@Composable
internal fun ScorePanelOverlay(
    backdrop: Backdrop,
    myScore: Int,
    submitting: Boolean,
    /** 1.153: 打分随带评论（官方打分弹窗即一体流）——comment 为空表示只打分 */
    onSubmit: (Int, String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val isLight = !isAppDarkTheme()
    var picked by remember { mutableStateOf(if (myScore > 0) (myScore / 2).coerceIn(1, 5) else 0) }  // 星数 1..5（每星 2 分）；历史奇数分向下取整，重选即修正
    // 1.153: 随评分一起提交的评论（选填）
    var comment by remember { mutableStateOf("") }
    // 面板内取消评分二次确认（删除类操作不可逆）
    var confirmDelete by remember { mutableStateOf(false) }
    // 1.192: 旧版「半透明遮罩 + 纯色居中卡」改为新弹窗（Liquid Glass 毛玻璃 + Q 弹出入场）。
    // 毛玻璃面板不能包在 Dialog() 里，且必须是「内容记录层」之后的兄弟节点
    // （宿主 PlayerDetailPage 已给内容层挂 .layerBackdrop(backdrop)）。
    LiquidGlassCard(
        backdrop = backdrop,
        // 1.153: 面板内有评论输入 → 需要键盘避让，否则键盘会盖住确认按钮
        modifier = Modifier.imePadding(),
        // 提交中不允许关闭（点遮罩 / 系统返回都无效）
        dismissible = !submitting,
        onDismiss = { onClose() },
    ) { close ->
        // 系统返回：关闭面板（盖过宿主与楼中楼的返回手势）
        androidx.activity.compose.PredictiveBackHandler { events ->
            events.collect { }
            close()
        }
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "给 TA 打分",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (myScore > 0) "已打 ${myScore} 分，可修改或取消" else "五星制，每星 2 分，打分后可修改",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 分值选择：五星制，每星 2 分（官方 starCount=(score)/2，仅偶数分对外显示）
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                for (i in 1..5) {
                    Icon(
                        Icons.Rounded.Star,
                        contentDescription = null,
                        tint = if (picked >= i) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable(enabled = !submitting) { picked = i },
                    )
                }
            }
            // 1.153: 随评分一起提交的评论（选填）——官方打分弹窗本就是「打分 + 评论」一体流
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    // 1.192: 毛玻璃上用 onSurface 淡底，避免「看起来什么都没有」
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 500) comment = it },
                    enabled = !submitting,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 3,
                    minLines = 1,
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            inner()
                            if (comment.isEmpty()) {
                                Text(
                                    "说点什么…（选填，随评分一起发布）",
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            // 操作按钮：取消（浅灰）/ 确认（蓝）；已打分时额外提供取消评分
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        // 1.192: 按钮形态对齐新弹窗（胶囊 + onSurface 淡底）
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = if (isLight) 0.12f else 0.16f))
                        .clickable(enabled = !submitting) { close() }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("取消", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    Modifier
                        .weight(1f)
                        // 1.192: 与取消同款胶囊
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = !submitting && picked > 0) { onSubmit(picked * 2, comment.trim()) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (submitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("确认", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            // 已打分 → 取消评分（点击原地变红二次确认；上方已有取消按钮兜底，无需"再想想"）
            if (myScore > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // 1.192: 与新弹窗按钮同款胶囊 + 淡底（确认态保持红色警示）
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (confirmDelete) Color(0xFFE53935).copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
                        )
                        .clickable(enabled = !submitting) {
                            if (confirmDelete) onDelete() else confirmDelete = true
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (confirmDelete) "确认取消评分" else "取消评分",
                        fontSize = 14.sp,
                        color = if (confirmDelete) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ---------- 1.146 评分评论回复框扩展（对齐帖子回复框能力） ----------

/** 发送可用：文本非空或图片非空，且不在上传/发送中 */
internal fun canSendScoreReply(
    text: String,
    images: List<android.net.Uri>,
    sending: Boolean,
    uploading: Boolean,
): Boolean =
    !sending && !uploading && (text.isNotBlank() || images.isNotEmpty())

/** 1.146: 表情面板 tab 胶囊（帖子回复框 EmojiTabChip 同款） */
@Composable
internal fun ScoreEmojiTabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent,
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** 1.146: 我的表情（收藏表情包）网格：点 = 插入/上传，长按 = 删除（帖子 StickerPane 同款） */
@Composable
internal fun ScoreStickerPane(
    stickers: List<HupuSticker>,
    onClick: (HupuSticker) -> Unit,
    onLongClick: (HupuSticker) -> Unit,
    bottomPad: Dp,
) {
    if (stickers.isEmpty()) {
        // 1.165: 空态也保留首位「+」（本地添加是唯一入口）
        Column(
            Modifier.fillMaxSize().padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StickerAddCell()
            Spacer(Modifier.height(12.dp))
            Text(
                "还没有表情\n点「+」添加本地图片，或长按评论区的表情收藏",
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = bottomPad),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1.165: 首位「+」添加本地图片（微信式）
        item(key = "__add_sticker__") { StickerAddCell() }
        gridItems(stickers, key = { it.url }) { s ->
            AsyncImage(
                // 1.191: 走带 Referer 的加载模型（收藏的 adoutu 表情有防盗链）
                model = stickerImageModel(s.url),
                contentDescription = null,
                modifier = Modifier
                    // 1.192: 同上——填满格子并保持正方形，表情更大更清晰
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { onClick(s) },
                        onLongClick = { onLongClick(s) },
                    ),
            )
        }
    }
}
