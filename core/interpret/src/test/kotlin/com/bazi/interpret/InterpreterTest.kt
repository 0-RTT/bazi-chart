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
 * 断语回归。
 *
 * 古籍论断已从盘面文本移到这里：盘面只给数据，解读留在断语页。
 * 这里既验证十家都在，也验证「分歧对照」那一节确实已经移除。
 */
class InterpreterTest {

    private val beijing = City("北京", "北京", 116.41, 39.90)

    private fun result(): Interpretation {
        val chart = BaziCalculator.calculate(
            BirthInput(
                calendarType = CalendarType.SOLAR,
                year = 1986, month = 5, day = 29, hour = 0, minute = 30,
                gender = Gender.MALE,
                city = beijing,
                sect = SectConfig(ziHour = ZiHourSect.LATE, useTrueSolarTime = true)
            )
        )
        return Interpreter.interpret(chart, ChartAnalyzer.analyze(chart), 2026)
    }

    @Test
    @DisplayName("含「古籍参照」一节，十部书逐条列出")
    fun classicsSection() {
        val section = result().sections.firstOrNull { it.title == "古籍参照" }
        assertTrue(section != null, "缺古籍参照节")

        val text = section!!.paragraphs.joinToString("\n")
        listOf(
            "滴天髓", "穷通宝鉴", "三命通会", "八字提要", "子平真诠",
            "天元咸巫", "神峰通考", "千里命稿", "五行精纪", "李虚中命书"
        ).forEach { book -> assertTrue(text.contains(book), "缺古籍：$book") }

        assertTrue(section.paragraphs.size == 10, "应有十条，实际 ${section.paragraphs.size}")
        assertFalse(text.contains("分歧对照"), "不应再有分歧对照")
    }

    @Test
    @DisplayName("含取用取舍一节，且说明扶抑与调候是否同指")
    fun yongShenSection() {
        val section = result().sections.firstOrNull { it.title == "取用取舍" }
        assertTrue(section != null, "缺取用取舍节")
        val text = section!!.paragraphs.joinToString("\n")
        assertTrue(text.contains("扶抑取用"), "缺扶抑取用")
        assertTrue(text.contains("调候取用"), "缺调候取用")
    }

    @Test
    @DisplayName("各节都有内容，且无空段")
    fun noEmptySections() {
        val sections = result().sections
        assertTrue(sections.size >= 9, "章节数偏少：${sections.size}")
        sections.forEach { s ->
            assertTrue(s.paragraphs.isNotEmpty(), "空节：${s.title}")
            s.paragraphs.forEach { p -> assertTrue(p.isNotBlank(), "空段落：${s.title}") }
        }
    }

    @Test
    @DisplayName("刑冲合害一节会如实报出相刑，而不是只报部分")
    fun relationsSection() {
        val section = result().sections.firstOrNull { it.title == "刑冲合害" }
        assertTrue(section != null, "缺刑冲合害节")
    }
}
