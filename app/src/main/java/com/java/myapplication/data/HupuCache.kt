package com.java.myapplication.data

import java.io.File

/**
 * 原始 HTML 磁盘缓存。
 * 目的：布局调试期间反复编译预览时不必每次真实请求虎扑；
 * 同时天然具备"断网可看最近内容"的降级能力。
 *
 * 策略：内存 LRU + 磁盘文件，TTL 10 分钟（列表）/ 30 分钟（详情）。
 * 1.125: 磁盘上限从"64 个文件"升级为"64MB 体积（1024 文件数兜底）"，最旧优先淘汰
 * ——专区 254 个子区 + 首页高频页的 SSR（单页 300~500KB）都能留住磁盘缓存。
 */
object HupuCache {
    private const val LIST_TTL_MS = 10 * 60 * 1000L
    private const val DETAIL_TTL_MS = 30 * 60 * 1000L
    private const val MAX_DISK_FILES = 1024

    /** 磁盘体积上限（internal set 供单测注入小值验证裁剪） */
    internal var maxDiskBytes = 64L * 1024 * 1024
        internal set

    private var cacheDir: File? = null
    private val memory = object : LinkedHashMap<String, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > 32
    }

    private class Entry(val html: String, val savedAt: Long)

    fun init(dir: File) {
        cacheDir = dir.apply { if (!exists()) mkdirs() }
    }

    fun get(path: String): String? {
        val key = path.replace('/', '_')
        synchronized(memory) {
            memory[key]?.let { e ->
                if (System.currentTimeMillis() - e.savedAt < ttlFor(path)) return e.html
                memory.remove(key)
            }
        }
        // 磁盘
        val f = fileFor(path) ?: return null
        if (!f.exists()) return null
        return try {
            val html = f.readText()
            val savedAt = f.lastModified()
            if (System.currentTimeMillis() - savedAt >= ttlFor(path)) {
                f.delete(); null
            } else {
                synchronized(memory) { memory[key] = Entry(html, savedAt) }
                html
            }
        } catch (e: Exception) { null }
    }

    fun put(path: String, html: String) {
        val key = path.replace('/', '_')
        val now = System.currentTimeMillis()
        synchronized(memory) { memory[key] = Entry(html, now) }
        val dir = cacheDir ?: return
        try {
            val f = fileFor(path) ?: return
            f.writeText(html)
            f.setLastModified(now)
            trimDisk(dir)
            trimDiskBySize(dir)
        } catch (e: Exception) { /* 磁盘满等，忽略 */ }
    }

    /** 清空全部缓存（调试用） */
    fun clear() {
        synchronized(memory) { memory.clear() }
        cacheDir?.listFiles()?.forEach { it.delete() }
    }

    private fun ttlFor(path: String): Long =
        if (path.endsWith(".html")) DETAIL_TTL_MS else LIST_TTL_MS

    private fun fileFor(path: String): File? {
        val dir = cacheDir ?: return null
        return File(dir, "hupu_${key(path)}.cache")
    }

    private fun key(path: String): String =
        path.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_").take(120)

    private fun trimDisk(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_DISK_FILES)
            .forEach { it.delete() }
    }

    /**
     * 1.125: 体积裁剪——总大小超过 maxDiskBytes 时按 lastModified 最旧优先删除。
     * 在文件数裁剪之后执行（先兜住文件数，再按体积精裁）。
     */
    private fun trimDiskBySize(dir: File) {
        val files = dir.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxDiskBytes) break
            total -= f.length()
            f.delete()
        }
    }
}