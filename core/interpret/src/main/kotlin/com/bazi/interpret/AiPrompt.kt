package com.bazi.interpret

import com.bazi.calendar.Gender
import com.bazi.engine.AnnualFortune
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
import com.bazi.engine.LuckPillar
import com.bazi.engine.MonthlyFortune
import com.bazi.engine.Pillar
import com.bazi.engine.PillarPosition
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 盘面文本生成器。
 *
 * 全文由两段构成：**任务**（要模型做什么）与**盘面数据**（排盘、大运、流年、流月）。
 *
 * 为什么要有任务段：这份文本的唯一用途就是粘给大模型。早先只给数据不给指令，
 * 是想让使用者自己决定怎么问；实际用下来，每次都要重打一遍维度与输出要求，
 * 十次有八次会漏掉「关键事件要给时间范围」。把要求写死在生成端，盘与问就不会走散。
 *
 * 盘面数据仍然只陈述事实：四柱、藏干、副星、纳音、空亡、神煞（按柱列名）、
 * 大运、流年、流月。旺衰、格局、调候、喜用这类**结论**一概不给——
 * 那是模型该算的，先给了它，它就只会顺着抄。
 *
 * 纯 Kotlin，无 Android 依赖，可在 JVM 上直接断言文本内容。
 */
object AiPromptBuilder {

    /** 流年的展示窗口：参考年前 5 年至后 15 年。 */
    private const val ANNUAL_BACK = 5
    private const val ANNUAL_FORWARD = 15

    /**
     * @param shenSha 各宫位的神煞名。core:interpret 刻意不依赖 core:analysis，
     *               故由调用方（app 层）传字符串进来，本层只负责排版。
     */
    fun build(
        chart: BaziChart,
        referenceYear: Int = LocalDate.now().year,
        shenSha: Map<PillarPosition, List<String>> = emptyMap()
    ): String = buildString {
        appendTask()
        appendBasic(chart)
        appendPillars(chart, shenSha)
        appendLuck(chart, referenceYear)
        appendAnnuals(chart, referenceYear)
        appendMonthlies(chart, referenceYear)
    }

    // ------------------------------------------------------------ 〇、任务

    /**
     * 任务段。
     *
     * 每条都是被「问出来才发现少了」逼出来的：不写「请分析」，写清
     * **怎么分析**（几种流派、冲突怎么办）、**分析什么**（七个维度，各点到哪一层）、
     * **事件怎么给**（时间范围、吉凶、影响程度四级）、**结论怎么落盘**（依据）、
     * **建议怎么给**（三档可执行）。模型缺的不是能力，是边界。
     */
    private fun StringBuilder.appendTask() {
        appendLine("# 八字命盘分析")
        appendLine()
        appendLine("你是一位精通中国传统命理的分析师：子平格局、旺衰扶抑、调候用神、盲派取象、")
        appendLine("神煞参断都有涉猎，能说清各派分歧在哪里、自己又为什么取这一派。")
        appendLine("下面是一份完整的八字盘面，请据此出分析。")
        appendLine()

        appendLine("## 一、先校盘")
        appendLine()
        appendLine("先按你自己的算法核对四柱、藏干、副星、纳音、旬空、大运起运。")
        appendLine("凡与下表不一致的，先指出差异和可能原因（换日流派、真太阳时、节气交接），")
        appendLine("然后**按下表继续分析**，不要擅自改盘。")
        appendLine()

        appendLine("## 二、分析方法：多派交叉，不许和稀泥")
        appendLine()
        appendLine("- 子平格局派：月令取格，看成格还是破格，格神有没有被伤")
        appendLine("- 旺衰扶抑派：日主强弱、印比与财官食伤的消长，定喜忌")
        appendLine("- 调候派（《穷通宝鉴》）：寒暖燥湿，冬夏之命先看调候有无")
        appendLine("- 盲派取象：宫位、干支组合、合冲刑害的象，重「发生了什么」而非「是什么格」")
        appendLine("- 神煞：只作旁证，单见不吉、多见成势才提，不单独定吉凶")
        appendLine()
        appendLine("各派结论冲突时，明确写出：分歧点是什么、你采信哪一派、依据是什么、")
        appendLine("另一派的结论在什么条件下才成立。不要用「综合来看」把冲突抹平。")
        appendLine()

        appendLine("## 三、分析维度：逐项，且每项都要落到干支与宫位")
        appendLine()
        appendLine("1. **事业**：适合的行业与岗位性质、体制内还是市场、升迁节奏、能否创业、转折点在哪步运")
        appendLine("2. **财运**：财从何处来（正财／偏财／食伤生财／官护财）、聚散节奏、破财节点、守财方式")
        appendLine("3. **婚恋**：配偶形象与相识途径、婚恋时间窗口、相处模式、风险点与化解方向")
        appendLine("4. **子女**：子女缘分厚薄、生育的时间窗口、与子女的亲疏")
        appendLine("5. **六亲**：父母、兄弟姐妹、祖辈各自的助力与牵累，各对应哪一宫")
        appendLine("6. **健康**：先天薄弱的脏腑、易发问题、需要留意的年份")
        appendLine("7. **学业**：读书与考试运、适合的学习方式、专业方向")
        appendLine()

        appendLine("## 四、关键事件：四项缺一不可")
        appendLine()
        appendLine("凡断定会发生的事，都要给出：")
        appendLine()
        appendLine("- **时间范围**：精确到年，必要时到月；要落在下表的大运与流年上，")
        appendLine("  不许用「二十几岁」「中年」这类无法核对的说法")
        appendLine("- **吉凶属性**：吉／凶／半吉半凶；半吉半凶要说明吉在何处、凶在何处")
        appendLine("- **影响程度**：决定性／重大／中等／轻微，四级取一")
        appendLine("- **具体影响**：对现实生活意味着什么（换工作、破财、成婚、伤病……）")
        appendLine()

        appendLine("## 五、结论要能核对")
        appendLine()
        appendLine("每条判断后面注明依据：哪一柱、哪个十神、哪组刑冲合害、哪步运、哪个流年。")
        appendLine("只给结论不给依据的段落，等于没写。")
        appendLine()

        appendLine("## 六、建议要能执行")
        appendLine()
        appendLine("针对这个盘的具体结构给建议，不要通用鸡汤。按三档写：")
        appendLine("**现在就能做**（三个月内）、**三年内**、**长期**。")
        appendLine()

        appendLine("## 七、不确定就说不确定")
        appendLine()
        appendLine("时辰存疑、出生地不明、流派分歧都算不确定性。")
        appendLine("先说明它会让哪些结论失效，再给条件式结论（「若为辰时则……，若为卯时则……」）。")
        appendLine()

        appendLine("## 八、输出格式")
        appendLine()
        appendLine("Markdown。顺序：一句话总论 → 七个维度逐项 → 关键事件时间轴（按年份列表）")
        appendLine("→ 建议三档。总论不超过三句，时间轴要能与上文逐一对应。")
        appendLine()

        appendLine("---")
        appendLine()
        appendLine("以下为盘面数据。")
        appendLine()
    }

    // ------------------------------------------------------------ 一、排盘

    private fun StringBuilder.appendBasic(chart: BaziChart) {
        val moment = chart.moment
        val input = chart.input
        val sex = if (input.gender == Gender.MALE) "男（乾造）" else "女（坤造）"

        appendLine("## 排盘")
        appendLine()
        appendLine("- 性别：$sex")
        appendLine("- 公历（钟表时）：${dateTime(moment.clockTime)}")
        appendLine(
            "- 真太阳时：${dateTime(moment.trueSolarTime)}" +
                if (moment.solarTimeApplied) "" else "（未启用，按钟表时定时辰）"
        )
        appendLine(
            "- 时差校正：经度 %+.1f 分（出生地经度 %.2f°E，东经 120° 基准）、均时差 %+.1f 分%s".format(
                moment.correction.longitudeMinutes,
                input.city.longitude,
                moment.correction.equationOfTimeMinutes,
                if (moment.correction.dstMinutes != 0) "、夏令时 −%d 分".format(moment.correction.dstMinutes) else ""
            )
        )
        appendLine("- 农历：${moment.lunar.text}，属${moment.lunar.zodiac}")
        appendLine("- 出生地：${input.city.displayName}（北纬 %.2f°）".format(input.city.latitude))
        appendLine(
            "- 节气：当令 ${moment.currentTerm.name}（${dateTime(moment.currentTerm.time)}）；" +
                "下一节 ${moment.nextJie.name}（${dateTime(moment.nextJie.time)}）"
        )
        appendLine(
            "- 起运：${chart.startLuck.text}，${dateTime(chart.startLuck.startTime)} 交运，起运虚岁 ${chart.startLuck.startAge}"
        )
        appendLine(
            "- 胎元 ${chart.fetalOrigin}　胎息 ${chart.fetalBreath}　命宫 ${chart.ownSign}　身宫 ${chart.bodySign}"
        )
        appendLine()
    }

    private fun StringBuilder.appendPillars(
        chart: BaziChart,
        shenSha: Map<PillarPosition, List<String>>
    ) {
        val p = chart.pillars

        appendLine("四柱：**${chart.ganZhiText}**（年　月　日　时）")
        appendLine()
        appendLine("日主：**${chart.dayMaster.cn}${chart.dayMaster.wuXing.cn}**（${chart.dayMaster.polarity}干）")
        appendLine()
        appendLine("| 项目 | 年柱 | 月柱 | 日柱 | 时柱 |")
        appendLine("| --- | --- | --- | --- | --- |")
        appendLine("| 主星 | " + p.joinToString(" | ") { it.ganTenGod?.cn ?: "日主" } + " |")
        appendLine("| 天干 | " + p.joinToString(" | ") { "${it.gan.cn}${it.gan.wuXing.cn}" } + " |")
        appendLine("| 地支 | " + p.joinToString(" | ") { "${it.zhi.cn}${it.zhi.wuXing.cn}" } + " |")
        appendLine("| 藏干 | " + p.joinToString(" | ") { hiddenStems(it) } + " |")
        appendLine("| 副星 | " + p.joinToString(" | ") { hiddenGods(it) } + " |")
        appendLine("| 纳音 | " + p.joinToString(" | ") { it.naYin } + " |")
        appendLine("| 星运 | " + p.joinToString(" | ") { it.growthPhase.cn } + " |")
        appendLine("| 自坐 | " + p.joinToString(" | ") { it.ownGrowthPhase.cn } + " |")
        // 每柱各按自身干支所属之旬取空亡，四柱通常各不相同；
        // 另用「落空」标出哪几柱的地支正落日柱旬空，两者语义不同，不可合并。
        appendLine("| 空亡 | " + p.joinToString(" | ") { pillar -> pillar.voidBranches.joinToString("") { it.cn } } + " |")
        appendLine("| 落空 | " + p.joinToString(" | ") { if (it.isEmptyBranch) "空" else "—" } + " |")
        if (shenSha.isNotEmpty()) {
            // 空亡已单独成行，神煞栏不再重复列它
            appendLine(
                "| 神煞 | " + p.joinToString(" | ") { pillar ->
                    shenSha[pillar.position].orEmpty()
                        .filter { it != "空亡" }
                        .joinToString("、")
                        .ifEmpty { "—" }
                } + " |"
            )
        }
        appendLine()
        appendLine("旬空（依日柱）：${chart.voidBranches.joinToString("、") { it.cn }}")
        appendLine()
        if (shenSha.isNotEmpty()) {
            appendLine("神煞按通行查法：天乙、国印、金舆、福星年干日干并查；亡神、劫煞年支日支并查；")
            appendLine("华盖、将星排除本宫自指；学堂、词馆取年柱纳音的长生与临官。")
            appendLine("各家口径有出入，若你的查法不同，以你的为准并注明。")
            appendLine()
        }
    }

    private fun hiddenStems(pillar: Pillar): String =
        pillar.hiddenStems.joinToString(" ") { it.hidden.gan.cn }

    private fun hiddenGods(pillar: Pillar): String =
        pillar.hiddenStems.joinToString("/") { it.tenGod.cn }

    // ------------------------------------------------------------ 二、大运

    private fun StringBuilder.appendLuck(chart: BaziChart, referenceYear: Int) {
        appendLine("## 大运")
        appendLine()
        appendLine("| 大运 | 虚岁 | 公历年 | 干十神 | 支十神 | 星运 | 纳音 | 空亡 |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
        chart.luckPillars.forEach { luck -> appendLuckRow(luck, referenceYear) }
        appendLine()
    }

    private fun StringBuilder.appendLuckRow(luck: LuckPillar, referenceYear: Int) {
        val mark = if (referenceYear in luck.startYear..luck.endYear) " ←当前" else ""
        appendLine(
            "| ${luck.ganZhi}$mark | ${luck.startAge}–${luck.endAge} | ${luck.startYear}–${luck.endYear} " +
                "| ${luck.ganTenGod.cn} | ${luck.zhiMainTenGod.cn} | ${luck.growthPhase.cn} " +
                "| ${luck.naYin} | ${if (luck.isEmptyBranch) "空" else "—"} |"
        )
    }

    // ------------------------------------------------------------ 三、流年

    private fun StringBuilder.appendAnnuals(chart: BaziChart, referenceYear: Int) {
        appendLine("## 流年")
        appendLine()
        appendLine("| 年份 | 虚岁 | 干支 | 十神 | 纳音 | 空亡 | 所属大运 |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        annualWindow(chart, referenceYear).forEach { annual -> appendAnnualRow(annual, referenceYear) }
        appendLine()
    }

    private fun StringBuilder.appendAnnualRow(annual: AnnualFortune, referenceYear: Int) {
        val mark = if (annual.year == referenceYear) " ←今年" else ""
        appendLine(
            "| ${annual.year}$mark | ${annual.nominalAge} | ${annual.ganZhi} | ${annual.ganTenGod.cn} " +
                "| ${annual.naYin} | ${if (annual.isEmptyBranch) "空" else "—"} | ${annual.luckGanZhi} |"
        )
    }

    // ------------------------------------------------------------ 四、流月

    private fun StringBuilder.appendMonthlies(chart: BaziChart, referenceYear: Int) {
        val months = BaziCalculator.monthliesOf(chart, referenceYear)
        appendLine("## 流月（$referenceYear 年）")
        appendLine()
        if (months.isEmpty()) {
            appendLine("该年流月数据缺失。")
            appendLine()
            return
        }
        appendLine("| 月建 | 干支 | 十神 | 纳音 | 空亡 | 交节 | 终节 |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        months.forEach { m -> appendMonthRow(m) }
        appendLine()
        appendLine("月建以节为界，寅月自立春起算，丑月交于次年立春。")
        appendLine()
    }

    private fun StringBuilder.appendMonthRow(m: MonthlyFortune) {
        val mark = if (m.isCurrent) " ←当月" else ""
        appendLine(
            "| ${m.monthName}${m.zhi.cn}月$mark | ${m.ganZhi} | ${m.ganTenGod.cn} " +
                "| ${m.naYin} | ${if (m.isEmptyBranch) "空" else "—"} " +
                "| ${dateTime(m.startTime)} | ${dateTime(m.endTime)} |"
        )
    }

    // ------------------------------------------------------------ 工具

    private fun annualWindow(chart: BaziChart, referenceYear: Int): List<AnnualFortune> =
        chart.annuals.filter { it.year in (referenceYear - ANNUAL_BACK)..(referenceYear + ANNUAL_FORWARD) }

    private fun dateTime(t: LocalDateTime): String =
        "%04d-%02d-%02d %02d:%02d".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
}
