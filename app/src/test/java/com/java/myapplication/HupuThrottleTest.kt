package com.java.myapplication

import com.java.myapplication.data.RequestThrottle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.169（代码审查 H3）：请求节流器单测。
 * 通过注入 clock / waitFor 让时间完全可控，用例确定性、不依赖真实时间（无 flaky）。
 */
class HupuThrottleTest {

    @Test
    fun acquire_firstCallNoWait_thenEnforcesMinInterval() {
        val now = longArrayOf(10_000L)
        val waits = mutableListOf<Long>()
        val th = RequestThrottle(
            minIntervalMs = 1_000L,
            clock = { now[0] },
            waitFor = { ms -> waits.add(ms); now[0] += ms },
        )
        runBlocking {
            // 首次：lastAt=0，now=10000 → 距上次已远超间隔 → 不等待
            th.acquire()
            assertEquals(emptyList<Long>(), waits)

            // 仅过 100ms 再取 → 必须等到满 1000ms（即再等 900ms）
            now[0] = 10_100L
            th.acquire()
        }
        assertEquals(listOf(900L), waits)
        assertEquals(11_000L, now[0])
    }

    @Test
    fun acquire_zeroInterval_neverWaits() {
        val now = longArrayOf(5L)
        var waits = 0
        val th = RequestThrottle(0L, clock = { now[0] }, waitFor = { waits++ })
        runBlocking {
            th.acquire(); th.acquire(); th.acquire()
        }
        assertEquals(0, waits)
    }

    @Test
    fun acquire_frozenClock_terminates() {
        // minInterval=0 且时钟不动：CAS 路径必须能直接返回，不能死循环
        val th = RequestThrottle(0L, clock = { 12_345L }, waitFor = { })
        runBlocking {
            th.acquire(); th.acquire()
        }
    }
}