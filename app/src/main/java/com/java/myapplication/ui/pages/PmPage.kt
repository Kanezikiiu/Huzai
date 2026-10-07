package com.java.myapplication.ui.pages

import kotlin.coroutines.cancellation.CancellationException
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuApi
import com.java.myapplication.data.HupuImage
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.glass.FrostedHeaderLayout
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.skeletonBlock
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.java.myapplication.ui.components.LiquidBackButton
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.glass.liquidElevation
import androidx.compose.foundation.border
import com.java.myapplication.ui.components.HuzaiToast

/**
 * 1.99 私信：会话列表（挂在消息中心三按钮下方）+ 聊天页（文字 + 图片每次一张）。
 * 端点（bbs.hupu.com/pcmapi/pc/space/v1/，JSON POST，实测）：
 *   pm/getPmList   {unreadList:0, page:{pageNum,pageSize:20}}
 *   pm/getPmDetail {fromPuid, page:{pageNum,pageSize:20}}
 *   sendPm         {content, puid, deviceId:"", allowLink:"1"}
 * 图片消息 = uploadReplyImage 上传后的 URL 包成 <img src="..."/>（官方编辑器同构）。
 * detail 里 loginPuid 用于区分自己/对方气泡。
 */

/** 私信会话条目 */
data class PmConversation(
    val puid: Long,
    val sid: Long,
    val name: String,
    val avatar: String?,
    val lastContent: String,
    val lastTime: Long,
    val unread: Int,
    val isSystem: Boolean,
)

/** 私信消息条目 */
data class PmMessage(
    val pmid: Long,
    val fromMe: Boolean,
    val content: String,
    val time: Long,
    val senderName: String,
    val senderAvatar: String?,
)

internal fun parsePmList(json: String): Pair<List<PmConversation>, Boolean>? {
    return try {
        val root = JSONObject(json)
        if (root.optInt("code", 0) != 1) return null
        val data = root.optJSONObject("data") ?: return null
        val page = data.optJSONObject("page")
        val hasNext = page != null && page.optInt("isEnd", 1) == 0
        val arr = data.optJSONArray("dataList") ?: return Pair(emptyList(), hasNext)
        val list = mutableListOf<PmConversation>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                PmConversation(
                    puid = o.optLong("puid", 0L),
                    sid = o.optLong("sid", 0L),
                    name = o.optString("nickName", "").ifBlank { "虎扑用户" },
                    avatar = o.optString("headerPic", "").takeIf { it.isNotEmpty() && it != "null" },
                    lastContent = o.optString("content", ""),
                    lastTime = o.optLong("lastTime", 0L),
                    unread = o.optInt("unread", 0),
                    isSystem = o.optInt("isSystem", 0) == 1,
                )
            )
        }
        Pair(list, hasNext)
    } catch (e: Exception) {
        null
    }
}

internal fun parsePmDetail(json: String): Triple<List<PmMessage>, Long, Boolean>? {
    return try {
        val root = JSONObject(json)
        if (root.optInt("code", 0) != 1) return null
        val data = root.optJSONObject("data") ?: return null
        val loginPuid = data.optLong("loginPuid", 0L)
        val page = data.optJSONObject("page")
        val hasNext = page != null && page.optInt("isEnd", 1) == 0
        val arr = data.optJSONArray("pmDetailList") ?: return Triple(emptyList(), loginPuid, hasNext)
        val list = mutableListOf<PmMessage>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            list.add(
                PmMessage(
                    pmid = o.optLong("pmid", 0L),
                    fromMe = o.optLong("puid", 0L) == loginPuid,
                    content = o.optString("content", ""),
                    time = o.optLong("createTime", 0L),
                    senderName = o.optString("nickName", ""),
                    senderAvatar = o.optString("headerPic", "").takeIf { it.isNotEmpty() && it != "null" },
                )
            )
        }
        Triple(list, loginPuid, hasNext)
    } catch (e: Exception) {
    null
    }
}

/** 发送私信。成功返回 null，失败返回错误文案 */
internal suspend fun sendPm(puid: Long, content: String): String? {
    val body = JSONObject().apply {
        put("content", content)
        put("puid", puid)
        put("deviceId", "")
        put("allowLink", "1")
    }
    val json = HupuApi.postSpaceApiJson("sendPm", body.toString()) ?: return "网络异常"
    return try {
        val root = JSONObject(json)
        if (root.optInt("code", 0) == 1) null else root.optString("msg", "发送失败").ifBlank { "发送失败" }
    } catch (e: Exception) {
        "发送失败"
    }
}

/** 私信会话列表（消息中心三按钮下方区块）
 *  1.102: 刷新上提到消息页外层 PullToRefreshBox（嵌在 LazyColumn item 内部拉不动），
 *  通过 refreshTick 触发重拉；聊天页关闭时也会 ++ 触发同步最新一条消息 */
@Composable
fun PmListSection(
    onOpen: (PmConversation) -> Unit,
    refreshTick: Int = 0,
    onRefreshed: () -> Unit = {},
) {
    val convs = remember { mutableStateOf<List<PmConversation>?>(null) }
    var nextPage by remember { mutableIntStateOf(1) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 入场动画结束后再加载（同通知列表的卡顿治理）
    LaunchedEffect(Unit) {
        delay(120)
        if (convs.value != null) return@LaunchedEffect
        val body = JSONObject().apply {
            put("unreadList", 0)
            put("page", JSONObject().apply { put("pageNum", 1); put("pageSize", 20) })
        }
        val parsed = HupuApi.postSpaceApiJson("pm/getPmList", body.toString())?.let { parsePmList(it) }
        if (parsed != null) {
            convs.value = parsed.first
            hasMore = parsed.second
            nextPage = 2
        } else {
            convs.value = emptyList()
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        loadingMore = true
        scope.launch {
            val body = JSONObject().apply {
                put("unreadList", 0)
                put("page", JSONObject().apply { put("pageNum", nextPage); put("pageSize", 20) })
            }
            val parsed = HupuApi.postSpaceApiJson("pm/getPmList", body.toString())?.let { parsePmList(it) }
            if (parsed != null) {
                convs.value = (convs.value ?: emptyList()) + parsed.first
                hasMore = parsed.second
                nextPage++
            }
            loadingMore = false
        }
    }

    Column {
        // 1.2xx: 区块标题行 —— 左侧「私信」+ 右侧未读总数胶囊（无未读时退化为会话数）
        val headerUnread = convs.value?.sumOf { it.unread } ?: 0
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "私信",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            val headerList = convs.value
            if (headerUnread > 0) {
                Text(
                    "$headerUnread 条未读",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            } else if (!headerList.isNullOrEmpty()) {
                Text(
                    "${headerList.size} 个会话",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // 1.102: 刷新由外层触发（下拉 / 聊天页关闭同步），重拉第一页整体替换
        fun refreshNow() {
            scope.launch {
                val body = JSONObject().apply {
                    put("unreadList", 0)
                    put("page", JSONObject().apply { put("pageNum", 1); put("pageSize", 20) })
                }
                val parsed = HupuApi.postSpaceApiJson("pm/getPmList", body.toString())?.let { parsePmList(it) }
                if (parsed != null) {
                    convs.value = parsed.first
                    hasMore = parsed.second
                    nextPage = 2
                }
                onRefreshed()
            }
        }
        LaunchedEffect(refreshTick) {
            if (refreshTick > 0) refreshNow()
        }
        // 1.170: 一次取出会话列表，避免多处 !! 与重复读状态
        val convList = convs.value
        when {
            convList == null -> {
                // 骨架条（与信息流骨架同款流光），替代原来的转圈
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(4) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(68.dp)
                                .skeletonBlock(RoundedCornerShape(14.dp))
                        )
                    }
                }
            }
            convList.isEmpty() -> {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            HupuIcons.Mail,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("暂无私信", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                // 1.2xx（真机反馈：参考 iOS 26 短信页）——所有会话合成**一张分组卡片**：
                //   · 首行顶角圆角、末行底角圆角（由整卡 clip 统一给）
                //   · 行与行之间**没有间隔**、无缝相连
                //   · 行间只用一条细分割线（从头像右侧开始，与 iOS 一致）
                //   · 落影打在这张整卡上（保留悬浮感），行本身不再各自成卡
                val groupShape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        // 1.2xx（真机反馈）：卡片颜色看起来比信息流卡片灰——落影会在卡边形成灰晕，
                        // 去掉落影，与信息流卡片（clip16 + surface）完全一致
                        .clip(groupShape)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    convList.forEachIndexed { idx, c ->
                        PmConversationRow(c, onClick = { onOpen(c) })
                        if (idx != convList.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 68.dp)
                                    .height(0.6.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                            )
                        }
                        if (idx == convList.lastIndex && hasMore) {
                            LaunchedEffect(convList.size) { loadMore() }
                        }
                    }
                }
                if (loadingMore) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

/** 从私信内容里提取虎扑站内帖子链接（如站务组「审核通过」消息里的 huputiyu://bbs/topic/{tid}）。 */
internal fun pmThreadTid(content: String): String? =
    Regex("huputiyu://bbs/topic/(\\d+)").find(content)?.groupValues?.get(1)
        ?: Regex("hupu://bbs/topic/(\\d+)").find(content)?.groupValues?.get(1)

/** 私信预览：把图片消息 / HTML 片段转成可读的一行文本（纯函数，可单测） */
internal fun pmPreview(raw: String): String {
    val t = raw.trim()
    if (t.isEmpty()) return ""
    val hasImg = t.contains("<img", ignoreCase = true)
    val plain = t
        .replace(Regex("<img[^>]*>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()
    return when {
        plain.isNotEmpty() -> plain
        hasImg -> "[图片]"
        else -> ""
    }
}

private fun sameDay(a: java.util.Calendar, b: java.util.Calendar): Boolean =
    a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) &&
        a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)

/** 1.198：两个秒级时间戳是否同一天（私信日期分隔用） */
private fun sameDaySec(a: Long, b: Long): Boolean {
    if (a <= 0L || b <= 0L) return false
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a * 1000L }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b * 1000L }
    return sameDay(ca, cb)
}

/** 1.198 私信日期分隔标签（纯函数，可单测）：今天 / 昨天 / M月d日 / YYYY年M月d日 */
internal fun pmDayLabel(sec: Long, now: Long = System.currentTimeMillis()): String {
    if (sec <= 0L) return ""
    val t = sec * 1000L
    val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val n = java.util.Calendar.getInstance().apply { timeInMillis = now }
    if (sameDay(c, n)) return "今天"
    val y = (n.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(c, y)) return "昨天"
    return if (c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR)) {
        "${c.get(java.util.Calendar.MONTH) + 1}月${c.get(java.util.Calendar.DAY_OF_MONTH)}日"
    } else {
        "${c.get(java.util.Calendar.YEAR)}年${c.get(java.util.Calendar.MONTH) + 1}月" +
            "${c.get(java.util.Calendar.DAY_OF_MONTH)}日"
    }
}

/** 私信时间分级（纯函数，可单测）：刚刚 / x分钟前 / HH:mm / 昨天 / 周x / M月d日。
 *  入参为秒级时间戳（接口口径，与列表其它处一致）。 */
internal fun formatPmTime(sec: Long, now: Long = System.currentTimeMillis()): String {
    if (sec <= 0L) return ""
    val t = sec * 1000L
    val diff = now - t
    if (diff < 60_000L) return "刚刚"
    if (diff < 3_600_000L) return "${diff / 60_000L}分钟前"
    val c = java.util.Calendar.getInstance().apply { timeInMillis = t }
    val n = java.util.Calendar.getInstance().apply { timeInMillis = now }
    if (sameDay(c, n)) {
        return String.format(
            "%02d:%02d",
            c.get(java.util.Calendar.HOUR_OF_DAY),
            c.get(java.util.Calendar.MINUTE),
        )
    }
    val y = (n.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    if (sameDay(c, y)) return "昨天"
    if (diff < 7 * 86_400_000L &&
        c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR)
    ) {
        return "周" + "日一二三四五六"[c.get(java.util.Calendar.DAY_OF_WEEK) - 1]
    }
    // 1.2xx（真机反馈）：非今年的私信要**带上年份**，否则只有"5月10日"看不出是哪一年
    return if (c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR)) {
        "${c.get(java.util.Calendar.MONTH) + 1}月${c.get(java.util.Calendar.DAY_OF_MONTH)}日"
    } else {
        "${c.get(java.util.Calendar.YEAR)}年${c.get(java.util.Calendar.MONTH) + 1}月" +
            "${c.get(java.util.Calendar.DAY_OF_MONTH)}日"
    }
}

/** 会话行：iOS 26 短信风格 —— **行本身不再是卡片**，
 *  首尾圆角 / 整卡边界 / 行间分隔线都由外层「分组卡片」统一处理。 */
@Composable
private fun PmConversationRow(c: PmConversation, onClick: () -> Unit) {
    val unread = c.unread > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (c.isSystem) {
                // 系统会话（虎扑官方号等）没有头像 → 圆形浅底 + 品牌图标
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        HupuIcons.BellOutlined,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = c.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                )
            }
            if (unread) {
                androidx.compose.material3.Badge(
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        if (c.unread > 9) "9+" else c.unread.toString(),
                        fontSize = 9.sp,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name,
                    fontSize = 15.sp,
                    // 1.2xx（真机反馈）：名称发灰 → 与上方三个入口的名称同色（onSurface），
                    // 未读仍然靠字重 + 头像角标区分
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                if (c.lastTime > 0) {
                    Text(
                        formatPmTime(c.lastTime),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                pmPreview(c.lastContent),
                fontSize = 12.sp,
                color = if (unread) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 与某人的聊天页（4级页，盖入）
 *  1.104: 顶栏头像/昵称可点 → 打开对方用户主页（puid 即数字型 euid） */
@Composable
fun PmChatPage(
    conv: PmConversation,
    onClose: () -> Unit,
    onOpenProfile: ((Long) -> Unit)? = null,
    /** 1.2xx：点私信里的站内链接（如站务组「审核通过」）→ 打开对应帖子 */
    onOpenThread: ((String) -> Unit)? = null,
) {
    val progress = remember { Animatable(0f) }
    DisposableEffect(Unit) {
        SecondaryPage.enter()
        onDispose { SecondaryPage.exit() }
    }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
    // 1.101: 预测性返回（与通知子页同款：手势跟手 + 取消回弹）
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

    val messages = remember { mutableStateOf<List<PmMessage>?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var pickedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // 选择图片（每次一张）
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) pickedUri = uri
    }

    suspend fun refreshLatest() {
        val body = JSONObject().apply {
            put("fromPuid", conv.puid)
            put("page", JSONObject().apply { put("pageNum", 1); put("pageSize", 20) })
        }
        val parsed = HupuApi.postSpaceApiJson("pm/getPmDetail", body.toString())?.let { parsePmDetail(it) }
        if (parsed != null && parsed.first.isNotEmpty()) {
            messages.value = parsed.first
            listState.animateScrollToItem(0)
        }
    }

    // 首次加载
    LaunchedEffect(conv.puid) {
        delay(120)
        if (messages.value != null) return@LaunchedEffect
        val body = JSONObject().apply {
            put("fromPuid", conv.puid)
            put("page", JSONObject().apply { put("pageNum", 1); put("pageSize", 20) })
        }
        val parsed = HupuApi.postSpaceApiJson("pm/getPmDetail", body.toString())?.let { parsePmDetail(it) }
        if (parsed != null) {
            messages.value = parsed.first
            if (parsed.first.isNotEmpty()) listState.scrollToItem(0)
        } else {
            messages.value = emptyList()
        }
    }

    // 1.103: 在页轮询——每 4s 拉一次最新消息，按 pmid 去重增量并入，
    // 有新消息自动滚到底（对方回复无需退出重进）
    LaunchedEffect(conv.puid) {
        while (true) {
            kotlinx.coroutines.delay(4000)
            val cur = messages.value ?: continue
            if (sending) continue
            val body = JSONObject().apply {
                put("fromPuid", conv.puid)
                put("page", JSONObject().apply { put("pageNum", 1); put("pageSize", 20) })
            }
            val parsed = HupuApi.postSpaceApiJson("pm/getPmDetail", body.toString())?.let { parsePmDetail(it) } ?: continue
            if (parsed.first.isEmpty()) continue
            val known = cur.map { it.pmid }.toHashSet()
            val fresh = parsed.first.filter { it.pmid !in known }
            if (fresh.isNotEmpty()) {
                val merged = (cur + fresh).sortedBy { it.time }
                messages.value = merged
                listState.animateScrollToItem(0)
            }
        }
    }

    // 发送文字
    fun sendText() {
        val text = input.trim()
        if (text.isEmpty() || sending) return
        sending = true
        scope.launch {
            val err = sendPm(conv.puid, text)
            if (err == null) {
                input = ""
                refreshLatest()
            } else {
                com.java.myapplication.ui.components.HuzaiToast.show(err)
            }
            sending = false
        }
    }

    // 发送图片：选中 → 读字节 → uploadReplyImage 上传 → 包 <img> 发送
    // 1.101 根修：不得在 effect 内部清掉自己的 key（pickedUri = null 放最后），
    // 否则 effect 被取消重启，CancellationException 被兜底 catch 误报「图片发送失败」
    LaunchedEffect(pickedUri) {
        val uri = pickedUri ?: return@LaunchedEffect
        if (sending) return@LaunchedEffect
        sending = true
        try {
            // 1.170: 带上限读取（>32MB 或读取失败 → null）
            val bytes = com.java.myapplication.data.HupuImage.readCapped(ctx.contentResolver, uri)
            if (bytes == null) {
                com.java.myapplication.ui.components.HuzaiToast.show("读取图片失败（图片过大或已损坏）")
            } else {
                // 1.157: 统一走魔数嗅探（GIF/动画 WebP 原样上传，后缀不再决定格式）
                val img = HupuImage.prepareForUpload(bytes, ctx.contentResolver.getType(uri) ?: "", uri.lastPathSegment ?: "")
                if (img == null) {
                    com.java.myapplication.ui.components.HuzaiToast.show("不支持的图片格式")
                } else {
                    val up = HupuAccount.uploadReplyImage(img.bytes, img.ext, img.width, img.height)
                    if (up.url != null) {
                        val err = sendPm(conv.puid, "<img src=\"${up.url}\"/>")
                        if (err == null) {
                            refreshLatest()
                        } else com.java.myapplication.ui.components.HuzaiToast.show(err)
                    } else {
                        com.java.myapplication.ui.components.HuzaiToast.show(up.error ?: "图片上传失败")
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.java.myapplication.ui.components.HuzaiToast.show("图片发送失败: " + (e.message ?: e.javaClass.simpleName))
        }
        sending = false
        pickedUri = null
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(1f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard()
            .imePadding(),
    ) {
        // 1.198：顶栏浮空（可选效果，默认关；关闭时退回原有观感）
        FrostedHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            header = {

            // 顶栏：返回 + 对方头像/昵称
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LiquidBackButton(onClick = { closing = true })
                Spacer(Modifier.width(8.dp))
    // 1.104: 头像/昵称可点 → 用户主页（系统账号不跳）
            Row(
                Modifier
                    .weight(1f)
                    .then(if (onOpenProfile != null && !conv.isSystem) Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpenProfile(conv.puid) } else Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(8.dp))
                AsyncImage(
                    model = conv.avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(32.dp).clip(CircleShape),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    conv.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            }
            },
        ) { topInset ->
                Column(Modifier.fillMaxSize()) {


            // 消息流
            Box(Modifier.weight(1f)) {
                // 1.170: 一次取出消息列表，避免多处 !!
                val msgList = messages.value
                when {
                    msgList == null -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    msgList.isEmpty() -> {
                        // 1.198：空态加图标，不再是孤零零一行字
                        Column(
                            Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                HupuIcons.Comment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "还没有消息，打个招呼吧",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState, reverseLayout = true,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(msgList.size) { idx ->
                                val real = msgList.size - 1 - idx
                                val m = msgList[real]
                                val prev = msgList.getOrNull(real - 1)
                                Column {
                                    // 1.198：跨天插入日期分隔（今天 / 昨天 / M月d日）
                                    if (prev == null || !sameDaySec(prev.time, m.time)) {
                                        PmDaySeparator(m.time)
                                    }
                                    // 1.198b（真机反馈：气泡头像糊）：对方头像优先用**会话头像**——
                                    // 与顶栏同一个 URL（顶栏清晰），详情接口的 headerPic 常是小尺寸变体。
                                    PmBubble(m, peerAvatar = conv.avatar, onOpenThread = onOpenThread)
                                }
                            }
                                                    // 1.198：浮空顶栏时最早的消息从栏下开始（reverseLayout 下末项在视觉顶部）
                            item(key = "top-inset") { Spacer(Modifier.height(topInset)) }
}
                    }
                }
            }

            // 1.198：输入条升级为「浮动玻璃胶囊」——图片按钮 + 无边框输入框 + 圆形主题色发送按钮
            // （原 Material OutlinedTextField + 文字「发送」，是此页最后一块与全站液态玻璃语言不符的地方）
            val chatDark = isAppDarkTheme()
            val barShape = RoundedCornerShape(24.dp)
            val canSend = input.isNotBlank() && !sending
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp)
                    // 悬浮感：与顶栏返回键 / Tab 栏同源（落影 → 裁形 → 玻璃底 → 极淡描边）
                    .liquidElevation(barShape, chatDark, elevation = 6.dp)
                    .clip(barShape)
                    .background(buttonFill(chatDark))
                    .border(0.6.dp, buttonBorder(chatDark), barShape)
                    .padding(start = 6.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !sending) { pickImage.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        HupuIcons.ImageIcon,
                        contentDescription = "发图片",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.width(4.dp))
                // 无边框输入框：直接坐在玻璃条上（不再有 Material 的方框描边）
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        // 1.198b（真机反馈：字数多不换行）：单行 → 最多 4 行自动换行，
                        // 输入条随内容长高（高度变化由外层 Row 自然撑开，上方的消息区 weight(1f) 相应收缩）
                        maxLines = 4,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    "发送消息…",
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                Spacer(Modifier.width(6.dp))
                // 圆形发送按钮：可发送 = 主题色实心 + 白色箭头；不可用 = 极淡灰
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend || sending) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
                        )
                        .clickable(enabled = canSend) { sendText() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            HupuIcons.SendArrow,
                            contentDescription = "发送",
                            tint = if (canSend) androidx.compose.ui.graphics.Color.White
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        // 错误提示条
    }
    }
}

/** 1.198 私信日期分隔：居中次要文字（今天 / 昨天 / M月d日） */
@Composable
private fun PmDaySeparator(timeSec: Long) {
    val label = pmDayLabel(timeSec)
    if (label.isEmpty()) return
    Text(
        label,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
    )
}

/** 聊天气泡（1.198 iOS 风格升级）：自己在右（主题色 + 白字）、对方在左（浅底 + 主文字色）；
 *  圆角 18dp，靠近自己那一侧的底角收成 6dp 作为「气泡尖」；自己的气泡带极轻落影浮起。
 *
 *  @param peerAvatar 对方头像（会话头像，与顶栏同源 → 清晰度一致）；为空时回退到消息自带头像 */
@Composable
private fun PmBubble(
    m: PmMessage,
    peerAvatar: String? = null,
    onOpenThread: ((String) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (m.fromMe) 18.dp else 6.dp,
        bottomEnd = if (m.fromMe) 6.dp else 18.dp,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.fromMe) {
            AsyncImage(
                // 1.198b：与顶栏同一个头像 URL（会话头像），不再用详情接口里的小图
                model = peerAvatar ?: m.senderAvatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(30.dp).clip(CircleShape),
            )
            Spacer(Modifier.width(6.dp))
        }
        val imgSrc = Regex("<img[^>]+src=\"([^\"]+)\"").find(m.content)?.groupValues?.getOrNull(1)
        if (imgSrc != null) {
            AsyncImage(
                model = imgSrc,
                contentDescription = "图片消息",
                modifier = Modifier
                    .widthIn(max = 200.dp)
                    .clip(shape),
            )
        } else {
            // 1.2xx：站内链接（站务组「审核通过」等）→ 整体可点，点开对应帖子；
            // 正文用 pmPreview 剥掉 HTML 标签，只留可读文字
            val threadTid = pmThreadTid(m.content)
            val clickable = if (threadTid != null && onOpenThread != null) {
                Modifier.clickable { onOpenThread(threadTid) }
            } else {
                Modifier
            }
            Text(
                pmPreview(m.content),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = if (m.fromMe) androidx.compose.ui.graphics.Color.White
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .then(clickable)
                    .widthIn(max = 262.dp)
                    // 自己的气泡「浮」起来一点（对方气泡保持贴地，形成左右层次）
                    .shadow(
                        elevation = if (m.fromMe) 4.dp else 0.dp,
                        shape = shape,
                        clip = false,
                        ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.10f),
                        spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.14f),
                    )
                    .clip(shape)
                    .background(
                        if (m.fromMe) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp),
            )
        }
        if (m.fromMe) {
            Spacer(Modifier.width(6.dp))
        }
    }
}