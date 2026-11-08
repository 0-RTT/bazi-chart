package com.bazi.interpret

import com.bazi.calendar.Gender
import com.bazi.engine.AnnualFortune
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
import com.bazi.engine.LuckPillar
import com.bazi.engine.MonthlyFortune
import com.bazi.engine.Pillar
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 盘面信息生成器。
 *
 * 只给数据，不给指令：不写「请分析」，不写「仅供参考」，也不替读者划定边界。
 *
 * 全文四节，只保留基本盘面：排盘、大运、流年、流月。
 * 神煞不在此列——各家查法分歧极大，同一张盘能排出完全不同的结果，
 * 与其给一份站不住的清单，不如只给可以逐格核对的干支。
 * 旺衰、格局、调候、刑冲合害同属推论，一并不收。
 *
 * 纯 Kotlin，无 Android 依赖，可在 JVM 上直接断言文本内容。
 */
object AiPromptBuilder {

    /** 流年的展示窗口：参考年前 5 年至后 15 年。 */
    private const val ANNUAL_BACK = 5
    private const val ANNUAL_FORWARD = 15

    fun build(
        chart: BaziChart,
        referenceYear: Int = LocalDate.now().year
    ): String = buildString {
        appendLine("# 八字命盘")
        appendLine()
        appendBasic(chart)
        appendPillars(chart)
        appendLuck(chart, referenceYear)
        appendAnnuals(chart, referenceYear)
        appendMonthlies(chart, referenceYear)
    }

    // ------------------------------------------------------------ 一、排盘

    private fun StringBuilder.appendBasic(chart: BaziChart) {
        val moment = chart.moment
        val input = chart.input
        val sex = if (input.gender == Gender.MALE) "男（乾造）" else "女（坤造）"

        appendLine("## 一、排盘")
        appendLine()
        appendLine("- 性别：$sex")
        appendLine("- 公历（钟表时）：${dateTime(moment.clockTime)}")
        appendLine("- 真太阳时：${dateTime(moment.trueSolarTime)}")
        appendLine(
            "- 时差校正：经度 %+.1f 分（出生地经度 %.2f°E，东经 120° 基准）、均时差 %+.1f 分%s".format(
                moment.correction.longitudeMinutes,
                input.city.longitude,
                moment.correction.equationOfTimeMinutes,
                if (moment.correction.dstMinutes != 0) "、夏令时 −%d 分".format(moment.correction.dstMinutes) else ""
            )
        )
        appendLine("- 农历：${moment.lunar.text}，属${moment.lunar.zodiac}")
        appendLine("- 出生地：${input.city.displayName}")
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

    private fun StringBuilder.appendPillars(chart: BaziChart) {
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
        appendLine()
        appendLine("旬空（依日柱）：${chart.voidBranches.joinToString("、") { it.cn }}")
        appendLine()
    }

    private fun hiddenStems(pillar: Pillar): String =
        pillar.hiddenStems.joinToString(" ") { it.hidden.gan.cn }

    private fun hiddenGods(pillar: Pillar): String =
        pillar.hiddenStems.joinToString("/") { it.tenGod.cn }

    // ------------------------------------------------------------ 二、大运

    private fun StringBuilder.appendLuck(chart: BaziChart, referenceYear: Int) {
        appendLine("## 二、大运")
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
        appendLine("## 三、流年")
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
        appendLine("## 四、流月（$referenceYear 年）")
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
