package com.java.myapplication.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuThreadDetail
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 完整版帖子查看器（按 tid 直接打开）。
 *
 * 1.2xx（真机反馈：「私信里的站内链接要像信息流那样完整打开一篇帖子，而不是简化版」）：
 * 这里把通知列表里那套宿主逻辑封装成可复用组件 —— **分页加载 / 下拉刷新 / 回复排序 / 楼中楼全部可用**，
 * 并且 zIndex 抬到聊天页之上。以后再有新的入口直接调它，不会再出现"简化版"。
 *
 * 用法：`PmThreadViewer(tid) { openTid = null }`
 */
@Composable
internal fun PmThreadViewer(tid: String, onClose: () -> Unit) {
    val repo = remember { HupuRepository() }
    val scope = rememberCoroutineScope()

    // 状态按 tid 记忆：换一个帖子自动重置
    var detail by remember(tid) { mutableStateOf<HupuThreadDetail?>(null) }
    var loading by remember(tid) { mutableStateOf(true) }
    var loadingMore by remember(tid) { mutableStateOf(false) }
    var sortSeq by remember(tid) { mutableIntStateOf(0) }
    var closing by remember(tid) { mutableStateOf(false) }
    val floorStates = remember(tid) { androidx.compose.runtime.mutableStateMapOf<String, HupuFloorReplies>() }
    val floorLoading = remember(tid) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    val floorLoadingMore = remember(tid) { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    // 打开：有会话缓存先秒开，再强刷替换（与通知列表同款时效性策略）
    LaunchedEffect(tid) {
        loading = true
        val cached = repo.threadDetailCached(tid)
        if (cached != null) {
            detail = cached
            loading = false
            val fresh = repo.threadDetail(tid, refresh = true)
            if (fresh != null) detail = repo.mergeThreadDetail(detail, fresh)
        } else {
            delay(120)
            val d = repo.threadDetail(tid)
            if (d != null) detail = d
            loading = false
        }
    }

    fun openFloor(r: HupuReply) {
        if (floorStates.containsKey(r.pid)) return
        floorLoading[r.pid] = true
        scope.launch {
            delay(120)
            val fr = repo.floorReplies(tid, r)
            if (fr != null) floorStates[r.pid] = fr
            floorLoading[r.pid] = false
        }
    }

    fun loadFloorMore(pid: String, fr: HupuFloorReplies) {
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

    // zIndex 抬高：确保盖在聊天页（4 级）之上
    Box(Modifier.zIndex(9f)) {
        ThreadDetailOverlay(
            tid = tid,
            titleText = detail?.thread?.title ?: "帖子",
            detail = detail,
            loading = loading,
            loadingMore = loadingMore,
            closing = closing,
            onBack = { closing = true },
            onExitStart = {},
            onClosed = onClose,
            onRefresh = {
                val mySeq = sortSeq
                scope.launch {
                    loading = true
                    val cur = detail
                    val desc = cur?.descReplies == true
                    val nd = repo.threadDetailDirected(tid, desc, refresh = true)
                    if (nd != null && mySeq == sortSeq) {
                        detail = if (desc) nd else repo.mergeThreadDetail(cur, nd)
                    }
                    loading = false
                }
            },
            onLoadMore = {
                val c = detail ?: return@ThreadDetailOverlay
                if (loadingMore || !repo.hasMoreReplies(c)) return@ThreadDetailOverlay
                val mySeq = sortSeq
                scope.launch {
                    loadingMore = true
                    val next = repo.threadRepliesNext(tid, c)
                    if (next != null && mySeq == sortSeq) {
                        detail = next.copy(
                            replies = (c.replies + next.replies).distinctBy { it.pid },
                        )
                    }
                    loadingMore = false
                }
            },
            onSortChanged = { desc ->
                val mySeq = ++sortSeq
                val cur = detail
                if (cur != null && cur.descReplies != desc) {
                    scope.launch {
                        val d = repo.repliesDirected(tid, cur, desc)
                        if (d != null && mySeq == sortSeq) detail = d
                    }
                }
            },
            floorStates = floorStates,
            floorLoading = floorLoading.filterValues { it }.keys,
            floorLoadingMore = floorLoadingMore.filterValues { it }.keys,
            onOpenFloor = { r -> openFloor(r) },
            onFloorLoadMore = { pid, fr -> loadFloorMore(pid, fr) },
        )
    }
}