package com.bazi.calendar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 十二「节」的取法。
 *
 * 这里盯的是一个**看起来像 bug、其实不是**的现象：
 *
 * 流月格上写着「立春 2/4」。切到十年后的大运，日期往往还是「2/4」，
 * 于是很容易判定「数据没跟着年份走」。实际上节气时刻每年都要后移 5 小时 49 分，
 * 遇闰年再前移一天，净效果是——**时刻每年都不同，月日多数只差 0–1 天**。
 *
 * 所以断言分两层：
 * 1. 时刻必须逐年不同（证明数据确实在动）
 * 2. 月日可以相同（证明「看起来没变」是精度问题，不是数据停了）
 *
 * 界面上把节名与交节日一起显示（`立春 2/4`），就是为让人读得出这一层。
 */
class SolarTermsTest {

    @Test
    @DisplayName("十二节取齐，且顺序与十二支一一对应")
    fun twelveJie() {
        val jie = SolarTerms.jieOfYear(2026)
        assertEquals(12, jie.size, "2026 年应取到 12 个节")
        assertEquals(SolarTerms.JIE_NAMES, jie.map { it.name }, "十二节顺序不符")
        assertTrue(jie.all { it.isJie }, "取到的项都应是「节」，不是「气」")
    }

    @Test
    @DisplayName("立春落在 2 月 3–5 日，寅月起算于此时")
    fun lichunRange() {
        (2020..2050).forEach { year ->
            val lichun = SolarTerms.jieOfYear(year).first()
            assertEquals("立春", lichun.name, "$year 年首节应为立春")
            assertEquals(2, lichun.time.monthValue, "$year 年立春应在 2 月")
            assertTrue(
                lichun.time.dayOfMonth in 3..5,
                "$year 年立春应在 2 月 3–5 日，实得 ${lichun.time}"
            )
        }
    }

    @Test
    @DisplayName("丑月跨到次年 1 月——末日不在本公历年内是正常的")
    fun chouCrossesYear() {
        val jie = SolarTerms.jieOfYear(2026)
        val chou = jie.last()
        assertEquals("小寒", chou.name, "第 12 节应为小寒（丑月起）")
        assertEquals(2027, chou.time.year, "丑月的起点应落在次年 1 月")
        assertEquals(1, chou.time.monthValue)
    }

    @Test
    @DisplayName("交节时刻逐年不同：月日可能相同，时刻不会")
    fun timeDiffersEveryYear() {
        val years = listOf(2026, 2027, 2028, 2029)
        val lichun = years.map { SolarTerms.jieOfYear(it).first().time }
        assertEquals(
            years.size,
            lichun.toSet().size,
            "连续四年的立春时刻应互不相同，实得 $lichun"
        )

        // 十年一步的大运：月日常常不变，但时刻一定变了。
        val decadal = listOf(2026, 2036, 2046).map { SolarTerms.jieOfYear(it).first().time }
        assertEquals(3, decadal.toSet().size, "相隔十年的大运，立春时刻应互不相同，实得 $decadal")
    }

    @Test
    @DisplayName("每个节都晚于上一个节，且间隔在 29–32 天之间")
    fun monotonic() {
        val jie = SolarTerms.jieOfYear(2026)
        jie.zipWithNext().forEach { (a, b) ->
            val days = java.time.Duration.between(a.time, b.time).toDays()
            assertTrue(days in 29..32, "${a.name} → ${b.name} 间隔异常：$days 天")
        }
    }

    @Test
    @DisplayName("同一年取两次结果一致：节气表不能带隐藏状态")
    fun stable() {
        assertEquals(SolarTerms.jieOfYear(2030), SolarTerms.jieOfYear(2030))
    }

    @Test
    @DisplayName("节与气不同名：JIE_NAMES 里不得混入中气")
    fun noQi() {
        val qi = listOf(
            "雨水", "春分", "谷雨", "小满", "夏至", "大暑",
            "处暑", "秋分", "霜降", "小雪", "冬至", "大寒"
        )
        qi.forEach { name ->
            assertFalse(name in SolarTerms.JIE_NAMES, "十二节里混入了中气：$name")
        }
    }
}
