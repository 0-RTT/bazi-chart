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
    @DisplayName("任务段是用户给定的一段话：一整行，不被折行切断")
    fun taskIsOneParagraph() {
        val task = prompt().substringBefore("以下为盘面数据。")
        // 这段话要整段复制出去，折行会让各家模型当成列表或断句。
        // 断言「同一行既以『请你以』开头、又以『风险点。』收尾」，钉住不折行。
        assertTrue(
            task.lines().any {
                it.startsWith("请你以精通中国传统命理") && it.endsWith("需规避的风险点。")
            },
            "任务段被折成了多行，或首尾措辞与用户给定不一致"
        )
    }

    @Test
    @DisplayName("任务段给了推演次序：日主强弱 → 十神格局 → 用神喜忌，次序不可乱")
    fun taskOrder() {
        val task = prompt().substringBefore("以下为盘面数据。")
        listOf("日主强弱与五行旺衰", "十神格局", "用神喜忌").forEach {
            assertTrue(task.contains(it), "任务段缺推演环节：$it")
        }
        assertTrue(
            task.indexOf("日主强弱与五行旺衰") < task.indexOf("十神格局"),
            "次序错：日主强弱应在十神格局之前"
        )
        assertTrue(
            task.indexOf("十神格局") < task.indexOf("用神喜忌"),
            "次序错：十神格局应在用神喜忌之前"
        )
    }

    @Test
    @DisplayName("任务段六项维度齐全，且按用户给的次序排列")
    fun taskDimensions() {
        val task = prompt().substringBefore("以下为盘面数据。")
        val dims = listOf("健康", "学业", "事业", "财运", "人际关系", "婚姻与感情")
        dims.forEach { assertTrue(task.contains(it), "任务段缺维度：$it") }
        // 逐对比较下标，次序错一处就报
        dims.zipWithNext().forEach { (a, b) ->
            assertTrue(task.indexOf(a) < task.indexOf(b), "维度次序错：$a 应在 $b 之前")
        }
    }

    @Test
    @DisplayName("任务段要求每项交代三样：吉凶倾向、时间范围、影响程度高中低")
    fun taskPerItem() {
        val task = prompt().substringBefore("以下为盘面数据。")
        listOf("吉凶倾向", "关键事件", "时间范围", "大运、流年或年龄段", "影响程度（高、中、低）")
            .forEach { assertTrue(task.contains(it), "任务段缺逐项交代要素：$it") }
    }

    @Test
    @DisplayName("任务段末尾落到方位、行业、行事时机与风险点")
    fun taskAdvice() {
        val task = prompt().substringBefore("以下为盘面数据。")
        listOf("性格与命局特点", "改善建议与应对策略", "方位", "行业", "行事时机", "风险点")
            .forEach { assertTrue(task.contains(it), "任务段缺建议要素：$it") }
    }

    @Test
    @DisplayName("九节清单已撤下：任务段不再有分节标题与分档输出")
    fun noSectionList() {
        val task = prompt().substringBefore("以下为盘面数据。")
        listOf(
            "## 一、先校盘", "## 二、判读主链", "## 三、判读纪律", "## 四、分析维度",
            "## 五、关键事件", "## 六、建议要能执行", "## 七、流月只在落到月份时用",
            "## 八、不确定就说不确定", "## 九、输出分两段",
            "第一段", "第二段", "要不要展开", "置信度", "多路闭环"
        ).forEach { assertFalse(task.contains(it), "任务段仍残留旧指令：$it") }
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
    @DisplayName("指令与数据之间只隔一条分隔线与一句过渡，不夹其他指令")
    fun taskThenData() {
        val text = prompt()
        val task = text.substringBefore("以下为盘面数据。")
        // 用户要求「不要掺杂其他内容」：指令就这一段话，后面直接接盘面。
        // 分隔线保留，否则表头会紧贴上句被当成行文续写。
        assertTrue(task.trimEnd().endsWith("---"), "指令与数据之间缺分隔线")
        val between = task.substringAfter("需规避的风险点。").trim()
        assertEquals("---", between, "指令与盘面之间夹了额外内容：$between")
        assertTrue(text.substringAfter("以下为盘面数据。").contains("## 排盘"), "盘面未紧随指令")
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
