package com.bazi.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 滚轮选择器依赖的月天数。
 *
 * 这里的断言都是硬事实（公历闰年、农历大小月、闰月年份），
 * 一旦上游历法库行为变化，测试会先报警，而不是让用户在滚轮上选到不存在的日期。
 */
class MonthLengthTest {

    @Test
    @DisplayName("公历月天数遵循闰年规则")
    fun solar() {
        assertEquals(29, MonthLength.solarDayCount(2024, 2))
        assertEquals(28, MonthLength.solarDayCount(2023, 2))
        assertEquals(30, MonthLength.solarDayCount(2024, 4))
        assertEquals(31, MonthLength.solarDayCount(2024, 1))
        assertEquals(30, MonthLength.solarDayCount(2024, 6))
    }

    @Test
    @DisplayName("农历月只有 29 或 30 天")
    fun lunarDayCount() {
        (1..12).forEach { month ->
            val days = MonthLength.lunarDayCount(2024, month)
            assertTrue(days == 29 || days == 30, "2024 年农历 $month 月天数异常：$days")
        }
    }

    @Test
    @DisplayName("2020 年闰四月，2021 年无闰月")
    fun leapMonth() {
        assertEquals(4, MonthLength.lunarLeapMonth(2020))
        assertEquals(0, MonthLength.lunarLeapMonth(2021))
    }

    @Test
    @DisplayName("闰月为负月号时同样可取天数")
    fun leapMonthDays() {
        val days = MonthLength.lunarDayCount(2020, -4)
        assertTrue(days == 29 || days == 30, "闰四月天数异常：$days")
    }

    @Test
    @DisplayName("按历法类型分发")
    fun dispatch() {
        assertEquals(29, MonthLength.dayCount(CalendarType.SOLAR, 2024, 2, false))
        assertTrue(MonthLength.dayCount(CalendarType.LUNAR, 2024, 2, false) in 29..30)
    }
}
