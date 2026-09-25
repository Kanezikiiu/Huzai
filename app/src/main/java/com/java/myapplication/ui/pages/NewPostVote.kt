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

/** 1.119: 投票面板里的单选 / 多选胶囊（选中填充主题色） */
@Composable
internal fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/** 1.119: 「最多可选」步进按钮（− / + 文本，避免依赖扩展图标库） */
@Composable
internal fun StepBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

/**
 * 1.113: 创建投票面板（ModalBottomSheet，2~10 个选项）。
 * 1.119: 支持单选（radio / limit 1）与多选（checkbox / limit N）。
 * 点击「创建投票」后先调 POST /api/v1/votes 拿 voteId，再由页面插入块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VoteCreateSheet(
    creating: Boolean,
    onSubmit: (String, List<String>, String, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.66f)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                "发起投票",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (multi) "多选 · 2~10 个选项，可同时选择多项" else "单选 · 2~10 个选项",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // 1.119: 单选 / 多选切换 + 多选时的「最多可选」步进器
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeChip("单选", selected = !multi) { multi = false }
                Spacer(Modifier.width(8.dp))
                ModeChip("多选", selected = multi) { multi = true }
                if (multi) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        "最多可选",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(6.dp))
                    StepBtn(label = "−", enabled = limit > 2) { limit-- }
                    Text(
                        "$limit",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp),
                    )
                    StepBtn(label = "+", enabled = limit < maxLimit) { limit++ }
                }
            }
            Spacer(Modifier.height(12.dp))
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
                        IconButton(onClick = { choices.removeAt(i) }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "删除选项",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (choices.size < 10) {
                Text(
                    "+ 添加选项",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !creating) { choices.add(""); addTick++ }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (canSubmit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
                    .clickable(enabled = canSubmit) {
                        onSubmit(voteTitle.trim(), picked(), modeType, modeLimit)
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (creating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text(
                        "创建投票",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
