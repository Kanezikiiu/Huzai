package com.java.myapplication.ui.pages

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.java.myapplication.ui.components.animateChipCenterTo
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuCommonSubject
import com.java.myapplication.data.HupuCommonTree
import com.java.myapplication.data.HupuImage
import com.java.myapplication.data.HupuMatch
import com.java.myapplication.data.HupuMatchApi
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.isLocalStickerUrl
import com.java.myapplication.data.HupuMatchDay
import com.java.myapplication.data.HupuMatchTeam
import com.java.myapplication.data.HupuPlayerScore
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.data.HupuUiSignals
import com.java.myapplication.data.HupuScoreGroup
import com.java.myapplication.data.HupuScoreItem
import com.java.myapplication.data.HupuScoreTree
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuSelfDetail
import com.java.myapplication.data.GrandExpandState
import com.java.myapplication.data.flattenWithDescendants
import com.java.myapplication.data.ScoreCommentState
import com.java.myapplication.data.mergeWithOptimistic
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.ErrorRetry
import com.java.myapplication.ui.components.PageHeader
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.SkeletonHome
import com.java.myapplication.ui.components.normalizeCover
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** 赛程列表（按日分组、按日期升序）。默认定位到今天：当天有比赛显示当天，
 *  当天无比赛显示最近的过往比赛日（更早才全部是未来赛程时取第一天）；
 *  上滑自然查看过往、下滑查看未来。刷新后重新定位。 */
@Composable
internal fun ScheduleList(days: List<HupuMatchDay>, resetKey: String, onOpenMatch: (HupuMatch) -> Unit) {
    val today = remember { java.time.LocalDate.now().toString() }
    // 目标定位项索引：日期头 + 当日比赛卡的累计偏移
    fun targetItemIndex(): Int {
        var todayIdx = -1
        var lastPastIdx = -1
        days.forEachIndexed { i, d ->
            if (d.dayTime == today) todayIdx = i
            if (d.dayTime <= today) lastPastIdx = i
        }
        val dayIdx = when {
            todayIdx >= 0 -> todayIdx       // 当天有比赛
            lastPastIdx >= 0 -> lastPastIdx // 当天无比赛 → 最近的过往日（含今天之前最后一天）
            else -> 0                       // 全是未来赛程 → 第一天
        }
        var item = 0
        for (i in 0 until dayIdx) item += 1 + days[i].matches.size
        return item
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetItemIndex())
    // 数据变化（首次加载/切换项目/下拉刷新）→ 重新定位到目标日期
    LaunchedEffect(days, resetKey) {
        listState.scrollToItem(targetItemIndex())
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        days.forEach { day ->
            // 日期头（今天高亮标注）
            val isToday = day.dayTime == today
            item(key = "day-${day.dayTime}") {
                Text(
                    day.dateBlock,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            // 当日比赛卡
            items(day.matches, key = { it.matchId }) { m ->
                MatchCard(m, onOpen = onOpenMatch)
            }
        }
    }
}

/** 单场比赛卡：队伍比分行 + 选手评分卡（有评分钥匙的已完赛/进行中比赛可点进详情） */
@Composable
internal fun MatchCard(m: HupuMatch, onOpen: (HupuMatch) -> Unit) {
    val openable = m.scoreBizNo != null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (openable) Modifier.clickable { onOpen(m) } else Modifier)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 队伍比分行（左队靠左、右队靠右，与比分等距对称）；
        // 多队赛事（和平精英/绝地求生/奥运团体赛等）：memberInfos 是空占位 → home/away 为 null，
        // 走「赛事名居中」形态，不渲染空白队伍槽
        // 多队赛事（和平精英/绝地求生/奥运团体赛等）：memberInfos 是空占位 → 无有效队伍，
        // 走「赛事名居中」形态，不渲染空白队伍槽；双保险：即使队伍对象存在但双方都无名字，
        // 同样按多队形态处理
        val multiTeam = (m.home == null && m.away == null) ||
            (m.home?.name.isNullOrBlank() && m.away?.name.isNullOrBlank())
        if (multiTeam) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(2.dp))
                Text(
                    m.matchName.ifBlank { m.introduction },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // 状态行（多队赛事显示完整开始时间更有信息量）
                Text(
                    m.startTimeText.ifBlank { m.statusDesc },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamScore(m.home, isWinner = m.winnerMemberId == m.home?.memberId && m.home != null, alignEnd = false, modifier = Modifier.weight(1f))
            // 中间比分区
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp)) {
                if (m.home?.baseScore?.isNotEmpty() == true && m.away?.baseScore?.isNotEmpty() == true) {
                    Text(
                        "${m.home.baseScore} : ${m.away.baseScore}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Text(
                        m.startTimeText,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(2.dp))
                // 状态行并入开始时间（仅取 HH:mm 部分；未开赛的卡中间已显示完整时间，不重复）
                val timeOnly = m.startTimeText.substringAfter(' ', "")
                val statusText = if (timeOnly.isNotBlank() && m.status != "NOT_STARTED") "$timeOnly · ${m.statusDesc}" else m.statusDesc
                Text(
                    statusText,
                    fontSize = 11.sp,
                    color = when (m.status) {
                        "IN_PROGRESS" -> MaterialTheme.colorScheme.primary
                        "COMPLETED" -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.tertiary
                    },
                )
            }
            TeamScore(m.away, isWinner = m.winnerMemberId == m.away?.memberId && m.away != null, alignEnd = true, modifier = Modifier.weight(1f))
        }
        } // end else (1v1 队伍比分行)
        // 赛事名（如 LPL第三赛段组内赛）
        if (m.introduction.isNotBlank()) {
            Text(
                m.introduction,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // 选手评分卡
        m.playerScore?.let { PlayerScoreCard(it, matchScoreCount = m.scoreCountText) }
    }
}

/** 队伍：logo + 名称（胜方加粗；镜像参数控制左/右槽位对齐方式） */
@Composable
internal fun TeamScore(
    t: HupuMatchTeam?,
    isWinner: Boolean,
    alignEnd: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
    ) {
        // 右槽位镜像：logo 移到名字右侧
        if (alignEnd) {
            Text(
                t?.name ?: "",
                fontSize = 15.sp,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = normalizeCover(t?.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
        } else {
            AsyncImage(
                model = normalizeCover(t?.logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(32.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                t?.name ?: "",
                fontSize = 15.sp,
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
