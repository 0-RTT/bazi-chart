package com.bazi.calendar

/**
 * 农历的中文写法。
 *
 * 单独抽出来，是因为同一套写法有两个使用方：命盘上的农历日期，与录入页滚轮的「日」列。
 * 两处各写一遍必然走形——滚轮里出「21」而命盘上出「廿一」，用户一眼就看得出。
 */
object LunarText {

    private val MONTHS = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )
    private val DIGITS = arrayOf("十", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    fun monthName(month: Int): String = MONTHS.getOrElse(month - 1) { "${month}月" }

    /**
     * 农历日名：初一……初九、初十、十一……十九、二十、廿一……廿九、三十。
     *
     * 注意「二十」不写「二十日」，「三十」同理；其余均不带「日」字。
     */
    fun dayName(day: Int): String = when {
        day == 10 -> "初十"
        day == 20 -> "二十"
        day == 30 -> "三十"
        day in 1..9 -> "初" + DIGITS[day]
        day in 11..19 -> "十" + DIGITS[day - 10]
        day in 21..29 -> "廿" + DIGITS[day - 20]
        else -> "${day}日"
    }
}
