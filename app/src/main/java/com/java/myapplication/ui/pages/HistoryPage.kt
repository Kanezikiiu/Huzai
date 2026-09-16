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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuFloorReplies
import com.java.myapplication.data.HupuHistoryEntry
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuThread
import com.java.myapplication.data.HupuThreadDetail
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.formatCount
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 浏览记录页（盖入式二级页）：查看/查找/清空最近浏览的帖子。
 * 记录在帖子详情打开时写入（HupuPrefs.addHistory），上限 300 条。
 * 条目点击 → 帖子详情（本页自带 ThreadDetailOverlay 宿主，与其他页同款缓存策略）。
 */
@Composable
fun HistoryPage(onClose: () -> Unit) {
    val repo = remember { HupuRepository() }
    var query by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    // 弹窗打开时刻的条数快照（避免每帧重组读 SharedPreferences）
    var clearDialogCount by remember { mutableStateOf(0) }
    // 历史版本订阅：HupuPrefs.historyVersion 是 object observable state——
    // 任何写入（详情打开置顶/清空）即刻驱动下方列表重读，无需手动同步

    // 盖入动画：进入 0→1；返回动画结束后由父级移除本组件
    val progress = remember { Animatable(0f) }
    // 计数兜底：进入组合 +1，任何销毁路径（含退场中被重建/异常打断）离开组合 -1。
    // 正常退场仍由下方 closing 分支先 exit（Tab 栏与页面同步滑出）；onDispose 是
    // 防计数泄漏的安全网（exit 自带 count>0 守卫，双重 exit 无害）
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
    // 系统返回手势：预测性返回（与 TopicPickerPage 同款）
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { } // 吞掉
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

    // ---------- 帖子详情宿主（与其他页同款：状态外置缓存） ----------
    var openedThread by remember { mutableStateOf<HupuThread?>(null) }
    var threadClosing by remember { mutableStateOf(false) }
    var threadLoading by remember { mutableStateOf(false) }
    var threadLoadingMore by remember { mutableStateOf(false) }
    val threadDetails = remember { mutableStateMapOf<String, HupuThreadDetail>() }
    val floorStates = remember { mutableStateMapOf<String, HupuFloorReplies>() }
    val floorLoading = remember { mutableStateMapOf<String, Boolean>() }
    val floorLoadingMore = remember { mutableStateMapOf<String, Boolean>() }
    val scope = rememberCoroutineScope()

    fun openDetail(t: HupuThread) {
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
        threadClosing = false
        SecondaryPage.enter()
        openedThread = t
        threadLoading = !threadDetails.containsKey(t.tid)
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

    fun closeDetail() {
        threadClosing = true
    }

    // 1.123: 详情页时效性——每次进入都强刷；有缓存秒开第一帧，新数据到达后无缝替换
    LaunchedEffect(openedThread?.tid) {
        val t = openedThread ?: return@LaunchedEffect
        val tid = t.tid
        if (threadDetails.containsKey(tid)) {
            val old = threadDetails[tid]
            val fresh = repo.threadDetail(tid, refresh = true)
            if (fresh != null && openedThread?.tid == tid) {
                threadDetails[tid] = repo.mergeThreadDetail(old, fresh)
            }
        } else {
            threadLoading = true
            val cached = repo.threadDetailCached(tid)
            if (cached != null && openedThread?.tid == tid) {
                threadDetails[tid] = cached
                threadLoading = false
                val fresh = repo.threadDetail(tid, refresh = true)
                if (fresh != null && openedThread?.tid == tid) {
                    threadDetails[tid] = repo.mergeThreadDetail(threadDetails[tid], fresh)
                }
            } else {
                delay(300)
                if (threadDetails.containsKey(tid)) return@LaunchedEffect
                val d = repo.threadDetail(tid)
                if (d != null) threadDetails[tid] = d
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
        // 记录列表快照：键直接挂在对象版本号上——任何写入（打开置顶/清空）即刻刷新（含顶栏计数）
        val all = remember(HupuPrefs.historyVersion) { HupuPrefs.loadHistory() }
        Column(Modifier.fillMaxSize()) {
            // 顶栏：与主页频道自定义页同款——返回 + 标题 + 计数 + 右侧动作按钮
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
                    "浏览记录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${all.size}/${HupuPrefs.MAX_HISTORY}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = {
                    clearDialogCount = HupuPrefs.loadHistory().size
                    showClearDialog = true
                }) {
                    Icon(Icons.Rounded.Delete, contentDescription = "清空记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = { Text("搜索标题或版块", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 14.sp),
            )

            val list = if (query.isBlank()) all else all.filter {
                it.title.contains(query, true) || it.topicName.contains(query, true)
            }

            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (all.isEmpty()) "还没有浏览记录" else "没有匹配的记录",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.tid }) { e ->
                        HistoryRow(
                            e = e,
                            onOpen = {
                                openDetail(
                                    HupuThread(
                                        tid = e.tid,
                                        title = e.title,
                                        lights = e.lights,
                                        replies = e.replies,
                                        read = e.read,
                                        // 保留原版块名：重开时 addHistory 不再把 topicName 覆写成空
                                        topic = e.topicName.takeIf { it.isNotBlank() }?.let { name ->
                                            com.java.myapplication.data.HupuTopic(name = name)
                                        },
                                    )
                                )
                            },
                        )
                    }
                }
            }
        }

        // 帖子详情页（盖入式；组合顺序在列表之后 → 盖在其上）
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
                onClosed = {
                    threadClosing = false
                    openedThread = null
                    SecondaryPage.exit()
                },
                onRefresh = {
                    scope.launch {
                        threadLoading = true
                        val nd = repo.threadDetail(ot.tid, refresh = true)
                        if (nd != null) threadDetails[ot.tid] = repo.mergeThreadDetail(threadDetails[ot.tid], nd)
                        threadLoading = false
                    }
                },
                onLoadMore = {
                    val cur = threadDetails[ot.tid] ?: return@ThreadDetailOverlay
                    if (threadLoadingMore || cur.replyPage >= cur.replyTotalPages) return@ThreadDetailOverlay
                    scope.launch {
                        threadLoadingMore = true
                        val next = repo.threadReplies(ot.tid, cur.replyPage + 1)
                        if (next != null) {
                            val merged = (cur.replies + next.replies).distinctBy { it.pid }
                            threadDetails[ot.tid] = next.copy(replies = merged)
                        }
                        threadLoadingMore = false
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

    // 清空确认弹窗（iOS 风格：标题/正文居左、取消浅灰、确认蓝色）
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空浏览记录") },
            text = { Text("将删除全部 $clearDialogCount 条浏览记录，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    HupuPrefs.clearHistory()
                    showClearDialog = false
                }) { Text("清空", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
        )
    }
}

/** 历史条目行：标题 + 话题/时间/数据（点击进详情） */
@Composable
private fun HistoryRow(e: HupuHistoryEntry, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            e.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 复刻主页条目 MetaRow 形式：亮/评最前，依次版块名、时间（12sp 流式左对齐）
            Text(
                "💡${formatCount(e.lights)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "💬${formatCount(e.replies)}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (e.topicName.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    e.topicName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            formatVisitedAt(e.visitedAt).takeIf { it.isNotEmpty() }?.let {
                Spacer(Modifier.width(12.dp))
                Text(
                    it,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 相对时间（"3 分钟前"式，本地渲染） */
private fun formatVisitedAt(ts: Long): String {
    if (ts <= 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - ts
    return when {
        diff < DateUtils.MINUTE_IN_MILLIS -> "刚刚"
        diff < DateUtils.HOUR_IN_MILLIS -> "${diff / DateUtils.MINUTE_IN_MILLIS} 分钟前"
        diff < DateUtils.DAY_IN_MILLIS -> "${diff / DateUtils.HOUR_IN_MILLIS} 小时前"
        diff < DateUtils.DAY_IN_MILLIS * 7 -> "${diff / DateUtils.DAY_IN_MILLIS} 天前"
        else -> "${diff / (DateUtils.DAY_IN_MILLIS * 7)} 周前"
    }
}
