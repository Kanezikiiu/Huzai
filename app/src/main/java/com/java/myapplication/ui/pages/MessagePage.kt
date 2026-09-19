package com.java.myapplication.ui.pages

import android.text.format.DateUtils
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuMsgBadge
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.data.HupuApi
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 1.97 消息中心：提到我的 / 评论 / 亮了-推荐（去掉官方「动态」）。
 * 数据源 my.hupu.com/message 页（tabKey 1/2/3）对应接口：
 *   getMentionedRemindList?plate=2&pageStr=
 *   getReplyRemindList?plat=2&pageStr=
 *   getLightRemindList?plat=2&pageStr=
 * 走 bbs.hupu.com/pcmapi/pc/space/v1 同端点（实测同源同参，需登录 cookie）。
 * 分页：pageStr 首页传空串，翻页回传上一页 data.pageStr，hasNextPage 判断穷尽。
 * 主页顶部三圆形入口点击各自盖入子页面；三按钮下方区域留给后续私信列表。
 */

/** 通知类型 */
internal const val NOTICE_MENTION = 1
internal const val NOTICE_REPLY = 2
internal const val NOTICE_LIGHT = 3
// 1.179: 三入口图标色改为跟随色彩主题（原为硬编码品牌蓝，导致切主题时不跟随）


internal fun noticeEndpoint(kind: Int): String = when (kind) {
    NOTICE_MENTION -> "getMentionedRemindList?plate=2&pageStr="
    NOTICE_REPLY -> "getReplyRemindList?plat=2&pageStr="
    else -> "getLightRemindList?plat=2&pageStr="
}

/** 统一通知条目模型 */
data class HupuNotice(
    val kind: Int,
    val user: String,
    val avatar: String?,
    val content: String,      // 通知正文（提到的内容 / 回复内容 / 帖子内容）
    val threadTitle: String,  // 帖子标题
    val tid: String,
    val pid: String,
    val timeText: String,
    val extra: String = "",   // 附加信息（如点亮数）
)

/** 解析一页通知。返回 (列表, 下一页 pageStr, hasNext)；失败返回 null */
internal fun parseNoticePage(json: String, kind: Int): Triple<List<HupuNotice>, String, Boolean>? {
    return try {
        val root = JSONObject(json)
        if (root.optInt("code", 0) != 1) return null
        val data = root.optJSONObject("data") ?: return null
        // 1.98: 数据可能在 newList（新消息）或 hisList（历史）任一/两者——合并去重
        val list = mutableListOf<HupuNotice>()
        for (key in listOf("newList", "hisList")) {
            val arr = data.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                parseNoticeItem(arr.optJSONObject(i) ?: continue, kind)?.let { list.add(it) }
            }
        }
        Triple(list, data.optString("pageStr", ""), data.optBoolean("hasNextPage", false))
    } catch (e: Exception) {
        null
    }
}

private fun parseNoticeItem(o: JSONObject, kind: Int): HupuNotice? {
    return when (kind) {
        NOTICE_LIGHT -> {
            // 亮了/推荐：{title,url,lightNum,lastTime,post:{tid,pid,username,content,header}}
            val post = o.optJSONObject("post")
            val url = o.optString("url", "")
            val tid = post?.optString("tid", "")?.takeIf { it.isNotEmpty() && it != "null" }
                ?: (Regex("(\\d+)\\.html").find(url)?.groupValues ?: emptyList()).getOrNull(0)?.trimEnd('.', 'h', 't', 'm', 'l') ?: ""
            val pid = post?.optString("pid", "")?.takeIf { it.isNotEmpty() && it != "null" }
                ?: url.substringAfter('#', "").takeIf { it.isNotEmpty() } ?: ""
            if (tid.isEmpty()) return null
            val last = o.optLong("lastTime", 0L)
            HupuNotice(
                kind = kind,
                user = post?.optString("username", "")?.takeIf { it.isNotEmpty() && it != "null" } ?: "",
                avatar = post?.optString("header", "")?.takeIf { it.isNotEmpty() && it != "null" },
                content = post?.optString("content", "")?.takeIf { it.isNotEmpty() && it != "null" } ?: "",
                threadTitle = o.optString("title", ""),
                tid = tid,
                pid = pid,
                timeText = if (last > 0) DateUtils.getRelativeTimeSpanString(
                    last * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString() else "",
                extra = "点亮 ${o.optInt("lightNum", o.optInt("number", 0))}",
            )
        }
        else -> {
            // 提到/评论：{username,headerUrl,postContent,threadTitle,tid,pid,publishTime}
            val tid = o.optString("tid", "").takeIf { it.isNotEmpty() && it != "null" } ?: return null
            HupuNotice(
                kind = kind,
                user = o.optString("username", "").takeIf { it.isNotEmpty() && it != "null" } ?: "",
                avatar = o.optString("headerUrl", "").takeIf { it.isNotEmpty() && it != "null" },
                content = o.optString("postContent", "").takeIf { it.isNotEmpty() && it != "null" } ?: "",
                threadTitle = o.optString("threadTitle", "").takeIf { it.isNotEmpty() && it != "null" } ?: "",
                tid = tid,
                pid = o.optString("pid", "").takeIf { it.isNotEmpty() && it != "null" } ?: "",
                timeText = o.optString("publishTime", "").takeIf { it.isNotEmpty() && it != "null" } ?: "",
            )
        }
    }
}

/** 消息中心主页：三个圆形入口（子页面盖入），下方区域留给私信列表 */
@Composable
fun MessageCenterPage(onClose: () -> Unit) {
    val progress = remember { Animatable(0f) }
    DisposableEffect(Unit) {
        SecondaryPage.enter()
        onDispose { SecondaryPage.exit() }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
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

    // 三个子页面：epoch 键控盖入
    var openedKind by remember { mutableStateOf(0) }
    var listEpoch by remember { mutableIntStateOf(0) }
    // 私信聊天页
    var openedConv by remember { mutableStateOf<PmConversation?>(null) }
    var chatEpoch by remember { mutableIntStateOf(0) }
    // 1.102: 私信刷新信号（外层下拉 / 聊天页关闭后同步最新一条消息）
    var pmRefreshTick by remember { mutableIntStateOf(0) }
    var pmRefreshing by remember { mutableStateOf(false) }
    // 1.104: 聊天页点开的对方用户主页
    var chatAuthorEuid by remember { mutableStateOf<String?>(null) }
    var chatProfileEpoch by remember { mutableIntStateOf(0) }

    // 1.102: 整页外层下拉刷新（刷新私信会话列表；外层包 Box 使手势不被 LazyColumn 吞掉）
    val pmPullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        state = pmPullState,
        isRefreshing = pmRefreshing,
        onRefresh = {
            if (pmRefreshing) return@PullToRefreshBox
            pmRefreshing = true
            pmRefreshTick++
        },
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(1f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        // 1.101: 改 LazyColumn 使内容可滚动（原 Column 内容超高即被裁切）
        LazyColumn(Modifier.fillMaxSize()) {
            item {
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "消息",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            // 三个圆形入口：提到我的 / 评论 / 亮了-推荐
            // 1.99: 统一配色（浅灰半透明圆底 + 墨绿图标）+ weight 均分整行（手机端间距最大化）
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
                    NoticeEntry(
                        icon = { Icon(HupuIcons.At, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) },
                        label = "提到我的", modifier = Modifier.weight(1f),
                        badge = HupuMsgBadge.mention,
                    ) { openedKind = NOTICE_MENTION; listEpoch++ }
                    NoticeEntry(
                        icon = { Icon(HupuIcons.Comment, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp)) },
                        label = "评论", modifier = Modifier.weight(1f),
                        badge = HupuMsgBadge.reply,
                    ) { openedKind = NOTICE_REPLY; listEpoch++ }
                    NoticeEntry(
                        icon = { Icon(HupuIcons.Light, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp)) },
                        label = "亮了/推荐", modifier = Modifier.weight(1f),
                        badge = HupuMsgBadge.light,
                    ) { openedKind = NOTICE_LIGHT; listEpoch++ }
                }
            }

            // 私信会话列表（1.99：三按钮下方区域）
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                    PmListSection(
                        onOpen = { conv ->
                            openedConv = conv
                            chatEpoch++
                        },
                        refreshTick = pmRefreshTick,
                        onRefreshed = { pmRefreshing = false },
                    )
                }
            }
        }
    }
    } // PullToRefreshBox

    // 聊天页（4级，盖入）+ 用户主页（5级，puid 即数字型 euid）
    openedConv?.let { conv ->
        if (chatEpoch > 0) {
            androidx.compose.runtime.key(chatEpoch) {
                PmChatPage(conv = conv, onClose = {
                    chatEpoch = 0
                    openedConv = null
                    pmRefreshing = true
                    pmRefreshTick++ // 1.102: 返回私信页即同步最新一条消息
                    HupuMsgBadge.refresh() // 1.106: 聊天页关闭后重拉角标（该会话已读）
                }, onOpenProfile = { puid ->
                    chatAuthorEuid = puid.toString()
                    chatProfileEpoch++
                })
                // 1.104: 从聊天页点开的对方主页
                chatAuthorEuid?.let { eu ->
                    if (chatProfileEpoch > 0) {
                        androidx.compose.runtime.key(chatProfileEpoch) {
                            UserProfilePage(euid = eu, onClose = { chatProfileEpoch = 0; chatAuthorEuid = null })
                        }
                    }
                }
            }
        }
    }

    // 子列表页（盖在消息中心之上）
    if (openedKind != 0) {
        androidx.compose.runtime.key(listEpoch) {
            NoticeListPage(kind = openedKind, onClose = {
                openedKind = 0
                HupuMsgBadge.refresh() // 1.106: 该类通知已读，重拉三入口角标
            })
        }
    }

    // 1.106: 打开消息页时刷新一次角标（三入口立即显示最新未读）
    LaunchedEffect(Unit) { HupuMsgBadge.refresh() }
}

/** 圆形入口按钮：浅灰半透明圆底 + 深蓝图标，weight 均分行宽；badge>0 时右上挂角标 */
@Composable
private fun NoticeEntry(
    icon: @Composable () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) { icon() }
            if (badge > 0) {
                androidx.compose.material3.Badge(
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(if (badge > 9) "9+" else badge.toString(), fontSize = 9.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** 通知列表子页：数据来自对应 tab 接口，条目点击进帖子详情 */
@Composable
fun NoticeListPage(kind: Int, onClose: () -> Unit) {
    val repo = remember { HupuRepository() }
    val title = when (kind) {
        NOTICE_MENTION -> "提到我的"
        NOTICE_REPLY -> "评论"
        else -> "亮了/推荐"
    }
    val progress = remember { Animatable(0f) }
    DisposableEffect(Unit) {
        SecondaryPage.enter()
        onDispose { SecondaryPage.exit() }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    var closing by remember { mutableStateOf(false) }
    // 1.98: 退场不再手动 SecondaryPage.exit()——onDispose 是唯一出口。
    // 此页叠在消息中心之上（count≥2），若在此 exit 会双重扣减导致
    // 3级→2级时 count 提前归零、底部 Tab 栏错误出现
    LaunchedEffect(closing) {
        if (closing) {
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

    val notices = remember { mutableStateOf<List<HupuNotice>?>(null) }
    var nextPageStr by remember { mutableStateOf("") }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 首次加载：1.99 打开瞬间即显示加载态（第一帧转圈），但网络请求推迟到
    // 入场动画（280ms）结束后再发——避免「一边滑入一边建网络/解析」造成的掉帧卡顿
    LaunchedEffect(kind) {
        if (notices.value == null) {
            kotlinx.coroutines.delay(320)
            if (notices.value != null) return@LaunchedEffect
            val json = HupuApi.fetchSpaceApi(noticeEndpoint(kind))
            val parsed = json?.let { parseNoticePage(it, kind) }
            if (parsed != null) {
                notices.value = parsed.first
                nextPageStr = parsed.second
                hasMore = parsed.third
            } else {
                notices.value = emptyList()
            }
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            val ep = noticeEndpoint(kind) + java.net.URLEncoder.encode(nextPageStr, "UTF-8")
            val json = HupuApi.fetchSpaceApi(ep)
            val parsed = json?.let { parseNoticePage(it, kind) }
            if (parsed != null) {
                notices.value = (notices.value ?: emptyList()) + parsed.first
                nextPageStr = parsed.second
                hasMore = parsed.third
            }
            loadingMore = false
        }
    }

    // ---------- 帖子详情宿主（与浏览记录页同款：状态外置缓存） ----------
    var openedThread by remember { mutableStateOf<HupuThread?>(null) }
    var threadClosing by remember { mutableStateOf(false) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadLoadingMore by remember { mutableStateOf(false) }
    var threadSortSeq by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val threadDetails = remember { mutableStateMapOf<String, HupuThreadDetail>() }
    val floorStates = remember { mutableStateMapOf<String, HupuFloorReplies>() }
    val floorLoading = remember { mutableStateMapOf<String, Boolean>() }
    val floorLoadingMore = remember { mutableStateMapOf<String, Boolean>() }

    fun openDetail(n: HupuNotice) {
        if (openedThread != null) return
        threadClosing = false
        SecondaryPage.enter()
        openedThread = HupuThread(tid = n.tid, title = n.threadTitle.ifBlank { "帖子" })
        threadLoading = !threadDetails.containsKey(n.tid)
    }

    fun openFloor(tid: String, r: HupuReply) {
        if (floorStates.containsKey(r.pid)) return
        floorLoading[r.pid] = true
        scope.launch {
            delay(300)
            if (floorStates.containsKey(r.pid)) {
                floorLoading[r.pid] = false
                return@launch
            }
            val fr = repo.floorReplies(tid, r)
            if (fr != null) floorStates[r.pid] = fr
            floorLoading[r.pid] = false
        }
    }

    fun loadFloorMore(tid: String, pid: String, fr: HupuFloorReplies) {
        if (floorLoadingMore[pid] == true || !fr.hasMore) return
        val lastPid = fr.subReplies.lastOrNull { it.depth == 0 }?.pid ?: return
        floorLoadingMore[pid] = true
        scope.launch {
            val next = repo.floorReplies(tid, HupuReply(pid = pid), maxpid = lastPid)
            val cur = floorStates[pid]
            if (next != null && cur != null) {
                val merged = (cur.subReplies + next.subReplies).distinctBy { it.pid }
                floorStates[pid] = cur.copy(subReplies = merged, hasMore = next.hasMore)
            }
            floorLoadingMore[pid] = false
        }
    }

    // 1.123: 详情页时效性——每次进入都强刷；有缓存秒开第一帧，新数据到达后无缝替换
    LaunchedEffect(openedThread?.tid) {
        val t = openedThread ?: return@LaunchedEffect
        val tid = t.tid
        val mySeq = threadSortSeq
        if (threadDetails.containsKey(tid)) {
            val old = threadDetails[tid]
            val fresh = repo.threadDetail(tid, refresh = true)
            if (fresh != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                threadDetails[tid] = repo.mergeThreadDetail(old, fresh)
            }
        } else {
            threadLoading = true
            val cached = repo.threadDetailCached(tid)
            if (cached != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                threadDetails[tid] = cached
                threadLoading = false
                val fresh = repo.threadDetail(tid, refresh = true)
                if (fresh != null && openedThread?.tid == tid && mySeq == threadSortSeq) {
                    threadDetails[tid] = repo.mergeThreadDetail(threadDetails[tid], fresh)
                }
            } else {
                delay(300)
                if (threadDetails.containsKey(tid)) return@LaunchedEffect
                val d = repo.threadDetail(tid)
                if (d != null && mySeq == threadSortSeq) threadDetails[tid] = d
                if (openedThread?.tid == tid) threadLoading = false
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(1f)
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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            val cur = notices.value
            when {
                cur == null -> {
                    // 第一帧即加载态
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                cur.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无消息", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    val listState = rememberLazyListState()
                    // 1.101: 下拉刷新（重拉第一页，pageStr 首页必须为空串）
                    var refreshing by remember { mutableStateOf(false) }
                    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
                    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                        state = pullState,
                        isRefreshing = refreshing,
                        onRefresh = {
                            if (refreshing) return@PullToRefreshBox
                            refreshing = true
                            scope.launch {
                                val json = HupuApi.fetchSpaceApi(noticeEndpoint(kind))
                                val parsed = json?.let { parseNoticePage(it, kind) }
                                if (parsed != null) {
                                    notices.value = parsed.first
                                    nextPageStr = parsed.second
                                    hasMore = parsed.third
                                }
                                refreshing = false
                            }
                        },
                    ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(cur.size) { idx ->
                            val n = cur[idx]
                            NoticeRow(n = n, onClick = { openDetail(n) })
                            if (idx == cur.lastIndex && hasMore) {
                                LaunchedEffect(cur.size) { loadMore() }
                            }
                        }
                        item {
                            if (loadingMore) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        val ot = openedThread
        if (ot != null) {
            val d = threadDetails[ot.tid]
            ThreadDetailOverlay(
                tid = ot.tid,
                titleText = ot.title,
                detail = d,
                loading = threadLoading,
                loadingMore = threadLoadingMore,
                closing = threadClosing,
                onBack = { threadClosing = true },
                onExitStart = { SecondaryPage.exit() },
                onClosed = {
                    threadClosing = false
                    openedThread = null
                },
                onRefresh = {
                    val mySeq = threadSortSeq
                    scope.launch {
                        threadLoading = true
                        val cur = threadDetails[ot.tid]
                        val desc = cur?.descReplies == true
                        val nd = repo.threadDetailDirected(ot.tid, desc, refresh = true)
                        if (nd != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) threadDetails[ot.tid] = if (desc) nd else repo.mergeThreadDetail(cur, nd)
                        threadLoading = false
                    }
                },
                onLoadMore = {
                    val c = threadDetails[ot.tid] ?: return@ThreadDetailOverlay
                    if (threadLoadingMore || !repo.hasMoreReplies(c)) return@ThreadDetailOverlay
                    val mySeq = threadSortSeq
                    scope.launch {
                        threadLoadingMore = true
                        val next = repo.threadRepliesNext(ot.tid, c)
                        if (next != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) {
                            val merged = (c.replies + next.replies).distinctBy { it.pid }
                            threadDetails[ot.tid] = next.copy(replies = merged)
                        }
                        threadLoadingMore = false
                    }
                },
                onSortChanged = { desc ->
                    // 1.185c: 每次意图推进序号——迟到的旧响应据此被丢弃（连点不再来回闪）
                    val mySeq = ++threadSortSeq
                    val cur = threadDetails[ot.tid]
                    if (cur != null && cur.descReplies != desc) {
                        scope.launch {
                            val d = repo.repliesDirected(ot.tid, cur, desc)
                            if (d != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) threadDetails[ot.tid] = d
                        }
                    }
                },
                floorStates = floorStates,
                floorLoading = floorLoading.filterValues { it }.keys,
                floorLoadingMore = floorLoadingMore.filterValues { it }.keys,
                onOpenFloor = { r -> openFloor(ot.tid, r) },
                onFloorLoadMore = { pid, fr -> loadFloorMore(ot.tid, pid, fr) },
            )
        }
    }
}

/** 通知条目行：头像 + 用户/内容 + 标题/时间 */
@Composable
private fun NoticeRow(n: HupuNotice, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.avatar != null) {
                AsyncImage(
                    model = n.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                n.user.ifBlank { "虎扑用户" },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (n.extra.isNotBlank()) {
                Text(n.extra, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
            }
            Text(n.timeText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (n.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                n.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (n.threadTitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                n.threadTitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}