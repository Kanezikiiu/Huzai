package com.java.myapplication.ui.pages
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.java.myapplication.data.HupuMatchApi
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.ui.components.SecondaryPage
import com.java.myapplication.ui.components.tapGuard
/**
 * 评分频道自定义页（盖入式二级页）：与主页频道自定义同构。
 * - 顶部已选条：长按拖拽排序 / 点按移除（保存顺序即横滑条顺序）
 * - 下方全量网格：点按增删（选中高亮）
 * - 「虎扑评分」固定显示不参与配置；保存实时持久化 + 版本号驱动评分页刷新
 */
@Composable
fun ScorePickerPage(onClose: () -> Unit) {
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
    val games = HupuMatchApi.GAMES
    val nameById = remember(games) { games.associate { it.first to it.second } }
    // 已选频道（有序 id 列表，保存顺序 = 评分页横滑条顺序）。
    // 未配置过 → 默认全选（与评分页「未配置=全量」语义一致）；首次交互即写入自定义
    var selected by remember {
        mutableStateOf(
            if (HupuPrefs.hasCustomScoreGames()) HupuPrefs.loadScoreGames()
            else HupuMatchApi.GAMES.map { it.first }
        )
    }
    fun toggle(id: String) {
        if (id in selected) {
            val next = selected.filterNot { it == id }
            selected = next
            HupuPrefs.saveScoreGames(next)
        } else if (selected.size < HupuPrefs.MAX_SCORE_GAMES) {
            val next = selected + id
            selected = next
            HupuPrefs.saveScoreGames(next)
        }
    }
    fun remove(id: String) {
        val next = selected.filterNot { it == id }
        selected = next
        HupuPrefs.saveScoreGames(next)
    }
    fun reorder(newIds: List<String>) {
        selected = newIds
        HupuPrefs.saveScoreGames(newIds)
    }
    var resetAsk by remember { mutableStateOf(false) }
    fun resetDefault() {
        HupuPrefs.clearScoreGames()
        selected = emptyList()
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
            // 顶栏：返回 + 标题 + 计数 + 恢复默认（与主页频道自定义同款）
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
                    "自定义评分频道",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${selected.size}/${HupuPrefs.MAX_SCORE_GAMES}",
                    fontSize = 14.sp,
                    color = if (selected.size >= HupuPrefs.MAX_SCORE_GAMES) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { resetAsk = true }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "恢复默认", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (resetAsk) {
                AlertDialog(
                    onDismissRequest = { resetAsk = false },
                    title = { Text("清空评分频道") },
                    text = { Text("将清除全部已选评分频道，评分页恢复显示全部项目。此操作不可恢复。") },
                    confirmButton = {
                        TextButton(onClick = { resetDefault(); resetAsk = false }) {
                            Text("清空", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { resetAsk = false }) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                )
            }
            // 已选条（长按拖动排序 / 点按移除）
            if (selected.isEmpty()) {
                Text(
                    "还没有选择频道，从下方勾选吧",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            } else {
                ScoreSelectedBar(selected = selected, names = nameById, onReorder = ::reorder, onRemove = ::remove)
            }
            Text(
                "长按拖动排序 · 点按移除",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            )
            // 1.187: 全部项目改为自适应 chip 流（FlowRow），与「浏览流关键词」同款形态。
            // 旧版固定 3 列等宽胶囊有两个毛病：
            //   ① 名称 2~4 字宽窄不一（英超 / 网球 vs 王者荣耀），等宽格子里短名大量留白；
            //   ② 勾选时在胶囊内插入 16dp 对勾 → 文字被挤位移，并引发整行重排。
            // 现在 chip 宽度由文字决定，选中态只用「实心填充 + 白字」表达（不再插入对勾），
            // 因此点击选中 / 取消时 chip 尺寸恒定：不位移、不重排，只有颜色变化。
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    games.forEach { (id, name) ->
                        val checked = id in selected
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .border(
                                    width = 1.dp,
                                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .clickable { toggle(id) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name,
                                fontSize = 13.sp,
                                fontWeight = if (checked) FontWeight.Medium else FontWeight.Normal,
                                color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
                // 底部留白（等价于旧网格 contentPadding 的 bottom = 140.dp）
                Spacer(Modifier.height(140.dp))
            }
        }
    }
}
/** 评分频道已选条：长按拖拽排序（跨位交换式），点按移除（复刻主页 SelectedBar） */
@Composable
private fun ScoreSelectedBar(
    selected: List<String>,
    names: Map<String, String>,
    onReorder: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    val currentList by rememberUpdatedState(selected)
    val currentOnReorder by rememberUpdatedState(onReorder)
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
            .clipToBounds()
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { off ->
                        listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { off.x.toInt() in it.offset..(it.offset + it.size) }
                            ?.let { draggingIndex = it.index }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                        dragOffset += amount.x
                        val info = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == from }
                            ?: return@detectDragGesturesAfterLongPress
                        val center = info.offset + info.size / 2f + dragOffset
                        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { t ->
                            t.index != from && center > t.offset && center < (t.offset + t.size)
                        } ?: return@detectDragGesturesAfterLongPress
                        val list = currentList
                        if (target.index < list.size) {
                            val mutable = list.toMutableList()
                            mutable.add(target.index, mutable.removeAt(from))
                            currentOnReorder(mutable)
                            draggingIndex = target.index
                            dragOffset = 0f
                        }
                    },
                    onDragEnd = { draggingIndex = null; dragOffset = 0f },
                    onDragCancel = { draggingIndex = null; dragOffset = 0f },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(selected, key = { _, id -> id }) { index, id ->
            val dragging = index == draggingIndex
            Row(
                Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationX = if (dragging) dragOffset else 0f }
                    .animateItem()
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onRemove(id) }
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    names[id] ?: id,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.width(2.dp))
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "移除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
