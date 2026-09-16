package com.java.myapplication.ui.pages

import com.java.myapplication.data.HupuReply
import com.java.myapplication.data.HupuSubReply

/**
 * 1.128：帖子详情「最外层评论流」的纯逻辑（不依赖 Compose，可单测）。
 */

/**
 * 只保留真一级评论：剔除「回复某条评论」的子回复。
 * 官方 SSR 会把子回复平铺进最外层 replies，而楼中楼里同样能看到 → 重复展示；
 * 真一级评论（回复楼主/主题）没有 quote 键，quotePid 为空。
 */
internal fun topLevelReplies(list: List<HupuReply>): List<HupuReply> =
    list.filter { it.quotePid.isEmpty() }

/** 1.128①B：过滤后真一级评论若不足此数且还有下一页 → 自动补拉下一页。 */
internal const val TOP_FILL_MIN = 15

/**
 * 1.128①B：是否需要自动补齐下一页。
 * 只看楼主模式自带全量翻页链（onlyOp effect），这里不重复触发。
 */
internal fun needAutoFillTop(
    realTopCount: Int,
    canLoadMore: Boolean,
    loadingMore: Boolean,
    onlyOp: Boolean,
): Boolean = !onlyOp && canLoadMore && !loadingMore && realTopCount < TOP_FILL_MIN

/**
 * 1.129：楼中楼渲染数据合并 = 服务端/缓存子回复 ⊕ 本机乐观子回复。
 * 按 pid 去重、保持顺序（服务端条目在前，真 pid 到位后以服务端为准）。
 */
internal fun mergeFloorSubs(
    base: List<HupuSubReply>,
    extra: List<HupuSubReply>,
): List<HupuSubReply> = (base + extra).distinctBy { it.pid }
