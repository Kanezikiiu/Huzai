package com.java.myapplication

import com.java.myapplication.ui.pages.formatPmTime
import com.java.myapplication.ui.pages.pmPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** 私信列表格式化（预览文本 / 时间分级）行为锁定。 */
class PmListFormatTest {

    // ---------- pmPreview ----------

    @Test
    fun preview_plainTextKept() {
        assertEquals("你好呀", pmPreview("你好呀"))
    }

    @Test
    fun preview_imageOnlyBecomesPlaceholder() {
        assertEquals("[图片]", pmPreview("<img src=\"https://x/a.jpg\"/>"))
    }

    @Test
    fun preview_textWithImageKeepsOrderAndText() {
        assertEquals("看图 漂亮", pmPreview("看图 <img src=\"a.jpg\"> 漂亮"))
    }

    @Test
    fun preview_stripsTagsAndDecodesEntities() {
        assertEquals("a & b", pmPreview("<p>a&nbsp;&amp;&nbsp;b</p>"))
    }

    @Test
    fun preview_blankIsEmpty() {
        assertEquals("", pmPreview(" "))
        assertEquals("", pmPreview(""))
    }

    // ---------- formatPmTime ----------

    @Test
    fun time_justNow() {
        val now = System.currentTimeMillis()
        assertEquals("刚刚", formatPmTime(now / 1000 - 30, now))
    }

    @Test
    fun time_minutesAgo() {
        val now = System.currentTimeMillis()
        assertEquals("5分钟前", formatPmTime(now / 1000 - 5 * 60, now))
    }

    @Test
    fun time_todayShowsClock() {
        val now = System.currentTimeMillis()
        val then = now - 3 * 3_600_000L
        val c = Calendar.getInstance().apply { timeInMillis = then }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        if (c.get(Calendar.YEAR) == n.get(Calendar.YEAR) &&
            c.get(Calendar.DAY_OF_YEAR) == n.get(Calendar.DAY_OF_YEAR)
        ) {
            val expected = String.format(
                "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY),
                c.get(Calendar.MINUTE),
            )
            assertEquals(expected, formatPmTime(then / 1000, now))
        }
    }

    @Test
    fun time_yesterday() {
        val now = System.currentTimeMillis()
        val y = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
        }
        assertEquals("昨天", formatPmTime(y.timeInMillis / 1000, now))
    }

    @Test
    fun time_withinWeekShowsWeekday() {
        val now = System.currentTimeMillis()
        val d = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -3)
        }
        val r = formatPmTime(d.timeInMillis / 1000, now)
        assertTrue("实际=$r", r.startsWith("周"))
    }

    @Test
    fun time_olderShowsMonthDay() {
        val now = System.currentTimeMillis()
        val d = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -30)
        }
        val r = formatPmTime(d.timeInMillis / 1000, now)
        assertTrue("实际=$r", r.endsWith("日"))
    }

    @Test
    fun time_previousYearShowsYear() {
        // 1.2xx（真机反馈）：非今年的私信要带年份
        val now = Calendar.getInstance().apply { set(2025, 5, 15, 12, 0, 0) }.timeInMillis
        val old = Calendar.getInstance().apply { set(2023, 4, 10, 12, 0, 0) }.timeInMillis
        val r = formatPmTime(old / 1000, now)
        assertEquals("2023年5月10日", r)
    }

    @Test
    fun time_zeroIsEmpty() {
        assertEquals("", formatPmTime(0L))
    }
}
