package com.java.myapplication.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.java.myapplication.data.Adoutu
import com.java.myapplication.data.HupuPrefs
import com.java.myapplication.data.HupuSticker
import com.java.myapplication.data.stickerReferer
import com.java.myapplication.ui.glass.LiquidGlassDialog
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 1.191: 输入行统一行高——光标与文字垂直居中（默认 includeFontPadding 会把光标抬到顶部） */
private val INPUT_LINE_HEIGHT = 18.sp

/** 半屏搜索面板高度占屏比（微信式：贴底半屏，上方露出原页面） */
private const val STICKER_SEARCH_SHEET_HEIGHT = 0.6f

/**
 * 1.191: 表情搜索（数据源 adoutu）。
 *
 * 设计要点：
 *  · 状态挂在宿主页面（不在面板内部），开关面板来回都不丢搜索结果；
 *  · 三种「空」严格区分：还没搜 / 搜了没结果 / 真失败——失败态只在网络真失败后出现；
 *  · 收藏/插图沿用既有链路：点按 = 直接插入回复（下载落盘 → 图片附件）。
 */
internal class StickerSearchController(
    private val scope: CoroutineScope,
    /** 可注入的搜索实现（默认走 [Adoutu.search]）——便于单测「失败 / 无结果 / 翻页」分支 */
    private val search: suspend (String, Int) -> List<Adoutu.Sticker>? = Adoutu::search,
) {
    /** 上一次提交的关键词（用于「重试」与输入框回填） */
    var keyword by mutableStateOf("")
        private set
    var items by mutableStateOf<List<Adoutu.Sticker>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    /** 仅首页失败时置位（翻页失败另有 [moreFailed]，不会把已有结果清成失败态） */
    var failed by mutableStateOf(false)
        private set
    /** 「搜过了但没有结果」——与「还没搜」区分 */
    var empty by mutableStateOf(false)
        private set
    var noMore by mutableStateOf(false)
        private set
    var moreFailed by mutableStateOf(false)
        private set

    private var query = ""
    private var page = 0
    private var inFlight = false

    /**
     * 提交一次新搜索（空关键词直接忽略）。
     * [keepItems] = true 时保留上一次结果继续显示（输入即搜场景：改关键词不该把网格闪空）。
     */
    fun submit(raw: String, keepItems: Boolean = false) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val keep = keepItems && items.isNotEmpty()
        keyword = q
        query = q
        page = 1
        if (!keep) items = emptyList()
        failed = false
        empty = false
        noMore = false
        moreFailed = false
        loading = true
        inFlight = true
        scope.launch { fetch(q, 1, reset = true) }
    }

    /** 清空输入 → 回到「还没搜」的初始态（微信式：清掉关键词就回到起点） */
    fun clear() {
        keyword = ""
        query = ""
        page = 0
        items = emptyList()
        loading = false
        failed = false
        empty = false
        noMore = false
        moreFailed = false
    }

    /** 触底加载下一页；[force] 用于「翻页失败后点重试」 */
    fun loadMore(force: Boolean = false) {
        if (query.isEmpty() || inFlight || loading || noMore) return
        if (moreFailed && !force) return
        inFlight = true
        moreFailed = false
        page += 1
        scope.launch { fetch(query, page, reset = false) }
    }

    private suspend fun fetch(q: String, p: Int, reset: Boolean) {
        val res = search(q, p)
        // 已被新的搜索/清空取代 → 丢弃这次结果（不碰 inFlight，由当前那次请求收尾）
        if (q != query) return
        inFlight = false
        if (res == null) {
            if (reset) {
                loading = false
                failed = true
            } else {
                moreFailed = true
            }
            return
        }
        if (reset) {
            items = res
            loading = false
            empty = res.isEmpty()
            page = 1
        } else {
            items = items + res.filterNot { n -> items.any { it.url == n.url } }
        }
        // 不满一页 = 没有下一页了（站点每页固定 40 条）
        noMore = res.size < Adoutu.PAGE_SIZE
    }
}

/** 宿主页面用：搜索状态跨面板开关存活（remember 在页面作用域） */
@Composable
internal fun rememberStickerSearchController(): StickerSearchController {
    val scope = rememberCoroutineScope()
    return remember(scope) { StickerSearchController(scope) }
}

/**
 * 表情图片的加载模型。
 * adoutu 图片有防盗链（无 Referer 403），必须逐请求带 Referer；
 * 虎扑图床/本地 file:// 不需要 → 原样返回 URL，零行为变化。
 */
@Composable
internal fun stickerImageModel(url: String): Any {
    val ctx = LocalContext.current
    return remember(url) {
        val ref = stickerReferer(url)
        if (ref == null) url
        else ImageRequest.Builder(ctx).data(url).addHeader("Referer", ref).build()
    }
}

/**
 * 表情面板里的搜索入口：**图标按钮**（点击弹出半屏搜索面板，不再是一个 tab）。
 * 与既有 tab 胶囊区分开——它是动作，不是分类。
 */
@Composable
internal fun StickerSearchEntry(onClick: () -> Unit) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Search,
            contentDescription = "搜索表情",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
}

/**
 * 1.191: 表情搜索半屏面板（微信式）——贴底半屏、顶部圆角、从底部滑入；
 * 点遮罩 / 点「取消」/ 返回键收起（收起动画播完回调 [onClosed]）。
 *
 * **明确不避让键盘**：系统键盘是独立窗口，会盖住面板下半部分——这是刻意的。
 * 输入框在面板顶部，键盘升起后依然可见；想看完整个结果网格，用户自己收起键盘即可，
 * 收起后面板位置不动、被遮挡区域自动露出。
 */
@Composable
internal fun StickerSearchSheet(
    controller: StickerSearchController,
    closing: Boolean,
    onPick: (HupuSticker) -> Unit,
    onRequestClose: () -> Unit,
    onClosed: () -> Unit,
    bottomPad: Dp,
    zIndex: Float = 1200f,
    /** 待确认的清空目标（null = 不显示确认弹窗）；状态提升到页面，好让返回键先收弹窗 */
    clearTarget: String? = null,
    onClearRequest: (String) -> Unit = {},
    onClearDismiss: () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(280)) }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, tween(280))
            onClosed()
        }
    }
    // 1.191: 面板自身的内容层——毛玻璃确认弹窗靠它取到「背后像素」
    // 1.191: 先铺一层不透明页面底色再画内容——记录层若是透明的，
    // 卡片就只剩半透明白浮在页面上，视觉上会「花」
    val dialogBg = MaterialTheme.colorScheme.background
    val sheetBackdrop = rememberLayerBackdrop {
        drawRect(dialogBg)
        drawContent()
    }
    Box(
        Modifier
            .fillMaxSize()
            .zIndex(zIndex),
    ) {
        // 记录层：遮罩 + 半屏面板（弹窗画在它之后，才能采到它）
        Box(
            Modifier
                .fillMaxSize()
                // 半屏遮罩（与楼中楼 sheet 同规格）：点面板外任意处收起
                .background(Color.Black.copy(alpha = 0.25f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onRequestClose() }
                .layerBackdrop(sheetBackdrop),
        ) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(STICKER_SEARCH_SHEET_HEIGHT)
                    .graphicsLayer { translationY = (1f - progress.value) * size.height }
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .tapGuard(),
            ) {
                // 抓手条
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                        .align(Alignment.CenterHorizontally),
                )
                StickerSearchBody(
                    controller = controller,
                    bottomPad = bottomPad,
                    onPick = onPick,
                    onCancel = onRequestClose,
                    onClear = onClearRequest,
                )
            }
        }
        // 清空历史确认（Liquid Glass 弹窗，与底部 Tab 栏同源）
        clearTarget?.let { target ->
            LiquidGlassDialog(
                backdrop = sheetBackdrop,
                title = "清空$target",
                message = "将删除全部${target}记录，此操作不可恢复。",
                confirmText = "清空",
                dismissText = "取消",
                onConfirm = {
                    // 只执行清空动作——收起动画与卸载由弹窗内部 close() 负责
                    when (target) {
                        "最近使用" -> HupuPrefs.clearStickerRecent()
                        else -> HupuPrefs.clearStickerSearchLog()
                    }
                },
                onDismiss = onClearDismiss,
            )
        }
    }
}

/** 面板内容：搜索输入行（放大镜 + 输入框 + 取消） + 结果网格 */
@Composable
private fun StickerSearchBody(
    controller: StickerSearchController,
    bottomPad: Dp,
    onPick: (HupuSticker) -> Unit,
    onCancel: () -> Unit,
    /** 点「最近使用 / 最近在搜」的垃圾桶 → 交给上层弹确认框（传区块标题） */
    onClear: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    // 1.191: 用 TextFieldValue 而非 String——重开面板时要显式把光标放到**文本末尾**
    // （String 版无法指定 selection，重开时光标会落在最前面，续写很不方便）。
    // 初始值取上次提交的关键词，selection 直接给到末尾。
    var input by remember {
        val kw = controller.keyword
        mutableStateOf(TextFieldValue(kw, TextRange(kw.length)))
    }
    // 打开即聚焦（微信式：弹出面板就准备输入）
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // 面板离开组合（收起/切页）时收起键盘
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    // 1.191: 输入即搜（微信式）——停顿 300ms 自动提交，不再依赖回车键。
    // 同关键词且已有结果时直接跳过（重开面板不重复请求、不闪动）。
    // key 用 input.text：光标移动（selection 变化）不该重新触发搜索。
    LaunchedEffect(input.text) {
        val q = input.text.trim()
        if (q.isEmpty()) {
            controller.clear()
            return@LaunchedEffect
        }
        if (q == controller.keyword && controller.items.isNotEmpty()) return@LaunchedEffect
        delay(300)
        controller.submit(q, keepItems = true)
    }
    // 1.191: 搜出结果才记入「最近在搜」（无结果 / 被清空不记，避免噪声历史）
    LaunchedEffect(controller.keyword, controller.items) {
        if (controller.items.isNotEmpty()) HupuPrefs.pushStickerSearchLog(controller.keyword)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (input.text.isEmpty()) {
                        Text(
                            "搜索表情包，如「猫」「熊猫头」",
                            fontSize = 12.sp,
                            lineHeight = INPUT_LINE_HEIGHT,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        // 1.191: 关掉默认的 includeFontPadding（它会给首行上方留白，
                        // 让光标贴到布局顶部、看起来偏上），行高与 placeholder 对齐
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            lineHeight = INPUT_LINE_HEIGHT,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.None,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            // 输入即搜已是主路径，回车只负责收键盘
                            keyboard?.hide()
                        }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (input.text.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "清空",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable {
                                input = TextFieldValue("")
                                controller.clear()
                            },
                    )
                }
            }
            Text(
                "取消",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onCancel() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        Box(Modifier.fillMaxSize()) {
            when {
                // 已有结果时优先显示网格：输入即搜过程中不闪「搜索中…」空白
                controller.items.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = bottomPad),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(controller.items, key = { it.id }) { st ->
                        StickerSearchCell(
                            url = st.url,
                            onPick = { onPick(HupuSticker(st.url)) },
                        )
                    }
                    if (!controller.noMore) {
                        item(key = "__sticker_more__") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(34.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (controller.moreFailed) {
                                    Text(
                                        "加载失败，点此重试",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { controller.loadMore(force = true) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                } else {
                                    // 触底哨兵：本格进入组合即取下一页（分页懒加载，不做预取）
                                    LaunchedEffect(controller.items.size) { controller.loadMore() }
                                    CircularProgressIndicator(
                                        Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }

                controller.loading -> CenterHint("搜索中…", spinner = true)

                controller.failed -> CenterHint("搜索失败，请稍后再试", action = {
                    Text(
                        "重试",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { controller.submit(controller.keyword) }
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                })

                controller.empty -> CenterHint("没有找到相关表情\n换个关键词试试")

                // 1.191: 未搜索时不再留白——上方「最近使用」表情行 + 下方「最近在搜」关键词流
                else -> StickerRecentPane(
                    onPick = onPick,
                    onSearch = { kw -> input = TextFieldValue(kw, TextRange(kw.length)) },
                    onClear = onClear,
                )
            }
        }
    }
}

/** 结果格子：点按 = 插入回复（1.191: 已去掉长按收藏） */
@Composable
private fun StickerSearchCell(
    url: String,
    onPick: () -> Unit,
) {
    Box(
        Modifier
            // 1.192: 填满格子并保持正方形（原 44dp 在网格里偏小，带字表情看不清）
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .clickable {
                // 1.191: 用过的表情进「最近使用」（按 URL 去重后置顶）
                HupuPrefs.pushStickerRecent(url)
                onPick()
            },
    ) {
        AsyncImage(
            model = stickerImageModel(url),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

/** 居中提示（可带 loading 或一个操作按钮） */
@Composable
private fun BoxScope.CenterHint(
    text: String,
    spinner: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val m = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 24.dp)
    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
        if (spinner) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let {
            Spacer(Modifier.height(8.dp))
            it()
        }
    }
}

/**
 * 1.191: 未搜索时的面板内容（微信式）——「最近使用」表情行 +「最近在搜」关键词流。
 * 两项都为空时退回一句提示，避免面板空荡。
 * 点击历史表情 = 直接插入（并把它顶到最近使用最前）；点击历史关键词 = 回填输入框并触发搜索。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BoxScope.StickerRecentPane(
    onPick: (HupuSticker) -> Unit,
    onSearch: (String) -> Unit,
    /** 点垃圾桶：交给宿主弹确认框（传区块标题），确认后才真正清空 */
    onClear: (String) -> Unit,
) {
    val version = HupuPrefs.stickerRecentVersion
    // 1.192: 「最近使用」格子与搜索结果网格同一尺寸
    // （网格：5 列、水平 padding 共 24dp、列间距共 40dp → 格子 =（屏宽 - 64）/ 5）
    val screenW = LocalConfiguration.current.screenWidthDp
    val recentCell = ((screenW - 64) / 5).coerceAtLeast(48).dp
    val recents = remember(version) { HupuPrefs.loadStickerRecent() }
    val logs = remember(version) { HupuPrefs.loadStickerSearchLog() }
    if (recents.isEmpty() && logs.isEmpty()) {
        CenterHint("输入关键词即可搜索表情包")
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (recents.isNotEmpty()) {
            StickerSectionHeader("最近使用") { onClear("最近使用") }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(recents, key = { it.url }) { s ->
                    Box(
                        Modifier
                            .size(recentCell)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .clickable {
                                HupuPrefs.pushStickerRecent(s.url)
                                onPick(s)
                            },
                    ) {
                        AsyncImage(
                            model = stickerImageModel(s.url),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                        )
                    }
                }
            }
        }
        if (logs.isNotEmpty()) {
            StickerSectionHeader("最近在搜") { onClear("最近在搜") }
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                logs.forEach { kw ->
                    Text(
                        kw,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            .clickable { onSearch(kw) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

/** 区块标题 + 右侧「清空」垃圾桶（微信式） */
@Composable
private fun StickerSectionHeader(title: String, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Rounded.Delete,
            contentDescription = "清空$title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .clickable(onClick = onClear),
        )
    }
}