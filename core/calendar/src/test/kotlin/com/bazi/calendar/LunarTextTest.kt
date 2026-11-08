package com.bazi.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 农历中文写法。
 *
 * 这套写法有两个使用方：命盘上的农历日期、录入页滚轮的「日」列。
 * 两处必须一致，否则滚轮里选「21」、盘上却显示「廿一」，用户会以为自己选错了。
 */
class LunarTextTest {

    @Test
    @DisplayName("初一至三十的中文写法")
    fun dayNames() {
        val expected = mapOf(
            1 to "初一", 2 to "初二", 5 to "初五", 9 to "初九",
            10 to "初十",
            11 to "十一", 15 to "十五", 19 to "十九",
            20 to "二十",
            21 to "廿一", 25 to "廿五", 29 to "廿九",
            30 to "三十"
        )
        expected.forEach { (day, text) ->
            assertEquals(text, LunarText.dayName(day), "第 $day 日")
        }
    }

    @Test
    @DisplayName("月名：正月起、腊月止，冬月不是十一月")
    fun monthNames() {
        assertEquals("正月", LunarText.monthName(1))
        assertEquals("四月", LunarText.monthName(4))
        assertEquals("冬月", LunarText.monthName(11))
        assertEquals("腊月", LunarText.monthName(12))
    }
}
