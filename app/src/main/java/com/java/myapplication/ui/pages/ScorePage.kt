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

/**
 * 评分页：6 大电竞项目赛程 + 选手评分（浏览，打分需登录暂不做）
 * 数据：match-api.hupu.com 匿名接口（实测可用）
 */
@Composable
fun ScorePage(modifier: Modifier = Modifier) {
    val repo = remember { HupuRepository() }
    // 评分频道（即时生效）：订阅 scoreGamesVersion——ScorePickerPage 每次保存后
    // version+1，此处 remember 重算，横滑条立即增删/重排，无需重启或重进页面
    val effectiveGames = remember(HupuPrefs.scoreGamesVersion) {
        HupuPrefs.effectiveScoreGames(HupuMatchApi.GAMES)
    }
    // 项目横滑条单选（默认英雄联盟）
    var selectedGame by remember { mutableStateOf(HupuMatchApi.GAMES.first().first) }
    // 各项目的赛程缓存（切回不重载）
    val schedules = remember { mutableStateMapOf<String, List<HupuMatchDay>>() }
    // 电竞项目赛程的加载态：进电竞 chip 时由 onClick/LaunchedEffect 同步置位
    var loadingGame by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var refreshVersion by remember { mutableIntStateOf(0) }

    // ---------- 比赛详情二级页（盖入式；状态外置：返回再进不重载） ----------
    val scope = rememberCoroutineScope()
    var openedMatch by remember { mutableStateOf<HupuMatch?>(null) }
    var detailClosing by remember { mutableStateOf(false) }
    var detailLoading by remember { mutableStateOf(false) }
    val matchTrees = remember { mutableStateMapOf<String, HupuScoreTree>() }
    // 服务端分组（getSubGroups）：flat 结构的「全部/队名/趣评」分类条数据源（官方所有赛事通用）
    val matchGroups = remember { mutableStateMapOf<String, List<HupuScoreGroup>>() }
    // 分组成员缓存（groupAndSubNodes，按 rootNodeId → groupId 键）
    val groupMembers = remember { mutableStateMapOf<Long, List<HupuScoreItem>>() }

    // ---------- 选手详情三级页（盖入式；评分分布 + 热评 + 评论流） ----------
    var openedPlayer by remember { mutableStateOf<HupuScoreItem?>(null) }
    // 评论排序（官方 queryType：brightest=最亮 / latest=最晚 / earliest=最早）。
    // 默认最亮（对齐官方客户端）；每位选手打开时重置为默认。
    var playerSort by remember { mutableStateOf("brightest") }
    // 排序是否处于默认态：仅默认态下「最亮无评论」才自动降级到最晚，
    // 用户手动切到最亮且为空时保持空态（不抢用户的操作）
    var playerSortIsDefault by remember { mutableStateOf(true) }
    // 各排序的评论缓存（key = "bizType-bizId-queryType"，切排序/切选手互不干扰、返回不重载）
    val playerComments = remember { mutableStateMapOf<String, ScoreCommentState>() }
    // 1.59 切排序「留旧显示」：displaySort=数据源索引（新排序数据到位才切换），
    // playerSort=排序条高亮（点击即时反馈）；switching=切换加载中角标
    var playerDisplaySort by remember { mutableStateOf("brightest") }
    var playerSwitching by remember { mutableStateOf(false) }
    var playerClosing by remember { mutableStateOf(false) }
    var playerLoading by remember { mutableStateOf(false) }
    var playerLoadingMore by remember { mutableStateOf(false) }
    val selfDetails = remember { mutableStateMapOf<String, HupuSelfDetail>() }

    // ---------- 评分评论楼中楼 sheet（subCommentList 全量；母评论 → 子回复流） ----------
    var scoreSubSheet by remember { mutableStateOf<SubCommentSheetData?>(null) }
    var subSheetLoading by remember { mutableStateOf(false) }
    var subSheetLoadingMore by remember { mutableStateOf(false) }
    var subSheetClosing by remember { mutableStateOf(false) }
    // 楼中楼内排序（默认最亮，对齐官方 sheet defaultCommentOrderBy:"brightest"）
    var subSheetSort by remember { mutableStateOf("brightest") }
    // 各母评论的子回复缓存（key="bizType-bizId-parentCommentId-sortKey"，切排序互不干扰）
    val subComments = remember { mutableStateMapOf<String, ScoreCommentState>() }
    // ---------- 1.64 评分 & 点亮（登录态；key 与 PlayerDetailPage 的 litKey 同构） ----------
    // 提示条（点亮/打分结果；2600ms 自动清除，点击立即消失——与帖子详情页同手感）
    var scoreToast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(scoreToast) {
        if (scoreToast != null) {
            kotlinx.coroutines.delay(2600)
            scoreToast = null
        }
    }
    // 打分面板（按选手键控：同一时间只挂一个打开位）
    var scorePanelOpen by remember { mutableStateOf(false) }
    var scorePanelKey by remember { mutableStateOf("") }
    var scorePanelSubmitting by remember { mutableStateOf(false) }
    // 本会话已提交的评分（打分成功即时回显；k=选手键）
    val myScores = remember { mutableStateMapOf<String, Int>() }
    // 点亮状态（k=litKey = subjectId:commentId；本地乐观值优先于 hasLight）
    val scoreLightState = remember { mutableStateMapOf<String, Boolean>() }
    fun lightKey(c: com.java.myapplication.data.HupuScoreComment): String =
        (c.subjectId.ifEmpty { c.commentId }) + ":" + c.commentId
    fun subCacheKey(bizType: String, bizNo: String, pid: String, sort: String): String =
        "sub-$bizType-$bizNo-$pid-$sort"

    // ---------- 1.145 评分评论回复（登录态；回复嵌套进被回复评论的楼中楼） ----------
    var scoreReplyTarget by remember { mutableStateOf<com.java.myapplication.data.HupuScoreComment?>(null) }
    var scoreReplyText by remember { mutableStateOf("") }
    var scoreReplySending by remember { mutableStateOf(false) }
    // 1.150: 本机刚发送的回复（乐观层）：key = 被回复评论 commentId。
    // 服务端有延迟（实测发送后不会立刻出现在列表里），本地先上屏；条目用服务端回包的真实
    // commentId 作 id，服务端稍后返回同一条时按 id 去重——修复 1.148「两条我的回复」。
    val optimisticScoreReplies = remember {
        mutableStateMapOf<String, List<com.java.myapplication.data.HupuScoreComment>>()
    }

    // ---------- 1.146 回复框扩展（对齐帖子回复框：表情/表情包/图片） ----------
    // 图片附件（Uri，最多 9 张；发送时顺序上传换 URL）
    var scoreReplyImages by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var scoreReplyImageUploading by remember { mutableStateOf(false) }
    // 表情面板（覆盖式：贴屏底，键盘盖其上；高度 = IME 峰值）
    var scoreReplyEmojiOpen by remember { mutableStateOf(false) }
    var scoreReplyEmojiTab by remember { mutableStateOf(0) }
    var scoreStickerAdding by remember { mutableStateOf(false) }
    var scoreStickerToDelete by remember { mutableStateOf<com.java.myapplication.data.HupuSticker?>(null) }
    // IME 峰值跟踪（ViewTree insets 监听器，非组合 API——键盘动画每帧零组合开销）
    var scoreReplyLastImePx by remember { mutableStateOf(0) }
    // 面板→键盘切换（点输入框先升键盘，IME 到面板高才撤面板——零跳变）
    var scorePanelToKeyboardPending by remember { mutableStateOf(false) }
    var scorePanelTargetPx by remember { mutableStateOf(0) }
    val scoreCtx = androidx.compose.ui.platform.LocalContext.current
    val scoreDensity = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.DisposableEffect(Unit) {
        val view = (scoreCtx as? android.app.Activity)?.window?.decorView
        val cb = android.view.View.OnApplyWindowInsetsListener { v, insets ->
            val ime = androidx.core.view.WindowInsetsCompat.toWindowInsetsCompat(insets, v)
                .getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
            if (ime > scoreReplyLastImePx) scoreReplyLastImePx = ime
            insets
        }
        if (view != null) view.setOnApplyWindowInsetsListener(cb)
        onDispose {
            if (view != null) view.setOnApplyWindowInsetsListener(null)
        }
    }
    // 图片选择器（多选，最多 9 张）
    val scorePickImages = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) scoreReplyImages = (scoreReplyImages + uris).distinct().take(9)
    }
    // 表情包下载落盘 → 图片附件（对齐帖子 insertSticker）
    fun scoreInsertSticker(s: com.java.myapplication.data.HupuSticker) {
        if (scoreStickerAdding) return
        scoreStickerAdding = true
        scope.launch {
            HupuPrefs.stickerToast = "表情包处理中…"
            // 1.165: 本地导入的表情直接读盘；远程收藏的表情走下载
            val bytes = if (isLocalStickerUrl(s.url)) {
                runCatching { java.io.File(s.url.removePrefix("file://")).readBytes() }.getOrNull()
            } else {
                HupuAccount.downloadImageBytes(s.url)
            }
            if (bytes == null || bytes.isEmpty()) {
                scoreStickerAdding = false
                HupuPrefs.stickerToast = "表情包下载失败"
                return@launch
            }
            val lower = s.url.lowercase()
            val ext = when {
                lower.contains(".gif") -> "gif"
                lower.contains(".png") -> "png"
                lower.contains(".webp") -> "webp"
                else -> "jpg"
            }
            val file = try {
                val dir = java.io.File(scoreCtx.cacheDir, "stickers").apply { mkdirs() }
                val f = java.io.File(dir, "sticker_" + System.currentTimeMillis() + "." + ext)
                f.writeBytes(bytes)
                f
            } catch (e: Exception) {
                scoreStickerAdding = false
                HupuPrefs.stickerToast = "表情包写入失败：" + (e.message ?: e.javaClass.simpleName)
                return@launch
            }
            scoreReplyImages = scoreReplyImages + android.net.Uri.fromFile(file)
            scoreStickerAdding = false
            HupuPrefs.stickerToast = "已加入图片，发送时随回复一起上传"
        }
    }
    // 面板→键盘翻转超时兜底（800ms 无 IME 变化直接切换——悬浮键盘等场景）
    LaunchedEffect(scorePanelToKeyboardPending) {
        if (!scorePanelToKeyboardPending) return@LaunchedEffect
        if (scoreReplyLastImePx <= 0) {
            if (scoreReplyEmojiOpen) scoreReplyEmojiOpen = false
            scorePanelToKeyboardPending = false
            return@LaunchedEffect
        }
        withTimeoutOrNull(800) {
            androidx.compose.runtime.snapshotFlow { scorePanelToKeyboardPending }.first { !it }
        }
        if (scorePanelToKeyboardPending && scoreReplyEmojiOpen) scoreReplyEmojiOpen = false
        scorePanelToKeyboardPending = false
    }
    // 收藏表情包 toast 自动消失（2s）
    LaunchedEffect(HupuPrefs.stickerToast) {
        val t = HupuPrefs.stickerToast
        if (t != null) {
            kotlinx.coroutines.delay(2000)
            if (HupuPrefs.stickerToast == t) HupuPrefs.stickerToast = null
        }
    }
    // 回复框关闭统一复位表情面板（点空白/发送/返回，防止下次打开键盘+面板同时出现）
    LaunchedEffect(scoreReplyTarget) {
        if (scoreReplyTarget == null) {
            scoreReplyEmojiOpen = false
            scorePanelToKeyboardPending = false
        }
    }

    // ---------- 虎扑通用评分（非赛事体系）：独立 chip + 主题卡流 + 主题详情 overlay ----------
    // 默认选中虎扑评分（用户指定）
    var commonOpen by remember { mutableStateOf(true) }
    var commonSubjects by remember { mutableStateOf<List<HupuCommonSubject>>(emptyList()) }
    var commonLoading by remember { mutableStateOf(false) }
    var commonFailed by remember { mutableStateOf(false) }
    var openedCommon by remember { mutableStateOf<HupuCommonSubject?>(null) }
    var commonClosing by remember { mutableStateOf(false) }
    var commonDetailLoading by remember { mutableStateOf(false) }
    var commonLoadingMore by remember { mutableStateOf(false) }
    val commonTrees = remember { mutableStateMapOf<String, HupuCommonTree>() }

    fun openMatch(m: HupuMatch) {
        if (m.scoreBizNo == null) return
        val key = "${m.scoreBizType}-${m.scoreBizNo}"
        detailClosing = false
        SecondaryPage.enter()
        openedMatch = m
        // 加载态在交互瞬间同步置位（自定义频道同款策略）：无缓存 → 骨架屏第一帧
        // 就出现；失败态只在网络真正失败后才出现，打开瞬间不再闪「加载失败」
        detailLoading = !matchTrees.containsKey(key)
    }

    fun closeMatch() {
        // 先播返回动画（overlay 滑回右侧），动画结束后真正移除
        detailClosing = true
    }

    fun openPlayer(p: HupuScoreItem) {
        val key = "${p.bizType}-${p.bizId}"
        playerClosing = false
        SecondaryPage.enter()
        // 每位选手都从默认「最亮」开始（排序选择不跨选手沿用）
        playerSort = "brightest"
        playerDisplaySort = "brightest"
        playerSwitching = false
        playerSortIsDefault = true
        openedPlayer = p
        // 同上：同步置位，骨架屏第一帧出现，失败只在真实网络失败后出现
        playerLoading = !selfDetails.containsKey(key)
    }

    fun closePlayer() {
        playerClosing = true
    }

    // ---------- 虎扑通用评分：打开/关闭/加载（同款「同步置位 + 错峰请求」策略） ----------
    /**
     * 1.132: 冷启动时评分页与首页/专区并发发起首屏请求，评分首页 SSR（桌面 UA）偶发超时 /
     * 返回不含 __NEXT_DATA__ 的页面 → 空列表被判为失败。这里做三件事：
     *  ① 首次加载 [stagger] 延后错峰，不与冷启动其余页面首屏请求争网络；
     *  ② 失败自动重试（递增退避，共 3 次尝试），消除偶发失败；
     *  ③ 已有数据时刷新失败不清空（保留旧数据，不闪失败态）。
     * commonLoading 同步置位——骨架屏第一帧出现，失败态只在真实失败后出现。
     */
    fun loadCommonSubjects(forceNetwork: Boolean = false, stagger: Boolean = false) {
        commonLoading = true
        commonFailed = false
        scope.launch {
            if (stagger) delay(400) // 错峰：等冷启动首屏请求先发
            var list = repo.commonSubjects(refresh = forceNetwork)
            var tries = 0
            while (list.isEmpty() && tries < 2) {
                tries++
                delay(600L * tries)
                list = repo.commonSubjects(refresh = forceNetwork)
            }
            if (list.isNotEmpty()) {
                commonSubjects = list
            } else if (commonSubjects.isEmpty()) {
                commonFailed = true
            }
            commonLoading = false
        }
    }

    fun openCommon(s: HupuCommonSubject) {
        val key = "${s.bizType}-${s.bizNo}"
        commonClosing = false
        SecondaryPage.enter()
        openedCommon = s
        commonDetailLoading = !commonTrees.containsKey(key)
        if (commonSubjects.isEmpty()) loadCommonSubjects()
    }

    fun closeCommon() {
        commonClosing = true
    }

    // 默认选中虎扑评分：首帧即触发加载（chip 的 onClick 在默认态不会执行）
    // 同款「同步置位」：commonLoading 同步置 true，第一帧就是骨架屏；
    // 1.132: 首次加载错峰（stagger）——避开冷启动其余页面的首屏请求洪峰
    LaunchedEffect(Unit) {
        if (commonSubjects.isEmpty() && !commonLoading && !commonFailed) loadCommonSubjects(stagger = true)
    }

    fun loadMoreCommon() {
        val s = openedCommon ?: return
        val key = "${s.bizType}-${s.bizNo}"
        val t = commonTrees[key] ?: return
        if (commonLoadingMore || t.items.size >= t.totalCount) return
        scope.launch {
            commonLoadingMore = true
            val next = repo.commonTree(s.bizType, s.bizNo, page = t.items.size / 20 + 1)
            if (next != null) {
                val merged = (t.items + next.items).distinctBy { it.bizId }
                commonTrees[key] = next.copy(items = merged, totalCount = maxOf(t.totalCount, next.totalCount))
            }
            commonLoadingMore = false
        }
    }

    // Tab 重选刷新信号：已选中时再点评分 Tab → 当前视图强刷（虎扑评分主题流 / 电竞赛程）
    LaunchedEffect(HupuUiSignals.scoreTabTap) {
        if (HupuUiSignals.scoreTabTap == 0) return@LaunchedEffect
        if (commonOpen) loadCommonSubjects(forceNetwork = true) else refreshTick++
    }
    // 1.132: 切入评分页时补偿重试——页面常驻组合，冷启动首屏加载若失败（含重试后仍失败），
    // 用户切过来会看到残留失败态；此时若仍为空且不在加载中，再补一次加载。
    LaunchedEffect(HupuUiSignals.scoreTabShown) {
        if (HupuUiSignals.scoreTabShown == 0) return@LaunchedEffect
        if (commonOpen && commonSubjects.isEmpty() && !commonLoading) loadCommonSubjects()
    }
    // 评分频道自定义：当前选中的赛事项目被移除 → 回落到虎扑评分
    // （effectiveGames 即上面的订阅值，保存后立即重跑）
    LaunchedEffect(effectiveGames) {
        if (commonOpen) return@LaunchedEffect
        if (effectiveGames.none { it.first == selectedGame }) commonOpen = true
    }

    // 服务端分组：树就绪后加载分组定义+全部成员，全部完成才一次性写入状态（分类条与内容
        // 同帧出现，无中途闪态/插入跳动）。维度：flat=比赛维度；CS2 等 common_sports 三层=
        // 按地图(second)维度并行请求按 groupId 去重合并；LOL（lol_match）无服务端分组 →
        // 写空列表标记已解析（该类赛事走赛程卡两队 teamId 方案）
        suspend fun loadGroupsResolved(key: String, m: HupuMatch, t: HupuScoreTree) {
            if (matchGroups.containsKey(key)) return
            val no = m.scoreBizNo ?: return
            val base = m.scoreBizType ?: "lol_match"
            val groups = when {
                t.flat -> repo.scoreGroups(base, no)
                base == "common_sports_first" && t.rounds.isNotEmpty() -> {
                    val subType = base.removeSuffix("first") + "second"
                    coroutineScope {
                        t.rounds.map { r -> async { repo.scoreGroups(subType, r.bizId) } }
                            .flatMap { it.await() }
                            .distinctBy { it.groupId }
                    }
                }
                else -> emptyList()
            }
            val resolved = if (groups.isEmpty()) groups else coroutineScope {
                groups.map { g -> async { g to repo.scoreGroupNodes(g.rootNodeId) } }
                    .map { it.await() }
                    .mapNotNull { (g, mem) ->
                        if (mem.isNotEmpty()) { groupMembers[g.groupId] = mem; g } else null
                    }
            }
            matchGroups[key] = resolved
        }

    // 当前打开的比赛：树+分组都就绪后才关 loading——内容与分类条同帧出现，不跳动；
    // 二次进入（此前提前 return 导致分组丢失的 bug）也走同一入口补齐分组
    LaunchedEffect(openedMatch?.scoreBizNo) {
        val m = openedMatch ?: return@LaunchedEffect
        val no = m.scoreBizNo ?: return@LaunchedEffect
        val key = "${m.scoreBizType}-$no"
        if (matchTrees.containsKey(key) && matchGroups.containsKey(key)) {
            detailLoading = false
            return@LaunchedEffect
        }
        delay(300) // 盖入动画 280ms + 余量（首个网络请求前）
        val t = matchTrees[key] ?: repo.scoreTree(m.scoreBizType ?: "lol_match", no)?.also { matchTrees[key] = it }
        if (t == null) {
            if (openedMatch?.scoreBizNo == no) detailLoading = false
            return@LaunchedEffect
        }
        loadGroupsResolved(key, m, t)
        if (openedMatch?.scoreBizNo == no) detailLoading = false
    }

    // 当前打开的选手：无缓存则拉详情 + 首页评论（loading 已在打开瞬间置位；仅错峰网络请求）
    LaunchedEffect(openedPlayer?.bizId, playerSort) {
        val p = openedPlayer ?: return@LaunchedEffect
        val key = "${p.bizType}-${p.bizId}"
        val cKey = "$key-$playerSort"
        if (selfDetails.containsKey(key) && playerComments[cKey] != null) {
            // 缓存已填充：零延迟同步显示，清 loading 与切换角标（缓存秒开不与动画争帧）
            if (openedPlayer?.bizId == p.bizId) {
                playerDisplaySort = playerSort
                playerSwitching = false
                playerLoading = false
            }
            return@LaunchedEffect
        }
        // 1.60 恢复 delay(300)：盖入动画 280ms + 余量错峰，网络请求不与转场动画争帧
        // （1.59 误删导致打开即卡——一边加载一边动画，缓存未命中时尤其明显）
        if (!selfDetails.containsKey(key)) {
            delay(300)
        }
        if (!selfDetails.containsKey(key)) {
            val d = repo.scoreSelf(p.bizType, p.bizId)
            if (d != null) selfDetails[key] = d
        }
        if (playerComments[cKey] == null) {
            val c = repo.scoreComments(p.bizType, p.bizId, cursor = 0L, queryType = playerSort)
            if (c != null) playerComments[cKey] = c
            // 最亮默认降级：hottest 端点无评论（scoreComments 返回 null）且排序仍处于
            // 默认态（非用户手动选择）→ 自动切到最晚；playerSort 变化触发本 effect 重跑。
            // 降级时保持 loading=true（骨架屏无缝衔接最晚加载，不闪空态帧）
            val fallback = c == null && playerSort == "brightest" && playerSortIsDefault &&
                openedPlayer?.bizId == p.bizId
            if (fallback) playerSort = "latest"
            if (!fallback && openedPlayer?.bizId == p.bizId) {
                playerLoading = false
                playerDisplaySort = playerSort
                playerSwitching = false
            }
        } else if (openedPlayer?.bizId == p.bizId) {
            playerLoading = false
            playerDisplaySort = playerSort
            playerSwitching = false
        }
    }

    // 当前打开的通用评分主题：无缓存则拉树（loading 已在打开瞬间置位；仅错峰网络请求）
    LaunchedEffect(openedCommon?.bizNo) {
        val s = openedCommon ?: return@LaunchedEffect
        val key = "${s.bizType}-${s.bizNo}"
        if (commonTrees.containsKey(key)) return@LaunchedEffect
        delay(300) // 盖入动画 280ms + 余量
        if (commonTrees.containsKey(key)) {
            if (openedCommon?.bizNo == s.bizNo) commonDetailLoading = false
            return@LaunchedEffect
        }
        val t = repo.commonTree(s.bizType, s.bizNo)
        if (t != null) commonTrees[key] = t
        if (openedCommon?.bizNo == s.bizNo) commonDetailLoading = false
    }

    // 首次进入某项目：缓存优先加载，有缓存直接跳过（切回不重载）
    // 默认选中虎扑评分：首帧不再拉 lol 赛程，仅当切到电竞 chip（commonOpen=false）时才加载
    LaunchedEffect(selectedGame, commonOpen) {
        if (commonOpen) return@LaunchedEffect
        if (schedules.containsKey(selectedGame)) return@LaunchedEffect
        loadingGame = selectedGame
        val days = repo.matchSchedule(selectedGame)
        if (days.isNotEmpty()) schedules[selectedGame] = days
        if (loadingGame == selectedGame) loadingGame = null
    }

    // 下拉刷新 / 失败重试：强制走网络（绕过缓存），成功后回顶
    LaunchedEffect(refreshTick) {
        if (refreshTick == 0) return@LaunchedEffect
        loadingGame = selectedGame
        val days = repo.matchSchedule(selectedGame, refresh = true)
        if (days.isNotEmpty()) {
            schedules[selectedGame] = days
            refreshVersion++
        }
        if (loadingGame == selectedGame) loadingGame = null
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(title = "评分")
        // 项目横滑条（与首页/专区同款裁剪）——首项为通用评分（虎扑评分，非赛事体系）
        val barState = rememberLazyListState()
        val selIdx = if (commonOpen) 0 else 1 + effectiveGames.indexOfFirst { it.first == selectedGame }
        LaunchedEffect(commonOpen, selectedGame) { if (selIdx >= 0) barState.animateChipCenterTo(selIdx) }
        LazyRow(
            state = barState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clipToBounds()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "common") {
                Chip(
                    text = "虎扑评分",
                    selected = commonOpen,
                ) {
                    if (commonOpen) return@Chip
                    commonOpen = true
                    // 同步置位：首次切换骨架屏第一帧出现，不闪「加载失败」
                    if (commonSubjects.isEmpty() && !commonFailed) commonLoading = true
                    if (commonSubjects.isEmpty()) loadCommonSubjects()
                }
            }
            items(effectiveGames, key = { it.first }) { (id, name) ->
                Chip(text = name, selected = !commonOpen && selectedGame == id) {
                    if (!commonOpen && selectedGame == id) return@Chip
                    commonOpen = false
                    selectedGame = id
                    // 同步置位（频道页同款）：无缓存的项目切过去骨架屏第一帧出现，
                    // 不闪「加载失败」；有缓存则直接出内容
                    if (!schedules.containsKey(id)) loadingGame = id
                }
            }
        }
        // 列表区：通用评分与赛事赛程二选一
        if (commonOpen) {
            when {
                commonSubjects.isEmpty() && commonLoading -> SkeletonHome()
                commonSubjects.isEmpty() && commonFailed -> ErrorRetry { loadCommonSubjects(forceNetwork = true) }
                else -> {
                    // 与电竞侧同款：下拉刷新（虎扑评分首页每次刷新换一批）
                    val pullState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = commonLoading,
                        onRefresh = { loadCommonSubjects(forceNetwork = true) },
                        state = pullState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        CommonSubjectsFeed(
                            subjects = commonSubjects,
                            onOpenSubject = { openCommon(it) },
                            onOpenItem = { openPlayer(it) },
                        )
                    }
                }
            }
        } else {
        val days = schedules[selectedGame]
        val pullState = rememberPullToRefreshState()
        val refreshing = loadingGame == selectedGame
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    loadingGame = selectedGame
                    val ds = repo.matchSchedule(selectedGame, refresh = true)
                    if (ds.isNotEmpty()) {
                        schedules[selectedGame] = ds
                        refreshVersion++
                    }
                    if (loadingGame == selectedGame) loadingGame = null
                }
            },
            state = pullState,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                days == null && refreshing -> SkeletonHome()
                days == null -> ErrorRetry { refreshTick++ }
                days.isEmpty() -> EmptySchedule()
                else -> ScheduleList(days, resetKey = "$selectedGame-$refreshVersion", onOpenMatch = { openMatch(it) })
            }
        }
        }
        }

        // ---------- 比赛详情二级页：盖入式转场 ----------
        val om = openedMatch
        if (om != null) {
            val key = "${om.scoreBizType}-${om.scoreBizNo}"
            MatchDetailOverlay(
                title = om.introduction.ifBlank { om.matchName.ifBlank { om.startTimeText } },
                tree = matchTrees[key],
                loading = detailLoading,
                closing = detailClosing,
                teams = listOfNotNull(
                    om.home?.let { it.memberId to it.name },
                    om.away?.let { it.memberId to it.name },
                ),
                groups = matchGroups[key] ?: emptyList(),
                groupMembers = groupMembers,
                onBack = { closeMatch() },
                onClosed = {
                    detailClosing = false
                    openedMatch = null
                    SecondaryPage.exit()
                },
                onRefresh = {
                    val no = om.scoreBizNo ?: return@MatchDetailOverlay
                    scope.launch {
                        detailLoading = true
                        val t = repo.scoreTree(om.scoreBizType ?: "lol_match", no, refresh = true)
                        if (t != null) matchTrees[key] = t
                        detailLoading = false
                    }
                },
                onOpenPlayer = { openPlayer(it) },
            )
        }

        // ---------- 虎扑通用评分主题详情（盖入式，zIndex 2；叶子详情 3 盖其上） ----------
        val oc = openedCommon
        if (oc != null) {
            val cKey = "${oc.bizType}-${oc.bizNo}"
            CommonDetailOverlay(
                subject = oc,
                tree = commonTrees[cKey],
                loading = commonDetailLoading,
                loadingMore = commonLoadingMore,
                closing = commonClosing,
                onBack = { closeCommon() },
                onClosed = {
                    commonClosing = false
                    openedCommon = null
                    SecondaryPage.exit()
                },
                onRefresh = {
                    scope.launch {
                        commonDetailLoading = true
                        val t = repo.commonTree(oc.bizType, oc.bizNo, refresh = true)
                        if (t != null) commonTrees[cKey] = t
                        commonDetailLoading = false
                    }
                },
                onOpenItem = { openPlayer(it) },
                onLoadMore = { loadMoreCommon() },
            )
        }

        // ---------- 选手详情三级页：盖入式转场 ----------
        val op = openedPlayer
        if (op != null) {
            val pKey = "${op.bizType}-${op.bizId}"
            // 1.59 数据源索引 displaySort（切排序时留旧数据），高亮仍用 playerSort（即时）
            val cState = playerComments["$pKey-$playerDisplaySort"]
            PlayerDetailOverlay(
                bizType = op.bizType,
                bizNo = op.bizId,
                playerName = op.name,
                self = selfDetails[pKey],
                comments = cState?.comments ?: emptyList(),
                commentCount = cState?.commentCount ?: 0L,
                hasMore = cState?.hasMore ?: false,
                loadingMore = playerLoadingMore,
                loading = playerLoading,
                closing = playerClosing,
                onBack = { closePlayer() },
                onClosed = {
                    playerClosing = false
                    openedPlayer = null
                    SecondaryPage.exit()
                },
                onRefresh = {
                    scope.launch {
                        playerLoading = true
                        val d = repo.scoreSelf(op.bizType, op.bizId, refresh = true)
                        if (d != null) selfDetails[pKey] = d
                        val c = repo.scoreComments(op.bizType, op.bizId, cursor = 0L, refresh = true, queryType = playerSort)
                        if (c != null) playerComments["$pKey-$playerSort"] = c
                        playerLoading = false
                    }
                },
                onLoadMore = {
                    val cur = playerComments["$pKey-$playerSort"] ?: return@PlayerDetailOverlay
                    if (playerLoadingMore || !cur.hasMore) return@PlayerDetailOverlay
                    scope.launch {
                        playerLoadingMore = true
                        val next = repo.scoreComments(op.bizType, op.bizId, cursor = cur.cursor, queryType = playerSort)
                        if (next != null) {
                            val merged = (cur.comments + next.comments).distinctBy { it.commentId }
                            playerComments["$pKey-$playerSort"] = next.copy(
                                comments = merged,
                                hasMore = next.hasMore,
                            )
                        }
                        playerLoadingMore = false
                    }
                },
                sortKey = playerSort,
                switching = playerSwitching,
                onSortChange = {
                    playerSort = it
                    playerSortIsDefault = false
                    val cKey = "$pKey-$it"
                    if (playerComments[cKey] != null) {
                        // 有缓存：零延迟切换（本地内存读取，无网络）
                        playerDisplaySort = it
                        playerSwitching = false
                    } else {
                        // 无缓存：留旧列表 + 顶部小转圈，数据到达后原位替换
                        // （与楼中楼切排序同款手感，1.59）
                        playerSwitching = true
                    }
                },
                subSheet = scoreSubSheet,
                onOpenSubSheet = { parent ->
                    subSheetSort = "brightest"
                    val ck = subCacheKey(op.bizType, op.bizId, parent.commentId, subSheetSort)
                    val cached = subComments[ck]
                    if (cached != null) {
                        // 缓存命中：直接出数据，无 loading（二次打开秒开，不与弹入动画争帧）
                        scoreSubSheet = SubCommentSheetData(parent = parent, data = cached, sortKey = subSheetSort)
                        subSheetLoading = false
                    } else {
                        // 同步置位 loading：sheet 第一帧就是骨架屏，网络失败才显示失败态
                        subSheetLoading = true
                        scoreSubSheet = SubCommentSheetData(parent = parent, data = null, sortKey = subSheetSort)
                        val sort0 = subSheetSort
                        scope.launch {
                            // 1.60 恢复 delay(300)：sheet 弹入 280ms + 余量。骨架屏已同步置位，
                            // 但网络解析/重组仍会与弹入动画争帧（1.59 删 delay 后实测打开卡顿）
                            delay(300)
                            val d = repo.scoreSubComments(op.bizType, op.bizId, parent.commentId, queryType = sort0)
                            if (d != null) subComments[ck] = d
                            // 1.60 三重匹配：sheet 开着、母评论没换、排序没被切——加载途中切排序/切
                            // 母评论/关闭重开时，旧回调不得污染新 sheet，也不得错清新请求的 loading
                            if (scoreSubSheet != null && scoreSubSheet?.parent?.commentId == parent.commentId && subSheetSort == sort0) {
                                scoreSubSheet = scoreSubSheet?.copy(data = subComments[ck])
                                subSheetLoading = false
                            }
                        }
                    }
                },
                // 1.61 修复：1.60 补丁块替换时误删了楼中楼宿主接线，参数落回默认值
                // （loading 恒 false → 打开/切排序闪「加载失败」；onCloseSubSheet 恒空操作
                // → 点遮罩/返回手势关不掉 sheet；subSheetClosing 恒 false → 退场动画不触发）
                subSheetLoading = subSheetLoading,
                subSheetLoadingMore = subSheetLoadingMore,
                subSheetClosing = subSheetClosing,
                onCloseSubSheet = { subSheetClosing = true },
                onClosedSubSheet = {
                    subSheetClosing = false
                    scoreSubSheet = null
                },
                onLoadMoreSub = {
                    val cur = scoreSubSheet?.data ?: return@PlayerDetailOverlay
                    // 1.58 切排序加载中不翻页：当前显示的是旧排序数据，其 cursor 不能
                    // 用于新排序（否则新排序列表会把旧排序下一页混进来）。1.60 切排序即清
                    // data 退回 loading，本守卫继续兜底
                    if (subSheetLoadingMore || subSheetLoading || !cur.hasMore) return@PlayerDetailOverlay
                    val pid = scoreSubSheet?.parent?.commentId ?: return@PlayerDetailOverlay
                    // 1.60 发起时固定排序与缓存键：完成后仅当排序未变才更新 sheet——
                    // 请求途中切排序时旧排序分页结果不得写进新排序缓存键污染数据
                    val reqSort = subSheetSort
                    val ck = subCacheKey(op.bizType, op.bizId, pid, reqSort)
                    scope.launch {
                        subSheetLoadingMore = true
                        val next = repo.scoreSubCommentsMore(
                            op.bizType, op.bizId, pid,
                            queryType = reqSort, cursor = cur.cursor,
                        )
                        if (next != null && scoreSubSheet != null) {
                            val merged = (cur.comments + next.comments).distinctBy { it.commentId }
                            val newState = next.copy(comments = merged, hasMore = next.hasMore)
                            subComments[ck] = newState
                            if (scoreSubSheet?.parent?.commentId == pid && subSheetSort == reqSort) {
                                scoreSubSheet = scoreSubSheet?.copy(data = newState)
                            }
                        }
                        subSheetLoadingMore = false
                    }
                },
                onSubSheetSortChange = { sort ->
                    subSheetSort = sort
                    val pid = scoreSubSheet?.parent?.commentId ?: return@PlayerDetailOverlay
                    val ck = subCacheKey(op.bizType, op.bizId, pid, sort)
                    // 1.60 恢复旧交互并修「切换后内容不变」：点击瞬间立即切高亮 + 清 data
                    // （sheet 退回 loading 骨架——先切换过去、再播加载动画、再加载）。
                    // 1.58 的「留旧数据」有两宗罪：①缓存命中分支只切 sortKey 不换 data，
                    // 来回切换已加载过的排序时内容永远停在旧排序（确定性复现）；②留旧列表
                    // +小角标偏离此前确认的旧交互。grandMap 经 copy 跨排序保留
                    scoreSubSheet = scoreSubSheet?.copy(sortKey = sort, data = null)
                    subSheetLoading = true
                    scope.launch {
                        // 1.60 恢复 delay(300)：tab 点击→请求错峰（避免与 tab 切换+列表
                        // 重组同帧争抢）；同时对同 pid 并发请求去重
                        delay(300)
                        val live = subComments[ck]
                        if (live == null) {
                            val d = repo.scoreSubComments(op.bizType, op.bizId, pid, queryType = sort)
                            if (d != null) subComments[ck] = d
                        }
                        // 三重匹配：sheet 开着、母评论没换、排序仍是要切的目标——
                        // 快速连点排序时旧请求只回填缓存，不得污染新排序的 sheet/loading
                        if (scoreSubSheet != null && scoreSubSheet?.parent?.commentId == pid && subSheetSort == sort) {
                            scoreSubSheet = scoreSubSheet?.copy(data = subComments[ck])
                            subSheetLoading = false
                        }
                    }
                },
                onSubSheetRetry = {
                    val pid = scoreSubSheet?.parent?.commentId ?: return@PlayerDetailOverlay
                    // 1.60 发起时固定排序与缓存键：重试途中切排序时不得把旧排序结果
                    // 写进新排序缓存键，也不得错清新请求的 loading
                    val reqSort = subSheetSort
                    val ck = subCacheKey(op.bizType, op.bizId, pid, reqSort)
                    subSheetLoading = true
                    scope.launch {
                        val d = repo.scoreSubComments(op.bizType, op.bizId, pid, queryType = reqSort)
                        if (d != null) subComments[ck] = d
                        // 三重匹配：sheet 还开着、parent 没换、排序没被切换，才换数据并清 loading
                        if (scoreSubSheet != null && scoreSubSheet?.parent?.commentId == pid && subSheetSort == reqSort) {
                            scoreSubSheet = scoreSubSheet?.copy(data = subComments[ck])
                            subSheetLoading = false
                        }
                    }
                },
                scorePanelOpen = scorePanelOpen && scorePanelKey == pKey,
                onScorePanelOpen = { open ->
                    if (open) {
                        if (!HupuAccount.isLoggedIn) {
                            scoreToast = "请先在「我的」页登录"
                        } else {
                            scorePanelKey = pKey
                            scorePanelOpen = true
                        }
                    } else scorePanelOpen = false
                },
                myScore = myScores[pKey] ?: (selfDetails[pKey]?.userScore ?: 0),
                onScoreSubmit = { v, comment ->
                    if (scorePanelSubmitting) return@PlayerDetailOverlay
                    scorePanelSubmitting = true
                    scope.launch {
                        val err = HupuAccount.scoreSave(op.bizType, op.bizId, v)
                        if (err != null) {
                            scorePanelSubmitting = false
                            scoreToast = err
                            return@launch
                        }
                        myScores[pKey] = v
                        scorePanelOpen = false
                        // 1.153: 打分随带评论——官方打分弹窗本就是「打分 + 评论」一体流：
                        // 先落分，紧接着把评论以一级评论发出（parentCommentId 空、subjectId 空）。
                        if (comment.isNotEmpty()) {
                            val res = HupuAccount.publishScoreComment(
                                op.bizType, op.bizId, comment,
                                parentCommentId = "",
                                subjectId = "",
                            )
                            val cKey = "$pKey-$playerSort"
                            if (res.error == null) {
                                // 乐观上屏（服务端有延迟）：用回包真实 commentId 建一级评论条目，
                                // 服务端稍后返回同一条时按 id 去重，不会出现两条。
                                val me = HupuAccount.profile
                                val local = com.java.myapplication.data.HupuScoreComment(
                                    commentId = res.commentId.ifEmpty { "local-" + System.currentTimeMillis() },
                                    userName = me?.name ?: "我",
                                    userHead = me?.avatar,
                                    // 1.163: 本机评论也带自己的 euid（点头像/昵称同样可进主页）
                                    userId = me?.euid.orEmpty(),
                                    content = comment,
                                    score = v,
                                    lightCount = 0L,
                                    date = "刚刚",
                                    ipLocation = "",
                                    parentCommentId = "",
                                    subjectId = "",
                                )
                                val cur = playerComments[cKey]
                                playerComments[cKey] = if (cur == null) {
                                    ScoreCommentState(comments = listOf(local), commentCount = 1L, cursor = 0L, hasMore = false)
                                } else {
                                    cur.copy(
                                        comments = mergeWithOptimistic(cur.comments, listOf(local)),
                                        commentCount = maxOf(cur.commentCount, cur.commentCount + 1),
                                    )
                                }
                                scoreToast = "已打 $v 分，评论已发布"
                                // 静默重拉第一页并合并（服务端数据到位后接管，本地条目同 id 去重）
                                val c = repo.scoreComments(
                                    op.bizType, op.bizId, cursor = 0L,
                                    refresh = true, queryType = playerSort,
                                )
                                if (c != null) {
                                    val base = playerComments[cKey]
                                    playerComments[cKey] = if (base == null) c else c.copy(
                                        comments = mergeWithOptimistic(c.comments, listOf(local)),
                                        commentCount = maxOf(c.commentCount, base.commentCount),
                                    )
                                }
                            } else {
                                scoreToast = "已打 $v 分，评论发布失败：" + res.error
                            }
                        } else {
                            scoreToast = "已打 $v 分"
                        }
                        scorePanelSubmitting = false
                        // 强刷 self：更新评分分布/均分/评分人数（登录态缓存分键）
                        val d = repo.scoreSelf(op.bizType, op.bizId, refresh = true)
                        if (d != null) selfDetails[pKey] = d
                    }
                },
                onScoreDelete = {
                    if (scorePanelSubmitting) return@PlayerDetailOverlay
                    scorePanelSubmitting = true
                    scope.launch {
                        val err = HupuAccount.scoreDelete(op.bizType, op.bizId)
                        scorePanelSubmitting = false
                        if (err == null) {
                            myScores[pKey] = 0
                            scorePanelOpen = false
                            scoreToast = "已取消评分"
                            val d = repo.scoreSelf(op.bizType, op.bizId, refresh = true)
                            if (d != null) selfDetails[pKey] = d
                        } else {
                            scoreToast = err
                        }
                    }
                },
                scorePanelSubmitting = scorePanelSubmitting,
                onLightComment = { target ->
                    if (!HupuAccount.isLoggedIn) {
                        scoreToast = "请先在「我的」页登录"
                        return@PlayerDetailOverlay
                    }
                    val k = lightKey(target)
                    val cur = scoreLightState[k] ?: target.hasLight
                    scoreLightState[k] = !cur
                    scope.launch {
                        val state = ArrayList<String>(1)
                        val err = HupuAccount.scoreLight(target.subjectId, target.commentId, !cur, state)
                        if (err != null) {
                            scoreLightState[k] = cur  // 失败回滚
                            scoreToast = err
                        } else if (state.isNotEmpty()) {
                            scoreLightState[k] = state.first() == "lit"  // 服务端真相自愈
                        }
                    }
                },
                lightState = scoreLightState,
                replyTarget = scoreReplyTarget,
                replyText = scoreReplyText,
                replySending = scoreReplySending,
                optimisticReplies = optimisticScoreReplies,
                // ---------- 1.146 回复框扩展接线 ----------
                replyImages = scoreReplyImages,
                onAddReplyImages = { scorePickImages.launch("image/*") },
                onRemoveReplyImage = { u -> scoreReplyImages = scoreReplyImages - u },
                replyImageUploading = scoreReplyImageUploading,
                replyEmojiOpen = scoreReplyEmojiOpen,
                onReplyEmojiToggle = { scoreReplyEmojiOpen = !scoreReplyEmojiOpen },
                replyEmojiTab = scoreReplyEmojiTab,
                onReplyEmojiTabChange = { scoreReplyEmojiTab = it },
                stickerAdding = scoreStickerAdding,
                onInsertSticker = { scoreInsertSticker(it) },
                onStickerLongClick = { scoreStickerToDelete = it },
                stickerToast = HupuPrefs.stickerToast,
                replyLastImePx = scoreReplyLastImePx,
                replyPanelToKeyboardPending = scorePanelToKeyboardPending,
                onReplyPanelToKeyboard = {
                    scorePanelTargetPx = maxOf(scoreReplyLastImePx, 280)
                    scorePanelToKeyboardPending = true
                },
                onReply = { _, target ->
                    if (!HupuAccount.isLoggedIn) {
                        scoreToast = "请先在「我的」页登录"
                    } else {
                        scoreReplyTarget = target
                        scoreReplyText = ""
                    }
                },
                onReplyTextChange = { scoreReplyText = it },
                onReplyCancel = {
                    scoreReplyTarget = null
                    scoreReplyText = ""
                    scoreReplyImages = emptyList()
                },
                onReplySend = {
                    val target = scoreReplyTarget
                    if (target != null && !scoreReplySending && !scoreReplyImageUploading) {
                        val text = scoreReplyText.trim()
                        if (text.isNotEmpty() || scoreReplyImages.isNotEmpty()) {
                            scope.launch {
                                scoreReplySending = true
                                // 图片先上传拿 URL（顺序上传，失败跳过该张）——对齐帖子回复链路
                                val urls = ArrayList<String>()
                                val uploadErrs = ArrayList<String>()
                                if (scoreReplyImages.isNotEmpty()) {
                                    scoreReplyImageUploading = true
                                    val cr = scoreCtx.contentResolver
                                    for (u in scoreReplyImages) {
                                        try {
                                            // 1.170: 带上限读取（>32MB 或读取失败 → null）
                                            val bytes = HupuImage.readCapped(cr, u)
                                            if (bytes == null) {
                                                uploadErrs.add("图片过大或无法读取")
                                                continue
                                            }
                                            // 1.157: 统一走魔数嗅探（与帖子回复同一实现），
                                            // GIF/动画 WebP 原样上传，不再被误标成 jpeg 丢动画
                                            val img = HupuImage.prepareForUpload(
                                                bytes,
                                                cr.getType(u) ?: "",
                                                u.lastPathSegment ?: "",
                                            )
                                            if (img == null) {
                                                uploadErrs.add("不支持的图片格式")
                                                continue
                                            }
                                            val r = HupuAccount.uploadReplyImage(img.bytes, img.ext, img.width, img.height)
                                            if (r.url != null) {
                                                urls.add(r.url)
                                            } else uploadErrs.add(r.error ?: "未知错误")
                                        } catch (e: Exception) {
                                            uploadErrs.add("读取图片失败: " + (e.message ?: e.javaClass.simpleName))
                                        }
                                    }
                                    scoreReplyImageUploading = false
                                    if (uploadErrs.any { it.contains("登录已过期") }) {
                                        scoreReplySending = false
                                        scoreToast = "登录已过期，请在「我的」页重新登录"
                                        return@launch
                                    }
                                    if (urls.isEmpty() && scoreReplyImages.isNotEmpty()) {
                                        // 图片全失败：仅当文本也空时终止；否则仅发文本
                                        if (text.isEmpty()) {
                                            scoreReplySending = false
                                            scoreToast = "图片上传失败：" + (uploadErrs.firstOrNull() ?: "未知错误")
                                            return@launch
                                        }
                                    }
                                    if (urls.size < scoreReplyImages.size) {
                                        scoreToast = "部分图片上传失败(${uploadErrs.firstOrNull() ?: ""})，已发送${urls.size}张"
                                    }
                                }
                                val res = HupuAccount.publishScoreComment(
                                    op.bizType, op.bizId, text,
                                    parentCommentId = target.commentId,
                                    subjectId = target.subjectId,
                                    images = urls,
                                )
                                if (res.error == null) {
                                    // 1.150: 恢复乐观层——实测服务端有延迟，发送后不会立刻返回这条回复，
                                    // 本地先上屏。id 用服务端回包的真实 commentId（缺失才退回 local-临时 id），
                                    // 服务端稍后返回同一条时按 commentId 去重，不会再出现「两条我的回复」。
                                    val me = HupuAccount.profile
                                    val realId = res.commentId.ifEmpty { "local-" + System.currentTimeMillis() }
                                    val local = com.java.myapplication.data.HupuScoreComment(
                                        commentId = realId,
                                        userName = me?.name ?: "我",
                                        userHead = me?.avatar,
                                        // 1.163: 本机回复也带自己的 euid
                                        userId = me?.euid.orEmpty(),
                                        content = text,
                                        score = 0,
                                        lightCount = 0,
                                        date = "刚刚",
                                        ipLocation = "",
                                        parentCommentId = target.commentId,
                                        subjectId = res.subjectId.ifEmpty { target.subjectId },
                                        images = urls,
                                    )
                                    optimisticScoreReplies[target.commentId] =
                                        (optimisticScoreReplies[target.commentId].orEmpty() + local)
                                            .distinctBy { it.commentId }
                                    scoreToast = "回复成功"
                                    scoreReplyTarget = null
                                    scoreReplyText = ""
                                    scoreReplyImages = emptyList()
                                    // ① 楼中楼正开着：强制重拉该层（绕缓存），拿到服务端真实数据
                                    val sheetNow = scoreSubSheet
                                    if (sheetNow != null) {
                                        val pid = sheetNow.parent.commentId
                                        val sort0 = subSheetSort
                                        val ck = subCacheKey(op.bizType, op.bizId, pid, sort0)
                                        subComments.remove(ck)
                                        // 孙评论展开态失效：回复可能落在已展开的子评论下，重开会重新拉
                                        scoreSubSheet = sheetNow.copy(grandMap = emptyMap())
                                        scope.launch {
                                            val d = repo.scoreSubComments(
                                                op.bizType, op.bizId, pid,
                                                queryType = sort0, refresh = true,
                                            )
                                            if (d != null) {
                                                subComments[ck] = d
                                                if (scoreSubSheet != null &&
                                                    scoreSubSheet?.parent?.commentId == pid &&
                                                    subSheetSort == sort0
                                                ) {
                                                    scoreSubSheet = scoreSubSheet?.copy(data = d)
                                                }
                                            }
                                        }
                                    }
                                    // ② 楼层列表：静默重拉第一页并原位替换同 id 条目（保序、不缩列表、不闪骨架）
                                    val curKey = "$pKey-$playerSort"
                                    val prev = playerComments[curKey]
                                    scope.launch {
                                        val c = repo.scoreComments(
                                            op.bizType, op.bizId, cursor = 0L,
                                            refresh = true, queryType = playerSort,
                                        )
                                        if (c != null) {
                                            if (prev == null) {
                                                playerComments[curKey] = c
                                            } else {
                                                val fresh = c.comments.associateBy { it.commentId }
                                                val kept = prev.comments.map { fresh[it.commentId] ?: it }
                                                val added = c.comments.filter { n ->
                                                    prev.comments.none { it.commentId == n.commentId }
                                                }
                                                playerComments[curKey] = prev.copy(
                                                    comments = kept + added,
                                                    commentCount = maxOf(prev.commentCount, c.commentCount),
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    scoreToast = res.error
                                }
                                scoreReplySending = false
                            }
                        }
                    }
                },
                onExpandGrand = { sub ->
                    // 孙评论「展开更多回复」：拉该子评论的孙评论端点（subCommentList?parentCommentId=子评论id），
                    // 返回 direct 孙评论（每条自带全量内嵌后代树）；分页 getMore 可多次点击
                    val sheet0 = scoreSubSheet ?: return@PlayerDetailOverlay
                    val cur = sheet0.grandMap[sub.commentId]
                    if (cur?.loading == true) return@PlayerDetailOverlay
                    scoreSubSheet = sheet0.copy(
                        grandMap = sheet0.grandMap + (sub.commentId to (cur ?: GrandExpandState()).copy(loading = true)),
                    )
                    scope.launch {
                        val existing = scoreSubSheet?.grandMap?.get(sub.commentId) ?: GrandExpandState()
                        // Official behavior (verified against official JS + live API): the expand
                        // button ALWAYS pages via getMore as long as a cursor exists. The first
                        // subCommentList response's hasMore is unreliable (it reports false even
                        // when deeper descendants exist), so gating the second click on hasMore
                        // made it re-fetch page 1 -> zero new items -> attempted -> button gone.
                        // Fix: cursor > 0 means we have a paging position -> continue with getMore.
                        val next: ScoreCommentState? = if (existing.cursor > 0L) {
                            repo.scoreSubCommentsMore(op.bizType, op.bizId, sub.commentId, queryType = "latest", cursor = existing.cursor)
                        } else {
                            repo.scoreSubComments(op.bizType, op.bizId, sub.commentId, queryType = "latest")
                        }
                        val sheetNow = scoreSubSheet
                        if (sheetNow != null) {
                            val base = sheetNow.grandMap[sub.commentId] ?: GrandExpandState()
                            val updated = if (next != null) {
                                // 端点返回 direct 孙（自带嵌套深层后代树）——拉平后合并去重
                                val flat = next.comments.flatMap { flattenWithDescendants(it) }
                                val merged = (base.comments + flat).distinctBy { it.commentId }
                                base.copy(
                                    comments = merged,
                                    cursor = next.cursor,
                                    hasMore = next.hasMore,
                                    loading = false,
                                    // 无新增且无下一页：置 attempted（按钮退场，防端点空转）；有进展则按钮保留
                                    attempted = merged.size == base.comments.size && !next.hasMore,
                                )
                            } else {
                                // 失败：解除 loading 保留按钮可重试
                                base.copy(loading = false)
                            }
                            scoreSubSheet = sheetNow.copy(
                                grandMap = sheetNow.grandMap + (sub.commentId to updated),
                            )
                        }
                    }
                },
            )
        }
        // 1.64 评分/点亮提示条（与帖子详情页同形态：顶部胶囊、点击消失）
        scoreToast?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(30f)
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
                        .clickable { scoreToast = null }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        // 1.146: 删除收藏表情确认弹窗（评分回复框「我的表情」长按触发）
        scoreStickerToDelete?.let { st ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { scoreStickerToDelete = null },
                title = { androidx.compose.material3.Text("删除表情") },
                text = { androidx.compose.material3.Text("确定从「我的表情」中移除这个表情吗？") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        HupuPrefs.removeSticker(st.url)
                        HupuPrefs.stickerToast = "已删除"
                        scoreStickerToDelete = null
                    }) { androidx.compose.material3.Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { scoreStickerToDelete = null }) {
                        androidx.compose.material3.Text("取消")
                    }
                },
            )
        }
    }
}
