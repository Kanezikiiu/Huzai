package com.java.myapplication.ui.components

/**
 * 虎扑信息流共享 UI 组件（首页 / 专区 / 后续详情页共用）
 * 全部为无状态渲染组件，数据与加载逻辑由调用方页面持有。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.SortTab

/** 话题流的累积状态（支持无限滚动翻页）——首页/专区共用 */
internal data class TopicFeedState(
    val threads: List<HupuThread> = emptyList(),
    val page: Int = 1,
    val totalPages: Int = 1,
    val loadingMore: Boolean = false,
)

/**
 * 评论/回复图片 URL 规范化（1.151）：`//` 补 https；`http://` 升级 https。
 * 虎扑 CDN 回包常给 http://（i1.hoopchina.com.cn 等），Android 9+ 默认禁明文，
 * 直接用原串交给 Coil 会被网络安全策略静默拦截（图片不显示、无报错）。
 * 非 http(s) 协议（content://、file:// 等）原样返回，不误伤本地图片。
 */
internal fun normalizeImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https://" + url.substring(7)
        else -> url
    }
}

/** 封面/队徽 URL 规范化：// 协议相对补 https；http:// 升级 https——
 *  Android 9+ 默认禁止明文 HTTP，赛程接口的 memberLogo/teamLogo 全是 http://，
 *  不升级会导致 Coil 加载被网络安全策略拦截（队徽不显示）。CDN 支持 https（实测 200）。 */
internal fun normalizeCover(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("http://") -> "https://" + url.substring(7)
        url.startsWith("https://") -> url
        else -> null
    }
}

/**
 * 1.190: 小尺寸展示位改用 CDN 缩略图，避免整张原图下载。
 *
 * 实测（i11.hoopchina.com.cn，原图 1256px 宽 / 155KB）：
 * 加 `?x-oss-process=image/resize,w_240` → 14KB；`w_168` → 8.5KB（约 1/11 ~ 1/18）。
 * 列表里几十张原图会把带宽打满、抢走同屏其它请求的时间（资料卡 / 下一页），
 * 这正是「用户发帖多且带图 → 主页骨架屏久、滑动发涩」的主因之一。
 *
 * · 已带 `x-oss-process` 的 URL（如头像的 m_fill 裁切）原样返回，不覆盖已有处理参数
 * · GIF 不缩放（缩放会丢动图）
 * · 非虎扑 CDN 域名原样返回
 *
 * @param widthPx 展示位宽度对应的像素值（dp × density，取略大值即可）
 */
internal fun thumbnailUrl(url: String?, widthPx: Int): String? {
    val u = normalizeCover(url) ?: return null
    if (widthPx <= 0) return u
    val lower = u.lowercase()
    if (lower.contains("x-oss-process=")) return u
    if (lower.contains(".gif")) return u
    if (!lower.contains("hoopchina.com.cn") && !lower.contains("hupucdn.com")) return u
    val sep = if (u.contains("?")) "&" else "?"
    return "$u${sep}x-oss-process=image/resize,w_$widthPx"
}

/**
 * 带英雄角标的头像（MOBA 赛事：选手所选英雄/角色小图叠在头像右下角，官方形态）。
 * heroIcon 为空时退化为普通圆形头像——教练/中立角色/非 MOBA 赛事不显示角标。
 * 角标走网络 URL + Coil（不走 painterResource，避免自适应图标强转 BitmapDrawable 崩溃）。
 */
@Composable
internal fun HeroBadgeAvatar(
    avatar: String?,
    heroIcon: String?,
    size: Dp,
) {
    val badge = size * 0.42f
    val badgeShape = RoundedCornerShape(badge * 0.34f)
    Box(Modifier.size(size)) {
        AsyncImage(
            model = normalizeCover(avatar),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
        if (!heroIcon.isNullOrBlank()) {
            AsyncImage(
                model = normalizeCover(heroIcon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(badge)
                    .clip(badgeShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, badgeShape),
            )
        }
    }
}

/**
 * 点击穿透守卫：挂在盖入式 overlay 根节点 / 常驻页面根节点上。
 * 子树内自身可点（clickable/scroll 等节点优先命中），空白区域由守卫兜底消费——
 * 事件不再落穿到 z 轴下方兄弟（修复：失败态空白点击穿透下层卡片、
 * 排序条空隙穿透导致 SecondaryPage 计数被后台页污染、Tab 栏永久隐藏）。
 */
/**
 * 1.192: 固定频道开关行（首页「热帖」/ 评分页「虎扑评分」）——不参与排序，只有显示/隐藏两态。
 * [enabled] = false 时置灰，并把 [subtitle] 当作说明（用于「至少保留一个频道」的约束）。
 */
@Composable
internal fun FixedChannelRow(
    name: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            // 1.192: 圆角与「自定义首页频道」页的搜索框保持一致（22dp）
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            // 1.192: 整行可点（与「滚动时自动隐藏底栏」等开关行同款手感），
            // 不再只有右侧那个小 Switch 可点——之前点卡片主体没有任何反应
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                fontSize = 11.sp,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

internal fun Modifier.tapGuard(): Modifier =
    pointerInput(Unit) { detectTapGestures { } }

/** 数字缩写：12345 -> 12.3k */
internal fun formatCount(n: Int): String = when {
    n >= 10000 -> String.format("%.1fw", n / 10000f)
    n >= 1000 -> String.format("%.1fk", n / 1000f)
    else -> n.toString()
}

/** 信息流条目：左文右图（无图时纯文字） */
@Composable
internal fun FeedItem(thread: HupuThread, showImage: Boolean, onOpen: (HupuThread) -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen(thread) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                thread.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            thread.desc?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            MetaRow(thread)
        }
        if (showImage) {
            AsyncImage(
                // 1.190: 100×76dp 小图位走 CDN 缩略图（原图 155KB → 约 15KB），避免列表里几十张原图打满带宽
                model = thumbnailUrl(thread.cover, 320),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 100.dp, height = 76.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

@Composable
private fun MetaRow(thread: HupuThread) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MetaText("💡${formatCount(thread.lights)}")
        Spacer(Modifier.width(12.dp))
        MetaText("💬${formatCount(thread.replies)}")
        thread.topic?.name?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.width(12.dp))
            MetaText(it)
        }
        if (thread.createdAtText.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            MetaText(thread.createdAtText)
        }
    }
}

@Composable
private fun MetaText(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 通用帖子流列表（支持无限滚动加载更多；resetKey 变化时回顶） */
@Composable
internal fun FeedList(
    threads: List<HupuThread>,
    useBigCards: Boolean,
    canLoadMore: Boolean = false,
    loadingMore: Boolean = false,
    onLoadMore: (() -> Unit)? = null,
    resetKey: String = "",
    onOpenThread: ((HupuThread) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    // 刷新完成后回顶（仅当前活动列表）
    LaunchedEffect(resetKey) {
        if (resetKey.isNotEmpty() && listState.firstVisibleItemIndex > 0) {
            listState.scrollToItem(0)
        }
    }
    // 滚动接近尾部（最后 5 条内）时自动触发加载
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            canLoadMore && !loadingMore && last >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore?.invoke()
    }
    // 浏览流关键词过滤（我的→浏览流设置）：标题/分区命中 → 条目不显示；
    // 挂 filterVersion 订阅——关键词保存即刻刷新（首页/专区共用本列表）
    val filteredThreads = remember(threads, HupuPrefs.filterVersion) {
        val kw = HupuPrefs.loadFilterKeywords()
        if (kw.isEmpty) threads
        else threads.filterNot { t -> HupuFilter.blockedThread(t.title, t.topic?.name, kw) }
    }
    if (filteredThreads.isEmpty()) {
        // 空态（1.58）：关键词过滤后全灭或无内容——居中提示替代空列表（空 LazyColumn
        // + 140dp bottom padding 会让过度滚动看起来「能滑很高」，实际并无内容）
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("暂无内容", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "可能被浏览流关键词过滤，或该话题暂无帖子",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(filteredThreads, key = { it.tid }) { thread ->
            FeedItem(thread, showImage = useBigCards && normalizeCover(thread.cover) != null, onOpen = onOpenThread ?: {})
        }
        if (canLoadMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/**
 * 排序子 Tab（最新回复 / 最新发布 / 24小时榜）
 *
 * 1.192：由「文字 + 下划线」改为 iOS 分段控件——
 * 玻璃容器（半透明底 + 细描边）+ 等宽分段 + 滑动选中胶囊（spring Q 弹）。
 * 与顶部的话题/赛事玻璃胶囊同源质感，但形态更「轻」，构成二级层级。
 */
@Composable
internal fun SortBar(
    sorts: List<SortTab>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    if (sorts.isEmpty()) return
    val dark = isAppDarkTheme()
    val count = sorts.size
    val idx = sorts.indexOfFirst { it.url == selected }.coerceAtLeast(0)
    val density = LocalDensity.current
    // 1.192b（真机反馈）：圆角增大到「半高 = 胶囊」，不再显方
    val shape = RoundedCornerShape(17.dp)
    val segShape = RoundedCornerShape(14.dp)
    val slide = remember { Animatable(idx.toFloat()) }
    LaunchedEffect(idx) {
        slide.animateTo(
            idx.toFloat(),
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        val segW = maxWidth / count
        val segWpx = with(density) { segW.toPx() }
        val inset = 3.dp
        val insetPx = with(density) { inset.toPx() }
        val barH = 34.dp
        Box(
            Modifier
                .fillMaxWidth()
                .height(barH)
                .clip(shape)
                .background(if (dark) Color.White.copy(0.07f) else Color.Black.copy(0.05f))
                .border(
                    0.6.dp,
                    if (dark) Color.White.copy(0.08f) else Color.White.copy(0.55f),
                    shape,
                ),
        ) {
            // 滑动选中胶囊
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (slide.value * segWpx + insetPx).roundToInt(),
                            insetPx.roundToInt(),
                        )
                    }
                    .width(segW - inset * 2)
                    .height(barH - inset * 2)
                    .clip(segShape)
                    .background(if (dark) Color(0xFF2E2E30).copy(0.96f) else Color.White.copy(0.95f)),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(barH),
            ) {
                sorts.forEach { s ->
                    val active = s.url == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // 1.192b（真机反馈）：去掉默认 ripple 方形遮罩
                            .clickable(
                                interactionSource = null,
                                indication = null,
                            ) { onSelect(s.url) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            s.title,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 胶囊 chip（横滑条条目：文字 + 可选圆形 logo 或矢量图标）
 *
 * 1.192：iOS 玻璃质感重绘——
 * · 选中 = 平面化的近实心玻璃胶囊（浅色 = 白 / 深色 = #2E2E30），无投影、无渐变
 * · 未选 = 极淡半透明胶囊，文字取 onSurfaceVariant
 * · 按压 spring 缩放反馈（0.90 / StiffnessHigh），无 ripple 压暗
 * 尺寸与旧版完全一致（12dp / 7dp 内边距），不改变各页横滑条布局高度。
 * 配色与按压统一走 glassFill / glassBorder / Modifier.glassPress。
 */
/** 1.192f: 玻璃胶囊统一配色 —— 全站唯一定义，新增玻璃元素一律复用 */
internal fun glassFill(dark: Boolean, selected: Boolean = false): Color =
    if (selected) (if (dark) Color(0xFF2E2E30).copy(0.94f) else Color.White.copy(0.92f))
    else (if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f))

internal fun glassBorder(dark: Boolean, selected: Boolean = false): Color =
    if (selected) (if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.05f))
    else (if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f))

/**
 * 1.192f: 玻璃胶囊的通用「按住缩放」反馈 —— 与主页横滑条 Chip 完全同一套
 * （spring + StiffnessHigh，无 ripple）。任何玻璃条/玻璃小按钮都该用它，
 * 避免同一种控件在不同页面出现「压暗」与「缩放」两种手感。
 */
@Composable
internal fun Modifier.glassPress(
    shape: Shape,
    fill: Color,
    border: Color,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "glassPress",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(fill)
        .border(0.6.dp, border, shape)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
internal fun Chip(
    text: String,
    logoUrl: String? = null,
    /** 1.191: 可选矢量图标（如「收藏专区」的星标）；有 logoUrl 时以 logo 优先 */
    leadingIcon: ImageVector? = null,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val dark = isAppDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 1.192d（真机反馈）：按压只用 spring 缩放，不做变暗。
    // 相比最初那版：StiffnessHigh 让按下那一帧就起步（不再是慢起势），
    // 深度加大到 0.90，快速轻点也看得见；松手仍靠 spring 弹性回弹。
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "chipPress",
    )
    val shape = RoundedCornerShape(50)
    // 1.192b（真机反馈）：去掉立体感——不再用投影/上下渐变/白色高光描边，
    // 改成「平铺半透明玻璃块 + 一层极淡描边」，接近 iOS 原生 chip 的扁平观感。
    // 1.192f: 配色收敛到全站唯一的 glassFill / glassBorder
    val bg = glassFill(dark, selected)
    val edge = glassBorder(dark, selected)
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .border(0.6.dp, edge, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        logoUrl?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(20.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(6.dp))
        }
        // 1.191: 无 logo 时可显示矢量图标（与文字同色系）
        if (logoUrl == null && leadingIcon != null) {
            Icon(
                leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** 页面顶部标题栏（各页共用）：状态栏避让 + 左标题 + 右侧 40dp 槽位（保证各页顶部高度一致） */
@Composable
internal fun PageHeader(
    title: String,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        // 高度固定 40dp（保证各页标题栏高度恒等），宽度自适应——右侧可放多个图标不互相挤压
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            trailing()
        }
    }
}

/** 信息流骨架屏（chips 条 + 卡片块） */
@Composable
internal fun SkeletonHome(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        // 模拟话题条
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 88.dp, height = 34.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        repeat(4) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (it % 2 == 0) 120.dp else 84.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

/** 加载失败重试 */
@Composable
internal fun ErrorRetry(modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("加载失败", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("网络不给力，稍后再试", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(
            "重试",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRetry)
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}
