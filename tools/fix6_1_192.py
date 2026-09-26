#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""1.192 第六批（三项衍生问题）：
   ① 重置按钮：改为「清空所有已添加频道，仅保留固定项（热帖/虎扑评分）」+ 重写弹窗措辞。
      关键：要写入「空的自定义列表」而不是删 key——删 key 会被当成「未配置」而回退到官方全量。
   ② 守卫公式写反：canHide 应为 hotHidden || 自定义列表非空
      （原来 !hotHidden || ... 会让「仅剩固定项」时开关仍可点，点完才置灰，出现
        「开关显示关闭、页面却仍保留固定 tab」的不一致）
   ③ 首页 tab：已选中时再次点击应回到「热帖」，但热帖被隐藏时不应切换。
   另附自愈：修正历史遗留的非法态（固定项隐藏 + 自定义列表为空）。
   原子式：先全部校验命中数，再统一写盘。"""
import io

EDITS = {}

def patch(path, old, new, expect):
    EDITS.setdefault(path, []).append((old, new, expect))

B = "app/src/main/java/com/java/myapplication/"

# ---------- ② 守卫公式（两个自定义页） ----------
patch(B + "ui/pages/TopicPickerPage.kt",
      "    val canHideHot = !hotHidden || selected.isNotEmpty()\n",
      "    // 1.192: 开关是否可操作——「关掉它就会变成 0 个 tab」时不可操作（置灰 + 红字说明）。\n"
      "    // 注意方向：开关已关（热帖隐藏）→ 永远可以再打开；开关开着 → 只有还有别的话题才能关。\n"
      "    val canHideHot = hotHidden || selected.isNotEmpty()\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "    val canHideCommon = !commonHidden || selected.isNotEmpty()\n",
      "    // 1.192: 同上——开关已关时永远可开；开着时只有还有别的赛事才能关\n"
      "    val canHideCommon = commonHidden || selected.isNotEmpty()\n", 1)

# ---------- ① 重置行为 + 措辞 ----------
patch(B + "ui/pages/TopicPickerPage.kt",
      "    fun resetDefault() {\n"
      "        HupuPrefs.clearHomeTopics()\n"
      "        selected = emptyList()\n"
      "        // 1.192: 话题被清空后若热帖仍隐藏，会出现 0 个 tab → 同时恢复显示热帖\n"
      "        if (hotHidden) {\n"
      "            hotHidden = false\n"
      "            HupuPrefs.setHomeHotHidden(false)\n"
      "        }\n"
      "    }\n",
      "    fun resetDefault() {\n"
      "        // 1.192: 「重置」= 清空所有已添加的频道，只保留「热帖」。\n"
      "        // 必须写「空的自定义列表」而不是删掉这个 key：删 key 会被当成「未配置」，\n"
      "        // 反而回退到官方热门全量话题；写空列表才是「一个话题都不显示」。\n"
      "        HupuPrefs.saveHomeTopics(emptyList())\n"
      "        selected = emptyList()\n"
      "        if (hotHidden) {\n"
      "            hotHidden = false\n"
      "            HupuPrefs.setHomeHotHidden(false)\n"
      "        }\n"
      "    }\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "    fun resetDefault() {\n"
      "        HupuPrefs.clearScoreGames()\n"
      "        selected = emptyList()\n"
      "        // 1.192: 赛事被清空后若虎扑评分仍隐藏，会出现 0 个 tab → 同时恢复显示\n"
      "        if (commonHidden) {\n"
      "            commonHidden = false\n"
      "            HupuPrefs.setScoreCommonHidden(false)\n"
      "        }\n"
      "    }\n",
      "    fun resetDefault() {\n"
      "        // 1.192: 「重置」= 清空所有已添加的赛事频道，只保留「虎扑评分」。\n"
      "        // 同首页：写空列表（而非删 key），避免被当成「未配置」而回退到全量赛事。\n"
      "        HupuPrefs.saveScoreGames(emptyList())\n"
      "        selected = emptyList()\n"
      "        if (commonHidden) {\n"
      "            commonHidden = false\n"
      "            HupuPrefs.setScoreCommonHidden(false)\n"
      "        }\n"
      "    }\n", 1)

# 弹窗措辞
patch(B + "ui/pages/TopicPickerPage.kt",
      "                title = \"清空首页频道\",\n"
      "                message = \"将清除全部已选首页频道，主页恢复显示默认频道。此操作不可恢复。\",\n"
      "                confirmText = \"清空\",\n",
      "                title = \"重置首页频道\",\n"
      "                message = \"将清空所有已添加的频道，只保留「热帖」。此操作不可恢复。\",\n"
      "                confirmText = \"重置\",\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "                title = \"清空评分频道\",\n"
      "                message = \"将清除全部已选评分频道，评分页恢复显示全部项目。此操作不可恢复。\",\n"
      "                confirmText = \"清空\",\n",
      "                title = \"重置评分频道\",\n"
      "                message = \"将清空所有已添加的赛事频道，只保留「虎扑评分」。此操作不可恢复。\",\n"
      "                confirmText = \"重置\",\n", 1)

# 顶栏按钮的无障碍描述同步
patch(B + "ui/pages/TopicPickerPage.kt",
      "Icons.Rounded.Refresh, contentDescription = \"恢复默认\"",
      "Icons.Rounded.Refresh, contentDescription = \"重置频道\"", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "Icons.Rounded.Refresh, contentDescription = \"恢复默认\"",
      "Icons.Rounded.Refresh, contentDescription = \"重置频道\"", 1)

# ---------- 自愈：修正历史遗留的非法态 ----------
patch(B + "ui/pages/TopicPickerPage.kt",
      "            selected = if (HupuPrefs.hasCustomHomeTopics()) HupuPrefs.loadHomeTopics() else hotTopics\n"
      "        }\n",
      "            selected = if (HupuPrefs.hasCustomHomeTopics()) HupuPrefs.loadHomeTopics() else hotTopics\n"
      "            // 1.192: 自愈——「热帖隐藏 + 一个话题都没有」是非法态（首页会兜底显示热帖，\n"
      "            // 于是开关显示关闭、页面却仍有热帖）。这里恢复成「显示热帖」，保证两者一致。\n"
      "            if (HupuPrefs.isHomeHotHidden() && selected.isEmpty()) {\n"
      "                hotHidden = false\n"
      "                HupuPrefs.setHomeHotHidden(false)\n"
      "            }\n"
      "        }\n", 1)
patch(B + "ui/pages/ScorePickerPage.kt",
      "    val onlyOneLeft = commonHidden && selected.size == 1\n"
      "    fun toggle(id: String) {\n",
      "    val onlyOneLeft = commonHidden && selected.size == 1\n"
      "    // 1.192: 自愈——「虎扑评分隐藏 + 一个赛事都没有」是非法态（评分页会兜底显示它，\n"
      "    // 于是开关显示关闭、页面却仍有虎扑评分）。这里恢复成「显示虎扑评分」。\n"
      "    LaunchedEffect(Unit) {\n"
      "        if (commonHidden && selected.isEmpty()) {\n"
      "            commonHidden = false\n"
      "            HupuPrefs.setScoreCommonHidden(false)\n"
      "        }\n"
      "    }\n"
      "    fun toggle(id: String) {\n", 1)

# ---------- ③ 首页 tab：已选中再点，仅在有热帖时才回到热帖 ----------
patch(B + "ui/pages/HomePage.kt",
      "            if (selected == it) selected = \"hot\"\n"
      "            else {\n"
      "                selected = it\n"
      "                selectedSort = null\n"
      "            }\n",
      "            if (selected == it) {\n"
      "                // 1.192: 再点已选 = 回到「热帖」；但热帖被隐藏时该动作无意义 → 保持不动\n"
      "                if (showHot) selected = \"hot\"\n"
      "            } else {\n"
      "                selected = it\n"
      "                selectedSort = null\n"
      "            }\n", 1)

# ---------- 原子应用 ----------
total = 0
for path, edits in EDITS.items():
    with io.open(path, encoding='utf-8') as f:
        s = f.read()
    for old, new, expect in edits:
        n = s.count(old)
        assert n == expect, "%s: expect %d of %r, got %d" % (path, expect, old[:70], n)
        s = s.replace(old, new)
        total += n
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(s)
    print("OK %s (%d edits)" % (path, len(edits)))
print("FIX6 1.192 OK, %d replacements" % total)