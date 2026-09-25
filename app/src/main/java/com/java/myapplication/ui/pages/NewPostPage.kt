package com.java.myapplication.ui.pages

import android.net.Uri
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.java.myapplication.ui.components.huzaiFieldColors
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuBlocks
import com.java.myapplication.data.HupuDraft
import com.java.myapplication.data.HupuImage
import com.java.myapplication.data.HupuPostApi
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * 1.107: 发帖页（盖入式二级页，从「我的」页右上角铅笔进入）。
 *
 * P0 范围：标题 + 纯文本正文 + 图片（追加在正文末尾）+ 专区/子专区选择，原创帖。
 * 不含富文本格式、视频、投票、话题标签（后续版本）。
 *
 * 实测接口：POST /pcmapi/pc/bbs/v1/createThread
 *  payload { title, content(HTML), topicId, zoneId, creationType:"ORIGINAL", containsAi:0 }
 *  成功返回 data.tid；失败返回 msg（中文，直接展示）。
 * 图片复用帖子回复的上传链（HupuAccount.uploadReplyImage → 虎扑图床 URL）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewPostPage(
    onClose: () -> Unit,
    onPosted: (String) -> Unit = {},
    /** 1.112: 非空则为「编辑帖子」模式（preCheckEdit 预填 + editThread 提交） */
    editTid: String? = null,
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
    // 预测性返回（与项目其他二级页同款：跟手 + 取消回弹）
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

    var title by remember { mutableStateOf("") }
    // 1.117: 正文草稿（方案 B）——单文本框 + 原子占位符 + 附件表
    val bodyState = remember { TextFieldState("") }
    val attachments = remember { mutableStateListOf<HupuDraft.Attachment>() }
    var showVoteSheet by remember { mutableStateOf(false) }
    var voteCreating by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(0) }
    var publishing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var topic by remember { mutableStateOf<HupuPostApi.Topic?>(null) }
    var zone by remember { mutableStateOf<HupuPostApi.Zone?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    // 1.108: 话题（最多 2 个，随专区变化清空）+ 类型（原创/转载）
    val tags = remember { mutableStateListOf<HupuPostApi.Tag>() }
    var showTagPicker by remember { mutableStateOf(false) }
    var creationType by remember { mutableStateOf("ORIGINAL") }
    // 1.109: 可见范围（ALL_SEE 公开 / SELF_SEE 仅自己）+ 内容声明（containsAi 0~5）
    var visibleRange by remember { mutableStateOf("ALL_SEE") }
    var containsAi by remember { mutableIntStateOf(0) }
    var showDeclare by remember { mutableStateOf(false) }
    // 1.110: 视频帖（与图片互斥，最多 1 个；选中即上传，带进度）
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("") }
    var videoUploading by remember { mutableStateOf(false) }
    var videoProgress by remember { mutableStateOf(0f) }
    var videoUrl by remember { mutableStateOf<String?>(null) }
    var videoCover by remember { mutableStateOf<String?>(null) }
    var videoError by remember { mutableStateOf<String?>(null) }
    // 1.112: 编辑模式状态（原帖 zoneId 没有名称，单独记住；加载/不可编辑用遮罩展示）
    var editLoading by remember { mutableStateOf(editTid != null) }
    var editBlocked by remember { mutableStateOf<String?>(null) }
    var zoneIdOverride by remember { mutableIntStateOf(0) }

    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---------- 1.117: 正文草稿辅助（方案 B：单文本框 + 占位符 + 附件表） ----------

    /** 正文文本（含占位符字符） */
    fun bodyText(): String = bodyState.text.toString()

    /** 去掉占位符后的纯文字（用于正文校验 / 视频帖 content） */
    fun plainText(): String = bodyText().filter { !HupuDraft.isToken(it) }.trim()

    fun imageUrls(): List<String> =
        attachments.filterIsInstance<HupuDraft.Attachment.Image>().map { it.url }

    fun hasVote(): Boolean = attachments.any { it is HupuDraft.Attachment.Vote }

    /** 附件展示顺序：占位符仍在正文里的按位置排前，占位符被删的排到最后 */
    fun orderedAttachments(): List<HupuDraft.Attachment> =
        HupuDraft.ordered(bodyText(), attachments)

    // ---------- 1.117: 占位符的插入 / 删除（方案 B） ----------
    // 插图/投票时在当前光标处写入一个占位符字符（BMP 私有区，单字符 → 退格即整体删除），
    // 占位符在正文里的位置就是媒体的位置。删掉占位符 → 附件保留并排到最后；
    // 删掉附件 → 占位符随之从正文里移除。

    /** 在光标处写入占位符并登记附件；光标落到占位符之后 */
    fun insertAttachment(att: HupuDraft.Attachment) {
        attachments.add(att)
        bodyState.edit {
            val s = selection.min
            val e = selection.max
            replace(s, e, att.token.toString())
            placeCursorAfterCharAt(s)
        }
    }

    /** 删除附件：连同它在正文里的占位符一起移除 */
    fun removeAttachment(att: HupuDraft.Attachment) {
        attachments.remove(att)
        bodyState.edit {
            val txt = asCharSequence().toString()
            val idx = txt.indexOf(att.token)
            if (idx >= 0) replace(idx, idx + 1, "")
        }
    }

    /** 移除全部图片 / 投票（选视频时与媒体互斥）：清空附件并把正文里的占位符一并抹掉 */
    fun removeAllMedia() {
        attachments.removeAll { it is HupuDraft.Attachment.Image || it is HupuDraft.Attachment.Vote }
        bodyState.edit {
            val txt = asCharSequence().toString()
            for (i in txt.length - 1 downTo 0) {
                if (HupuDraft.isToken(txt[i])) replace(i, i + 1, "")
            }
        }
    }

    fun addImageBlock(url: String) {
        insertAttachment(HupuDraft.Attachment.Image(HupuDraft.nextToken(), url))
    }

    // ---------- 1.117: 占位符的显示层 ----------
    // 把私有区字符渲染成带底色的胶囊标签（正文里看起来就是〔图片〕/〔投票〕）。
    val chipBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    val chipFg = MaterialTheme.colorScheme.primary
    val bodyOutputTransform = remember(chipBg, chipFg) {
        object : OutputTransformation {
            override fun TextFieldBuffer.transformOutput() {
                val text = asCharSequence().toString()
                val byToken = attachments.associateBy { it.token }
                var delta = 0
                var n = 0
                for (i in text.indices) {
                    val c = text[i]
                    if (!HupuDraft.isToken(c)) continue
                    n++
                    val label = HupuDraft.labelOf(byToken[c], n)
                    val s = i + delta
                    replace(s, s + 1, label)
                    addStyle(
                        SpanStyle(background = chipBg, color = chipFg, fontWeight = FontWeight.Medium),
                        s,
                        s + label.length,
                    )
                    delta += label.length - 1
                }
            }
        }
    }

    // 1.118: 占位符与附件 1:1 绑定——正文里的占位符被删掉，对应附件也一并删除
    LaunchedEffect(bodyText()) {
        val kept = HupuDraft.pruneAttachments(bodyText(), attachments)
        if (kept.size != attachments.size) {
            attachments.clear()
            attachments.addAll(kept)
        }
    }

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val room = (9 - imageUrls().size).coerceAtLeast(0)
        if (room <= 0) {
            toast = "最多 9 张图片"
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            var added = 0
            for (uri in uris) {
                if (added >= room) break
                uploading++
                val res = uploadImage(ctx, uri)
                uploading--
                if (res.url != null) {
                    addImageBlock(res.url)
                    added++
                } else {
                    toast = "有图片上传失败"
                }
            }
        }
    }

    // 1.110: 选视频（mp4）→ 选中即上传（进度回调）→ 取封面。
    // 视频与图片互斥（官方图文/视频是两个 tab），故选中视频前先清空图片。
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // 视频与图片/投票互斥（官方图文/视频是两个 tab），先清掉图片与投票块
        removeAllMedia()
        videoUri = uri
        videoName = queryDisplayName(ctx, uri)
        videoUrl = null
        videoCover = null
        videoError = null
        videoProgress = 0f
        videoUploading = true
        scope.launch {
            val r = HupuAccount.uploadVideo(ctx, uri) { done, total ->
                if (total > 0) scope.launch { videoProgress = (done.toFloat() / total).coerceIn(0f, 1f) }
            }
            videoUploading = false
            if (r.url.isNullOrBlank()) {
                videoError = r.error ?: "视频上传失败"
            } else {
                videoUrl = r.url
                videoCover = HupuPostApi.videoCover(r.url)
                if (videoCover == null) toast = "封面获取失败，帖子将使用默认封面"
            }
        }
    }

    // 1.112: 编辑模式——拉原文并预填（视频帖保留原视频地址与封面，无需重新上传）
    LaunchedEffect(editTid) {
        val tid = editTid ?: return@LaunchedEffect
        editLoading = true
        val info = HupuPostApi.preCheckEdit(tid)
        editLoading = false
        if (info == null) {
            editBlocked = "加载原帖失败，请重试"
            return@LaunchedEffect
        }
        if (!info.editFlag) {
            editBlocked = info.editMsg.ifEmpty { "该帖当前不可编辑" }
            return@LaunchedEffect
        }
        title = info.title
        // 1.113/1.117: 反解析（jsonV3 优先，能还原投票节点；老帖回退 HTML 拆分）→ 折成单文本框草稿
        val parsed = HupuBlocks.fromEdit(info.contentHtml, info.formatJson)
        val (draftText, draftAtts) = HupuDraft.fromBlocks(parsed)
        attachments.clear()
        attachments.addAll(draftAtts)
        bodyState.edit { replace(0, length, draftText) }
        if (info.topicId > 0) {
            topic = HupuPostApi.Topic(info.topicId, info.topicName, null, null, null, 0)
        }
        zone = null
        zoneIdOverride = info.zoneId
        tags.clear()
        tags.addAll(info.tags)
        creationType = info.creationType.takeIf { it == "ORIGINAL" || it == "REPRINT" } ?: "ORIGINAL"
        visibleRange = info.visibleRange
        containsAi = info.containsAi
        if (!info.videoUrl.isNullOrBlank()) {
            videoUrl = info.videoUrl
            videoCover = info.videoCover
            videoName = "原视频"
            videoUri = null
        }
    }

    // toast 2.6s 自动消失
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }

    fun publish() {
        if (publishing) return
        val t = title.trim()
        if (t.length < 4) {
            toast = "标题不少于 4 个字"
            return
        }
        if (plainText().isEmpty() && imageUrls().isEmpty() && videoUrl == null && !hasVote()) {
            toast = "正文不能为空"
            return
        }
        if (topic == null) {
            toast = "请选择专区"
            return
        }
        if (!HupuAccount.isLoggedIn) {
            toast = "请先在「我的」页登录"
            return
        }
        if (uploading > 0) {
            toast = "图片还在上传，稍等"
            return
        }
        if (videoUploading) {
            toast = "视频还在上传，稍等"
            return
        }
        if (videoUri != null && videoUrl == null) {
            toast = videoError ?: "视频上传失败，请重新选择"
            return
        }
        publishing = true
        scope.launch {
            // 1.170: 一次取出专区，避免多处 !! 与协程内读到不一致的值
            val pubTopic = topic
            if (pubTopic == null) {
                publishing = false
                return@launch
            }
            // 1.113: 块模型序列化。视频帖沿用旧的纯文本 content + 视频 format；
            // 其余情况（含投票帖）都走块模型，只有存在投票时才生成 format。
            val isVideo = videoUrl != null
            val votePost = !isVideo && hasVote()
            val html: String
            var fmt: String? = null
            if (isVideo) {
                html = HupuPostApi.buildContent(plainText(), emptyList())
            } else {
                val payload = HupuBlocks.serialize(HupuDraft.toBlocks(bodyText(), attachments))
                html = payload.contentHtml
                fmt = payload.formatJson
            }
            // 官方投票帖 creationType 用 NORMAL（图文/视频是 ORIGINAL/REPRINT）
            val ctype = if (votePost) "NORMAL" else creationType
            val text = plainText()
            val r = if (editTid != null) {
                HupuPostApi.editThread(
                    tid = editTid,
                    title = t,
                    contentHtml = html,
                    topicId = pubTopic.id,
                    zoneId = zone?.id ?: zoneIdOverride,
                    tagIds = tags.map { it.id },
                    creationType = ctype,
                    containsAi = containsAi,
                    visibleRange = visibleRange,
                    videoUrl = videoUrl,
                    videoCover = videoCover,
                    videoBaseName = videoName,
                    contentText = text,
                    format = fmt,
                )
            } else {
                HupuPostApi.createThread(
                    title = t,
                    contentHtml = html,
                    topicId = pubTopic.id,
                    zoneId = zone?.id ?: zoneIdOverride,
                    tagIds = tags.map { it.id },
                    creationType = ctype,
                    containsAi = containsAi,
                    visibleRange = visibleRange,
                    videoUrl = videoUrl,
                    videoCover = videoCover,
                    videoBaseName = videoName,
                    contentText = text,
                    format = fmt,
                )
            }
            when (r) {
                is HupuPostApi.Result.Ok -> {
                    publishing = false
                    onPosted(r.tid)
                }
                is HupuPostApi.Result.Err -> {
                    publishing = false
                    toast = r.msg
                }
            }
        }
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
            // 顶栏：返回 + 标题 + 发布
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
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    if (editTid != null) "编辑帖子" else "发帖",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                if (publishing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        if (editTid != null) "保存" else "发布",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (title.trim().length >= 4) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { publish() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
            ) {
                // 专区行
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showPicker = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "专区",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    val tpc = topic
                    val label = when {
                        tpc == null -> "选择专区"
                        zone != null -> "${tpc.name} · ${zone!!.name}"
                        else -> tpc.name
                    }
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = if (topic == null) FontWeight.Normal else FontWeight.Medium,
                        color = if (topic == null) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "›",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 话题行（最多 2 个；需先选专区）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            if (topic == null) toast = "请先选择专区"
                            else showTagPicker = true
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("话题", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        if (tags.isEmpty()) {
                            Text(
                                "添加话题（最多 2 个）",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                tags.forEach { tg ->
                                    Row(
                                        Modifier
                                            .padding(end = 6.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                            .clickable { tags.remove(tg) }
                                            .padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            "#${tg.name}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "移除话题",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Text("+", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 类型行：原创/转载
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("类型", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    listOf("ORIGINAL" to "原创", "REPRINT" to "转载").forEach { (value, label) ->
                        val on = creationType == value
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable { creationType = value }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 可见范围：公开可见 / 仅自己可见（官方只有这两档）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("可见范围", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    listOf("ALL_SEE" to "公开可见", "SELF_SEE" to "仅自己可见").forEach { (value, label) ->
                        val on = visibleRange == value
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable { visibleRange = value }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 内容声明（containsAi 0~5，单选）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { showDeclare = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("内容声明", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        declareLabel(containsAi),
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(14.dp))

                // 标题
                OutlinedTextField(
                    colors = huzaiFieldColors(),
                    value = title,
                    onValueChange = { if (it.length <= 60) title = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("标题（不少于 4 个字）", fontSize = 15.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
                )

                Spacer(Modifier.height(10.dp))

                // 1.116: 视频卡片固定放在正文之前 —— 服务端渲染视频帖时就是 video 在 thread-content-detail 之前，
                // 编辑器要与发布后的形态一致（视频是帖子级字段，不是正文里的块，无法拖动位置）。
                // 1.110: 已选视频卡片（封面 + 文件名 + 进度 / 状态；与图片互斥）
                // 1.112: 编辑已有视频帖时没有本地 uri，但 videoUrl 已有 → 同样展示卡片
                if (videoUri != null || videoUrl != null) {
                    Text(
                        "视频 · 发布后显示在正文之前",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Spacer(Modifier.height(6.dp))
                    val err = videoError
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .width(108.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF222222)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val cover = videoCover
                            if (cover != null) {
                                AsyncImage(
                                    model = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Icon(
                                HupuIcons.PlayArrow,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.92f),
                                modifier = Modifier.size(30.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                videoName.ifEmpty { "视频" },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            val status = when {
                                videoUploading -> "上传中 ${(videoProgress * 100).toInt()}%"
                                err != null -> err
                                videoUrl != null -> "已就绪"
                                else -> "待上传"
                            }
                            Text(
                                status,
                                fontSize = 12.sp,
                                color = if (err != null) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (videoUploading) {
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { videoProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    videoUri = null
                                    videoUrl = null
                                    videoCover = null
                                    videoError = null
                                    videoName = ""
                                    videoProgress = 0f
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "移除视频",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Spacer(Modifier.height(10.dp))

                // 1.117: 单文本框正文（图片/投票以占位符内嵌，样式由 OutputTransformation 渲染）
                OutlinedTextField(
                    colors = huzaiFieldColors(),
                    state = bodyState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    placeholder = { Text("说点什么…", fontSize = 15.sp) },
                    shape = RoundedCornerShape(12.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    outputTransformation = bodyOutputTransform,
                )

                // 1.118: 附件区——缩略图形式（按占位符在正文中的顺序，序号与正文标签一致）
                val attIndex = HupuDraft.indexMap(bodyText())
                val visibleAtts = orderedAttachments()
                if (visibleAtts.isNotEmpty()) Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    visibleAtts.forEach { att ->
                        key(att.token) {
                            when (att) {
                                is HupuDraft.Attachment.Image -> AttachmentThumb(
                                    url = att.url,
                                    index = attIndex[att.token] ?: 0,
                                    onRemove = { removeAttachment(att) },
                                )

                                is HupuDraft.Attachment.Vote -> VoteBlockCard(
                                    title = att.title,
                                    choices = att.choices,
                                    index = attIndex[att.token] ?: 0,
                                    voteType = att.voteType,
                                    limit = att.limit,
                                    onRemove = { removeAttachment(att) },
                                )
                            }
                        }
                    }
                }

                if (uploading > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "图片上传中…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(2.dp))

                Spacer(Modifier.height(12.dp))

                // 工具行：加图片 / 加视频 + 说明
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val hasVideo = videoUri != null || videoUrl != null
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(enabled = uploading == 0) {
                                when {
                                    hasVideo -> toast = "视频与图片不能同时添加，请先移除视频"
                                    imageUrls().size >= 9 -> toast = "最多 9 张图片"
                                    else -> pickImages.launch("image/*")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            HupuIcons.ImageIcon,
                            contentDescription = "添加图片",
                            tint = if (hasVideo) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(enabled = !videoUploading && !hasVote()) {
                                when {
                                    hasVideo -> toast = "已添加视频，请先移除再重新选择"
                                    hasVote() -> toast = "投票帖不支持视频"
                                    imageUrls().isNotEmpty() -> toast = "视频与图片不能同时添加，请先移除图片"
                                    else -> pickVideo.launch("video/mp4")
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            HupuIcons.Videocam,
                            contentDescription = "添加视频",
                            tint = when {
                                videoUploading -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                imageUrls().isNotEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                hasVote() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    // 1.113: 添加投票（单选，2~10 项；与视频互斥）
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                when {
                                    hasVideo -> toast = "视频帖不支持投票"
                                    hasVote() -> toast = "每帖仅支持 1 个投票"
                                    else -> showVoteSheet = true
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            HupuIcons.Poll,
                            contentDescription = "添加投票",
                            tint = if (hasVideo || hasVote()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            hasVideo -> "视频帖不支持图片 / 投票"
                            else -> "最多 9 张图片，或 1 个视频（mp4）"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "提示：图片 / 投票会插到光标处（正文里显示为〔图片N〕/〔投票N〕标记）；删掉标记会一并删除该内容",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }

        // 1.112: 编辑模式——加载中 / 不可编辑遮罩（盖在表单之上）
        if (editLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
            }
        }
        editBlocked?.let { msg ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        msg,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 40.dp),
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "返回",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .clickable { closing = true }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // toast
        toast?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 70.dp)) {
                Text(
                    msg,
                    fontSize = 13.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xE6303030))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (showPicker) {
        PostTopicSheet(
            selectedTopicId = topic?.id,
            onPick = { t, z ->
                // 换专区 → 已选话题作废（话题从属于专区）
                if (topic?.id != t.id) tags.clear()
                topic = t
                zone = z
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }

    val tagTopic = topic
    if (showTagPicker && tagTopic != null) {
        TagPickerSheet(
            topicId = tagTopic.id,
            selectedIds = tags.map { it.id },
            onToggle = { tg ->
                val idx = tags.indexOfFirst { it.id == tg.id }
                if (idx >= 0) tags.removeAt(idx)
                else if (tags.size >= 2) toast = "最多选择 2 个话题"
                else tags.add(tg)
            },
            onDismiss = { showTagPicker = false },
        )
    }

    if (showDeclare) {
        DeclarePickerSheet(
            current = containsAi,
            onPick = {
                containsAi = it
                showDeclare = false
            },
            onDismiss = { showDeclare = false },
        )
    }

    // 1.113: 创建投票（先建票拿 voteId，再按光标位置插入块）
    if (showVoteSheet) {
        VoteCreateSheet(
            creating = voteCreating,
            onSubmit = { vt, choices, type, limit ->
                voteCreating = true
                scope.launch {
                    val vid = HupuPostApi.createVote(vt, choices, limit, type)
                    voteCreating = false
                    if (vid == null) {
                        toast = "投票创建失败，请重试"
                    } else {
                        insertAttachment(
                            HupuDraft.Attachment.Vote(
                                token = HupuDraft.nextToken(),
                                voteId = vid,
                                title = vt,
                                choices = choices,
                                limit = limit,
                                voteType = type,
                            ),
                        )
                        showVoteSheet = false
                        toast = if (type == "checkbox") "多选投票已添加" else "投票已添加"
                    }
                }
            },
            onDismiss = { if (!voteCreating) showVoteSheet = false },
        )
    }
}
