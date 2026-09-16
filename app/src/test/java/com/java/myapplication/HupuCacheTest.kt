package com.java.myapplication

import com.java.myapplication.data.HupuCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 1.125: HupuCache 磁盘缓存测试——体积上限裁剪（maxDiskBytes）与 TTL 行为。
 * HupuCache 是 object（内存 LRU 单例），每个测试先 init 新目录 + clear 清内存做隔离。
 */
class HupuCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 每个测试独立的干净缓存目录（内存 + 磁盘都清零） */
    private fun freshDir(): File {
        val dir = tmp.newFolder()
        HupuCache.init(dir)
        HupuCache.clear()
        return dir
    }

    @Test
    fun `put 后 get 可读回且写入磁盘`() {
        val dir = freshDir()
        HupuCache.put("/abc.html", "<p>hello</p>")
        assertEquals("<p>hello</p>", HupuCache.get("/abc.html"))
        // 磁盘文件确实存在（key 替换 / 和 . 为 _）
        val f = dir.listFiles()!!.first { it.name.contains("abc") }
        assertTrue(f.length() > 0)
    }

    @Test
    fun `超过体积上限时最旧文件被裁剪`() {
        val dir = freshDir()
        val saved = HupuCache.maxDiskBytes
        try {
            // 上限 250B；三个 100B 缓存 → 第 3 个写入后总量 300B 超限，最旧的 p1 被删
            HupuCache.maxDiskBytes = 250L
            HupuCache.put("/p1.html", "a".repeat(100))
            Thread.sleep(60) // 拉开 lastModified 差距，保证裁剪顺序稳定
            HupuCache.put("/p2.html", "b".repeat(100))
            Thread.sleep(60)
            HupuCache.put("/p3.html", "c".repeat(100))

            assertEquals("c".repeat(100), HupuCache.get("/p3.html"))

            val names = dir.listFiles()!!.map { it.name }
            assertTrue("最旧的 p1 应被体积裁剪删除", names.none { it.contains("p1") })
            assertTrue("较新的 p2 应保留", names.any { it.contains("p2") })
            assertTrue("最新的 p3 应保留", names.any { it.contains("p3") })
        } finally {
            HupuCache.maxDiskBytes = saved
        }
    }

    @Test
    fun `体积未超上限时不裁剪`() {
        freshDir()
        val saved = HupuCache.maxDiskBytes
        try {
            HupuCache.maxDiskBytes = 1024L * 1024L
            HupuCache.put("/q1.html", "a".repeat(200))
            HupuCache.put("/q2.html", "b".repeat(200))
            assertEquals("a".repeat(200), HupuCache.get("/q1.html"))
            assertEquals("b".repeat(200), HupuCache.get("/q2.html"))
        } finally {
            HupuCache.maxDiskBytes = saved
        }
    }

    @Test
    fun `TTL 过期的详情缓存 get 返回 null 并删文件`() {
        val dir = freshDir()
        // 直接构造磁盘文件（绕开 put，避免内存 LRU 命中）：拨回 31 分钟前 → 超详情 TTL
        val f = File(dir, "hupu__123456_html.cache")
        f.writeText("<p>detail</p>")
        f.setLastModified(System.currentTimeMillis() - 31 * 60 * 1000L)
        assertNull(HupuCache.get("/123456.html"))
        assertTrue("过期文件应被删除", !f.exists())
    }

    @Test
    fun `未知 key 的 get 返回 null 不抛异常`() {
        freshDir()
        assertNull(HupuCache.get("/not-exist-anywhere.html"))
    }
}