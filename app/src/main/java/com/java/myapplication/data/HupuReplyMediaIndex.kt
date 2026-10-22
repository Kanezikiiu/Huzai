package com.java.myapplication.data

import java.util.concurrent.ConcurrentHashMap

/**
 * 1.226g：**回复媒体索引**（pid → 视频地址/封面）。
 *
 * 背景（真机样本 tid 643087279，楼中楼子回复 pid 23931）：
 *  - `bbs-reply-detail` 接口返回的子回复**没有** `video` 字段，`content` 也是空串；
 *  - 但帖子 SSR 的 `detail.lights` 数组里，同一个 pid **带着**
 *    `video` / `videoCover`（PC 页面就是用这份数据渲染视频回复的）。
 *
 * 所以这里在解析帖子时把 `lights`（以及 `replies.list`）里的视频信息按 pid 存成索引，
 * 等楼中楼加载完成后再按 pid 回填 —— 这样所有楼层入口（首页/专区/历史/消息/用户主页/
 * 帖子详情/嵌套帖子）都能自动生效，不必逐个改调用点。
 */
object HupuReplyMediaIndex {

    private val index = ConcurrentHashMap<String, Map<String, Pair<String, String>>>()

    /**
     * 1.226k：**全局 pid 索引**。
     *
     * pid 在虎扑全局唯一，所以再存一份「pid → 视频/封面」，供渲染层在
     * `sub.videoUrl` 为空时兜底查询 —— 不依赖楼中楼走哪条加载路径、也不依赖
     * 那份 UI 状态是什么时候构建的。
     */
    private val byPid = ConcurrentHashMap<String, Pair<String, String>>()

    fun videoOf(pid: String): Pair<String, String>? =
        if (pid.isBlank()) null else byPid[pid]

    fun clear(tid: String) {
        if (tid.isNotBlank()) index.remove(tid)
    }

    fun put(tid: String, videos: Map<String, Pair<String, String>>) {
        if (tid.isBlank()) return
        // 1.226i：**合并**而不是覆盖。
        // 回复分页（第 2 页起）解析出的 replies 更少，若直接覆盖会把第 1 页里的视频条目冲掉。
        if (videos.isEmpty()) return
        index.merge(tid, videos) { old, new -> old + new }
        // 同步进全局 pid 索引（渲染层兜底查）
        videos.forEach { (pid, v) -> if (pid.isNotBlank()) byPid[pid] = v }
    }

    fun videos(tid: String): Map<String, Pair<String, String>> =
        if (tid.isBlank()) emptyMap() else index[tid].orEmpty()

    /** 给缺少视频字段的子回复回填（已有值的保持不动） */
    fun applyTo(tid: String, subs: List<HupuSubReply>): List<HupuSubReply> {
        val v = videos(tid)
        if (v.isEmpty() || subs.isEmpty()) return subs
        var changed = false
        val out = subs.map { s ->
            if (s.videoUrl.isBlank()) {
                val hit = v[s.pid]
                if (hit != null) {
                    changed = true
                    s.copy(videoUrl = hit.first, videoCover = hit.second)
                } else {
                    s
                }
            } else {
                s
            }
        }
        return if (changed) out else subs
    }
}