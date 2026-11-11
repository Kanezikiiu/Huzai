package com.java.myapplication.ui.pages

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.java.myapplication.data.HupuAuthor
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuHistoryEntry
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.ui.components.SecondaryPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 1.343：**帖子详情托管器** —— 首页与搜索层共用同一份实现。
 *
 * ## 为什么抽出来
 *
 * 之前搜索层用的是「精简版宿主」（只有正文/回复/楼中楼，没有排序跳页、页码、刷新合并等），
 * 而首页那份是完整实现 —— 两套代码必然走样（真机反馈「不要精简版」）。
 * 这里把**首页那份完整逻辑**原样抽成可复用组件，两边共用，能力完全一致
 * （编辑/删除/投票这些 UI 在 [ThreadDetailOverlay] 内部，只要传参齐全就自动具备）。
 *
 * ## 用 hook 形态而不是直接 Composable
 *
 * 详情页需要与「列表/搜索」共用同一棵组合树（列表不能因为打开详情而被卸载，
 * 否则出现「一瞬间返回最外层再打开」的闪跳）。所以这里只返回状态与渲染函数，
 * 由调用方在**同一层**决定何时把 overlay 叠上去。
 */
internal class ThreadDetailHostState(
    val repo: HupuRepository,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    var openedThread by mutableStateOf<HupuThread?>(null)
        private set

    /** 本层「已计数」标记 —— 连点多个条目时 enter 只发生一次，防 Tab 栏计数泄漏 */
    var entered by mutableStateOf(false)
        private set

    var closing by mutableStateOf(false)
        private set

    var loading by mutableStateOf(false)
        private set

    var loadingMore by mutableStateOf(false)
        private set

    var sortSeq by mutableIntStateOf(0)
        private set

    val details = mutableStateMapOf<String, HupuThreadDetail>()
    val floorStates = mutableStateMapOf<String, HupuFloorReplies>()
    val floorLoading = mutableStateMapOf<String, Boolean>()
    val floorLoadingMore = mutableStateMapOf<String, Boolean>()

    fun open(t: HupuThread) {
        HupuPrefs.addHistory(
            HupuHistoryEntry(
                tid = t.tid,
                title = t.title,
                topicName = t.topic?.name ?: "",
                lights = t.lights,
                replies = t.replies,
                read = t.read,
                visitedAt = System.currentTimeMillis(),
            )
        )
        closing = false
        if (!entered) { entered = true; SecondaryPage.enter() }
        openedThread = t
        // 加载态在交互瞬间同步置位：无缓存 → 骨架屏第一帧出现
        loading = !details.containsKey(t.tid)
    }

    fun requestClose() { closing = true }

    /** 退场开始即回收二级页计数（Tab 栏与页面同步 Q 弹回归） */
    fun onExitStart() {
        if (entered) { entered = false; SecondaryPage.exit() }
    }

    fun onClosed(tid: String) {
        closing = false
        openedThread = null
        // 关闭即清掉该帖缓存 —— 页码跳转是临时视图状态，重开必须回到第 1 页
        details.remove(tid)
    }

    fun refresh(tid: String) {
        val mySeq = sortSeq
        scope.launch {
            loading = true
            val cur = details[tid]
            val desc = cur?.descReplies == true
            val nd = repo.threadDetailDirected(tid, desc, refresh = true)
            if (nd != null && mySeq == sortSeq && openedThread?.tid == tid) {
                details[tid] = if (desc) nd else repo.mergeThreadDetail(cur, nd)
            }
            loading = false
        }
    }

    fun loadMore(tid: String) {
        val cur = details[tid] ?: return
        if (loadingMore || !repo.hasMoreReplies(cur)) return
        val mySeq = sortSeq
        scope.launch {
            loadingMore = true
            val next = repo.threadRepliesNext(tid, cur)
            if (next != null && mySeq == sortSeq && openedThread?.tid == tid) {
                details[tid] = next.copy(
                    replies = (cur.replies + next.replies).distinctBy { it.pid },
                )
            }
            loadingMore = false
        }
    }

    fun jumpReplyPage(tid: String, page: Int) {
        val cur = details[tid] ?: return
        if (loadingMore) return
        val mySeq = sortSeq
        scope.launch {
            loadingMore = true
            val jumped = repo.threadRepliesJump(tid, cur, page)
            if (jumped != null && mySeq == sortSeq && openedThread?.tid == tid) {
                details[tid] = jumped
            }
            loadingMore = false
        }
    }

    fun changeSort(tid: String, desc: Boolean) {
        // 每次意图推进序号 —— 迟到的旧响应据此被丢弃（连点不再来回闪）
        val mySeq = ++sortSeq
        val cur = details[tid]
        if (cur != null && cur.descReplies != desc) {
            scope.launch {
                val d = repo.repliesDirected(tid, cur, desc)
                if (d != null && mySeq == sortSeq && openedThread?.tid == tid) details[tid] = d
            }
        }
    }

    fun openFloor(tid: String, r: HupuReply) {
        if (floorStates.containsKey(r.pid)) return
        floorLoading[r.pid] = true
        scope.launch {
            delay(120)
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
                floorStates[pid] = cur.copy(
                    subReplies = (cur.subReplies + next.subReplies).distinctBy { it.pid },
                    hasMore = next.hasMore,
                )
            }
            floorLoadingMore[pid] = false
        }
    }

    /** 打开后拉详情：有缓存秒开 + 后台强刷；无缓存先磁盘再网络（与首页完全同款） */
    suspend fun ensureLoaded() {
        val t = openedThread ?: return
        val tid = t.tid
        val mySeq = sortSeq
        if (details.containsKey(tid)) {
            val old = details[tid]
            val fresh = repo.threadDetail(tid, refresh = true)
            if (fresh != null && openedThread?.tid == tid && mySeq == sortSeq) {
                details[tid] = repo.mergeThreadDetail(old, fresh)
            }
        } else {
            loading = true
            val cached = repo.threadDetailCached(tid)
            if (cached != null && openedThread?.tid == tid && mySeq == sortSeq) {
                details[tid] = cached
                loading = false
                val fresh = repo.threadDetail(tid, refresh = true)
                if (fresh != null && openedThread?.tid == tid && mySeq == sortSeq) {
                    details[tid] = repo.mergeThreadDetail(details[tid], fresh)
                }
            } else {
                delay(120) // 盖入动画 280ms + 余量
                if (details.containsKey(tid)) return
                val d = repo.threadDetail(tid)
                if (d != null && mySeq == sortSeq) details[tid] = d
                if (openedThread?.tid == tid) loading = false
            }
        }
    }
}

@Composable
internal fun rememberThreadDetailHost(repo: HupuRepository): ThreadDetailHostState {
    val scope = rememberCoroutineScope()
    val state = remember(repo) { ThreadDetailHostState(repo, scope) }
    LaunchedEffect(state.openedThread?.tid) {
        state.ensureLoaded()
    }
    return state
}

/**
 * 渲染详情覆盖层（若当前有打开的帖子）。
 * 抽成独立函数是为了让首页与搜索层用**完全相同**的参数装配方式，避免再次走样。
 */
@Composable
internal fun RenderThreadDetailOverlay(
    state: ThreadDetailHostState,
    onOpenUser: (String) -> Unit = {},
) {
    val ot = state.openedThread ?: return
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (state.entered) state.onExitStart() }
    }
    ThreadDetailOverlay(
        tid = ot.tid,
        titleText = ot.title,
        detail = state.details[ot.tid],
        loading = state.loading,
        loadingMore = state.loadingMore,
        closing = state.closing,
        onBack = { state.requestClose() },
        onExitStart = { state.onExitStart() },
        onClosed = { state.onClosed(ot.tid) },
        onRefresh = { state.refresh(ot.tid) },
        onLoadMore = { state.loadMore(ot.tid) },
        onJumpReplyPage = { p -> state.jumpReplyPage(ot.tid, p) },
        onSortChanged = { desc -> state.changeSort(ot.tid, desc) },
        onOpenUser = onOpenUser,
        floorStates = state.floorStates,
        floorLoading = state.floorLoading.filterValues { it }.keys,
        floorLoadingMore = state.floorLoadingMore.filterValues { it }.keys,
        onOpenFloor = { r -> state.openFloor(ot.tid, r) },
        onFloorLoadMore = { pid, fr -> state.loadFloorMore(ot.tid, pid, fr) },
    )
}