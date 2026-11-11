package com.java.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 1.357：视频静音是**进程级共享状态**，且「静音只管一次」。
 *
 * ## 演进（每条都是真机反馈逼出来的）
 *  · 1.354 把静音写在创建播放器那一处 → 点播放走的另一条路径漏了 → 不生效。
 *  · 1.356 改成每帧约束 `ui.muted` → 生效了，但 `muted` 是**每个播放器实例一份**：
 *      小窗取消静音 → 进全屏（新建一份，初值又是 true）→ 又静音；
 *      换一个视频（新实例）→ 又静音。
 *  · 1.357 把状态提到进程级 [com.java.myapplication.ui.player.VideoMuteState]：
 *      一次选择对**所有**视频 / 全屏 / 小窗生效，重启应用才回到默认。
 *
 * 下面把这条语义固化成测试。
 */
class VideoMuteLogicTest {

    /** 模拟进程级状态（与 VideoMuteState 同语义） */
    private class Session(var muted: Boolean)

    /** 新建一个播放器实例时的静音初值：读会话，不读设置项 */
    private fun newPlayerMuted(session: Session): Boolean = session.muted

    /** 每帧约束：返回应写入的音量；null = 不干涉 */
    private fun enforce(session: Session, currentVolume: Float): Float? =
        if (session.muted && currentVolume != 0f) 0f else null

    @Test
    fun `进程启动时按设置初始化一次`() {
        assertEquals(true, Session(muted = true).muted)     // 默认静音开
        assertEquals(false, Session(muted = false).muted)   // 用户关掉了开关
    }

    @Test
    fun `小窗取消静音后 全屏保持有声`() {
        val session = Session(muted = true)

        // 小窗：用户点静音钮取消
        session.muted = false

        // 进入全屏 → 新建实例，读的是同一个会话
        assertEquals(
            "全屏不应重新静音",
            false,
            newPlayerMuted(session),
        )
        assertEquals("不应对音量做任何约束", null, enforce(session, 1f))
    }

    @Test
    fun `取消静音后 换视频也不再静音`() {
        val session = Session(muted = true)
        session.muted = false   // 用户在第一个视频里取消了静音

        // 连续打开 3 个新视频
        repeat(3) {
            assertEquals(false, newPlayerMuted(session))
        }
    }

    @Test
    fun `从未取消静音时 每个视频仍按默认静音`() {
        val session = Session(muted = true)
        // 用户没动过 → 每个新视频依旧静音
        repeat(3) {
            assertEquals(true, newPlayerMuted(session))
            assertEquals(0f, enforce(session, 1f))
        }
    }

    @Test
    fun `静音时每帧把音量压回 0 已静音则不重复写`() {
        val session = Session(muted = true)
        assertEquals(0f, enforce(session, 1f))
        assertEquals(null, enforce(session, 0f))
    }

    @Test
    fun `点静音钮翻转的是全局状态`() {
        val session = Session(muted = false)
        session.muted = !session.muted   // 点一下
        assertEquals(true, session.muted)
        session.muted = !session.muted   // 再点一下
        assertEquals(false, session.muted)
    }
}