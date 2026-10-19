package com.java.myapplication.ui.pages

import android.net.Uri
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.java.myapplication.ui.components.SortBar
import com.java.myapplication.ui.components.LiquidIconButton
import com.java.myapplication.ui.glass.LiquidButton
import com.java.myapplication.ui.glass.buttonBorder
import com.java.myapplication.ui.glass.buttonFill
import com.java.myapplication.ui.theme.isAppDarkTheme
import com.java.myapplication.data.SortTab
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import com.java.myapplication.ui.components.huzaiFieldColors
import com.java.myapplication.ui.components.HuzaiHalfSheet
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
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
internal fun VoteBlockCard(
    title: String,
    choices: List<String>,
    index: Int,
    voteType: String,
    limit: Int,
    onRemove: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                HupuIcons.Poll,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    index > 0 && voteType == "checkbox" -> "投票 $index · 多选（最多 $limit 项）"
                    index > 0 -> "投票 $index"
                    voteType == "checkbox" -> "多选 · 最多 $limit 项"
                    else -> "投票"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable { onRemove() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "移除投票",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        choices.forEach { c ->
            Row(
                Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(if (voteType == "checkbox") RoundedCornerShape(3.dp) else CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                )
                Spacer(Modifier.width(8.dp))
                Text(c, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * 1.224o：「最多可选」步进按钮 —— 升级为 **kyant 液态玻璃按钮**（与发帖页工具按钮同源：
 * 液体按压形变 + 落影 + 玻璃底 + 极淡描边），尺寸按面板内联场景收到 30dp。
 * 不可用时只置灰 tint（与 LiquidIconButton 约定一致）。
 */
@Composable
internal fun StepBtn(icon: ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    LiquidIconButton(
        icon = icon,
        contentDescription = desc,
        onClick = onClick,
        size = 30.dp,
        iconSize = 16.dp,
        enabled = enabled,
        tint = if (enabled) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
    )
}

/**
 * 1.113: 创建投票面板（自研半屏面板，2~30 个选项）。
 * 1.119: 支持单选（radio / limit 1）与多选（checkbox / limit N）。
 * 1.224f: 由 ModalBottomSheet 改为自研半屏面板（性能）。
 * 点击「创建投票」后先调 POST /api/v1/votes 拿 voteId，再由页面插入块。
 */
@Composable
internal fun VoteCreateSheet(
    visible: Boolean,
    creating: Boolean,
    onSubmit: (String, List<String>, String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var voteTitle by remember { mutableStateOf("") }
    val choices = remember { mutableStateListOf("", "") }
    // 1.119: 单选 / 多选 + 多选时的「最多可选 N 项」
    var multi by remember { mutableStateOf(false) }
    var limit by remember { mutableIntStateOf(2) }

    fun picked(): List<String> = choices.map { it.trim() }.filter { it.isNotEmpty() }
    val maxLimit = picked().size.coerceAtLeast(2)
    LaunchedEffect(multi, maxLimit) { if (limit > maxLimit) limit = maxLimit }
    val modeType = if (multi) "checkbox" else "radio"
    val modeLimit = if (multi) limit.coerceIn(2, maxLimit) else 1
    val canSubmit = voteTitle.trim().isNotEmpty() && picked().size >= 2 && !creating
    val scrollState = rememberScrollState()
    var addTick by remember { mutableIntStateOf(0) }
    // 1.131: 添加选项后自动下滑到底部——否则新选项把「+ 添加选项」按钮顶出可视区
    LaunchedEffect(addTick) {
        if (addTick == 0) return@LaunchedEffect
        withFrameNanos { } // 等一帧：新选项完成测量/布局、maxValue 更新
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    HuzaiHalfSheet(visible = visible, onDismiss = onDismiss, fraction = 0.72f) {
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "发起投票",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            // 1.224o：**投票标题提到模式控件之上**。
            // 理由：标题是公共字段（单选 / 多选都只是"选项怎么选"，与标题无关），
            // 先填标题、再选模式、最后填选项，阅读与填写顺序更顺；切模式也不会打断标题。
            OutlinedTextField(
                colors = huzaiFieldColors(),
                value = voteTitle,
                onValueChange = { if (it.length <= 50) voteTitle = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("投票标题", fontSize = 15.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(12.dp))
            // 1.224n：单选 / 多选改为**与发帖页「类型」同款的分段控件**（iOS 式玻璃分段）。
            // 做法也照抄「类型」行：先在分段条下面垫一层**页面底色**的凹槽，
            // 否则半透明玻璃轨道会落在面板底色上被抬亮，选中胶囊与轨道分不出来。
            // 选中值直接用提交时的官方串（radio / checkbox），少一层映射。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SortBar(
                        sorts = listOf(
                            SortTab(0, "单选", "radio"),
                            SortTab(1, "多选", "checkbox"),
                        ),
                        selected = modeType,
                        segWidth = 56.dp,
                        onSelect = { multi = it == "checkbox" },
                    )
                }
                if (multi) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "最多可选",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    StepBtn(icon = HupuIcons.Remove, desc = "减少可选数量", enabled = limit > 2) { limit-- }
                    Text(
                        "$limit",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    StepBtn(icon = HupuIcons.Add, desc = "增加可选数量", enabled = limit < maxLimit) { limit++ }
                }
            }
            Spacer(Modifier.height(6.dp))
            // 1.224o：模式说明紧贴模式行（它随单选/多选变化，放在标题上方会与标题抢主次）
            Text(
                if (multi) "多选 · 2~30 个选项，可同时选择多项" else "单选 · 2~30 个选项",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            choices.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        colors = huzaiFieldColors(),
                        value = c,
                        onValueChange = { choices[i] = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("选项 ${i + 1}", fontSize = 15.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (choices.size > 2) {
                        Spacer(Modifier.width(8.dp))
                        // 1.224o：删除选项 → kyant 液态玻璃按钮（与「添加选项 / 创建投票 / 加减」同源同款）
                        LiquidIconButton(
                            icon = Icons.Rounded.Close,
                            contentDescription = "删除选项",
                            onClick = { choices.removeAt(i) },
                            size = 34.dp,
                            iconSize = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (choices.size < 30) {
                // 1.224o：「添加选项」升级为 kyant 液态玻璃按钮（与发帖页工具按钮同源同款）
                val addDark = isAppDarkTheme()
                LiquidButton(
                    onClick = { if (!creating) { choices.add(""); addTick++ } },
                    fill = buttonFill(addDark),
                    border = buttonBorder(addDark),
                    shape = RoundedCornerShape(20.dp),
                    height = 38.dp,
                    contentPadding = 14.dp,
                    isInteractive = !creating,
                ) {
                    Icon(
                        HupuIcons.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "添加选项",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (creating) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            // 1.224o：「创建投票」升级为 kyant 液态玻璃按钮（主按钮：可用=主题色实心；
            // 不可用=玻璃底 + 次要文字，与发帖页「发布」同一套状态语言）
            //
            // ⚠️ 状态判据必须把 `creating` 也算进"进行中"：canSubmit 里含 `!creating`，
            // 点下去的同一帧它就会翻成 false —— 若只用 canSubmit 决定底色，
            // 按钮会瞬间从实心主色切到近白玻璃底 + 白色转圈（白圈画在白底上＝看不见），
            // 真机表现为"点一下闪白一下"（慢放截图已确认）。
            val submitDark = isAppDarkTheme()
            val submitActive = canSubmit || creating
            LiquidButton(
                onClick = {
                    if (canSubmit) onSubmit(voteTitle.trim(), picked(), modeType, modeLimit)
                },
                modifier = Modifier.fillMaxWidth(),
                // 1.224o：**改为玻璃底**（真机反馈：填充主题色与整体风格不搭）——
// 可提交时用主题色**文字**表达"能点"，而不是整块实心色。
                fill = buttonFill(submitDark),
                border = buttonBorder(submitDark),
                shape = RoundedCornerShape(20.dp),
                height = 44.dp,
                contentPadding = 16.dp,
                isInteractive = canSubmit,
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        // 用主题色前景而非硬白：玻璃底上保持对比度
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        "创建投票",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (submitActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
