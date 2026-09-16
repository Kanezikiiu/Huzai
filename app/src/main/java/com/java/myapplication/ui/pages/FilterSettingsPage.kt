package com.java.myapplication.ui.pages

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuFilter
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard

/**
 * 信息流设置页（盖入式二级页）：三组关键词过滤。
 * - 标题关键词：命中 → 帖子条目不显示（首页/专区/搜索）
 * - 分区关键词：命中 → 帖子条目不显示（首页/专区/搜索）
 * - 评论关键词：命中 → 该条评论不显示（帖子回复/楼中楼/评分评论/孙评论）
 * 回车即添加（区分大小写不敏感）；点击 chip 删除；保存实时持久化。
 */
@Composable
fun FilterSettingsPage(onClose: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        SecondaryPage.enter()
        progress.animateTo(1f, tween(280))
    }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (closing) {
            SecondaryPage.exit()
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
    // 当前关键词快照（记住版本号，进入时读取；保存实时持久化并更新本地态）
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
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2f)
            .graphicsLayer { translationX = (1f - progress.value) * size.width }
            .background(MaterialTheme.colorScheme.background)
            .tapGuard(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // 顶栏：与主页频道自定义同款
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
                    "信息流设置",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().imePadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 140.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "intro") {
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
        }
    }
}

/** 一组关键词编辑器：标题 + 输入行（回车添加） + chip 流（点击删除） */
@Composable
private fun KeywordGroup(
    title: String,
    hint: String,
    words: List<String>,
    kind: String,
    onAdd: (String, String, () -> Unit) -> Unit,
    onRemove: (String, String) -> Unit,
) {
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
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("输入关键词，回车添加", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
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
            // chip 流：FlowRow 自适应换行，按内容实际宽度排布（不再固定 3 个/行）
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                words.forEach { w ->
                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onRemove(kind, w) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
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