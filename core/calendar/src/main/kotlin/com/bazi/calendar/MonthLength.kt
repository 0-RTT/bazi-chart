package com.bazi.calendar

import com.tyme.lunar.LunarMonth
import com.tyme.lunar.LunarYear
import java.time.LocalDate

/**
 * 月天数查询。
 *
 * 滚轮选择器必须知道每个月到底有几天，否则用户能滚到「2 月 31 日」这种不存在的日期。
 * 公历走 java.time；农历必须走 tyme4kt 的朔望推算（大月 30、小月 29），
 * 不能用固定的 30 天糊过去——农历月长本身没有规律。
 */
object MonthLength {

    /** 公历某月天数。 */
    fun solarDayCount(year: Int, month: Int): Int =
        runCatching { LocalDate.of(year, month, 1).lengthOfMonth() }.getOrDefault(30)

    /** 农历某月天数。@param signedMonth 闰月传负数。 */
    fun lunarDayCount(year: Int, signedMonth: Int): Int =
        runCatching { LunarMonth(year, signedMonth).dayCount }.getOrDefault(29)

    /** 该农历年的闰月序号；0 表示无闰月。 */
    fun lunarLeapMonth(year: Int): Int =
        runCatching { LunarYear(year).leapMonth }.getOrDefault(0)

    fun dayCount(type: CalendarType, year: Int, month: Int, leapMonth: Boolean): Int =
        when (type) {
            CalendarType.SOLAR -> solarDayCount(year, month)
            CalendarType.LUNAR -> lunarDayCount(year, if (leapMonth) -month else month)
        }
}
