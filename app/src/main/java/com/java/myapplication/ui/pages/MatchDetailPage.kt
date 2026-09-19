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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuScoreGroup
import com.java.myapplication.data.HupuScoreItem
import com.java.myapplication.data.HupuScoreTree
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.HeroBadgeAvatar
import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.tapGuard

/**
 * 比赛详情二级页（评分页 → 赛程卡点击进入）。
 * 三层数据一次拉全：比赛 → 对局[]（横滑条切换）→ 选手评分列表。
 * 盖入式转场：进入从右滑入、返回滑回右侧，与专区话题流同款。
 */
@Composable
fun MatchDetailOverlay(
    title: String,
    tree: HupuScoreTree?,
    loading: Boolean,
    closing: Boolean,
    onBack: () -> Unit,
    /** 1.183: 退场动画开始即回调——父级据此同步减二级页计数，使 Tab 栏与页面同步 Q 弹回归（与「我的」页二级页同款手感） */
    onExitStart: () -> Unit = {},
    onClosed: () -> Unit,
    onRefresh: () -> Unit,
    onOpenPlayer: (HupuScoreItem) -> Unit,
    /** 赛程卡的两队（memberId/memberName）：与选手 infoJson.teamId 同命名空间，
     *  用于三层结构（LOL）在选手列表上方生成「全部/主队/客队/趣评」分类子 Tab（官方详情页同款） */
    teams: List<Pair<String, String>> = emptyList(),
    /** 服务端分组（getSubGroups）：flat 结构（WNBA/CUBA/VAL/CBA/女篮等）的分类条数据源；
     *  空列表 = 无分组，退回 teamId 本地过滤（LOL 三层结构方案） */
    groups: List<HupuScoreGroup> = emptyList(),
    /** 分组成员缓存（groupId → 该组选手 bizId 集合的持有列表），由 ScorePage 预取 */
    groupMembers: Map<Long, List<HupuScoreItem>> = emptyMap(),
) {
    // 盖入动画：进入 0→1；返回动画结束后由父级移除本组件
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(280))
    }
    LaunchedEffect(closing) {
        if (closing) {
            onExitStart()
            progress.animateTo(0f, tween(280))
            onClosed()
        }
    }
    // 系统返回手势：预测性返回（单一 handler——多个 handler 共存时后组合者永远优先）。
    // - 退场动画播放中：吞掉手势（防止漏到系统直接退出应用）
    // - 其余：页面跟随手指实时滑出（progress: 0→1 跟手），松手未过阈值弹回、过阈值退出
    PredictiveBackHandler { events ->
        if (closing) {
            events.collect { } // 吞掉
            return@PredictiveBackHandler
        }
        try {
            events.collect { event ->
                // 手势进度实时驱动盖出动画（页面跟手右滑）
                progress.snapTo(1f - event.progress)
            }
            // 越过系统提交阈值 → 播完剩余动画并退出
            progress.animateTo(0f, tween(120))
            onBack()
        } catch (e: CancellationException) {
            // 未过阈值松手 → 弹回原位
            progress.animateTo(1f, tween(200))
            throw e
        }
    }

    // 当前选中对局（默认第1局）
    var selectedRoundId by remember { mutableStateOf<String?>(null) }
    val currentRound = tree?.rounds?.find { it.bizId == selectedRoundId } ?: tree?.rounds?.firstOrNull()
    // 队伍分类子 Tab（"全部"为默认）：key = memberId 或 "fun"（趣评=中立角色）
    var selectedGroup by remember { mutableStateOf("all") }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer {
                translationX = (1f - progress.value) * size.width
            }
            .background(MaterialTheme.colorScheme.background)
            // 穿透守卫：空白点击不落穿到下层页面
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：返回 + 比赛名 + 总评分人数
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    tree?.let {
                        Text(
                            "${formatCount(it.scorePersonCount)}人评分",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // 内容区：加载中 / 失败重试 / 正常
            val state = if (tree != null) "ok" else if (loading) "loading" else "error"
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = loading && tree != null,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxSize(),
            ) {
                Crossfade(targetState = state, animationSpec = tween(180), label = "matchDetailSwitch") { s ->
                    when (s) {
                        "loading" -> SkeletonHome()
                        "error" -> ErrorRetry { onRefresh() }
                        else -> {
                            val t = tree
                            if (t == null) {
                                ErrorRetry { onRefresh() }
                            } else {
                                TreeContent(t, currentRound?.bizId, teams = teams, groups = groups, groupMembers = groupMembers, selectedGroup = selectedGroup, onGroupChange = { selectedGroup = it }, onSelectRound = { selectedRoundId = it }, onOpenPlayer = onOpenPlayer)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 树内容：对局横滑条（MOBA 三层结构）+ 队伍分类子 Tab + 选手评分列表；flat 结构直接列选手 */
@Composable
private fun TreeContent(
    tree: HupuScoreTree,
    selectedRoundId: String?,
    teams: List<Pair<String, String>>,
    /** 服务端分组定义（getSubGroups）：非空时走服务端分组方案（flat 结构） */
    groups: List<HupuScoreGroup>,
    /** groupId → 该组成员（groupAndSubNodes 解析结果，bizId 与树 players 同命名空间） */
    groupMembers: Map<Long, List<HupuScoreItem>>,
    selectedGroup: String,
    onGroupChange: (String) -> Unit,
    onSelectRound: (String) -> Unit,
    onOpenPlayer: (HupuScoreItem) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        // 对局横滑条（第1局…第N局，正序）——仅 MOBA 三层结构显示；
        // 传统体育（NBA 等）children 直接是选手（flat），没有对局层
        if (!tree.flat) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clipToBounds()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(tree.rounds, key = { it.bizId }) { round ->
                    Chip(
                        text = round.name,
                        selected = round.bizId == selectedRoundId,
                    ) { onSelectRound(round.bizId) }
                }
            }
        }
        // 选手评分列表（flat 结构直接用唯一虚拟轮；MOBA 用选中的对局）
        val round = tree.rounds.find { it.bizId == selectedRoundId } ?: tree.rounds.firstOrNull()
        if (round == null || round.players.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "该对局暂无评分",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // 分类子 Tab，两种方案：
            // ① 服务端分组（getSubGroups，官方对所有赛事通用）：flat 结构（WNBA/CUBA/VAL/CBA/女篮等），
            //    Tab = 全部 + 各分组名（如「全部/美国女篮/中国女篮」，含教练/裁判等中立角色按官方归属入组）
            // ② teamId 本地过滤：三层结构（LOL）——赛程卡两队 + 趣评（无 teamId 中立角色）
            // 无数据分组不显示：CS2 三层结构每张地图一套独立分组与选手节点（bizId 逐图不同），
            // 其他地图的分组与当前对局选手无交集、点开必为空态——只保留有成员交集（有数据）的分组，
            // 并按分组名去重（地图间同队分组重复时只留一个，与官方单地图分类条一致）
            val roundPlayerIds = round.players.map { it.bizId }.toSet()
            val serverGroups = groups
                .filter { g ->
                    val mem = groupMembers[g.groupId]
                    !mem.isNullOrEmpty() && mem.any { it.bizId in roundPlayerIds }
                }
                .distinctBy { it.name }
            // 切换对局（地图）后旧选中的分组可能已不在当前对局分组里 → 回退「全部」，避免命中空态
            val selected = if (selectedGroup.startsWith("g-") && serverGroups.none { "g-${it.groupId}" == selectedGroup }) "all" else selectedGroup
            val teamTabs = if (serverGroups.isEmpty()) teams.filter { (id, _) -> round.players.any { it.teamId == id } } else emptyList()
            val hasNeutral = serverGroups.isEmpty() && round.players.any { it.teamId == null }
            val showGroupBar = serverGroups.isNotEmpty() || teamTabs.isNotEmpty() && (teamTabs.size >= 2 || hasNeutral)
            if (showGroupBar) {
                val tabs = buildList {
                    add("all" to "全部")
                    if (serverGroups.isNotEmpty()) {
                        serverGroups.forEach { g -> add("g-${g.groupId}" to g.name) }
                    } else {
                        teamTabs.forEach { add(it) }
                        if (hasNeutral) add("fun" to "趣评")
                    }
                }
                // 分类条可横向滚动（和平精英等战队多的赛事 Tab 超宽会挤爆固定 Row）
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(tabs, key = { it.first }) { (key, title) ->
                        val active = key == selected
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onGroupChange(key) },
                        ) {
                            Text(
                                title,
                                fontSize = 14.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                color = if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(3.dp))
                            Box(
                                Modifier
                                    .width(if (active) 20.dp else 0.dp)
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            val players = when {
                !showGroupBar -> round.players
                selected == "all" -> round.players
                serverGroups.isNotEmpty() && selected.startsWith("g-") -> {
                    // 服务端分组过滤：按该组成员 bizId 集合反查（成员含教练/裁判等全部节点）
                    val memberIds = selected.removePrefix("g-").toLongOrNull()
                        ?.let { gid -> groupMembers[gid]?.map { it.bizId }?.toSet() }
                    if (memberIds.isNullOrEmpty()) round.players
                    else round.players.filter { it.bizId in memberIds }
                }
                selected == "fun" -> round.players.filter { it.teamId == null }
                else -> round.players.filter { it.teamId == selected }
            }
            if (players.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "该分组暂无评分",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(players, key = { it.bizId }) { p ->
                        PlayerScoreRow(p, onClick = { onOpenPlayer(p) })
                    }
                }
            }
        }
    }
}

/** 选手评分行：头像 + 名字 + 热评 | 大字分数（点击进入选手详情） */
@Composable
private fun PlayerScoreRow(p: HupuScoreItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeroBadgeAvatar(avatar = p.image, heroIcon = p.heroIcon, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                p.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (p.hotComment.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    "「${p.hotComment}」",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                p.scoreAvg,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor(p.scoreAvg),
            )
            Text(
                "${formatCount(p.scorePersonCount)}人",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 人数格式化：117494 → 11.7万 */
internal fun formatCount(n: Long): String = when {
    n >= 10000 -> String.format(java.util.Locale.CHINA, "%.1f万", n / 10000.0)
    else -> n.toString()
}

/** 分数配色：>=7 绿 / 5-6.9 橙 / <5 红（虎扑评分惯例，供本页与 ScorePage 共用） */
internal fun scoreColor(scoreNum: String): androidx.compose.ui.graphics.Color {
    val v = scoreNum.toDoubleOrNull() ?: return androidx.compose.ui.graphics.Color.Unspecified
    return when {
        v >= 7.0 -> androidx.compose.ui.graphics.Color(0xFF30A46C)
        v >= 5.0 -> androidx.compose.ui.graphics.Color(0xFFF5A524)
        else -> androidx.compose.ui.graphics.Color(0xFFE5484D)
    }
}

