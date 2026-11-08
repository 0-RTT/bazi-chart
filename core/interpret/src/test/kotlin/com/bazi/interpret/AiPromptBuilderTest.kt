package com.bazi.interpret

import com.bazi.analysis.ChartAnalyzer
import com.bazi.calendar.BirthInput
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.Gender
import com.bazi.calendar.SectConfig
import com.bazi.calendar.ZiHourSect
import com.bazi.engine.BaziCalculator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * AI 指令文本回归。
 *
 * 这段文本是要整段交给大模型的，最怕两件事：
 * 1. 生成时抛异常（格式化占位符与参数不匹配）；
 * 2. 命盘关键信息漏字段，模型拿到残缺数据后自行脑补。
 * 所以这里既跑通构建，也逐项校验必须出现的内容。
 */
class AiPromptBuilderTest {

    private val beijing = City("北京", "北京", 116.41, 39.90)

    private fun prompt(referenceYear: Int = 2026): String {
        val chart = BaziCalculator.calculate(
            BirthInput(
                calendarType = CalendarType.SOLAR,
                year = 1986, month = 5, day = 29, hour = 0, minute = 30,
                gender = Gender.MALE,
                city = beijing,
                sect = SectConfig(ziHour = ZiHourSect.LATE, useTrueSolarTime = true)
            )
        )
        return AiPromptBuilder.build(chart, ChartAnalyzer.analyze(chart), referenceYear)
    }

    @Test
    @DisplayName("指令文本可正常生成且不短于 1000 字")
    fun generates() {
        val text = prompt()
        assertTrue(text.length > 1000, "实际长度 ${text.length}")
    }

    @Test
    @DisplayName("包含四柱、日主与十神")
    fun containsCoreChart() {
        val text = prompt()
        assertTrue(text.contains("丙寅"), "缺年柱")
        assertTrue(text.contains("癸酉"), "缺日柱")
        assertTrue(text.contains("癸水"), "缺日主")
        assertTrue(text.contains("日主"), "缺十神占位")
        assertTrue(text.contains("正财"), "缺十神")
    }

    @Test
    @DisplayName("包含旺衰、格局、调候、神煞与大运")
    fun containsAnalysisSections() {
        val text = prompt()
        listOf("五行力量", "格局与调候", "神煞", "刑冲合害", "大运与流年").forEach { title ->
            assertTrue(text.contains(title), "缺少章节：$title")
        }
        assertTrue(text.contains("帮身之力"), "缺旺衰结论")
        assertTrue(text.contains("起运"), "缺起运信息")
    }

    @Test
    @DisplayName("包含分析任务清单与输出约束")
    fun containsTasks() {
        val text = prompt()
        assertTrue(text.contains("请完成以下分析"), "缺任务章节")
        assertTrue(text.contains("输出要求"), "缺输出约束")
        assertTrue(text.contains("日主旺衰复核"), "缺第一项任务")
        assertFalse(text.contains("\${"), "存在未替换的模板占位符")
        assertFalse(text.contains("null"), "存在 null 值")
    }

    @Test
    @DisplayName("流年表覆盖参考年之后的六年")
    fun containsUpcomingYears() {
        val text = prompt(2026)
        (2027..2032).forEach { year ->
            assertTrue(text.contains("| $year |"), "缺 $year 年流年行")
        }
    }
}
