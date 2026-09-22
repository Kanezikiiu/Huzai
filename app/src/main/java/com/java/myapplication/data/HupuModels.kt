package com.java.myapplication.data

/**
 * 虎扑数据模型
 * 数据来源见 docs/hupu_web_probe.md（HTML 内嵌 JSON 通道）
 */

data class HupuAuthor(
    val puid: String = "",
    val name: String = "",
    val url: String = "",
    // 加密用户 id：官方用户主页为 my.hupu.com/{euid}（移动端为 m.hupu.com/user/{euid}），用户主页跳转用
    val euid: String = "",
)

data class HupuTopic(
    val topicId: String = "",
    val name: String = "",
    val url: String = "",
)

/** 帖子条目（列表页） */
data class HupuThread(
    val tid: String,
    val title: String,
    val fid: String = "",
    val cover: String? = null,
    val desc: String? = null,
    val lights: Int = 0,
    val replies: Int = 0,
    val read: Int = 0,
    val createdAt: Long = 0L,
    val createdAtText: String = "",
    /** 1.189: 主楼发布地（官方 detail JSON 的 `location`，如「上海」；可为空） */
    val location: String = "",
    val hasVideo: Boolean = false,
    val video: String = "",
    val videoCover: String = "",
    val author: HupuAuthor? = null,
    val topic: HupuTopic? = null,
    val url: String = "",
) {
    val detailUrl: String get() = if (url.isNotEmpty()) url else "/$tid.html"
}

/** 回复条目（详情页） */
data class HupuReply(
    val pid: String,
    val contentHtml: String = "",
    val floor: Int = 0,
    val lights: Int = 0,
    val totalReplies: Int = 0,
    val createdAtText: String = "",
    val isStarter: Boolean = false,
    val location: String = "",
    val author: HupuAuthor? = null,
    /**
     * 1.128：引用楼层的 pid（来自 SSR replies 条目的 quote.pid）。
     * 非空 = 该条实为「回复某条评论」的子回复——官方 SSR 也把它平铺进最外层，
     * 但楼中楼里同样能看到，造成重复；真一级评论（回复楼主/主题）**没有** quote 键。
     */
    val quotePid: String = "",
) {
    /** 1.128：是否为子回复（最外层应过滤，只留真一级评论）。 */
    val isSubReply: Boolean get() = quotePid.isNotEmpty()
}

/** 楼中楼：一条子回复（bbs-reply-detail API） */
data class HupuSubReply(
    val pid: String = "",
    val contentHtml: String = "",
    val lights: Int = 0,
    val nestedCount: Int = 0,
    val createdAtText: String = "",
    val isStarter: Boolean = false,
    val location: String = "",
    val author: HupuAuthor? = null,
    /** 被引用楼层（同楼内）的作者名，用于「回复 @xxx」语义 */
    val quoteUser: String = "",
    /** 树层级：0=直接子楼层；>0 为「展开」递归加载的深层回复（渲染缩进用） */
    val depth: Int = 0,
)

/** 楼中楼数据包：父楼层 + 子回复分页 */
data class HupuFloorReplies(
    val parent: HupuReply,
    val subReplies: List<HupuSubReply>,
    /** 是否还有下一页（nextPage>0） */
    val hasMore: Boolean = false,
)

/** 帖子详情（主楼 + 回复分页） */
/**
 * 1.119: 帖子里的投票（从 thread.format 的 jsonV3 vote 节点解析）。
 *  - type: "radio" 单选 / "checkbox" 多选
 *  - limit: 最多可选几项（单选固定 1）
 * 依据实测帖 642389664：type=checkbox、limit=2、7 个选项。
 */
data class HupuVote(
    val voteId: Int,
    val title: String,
    val choices: List<String>,
    val limit: Int = 1,
    val type: String = "radio",
) {
    /**
     * 1.121: 多选判定 = type 为 checkbox **或** limit>1。
     * 实测单选帖（642385783）的 vote 节点**没有 type 字段**（只有 voteType:"text"、limit:1），
     * 故不能只认 type=="checkbox"，须用 limit 兜底。
     */
    val isMulti: Boolean get() = type == "checkbox" || limit > 1
}

/** 1.121: 投票选项（读取接口 voteDetailList 单项） */
data class HupuVoteOption(
    val sort: Int,
    val content: String,
    val voteCount: Int,
)

/**
 * 1.121: 投票实时结果（getVoteInfo 读取 / 提交投票响应体共用同一结构）。
 * 实测字段：voteCount(总票数)、userCount(参与人数)、userOptionLimit(最多可选)、
 * canVote(是否还能投)、end(是否结束)、voteDetailList、userVoteRecordList(我投了哪几项)。
 */
data class HupuVoteResult(
    val voteId: Int,
    val title: String,
    val options: List<HupuVoteOption>,
    val totalVotes: Int,
    val userCount: Int,
    val limit: Int,
    val canVote: Boolean,
    val ended: Boolean,
    /** 当前登录用户已投的选项序号；未登录/未投为空 */
    val myChoices: List<Int>,
)

data class HupuThreadDetail(
    val thread: HupuThread,
    val contentHtml: String,
    val replies: List<HupuReply>,
    val replyCount: Int,
    val replyPage: Int,
    val replyTotalPages: Int,
    /** 1.185: 回复展示方向（true=最新在前，倒序分页）；默认 false=正序 */
    val descReplies: Boolean = false,
    val isLocked: Boolean = false,
    /** 1.119: 正文里的投票（无投票帖为 null） */
    val vote: HupuVote? = null,
)

/** 浏览记录条目（「我的」页历史：帖子打开时写入，仅本地存储） */
data class HupuHistoryEntry(
    val tid: String,
    val title: String,
    val topicName: String = "",
    val lights: Int = 0,
    val replies: Int = 0,
    val read: Int = 0,
    /** 打开时刻（epoch ms） */
    val visitedAt: Long = 0L,
)

/** 话题（具体版块，如"步行街主干道"） */
data class HupuTopicInfo(
    val topicId: String,
    val name: String,
    val url: String,
    val desc: String = "",
    val hotText: String = "",
    val logo: String? = null,
)

/** 版块大类（如"步行街"），含话题列表 */
data class HupuCategory(
    val cateId: String,
    val name: String,
    val logo: String? = null,
    val topics: List<HupuTopicInfo> = emptyList(),
)

/** 版块热帖页数据 */
data class HupuBoardPage(
    val category: HupuTopicInfo?,
    val threads: List<HupuThread>,
    val hotTopics: List<HupuTopicInfo>,
    val recommendations: List<HupuThread>,
)

/** 话题页数据（含分页） */
data class HupuTopicPage(
    val topic: HupuTopicInfo,
    val threads: List<HupuThread>,
    val page: Int,
    val totalPages: Int,
    val sortTabs: List<SortTab>,
)

data class SortTab(val id: Int, val title: String, val url: String)

/** 搜索结果条目（bbs.hupu.com/search SSR 页 window.$$data.searchRes.data） */
data class HupuSearchItem(
    val tid: String,
    /** 已剥高亮标签的标题 */
    val title: String,
    /** 已剥高亮标签的摘要 */
    val desc: String,
    /** 封面图（可空） */
    val picture: String? = null,
    val replies: Int = 0,
    val lights: Int = 0,
    /** 发布时间戳（秒） */
    val addtime: Long = 0L,
    /** 相对时间文案（"9小时前"） */
    val addTimeDisplay: String = "",
    val fid: String = "",
    val forumName: String = "",
    val username: String = "",
)

/** 搜索结果页数据 */
data class HupuSearchPage(
    val items: List<HupuSearchItem>,
    val page: Int,
    val totalPages: Int,
    val count: Long = 0L,
)

/** 搜索排序方式（sortby 参数，桌面版六种） */
data class SearchSort(val key: String, val title: String)

/** 桌面版搜索页的六种排序 */
val SEARCH_SORTS = listOf(
    SearchSort("general", "综合"),
    SearchSort("createtime", "最新发布"),
    SearchSort("createtimeasc", "最早发布"),
    SearchSort("replytime", "最新回复"),
    SearchSort("light", "最多亮评"),
    SearchSort("reply", "最多回复"),
)

// ---------- 比赛评分（match-api.hupu.com，匿名浏览） ----------

/** 赛程日分组 */
data class HupuMatchDay(
    val dayTime: String,
    val dateBlock: String,
    val matches: List<HupuMatch>,
)

/** 一场比赛（含选手评分卡） */
data class HupuMatch(
    val matchId: String,
    val statusDesc: String,          // 已结束 / 进行中 / 未开始
    val status: String,             // COMPLETED / IN_PROGRESS / NOT_STARTED
    val introduction: String,       // LPL第三赛段组内赛
    val matchName: String,          // 电竞第三赛段组内赛
    val startTimeText: String,      // "7月30日 20:00" 之类（由时间戳格式化）
    val startTimestamp: Long,
    val scoreCountText: String,      // 3.3万人评分
    val home: HupuMatchTeam?,        // 左队
    val away: HupuMatchTeam?,        // 右队
    val winnerMemberId: String?,
    val playerScore: HupuPlayerScore?, // 选手评分卡（本场焦点选手）
    val scoreBizType: String?,          // 评分树钥匙（scoreItemKey.outBizType，如 lol_match）
    val scoreBizNo: String?,            // 评分树钥匙（scoreItemKey.outBizNo，如 3837）
)

/** ============ 虎扑通用评分（m.hupu.com/score-home，非赛事体系） ============ */

/** 通用评分主题卡（首页 SSR __NEXT_DATA__.pageProps.list 一项） */
data class HupuCommonSubject(
    val bizType: String,                // "common_first"
    val bizNo: String,                  // 主题 id，如 12855
    val name: String,                   // "全职法师"
    val score: String,                  // 主题均分（可能为空串）
    val scoreCountNum: Long,            // 评分人数
    val desc: String,                   // 主题简介（可能为空）
    val bgColorDay: String?,            // 卡片日间底色（官方色）
    val scoreColorDay: String?,         // 分数日间色
    val items: List<HupuScoreItem>,     // 首页预置的部分子项（直接点进叶子详情）
)

/** 通用评分主题树（getCurAndSubNodeByBizKey CHILD：主题 → 子项流，分页续拉） */
data class HupuCommonTree(
    val name: String,
    val image: String?,
    val desc: String,
    val scoreAvg: String,
    val scorePersonCount: Long,
    val items: List<HupuScoreItem>,
    val totalCount: Long,
)

/** 评分树（bplcommentapi getCurAndSubNodeByBizKey，匿名可用）：比赛 → 对局[] → 选手[] */
data class HupuScoreTree(
    val name: String,                    // 比赛名，如 "HLE 3-2 T1"
    val image: String?,                  // 比赛封面
    val scorePersonCount: Long,          // 总评分人数（summedScorePersonCount）
    val rounds: List<HupuScoreRound>,    // 对局列表（正序：第1局在前）
    /** true = 扁平结构（NBA/WNBA 等传统体育：children 直接是选手，无对局层，UI 隐藏对局横滑条） */
    val flat: Boolean = false,
)

/** 一个对局（BO）及其选手 */
data class HupuScoreRound(
    val bizId: String,
    val name: String,                    // "第1局"
    val scorePersonCount: Long,
    val players: List<HupuScoreItem>,
)

/** 一名选手的评分 */
data class HupuScoreItem(
    val bizId: String,
    val bizType: String,                  // 跳转钥匙（lol_item）
    val name: String,
    val image: String?,
    val scoreAvg: String,                // "9.8"
    val scorePersonCount: Long,          // "6124人评分"
    val hotComment: String,              // 最热评论内容
    /** 所属队伍 id（infoJson.teamId，与赛程卡 againstInfo.memberId 同一命名空间）；
     *  为空表示中立角色（解说/主持/BP聚合），官方详情页将其归入「趣评」类 Tab */
    val teamId: String? = null,
    /** 所选英雄/角色小图标（infoJson.auxiliaryPic[0]，MOBA 赛事有值）；
     *  教练/中立角色为 null，偶有兜底图 → 渲染层「有才显示」 */
    val heroIcon: String? = null,
)

/** 服务端分组定义（getSubGroups 响应项）：分类子 Tab 的数据源（官方对所有赛事通用） */
data class HupuScoreGroup(
    val groupId: Long,
    val name: String,
    val logo: String?,
    val rootNodeId: Long,   // groupAndSubNodes?nodeId= 拉取该组成员
    val childCount: Int,
)

/** 选手/对局节点详情（getSelfByBizKey data.detail，第 4 层） */
data class HupuSelfDetail(
    val bizType: String,
    val bizId: String,
    val name: String,
    val image: String?,
    val scoreAvg: String,                // "9.8"
    val scorePersonCount: Long,
    val kda: String,                     // "K/D/A:1/3/20"（infoJson.desc 为 KDA 特征短串时）
    val intro: String = "",              // 评分对象介绍（infoJson.desc 为长文时，通用评分层；内容区介绍卡展示）
    /** 所选英雄/角色小图标（infoJson.auxiliaryPic[0]，MOBA 赛事有值；非 MOBA/教练为 null） */
    val heroIcon: String? = null,
    val distribution: List<Pair<String, Long>>,   // 评分分布（档位→人数，降序：10 分在前）
    val hottestComments: List<String>,    // 热评文本
    /** 登录态：当前用户已打的分（getSelfByBizKey detail.userScore，10 制；0=未打分）——1.64 */
    val userScore: Int = 0,
)

/** 评分区一条评论（primarySingleRow，匿名浏览） */
data class HupuScoreComment(
    val commentId: String,
    val userName: String,
    val userHead: String?,
    /** 1.163: 评论作者的用户 id（来源 commentUserId；数字型，可直接当 euid 打开用户主页） */
    val userId: String = "",
    val content: String,
    val score: Int,                       // 该用户打的分（10 制，0=无）
    val lightCount: Long,                 // 亮数
    val date: String,                     // "18小时前"
    val ipLocation: String,
    /** 楼中楼：子回复数（subCommentCount；官方主响应只内嵌前几条） */
    val subCommentCount: Int = 0,
    /** 楼中楼：后代回复数（descendantCount=回复的回复；官方「全部回复 N」= subCommentCount+descendantCount） */
    val descendantCount: Int = 0,
    /** 楼中楼：内嵌子回复（subCommentList，官方默认带 1~3 条；深层是扁平后代树） */
    val subComments: List<HupuScoreComment> = emptyList(),
    /** 宿主评论 id（parentCommentId；区分 direct 子回复 vs 深层后代——楼中楼展开去重用） */
    val parentCommentId: String = "",
    /** 附件图片（commentContentImages：元素 commentContent=URL；纯图评论 content 为空） */
    val images: List<String> = emptyList(),
    /** 点亮目标 subjectId（评论 commentKey.subjectId——实测 ≠ outBizNo，必须用评论自带值） */
    val subjectId: String = "",
    /** 登录态：当前用户是否已点亮（hasLight；匿名恒 false） */
    val hasLight: Boolean = false,
)
/**
 * 服务端评论 ⊕ 本机乐观条目（1.151）。
 * 两个坑一起解决：
 *  1) 重复：乐观条目用服务端真实 commentId 建，按 id 合并，服务端稍后返回同一条时不会出现两条；
 *  2) 带图回复「图消失」：刚发送时服务端可能先回一条还没有 commentContentImages 的副本，
 *     而它排在乐观条目之前——简单 distinctBy 会让服务端空图副本胜出，于是图就没了。
 *     这里按 commentId 对齐，图片以信息更全的一方为准。
 */
internal fun mergeWithOptimistic(
    server: List<HupuScoreComment>,
    optimistic: List<HupuScoreComment>,
): List<HupuScoreComment> {
    if (optimistic.isEmpty()) return server
    val local = optimistic.associateBy { it.commentId }
    val merged = server.map { s ->
        val l = local[s.commentId] ?: return@map s
        if (l.images.size > s.images.size) s.copy(images = l.images) else s
    }
    val ids = server.mapTo(HashSet()) { it.commentId }
    return merged + optimistic.filterNot { it.commentId in ids }
}

/** 孙评论「展开更多回复」状态（楼中楼内某子评论的；commentId 键控、可多次点击分页） */
data class GrandExpandState(
    /** 已追加显示的孙评论（拉平后的平级列表，与内嵌 subComments 去重合并） */
    val comments: List<HupuScoreComment> = emptyList(),
    /** 下一页游标（孙评论端点 cursor.publishTime） */
    val cursor: Long = 0L,
    /** 还有下一页（多次点击展开） */
    val hasMore: Boolean = false,
    /** 加载中（按钮转圈） */
    val loading: Boolean = false,
    /** 已完成过一次请求（成功响应即置位；失败不置位——按钮保留可重试） */
    val attempted: Boolean = false,
)
/** 递归拉平评论及其全部后代（先序：本条在前，后代随后）——楼中楼「展开更多回复」合并去重用 */
fun flattenWithDescendants(c: HupuScoreComment): List<HupuScoreComment> =
    listOf(c) + c.subComments.flatMap { flattenWithDescendants(it) }

/** 评论流状态（游标分页：cursor=下一页 publishTime） */
data class ScoreCommentState(
    val comments: List<HupuScoreComment> = emptyList(),
    val commentCount: Long = 0L,
    val cursor: Long = 0L,
    val hasMore: Boolean = false,
    val loadingMore: Boolean = false,
)

/** 参赛队伍 */
data class HupuMatchTeam(
    val memberId: String,
    val name: String,
    val logo: String?,
    val baseScore: String,    // 2:0 的比分
    val extraScore: String?,  // 加时/额外分
    val bigScore: String?,    // 大分（BO 赛制总局数）
)

/** 选手评分卡（官方展示的本场焦点评分） */
data class HupuPlayerScore(
    val name: String,
    val logo: String?,
    val teamLogo: String?,
    val scoreNum: String,        // "2.4"
    val scoreCountText: String,   // "2941人评分"
    val hotComment: String,      // 热评
)


/** 用户主页（资料卡 + 双 Tab 首屏，SSR 一次请求全量给出） */
data class HupuUserProfile(
    val euid: String = "",
    val puid: String = "",
    val name: String = "",
    val avatar: String = "",
    // IP 属地（如 "广东"）
    val locationStr: String = "",
    // 如 "加入虎扔2127天"
    val regTimeStr: String = "",
    // 等级体系：Lv 徽章 + 进度条（官方色 #704BE5）
    val level: String = "",
    val levelDesc: String = "",
    val levelColor: String = "",
    val levelScore: Long = 0L,
    val nextLevelScore: Long = 0L,
    val levelPercent: Double = 0.0,
    // 统计：粉丝/关注/被点亮/被推荐（后两者不封顶，与详情页口径一致）
    val followers: Int = 0,
    val following: Int = 0,
    val beLightCount: Int = 0,
    val beRecommendCount: Int = 0,
    val msgCount: Int = 0,
    val postCount: Int = 0,
    // PC 个人中心口径：推荐数 / 收藏数（收藏仅自己可见）；isSelf 由 getUserInfo 下发
    val recommendCount: Int = 0,
    val favoriteCount: Int = 0,
    val isSelf: Boolean = false,
    val reputation: Int = 0,
    val threads: List<HupuProfileThread> = emptyList(),
    val replies: List<HupuProfileReply> = emptyList(),
)

/** 用户主页主题帖条目（threadList；recommend_num 为真实推荐数，不封顶） */
data class HupuProfileThread(
    val tid: String,
    val title: String = "",
    val forumName: String = "",
    val topicName: String = "",
    val replies: Int = 0,
    val recommendNum: Int = 0,
    val createdAtText: String = "",
    val summary: String = "",
    val cover: String? = null,
)

/** 用户主页回帖条目（replyList：content 为纯文本，threadTitle 为所在帖标题） */
data class HupuProfileReply(
    val pid: String,
    val tid: String = "",
    val content: String = "",
    val formatTime: String = "",
    val lights: Int = 0,
    val threadTitle: String = "",
    val cover: String? = null,
)

/** PC 个人中心关注列表条目（getUserFollowList） */
data class HupuFollowUser(
    val puid: String = "",
    val name: String = "",
    val avatar: String = "",
    val level: Int = -1,
    val fansNum: Int = 0,
    val joinDaysText: String = "",
    val threadsNum: Int = 0,
    val repliesNum: Int = 0,
    val isSelf: Boolean = false,
)
