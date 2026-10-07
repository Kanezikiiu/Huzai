package com.java.myapplication.ui.pages

import androidx.activity.compose.PredictiveBackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.java.myapplication.data.HupuAccount
import com.java.myapplication.data.HupuBlacklist
import com.java.myapplication.data.HupuBlacklistEntry
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.blacklistProfileId
import com.java.myapplication.data.formatBlacklistTime
import com.java.myapplication.ui.components.Chip
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.CenteredTopBar
import com.java.myapplication.ui.glass.FrostedHeaderLayout
import com.java.myapplication.ui.components.LiquidBackButton
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.huzaiFieldColors
import com.java.myapplication.ui.components.tabSwipeSwitch
import com.java.myapplication.ui.components.tapGuard
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.glass.liquidElevation
import com.java.myapplication.ui.theme.isAppDarkTheme
import kotlin.coroutines.cancellation.CancellationException
import com.java.myapplication.ui.components.HuzaiToast

/**
 * 「过滤与屏蔽」合并页（盖入式二级页）。
 *
 * 把原先两个语义互补、却分居「我的」两处的二级页合并为单一入口：
 *   ① 关键词过滤（原「信息流设置」）——过滤**内容**
 *   ② 黑名单（原「黑名单」）——过滤**人**
 * 共用同一个 LazyColumn：关键词三个区块在前，黑名单列表在后（行仍逐条虚拟化，
 * 500 人规模下只有可见行参与组合与头像加载）。
 *
 * 拉黑后不再出现的内容：首页/专区信息流的帖子、帖子详情一级回复、楼中楼子回复、
 * 评分评论与楼中楼（整棵剪枝）。帖子主楼刻意不过滤。
 */
@Composable
fun FilterBlockSettingsPage(
    onClose: () -> Unit,
    /** 点黑名单条目进该用户主页（由宿主压入用户主页栈） */
    onOpenUser: (String) -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    // 计数 flag 门控：多页叠加 / 重挂载时不会多减，离开组合时兜底回收
    var pageEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        pageEntered = true
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    DisposableEffect(Unit) {
        onDispose { if (pageEntered) { pageEntered = false; SecondaryPage.exit() } }
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            if (pageEntered) { pageEntered = false; SecondaryPage.exit() }
            progress.animateTo(0f, tween(280))
            onClose()
        }
    }
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

    // ---------- ① 关键词过滤 ----------
    var kw by remember { mutableStateOf(HupuPrefs.loadFilterKeywords()) }
    fun add(kind: String, raw: String, onAdded: () -> Unit) {
        val k = HupuFilter.normalize(raw)
        if (k.isEmpty()) return
        val cur = when (kind) {
            "title" -> kw.title
            "zone" -> kw.zone
            else -> kw.comment
        }
        if (cur.any { it == k }) return onAdded()
        if (cur.size >= HupuPrefs.MAX_FILTER_KEYWORDS) return
        val next = when (kind) {
            "title" -> kw.copy(title = cur + k)
            "zone" -> kw.copy(zone = cur + k)
            else -> kw.copy(comment = cur + k)
        }
        kw = next
        HupuPrefs.saveFilterKeywords(next)
        onAdded()
    }
    fun remove(kind: String, k: String) {
        val next = when (kind) {
            "title" -> kw.copy(title = kw.title.filterNot { it == k })
            "zone" -> kw.copy(zone = kw.zone.filterNot { it == k })
            else -> kw.copy(comment = kw.comment.filterNot { it == k })
        }
        kw = next
        HupuPrefs.saveFilterKeywords(next)
    }

    // ---------- ② 黑名单 ----------
    var entries by remember { mutableStateOf(HupuPrefs.loadBlacklistEntries()) }
    var version by remember { mutableIntStateOf(0) }
    LaunchedEffect(HupuPrefs.blacklistVersion) {
        if (version != HupuPrefs.blacklistVersion) {
            version = HupuPrefs.blacklistVersion
            entries = HupuPrefs.loadBlacklistEntries()
        }
    }
    val openUser: (HupuBlacklistEntry) -> Unit = { e ->
        val id = blacklistProfileId(e)
        when {
            id.isEmpty() -> com.java.myapplication.ui.components.HuzaiToast.show("无法打开主页：缺少用户 id")
            !HupuAccount.isLoggedIn -> com.java.myapplication.ui.components.HuzaiToast.show("请先在「我的」页登录")
            else -> onOpenUser(id)
        }
    }

    // ---------- 二级 Tab：0 = 关键词过滤，1 = 黑名单 ----------
    var tab by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        // 1.198：顶栏浮空（可选效果，默认关；关闭时退回原有观感）
        FrostedHeaderLayout(
            modifier = Modifier.fillMaxSize(),
            header = {

            CenteredTopBar(
                title = "过滤与屏蔽",
                onBack = { closing = true },
            )
            // 二级 Tab 条（复用三大页顶部横滑条同款玻璃胶囊 Chip）
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Chip(text = "关键词过滤", selected = tab == 0, onClick = { tab = 0 })
                Chip(text = "黑名单", selected = tab == 1, onClick = { tab = 1 })
            }
            },
        ) { topInset ->
        Column(Modifier.fillMaxSize()) {
            // 内容区：左右滑动切换 Tab（与三大页同款 tabSwipeSwitch）
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .tabSwipeSwitch(
                        onPrevious = { if (tab > 0) tab-- },
                        onNext = { if (tab < 1) tab++ },
                    ),
            ) {
                if (tab == 0) {
                    // ① 关键词过滤
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().imePadding(),
                        // 1.198：浮空顶栏时首项从栏下开始（关闭该效果时为 0，位置不变）
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp + topInset, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "filter-intro") {
                            Text(
                                "含有被过滤关键词的条目/评论将不显示。标题、分区过滤作用于首页/专区/搜索的帖子条目；评论过滤作用于帖子回复、楼中楼与评分页评论。",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item(key = "grp-title") { KeywordGroup("标题关键词", "命中后帖子不显示", kw.title, "title", ::add, ::remove) }
                        item(key = "grp-zone") { KeywordGroup("分区关键词", "命中版块的帖子不显示", kw.zone, "zone", ::add, ::remove) }
                        item(key = "grp-comment") { KeywordGroup("评论关键词", "命中的评论不显示", kw.comment, "comment", ::add, ::remove) }
                    }
                } else {
                    // ② 黑名单
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // 1.198：浮空顶栏时首项从栏下开始（关闭该效果时为 0，位置不变）
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp + topInset, bottom = 140.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(key = "bl-intro") {
                            Text(
                                "被拉黑的人发布的帖子、回复、楼中楼与评分评论都不会再显示。帖子正文（主楼）不受影响——你仍可以从他的主页或链接打开他的帖子阅读。黑名单只存在本机，不会通知对方，也不会影响服务端数据。",
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item(key = "bl-header") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "已拉黑",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "点击条目进 TA 的主页 · ${entries.size}/${HupuBlacklist.MAX_ENTRIES}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (entries.isEmpty()) {
                            item(key = "bl-empty") {
                                Text(
                                    "暂无黑名单成员。在任意用户主页点右上角「⋮」→「拉黑该用户」即可加入。",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            // 每行提为 LazyColumn 的直接 item，真正虚拟化。
                            items(
                                items = entries,
                                key = { e -> e.ids.first() },
                            ) { e ->
                                BlacklistRow(
                                    entry = e,
                                    onOpen = { openUser(e) },
                                    onRemove = {
                                        HupuPrefs.removeBlacklistEntry(e)
                                        entries = HupuPrefs.loadBlacklistEntries()
                                        com.java.myapplication.ui.components.HuzaiToast.show("已移出黑名单")
                                    },
                                )
                            }
                        }
                        item(key = "note") {
                            Text(
                                "说明：过滤只作用于已经加载进来的列表，不会改变服务端的翻页与计数，因此列表条数、回复数可能比网页版少。",
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        }
        // 顶部提示（未登录 / 已移除）
    }
}

/** 一组关键词编辑器：标题 + 输入行（回车添加） + chip 流（点击删除） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordGroup(
    title: String,
    hint: String,
    words: List<String>,
    kind: String,
    onAdd: (String, String, () -> Unit) -> Unit,
    onRemove: (String, String) -> Unit,
) {
    val dark = isAppDarkTheme()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(8.dp))
            Text("$hint · ${words.size}/${HupuPrefs.MAX_FILTER_KEYWORDS}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        var input by remember { mutableStateOf("") }
        OutlinedTextField(
            colors = huzaiFieldColors(container = false),
            value = input,
            onValueChange = { input = it },
            // 1.223c: 输入框补悬浮感（与返回键同源、同款落影）
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            placeholder = { Text("输入关键词，回车添加", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                if (input.isNotEmpty()) {
                    IconButton(onClick = { input = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onAdd(kind, input) { input = "" }
            }),
        )
        if (words.isEmpty()) {
            Text("暂无关键词", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            // chip 流：FlowRow 自适应换行，按内容实际宽度排布
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                words.forEach { w ->
                    // 关键词 chip 是按钮语义（点击删除该词）→ LiquidButton
                    LiquidButton(
                        onClick = { onRemove(kind, w) },
                        shape = RoundedCornerShape(16.dp),
                        fill = buttonFill(dark),
                        border = buttonBorder(dark),
                        height = 30.dp,
                        contentPadding = 12.dp,
                        arrangement = Arrangement.Start,
                    ) {
                        Text(w, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "删除 $w",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 黑名单一行：头像 + 昵称 + 拉黑时间；整行点击进主页，行尾 ✕ 单独负责移除。
 * 内层 ✕ 自带 clickable，会消费点击，不会误触发整行跳转。
 */
@Composable
private fun BlacklistRow(
    entry: HupuBlacklistEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onOpen() }
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.avatar.isNotEmpty()) {
            AsyncImage(
                model = entry.avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    HupuIcons.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (entry.name.isBlank()) "（未知昵称）" else entry.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                formatBlacklistTime(entry.at),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                HupuIcons.Close,
                contentDescription = "移出黑名单",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}