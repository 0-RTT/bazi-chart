package com.bazi.interpret

import com.bazi.calendar.Gender
import com.bazi.engine.AnnualFortune
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
import com.bazi.engine.LuckPillar
import com.bazi.engine.MonthlyFortune
import com.bazi.engine.Pillar
import com.bazi.engine.DiZhi
import com.bazi.engine.PillarPosition
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * 结构因子。
 *
 * 只装**可核对的事实**，不装判断：日主强弱、格局、用神一概不在这里。
 * 字段边界划在「能被重算」这一侧：「日主在月令为墓」可以查表核对，
 * 「日主偏弱」不能；「帮身占 47.3%」可以重算，「身弱用印」不能。
 */
data class StructureFactors(
    /** 月令。 */
    val monthCommand: DiZhi,
    /** 月令本气与透干情况，如「本气戊土，天干未透；藏戊辛丁」。 */
    val monthDetail: String,
    /** 日主在月令的十二长生，如「墓」。 */
    val dayMasterPhase: String,
    /** 月令是否生扶日主。 */
    val commandSupported: Boolean,
    /** 日支是否生扶日主。 */
    val groundSupported: Boolean,
    /** 天干中帮身（比劫、印）的位数。 */
    val supportStems: Int,
    /** 帮身力量占比，0..1。 */
    val supportRatio: Double,
    /** 五行力量占比，0..1，按从高到低。 */
    val elementShares: List<Pair<String, Double>>,
    /** 四柱未见的五行。 */
    val missing: List<String>,
    /** 天干关系，如「甲己五合（年干—月干，化土）」。 */
    val ganRelations: List<String>,
    /** 地支关系，如「卯戌火六合」「午午自刑」。 */
    val zhiRelations: List<String>
)

/**
 * 盘面文本生成器。
 *
 * 全文三段：**指令**（要模型做什么）、**盘面数据**（排盘、大运、流年、流月）、
 * **结构因子**（可复核但不含判断的事实）。
 *
 * 为什么要有指令段：这份文本的唯一用途就是粘给大模型。早先只给数据不给指令，
 * 是想让使用者自己决定怎么问；实际用下来，每次都要重打一遍维度与输出要求，
 * 十次有八次会漏掉「关键事件要给时间范围」。把要求写死在生成端，盘与问就不会走散。
 *
 * 结构因子是「不给结论」与「不给支撑」之间的那条线：旺衰、格局、调候、喜用
 * 一概不写，先给了模型只会顺着抄；但完全不给支撑，它又会凭印象乱判强弱。
 * 于是只给**能被重算**的东西（得令得地、力量占比、刑冲合害清单、月令气候），
 * 把「身弱」「正官格」「用神为某字」留给它自己推——判错时，读者对着因子
 * 一算就知道错在哪一步。
 *
 * 因子由 app 层从分析层取出后传入：本模块只排版、不计算，也不依赖
 * `core:analysis`（与神煞入参同一理由）。
 *
 * 指令段由用户给定，逐字照排：角色与视角一句，推演次序一句，六项维度一句，
 * 影响程度与建议一句。不再分节列举——一段话模型会照着走，九节清单它容易
 * 挑好写的格子填满。按用户决定，全文不加免责声明。
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
     * @param factors 结构因子。同上，由 app 层从分析层取出后传进来——
     *                依赖边界不变，本层只排版、不计算。
     */
    fun build(
        chart: BaziChart,
        referenceYear: Int = LocalDate.now().year,
        shenSha: Map<PillarPosition, List<String>> = emptyMap(),
        factors: StructureFactors? = null
    ): String = buildString {
        appendInstruction()
        appendBasic(chart)
        appendPillars(chart, shenSha)
        if (factors != null) appendFactors(factors)
        appendLuck(chart, referenceYear)
        appendAnnuals(chart, referenceYear)
        appendMonthlies(chart, referenceYear)
    }

    // ------------------------------------------------------------ 〇、指令

    /**
     * 指令段。
     *
     * 整段为用户给定原文，逐字照排，不增补、不改写：角色与视角 → 推演次序
     * （日主强弱与五行旺衰、十神格局、用神喜忌）→ 六项维度 → 每项三样交代
     * （吉凶倾向、关键事件与时间范围、影响程度高中低）→ 可落地的建议
     * （方位、行业、行事时机、风险点）。
     *
     * 此前那一版是九节清单（校盘、主链、纪律、维度、事件、建议、流月、不确定、分档）。
     * 用户明确要求换成这段连续行文，且「不要掺杂其他内容」，故九节整段撤下：
     * 置信度、多路闭环、不复述盘面这些纪律也一并去掉，不再以指令形式出现。
     *
     * 只留一处过渡——「以下为盘面数据」。没有它，表头会紧贴上句，被当成行文的续写。
     */
    private fun StringBuilder.appendInstruction() {
        // 整段输出为**一个**段落，不按屏宽折行：复制出去要是一段完整的话，
        // 折行会在各家模型那里被当成列表或换行断句。源码里的 + 拼接不产生换行。
        appendLine(
            "请你以精通中国传统命理、擅长八字分析的命理师视角，系统解读命主八字。" +
                "请按日主强弱与五行旺衰、十神格局、用神喜忌的次序展开推演，" +
                "再逐项分析健康、学业、事业、财运、人际关系、婚姻与感情，" +
                "每项给出明确的吉凶倾向、关键事件及其发生的时间范围" +
                "（可用大运、流年或年龄段表述）、事件对命主的影响程度（高、中、低）。" +
                "最后结合命主的性格与命局特点，给出可落地的改善建议与应对策略，" +
                "包括有利的方位、行业、行事时机与需规避的风险点。"
        )
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("以下为盘面数据。")
        appendLine()
    }

    // ------------------------------------------------------------ 一、结构因子

    /**
     * 结构因子表。
     *
     * 位置刻意放在四柱表之后：先看盘、再看因子，模型才不会把因子当主证据。
     * 表里每一行都能被复核，模型判「身弱」还是「身强」时，读者拿因子一算
     * 就知道它错在哪一步——这也是把「强弱／格局／用神」留给它自己判的底气。
     */
    private fun StringBuilder.appendFactors(f: StructureFactors) {
        appendLine("## 结构因子")
        appendLine()
        appendLine("| 因子 | 结果 |")
        appendLine("| --- | --- |")
        appendLine("| 月令 | ${f.monthCommand.cn} · ${f.monthDetail} |")
        appendLine("| 日主在月令 | ${f.dayMasterPhase} |")
        appendLine("| 得令 | ${yesNo(f.commandSupported)}，月令是否生扶日主 |")
        appendLine("| 得地 | ${yesNo(f.groundSupported)}，日支是否生扶日主 |")
        // 两个口径不能挤在同一行：位数只数四天干，占比却是含地支藏干的全盘加权，
        // 写在一起会被读成「这 N 位贡献了 X%」。
        appendLine("| 天干帮身 | ${f.supportStems} 位（比劫与印，只数四天干） |")
        appendLine(
            "| 帮身力量占比 | %.1f%%（含地支藏干的全盘加权） |"
                .format(Locale.ROOT, f.supportRatio * 100)
        )
        appendLine("| 五行力量 | ${shareText(f.elementShares)} |")
        appendLine("| 四柱未见 | ${if (f.missing.isEmpty()) "无" else f.missing.joinToString("、")} |")
        appendLine("| 天干关系 | ${orNone(f.ganRelations)} |")
        appendLine("| 地支关系 | ${orNone(f.zhiRelations)} |")
        appendLine("| 月令气候 | ${seasonNote(f.monthCommand)} |")
        appendLine()
        appendLine(
            "权重口径：天干各 1.0；地支月 3.0、日 2.0、年时各 1.0；" +
                "支内按本气／中气／余气折算。天干关系只判有没有合与合化何物，不判化与不化。"
        )
        appendLine()
    }

    private fun yesNo(v: Boolean): String = if (v) "是" else "否"

    private fun orNone(items: List<String>): String =
        if (items.isEmpty()) "无" else items.joinToString("；")

    private fun shareText(shares: List<Pair<String, Double>>): String =
        shares.joinToString(" ｜ ") {
            it.first + " " + "%.1f".format(Locale.ROOT, it.second * 100) + "%"
        }

    /** 月令气候。只写季节与燥湿，不写「该用什么」，那是读盘人的事。 */
    private fun seasonNote(zhi: DiZhi): String = when (zhi) {
        DiZhi.YIN -> "孟春，木当令，寒气未尽而阳气初升"
        DiZhi.MAO -> "仲春，木当令，气候温润"
        DiZhi.CHEN -> "季春，土湿，木有余气，雨水渐多"
        DiZhi.SI -> "孟夏，火当令，燥热渐起"
        DiZhi.WU -> "仲夏，火当令，最燥热"
        DiZhi.WEI -> "季夏，土燥，火有余气，湿热交蒸"
        DiZhi.SHEN -> "孟秋，金当令，暑退而燥"
        DiZhi.YOU -> "仲秋，金当令，最燥"
        DiZhi.XU -> "季秋，土燥，金有余气，天凉而干"
        DiZhi.HAI -> "孟冬，水当令，寒湿初起"
        DiZhi.ZI -> "仲冬，水当令，最寒"
        DiZhi.CHOU -> "季冬，土寒，水有余气，湿冷"
    }


    // ------------------------------------------------------------ 二、排盘

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
                Locale.ROOT,
                moment.correction.longitudeMinutes,
                input.city.longitude,
                moment.correction.equationOfTimeMinutes,
                if (moment.correction.dstMinutes != 0) "、夏令时 −%d 分".format(Locale.ROOT, moment.correction.dstMinutes) else ""
            )
        )
        appendLine("- 农历：${moment.lunar.text}，属${moment.lunar.zodiac}")
        appendLine("- 出生地：${input.city.displayName}（北纬 %.2f°）".format(Locale.ROOT, input.city.latitude))
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

    // ------------------------------------------------------------ 三、大运

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

    // ------------------------------------------------------------ 四、流年

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

    // ------------------------------------------------------------ 五、流月

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
        "%04d-%02d-%02d %02d:%02d".format(
            Locale.ROOT, t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute
        )
}
