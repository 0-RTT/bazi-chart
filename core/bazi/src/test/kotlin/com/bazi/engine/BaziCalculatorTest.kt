package com.bazi.engine

import com.bazi.calendar.BirthInput
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.Gender
import com.bazi.calendar.SectConfig
import com.bazi.calendar.ZiHourSect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 排盘回归测试。
 *
 * 基准命例取自 6tail/lunar 官方文档示例输出：
 * 「一九八六年四月廿一 丙寅年 癸巳月 癸酉日 子时 纳音[炉中火 长流水 剑锋金 桑柘木]」
 *
 * 这是目前唯一一个我们能拿到"外部权威输出逐字对照"的样本，
 * 因此把它作为四柱与纳音的锚点。
 */
class BaziCalculatorTest {

    private val beijing = City("北京", "北京", 116.41, 39.90)

    private fun solarCase(): BaziChart = BaziCalculator.calculate(
        BirthInput(
            calendarType = CalendarType.SOLAR,
            year = 1986, month = 5, day = 29, hour = 0, minute = 30,
            gender = Gender.MALE,
            city = beijing,
            sect = SectConfig(ziHour = ZiHourSect.LATE, useTrueSolarTime = true)
        )
    )

    @Test
    @DisplayName("四柱与官方示例一致")
    fun pillars() {
        assertEquals("丙寅 癸巳 癸酉 壬子", solarCase().ganZhiText)
    }

    @Test
    @DisplayName("纳音与官方示例一致")
    fun naYin() {
        val chart = solarCase()
        assertEquals("炉中火", chart.yearPillar.naYin)
        assertEquals("长流水", chart.monthPillar.naYin)
        assertEquals("剑锋金", chart.dayPillar.naYin)
        assertEquals("桑柘木", chart.hourPillar.naYin)
    }

    @Test
    @DisplayName("十神以日干为准")
    fun tenGod() {
        val chart = solarCase()
        assertEquals(null, chart.dayPillar.ganTenGod)          // 日主本身无十神
        assertEquals(TenGod.ZHENG_CAI, chart.yearPillar.ganTenGod)  // 癸见丙 → 正财
        assertEquals(TenGod.BI_JIAN, chart.monthPillar.ganTenGod)   // 癸见癸 → 比肩
        assertEquals(TenGod.JIE_CAI, chart.hourPillar.ganTenGod)    // 癸见壬 → 劫财
    }

    @Test
    @DisplayName("旬空为戌亥")
    fun voidBranches() {
        assertEquals(listOf(DiZhi.XU, DiZhi.HAI), solarCase().voidBranches)
    }

    @Test
    @DisplayName("阳年男命顺排，首运为月柱后一位")
    fun luckDirection() {
        val chart = solarCase()
        assertTrue(chart.startLuck.forward)
        assertEquals("甲午", chart.luckPillars.first().ganZhi)
        assertEquals(10, chart.luckPillars.size)
        // 大运首尾相接，不得断档
        chart.luckPillars.zipWithNext().forEach { (a, b) ->
            assertEquals(a.endYear + 1, b.startYear)
        }
    }

    @Test
    @DisplayName("流年干支以立春为界，甲子循环正确")
    fun annualCycle() {
        val chart = solarCase()
        assertEquals("丙寅", chart.annuals.first { it.year == 1986 }.ganZhi)
        // 2044 亦为甲子年，且落在 1986..2086 的流年区间内
        assertEquals("甲子", chart.annuals.first { it.year == 2044 }.ganZhi)
        assertEquals("甲辰", chart.annuals.first { it.year == 2024 }.ganZhi)
    }

    @Test
    @DisplayName("农历输入与公历输入应得同一命盘")
    fun lunarEqualsSolar() {
        val lunar = BaziCalculator.calculate(
            BirthInput(
                calendarType = CalendarType.LUNAR,
                year = 1986, month = 4, day = 21, hour = 0, minute = 30,
                gender = Gender.MALE,
                city = beijing,
                sect = SectConfig(ziHour = ZiHourSect.LATE, useTrueSolarTime = true)
            )
        )
        assertEquals(solarCase().ganZhiText, lunar.ganZhiText)
    }

    @Test
    @DisplayName("真太阳时校正会改变极端经度下的时柱")
    fun trueSolarTimeChangesHourPillar() {
        val urumqi = City("乌鲁木齐", "新疆", 87.62, 43.79)
        val withCorrection = BaziCalculator.calculate(
            BirthInput(
                CalendarType.SOLAR, 2024, 2, 4, 12, 0,
                Gender.MALE, urumqi,
                SectConfig(useTrueSolarTime = true)
            )
        )
        val withoutCorrection = BaziCalculator.calculate(
            BirthInput(
                CalendarType.SOLAR, 2024, 2, 4, 12, 0,
                Gender.MALE, urumqi,
                SectConfig(useTrueSolarTime = false)
            )
        )
        assertEquals("午", withoutCorrection.hourPillar.zhi.cn)
        assertEquals("巳", withCorrection.hourPillar.zhi.cn)
    }
}
