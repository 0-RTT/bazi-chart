package com.bazi.interpret

import com.bazi.analysis.AnalysisResult
import com.bazi.calendar.Gender
import com.bazi.engine.BaziChart
import com.bazi.engine.Pillar
import com.bazi.engine.RelationType
import java.time.LocalDate

/**
 * AI 分析指令生成器。
 *
 * 排盘完成后直接产出一段可整段粘贴给大模型的指令文本。
 * 设计要点：
 * 1. **命盘数据一次给全**——不让模型重新推算，否则它会自己"算"出不同的干支；
 * 2. **明确任务边界**——九项分析逐条列出，避免输出泛泛而谈的万能话术；
 * 3. **明确输出约束**——先结论后依据、术语附白话、分歧要标注、不做绝对化断言。
 *
 * 纯 Kotlin，无 Android 依赖，可在 JVM 上直接验收文本内容。
 */
object AiPromptBuilder {

    fun build(
        chart: BaziChart,
        analysis: AnalysisResult,
        referenceYear: Int = LocalDate.now().year
    ): String = buildString {
        appendLine("# 八字命盘分析任务")
        appendLine()
        appendLine("你是一位精研《滴天髓》《子平真诠》《穷通宝鉴》的八字分析师。下面这份命盘由专业排盘引擎算出，")
        appendLine("干支、节气与起运时刻均已核对无误。请直接在此基础上做结构化分析，**不要重新排盘**，也不要改动任何干支。")
        appendLine()

        appendBasic(chart)
        appendPillars(chart)
        appendElements(analysis)
        appendPattern(analysis)
        appendShenSha(analysis)
        appendRelations(chart, analysis)
        appendLuck(chart, referenceYear)
        appendTasks()
        appendConstraints()
    }

    // ------------------------------------------------------------ 一、基本信息

    private fun StringBuilder.appendBasic(chart: BaziChart) {
        val moment = chart.moment
        val input = chart.input
        val sex = if (input.gender == Gender.MALE) "男（乾造）" else "女（坤造）"

        appendLine("## 一、命主基本信息")
        appendLine()
        appendLine("- 性别：$sex")
        appendLine("- 公历（钟表时）：${dateTime(moment.clockTime)}")
        appendLine("- 真太阳时：${dateTime(moment.trueSolarTime)}")
        appendLine(
            "  - 经度校正 %+.1f 分钟（出生地经度 %.2f°E，按东经 120° 基准折算）".format(
                moment.correction.longitudeMinutes, input.city.longitude
            )
        )
        appendLine("  - 均时差 %+.1f 分钟".format(moment.correction.equationOfTimeMinutes))
        if (moment.correction.dstMinutes != 0) {
            appendLine("  - 夏令时扣减 %d 分钟".format(moment.correction.dstMinutes))
        }
        appendLine(
            "- 农历：${moment.lunar.text}（${moment.lunar.yearGanZhi}年，属${moment.lunar.zodiac}）"
        )
        appendLine("- 出生地：${input.city.displayName}")
        appendLine(
            "- 节气：当令 ${moment.currentTerm.name}（${dateTime(moment.currentTerm.time)}）；" +
                "下一节 ${moment.nextJie.name}（${dateTime(moment.nextJie.time)}）"
        )
        appendLine(
            "- 起运：${chart.startLuck.text}，${dateTime(chart.startLuck.startTime)} 交运" +
                "（起运虚岁 ${chart.startLuck.startAge} 岁）"
        )
        appendLine("- 胎元 ${chart.fetalOrigin}　命宫 ${chart.ownSign}　身宫 ${chart.bodySign}　胎息 ${chart.fetalBreath}")
        appendLine()
    }

    // ------------------------------------------------------------ 二、四柱

    private fun StringBuilder.appendPillars(chart: BaziChart) {
        val p = chart.pillars
        appendLine("## 二、四柱命盘")
        appendLine()
        appendLine("四柱：**${chart.ganZhiText}**（年　月　日　时）")
        appendLine()
        appendLine("日主：**${chart.dayMaster.cn}${chart.dayMaster.wuXing.cn}**（${chart.dayMaster.polarity}干）")
        appendLine()
        appendLine("| 项目 | 年柱 | 月柱 | 日柱 | 时柱 |")
        appendLine("| --- | --- | --- | --- | --- |")
        appendLine("| 十神 | " + p.joinToString(" | ") { it.ganTenGod?.cn ?: "日主" } + " |")
        appendLine("| 天干 | " + p.joinToString(" | ") { "${it.gan.cn}${it.gan.wuXing.cn}" } + " |")
        appendLine("| 地支 | " + p.joinToString(" | ") { "${it.zhi.cn}${it.zhi.wuXing.cn}" } + " |")
        appendLine("| 藏干 | " + p.joinToString(" | ") { hiddenStems(it) } + " |")
        appendLine("| 藏干十神 | " + p.joinToString(" | ") { hiddenGods(it) } + " |")
        appendLine("| 纳音 | " + p.joinToString(" | ") { it.naYin } + " |")
        appendLine("| 星运 | " + p.joinToString(" | ") { it.growthPhase.cn } + " |")
        appendLine("| 空亡 | " + p.joinToString(" | ") { if (it.isEmptyBranch) "空" else "—" } + " |")
        appendLine()
        appendLine("说明：星运为日主在该支的十二长生状态；空亡依日柱旬空（${chart.voidBranches.joinToString("、") { it.cn }}）。")
        appendLine()
    }

    private fun hiddenStems(pillar: Pillar): String =
        pillar.hiddenStems.joinToString(" ") { it.hidden.gan.cn }

    private fun hiddenGods(pillar: Pillar): String =
        pillar.hiddenStems.joinToString("/") { it.tenGod.cn }

    // ------------------------------------------------------------ 三、五行力量

    private fun StringBuilder.appendElements(a: AnalysisResult) {
        val s = a.strength
        appendLine("## 三、五行力量与旺衰")
        appendLine()
        appendLine("| 五行 | 得分 | 占比 |")
        appendLine("| --- | --- | --- |")
        s.elementScores.forEach { e ->
            appendLine("| ${e.wuXing.cn} | %.2f | %.1f%% |".format(e.score, e.percent * 100))
        }
        appendLine()
        appendLine(
            "日主${s.dayMasterElement.cn}，帮身之力（比劫＋印）占 **%.1f%%** → **%s**".format(
                s.ratio * 100, s.level.cn
            )
        )
        appendLine(
            "得令：${yes(s.hasCommand)}　得地：${yes(s.hasGround)}　得势：${yes(s.hasMomentum)}"
        )
        if (a.missingElements.isNotEmpty()) {
            appendLine("近乎不见的五行：${a.missingElements.joinToString("、") { it.cn }}")
        }
        appendLine()
        appendLine("打分口径：天干各 1.0；地支按位置加权（月支 3.0、日支 2.0、年时支各 1.0），支内按本气/中气/余气 1.0/0.5/0.2 折算。")
        appendLine()
    }

    // ------------------------------------------------------------ 四、格局调候

    private fun StringBuilder.appendPattern(a: AnalysisResult) {
        appendLine("## 四、格局与调候")
        appendLine()
        appendLine("- 格局：**${a.pattern.displayName}**")
        appendLine("  - 取格依据：${a.pattern.basis}")
        appendLine("  - 格神${if (a.pattern.transparent) "已透干，成格较实" else "未透干，成格偏虚，需运程引发"}")
        appendLine("- 调候（《穷通宝鉴》）：${a.tiaohou.text}")
        appendLine("- 喜用倾向（依旺衰得出）：${a.favorableElements.joinToString("、") { it.cn }}")
        appendLine()
    }

    // ------------------------------------------------------------ 五、神煞

    private fun StringBuilder.appendShenSha(a: AnalysisResult) {
        appendLine("## 五、神煞")
        appendLine()
        if (a.shenSha.isEmpty()) {
            appendLine("本局未见常用神煞。")
        } else {
            a.shenSha.forEach { hit ->
                appendLine("- ${hit.name}（${hit.category.cn}）：${hit.positionText}　—— ${hit.note}")
            }
        }
        appendLine()
    }

    // ------------------------------------------------------------ 六、刑冲合害

    private fun StringBuilder.appendRelations(chart: BaziChart, a: AnalysisResult) {
        appendLine("## 六、刑冲合害")
        appendLine()
        if (a.relations.isEmpty()) {
            appendLine("四柱地支之间无明显冲、合、刑、害。")
        } else {
            a.relations.distinctBy { it.type to it.note }.forEach { r ->
                val positions = if (r.positions.isEmpty()) "" else "（${r.positions.joinToString("、") { it.cn }}）"
                appendLine("- ${r.type.cn}：${r.note}$positions")
            }
            if (chart.relations.any { it.type == RelationType.LIU_CHONG }) {
                appendLine()
                appendLine("提示：本局见冲，请重点说明冲在哪两宫、冲的是何十神。")
            }
        }
        appendLine()
    }

    // ------------------------------------------------------------ 七、大运流年

    private fun StringBuilder.appendLuck(chart: BaziChart, referenceYear: Int) {
        appendLine("## 七、大运与流年")
        appendLine()
        appendLine("| 大运 | 虚岁 | 公历年份 | 干十神 | 支十神 | 星运 |")
        appendLine("| --- | --- | --- | --- | --- | --- |")
        chart.luckPillars.take(8).forEach { luck ->
            val mark = if (referenceYear in luck.startYear..luck.endYear) " ←当前" else ""
            appendLine(
                "| ${luck.ganZhi}$mark | ${luck.startAge}–${luck.endAge} | " +
                    "${luck.startYear}–${luck.endYear} | ${luck.ganTenGod.cn} | ${luck.zhiMainTenGod.cn} | ${luck.growthPhase.cn} |"
            )
        }
        appendLine()

        val current = chart.luckOfYear(referenceYear)
        if (current != null) {
            appendLine(
                "当前大运（$referenceYear 年）：**${current.ganZhi}**　${current.startAge}–${current.endAge} 岁　" +
                    "${current.startYear}–${current.endYear} 年"
            )
        } else {
            appendLine("当前尚未交入首步大运。")
        }
        appendLine()

        appendLine("未来流年（自 ${referenceYear + 1} 年起）：")
        appendLine()
        appendLine("| 年份 | 虚岁 | 干支 | 干十神 | 所属大运 |")
        appendLine("| --- | --- | --- | --- | --- |")
        chart.annuals
            .filter { it.year in (referenceYear + 1)..(referenceYear + 6) }
            .forEach { annual ->
                appendLine(
                    "| ${annual.year} | ${annual.nominalAge} | ${annual.ganZhi} | " +
                        "${annual.ganTenGod.cn} | ${annual.luckGanZhi} |"
                )
            }
        appendLine()
    }

    // ------------------------------------------------------------ 八、分析任务

    private fun StringBuilder.appendTasks() {
        appendLine("## 八、请完成以下分析")
        appendLine()
        appendLine("每一项都要落到具体干支与宫位，不要写放之四海皆准的话。")
        appendLine()
        appendLine("1. **日主旺衰复核**：核对上面的帮身占比结论是否成立，指出得令/得地/得势三者中哪一项最关键，哪一项可能被高估。")
        appendLine("2. **格局成败**：月令取格是否成立？格神有无透干、有无破格之神（如官格见伤、财格见劫），成格层次如何。")
        appendLine("3. **用神喜忌**：结合旺衰与调候两条线，给出用神、喜神、忌神；若两者取用冲突，说明你取哪一条及理由。")
        appendLine("4. **性格与心性**：以日干、日支、月令、旺衰四要素为主，写 150 字左右的人物素描，要能看出是「这个人」。")
        appendLine("5. **事业与财运**：财官印食伤中哪一类是本造真正的着力点？适配的行业属性、组织形态，以及最该避开的做事方式。")
        appendLine("6. **婚姻与感情**：看日支（配偶宫）与财星/官星的状态，指出配偶宫是否受冲刑合，感情上最容易重复出现的模式。")
        appendLine("7. **健康提示**：五行偏枯指向的脏腑与作息风险，只给生活调理建议，不做疾病判断。")
        appendLine("8. **大运流年**：逐年点评未来流年与本命的合冲刑害、伏吟反吟关系，指出其中哪一年是转折点、转折方向为何。")
        appendLine("9. **可执行建议**：三条，每条不超过 40 字，要具体到「做什么/避什么」，不要写「多行善事」这类空话。")
        appendLine()
    }

    // ------------------------------------------------------------ 九、输出要求

    private fun StringBuilder.appendConstraints() {
        appendLine("## 九、输出要求")
        appendLine()
        appendLine("- **先结论后依据**：每条判断后用括号注明依据，例如「（日支子水与月令子水并见，伏吟）」。")
        appendLine("- **术语附注**：专业术语首次出现时，用一句白话解释。")
        appendLine("- **标注分歧**：遇到各家取法不同的地方，写明「此处有分歧，本分析取…」，不要含糊带过。")
        appendLine("- **不要改动数据**：若你认为我给出的干支、节气或起运有误，请单独指出并说明，不要自行更正后继续分析。")
        appendLine("- **不做绝对化断言**：不预测生死寿夭，不诊断疾病，不承诺投资收益，不使用「必然」「一定会」。")
        appendLine("- 用 Markdown 小标题分节，篇幅 1200–1800 字。")
    }

    // ------------------------------------------------------------ 工具

    private fun yes(value: Boolean): String = if (value) "是" else "否"

    private fun dateTime(t: java.time.LocalDateTime): String =
        "%04d-%02d-%02d %02d:%02d".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
}
