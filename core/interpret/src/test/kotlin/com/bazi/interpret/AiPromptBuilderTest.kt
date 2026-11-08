package com.bazi.interpret

import com.bazi.calendar.BirthInput
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.Gender
import com.bazi.calendar.SectConfig
import com.bazi.calendar.ZiHourSect
import com.bazi.engine.BaziCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 盘面文本回归。
 *
 * 这段文本要整段交给大模型，最怕四件事：
 * 1. 生成时抛异常（格式化占位符与参数不匹配）；
 * 2. 关键字段遗漏，模型拿到残缺数据后自行脑补；
 * 3. 混进任务指令或规避性话语——本段只陈述盘面，不指挥读者；
 * 4. 混进站不住的推论。神煞查法各家不一，旺衰格局口径更多，
 *    这些一旦写进文本，就等于把争议当成事实交给模型。
 */
class AiPromptBuilderTest {

    private val beijing = City("北京", "北京", 116.41, 39.90)

    private fun chart() = BaziCalculator.calculate(
        BirthInput(
            calendarType = CalendarType.SOLAR,
            year = 1986, month = 5, day = 29, hour = 0, minute = 30,
            gender = Gender.MALE,
            city = beijing,
            sect = SectConfig(ziHour = ZiHourSect.LATE, useTrueSolarTime = true)
        )
    )

    private fun prompt(referenceYear: Int = 2026): String =
        AiPromptBuilder.build(chart(), referenceYear)

    @Test
    @DisplayName("四节齐全：排盘、大运、流年、流月")
    fun sections() {
        val text = prompt()
        listOf("## 一、排盘", "## 二、大运", "## 三、流年", "## 四、流月").forEach { title ->
            assertTrue(text.contains(title), "缺少章节：$title")
        }
    }

    @Test
    @DisplayName("只收基本信息：神煞、旺衰、格局、刑冲合害一概不进文本")
    fun noInference() {
        val text = prompt()
        listOf("神煞", "旺衰", "格局", "调候", "刑冲合害", "古籍", "喜用", "用神").forEach { word ->
            assertFalse(text.contains(word), "文本不应包含推论项：$word")
        }
    }

    @Test
    @DisplayName("文本可正常生成，四张表齐全且无空值")
    fun generates() {
        val text = prompt()
        val rows = text.lines().count { it.startsWith("| ") }
        // 四柱 10 行 + 大运 12 行 + 流年 23 行 + 流月 14 行，留出余量取 45
        assertTrue(rows >= 45, "表格行数偏少：$rows")
        assertFalse(text.contains("\${"), "存在未替换的模板占位符")
        assertFalse(text.contains("null"), "存在 null 值")
        assertFalse(text.contains("NaN"), "存在 NaN")
    }

    @Test
    @DisplayName("四柱表含主星、藏干、副星、纳音、星运、自坐、空亡与落空")
    fun pillarRows() {
        val text = prompt()
        listOf(
            "| 主星 |", "| 天干 |", "| 地支 |", "| 藏干 |", "| 副星 |",
            "| 纳音 |", "| 星运 |", "| 自坐 |", "| 空亡 |", "| 落空 |"
        ).forEach { row -> assertTrue(text.contains(row), "四柱表缺少行：$row") }
    }

    @Test
    @DisplayName("流月为当年十二个月建")
    fun twelveMonths() {
        val text = prompt()
        val monthRows = text.lines().count { it.startsWith("| ") && it.contains("月 |") }
        assertEquals(12, monthRows, "流月应为十二行，实得 $monthRows")
    }

    @Test
    @DisplayName("当前大运与今年各只有一处标记")
    fun markers() {
        val text = prompt()
        assertEquals(1, text.lines().count { it.contains("←当前") }, "当前大运应恰有一处标记")
        assertEquals(1, text.lines().count { it.contains("←今年") }, "今年应恰有一处标记")
    }
}
