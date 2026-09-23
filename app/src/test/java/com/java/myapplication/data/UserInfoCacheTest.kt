package com.java.myapplication.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 1.190: 用户主页资料卡缓存单测（LRU）。
 *
 * 目的：让「详情 → 用户主页 → 返回 → 再点同一作者」这条高频往返路径零骨架直出，
 * 同时保证缓存不会无上限增长（信息流里会连续点进很多不同作者）。
 */
class UserInfoCacheTest {

    private fun profile(euid: String) = HupuUserProfile(euid = euid, name = "u$euid")

    @Test
    fun putThenGet() {
        val c = UserInfoCache(max = 4)
        c.put("1", profile("1"))
        assertEquals("u1", c.get("1")?.name)
    }

    @Test
    fun missReturnsNull() {
        val c = UserInfoCache(max = 4)
        assertNull(c.get("nobody"))
        // 空 key 不参与缓存（避免把无效 euid 的结果写进去 / 取出来）
        c.put("", profile(""))
        assertNull(c.get(""))
        assertEquals(0, c.size())
    }

    @Test
    fun evictsLeastRecentlyUsed() {
        val c = UserInfoCache(max = 2)
        c.put("1", profile("1"))
        c.put("2", profile("2"))
        c.put("3", profile("3")) // 触发淘汰：1 最久未用
        assertNull(c.get("1"))
        assertEquals("u2", c.get("2")?.name)
        assertEquals("u3", c.get("3")?.name)
        assertEquals(2, c.size())
    }

    @Test
    fun getRefreshesRecency() {
        val c = UserInfoCache(max = 2)
        c.put("1", profile("1"))
        c.put("2", profile("2"))
        c.get("1") // 1 变成最近使用 → 下次淘汰 2
        c.put("3", profile("3"))
        assertEquals("u1", c.get("1")?.name)
        assertNull(c.get("2"))
    }

    @Test
    fun putSameKeyOverwritesWithoutGrowing() {
        val c = UserInfoCache(max = 4)
        c.put("1", profile("1"))
        c.put("1", HupuUserProfile(euid = "1", name = "fresh"))
        assertEquals(1, c.size())
        assertEquals("fresh", c.get("1")?.name)
    }
}