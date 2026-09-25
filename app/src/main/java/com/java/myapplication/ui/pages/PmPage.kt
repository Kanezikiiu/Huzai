package com.java.myapplication.ui.pages

import android.text.format.DateUtils
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.java.myapplication.ui.components.huzaiFieldColors
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuApi
import com.java.myapplication.data.HupuImage
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

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
        delay(320)
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
        Text(
            "私信",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(4.dp))
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
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
            }
            convList.isEmpty() -> {
                Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("暂无私信", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                Column {
                    convList.forEachIndexed { idx, c ->
                        PmConversationRow(c, onClick = { onOpen(c) })
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

/** 会话行 */
@Composable
private fun PmConversationRow(c: PmConversation, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = c.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
            if (c.unread > 0) {
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
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(6.dp))
                if (c.lastTime > 0) {
                    Text(
                        DateUtils.getRelativeTimeSpanString(c.lastTime * 1000, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                c.lastContent,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 与某人的聊天页（4级页，盖入）
 *  1.104: 顶栏头像/昵称可点 → 打开对方用户主页（puid 即数字型 euid） */
@Composable
fun PmChatPage(conv: PmConversation, onClose: () -> Unit, onOpenProfile: ((Long) -> Unit)? = null) {
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
    var errToast by remember { mutableStateOf<String?>(null) }
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
        delay(320)
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
                errToast = err
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
                errToast = "读取图片失败（图片过大或已损坏）"
            } else {
                // 1.157: 统一走魔数嗅探（GIF/动画 WebP 原样上传，后缀不再决定格式）
                val img = HupuImage.prepareForUpload(bytes, ctx.contentResolver.getType(uri) ?: "", uri.lastPathSegment ?: "")
                if (img == null) {
                    errToast = "不支持的图片格式"
                } else {
                    val up = HupuAccount.uploadReplyImage(img.bytes, img.ext, img.width, img.height)
                    if (up.url != null) {
                        val err = sendPm(conv.puid, "<img src=\"${up.url}\"/>")
                        if (err == null) {
                            errToast = null
                            refreshLatest()
                        } else errToast = err
                    } else {
                        errToast = up.error ?: "图片上传失败"
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errToast = "图片发送失败: " + (e.message ?: e.javaClass.simpleName)
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
        Column(Modifier.fillMaxSize()) {
            // 顶栏：返回 + 对方头像/昵称
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("还没有消息，打个招呼吧", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                PmBubble(msgList[msgList.size - 1 - idx])
                            }
                        }
                    }
                }
            }

            // 输入行：图片按钮 + 输入框 + 发送
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !sending) { pickImage.launch("image/*") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(HupuIcons.ImageIcon, contentDescription = "发图片", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    colors = huzaiFieldColors(),
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("发送消息…", fontSize = 14.sp) },
                    shape = RoundedCornerShape(20.dp),
                    singleLine = true,
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 14.sp),
                )
                Spacer(Modifier.width(8.dp))
                if (sending) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "发送",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (input.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = input.isNotBlank()) { sendText() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }

        // 错误提示条
        errToast?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp)) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color(0xE6303030))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            LaunchedEffect(msg) {
                delay(2200)
                errToast = null
            }
        }
    }
}

/** 聊天气泡：自己右侧主题色、对方左侧浅灰；内容含 <img> 时渲染图片 */
@Composable
private fun PmBubble(m: PmMessage) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!m.fromMe) {
            AsyncImage(
                model = m.senderAvatar,
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
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Text(
                m.content,
                fontSize = 14.sp,
                color = if (m.fromMe) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .widthIn(max = 260.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 12.dp, topEnd = 12.dp,
                            bottomStart = if (m.fromMe) 12.dp else 4.dp,
                            bottomEnd = if (m.fromMe) 4.dp else 12.dp,
                        )
                    )
                    .background(if (m.fromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (m.fromMe) {
            Spacer(Modifier.width(6.dp))
        }
    }
}