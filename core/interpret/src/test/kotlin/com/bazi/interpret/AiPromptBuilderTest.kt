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
 * 盘面信息文本回归。
 *
 * 这段文本要整段交给大模型，最怕三件事：
 * 1. 生成时抛异常（格式化占位符与参数不匹配）；
 * 2. 关键字段遗漏，模型拿到残缺数据后自行脑补；
 * 3. 混进任务指令或规避性话语——按要求本段只陈述盘面，不指挥读者。
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
    @DisplayName("文本可正常生成且不短于 4000 字")
    fun generates() {
        val text = prompt()
        assertTrue(text.length > 4000, "实际长度 ${text.length}")
        assertFalse(text.contains("\${"), "存在未替换的模板占位符")
        assertFalse(text.contains("null"), "存在 null 值")
        assertFalse(text.contains("NaN"), "存在 NaN")
    }

    @Test
    @DisplayName("九节齐全")
    fun sectionsPresent() {
        val text = prompt()
        listOf(
            "## 一、出生", "## 二、四柱", "## 三、四柱神煞",
            "## 四、大运", "## 五、大运神煞", "## 六、流年",
            "## 七、流年神煞", "## 八、流月", "## 九、古籍分歧"
        ).forEach { title -> assertTrue(text.contains(title), "缺少章节：$title") }
    }

    @Test
    @DisplayName("四柱、日主、藏干、纳音、空亡均在表中")
    fun chartFields() {
        val text = prompt()
        assertTrue(text.contains("丙寅"), "缺年柱")
        assertTrue(text.contains("癸酉"), "缺日柱")
        assertTrue(text.contains("癸水"), "缺日主")
        listOf("藏干", "藏干十神", "纳音", "空亡", "星运").forEach {
            assertTrue(text.contains(it), "缺字段：$it")
        }
    }

    @Test
    @DisplayName("大运与流年都带纳音、空亡、神煞列")
    fun luckAndAnnualColumns() {
        val text = prompt()
        assertTrue(text.contains("| 大运 | 虚岁 | 公历年 | 干十神 | 支十神 | 星运 | 纳音 | 空亡 | 神煞 |"), "大运表头不符")
        assertTrue(text.contains("| 年份 | 虚岁 | 干支 | 十神 | 纳音 | 空亡 | 神煞 | 所属大运 |"), "流年表头不符")
    }

    @Test
    @DisplayName("流月为参考年十二个月建，寅月起、丑月终")
    fun monthlies() {
        val text = prompt(2026)
        assertTrue(text.contains("## 八、流月（2026 年）"), "缺流月章节或年份不符")
        assertTrue(text.contains("正月寅月"), "缺正月")
        assertTrue(text.contains("腊月丑月"), "缺腊月")
        assertTrue(text.contains("2026-02-0"), "缺立春交节时刻")
    }

    @Test
    @DisplayName("十部古籍逐一列出，且给出分歧对照")
    fun classics() {
        val text = prompt()
        listOf(
            "滴天髓", "穷通宝鉴", "三命通会", "八字提要", "子平真诠",
            "天元咸巫", "神峰通考", "千里命稿", "五行精纪", "李虚中命书"
        ).forEach { book -> assertTrue(text.contains(book), "缺古籍：$book") }
        assertTrue(text.contains("### 分歧对照"), "缺分歧对照小节")
        assertTrue(text.contains("命主是谁"), "缺主星分歧轴")
        assertTrue(text.contains("用神从哪里取"), "缺取用分歧轴")
        assertTrue(text.contains("纳音用不用"), "缺纳音分歧轴")
    }

    @Test
    @DisplayName("不含任务指令与规避性话语")
    fun noTasksOrDisclaimers() {
        val text = prompt()
        listOf(
            "请完成以下分析", "输出要求", "请你", "请分析",
            "仅供参考", "不构成", "不作为", "风险提示",
            "仅供传统文化研究", "不可作为"
        ).forEach { banned ->
            assertFalse(text.contains(banned), "出现了不应有的措辞：$banned")
        }
    }
}
