package com.java.myapplication.data

import kotlin.random.Random

/**
 * 1.332：首页热帖的「多板块轮转」控制器。
 *
 * ## 为什么需要它
 *
 * 之前热帖只有两个来源，各有硬伤：
 *  · `/all-gambia`（社区热帖）：几分钟才动一两条，下拉刷新几乎看不出变化；
 *  · `walkingStreet` 推荐流：虽然每次换一批，但**只有步行街主干道**，来源单一、混旧帖、
 *    水帖比例高（真机反馈「内容质量有点差」）。
 *
 * 虎扑的每个 `bbs.hupu.com/all-*` 板块页都带一份**完整的热帖流**（70 条，带封面、亮数、
 * 回复数、所属专区），实测每页约 1s（少数板块首次请求会慢到 5~11s）。
 *
 * ## 本期策略（经用户确认）
 *
 *  · 首屏：随机抽 **3** 个板块（含首页 `/`）→ 并行抓取 → 合并去重铺列表
 *  · 翻页：再随机抽 **1** 个补上（比再抽 3 个快）
 *  · 直到所有板块都被抽过（`remaining` 空）才停 / 重新洗牌
 *
 * ## 可测试性
 *
 * 随机源与伪随机数发生器全部可注入，单测用固定 seed 复现同一串顺序。
 */
internal object HupuBoardRotation {

    /**
     * 板块池白名单（**必须硬编码**）。
     *
     * 为什么不动态拼 slug：实测 `/all-nfl`、`/all-hot`、`/all-backend` 这类拼出来的地址
     * 能返回 70 条，但 `pageData.category.name` 变成「虎扑社区」——服务端没匹配到板块，
     * 给的是通用兜底流。只有白名单里的才会返回真正的板块数据。
     *
     * 池子里不含（按用户要求排除）：
     *  · `/all-gambia` 步行街 —— 与首页 `/` 大量重合
     *  · `/all-digital` 数码、`/all-cars` 汽车 —— 质量差、容易吵架
     *  · `/all-funny` 搞笑趣味 —— 废弃板块，内容全是别处搬来的
     */
    internal val POOL: List<String> = listOf(
        "/",              // 虎扑社区首页（全站混合，广度担当）
        "/all-nba",       // NBA
        "/all-cba",       // CBA
        "/all-lol",       // 英雄联盟
        "/all-gg",        // 游戏
        "/all-soccer",    // 国际足球
        "/all-csl",       // 中国足球
        "/all-sports",    // 综合体育
        "/all-ent",       // 影视娱乐
        "/all-gear",      // 装备
    )

    /** 首屏抽取数量 */
    internal const val HEAD_SIZE = 3

    /** 翻页时补抽数量（比再抽 3 个快） */
    internal const val MORE_SIZE = 1

    /**
     * 1.358：**有放回的真随机抽取**（首屏 / 下拉刷新用）。
     *
     * ## 为什么改（真机反馈「像伪随机、每次有顺序」）
     *
     * 旧实现是「不放回抽样」：抽过的板块从剩余池里移除。于是
     *  · 前 3 次刷新**必定**各抽到不同的板块 —— 看起来像按顺序遍历，而不是随机；
     *  · 第 4 次剩余池只剩 1 个 → 只取到 1 个板块（内容量骤减，这是实打实的 bug）；
     *  · 第 5 次池空才重新洗牌。
     *
     * 现在每次都从**完整池子**重新洗牌取 n 个：允许与历史重复（这才是用户期望的随机），
     * 只保证「不与上一次完全相同」，避免点了刷新却毫无变化。
     *
     * @param lastPicked 上一次抽中的组合（用于避免"刷了等于没刷"）
     */
    internal fun draw(
        lastPicked: List<String>,
        n: Int,
        random: Random = Random.Default,
    ): List<String> {
        if (POOL.isEmpty()) return emptyList()
        val take = n.coerceAtMost(POOL.size)
        if (lastPicked.isEmpty()) {
            return POOL.shuffled(random).take(take)
        }
        // 最多试 8 次找一个"与上次不完全相同"的组合（n 远小于池子，几乎一次就中）
        repeat(8) {
            val candidate = POOL.shuffled(random).take(take)
            if (candidate.toSet() != lastPicked.toSet()) return candidate
        }
        // 极端兜底（池子很小时）：仍然给出结果，不能让刷新变空
        return POOL.shuffled(random).take(take)
    }

    /**
     * 1.358：「上滑加载更多」用的抽取 —— 仍然**优先取本轮还没用过的**板块。
     *
     * 翻页的语义和刷新不同：用户已经在往下看，继续给他没出现过的板块体验更好，
     * 全部用完之后再回到整池随机（不会像旧实现那样枯竭到只剩 1 个）。
     *
     * @param usedBoards 本轮已经加载过的板块（会被原地更新：抽走的加进去）
     */
    internal fun drawUnused(
        usedBoards: MutableSet<String>,
        n: Int,
        random: Random = Random.Default,
    ): List<String> {
        if (POOL.isEmpty()) return emptyList()
        val take = n.coerceAtMost(POOL.size)
        var pool = POOL.filterNot { it in usedBoards }
        if (pool.size < take) {
            // 本轮板块都用过了 → 清空占用记录，重新从整池开始（不枯竭）
            usedBoards.clear()
            pool = POOL
        }
        val picked = pool.shuffled(random).take(take)
        usedBoards.addAll(picked)
        return picked
    }

}
