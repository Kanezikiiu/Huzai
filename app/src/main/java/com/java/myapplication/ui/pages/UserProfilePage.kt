package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuBlacklist
import com.java.myapplication.data.HupuFollowStore
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.data.HupuFollowUser
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuProfileReply
import com.java.myapplication.data.HupuProfileThread
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.data.HupuUserProfile
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.FeedItem
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.formatCount
import com.java.myapplication.ui.components.thumbnailUrl
import com.java.myapplication.ui.components.glassBorder
import com.java.myapplication.ui.components.glassFill
import com.java.myapplication.ui.components.glassPress
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.LiquidGlassButton
import com.java.myapplication.ui.glass.LiquidGlassCard
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.glass.liquidPressTransform
import com.java.myapplication.ui.glass.rememberLiquidHighlight
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.java.myapplication.ui.components.LiquidBackButton
import com.java.myapplication.ui.components.LiquidIconButton

/**
 * \u7528\u6237\u4e3b\u9875\uff08\u76d6\u5165\u5f0f\u4e8c\u7ea7\u9875\uff09\uff1a\u8d44\u6599\u5361\uff08\u65e0\u80cc\u666f\u56fe\uff0c\u65b9\u6848A\uff09+ \u7edf\u8ba1\u884c + \u53d1\u5e16/\u56de\u5e16\u53cc Tab\u3002
 * \u6570\u636e\uff1am.hupu.com/user/{euid} \u79fb\u52a8 UA SSR\uff0c\u4e00\u6b21\u8bf7\u6c42\u62ff\u5230\u8d44\u6599\u5361 + 20+20 \u6761\u76ee\u3002
 * \u6761\u76ee\u70b9\u51fb \u2192 \u5e16\u5b50\u8be6\u60c5\uff08\u672c\u9875\u81ea\u5e26 ThreadDetailOverlay \u5bbf\u4e3b\uff0c\u4e0e HistoryPage \u540c\u6b3e\u7f13\u5b58\u7b56\u7565\uff09\u3002
 */
@Composable
fun UserProfilePage(
    euid: String,
    onClose: () -> Unit,
    /** 1.222: 层叠序号——从「黑名单管理页」再进主页时需盖在该页之上（该页 zIndex=2f） */
    zIndex: Float = 1f,
) {
    val repo = remember { HupuRepository() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 1.222: 顶栏「更多」→ Liquid Glass 弹窗（按钮纵向排列）。
    // 弹窗必须采样同一窗口的 LayerBackdrop，所以给「内容」挂记录层，
    // 弹窗自身作为它之后的兄弟节点（否则会采到自己 / 跨窗口取不到像素）。
    val pageBg = MaterialTheme.colorScheme.background
    val pageBackdrop = rememberLayerBackdrop {
        drawRect(pageBg)
        drawContent()
    }
    var showMore by remember { mutableStateOf(false) }
    // \u540c\u4e00 euid \u7684\u4e0a\u5c42\u53cd\u590d\u8fdb\u51fa\u4e0d\u91cd\u62c9\uff1brefresh=true \u65f6\u91cd\u62c9
    var profile by remember { mutableStateOf<HupuUserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) } // 0=\u53d1\u5e16 1=\u56de\u5e16 2=\u63a8\u8350 3=\u6536\u85cf 4=\u5173\u6ce8
    // \u5355\u8def\u5f84\uff1a\u767b\u5f55\u540e\u624d\u53ef\u67e5\u770b\u4e3b\u9875\uff0c\u4e0d\u518d\u56de\u9000\u79fb\u52a8\u7248 SSR
    val logged = HupuAccount.isLoggedIn
    val listState = remember { mutableStateListOf<ListItem>() }
    var listEndReached by remember { mutableStateOf(false) }
    var listLoading by remember { mutableStateOf(false) }
    var listPage by remember { mutableStateOf(1) }
    var replyCursor by remember { mutableStateOf(0L) }
    var followType by remember { mutableStateOf(1) }
    // 1.127 本地关注态（服务端无关注状态查询，见 HupuFollowStore）；null = 未知/不适用
    // 入参可能是短 puid（关注列表入口）或 euid：能确定「已关注」就第一帧直接渲染
    var followed by remember(euid) { mutableStateOf(HupuFollowStore.fastFollowed(euid)) }
    var followBusy by remember { mutableStateOf(false) }
    var followToast by remember { mutableStateOf<String?>(null) }
    // 1.221：本地黑名单态（纯本地、**不需要登录**；拉黑后该人的帖子/回复/评分评论不再出现）
    var blacklisted by remember(euid) { mutableStateOf(false) }
    LaunchedEffect(followToast) {
        if (followToast != null) {
            delay(2200)
            followToast = null
        }
    }

    // \u76d6\u5165\u52a8\u753b + \u4e8c\u7ea7\u9875\u8ba1\u6570\uff08Tab \u680f\u9690\u85cf\uff09\uff1a\u4e0e HistoryPage \u540c\u6b3e
    val progress = remember { Animatable(0f) }
    DisposableEffect(Unit) {
        SecondaryPage.enter()
        onDispose { SecondaryPage.exit() }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            // \u8ba1\u6570\u56de\u6536\u7edf\u4e00\u7531 onDispose \u5b8c\u6210\uff1a\u6b64\u5904\u518d exit \u4f1a\u5728\u5d4c\u5957\u65f6\u505a\u8d70\u4e0b\u5c42\u9875\u8ba1\u6570\uff0c\u5bfc\u81f4 Tab \u680f\u63d0\u524d\u56de\u5f52
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    // \u8fd4\u56de\u624b\u52bf\uff1a\u8ddf\u624b\u900f\u660e\u5ea6\uff08\u4e0e\u5176\u4ed6\u4e8c\u7ea7\u9875\u540c\u6b3e\uff09
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { }
            return@PredictiveBackHandler
        }
        try {
            events.collect { event ->
                progress.snapTo(1f - event.progress)
            }
            progress.animateTo(0f, tween(120))
            closing = true
        } catch (e: CancellationException) {
            progress.animateTo(1f, tween(200))
            throw e
        }
    }

    // ---------- \u5e16\u5b50\u8be6\u60c5\u5bbf\u4e3b\uff08\u4e0e HistoryPage \u540c\u6b3e\uff1a\u72b6\u6001\u5916\u7f6e\u7f13\u5b58\uff09 ----------
    var openedThread by remember { mutableStateOf<HupuThread?>(null) }
    // 1.192: 本层「已计数」标记——连点多个条目时 enter 只发生一次，防 Tab 栏计数泄漏
    var upThreadEntered by remember { mutableStateOf(false) }
    var threadClosing by remember { mutableStateOf(false) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadLoadingMore by remember { mutableStateOf(false) }
    var threadSortSeq by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val threadDetails = remember { mutableStateMapOf<String, HupuThreadDetail>() }
    val floorStates = remember { mutableStateMapOf<String, HupuFloorReplies>() }
    val floorLoading = remember { mutableStateMapOf<String, Boolean>() }
    val floorLoadingMore = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()
    // \u7528\u6237\u4e3b\u9875\u6808\uff1a\u5173\u6ce8\u5217\u8868\u70b9\u8fdb\u5176\u4ed6\u7528\u6237\u65f6\u5c42\u5c42\u53e0\u52a0\uff08\u6bcf\u5c42\u72ec\u7acb\u7ec4\u5408\u3001\u72b6\u6001\u4fdd\u7559\uff09\uff0c\u8fd4\u56de\u9010\u5c42\u5f39\u51fa
    val userPageStack = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    // 1.104: 私信入口——从用户主页直接开聊（复用 PmChatPage，puid 即数字型 euid）
    var pmConv by remember { mutableStateOf<PmConversation?>(null) }
    var pmEpoch by remember { mutableIntStateOf(0) }

    /** 1.126 关注 / 取关：本地乐观翻转，请求失败回滚并提示 */
    fun toggleFollow() {
        val p = profile ?: return
        if (followBusy) return
        if (!HupuAccount.isLoggedIn) {
            followToast = "请先在「我的」页登录"
            return
        }
        if (p.puid.isEmpty()) {
            followToast = "用户信息不完整，稍后再试"
            return
        }
        val target = !(followed ?: false)
        followed = target
        followBusy = true
        scope.launch {
            val err = if (target) HupuAccount.followUser(p.puid) else HupuAccount.unfollowUser(p.puid)
            if (err == null) {
                HupuFollowStore.markFollowed(p.puid, target)
                followToast = if (target) "已关注" else "已取消关注"
            } else {
                followed = !target
                followToast = err
            }
            followBusy = false
        }
    }

    /**
     * 1.221 拉黑 / 取消拉黑：**纯本地**操作（不影响服务端、不需要登录），
     * 立即生效并给出与「关注」同款的顶部提示。
     * 同时记下 puid 与 euid —— 不同接口给的 id 不一定同值，两个都记才不会漏过滤。
     */
    fun toggleBlacklist() {
        val p = profile ?: return
        val ids = listOf(p.puid, p.euid)
        if (ids.all { it.isBlank() }) {
            followToast = "用户信息不完整，稍后再试"
            return
        }
        if (!blacklisted) {
            if (HupuPrefs.addToBlacklist(ids, p.name, p.avatar)) {
                blacklisted = true
                followToast = "已拉黑 ${p.name}"
            } else {
                // 已存在（幂等）或已达上限
                blacklisted = HupuPrefs.isBlacklistedAny(ids)
                followToast = if (blacklisted) "已在黑名单中" else "黑名单已满（${HupuBlacklist.MAX_ENTRIES}）"
            }
        } else {
            HupuPrefs.removeFromBlacklist(ids)
            blacklisted = false
            followToast = "已移出黑名单"
        }
    }

    fun openDetail(t: HupuThread) {
        threadClosing = false
        if (!upThreadEntered) { upThreadEntered = true; SecondaryPage.enter() }
        openedThread = t
        threadLoading = !threadDetails.containsKey(t.tid)
    }
    fun openFloor(tid: String, pid: String, r: com.java.myapplication.data.HupuReply) {
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
            val next = repo.floorReplies(tid, com.java.myapplication.data.HupuReply(pid = pid), maxpid = lastPid)
            val cur = floorStates[pid]
            if (next != null && cur != null) {
                val merged = (cur.subReplies + next.subReplies).distinctBy { it.pid }
                floorStates[pid] = cur.copy(subReplies = merged, hasMore = next.hasMore)
            }
            floorLoadingMore[pid] = false
        }
    }
    fun closeDetail() {
        threadClosing = true
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

    // \u6570\u636e\u62c9\u53d6\uff08\u6253\u5f00\u77ac\u95f4\u7f6e loading\uff0c\u5931\u8d25\u6001\u53ea\u5728\u771f\u5b9e\u5931\u8d25\u540e\u51fa\u73b0\uff09
    LaunchedEffect(euid) {
        // 1.190: 命中内存缓存 → 第一帧就用真实资料渲染（零骨架），随后仍请求刷新覆盖
        val cached = repo.cachedUserInfo(euid)
        if (cached != null && profile == null) profile = cached
        loading = profile == null
        failed = false
        // \u767b\u5f55\u6001\u4f18\u5148 PC \u4e2a\u4eba\u4e2d\u5fc3 getUserInfo\uff08\u58f0\u671b/IP/\u771f\u5b9e\u8ba1\u6570/isSelf\uff09\uff1b\u5931\u8d25\u56de\u9000\u79fb\u52a8 SSR
        val p = if (HupuAccount.isLoggedIn) repo.spaceUserInfo(euid) else null
        if (p != null) {
            profile = p
            loading = false
        } else {
            // \u7f13\u51b2\uff1a\u547d\u4e2d\u78c1\u76d8\u7f13\u5b58\u65f6\u4e0d\u95ea\u5931\u8d25\u6001
            delay(200)
            if (profile == null) {
                failed = true
                loading = false
            }
        }
    }

    fun refresh() {
        scope.launch {
            loading = profile == null
            val p = if (HupuAccount.isLoggedIn) repo.spaceUserInfo(euid) else null
            if (p != null) profile = p
            loading = false
        }
    }

    // 1.127 关注态：先读本地集合；未校准时后台全量同步一次（失败保留旧值）
    LaunchedEffect(profile?.puid, HupuFollowStore.version) {
        val p = profile
        if (p == null || p.isSelf || p.puid.isEmpty() || !HupuAccount.isLoggedIn) {
            followed = null
            return@LaunchedEffect
        }
        // 集合未校准（冷启动首次）时「未命中」不足以下结论，保持未知，等同步结果
        followed = if (HupuFollowStore.isFollowed(p.puid)) true
        else if (HupuFollowStore.synced) false
        else null
    }
    // 1.221 黑名单态：资料到达后按本地表同步；订阅版本号 → 管理页删除后本页按钮即时回位
    LaunchedEffect(profile?.puid, profile?.euid, HupuPrefs.blacklistVersion) {
        val p = profile ?: return@LaunchedEffect
        blacklisted = HupuPrefs.isBlacklistedAny(listOf(p.puid, p.euid))
    }
    LaunchedEffect(profile?.puid) {
        val p = profile
        if (p == null || p.isSelf || p.puid.isEmpty() || !HupuAccount.isLoggedIn) return@LaunchedEffect
        HupuFollowStore.rememberId(p.euid, p.puid)
        if (HupuFollowStore.shouldSync()) {
            val ok = HupuFollowStore.sync()
            if (!ok && !HupuFollowStore.synced) {
                // 同步失败且从未校准：退化为「未关注」，否则按钮永远不出现
                followed = false
            }
        }
    }
    // ---------- \u767b\u5f55\u6001\u4e94 Tab \u5217\u8868\u52a0\u8f7d ----------
    val listLs = rememberLazyListState()
    // 1.193: Tab 行横滑状态提到页面级 —— 若 remember 写在 LazyColumn item 内部，
    // 该 item 划出屏幕被回收后滚动位置会被重置回最左。
    val tabsScroll = rememberScrollState()
    // \u5217\u8868\u4ee3\u9645\uff1a\u4efb\u4f55\u5207\u6362\uff08tab/followType/profile \u5c31\u7eea\uff09\u90fd\u4f5c\u5e9f\u5728\u9014\u8bf7\u6c42\u7684\u5199\u5165\uff0c\u9632\u6b62\u4e32\u5217\u8868
    var listEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(euid, profile, tab, followType) {
        val prof = profile ?: return@LaunchedEffect
        listEpoch++
        val myEpoch = listEpoch
        if (!prof.isSelf && tab == 3) { tab = 0; return@LaunchedEffect }
        listLoading = true
        listEndReached = false
        listPage = 1
        listState.clear()
        when (tab) {
            0 -> {
                val its = repo.spaceThreads(euid, 1)
                if (myEpoch != listEpoch) return@LaunchedEffect
                listState.addAll(its.map { ListItem.Th(it) })
                if (its.size < 30) listEndReached = true
            }
            1 -> {
                val pr = repo.spaceReplies(euid, 1)
                if (myEpoch != listEpoch) return@LaunchedEffect
                replyCursor = pr.second
                listState.addAll(pr.first.map { ListItem.Rp(it) })
                if (pr.first.isEmpty()) listEndReached = true
            }
            2 -> {
                val its = repo.spaceRecommends(euid, 1)
                if (myEpoch != listEpoch) return@LaunchedEffect
                listState.addAll(its.map { ListItem.Th(it) })
                if (its.size < 30) listEndReached = true
            }
            3 -> {
                val its = repo.spaceFavorites(euid, 1)
                if (myEpoch != listEpoch) return@LaunchedEffect
                listState.addAll(its.map { ListItem.Th(it) })
                if (its.size < 30) listEndReached = true
            }
            4 -> {
                val its = repo.spaceFollows(euid, followType, 1)
                if (myEpoch != listEpoch) return@LaunchedEffect
                listState.addAll(its.map { ListItem.Fo(it) })
                if (its.size < 30) listEndReached = true
            }
        }
        listLoading = false
    }
    // \u6eda\u52a8\u5230\u5e95\u7ffb\u4e0b\u4e00\u9875\uff1a\u6bcf\u6b21\u89e6\u53d1\u53ea\u8bf7\u4e00\u9875\uff0c\u4e0d\u4f1a\u4e00\u6b21\u8dd1\u5b8c\u6240\u6709\u5206\u9875
    LaunchedEffect(listLs, euid, profile, tab, followType) {
        snapshotFlow { listLs.reachedBottom }
            .collect { atEndNow ->
                if (!atEndNow || listLoading || listEndReached) return@collect
                listLoading = true
                when (tab) {
                    0 -> {
                        val its = repo.spaceThreads(euid, listPage + 1)
                        if (its.isEmpty()) listEndReached = true
                        else { listPage++; listState.addAll(its.map { ListItem.Th(it) }) }
                    }
                    1 -> {
                        val pr = repo.spaceReplies(euid, listPage + 1, replyCursor)
                        if (pr.first.isEmpty()) listEndReached = true
                        else { listPage++; replyCursor = pr.second; listState.addAll(pr.first.map { ListItem.Rp(it) }) }
                    }
                    2 -> {
                        val its = repo.spaceRecommends(euid, listPage + 1)
                        if (its.isEmpty()) listEndReached = true
                        else { listPage++; listState.addAll(its.map { ListItem.Th(it) }) }
                    }
                    3 -> {
                        val its = repo.spaceFavorites(euid, listPage + 1)
                        if (its.isEmpty()) listEndReached = true
                        else { listPage++; listState.addAll(its.map { ListItem.Th(it) }) }
                    }
                    4 -> {
                        val its = repo.spaceFollows(euid, followType, listPage + 1)
                        if (its.isEmpty()) listEndReached = true
                        else { listPage++; listState.addAll(its.map { ListItem.Fo(it) }) }
                    }
                    else -> listEndReached = true
                }
                listLoading = false
            }
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize().layerBackdrop(pageBackdrop)) {
            // \u9876\u680f\uff1a\u8fd4\u56de + \u6635\u79f0\uff08\u907f\u8ba9\u72b6\u6001\u680f\uff09
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidBackButton(onClick = { closing = true })
                Spacer(Modifier.width(8.dp))
                Text(
                    // 1.163: 资料卡已显示昵称，顶栏统一恒显示「用户主页」
                    "用户主页",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 1.222: 顶栏右侧「更多」——打开 Liquid Glass 操作弹窗（按钮纵向排列）。
                // 只有存在可用操作（非本人主页、且有 id）时才显示，避免弹出空菜单。
                val canMore = profile?.let {
                    !it.isSelf && (it.puid.isNotEmpty() || it.euid.isNotEmpty())
                } == true
                if (canMore) {
                    LiquidIconButton(
                        icon = HupuIcons.MoreVert,
                        contentDescription = "更多",
                        onClick = { showMore = true },
                    )
                }
            }

            when {
                loading -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    items(6) { i ->
                        Box(
                            Modifier
                                .padding(vertical = 8.dp)
                                .fillMaxWidth()
                                .height(if (i == 0) 180.dp else 88.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface),
                        )
                    }
                }
                failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorRetry { refresh() }
                }
                else -> {
                    val p = profile ?: return
                    // 1.193: 数据行已并入资料卡，回调在这里算好再传进去
                    //   （1.179 语义不变：粉丝 → 关注我的/关注TA的；关注 → 我关注的/TA关注的）
                    val openFollowers: (() -> Unit)? =
                        if (logged) ({ tab = 4; followType = 2 }) else null
                    val openFollowing: (() -> Unit)? =
                        if (logged) ({ tab = 4; followType = 1 }) else null
                    LazyColumn(
                        state = listLs,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // \u8d44\u6599\u5361\uff08\u65e0\u80cc\u666f\u56fe\uff09
                        item(key = "card") {
                            ProfileCard(
                                p = p,
                                onOpenFollowers = openFollowers,
                                onOpenFollowing = openFollowing,
                                showFollow = !p.isSelf && logged && p.puid.isNotEmpty(),
                                followed = followed,
                                followBusy = followBusy,
                                onToggleFollow = { toggleFollow() },
                                onPm = if (!p.isSelf && p.puid.isNotEmpty()) {
                                    {
                                        pmConv = PmConversation(
                                            puid = p.puid.toLongOrNull() ?: 0L,
                                            sid = 0L,
                                            name = p.name,
                                            avatar = p.avatar.ifEmpty { null },
                                            lastContent = "",
                                            lastTime = 0L,
                                            unread = 0,
                                            isSystem = false,
                                        )
                                        pmEpoch++
                                    }
                                } else null,
                            )
                        }
                        // \u53cc Tab
                        item(key = "tabs") {
                            // 1.193: Tab 行改为「可横向滑动」。
                            // 依据：登录态 5 个 chip 实测总宽 ≈370dp，而可用宽度只有 328dp
                            //（360dp 屏 − 两侧 16dp 内容边距），窄屏必然溢出。
                            // 为什么不改等宽分段：chip 文本带计数（「发帖 1.2万」）长度可变，
                            // 等宽会截断计数；折行则破坏「一排 tab」的视觉语义。
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(tabsScroll),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // \u767b\u5f55\u6001\u4e94 Tab\uff08\u8ba1\u6570\u5bf9\u9f50 PC \u5b98\u65b9\u6620\u5c04\uff1a\u53d1\u8d34=bbs_msg_count\uff0c\u56de\u5e16=bbs_post_count\uff09\uff1b\u672a\u767b\u5f55\u4ec5\u4e24 Tab
                                if (logged) {
                                    TabChip("\u53d1\u5e16 ${formatCount(p.msgCount)}", tab == 0) { tab = 0 }
                                    TabChip("\u56de\u5e16 ${formatCount(p.postCount)}", tab == 1) { tab = 1 }
                                    TabChip("\u63a8\u8350 ${formatCount(p.recommendCount)}", tab == 2) { tab = 2 }
                                    if (p.isSelf) TabChip("\u6536\u85cf", tab == 3) { tab = 3 }
                                    TabChip("\u5173\u6ce8", tab == 4) { tab = 4 }
                                } else {
                                    TabChip("\u53d1\u5e16 ${formatCount(p.msgCount)}", tab == 0) { tab = 0 }
                                    TabChip("\u56de\u5e16 ${formatCount(p.postCount)}", tab == 1) { tab = 1 }
                                }
                            }
                        }
                        if (!logged) {
                            item(key = "nologin") {
                                Text(
                                    "\u767b\u5f55\u540e\u624d\u80fd\u67e5\u770b\u4e3b\u9875\uff0c\u8bf7\u5148\u5728\u300c\u6211\u7684\u300d\u9875\u767b\u5f55\u864e\u6251\u8d26\u53f7",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        } else if (logged) {
                            // \u5173\u6ce8 tab \u4e8c\u7ea7\u5206\u7ec4
                            if (tab == 4) {
                                item(key = "followType") {
                                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TabChip(if (p.isSelf) "\u6211\u5173\u6ce8\u7684" else "TA\u5173\u6ce8\u7684", followType == 1) { if (followType != 1) followType = 1 }
                                        TabChip(if (p.isSelf) "\u5173\u6ce8\u6211\u7684" else "\u5173\u6ce8TA\u7684", followType == 2) { if (followType != 2) followType = 2 }
                                    }
                                }
                            }
                            itemsIndexed(listState) { _, li ->
                                val li = li
                                when (li) {
                                    is ListItem.Th -> ProfileThreadRow(li.t) {
                                        openDetail(
                                            HupuThread(
                                                tid = li.t.tid,
                                                title = li.t.title,
                                                lights = li.t.recommendNum,
                                                replies = li.t.replies,
                                                cover = li.t.cover,
                                            ),
                                        )
                                    }
                                    is ListItem.Rp -> ProfileReplyRow(li.r) {
                                        if (li.r.tid.isNotEmpty()) {
                                            openDetail(HupuThread(tid = li.r.tid, title = li.r.threadTitle))
                                        }
                                    }
                                    is ListItem.Fo -> ProfileFollowRow(li.f) { pu -> if (pu.isNotEmpty()) userPageStack.add(pu) }
                                }
                            }
                            if (listState.isEmpty() && !listLoading && listEndReached) {
                                item(key = "emptyList") {
                                    val who = if (p.isSelf) "\u4f60" else "TA"
                                    val emptyMsg = when (tab) {
                                        0 -> "${who}\u8fd8\u6ca1\u6709\u53d1\u8fc7\u5e16\u5b50"
                                        1 -> "${who}\u8fd8\u6ca1\u6709\u56de\u8fc7\u5e16"
                                        2 -> "${who}\u8fd8\u6ca1\u6709\u63a8\u8350\u8fc7\u5185\u5bb9"
                                        3 -> "${who}\u8fd8\u6ca1\u6709\u6536\u85cf\u4efb\u4f55\u5185\u5bb9"
                                        else -> if (followType == 1) "${who}\u8fd8\u6ca1\u6709\u5173\u6ce8\u7684\u4eba" else "${who}\u8fd8\u6ca1\u6709\u7c89\u4e1d"
                                    }
                                    Column(
                                        // 1.59 空态改视口 42% 高：满屏空态使列表总高超一屏，
                                        // 可把资料卡滑出屏；0.42f 叠在 header 后不超屏，滚动距离≈0
                                        Modifier.fillParentMaxHeight(0.42f).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            "(\u00b4\u30fb\u03c9\u30fb`)",
                                            fontSize = 22.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            emptyMsg,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                    }
                                }
                            }
                            if (listLoading) {
                                item(key = "footLoading") {
                                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(22.dp))
                                    }
                                }
                            } else if (!listEndReached) {
                                item(key = "footSentinel") { Spacer(Modifier.height(1.dp)) }
                            }
                        } else if (tab == 0) {
                            items(p.threads, key = { it.tid }) { t ->
                                ProfileThreadRow(t) {
                                    openDetail(
                                        HupuThread(
                                            tid = t.tid,
                                            title = t.title,
                                            lights = t.recommendNum,
                                            replies = t.replies,
                                            cover = t.cover,
                                        ),
                                    )
                                }
                            }
                        } else {
                            items(p.replies, key = { it.pid }) { r ->
                                ProfileReplyRow(r) {
                                    if (r.tid.isNotEmpty()) {
                                        openDetail(HupuThread(tid = r.tid, title = r.threadTitle))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // \u5e16\u5b50\u8be6\u60c5\u9875\uff08\u76d6\u5165\u5f0f\uff1b\u7ec4\u5408\u987a\u5e8f\u5728\u5217\u8868\u4e4b\u540e \u2192 \u76d6\u5728\u5176\u4e0a\uff09
        val ot = openedThread
        if (ot != null) {
            // 1.192: 本层离开组合时兜底回收计数（正常退场已由 onExitStart 提前回收）
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose { if (upThreadEntered) { upThreadEntered = false; SecondaryPage.exit() } }
            }
            val d = threadDetails[ot.tid]
            ThreadDetailOverlay(
                tid = ot.tid,
                titleText = ot.title,
                detail = d,
                loading = threadLoading,
                loadingMore = threadLoadingMore,
                closing = threadClosing,
                onBack = { closeDetail() },
                onExitStart = { if (upThreadEntered) { upThreadEntered = false; SecondaryPage.exit() } },
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
                    val cur = threadDetails[ot.tid] ?: return@ThreadDetailOverlay
                    if (threadLoadingMore || !repo.hasMoreReplies(cur)) return@ThreadDetailOverlay
                    val mySeq = threadSortSeq
                    scope.launch {
                        threadLoadingMore = true
                        val next = repo.threadRepliesNext(ot.tid, cur)
                        if (next != null && mySeq == threadSortSeq && openedThread?.tid == ot.tid) {
                            val merged = (cur.replies + next.replies).distinctBy { it.pid }
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
                floorLoading = floorLoading.keys,
                floorLoadingMore = floorLoadingMore.keys,
                onOpenFloor = { r -> openedThread?.let { t -> openFloor(t.tid, r.pid, r) } },
                onFloorLoadMore = { pid, fr -> openedThread?.let { t -> loadFloorMore(t.tid, pid, fr) } },
            )
        }
        // \u5b50\u7528\u6237\u4e3b\u9875\u6808\uff1a\u9010\u5c42\u76d6\u5165\uff0c\u8fd4\u56de\u9010\u5c42\u9000\u51fa
        userPageStack.forEachIndexed { si, subEuid ->
            androidx.compose.runtime.key(si) {
                UserProfilePage(euid = subEuid, onClose = { userPageStack.removeAt(si) })
            }
        }
        // 1.104: 用户主页私信聊天页（盖入，puid 即 euid；点顶栏可跳对方主页——此处传 null 防循环）
        // 1.126 关注反馈（置顶显示，点击立即消失）
        followToast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(9f)
                    .statusBarsPadding()
                    .padding(top = 64.dp),
            ) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f))
                        .clickable { followToast = null }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        pmConv?.let { c ->
            if (pmEpoch > 0) {
                androidx.compose.runtime.key(pmEpoch) {
                    PmChatPage(
                        conv = c,
                        onClose = { pmEpoch = 0; pmConv = null },
                    )
                }
            }
        }
        // ---------- 1.222 「更多」弹窗（Liquid Glass，按钮纵向排列） ----------
        // 位置：作为「内容记录层」之后的兄弟节点（毛玻璃才能正确采样背后的页面像素）。
        // 目前只有一项：拉黑 / 取消拉黑该用户（纯本地、不需要登录）。
        val moreTarget = profile
        if (showMore && moreTarget != null && !moreTarget.isSelf) {
            val hasId = moreTarget.puid.isNotEmpty() || moreTarget.euid.isNotEmpty()
            if (hasId) {
                LiquidGlassCard(
                    backdrop = pageBackdrop,
                    onDismiss = { showMore = false },
                ) { close ->
                    Text(
                        "更多操作",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp),
                    )
                    Text(
                        moreTarget.name,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 16.dp),
                    )
                    LiquidGlassButton(
                        text = "分享该用户",
                        accent = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        // 分享的是官方用户主页链接（my.hupu.com/{euid}），无 euid 时退回 puid
                        val shareId = moreTarget.euid.ifEmpty { moreTarget.puid }
                        shareLinkToSystem(
                            ctx = ctx,
                            chooserTitle = "分享用户主页",
                            shareText = "「${moreTarget.name}」的虎扑主页",
                            shareUrl = "https://my.hupu.com/$shareId",
                        )
                        close()
                    }
                    Spacer(Modifier.height(10.dp))
                    LiquidGlassButton(
                        text = if (blacklisted) "取消拉黑该用户" else "拉黑该用户",
                        accent = false,
                        contentColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        toggleBlacklist()
                        close()
                    }
                    Spacer(Modifier.height(10.dp))
                    LiquidGlassButton(
                        text = "取消",
                        accent = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    ) { close() }
                }
            }
        }
    }
}

/** 资料卡（1.193 方向A「沉浸式个人档案」，四段式）：
 *  ① 身份行：头像 72 + 昵称/Lv 徽章 + 信息行（IP 属地 · 加入天数 · 声望）
 *  ② 操作行：私信 / 关注（等宽平分；仅非本人主页、且按钮就绪时出现）
 *  ③ 指标行：等级积分 ｜ 下一级 + 进度条（无等级数据则整段隐藏）
 *  ④ 数据行：粉丝 / 关注 / 被点亮 / 被推荐（改为卡内嵌，不再单独成卡）
 *  无背景图（用户确认）。
 *  1.193c（真机反馈）：声望由 ③ 移入 ① 的信息行，紧跟在「加入天数」右侧。
 *
 * 版式依据（360dp 屏实测）：卡内可用 296dp，减去 72dp 头像 + 12dp 后中列仅剩 212dp。
 * 昵称(18sp 约 54dp) + Lv 徽章(约 38dp) = 约 100dp 可放下；若再把「私信+关注」
 * 两个按钮(约 120dp)塞进同一行，中列只剩 92dp —— 所以身份行与操作行必须拆开。
 */
@Composable
private fun ProfileCard(
    p: HupuUserProfile,
    showFollow: Boolean = false,
    followed: Boolean? = null,
    followBusy: Boolean = false,
    onToggleFollow: () -> Unit = {},
    onPm: (() -> Unit)? = null,
    onOpenFollowers: (() -> Unit)? = null,
    onOpenFollowing: (() -> Unit)? = null,
) {
    val dark = isAppDarkTheme()
    // 官方 Lv 徽章色（levelColor 为空/非法时回落主题色）
    val levelColor = runCatching {
        Color(android.graphics.Color.parseColor(p.levelColor))
    }.getOrDefault(MaterialTheme.colorScheme.primary)
    // 1.193: 先落到局部 val，避免在 Compose lambda 里对可空参数做智能转换
    val pmAction = onPm
    val canFollow = showFollow && followed != null

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- ① 身份行：头像 + 昵称/Lv + 信息行 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.avatar.isNotEmpty()) {
                AsyncImage(
                    model = p.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        p.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // fill = false：昵称按内容宽度占用，不和徽章抢整行
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (p.levelDesc.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        // 1.214（真机反馈）：保留渐变 + 液体按压手感，并做成可点击
                        //（点击动作暂为占位——需要接什么菜单/页面请告知）
                        val lvHighlight = rememberLiquidHighlight()
                        Box(
                            Modifier
                                .liquidPressTransform(lvHighlight)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            lerp(levelColor, Color.White, 0.18f),
                                            lerp(levelColor, Color.Black, 0.08f),
                                        )
                                    )
                                )
                                .clickable(
                                    interactionSource = null,
                                    indication = null,
                                    role = Role.Button,
                                ) { /* 占位：等级铭牌点击动作 */ }
                                .then(lvHighlight.modifier)
                                .then(lvHighlight.gestureModifier)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                p.levelDesc,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                    }
                }
                // 信息行：IP 属地 · 加入天数 · 声望（分段拼接，任一为空则自动省略）
                // 1.193c（真机反馈）：声望从 ③ 指标行移回此处，排在「加入天数」右侧。
                // 为免声望数值变大（4 位以上）时整行被省略号截断，这里拆成两段：
                //   左段（IP 属地 / 加入天数）weight(1f, fill = false) → 空间不足时自己省略
                //   右段（声望）不参与压缩 → 永远完整可见
                val infoLine = listOfNotNull(
                    p.locationStr.takeIf { it.isNotBlank() }?.let { "IP 属地 $it" },
                    p.regTimeStr.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                val repText = if (p.reputation > 0) "声望 ${formatCount(p.reputation)}" else ""
                if (infoLine.isNotEmpty() || repText.isNotEmpty()) {
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (infoLine.isNotEmpty()) {
                            Text(
                                infoLine,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (repText.isNotEmpty()) {
                            Text(
                                if (infoLine.isEmpty()) repText else " · $repText",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        // ---------- ② 操作行：私信 / 关注（等宽平分） ----------
        // 1.222:「拉黑」已从操作行移到顶栏「更多」弹窗里（产品要求：三点 → 弹窗 → 纵向按钮）
        if (pmAction != null || canFollow) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pmAction != null) {
                    LiquidButton(
                        onClick = pmAction,
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        modifier = Modifier.weight(1f),
                        // 1.206（真机反馈）：38 → 42dp —— 原来跟排序条同高，作为「操作按钮」显扁
                        height = 42.dp,
                        contentPadding = 12.dp,
                        // 1.193b（真机反馈）：图标与文字的间距对齐「关注」按钮 ——
                        // 关注是 arrangement 3dp + 显式 Spacer 3dp（共 6dp），此前这里是 8+6=14dp
                        arrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(
                            HupuIcons.Mail,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "私信",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (canFollow) {
                    FollowButton(
                        followed == true,
                        followBusy,
                        onToggleFollow,
                        Modifier.weight(1f),
                    )
                }
                // 单按钮时用等宽占位：否则「关注态未知 → 已知」的那一瞬，
                // 按钮会从满行宽突变到半行宽（可见跳变）。
                val shownActions = (if (pmAction != null) 1 else 0) + (if (canFollow) 1 else 0)
                if (shownActions < 2) Spacer(Modifier.weight(1f))
            }
        }
        // ---------- ③ 指标行：等级积分 ｜ 下一级 + 进度条 ----------
        //（1.193c：声望已上移到 ① 的信息行，和「加入天数」并排，此处不再重复）
        if (p.levelScore > 0 && p.nextLevelScore > p.levelScore) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "等级积分 ${formatCount(p.levelScore.toInt())}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "下一级 ${formatCount(p.nextLevelScore.toInt())}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val frac = (p.levelScore.toDouble() / p.nextLevelScore.toDouble()).coerceIn(0.0, 1.0)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac.toFloat())
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(levelColor),
                    )
                }
            }
        }
        // ---------- ④ 数据行：卡内嵌四格 ----------
        // 1.193b（真机反馈）：去掉分隔线（1 条横线 + 3 条纵线），改用留白分组
        StatsRow(p, onOpenFollowers, onOpenFollowing)
    }
}

/** 1.126 关注按钮：未关注 = 主题色实心 + 加号；已关注 = 浅灰底。即时反馈，失败由调用方回滚。
 *  1.193: 增加 modifier —— 在操作行里用 weight(1f) 与「私信」按钮等宽平分。 */
@Composable
private fun FollowButton(
    followed: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isAppDarkTheme()
    val bg = buttonFill(dark)
    val fg = if (followed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
    // 1.192e: 关注按钮 → 液态玻璃按钮（未关注 = 主题色；已关注 = 浅灰玻璃）
    LiquidButton(
        onClick = { if (!busy) onClick() },
        isInteractive = !busy,
        fill = if (followed) bg else MaterialTheme.colorScheme.primary,
        border = if (followed) buttonBorder(dark) else Color.Transparent,
        modifier = modifier,
        // 1.206（真机反馈）：与「私信」同步 38 → 42dp
        height = 42.dp,
        contentPadding = 16.dp,
        arrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
    ) {
        if (!followed) {
            Icon(
                HupuIcons.Plus,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            if (followed) "已关注" else "关注",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

/** 数据行（1.193）：改为资料卡内嵌 —— 不再自带背景/圆角/内边距，
 *  四格等宽，作为资料卡的「第 ④ 段」。
 *  1.193b（真机反馈）：去掉卡内分隔线（横 1 + 纵 3），改用留白分组。 */
@Composable
private fun StatsRow(
    p: HupuUserProfile,
    onOpenFollowers: (() -> Unit)? = null,
    onOpenFollowing: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatCell("粉丝", p.followers, onOpenFollowers, Modifier.weight(1f))
        StatCell("关注", p.following, onOpenFollowing, Modifier.weight(1f))
        StatCell("被点亮", p.beLightCount, null, Modifier.weight(1f))
        StatCell("被推荐", p.beRecommendCount, null, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(
    label: String,
    value: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.then(
            // 1.179: 可点（粉丝/关注）时加点击反馈；不可点保持原样（不占额外尺寸）
            if (onClick != null) {
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick)
            } else {
                Modifier
            },
        ),
    ) {
        Text(
            formatCount(value),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // 1.192e: 材质升级为与顶部玻璃胶囊同源（选中 = 近实心磨砂 + 主题色文字；未选 = 极淡半透明）
    val dark = isAppDarkTheme()
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            // 1.192f: 改走 glassPress —— 与主页横滑条 Chip 完全一致的 spring 缩放
            //（此前用 clickable 的默认 ripple，表现成「压暗」，与主页手感不一致）
            .glassPress(
                shape = RoundedCornerShape(999.dp),
                fill = glassFill(dark, selected),
                border = glassBorder(dark, selected),
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** \u4e3b\u9898\u5e16\u884c\uff1a\u6807\u9898 + \u7248\u5757/\u65f6\u95f4 + \u56de\u590d/\u63a8\u8350\uff08\u771f\u5b9e\u6570\uff0c\u4e0d\u5c01\u9876\uff09 */
@Composable
private fun ProfileThreadRow(t: HupuProfileThread, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                t.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.forumName.isNotBlank()) {
                    Text(
                        t.forumName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    listOf(t.createdAtText, "\u56de\u590d ${formatCount(t.replies)}", "\u63a8\u8350 ${formatCount(t.recommendNum)}")
                        .filter { it.isNotBlank() }
                        .joinToString(" \u00b7 "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (t.cover != null) {
            // 1.190: 封面加载失败时整块撤掉——否则右侧会留一块 84×60 空洞，把标题压窄换行
            var coverOk by remember(t.cover) { mutableStateOf(true) }
            if (coverOk) {
                AsyncImage(
                    // 1.190: 84×60dp 小图位走 CDN 缩略图（原图 155KB → 约 14KB）
                    model = thumbnailUrl(t.cover, 240),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onError = { coverOk = false },
                    modifier = Modifier
                        .size(width = 84.dp, height = 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

/** \u56de\u5e16\u884c\uff1a\u6240\u5728\u5e16\u6807\u9898 + \u5185\u5bb9\u7eaf\u6587\u672c + \u65f6\u95f4/\u70b9\u4eae */
@Composable
private fun ProfileReplyRow(r: HupuProfileReply, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (r.tid.isNotEmpty()) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (r.threadTitle.isNotBlank()) {
            Text(
                "\u56de\u590d\uff1a${r.threadTitle}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (r.content.isNotBlank()) {
            Text(
                r.content,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            listOf(r.formatTime, "\u70b9\u4eae ${formatCount(r.lights)}")
                .filter { it.isNotBlank() }
                .joinToString(" \u00b7 "),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** \u767b\u5f55\u6001\u4e94 Tab \u7edf\u4e00\u5217\u8868\u9879 */
// \u662f\u5426\u6eda\u52a8\u5230\u5e95\uff1a\u672b\u9879\u53ef\u89c1\u4e14\u8d34\u4f4f\u89c6\u53e3\u5e95\u90e8\uff1b\u521d\u59cb\u672a\u5e03\u5c40\u65f6\u6052\u4e3a false
private val androidx.compose.foundation.lazy.LazyListState.reachedBottom: Boolean
    get() {
        val li = layoutInfo
        val last = li.visibleItemsInfo.lastOrNull() ?: return false
        return last.index == li.totalItemsCount - 1 &&
            last.size > 0 &&
            last.offset + last.size <= li.viewportEndOffset
    }

private sealed class ListItem {
    data class Th(val t: HupuProfileThread) : ListItem()
    data class Rp(val r: HupuProfileReply) : ListItem()
    data class Fo(val f: HupuFollowUser) : ListItem()
}

/** \u5173\u6ce8\u5217\u8868\u884c\uff1a\u5934\u50cf + \u6635\u79f0 + \u7b49\u7ea7/\u7c89\u4e1d/\u52a0\u5165\u5929\u6570\uff0c\u70b9\u51fb\u8fdb\u5165\u5bf9\u65b9\u4e3b\u9875 */
@Composable
private fun ProfileFollowRow(f: HupuFollowUser, onOpenUser: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (f.puid.isNotEmpty()) Modifier.clickable { onOpenUser(f.puid) } else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = f.avatar.ifEmpty { null },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                f.name.ifEmpty { "\u864e\u6251\u7528\u6237" },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(
                    if (f.level > 0) "Lv.${f.level}" else "",
                    "\u7c89\u4e1d ${formatCount(f.fansNum)}",
                    f.joinDaysText,
                ).filter { it.isNotBlank() }.joinToString(" \u00b7 "),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
