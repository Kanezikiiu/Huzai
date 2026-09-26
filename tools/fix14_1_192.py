#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
1.192 第十四批（真机反馈 4 条）

#1 Chip 按压「只有长按才看得见」→ 按下瞬间立即变暗（无动画）+ 70ms 快速缩放 + 松手保暗 90ms
#2 TopicPickerPage（自定义首页频道）
     · 上方 tab（已选频道条 SelectedBar）→ 玻璃材质
     · 下方 tab（大类过滤横滑条）→ 补「选中项居中」便捷特性
#3 ScorePickerPage（自定义评分频道）
     · 上方 tab（已选条 ScoreSelectedBar）→ 玻璃材质
     · 下方按钮（FlowRow 频道选择 chips）→ 玻璃材质
#4 SearchPage（搜索结果）
     · tab 条（排序 chips）→ 补「选中项居中」
     · 专区选择按钮 → 玻璃材质
"""
import io, re, sys

W = "/data/user/0/com.ai.assistance.operit/files/workspace/2dab7fe4-ceff-4d94-99a0-8e839ec2f3da"
P = W + "/app/src/main/java/com/java/myapplication/ui/pages/"
FEED = W + "/app/src/main/java/com/java/myapplication/ui/components/FeedUi.kt"
TOPIC = P + "TopicPickerPage.kt"
SCORE = P + "ScorePickerPage.kt"
SEARCH = P + "SearchPage.kt"


def read(p):
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def write(p, s):
    with io.open(p, "w", encoding="utf-8") as f:
        f.write(s)


def rep(text, old, new, tag, count=1):
    if text.count(old) != count:
        print("ASSERT FAIL [%s]: found %d, expected %d" % (tag, text.count(old), count))
        sys.exit(1)
    print("  ok %s (%d)" % (tag, count))
    return text.replace(old, new, count)


def add_import(text, anchor, line, tag):
    assert text.count(anchor) == 1, "import anchor not unique: %s" % tag
    return text.replace(anchor, anchor + line, 1)


# ============================================================ #1 FeedUi
FEED_IMPORTS = [
    ("import androidx.compose.animation.core.spring\n",
     "import androidx.compose.animation.core.tween\n", "F1 tween"),
    ("import androidx.compose.runtime.getValue\n",
     "import androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.setValue\n",
     "F2 mutableStateOf"),
    ("import kotlin.math.roundToInt\n",
     "import kotlinx.coroutines.delay\n", "F3 delay"),
]

FEED_OLD_PRESS = """    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chipPress",
    )
"""
FEED_NEW_PRESS = """    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 1.192c（真机反馈）：按压必须「按下瞬间即见」——轻点也要亮。
    // 只靠 spring 缩放起势太慢，轻点几乎看不见；改为「按下立即变暗 + 松手后保暗 90ms」，
    // 并配一个 70ms 的快速缩放。held 同时驱动变暗与缩放。
    var held by remember { mutableStateOf(false) }
    LaunchedEffect(pressed) {
        if (pressed) {
            held = true
        } else {
            delay(90)
            held = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (held) 0.94f else 1f,
        animationSpec = tween(70),
        label = "chipPress",
    )
"""

FEED_OLD_CONTENT = """    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
"""
FEED_NEW_CONTENT = """    // 按下遮罩：浅色变暗、深色提亮（与底部 Tab 栏同款语言），不做动画 → 立即生效
    val pressOverlay =
        if (held) (if (dark) Color.White.copy(0.14f) else Color.Black.copy(0.12f))
        else Color.Transparent
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
"""

FEED_OLD_BG = """            .clip(shape)
            .background(bg)
            .border(0.6.dp, edge, shape)
"""
FEED_NEW_BG = """            .clip(shape)
            .background(bg)
            .background(pressOverlay)
            .border(0.6.dp, edge, shape)
"""

# ============================================================ #2 TopicPickerPage
TOPIC_IMPORTS = [
    ("import androidx.compose.foundation.background\n",
     "import androidx.compose.foundation.border\n", "T1 border"),
    ("import androidx.compose.foundation.lazy.rememberLazyListState\n",
     "import com.java.myapplication.ui.components.animateChipCenterTo\n"
     "import com.java.myapplication.ui.theme.isAppDarkTheme\n", "T2 center/theme"),
]

TOPIC_OLD_DARK = """    val currentOnReorder by rememberUpdatedState(onReorder)
"""
TOPIC_NEW_DARK = """    val currentOnReorder by rememberUpdatedState(onReorder)
    val dark = isAppDarkTheme()
"""

TOPIC_OLD_ITEM = """                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = removable) { onRemove(t) }
"""
TOPIC_NEW_ITEM = """                    .clip(RoundedCornerShape(999.dp))
                    // 1.192c: 与顶部玻璃胶囊同源材质（拖动中仍保持 primaryContainer 反馈）
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primaryContainer
                        else if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f)
                    )
                    .border(
                        0.6.dp,
                        if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f),
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(enabled = removable) { onRemove(t) }
"""

TOPIC_OLD_CATEBAR = """                    // 大类过滤
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clipToBounds()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
"""
TOPIC_NEW_CATEBAR = """                    // 大类过滤（1.192c: 补「选中项居中」便捷特性，与主页话题条一致）
                    val cateBarState = rememberLazyListState()
                    val cateSelIdx =
                        if (selectedCate == null) 0
                        else 1 + categories.indexOfFirst { it.cateId == selectedCate }
                    LaunchedEffect(selectedCate) {
                        if (cateSelIdx >= 0) cateBarState.animateChipCenterTo(cateSelIdx)
                    }
                    LazyRow(
                        state = cateBarState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clipToBounds()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
"""

# ============================================================ #3 ScorePickerPage
SCORE_IMPORTS = [
    ("import androidx.compose.foundation.clickable\n",
     "import androidx.compose.ui.graphics.Color\n"
     "import com.java.myapplication.ui.theme.isAppDarkTheme\n", "S1 color/theme"),
]

SCORE_OLD_ITEM = """                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(enabled = removable) { onRemove(id) }
"""
SCORE_NEW_ITEM = """                    .clip(RoundedCornerShape(999.dp))
                    // 1.192c: 与顶部玻璃胶囊同源材质（拖动中仍保持 primaryContainer 反馈）
                    .background(
                        if (dragging) MaterialTheme.colorScheme.primaryContainer
                        else if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f)
                    )
                    .border(
                        0.6.dp,
                        if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f),
                        RoundedCornerShape(999.dp),
                    )
                    .clickable(enabled = removable) { onRemove(id) }
"""

SCORE_OLD_COL = """            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            ) {
                FlowRow(
"""
SCORE_NEW_COL = """            val dark = isAppDarkTheme()
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            ) {
                FlowRow(
"""

SCORE_OLD_CHIP = """                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    when {
                                        !checked -> MaterialTheme.colorScheme.surface
                                        locked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                                .border(
                                    width = 1.dp,
                                    // 1.192: 被锁定时描边与底色一起变浅——原来只有填充变浅，
                                    // 描边仍是实色，看起来像「没被那层浅色盖住」
                                    color = when {
                                        locked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        checked -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .clickable(enabled = !locked) { toggle(id) }
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
"""
SCORE_NEW_CHIP = """                        Box(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                // 1.192c: 材质升级为与顶部玻璃胶囊同源（选中=近实心磨砂 + 主题色文字；
                                // 未选=极淡半透明；宽度仍由文字决定 → 点击不位移、不重排）
                                .background(
                                    when {
                                        !checked -> if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f)
                                        locked -> if (dark) Color.White.copy(0.05f) else Color.Black.copy(0.03f)
                                        else -> if (dark) Color(0xFF2E2E30).copy(0.94f) else Color.White.copy(0.92f)
                                    }
                                )
                                .border(
                                    width = 0.6.dp,
                                    color = when {
                                        locked -> if (dark) Color.White.copy(0.04f) else Color.Black.copy(0.02f)
                                        checked -> if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.05f)
                                        else -> if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f)
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                )
                                .clickable(enabled = !locked) { toggle(id) }
                                .padding(horizontal = 16.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                name,
                                fontSize = 13.sp,
                                fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    checked -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                            )
                        }
"""

# ============================================================ #4 SearchPage
SEARCH_IMPORTS = [
    ("import androidx.compose.foundation.background\n",
     "import androidx.compose.foundation.border\n", "S1 border"),
    ("import androidx.compose.foundation.lazy.rememberLazyListState\n",
     "import com.java.myapplication.ui.components.animateChipCenterTo\n"
     "import com.java.myapplication.ui.theme.isAppDarkTheme\n", "S2 center/theme"),
]

SEARCH_OLD_BAR = """            // 筛选条：专区按钮 + 排序条（已搜索才显示）
            if (searched) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clipToBounds()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
"""
SEARCH_NEW_BAR = """            // 筛选条：专区按钮 + 排序条（已搜索才显示）
            val dark = isAppDarkTheme()
            if (searched) {
                // 1.192c: 补「选中项居中」便捷特性。
                // 序号 0 是「专区」按钮，排序项从 1 开始；选第 1 个排序项时回到最左，
                // 避免把唯一入口「专区」按钮挤出屏幕左侧。
                val filterBarState = rememberLazyListState()
                val sortSelIdx = 1 + SEARCH_SORTS.indexOfFirst { it.key == sortby }
                LaunchedEffect(sortby) {
                    if (sortSelIdx >= 1) {
                        if (sortSelIdx <= 1) filterBarState.animateScrollToItem(0)
                        else filterBarState.animateChipCenterTo(sortSelIdx)
                    }
                }
                LazyRow(
                    state = filterBarState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clipToBounds()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
"""

SEARCH_OLD_BTN = """                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (topicId != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { forumPickerOpen = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selectedForumName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (topicId != null) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = "选择专区",
                            tint = if (topicId != null) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
"""
SEARCH_NEW_BTN = """                    val forumActive = topicId != null
                    val forumTint =
                        if (forumActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            // 1.192c: 材质升级为与顶部玻璃胶囊同源（扁平半透明 + 极淡描边）
                            .background(
                                if (forumActive) {
                                    if (dark) Color(0xFF2E2E30).copy(0.94f) else Color.White.copy(0.92f)
                                } else {
                                    if (dark) Color.White.copy(0.075f) else Color.Black.copy(0.045f)
                                }
                            )
                            .border(
                                0.6.dp,
                                if (forumActive) {
                                    if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.05f)
                                } else {
                                    if (dark) Color.White.copy(0.06f) else Color.Black.copy(0.03f)
                                },
                                RoundedCornerShape(50),
                            )
                            .clickable { forumPickerOpen = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selectedForumName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = forumTint,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.ArrowDropDown,
                            contentDescription = "选择专区",
                            tint = forumTint,
                            modifier = Modifier.size(18.dp),
                        )
                    }
"""


def main():
    # ---------------- #1 FeedUi
    s = read(FEED)
    for anchor, line, tag in FEED_IMPORTS:
        assert s.count(anchor) == 1, "feed import anchor: " + tag
        s = s.replace(anchor, anchor + line, 1)
        print("  ok %s" % tag)
    s = rep(s, FEED_OLD_PRESS, FEED_NEW_PRESS, "F4 Chip 按压即时化")
    s = rep(s, FEED_OLD_CONTENT, FEED_NEW_CONTENT, "F5 Chip 按下遮罩")
    s = rep(s, FEED_OLD_BG, FEED_NEW_BG, "F6 Chip 遮罩落层")
    write(FEED, s)

    # ---------------- #2 TopicPickerPage
    s = read(TOPIC)
    for anchor, line, tag in TOPIC_IMPORTS:
        s = add_import(s, anchor, line, tag)
        print("  ok %s" % tag)
    s = rep(s, TOPIC_OLD_DARK, TOPIC_NEW_DARK, "T3 SelectedBar dark")
    s = rep(s, TOPIC_OLD_ITEM, TOPIC_NEW_ITEM, "T4 已选条玻璃化")
    s = rep(s, TOPIC_OLD_CATEBAR, TOPIC_NEW_CATEBAR, "T5 大类过滤居中")
    write(TOPIC, s)

    # ---------------- #3 ScorePickerPage
    s = read(SCORE)
    for anchor, line, tag in SCORE_IMPORTS:
        s = add_import(s, anchor, line, tag)
        print("  ok %s" % tag)
    s = rep(s, TOPIC_OLD_DARK, TOPIC_NEW_DARK, "S2 ScoreSelectedBar dark")
    s = rep(s, SCORE_OLD_ITEM, SCORE_NEW_ITEM, "S3 已选条玻璃化")
    s = rep(s, SCORE_OLD_COL, SCORE_NEW_COL, "S4 dark 声明")
    s = rep(s, SCORE_OLD_CHIP, SCORE_NEW_CHIP, "S5 频道按钮玻璃化")
    write(SCORE, s)

    # ---------------- #4 SearchPage
    s = read(SEARCH)
    for anchor, line, tag in SEARCH_IMPORTS:
        s = add_import(s, anchor, line, tag)
        print("  ok %s" % tag)
    s = rep(s, SEARCH_OLD_BAR, SEARCH_NEW_BAR, "Q1 排序条居中")
    s = rep(s, SEARCH_OLD_BTN, SEARCH_NEW_BTN, "Q2 专区按钮玻璃化")
    write(SEARCH, s)

    print("FIX14 1.192 OK")


main()