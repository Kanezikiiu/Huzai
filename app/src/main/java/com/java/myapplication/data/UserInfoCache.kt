package com.java.myapplication.data

/**
 * 1.190: 用户主页资料卡的内存缓存（LRU）。
 *
 * 起因：用户主页每次打开都会重新请求 getSpaceApi(getUserInfo)，请求回来之前整页是骨架屏。
 * 而真实使用路径里「帖子详情 → 点作者 → 返回 → 再点进同一作者 / 再从列表点另一个作者」
 * 非常高频，重复请求既慢又浪费。
 *
 * 策略：命中缓存先直出内容（零骨架），随后静默请求覆盖（stale-while-revalidate）；
 * 网络失败时保留缓存内容，不再显示失败态。
 *
 * 只缓存内存、不落盘——资料卡含登录态相关字段（is_self / 私信入口等），
 * 不适合长期驻留磁盘，冷启动重新拉取即可。
 */
internal class UserInfoCache(private val max: Int = 24) {

    // accessOrder = true → 每次 get 都把条目移到队尾，实现 LRU 淘汰
    private val map = object : LinkedHashMap<String, HupuUserProfile>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, HupuUserProfile>): Boolean =
            size > max
    }

    @Synchronized
    fun get(euid: String): HupuUserProfile? = if (euid.isEmpty()) null else map[euid]

    @Synchronized
    fun put(euid: String, value: HupuUserProfile) {
        if (euid.isNotEmpty()) map[euid] = value
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun clear() = map.clear()
}

/**
 * 1.190: 进程级实例。UI 各页面都是各自 `remember { HupuRepository() }`（非单例），
 * 缓存必须挂进程级才能在「详情 → 主页 → 返回 → 再进」之间复用。
 */
internal val hupuUserInfoCache = UserInfoCache()
