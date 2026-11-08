package com.bazi.interpret

import com.bazi.calendar.BirthInput
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.Gender
import com.bazi.calendar.SectConfig
import com.bazi.calendar.ZiHourSect
import com.bazi.engine.BaziCalculator
import com.bazi.engine.PillarPosition
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
        AiPromptBuilder.build(chart(), referenceYear, shenSha = shenSha())

    /** 神煞由 app 层传入（core:interpret 不依赖 core:analysis）。 */
    private fun shenSha(): Map<PillarPosition, List<String>> = mapOf(
        PillarPosition.YEAR to listOf("天乙贵人", "空亡"),
        PillarPosition.MONTH to listOf("文昌"),
        PillarPosition.DAY to listOf("羊刃"),
        PillarPosition.HOUR to emptyList()
    )

    @Test
    @DisplayName("任务段九节齐全：校盘、主链、纪律、维度、事件、建议、流月、不确定、分档")
    fun taskSections() {
        val text = prompt()
        listOf(
            "# 八字命盘分析",
            "## 一、先校盘",
            "## 二、判读主链",
            "## 三、判读纪律",
            "## 四、分析维度",
            "## 五、关键事件",
            "## 六、建议要能执行",
            "## 七、流月只在落到月份时用",
            "## 八、不确定就说不确定",
            "## 九、输出分两段"
        ).forEach { title -> assertTrue(text.contains(title), "缺少任务章节：$title") }
    }

    @Test
    @DisplayName("任务段给出六步主链：月令取格到岁运引动，一步不少")
    fun pipeline() {
        val text = prompt()
        listOf("月令取格", "论成败救应", "取用神", "看刑冲合害", "落宫位", "岁运引动").forEach {
            assertTrue(text.contains(it), "主链缺少一步：$it")
        }
        // 主链要引用古籍次序，不是凭空排的
        assertTrue(text.contains("先看提纲为重"), "主链未引《渊海子平》次序")
        assertTrue(text.contains("八字用神，专求月令"), "主链未引《子平真诠》")
    }

    @Test
    @DisplayName("任务段点明八维度、事件四项、判读纪律与分档输出")
    fun taskRequirements() {
        val text = prompt()
        listOf("性格", "事业", "财运", "婚恋", "子女", "六亲", "健康", "学业").forEach { dim ->
            assertTrue(text.contains(dim), "任务段缺少维度：$dim")
        }
        listOf("时间", "吉凶", "程度", "影响", "决定性").forEach {
            assertTrue(text.contains(it), "任务段缺少事件要素：$it")
        }
        // 这几条是被真实输出逼出来的纪律，缺一条模型就会往该方向跑偏
        listOf("置信度", "多路闭环", "技术有利不等于必然发生", "不用现实结果反推",
               "术语要落地", "神煞只作旁证", "不要复述盘面").forEach {
            assertTrue(text.contains(it), "任务段缺纪律：$it")
        }
        // 分档：先给一段，末尾问要不要展开
        assertTrue(text.contains("第一段"), "任务段缺分档输出的第一段要求")
        assertTrue(text.contains("要不要展开"), "任务段未要求末尾征询是否展开")
    }

    @Test
    @DisplayName("结构因子表：只给可复核的事实，不给强弱、格局、用神")
    fun factors() {
        val text = AiPromptBuilder.build(
            chart(), 2026, shenSha = shenSha(), factors = sampleFactors()
        )
        assertTrue(text.contains("## 结构因子"), "缺少结构因子一节")
        listOf("月令", "日主在月令", "得令", "得地", "天干帮身", "帮身力量占比",
               "五行力量", "四柱未见", "天干关系", "地支关系", "月令气候").forEach { row ->
            assertTrue(text.contains("| " + row + " |"), "结构因子缺行：$row")
        }
        // 因子是事实，不能顺手把结论也写了
        val factorBlock = text.substringAfter("## 结构因子").substringBefore("## 大运")
        listOf("身弱", "身强", "正官格", "用神为", "喜用").forEach { word ->
            assertFalse(factorBlock.contains(word), "结构因子混入了结论：$word")
        }
        // 五行力量要按占比从高到低排，且带百分号
        assertTrue(text.contains("% ｜"), "五行力量未按占比并列")
    }

    @Test
    @DisplayName("不给结构因子时该节整段消失，不留空壳")
    fun factorsOptional() {
        val text = prompt()
        assertFalse(text.contains("## 结构因子"), "未传因子时不应出现该节")
    }

    @Test
    @DisplayName("按用户决定：不加免责声明")
    fun noDisclaimer() {
        val task = prompt().substringBefore("以下为盘面数据。")
        listOf("免责", "仅供", "娱乐参考", "非专业决策", "责任自负", "不构成").forEach {
            assertFalse(task.contains(it), "任务段不应出现免责措辞：$it")
        }
    }

    @Test
    @DisplayName("维度与建议都归入第二段，不与第一段的篇幅要求打架")
    fun stagedOutput() {
        val task = prompt().substringBefore("以下为盘面数据。")
        assertTrue(
            task.contains("八项必答与选答都属于第九节的**第二段**"),
            "未说明必答维度归第二段"
        )
        assertTrue(
            task.contains("这一节属于第二段，第一段只给结论与时间轴"),
            "未说明建议归第二段"
        )
        assertTrue(task.contains("不要在没被要求时把十项一次铺开"), "缺第一段的篇幅约束")
    }

    /** 因子由 app 层从分析层取出后传入。 */
    private fun sampleFactors() = StructureFactors(
        monthCommand = com.bazi.engine.DiZhi.SI,
        monthDetail = "本气丙火，透干丙；藏丙戊庚",
        dayMasterPhase = "临官",
        commandSupported = true,
        groundSupported = false,
        supportStems = 2,
        supportRatio = 0.62,
        elementShares = listOf("火" to 0.42, "木" to 0.21, "土" to 0.18, "水" to 0.12, "金" to 0.07),
        missing = listOf("金"),
        ganRelations = listOf("甲己五合（年干—月干，化土）"),
        zhiRelations = listOf("子午相冲")
    )

    @Test
    @DisplayName("盘面数据四节齐全：排盘、大运、流年、流月")
    fun sections() {
        val text = prompt()
        listOf("## 排盘", "## 大运", "## 流年", "## 流月").forEach { title ->
            assertTrue(text.contains(title), "缺少章节：$title")
        }
    }

    @Test
    @DisplayName("数据段只陈述事实：旺衰、格局、调候、喜用一概不进")
    fun noInferenceInData() {
        val text = prompt()
        val data = text.substringAfter("以下为盘面数据。")
        listOf("旺衰", "格局", "调候", "古籍", "喜用", "用神").forEach { word ->
            assertFalse(data.contains(word), "数据段不应包含推论项：$word")
        }
        // 神煞作为一行事实可以进数据段，但重复的空亡不列
        assertTrue(data.contains("| 神煞 |"), "数据段缺少神煞行")
        assertFalse(data.contains("天乙贵人、空亡"), "神煞行不应重复空亡")
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
            "| 纳音 |", "| 星运 |", "| 自坐 |", "| 空亡 |", "| 落空 |", "| 神煞 |"
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
