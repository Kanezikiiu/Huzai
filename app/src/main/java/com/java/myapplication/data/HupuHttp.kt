package com.java.myapplication.data

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * 1.169: 全局共享的 OkHttp 客户端与请求节流器。
 *
 * 起因（代码审查 H1/H2/H3）：
 *  · 原先有 6 个互不相干的 OkHttpClient（HupuApi / HupuMatchApi / HupuCommonApi /
 *    HupuAccount / HupuVoteApi 各一个，另有 HupuMsgBadge 与 ImageViewer 每次调用临时 new），
 *    每个实例自带连接池与 Dispatcher 线程池 → keep-alive / HTTP2 复用全部失效，内存与线程数被放大。
 *  · 同一段「读 lastAt → 判断 → sleep → 写回」节流代码被复制 10 份，且是「先检查后设置」，
 *    并发协程可能同时通过（恰好在防频控场景失效）。
 */
internal object HupuHttp {
    /** 常规请求：连接 15s / 读 20s（与历史各处配置保持一致） */
    val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * 上传专用：读/写 60s（大图 / 视频 PUT 慢）。
     * 用 `newBuilder()` 派生 → **与常规 client 共用连接池与 Dispatcher**，仅在超时上放宽。
     */
    val uploadClient: okhttp3.OkHttpClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
}

/**
 * 1.169: 请求节流器——原子占用时间片（CAS），替代原先复制 10 份且存在竞态的
 * 「先检查后设置 + Thread.sleep」写法。
 *
 * 语义与旧实现一致：**相邻两次 acquire() 之间至少间隔 minIntervalMs**，
 * 且同一实例的所有调用方共享同一个时间片（全局节流）。
 *
 * @param minIntervalMs 最小间隔（毫秒）
 * @param clock 时间源（可注入，便于单测）
 * @param waitFor 等待实现（可注入，便于单测；默认 delay，不阻塞线程）
 */
internal class RequestThrottle(
    private val minIntervalMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val waitFor: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) {
    private val lastAt = AtomicLong(0L)

    /** 挂起直到距上次成功占用已过 minIntervalMs；返回即代表本次已占用一个时间片。 */
    suspend fun acquire() {
        while (true) {
            val now = clock()
            val last = lastAt.get()
            val wait = minIntervalMs - (now - last)
            if (wait <= 0) {
                // CAS：并发下只有一个调用方能成功占用该时间片，其余重试
                if (lastAt.compareAndSet(last, now)) return
            } else {
                waitFor(wait)
            }
        }
    }
}