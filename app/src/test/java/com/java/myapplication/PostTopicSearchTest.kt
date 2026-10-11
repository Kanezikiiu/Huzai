package com.java.myapplication

import com.java.myapplication.data.HupuPostApi
import com.java.myapplication.ui.pages.filterTopicsByKeyword
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 1.224g：发帖页「选择专区」的本地搜索口径。
 *
 * 起因（真机反馈）：服务端 `topic/search?search=` 会漏结果 —— 输入「足球」查不到「足球话题区」，
 * 但输入「足球话」可以。改为本地 `contains` 匹配后，「足球」必须能命中。
 */
class PostTopicSearchTest {

    private fun topic(id: Int, name: String) =
        HupuPostApi.Topic(id = id, name = name, logo = null, desc = null, count = null, zoneId = 0)

    private val all = listOf(
        topic(1, "足球话题区"),
        topic(2, "篮球场"),
        topic(3, "步行街"),
        topic(4, "中超"),
    )

    @Test
    fun `足球 命中足球话题区`() {
        val hit = filterTopicsByKeyword(all, "足球")
        assertEquals(listOf("足球话题区"), hit.map { it.name })
    }

    @Test
    fun `单字也能命中`() {
        assertTrue(filterTopicsByKeyword(all, "足").any { it.name == "足球话题区" })
        assertTrue(filterTopicsByKeyword(all, "篮").any { it.name == "篮球场" })
    }

    @Test
    fun `空关键词返回全集`() {
        assertEquals(all.size, filterTopicsByKeyword(all, " ").size)
    }

    @Test
    fun `无匹配返回空`() {
        assertTrue(filterTopicsByKeyword(all, "乒乓球").isEmpty())
    }

    @Test
    fun `去重合并顺序：本地在前`() {
        val local = filterTopicsByKeyword(all, "足球")
        val remote = listOf(topic(1, "足球话题区"), topic(9, "足球装备"))
        val merged = (local + remote).distinctBy { it.id }
        assertEquals(listOf("足球话题区", "足球装备"), merged.map { it.name })
    }
}