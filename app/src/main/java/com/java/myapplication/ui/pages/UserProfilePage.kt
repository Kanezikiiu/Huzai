package com.java.myapplication.ui.pages

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuFollowStore
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.data.HupuFollowUser
import com.java.myapplication.data.HupuFloorReplies
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
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * \u7528\u6237\u4e3b\u9875\uff08\u76d6\u5165\u5f0f\u4e8c\u7ea7\u9875\uff09\uff1a\u8d44\u6599\u5361\uff08\u65e0\u80cc\u666f\u56fe\uff0c\u65b9\u6848A\uff09+ \u7edf\u8ba1\u884c + \u53d1\u5e16/\u56de\u5e16\u53cc Tab\u3002
 * \u6570\u636e\uff1am.hupu.com/user/{euid} \u79fb\u52a8 UA SSR\uff0c\u4e00\u6b21\u8bf7\u6c42\u62ff\u5230\u8d44\u6599\u5361 + 20+20 \u6761\u76ee\u3002
 * \u6761\u76ee\u70b9\u51fb \u2192 \u5e16\u5b50\u8be6\u60c5\uff08\u672c\u9875\u81ea\u5e26 ThreadDetailOverlay \u5bbf\u4e3b\uff0c\u4e0e HistoryPage \u540c\u6b3e\u7f13\u5b58\u7b56\u7565\uff09\u3002
 */
@Composable
fun UserProfilePage(
    euid: String,
    onClose: () -> Unit,
) {
    val repo = remember { HupuRepository() }
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

    fun openDetail(t: HupuThread) {
        threadClosing = false
        SecondaryPage.enter()
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
            .zIndex(1f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // \u9876\u680f\uff1a\u8fd4\u56de + \u6635\u79f0\uff08\u907f\u8ba9\u72b6\u6001\u680f\uff09
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { closing = true }) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "\u8fd4\u56de",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
                Spacer(Modifier.width(48.dp))
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
                        // \u7edf\u8ba1\u884c
                        item(key = "stats") {
                            // 1.179: 点子数据直达「关注」tab 对应分组：
                            //   粉丝 → 关注我的/关注TA的（followType=2）
                            //   关注 → 我关注的/TA关注的（followType=1）
                            // 仅登录态可点（未登录无「关注」tab，做预防性禁用）
                            val openFollowers: (() -> Unit)? =
                                if (logged) ({ tab = 4; followType = 2 }) else null
                            val openFollowing: (() -> Unit)? =
                                if (logged) ({ tab = 4; followType = 1 }) else null
                            StatsRow(p, openFollowers, openFollowing)
                        }
                        // \u53cc Tab
                        item(key = "tabs") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            val d = threadDetails[ot.tid]
            ThreadDetailOverlay(
                tid = ot.tid,
                titleText = ot.title,
                detail = d,
                loading = threadLoading,
                loadingMore = threadLoadingMore,
                closing = threadClosing,
                onBack = { closeDetail() },
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
    }
}

/** \u8d44\u6599\u5361\uff1a\u5934\u50cf + \u6635\u79f0 + Lv \u5fbd\u7ae0 + \u7b49\u7ea7\u8fdb\u5ea6\u6761 + IP \u5c5e\u5730/\u52a0\u5165\u5929\u6570\uff08\u65e0\u80cc\u666f\u56fe\uff0c\u7528\u6237\u786e\u8ba4\uff09 */
@Composable
private fun ProfileCard(
    p: HupuUserProfile,
    showFollow: Boolean = false,
    followed: Boolean? = null,
    followBusy: Boolean = false,
    onToggleFollow: () -> Unit = {},
    onPm: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.avatar.isNotEmpty()) {
                AsyncImage(
                    model = p.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (p.levelDesc.isNotBlank() || p.locationStr.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p.levelDesc.isNotBlank()) {
                            Text(
                                p.levelDesc,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        runCatching {
                                            Color(android.graphics.Color.parseColor(p.levelColor))
                                        }.getOrDefault(MaterialTheme.colorScheme.primary)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        if (p.locationStr.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "IP \u5c5e\u5730 ${p.locationStr}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // 1.130: 私信小图标按钮（置于关注按钮左侧；非本人主页才显示）
            if (onPm != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .clickable { onPm() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        HupuIcons.Mail,
                        contentDescription = "发私信",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // 1.127: 状态未定时不渲染按钮，避免先显示「关注」再跳成「已关注」
            if (showFollow && followed != null) {
                Spacer(Modifier.width(10.dp))
                FollowButton(followed == true, followBusy, onToggleFollow)
            }
        }
        // \u7b49\u7ea7\u8fdb\u5ea6\u6761\uff08\u5b98\u65b9\u8272\uff09
        if (p.levelScore > 0 && p.nextLevelScore > p.levelScore) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "\u7b49\u7ea7\u79ef\u5206 ${formatCount(p.levelScore.toInt())}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "\u4e0b\u4e00\u7ea7 ${formatCount(p.nextLevelScore.toInt())}",
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
                            .background(
                                runCatching {
                                    Color(android.graphics.Color.parseColor(p.levelColor))
                                }.getOrDefault(MaterialTheme.colorScheme.primary)
                            ),
                    )
                }
            }
        // 1.190: 「加入虎扑 N 天」与「声望」同行——声望在天数右侧（与网页资料卡一致）
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p.regTimeStr.isNotBlank()) {
                Text(
                    p.regTimeStr,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (p.reputation > 0) {
                if (p.regTimeStr.isNotBlank()) Spacer(Modifier.width(12.dp))
                Text(
                    "\u58f0\u671b ${formatCount(p.reputation)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        }
    }
}

/** 1.126 关注按钮：未关注 = 主题色实心 + 加号；已关注 = 浅灰底。即时反馈，失败由调用方回滚 */
@Composable
private fun FollowButton(followed: Boolean, busy: Boolean, onClick: () -> Unit) {
    val bg = if (followed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    else MaterialTheme.colorScheme.primary
    val fg = if (followed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
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
            if (followed) "\u5df2\u5173\u6ce8" else "\u5173\u6ce8",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = fg,
        )
    }
}

/** \u7edf\u8ba1\u884c\uff1a\u7c89\u4e1d/\u5173\u6ce8/\u88ab\u70b9\u4eae/\u88ab\u63a8\u8350\uff08\u540e\u4e24\u8005\u4e0d\u5c01\u9876\uff09 */
@Composable
private fun StatsRow(
    p: HupuUserProfile,
    onOpenFollowers: (() -> Unit)? = null,
    onOpenFollowing: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatCell("\u7c89\u4e1d", p.followers, onOpenFollowers)
        StatCell("\u5173\u6ce8", p.following, onOpenFollowing)
        StatCell("\u88ab\u70b9\u4eae", p.beLightCount)
        StatCell("\u88ab\u63a8\u8350", p.beRecommendCount)
    }
}

@Composable
private fun StatCell(label: String, value: Int, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // 1.179: 可点（粉丝/关注）时加点击反馈；不可点保持原样（不占额外尺寸）
        modifier = if (onClick != null) {
            Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        } else Modifier,
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
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable(onClick = onClick)
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
