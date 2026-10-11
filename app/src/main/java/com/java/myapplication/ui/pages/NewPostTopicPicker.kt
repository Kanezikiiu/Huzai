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
import com.java.myapplication.ui.glass.liquidElevation
import com.java.myapplication.ui.theme.isAppDarkTheme
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
import com.java.myapplication.data.HupuRepository
import com.java.myapplication.ui.components.HupuIcons
import com.java.myapplication.ui.components.HuzaiHalfSheet
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** 内容声明选项（对齐官方 MX 列表，值是 payload 的 containsAi） */
internal val DECLARE_OPTIONS = listOf(
    0 to "内容无需标注",
    1 to "含AI生成内容",
    2 to "含虚构演绎内容",
    3 to "内容含营销信息",
    4 to "个人观点，仅供参考",
    5 to "内容为转载",
)

internal fun declareLabel(v: Int): String =
    DECLARE_OPTIONS.firstOrNull { it.first == v }?.second ?: "内容无需标注"

/**
 * 1.224g：专区**本地子串匹配** —— 与「搜索结果页的专区面板」同一口径。
 *
 * 原因：服务端 `topic/search?search=` 会漏结果（真机实测：搜「足球」查不到「足球话题区」，
 * 但搜「足球话」可以）。本地按名称 contains 匹配不会漏，故发帖页的搜索改走这里。
 */
internal fun filterTopicsByKeyword(
    list: List<HupuPostApi.Topic>,
    keyword: String,
): List<HupuPostApi.Topic> {
    val kw = keyword.trim()
    return if (kw.isEmpty()) list else list.filter { it.name.contains(kw, ignoreCase = true) }
}

/**
 * 内容声明选择器（自研半屏面板）：6 档单选，点选即生效并关闭。
 * 1.224f: 由 ModalBottomSheet 改为自研半屏面板（性能）。
 */
@Composable
internal fun DeclarePickerSheet(
    visible: Boolean,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    HuzaiHalfSheet(visible = visible, onDismiss = onDismiss, fraction = 0.66f) {
        Column(Modifier.fillMaxWidth().weight(1f)) {
            Text(
                "内容声明",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            ) {
                items(DECLARE_OPTIONS, key = { it.first }) { (value, label) ->
                    val on = value == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable { onPick(value) }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            fontSize = 15.sp,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (on) {
                            Text("✓", fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 话题选择器（自研半屏面板）：搜索框 + 话题列表（tag/search?topicId=&name=），
 * 可多选（最多 2 个），点击行即切换选中，关闭由「完成」或下滑完成。
 * 1.224f: 由 ModalBottomSheet 改为自研半屏面板（性能）。
 */
@Composable
internal fun TagPickerSheet(
    visible: Boolean,
    topicId: Int,
    selectedIds: List<Int>,
    onToggle: (HupuPostApi.Tag) -> Unit,
    onDismiss: () -> Unit,
) {
    // 1.224f：面板内部的状态与拉取都放进 content —— 面板收起时不组合、不发请求。
    // （此前 ModalBottomSheet 是按需挂载，状态随开关重建；这里保持同样的时机）
    HuzaiHalfSheet(visible = visible, onDismiss = onDismiss, fraction = 0.66f) {
        var query by remember { mutableStateOf("") }
        var list by remember { mutableStateOf<List<HupuPostApi.Tag>?>(null) }

        LaunchedEffect(topicId, query) {
            val kw = query.trim()
            // 空关键词拉默认（热门）列表；1 个字不搜（服务端模糊匹配太宽）
            if (kw.length == 1) return@LaunchedEffect
            list = null
            list = HupuPostApi.tags(topicId, kw)
        }

        Column(Modifier.fillMaxWidth().weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "选择话题（最多 2 个）",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "完成",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            OutlinedTextField(
                colors = huzaiFieldColors(container = false),
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                placeholder = { Text("搜索话题", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            )
            val data = list
            if (data == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (data.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                    Text("没有找到话题", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    items(data, key = { it.id }) { tg ->
                        val on = selectedIds.contains(tg.id)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else Color.Transparent
                                )
                                .clickable { onToggle(tg) }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "#${tg.name}",
                                fontSize = 15.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (tg.tnum > 0) {
                                Text(
                                    "${tg.tnum}帖",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                if (on) "✓" else "＋",
                                fontSize = 15.sp,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 专区选择器（自研半屏面板）：
 * - 顶部搜索框（跨分类搜专区）
 * - 分类 chips（来自 /topic/cates 的 15 个分类）
 * - 专区列表（logo + 名称 + 帖数）；选中专区后若有子专区，列在名称下方作为 chips
 * 1.224f: 由 ModalBottomSheet 改为自研半屏面板（性能）。
 */
@Composable
internal fun PostTopicSheet(
    visible: Boolean,
    selectedTopicId: Int?,
    onPick: (HupuPostApi.Topic, HupuPostApi.Zone?) -> Unit,
    onDismiss: () -> Unit,
) {
    // 1.224f：面板内部的状态与拉取都放进 content —— 面板收起时不组合、不发请求。
    HuzaiHalfSheet(visible = visible, onDismiss = onDismiss, fraction = 0.66f) {
        var query by remember { mutableStateOf("") }
        var cates by remember { mutableStateOf<List<HupuPostApi.Cate>>(emptyList()) }
        var cateId by remember { mutableStateOf<Int?>(null) }
        var topics by remember { mutableStateOf<List<HupuPostApi.Topic>?>(null) }
        var zones by remember { mutableStateOf<List<HupuPostApi.Zone>>(emptyList()) }
        var pickedTopic by remember { mutableStateOf<HupuPostApi.Topic?>(null) }
        // 1.224g：全量专区表（本地搜索用）。
        // 1.224j：改为**面板一打开就预热**（见下方 LaunchedEffect）—— 此前是「第一次搜索时才拉」，
        // 于是首个字符输入后要等一次整树网络往返，期间列表还停在旧内容（真机反馈「慢悠悠」）。
        var allTopics by remember { mutableStateOf<List<HupuPostApi.Topic>?>(null) }
        val repo = remember { HupuRepository() }

        // 1.224j：预热全量专区表（与分类拉取并行；不阻塞后续搜索）
        LaunchedEffect(Unit) {
            val loaded = runCatching {
                repo.categories()
                    .flatMap { it.topics }
                    .distinctBy { it.topicId }
                    .mapNotNull { info ->
                        val id = info.topicId.toIntOrNull() ?: return@mapNotNull null
                        HupuPostApi.Topic(
                            id = id,
                            name = info.name,
                            logo = info.logo,
                            desc = info.desc.ifBlank { null },
                            count = null,
                            zoneId = 0,
                        )
                    }
            }.getOrNull()
            if (loaded != null) allTopics = loaded
        }

        LaunchedEffect(Unit) {
            cates = HupuPostApi.cates()
            // 1.224g（真机反馈）：不默认落在「我的」（关注专区）——没关注过专区的用户进来会看到空列表。
            // 优先选第一个非「我的」分类；若只有「我的」则退回第一条。
            if (cateId == null && cates.isNotEmpty()) {
                cateId = (cates.firstOrNull { !it.name.startsWith("我的") } ?: cates.first()).id
            }
        }
        // 分类切换 / 关键词搜索 → 拉专区
        LaunchedEffect(cateId, query) {
            val kw = query.trim()
            if (kw.isNotEmpty()) {
                // 1.224g（真机反馈）：**搜不到**根因是服务端 topic/search?search= 会漏结果
                // （实测「足球」查不到「足球话题区」，但「足球话」可以）。这里改为与
                // 「搜索结果页的专区面板」一致的**本地子串匹配**，再与服务端结果合并——
                // 二者取并集后按 id 去重，本地保证不漏、服务端补本地树里没有的新专区。
                //
                // 1.224j（真机反馈「输入后先停在旧内容、过一会儿才出结果」）：
                //  · 进入搜索立刻把列表切成「加载中」（topics = null）或**本地命中的即时结果**，
                //    绝不再把上一个分类的列表留在屏幕上；
                //  · 本地匹配同步算（预热已完成时零延迟），服务端结果到达后合并 —— 不再等网络才出屏。
                val l = allTopics
                topics = if (l != null) filterTopicsByKeyword(l, kw) else null
                pickedTopic = null
                zones = emptyList()
                val remote = runCatching { HupuPostApi.searchTopics(kw) }.getOrNull().orEmpty()
                val local = filterTopicsByKeyword(allTopics.orEmpty(), kw)
                topics = (local + remote).distinctBy { it.id }
            } else {
                val cid = cateId ?: return@LaunchedEffect
                topics = null
                topics = HupuPostApi.topicsByCate(cid)
                pickedTopic = null
                zones = emptyList()
            }
        }
        // 选中专区 → 拉子专区
        LaunchedEffect(pickedTopic?.id) {
            val t = pickedTopic ?: return@LaunchedEffect
            zones = HupuPostApi.zones(t.id)
        }

        Column(Modifier.fillMaxWidth().weight(1f)) {
            Text(
                if (pickedTopic == null) "选择专区" else "选择子专区（可选）",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            val curPicked = pickedTopic
            if (curPicked != null) {
                // 已选专区 + 子专区列表
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        curPicked.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "重选",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { pickedTopic = null }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        ZoneRow(
                            name = "不选子专区",
                            selected = false,
                            onClick = { onPick(curPicked, null) },
                        )
                    }
                    items(zones, key = { it.id }) { z ->
                        ZoneRow(
                            name = z.name,
                            selected = false,
                            onClick = { onPick(curPicked, z) },
                        )
                    }
                }
            } else {
                // 搜索框
                OutlinedTextField(
                    colors = huzaiFieldColors(container = false),
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    placeholder = { Text("搜索专区", fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "清空", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                )
                // 分类 chips（搜索时隐藏）
                if (query.isBlank()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(cates, key = { it.id }) { c ->
                            val on = cateId == c.id
                            Text(
                                c.name,
                                fontSize = 13.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        else MaterialTheme.colorScheme.surface
                                    )
                                    .clickable { cateId = c.id }
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                // 专区列表
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                ) {
                    val list = topics
                    if (list == null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    } else if (list.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                                Text("没有找到专区", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        items(list, key = { it.id }) { t ->
                            val on = t.id == selectedTopicId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (on) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                                    .clickable { pickedTopic = t }
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (t.logo != null) {
                                    AsyncImage(
                                        model = t.logo,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        t.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!t.desc.isNullOrBlank()) {
                                        Text(
                                            t.desc,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                if (!t.count.isNullOrBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${t.count}帖",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 子专区行 */
@Composable
internal fun ZoneRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surface
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Text("✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
