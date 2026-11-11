package com.java.myapplication.ui.components

/**
 * 虎扑信息流共享 UI 组件（首页 / 专区 / 后续详情页共用）
 * 全部为无状态渲染组件，数据与加载逻辑由调用方页面持有。
 */

import androidx.compose.runtime.SideEffect
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.java.myapplication.ui.glass.GlassSwitch
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuBlacklist
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
    // 1.224n：说明文本可为空 —— 为空时**整行只显示名称 + 开关**（不占两行、不留空白）。
    subtitle: String = "",
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
            // 1.224n：说明为空时整行只有名称，不渲染占位空隙
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
        // 1.198：升级为 kyant 液态玻璃开关（原 Material3 Switch）
        GlassSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
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

/** 1.223：信息流「点亮」的金色（真机反馈：金色填充） */
internal val META_LIGHT_GOLD = Color(0xFFF5B301)

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
        // 1.326：**只在带图时才让文本列撑到缩略图高度**，无图卡片保持自然高度。
        //
        // 为什么不统一：卡片等高不是目标（历史上就不是）。
        //  - 有图：卡片行高由固定 100×76 缩略图决定，文本列只占约 60 → meta 会紧贴标题、
        //    下方空一截。这里给列补上 min = 76，配合下面的可扩展间隔把 meta 压到与图底齐平。
        //  - 无图：没有图在撑高度，**不需要**对齐任何东西；若也给 76 下限，反而在标题与
        //    meta 之间塞进一段空白（真机反馈的"无图帖子中间大空隙"就是它）。
        // 注意：一律不能用 fillMaxHeight —— 那会让两行标题把卡片顶高、图片跟着被拉大（1.323 的锅）。
        val textCol = if (showImage) Modifier.weight(1f).heightIn(min = 76.dp)
        else Modifier.weight(1f)
        Column(textCol) {
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
            // 1.330：**按形态分开写**，修「改了数值却看不出变化」。
            //
            // 旧写法是 Spacer(Modifier.weight(1f).height(N.dp))，看起来像"至少 N dp"，
            // 但 weight 默认 fill = true —— Column 会把这个子项的主轴尺寸**强制成它分配到的空间**，
            // 于是 .height(N.dp) 里的数值被吞掉：
            //   · 无图卡片：Column 没有富余空间 → 分到 0 → 间隔恒为 0，改 8/12/16 全都看不出变化
            //   · 有图卡片：分到的是「76 - 内容高度」，本来也不需要这个数值
            // 所以拆成两支：有图靠 weight 吸底（数值无意义），无图用固定高度（数值一定生效）。
            // 1.334：无图间隔 11 → 8dp（真机反馈继续收）
            if (showImage) Spacer(Modifier.weight(1f))
            else Spacer(Modifier.height(8.dp))
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
        MetaCount(HupuIcons.MetaLightFilled, formatCount(thread.lights), tint = META_LIGHT_GOLD)
        Spacer(Modifier.width(12.dp))
        MetaCount(HupuIcons.MetaComment, formatCount(thread.replies))
        // 1.223v: 显示**作者名**（话题流/专区流解析时已带 author，零额外请求）；
        // 用作者位替换原来的专区名 —— 这类流天然隐含专区，行内信息量更合理。
        // 作者缺失时（如搜索结果那类没有 author 的流）回退显示专区名，避免出现空档。
        val who = thread.author?.name?.takeIf { it.isNotBlank() }
            ?: thread.topic?.name?.takeIf { it.isNotBlank() }
        who?.let {
            Spacer(Modifier.width(12.dp))
            MetaText(it)
        }
        if (thread.createdAtText.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            MetaText(thread.createdAtText)
        }
    }
}

/** 1.223：meta 行的小图标 + 计数（细描边图标，与 12sp 灰字同色；可指定 tint） */
@Composable
private fun MetaCount(
    icon: ImageVector,
    text: String,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        MetaText(text)
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
    /** 1.198：顶部内边距 —— 浮空顶栏骨架（FrostedHeaderLayout）把「实测栏高」传进来，
     *  首项才能从栏下开始；不传时保持原样（0dp） */
    topInset: Dp = 0.dp,
    onOpenThread: ((HupuThread) -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    // 刷新完成后回顶（仅当前活动列表）
    //
    // 1.363：之前的 LaunchedEffect 方案仍会"闪一下"——因为它是**副作用**，
    // 在内容替换的那一帧之后才跑；那一帧新内容已经被 lay 在旧滚动位置上，
    // 而且可能越到顶栏区域（真机：「刷新后的一瞬间，卡片内容会在顶栏中闪一下」）。
    //
    // 现在用 SideEffect：它在 apply changes 之后、**绘制之前**同步执行，
    // 于是"请求回顶"与"内容替换"落在同一帧，新内容第一次出画就在顶部。
    // 同时把这一帧标记为 resettingZone，供下面的顶部遮挡使用。
    var resetKeySeen by remember { mutableStateOf("") }
    var resettingZone by remember { mutableStateOf(false) }
    SideEffect {
        if (resetKey != resetKeySeen) {
            resetKeySeen = resetKey
            if (resetKey.isNotEmpty()) {
                // requestScrollToItem 是同步 API（把目标位置交给下一次测量）；
                // 若当前版本没有该 API，则什么也不做 —— 下方 LaunchedEffect 会用挂起的
                // scrollToItem 补齐，保证功能不丢。
                runCatching { listState.requestScrollToItem(0) }
                // 保持一帧遮挡，确保这次复位过程里顶栏区域不会漏出内容
                resettingZone = true
            }
        }
    }
    // 挂起兜底：万一 requestScrollToItem 不存在（老版本 Compose），这里把列表拉回顶部
    LaunchedEffect(resetKey) {
        if (resetKey.isEmpty()) return@LaunchedEffect
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(resettingZone) {
        if (!resettingZone) return@LaunchedEffect
        // 等一帧（布局已按新位置量完）再撤掉遮挡
        kotlinx.coroutines.android.awaitFrame()
        resettingZone = false
    }
    // 滚动接近尾部（最后 5 条内）时自动触发加载。
    //
    // 1.327：改用 snapshotFlow + 滞回，修「偶发继续滑动不触发加载」。
    // 旧写法是 derivedStateOf 出一个 Boolean 再 LaunchedEffect(boolean)：
    //   ① `loadingMore` 本身就参与了这个布尔值。加载结束时它 true→false，会让布尔值
    //      短暂变 true；可此时若手指已经按住（正在滑动），没有任何 value 变化去重算它，
    //      就停在 false 上再也不会重新触发。
    //   ② 旧条件用「可见项最后一条的 index」在条目高矮不一时本身也不稳。
    // 现在改成订阅列表快照：只要「还没到底 + 不在加载」并进入阈值就触发；
    // 额外用 triggered 做滞回（必须离开阈值区才允许下一次），避免一次滑动连发多次请求。
    var loadMoreArmed by remember { androidx.compose.runtime.mutableStateOf(true) }
    LaunchedEffect(listState, canLoadMore, loadingMore) {
        if (!canLoadMore || loadingMore) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && last >= total - 5
        }.collect { nearEnd ->
            if (!nearEnd)
                loadMoreArmed = true
            else if (loadMoreArmed) {
                loadMoreArmed = false
                onLoadMore?.invoke()
            }
        }
    }
    // 浏览流关键词过滤（我的→浏览流设置）：标题/分区命中 → 条目不显示；
    // 挂 filterVersion 订阅——关键词保存即刻刷新（首页/专区共用本列表）
    // 1.221：本地黑名单——被拉黑者发的帖子整条不显示（挂 blacklistVersion 同款即时生效）
    val filteredThreads = remember(threads, HupuPrefs.filterVersion, HupuPrefs.blacklistVersion) {
        val kw = HupuPrefs.loadFilterKeywords()
        val base = if (kw.isEmpty) threads
        else threads.filterNot { t -> HupuFilter.blockedThread(t.title, t.topic?.name, kw) }
        HupuBlacklist.pruneThreads(base, HupuPrefs.loadBlacklist())
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
    // 1.363：整个列表包一层 Box —— 复位期间在上面叠一条与顶栏等高的遮挡，
    // 保证"内容替换 / 回顶 / 遮挡"发生在同一帧，顶栏区域不会漏出卡片内容。
    // 平时（不在复位）这层不存在，所以正常滚动、顶栏渐进模糊都不受影响。
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        // 1.199：上方留白交给横滑条自身的 bottom padding，这里不再叠加（否则空隙过大）
        // 1.198：浮空顶栏模式下 topInset = 实测栏高（含横滑条的 bottom padding），首项从栏下开始
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = topInset, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(filteredThreads, key = { it.tid }) { thread ->
            FeedItem(thread, showImage = useBigCards && normalizeCover(thread.cover) != null, onOpen = onOpenThread ?: {})
        }
        // 1.324：只在**真正在加载**时显示转圈。
        // 之前是 `if (canLoadMore)`——分页流只要有下一页这个圈就常驻，看起来像"一直转圈"。
        if (canLoadMore && loadingMore) {
            item(key = "loading-more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
        if (resettingZone) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(topInset)
                    .background(MaterialTheme.colorScheme.background),
            )
        }
    }
}

/**
 * 1.2xx：排序条（非紧凑模式）的**总高度** = 40dp 条高 + 6dp 底部呼吸。
 * 用于「排序 tab 还没返回」时占位 —— 否则整行消失会让下方列表上移，tab 到位后再弹回来。
 */
internal val SORT_BAR_TOTAL_HEIGHT = 46.dp

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
    /**
     * 1.201：每段固定宽度（紧凑模式）。必须放在 onSelect 之前 ——
     * 现有调用方用尾随 lambda 传 onSelect，插在其后会把 lambda 当成这个参数。
     * - null = 等分填满整行（列表排序条：首页 / 专区 / 搜索）
     * - 给定 = 宽度由「段数 × segWidth」决定（帖子详情页「回复数」右侧）
     */
    segWidth: Dp? = null,
    /**
     * 1.224：整条左右内边距。默认 16dp（页面级排序条，如首页 / 专区）。
     * 嵌入「已有自身 16dp 内容边距」的卡片流时传 0，避免与容器 padding 叠加错位。
     */
    hPad: Dp = 16.dp,
    /** 1.224：整条底部呼吸。默认 6dp；嵌入列表项时传 0，由容器 spacing 负责间距。 */
    bottomPad: Dp = 6.dp,
    onSelect: (String) -> Unit,
) {
    if (sorts.isEmpty()) return
    val dark = isAppDarkTheme()
    // 1.198g：二级 tab（排序条）与横滑条同源 —— 开启渐进模糊时走「底色上合成的不透明色」
    val count = sorts.size
    val idx = sorts.indexOfFirst { it.url == selected }.coerceAtLeast(0)
    val density = LocalDensity.current
    // 1.192b（真机反馈）：圆角增大到「半高 = 胶囊」，不再显方
    // 1.197（真机反馈）：条高 34 → 38dp（「有点细长」），圆角同步跟随半高
    // 1.309：38dp 真机上显得细长，恢复 40dp（圆角随半高 = 20dp）
    val shape = RoundedCornerShape(20.dp)
    val segShape = RoundedCornerShape(17.dp)
    val slide = remember { Animatable(idx.toFloat()) }
    LaunchedEffect(idx) {
        slide.animateTo(
            idx.toFloat(),
            spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
        )
    }
    BoxWithConstraints(
        // 1.201：紧凑模式（segWidth 给定）宽度由内容决定，不加左右留白与底部呼吸
        if (segWidth == null) {
            Modifier
                .fillMaxWidth()
                // 1.197（真机反馈）：下方留一点呼吸空间（1.224：可由调用方覆盖）
                .padding(start = hPad, end = hPad, bottom = bottomPad)
        } else {
            Modifier
        },
    ) {
        val segW = segWidth ?: (maxWidth / count)
        val segWpx = with(density) { segW.toPx() }
        // 1.201：紧凑模式下容器与内容等宽（段数 × 段宽）
        val barWidthMod =
            if (segWidth == null) Modifier.fillMaxWidth() else Modifier.width(segW * count)
        val inset = 3.dp
        val insetPx = with(density) { inset.toPx() }
        // 1.197（真机反馈）：34 → 38dp，避免二级 tab 显得细长
        val barH = 40.dp
        Box(
            Modifier
                .then(barWidthMod)
                .height(barH)
                .clip(shape)
                .background(chipGlassFill(dark, false))
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
                    .background(chipGlassFill(dark, true)),
            )
            Row(
                Modifier
                    .then(barWidthMod)
                    .height(barH),
            ) {
                sorts.forEach { s ->
                    val active = s.url == selected
                    // P3：段基座在 RowScope 里先算好（key 的内容 lambda 不是 RowScope，不能直接用 weight）
                    val segBase = Modifier.weight(1f).fillMaxHeight()
                    // P3：用 url 作 key —— 段数变化时 remember 槽位不漂移
                    androidx.compose.runtime.key(s.url) {
                        // 1.223：每段按压走「立即触发」（Initial pass），在可滚动容器里也不延迟
                        val (segTrigger, segPress) = rememberInstantPressScale()
                        Box(
                            segBase
                                .then(segTrigger)
                                .pressScale(segPress)
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
}


/**
 * 胶囊 chip（横滑条条目：文字 + 可选圆形 logo 或矢量图标）
 *
 * 1.192：iOS 玻璃质感重绘——
 * · 选中 = 实心玻璃胶囊（浅色 = 白 / 深色 = #2E2E30）+ 极轻浮起投影，无渐变
 * · 未选 = 极淡半透明胶囊，文字取 onSurfaceVariant
 * · 按压 spring 缩放反馈（0.90 / StiffnessHigh），无 ripple 压暗
 * 尺寸与旧版完全一致（12dp / 7dp 内边距），不改变各页横滑条布局高度。
 * 配色与按压统一走 glassFill / glassBorder / Modifier.glassPress。
 */
/** 1.192f: 玻璃胶囊统一配色 —— 全站唯一定义，新增玻璃元素一律复用
 *
 *  1.198f：新增 [boost] —— 开启「顶栏渐进模糊」时，把话题条/排序条这些横滑条胶囊的
 *  **不透明度提上去**。原因是渐进模糊在栏位下半部本就偏淡（保留质感），
 *  而 chips 恰好坐在那里，若自身仍是极淡半透明，就会和背后滚过的内容糊在一起。
 *  按用户建议「提高控件自身不透明度」来保证可读性，而不是把顶栏压成实心。 */
internal fun glassFill(dark: Boolean, selected: Boolean = false, boost: Float = 1f): Color =
    if (selected) (if (dark) Color(0xFF2E2E30).copy(0.94f) else Color.White.copy(0.92f)).boostAlpha(boost)
    else (if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f)).boostAlpha(boost)

/** alpha 放大并封顶到 1（[boost] = 1 时原样返回） */
internal fun Color.boostAlpha(boost: Float): Color =
    if (boost == 1f) this else copy(alpha = (alpha * boost).coerceAtMost(1f))

/**
 * 1.198g：横滑条 / 排序条胶囊是否要「实心化」。
 *
 * 1.198f 曾用「提高同一个半透明色的 alpha」来改善可读性，真机反馈很准确：
 * **那只是越来越黑，依旧透**（背后滚过的内容照样看得见、照样抢可读性）。
 * 现在改为：[chipGlassFill] / [chipGlassBorder] 在开启时把半透明的玻璃色
 * **在页面底色上合成成一个不透明色** —— 静止时与原来的观感完全一致，
 * 但内容从栏下滚过时不再透出来。关闭该效果时一切照旧。
 */
@Composable
internal fun chipBarOpaque(): Boolean {
    val v = HupuPrefs.progressiveHeaderVersion
    return remember(v) { HupuPrefs.loadProgressiveHeader() }
}

/**
 * 1.198h：开启渐进模糊时，把半透明玻璃色换成「**在页面底色上合成后、再保留一点点透明**」的色：
 *
 *   · 先合成：`lerp(页面底色, 玻璃色, 原 alpha)` —— 结果与「静止时原来那层半透明叠在底色上」一致；
 *   · 再回透：乘上 [CHIP_SOLID_ALPHA]（0.94）—— 既留住一点玻璃的通透感，
 *     又只剩 6% 的透出，背后滚过的内容不会再干扰 chips 的可读性。
 *
 * 关闭该效果（[chipBarOpaque] = false）时原样返回，观感一像素不变。
 */
@Composable
private fun Color.solidOverPageBackground(): Color {
    if (!chipBarOpaque() || alpha >= 1f) return this
    val composited = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.background,
        Color(red, green, blue, 1f),
        alpha,
    )
    return composited.copy(alpha = CHIP_SOLID_ALPHA)
}

/** 合成后保留的不透明度：越大越实。0.94 = 只剩 6% 透出 */
private const val CHIP_SOLID_ALPHA = 0.94f

/** chip 底色：开启渐进模糊时是不透明色（见 [chipBarOpaque]） */
@Composable
internal fun chipGlassFill(dark: Boolean, selected: Boolean): Color =
    glassFill(dark, selected).solidOverPageBackground()

/** chip 描边：同上 */
@Composable
internal fun chipGlassBorder(dark: Boolean, selected: Boolean): Color =
    glassBorder(dark, selected).solidOverPageBackground()

internal fun glassBorder(dark: Boolean, selected: Boolean = false, boost: Float = 1f): Color =
    if (selected) (if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.05f)).boostAlpha(boost)
    else (if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f)).boostAlpha(boost)

/**
 * 1.223：**立即**按压缩放 —— 返回 (触发按压的 Modifier, 缩放值)。
 *
 * 为什么要单独一套：在可滚动容器（`LazyRow` / `LazyColumn`）里，`clickable` 的按压反馈会被
 * 父级滚动手势的判定推迟——表现为「顶部横滑条要按住一会儿才缩」，而普通 `Row` 里的 chip
 * 按下当帧就缩。这里改为在 **Initial pass** 直接捕获 down，按下当帧即触发缩放，
 * 与容器是否可滚动无关，全站手感一致。
 */
@Composable
internal fun rememberInstantPressScale(): Pair<Modifier, Float> {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "instantPressScale",
    )
    val trigger = Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            pressed = true
            waitForUpOrCancellation(pass = PointerEventPass.Initial)
            pressed = false
        }
    }
    return trigger to scale
}

/**
 * 1.223：按住 spring 缩放的手感值 —— 与主页横滑条 Chip 完全同源
 * （MediumBouncy + StiffnessHigh，按下 0.90，无 ripple）。
 *
 * 这是全站「按压缩放」的唯一定义：tab / chip / 玻璃小按钮一律复用它，
 * 配合 [Modifier.pressScale] + 提供同一 interaction 的 clickable 使用。
 */
@Composable
internal fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh,
        ),
        label = "pressScale",
    ).value
}

/** 1.223：应用按住缩放（只做手感，不绘制任何底色/描边）。 */
internal fun Modifier.pressScale(scale: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

/**
 * 1.192f: 玻璃胶囊的通用「玻璃底 + 按住缩放」整体 ——
 * 配色走 glassFill / glassBorder，手感走 rememberPressScale。
 */
@Composable
internal fun Modifier.glassPress(
    shape: Shape,
    fill: Color,
    border: Color,
    onClick: () -> Unit,
): Modifier {
    // 1.223：同样走「立即触发」——用户主页等含滚动的页面里也不延迟
    val (trigger, scale) = rememberInstantPressScale()
    return this
        .then(trigger)
        .pressScale(scale)
        .clip(shape)
        .background(fill)
        .border(0.6.dp, border, shape)
        .clickable(interactionSource = null, indication = null, onClick = onClick)
}

/**
 * 1.198：只裁水平方向，垂直方向放行。
 * 横滑条的 chip 带浮起阴影，`clipToBounds` 把上下两端的阴影一起裁掉了；
 * 这里只裁掉「滑出视野的内容」，纵向留出空间让阴影完整显示。
 */
internal fun Modifier.clipHorizontally(): Modifier = this.drawWithContent {
    val content = this
    clipRect(left = 0f, top = -size.height, right = size.width, bottom = size.height * 2f) {
        content.drawContent()
    }
}

// ---------- 1.223 横滑条「裁剪窗」几何（全站单一真源） ----------

/** 横滑条内容相对屏幕左右边缘的留白（与页面其它内容左缘保持一致）。 */
private val CHIP_BAR_EDGE = 16.dp

/**
 * 裁剪窗相对屏幕边缘的内缩量。**越小，留给 chip 浮起阴影的槽越宽**（槽宽 = EDGE − 本值）。
 *
 * 旧值 8dp：槽只有 8dp，而阴影半径 ≈ elevation(8dp) + 模糊 ≈ 10~12dp →
 * 首个/末个 chip 的外侧阴影被裁出一条**竖直硬边**（真机反馈「阴影缺失」）。
 * 取 2dp：槽宽 14dp，阴影完整；裁剪边界几乎贴着屏幕，视觉上与「全宽滚动」一致。
 */
private val CHIP_BAR_CLIP_INSET = 2.dp

/**
 * 横滑条统一裁剪窗（首页话题条 / 专区分类条 / 评分频道条 / 赛事二级分类条 /
 * 频道自定义页 / 搜索页历史条 等共用）。
 *
 * 只裁水平方向（纵向放行，见 [clipHorizontally]），并且让「裁掉越界内容」的边界
 * 停在 [CHIP_BAR_CLIP_INSET]（几乎贴屏），从而给 chip 的浮起阴影留出
 * `EDGE − CLIP_INSET` 的完整空间——不再被裁出硬边，同时内容左缘仍与页面其它内容对齐。
 */
internal fun Modifier.chipBarClip(): Modifier = this
    .padding(horizontal = CHIP_BAR_CLIP_INSET)
    .clipHorizontally()
    .padding(horizontal = CHIP_BAR_EDGE - CHIP_BAR_CLIP_INSET)

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
    // 1.223：按压反馈改走「立即触发」（Initial pass 捕获 down）——
    // 在 LazyRow 等可滚动容器里 clickable 的 press 会被滚动手势推迟，
    // 顶部横滑条曾表现为「按住一会儿才缩」。详见 rememberInstantPressScale。
    val (pressTrigger, scale) = rememberInstantPressScale()
    val shape = RoundedCornerShape(50)
    // 1.192b（真机反馈）：去掉立体感——不再用投影/上下渐变/白色高光描边，
    // 改成「平铺半透明玻璃块 + 一层极淡描边」，接近 iOS 原生 chip 的扁平观感。
    // 1.192f: 配色收敛到全站唯一的 glassFill / glassBorder
    val bg = chipGlassFill(dark, selected)
    // 1.194（方案 A）：选中态不再描边，改为极轻投影「浮起」；未选保持原样。
    val edge = if (selected) Color.Transparent else chipGlassBorder(dark, false)
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            // 1.196：shadow 提到最外层 —— 它自身就是一层 graphicsLayer，之前被
            // graphicsLayer(scale) 包在内部，系统阴影可能根本不绘制（「怎么调都看不到」的最大嫌疑）。
            // 强度按真机可见量级给：未选 5dp / 选中 8dp。
            .shadow(
                elevation = if (selected) 8.dp else 5.dp,
                shape = shape,
                clip = false,
                ambientColor = Color.Black.copy(if (selected) 0.40f else 0.28f),
                spotColor = Color.Black.copy(if (selected) 0.40f else 0.28f),
            )
            .then(pressTrigger)
            .pressScale(scale)
            .clip(shape)
            .background(bg)
            .border(0.6.dp, edge, shape)
            .clickable(interactionSource = null, indication = null, onClick = onClick)
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

/**
 * 页面顶部标题栏（各页共用）：状态栏避让 + 左标题 + 右侧 40dp 槽位（保证各页顶部高度一致）。
 *
 * 1.223：标题与右侧内容统一按**竖直中心**对齐——「首页 / 专区 / 评分 / 我的」四页共用本组件，
 * 「我的」页右侧两个图标同样跟随。整行内容再上移 [PAGE_HEADER_LIFT]，使标题与右侧按钮
 * 的竖直中心一致并略高于栏位中线（真机反馈）；上下留白对调，栏位总高保持不变。
 */
private val PAGE_HEADER_LIFT = 5.dp

@Composable
internal fun PageHeader(
    title: String,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = 20.dp,
                end = 16.dp,
                top = 10.dp - PAGE_HEADER_LIFT,
                bottom = 6.dp + PAGE_HEADER_LIFT,
            ),
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

/** 骨架块：**静态**（1.2xx 真机反馈最终定稿）。
 *
 * 演进：扫光（油腻）→ 同相位呼吸（看不出）→ 错峰呼吸（仍不理想）→ **静态**。
 * 结论：骨架屏不需要动效，靠「块本身」传达"内容在路上"即可；
 * 真正让加载态显得精致的是**形状贴近真实内容**与**颜色层次**，而不是动画。
 * 保留这个 Modifier 便于以后统一改（当前就是一块 surfaceVariant 圆角色块）。 */
@Composable
internal fun Modifier.skeletonBlock(shape: Shape = RoundedCornerShape(20.dp)): Modifier =
    this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)

/** 信息流骨架屏（chips 条 + 卡片块）——静态骨架 + 正中的弹跳篮球 */
@Composable
internal fun SkeletonHome(modifier: Modifier = Modifier) {
    // 1.314：骨架升级 —— 不再是几块大灰块，而是**按真实信息流卡片**逐行示意：
    // 标题两行 / 摘要一行 / 元信息三段 / 隔一张带缩略图。
    Column(
        modifier.fillMaxSize().statusBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        // 模拟话题横滑条
        Row(
            Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 88.dp, height = 34.dp)
                        .skeletonBlock(RoundedCornerShape(50))
                )
            }
        }
        SkeletonFeedCards(topPadding = 0.dp, horizontalPad = 16.dp, count = 4)
    }
}

/**
 * 1.314：**信息流卡片骨架**（列表通用）。
 *
 * 结构对齐真实卡片：surface 底 + 20dp 圆角 + 14dp 内边距；
 * 左侧标题两行（第二行更短）→ 摘要 → 元信息三段；右侧 84dp 缩略图（隔一张有封面）。
 */
@Composable
internal fun SkeletonFeedCards(
    topPadding: Dp,
    horizontalPad: Dp = 16.dp,
    count: Int = 4,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = horizontalPad),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) { i -> SkeletonFeedCard(hasCover = i % 2 == 0) }
    }
}

@Composable
internal fun SkeletonFeedCard(hasCover: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.92f)
                    .height(16.dp)
                    .skeletonBlock(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.58f)
                    .height(16.dp)
                    .skeletonBlock(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(11.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.78f)
                    .height(12.dp)
                    .skeletonBlock(RoundedCornerShape(6.dp)),
            )
            Spacer(Modifier.height(13.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonMetaBar(22.dp)
                SkeletonMetaBar(38.dp)
                SkeletonMetaBar(52.dp)
            }
        }
        if (hasCover) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(84.dp)
                    .skeletonBlock(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
internal fun SkeletonMetaBar(w: Dp) {
    Box(
        Modifier
            .width(w)
            .height(10.dp)
            .skeletonBlock(RoundedCornerShape(5.dp)),
    )
}

/**
 * 1.314：**详情页骨架**（帖子 / 比赛 / 选手详情这类"标题 + 正文 + 评论"的页面）。
 *
 * 之前这些页面也借用 [SkeletonHome]，于是会出现「假的横滑条」与「卡片流」——
 * 与详情页真实结构完全不搭。这里按详情页画：
 * 标题两行 → 作者行 → 正文若干行 → 分隔 → 评论条目（头像 + 两行文字）。
 */
@Composable
internal fun SkeletonDetail(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(52.dp)) // 让开浮空顶栏
        // 标题两行
        Box(
            Modifier
                .fillMaxWidth(0.95f)
                .height(20.dp)
                .skeletonBlock(RoundedCornerShape(6.dp))
        )
        Box(
            Modifier
                .fillMaxWidth(0.62f)
                .height(20.dp)
                .skeletonBlock(RoundedCornerShape(6.dp))
        )
        // 作者行：头像 + 昵称 + 时间
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(30.dp)
                    .skeletonBlock(RoundedCornerShape(50))
            )
            Spacer(Modifier.width(8.dp))
            SkeletonMetaBar(64.dp)
            Spacer(Modifier.width(8.dp))
            SkeletonMetaBar(40.dp)
        }
        Spacer(Modifier.height(4.dp))
        // 正文
        repeat(4) { i ->
            Box(
                Modifier
                    .fillMaxWidth(if (i == 3) 0.55f else 1f)
                    .height(14.dp)
                    .skeletonBlock(RoundedCornerShape(6.dp))
            )
        }
        Spacer(Modifier.height(14.dp))
        // 评论条目 × 3
        repeat(3) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(34.dp)
                        .skeletonBlock(RoundedCornerShape(50))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.35f)
                            .height(12.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(12.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}


/**
 * 1.315：**评分列表骨架**（虎扑评分首页）。
 *
 * 真实结构：一张横向大卡（封面 + 标题 + 副标题 + 右侧分数徽章），
 * 之前借用 SkeletonHome 会出现「假话题条 + 信息流卡」，既不像、又偏下。
 */
@Composable
internal fun SkeletonScoreCards(topPadding: Dp, count: Int = 4) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .skeletonBlock(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(15.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(12.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(44.dp)
                        .skeletonBlock(RoundedCornerShape(50))
                )
            }
        }
    }
}

/**
 * 1.315：**赛程列表骨架**（评分页赛事频道）。
 *
 * 真实结构：日期小标题 + 若干「比赛卡」（左边队名两行 / 中间比分 / 右边队名两行）。
 */
@Composable
internal fun SkeletonSchedule(topPadding: Dp) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 日期标题
        Box(
            Modifier
                .width(72.dp)
                .height(13.dp)
                .skeletonBlock(RoundedCornerShape(6.dp))
        )
        repeat(4) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(13.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(13.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                }
                Box(
                    Modifier
                        .width(46.dp)
                        .height(16.dp)
                        .skeletonBlock(RoundedCornerShape(6.dp))
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.72f)
                            .height(13.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(13.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                }
            }
        }
    }
}


/**
 * 1.316：**列表行骨架**（用于"顶栏/记分板已经画好、只差列表"的页面，例如比赛详情的队员列表）。
 *
 * 这类页面的顶栏、记分板、分类条都在**流里**已经渲染完毕，内容区只差列表本身；
 * 之前用 [SkeletonDetail]（自带 statusBarsPadding + 52dp 让位 + 标题段）会整体偏下。
 * 这里只画「头像 + 两行文字 + 右侧数值」的行，位置天然正确。
 */
@Composable
internal fun SkeletonRosterRows(count: Int = 8) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .skeletonBlock(RoundedCornerShape(50))
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.42f)
                            .height(12.dp)
                            .skeletonBlock(RoundedCornerShape(6.dp))
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.26f)
                            .height(10.dp)
                            .skeletonBlock(RoundedCornerShape(5.dp))
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .width(30.dp)
                        .height(12.dp)
                        .skeletonBlock(RoundedCornerShape(6.dp))
                )
            }
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
