package com.bazi.interpret

import com.bazi.analysis.AnalysisResult
import com.bazi.analysis.StrengthLevel
import com.bazi.engine.BaziChart
import com.bazi.engine.DiZhi
import com.bazi.engine.Pillar
import com.bazi.engine.PillarPosition
import com.bazi.engine.RelationType
import com.bazi.engine.TenGod
import com.bazi.engine.TenGodCategory
import com.bazi.engine.WuXing
import java.time.LocalDate

/** 解读中的一个板块。 */
data class Section(
    val title: String,
    val paragraphs: List<String>
)

/**
 * 解读结果。
 *
 * [ruleIds] 保留每条结论对应的规则编号，便于回溯与专业审校——
 * 任何一句文案都必须能追到规则，不允许"凭空说"。
 */
data class Interpretation(
    val sections: List<Section>,
    val ruleIds: List<String>
)

/**
 * 规则模板解读引擎。
 *
 * 三段式：结构化中间量 → 规则匹配 → 文案拼装。
 * 不联网、不调用大模型，输出全部由 [TextBank] 中的条目组合而成。
 */
object Interpreter {

    fun interpret(
        chart: BaziChart,
        analysis: AnalysisResult,
        referenceYear: Int = LocalDate.now().year
    ): Interpretation {
        val dedup = mutableListOf<String>()
        val sections = mutableListOf<Section>()

        sections += overview(chart, analysis, dedup)
        sections += temperament(chart, analysis, dedup)
        sections += structure(chart, analysis, dedup)
        sections += tiaohouSection(analysis, dedup)
        sections += palaces(chart, dedup)
        sections += relations(chart, analysis, dedup)
        sections += fortune(chart, analysis, referenceYear, dedup)
        sections += disclaimer()

        return Interpretation(sections, dedup)
    }

    // ---------------------------------------------------------- 命局概览

    private fun overview(chart: BaziChart, a: AnalysisResult, ids: MutableList<String>): Section {
        val me = chart.dayMaster
        val season = seasonOf(chart.monthCommand)
        val paragraphs = mutableListOf<String>()

        paragraphs += buildString {
            append("日主").append(me.cn).append(me.wuXing.cn)
            append("，生于").append(chart.monthCommand.cn).append("月（").append(season).append("）。")
            append("四柱：").append(chart.ganZhiText).append("。")
            append("月令为").append(chart.monthCommand.cn)
            append("，本气").append(com.bazi.engine.HiddenStemTable.mainStem(chart.monthCommand).cn)
            append("，日主").append(if (a.strength.hasCommand) "得令" else "失令").append("。")
        }
        ids += "overview.base"

        paragraphs += buildString {
            append("综合旺衰：").append(a.strength.level.cn)
            append("，帮身之力占 ").append("%.1f".format(a.strength.ratio * 100)).append("%。")
            if (a.strength.hasGround) append("日支有根，")
            if (a.strength.hasMomentum) append("天干得势，")
            if (!a.strength.hasCommand) append("月令不助，")
            append("格局取").append(a.pattern.displayName).append("。")
        }
        ids += "overview.strength"

        paragraphs += "喜用倾向：宜" + a.favorableElements.joinToString("、") { it.cn } + "。此为依据旺衰得出的通用取向，实际取用仍需结合格局与调候。"
        ids += "overview.favorable"

        return Section("命局概览", paragraphs)
    }

    // ---------------------------------------------------------- 性情

    private fun temperament(chart: BaziChart, a: AnalysisResult, ids: MutableList<String>): Section {
        val me = chart.dayMaster
        val base = TextBank.DAY_MASTER[me] ?: ""
        ids += "temper.${me.cn}"

        val modifier = when (a.strength.level) {
            StrengthLevel.VERY_WEAK, StrengthLevel.WEAK, StrengthLevel.SLIGHTLY_WEAK ->
                "日主偏弱，主见易受环境牵动，凡事更依赖外援与他人的认可，宜借势而不宜独扛。"
            StrengthLevel.BALANCED ->
                "日主中和，进退有度，能屈能伸，遇事不易走极端。"
            StrengthLevel.SLIGHTLY_STRONG, StrengthLevel.STRONG ->
                "日主偏强，自我主张明确，行动力足，但也容易固执，需留意听不进不同意见。"
            StrengthLevel.VERY_STRONG ->
                "日主极强，个性极显，一意孤行处多，宜以规则与专业约束自己，反成大气。"
        }
        ids += "temper.strength"

        val dominant = a.strength.elementScores.maxByOrNull { it.score }
        val elementLine = dominant?.let {
            "命局中${it.wuXing.cn}最旺（占 %.1f%%），${TextBank.ELEMENT_TRAIT[it.wuXing]}".format(it.percent * 100)
        }
        if (dominant != null) ids += "temper.element.${dominant.wuXing.cn}"

        return Section("性情倾向", listOfNotNull(base, modifier, elementLine))
    }

    // ---------------------------------------------------------- 格局与十神

    private fun structure(chart: BaziChart, a: AnalysisResult, ids: MutableList<String>): Section {
        val paragraphs = mutableListOf<String>()
        paragraphs += buildString {
            append("取格依据：").append(a.pattern.basis).append("。")
            if (!a.pattern.transparent) append("格神未透干，成格偏虚，需运程引发。")
            else append("格神透干，成格较实。")
        }
        ids += "pattern.basis"

        val god = a.pattern.tenGod
        if (god != null) {
            paragraphs += TextBank.TEN_GOD_MEANING[god.category] ?: ""
            ids += "pattern.${god.category.name.lowercase()}"
        }

        val counts = TenGodCategory.entries.associateWith { cat ->
            chart.pillars.count { it.ganTenGod?.category == cat }
        }.filterValues { it > 0 }

        if (counts.isNotEmpty()) {
            val text = counts.entries.sortedByDescending { it.value }
                .joinToString("，") { "${it.key.cn}${it.value}" }
            paragraphs += "天干十神分布：$text。哪一类最重，人生重心往往就落在哪一处。"
            ids += "pattern.distribution"
        }

        return Section("格局与十神", paragraphs.filter { it.isNotBlank() })
    }

    // ---------------------------------------------------------- 调候

    private fun tiaohouSection(a: AnalysisResult, ids: MutableList<String>): Section {
        ids += "tiaohou"
        return Section(
            "调候",
            listOf(
                a.tiaohou.text,
                "调候依《穷通宝鉴》通行本，看的是寒暖燥湿是否得宜，与旺衰取用是两条并行的线，不可混为一谈。"
            )
        )
    }

    // ---------------------------------------------------------- 六亲宫位

    private fun palaces(chart: BaziChart, ids: MutableList<String>): Section {
        val paragraphs = chart.pillars.map { pillar ->
            val god = pillar.ganTenGod?.cn ?: "日主"
            val phase = pillar.growthPhase.cn
            val void = if (pillar.isEmptyBranch) "，此柱落空亡" else ""
            "${pillar.position.cn} ${pillar.ganZhi}（干${god}·支${pillar.zhi.cn}·${phase}${void}）：${palaceRole(pillar)}"
        }
        ids += "palaces"
        return Section("六亲宫位", paragraphs)
    }

    private fun palaceRole(pillar: Pillar): String = when (pillar.position) {
        PillarPosition.YEAR -> "主祖上与幼年环境，也看早年的家庭根基。"
        PillarPosition.MONTH -> "主父母与青年阶段，是格局成败的枢纽。"
        PillarPosition.DAY -> "日干为自身，日支为配偶宫，主中年与婚姻。"
        PillarPosition.HOUR -> "主子女与晚年，也看志业落点与晚境。"
    }

    // ---------------------------------------------------------- 刑冲合害

    private fun relations(chart: BaziChart, a: AnalysisResult, ids: MutableList<String>): Section {
        if (a.relations.isEmpty()) {
            return Section("刑冲合害", listOf("四柱地支之间无明显的冲、合、刑、害，结构相对平顺。"))
        }
        val paragraphs = a.relations.groupBy { it.type }.map { (type, list) ->
            val detail = list.joinToString("；") { it.note }
            "${type.cn}：$detail。${relationEffect(type)}"
        }
        ids += "relations"
        return Section("刑冲合害", paragraphs)
    }

    private fun relationEffect(type: RelationType): String = when (type) {
        RelationType.LIU_HE -> "合则有情，牵绊中带助力，也主事情不易干净了断。"
        RelationType.SAN_HE -> "三合成局，力量集中，该五行之势大增。"
        RelationType.SAN_HUI -> "三会成方，气势最盛，往往决定命局的主要性格。"
        RelationType.LIU_CHONG -> "冲则动，主动荡、迁移、变动，冲在哪一宫，哪一段人生就容易翻动。"
        RelationType.XIANG_XING -> "刑主纠缠与反复，事情容易绕回原点，宜以规则化解。"
        RelationType.LIU_HAI -> "害主暗损，多为不易察觉的摩擦与损耗。"
    }

    // ---------------------------------------------------------- 大运

    private fun fortune(
        chart: BaziChart,
        a: AnalysisResult,
        referenceYear: Int,
        ids: MutableList<String>
    ): Section {
        val paragraphs = mutableListOf<String>()
        paragraphs += "起运：" + chart.startLuck.text + "，交运于 " +
            "${chart.startLuck.startTime.year} 年 ${chart.startLuck.startTime.monthValue} 月。"

        val current = chart.luckOfYear(referenceYear)
        if (current != null) {
            paragraphs += "当前行 ${current.ganZhi} 运（${current.startAge}–${current.endAge} 岁，" +
                "${current.startYear}–${current.endYear} 年），干为${current.ganTenGod.cn}，支主${current.zhiMainTenGod.cn}。" +
                fortuneHint(current.ganTenGod.category, current.gan.wuXing, a)
            ids += "fortune.current"
        } else {
            paragraphs += "尚未交入首步大运，当前仍属童限阶段。"
        }

        val upcoming = chart.luckPillars.filter { it.startYear > referenceYear }.take(2)
        if (upcoming.isNotEmpty()) {
            paragraphs += upcoming.joinToString("；") {
                "${it.ganZhi} 运（${it.startAge} 岁起）干为${it.ganTenGod.cn}"
            } + "。"
        }

        ids += "fortune.list"
        return Section("大运走势", paragraphs)
    }

    private fun fortuneHint(category: TenGodCategory, element: WuXing, a: AnalysisResult): String {
        val base = when (category) {
            TenGodCategory.SELF -> "此运比劫主事，自主与竞争并存。"
            TenGodCategory.OUTPUT -> "此运食伤主事，宜表达、创作、开拓，需防泄身过度。"
            TenGodCategory.WEALTH -> "此运财星主事，财务机会增多，身弱者需防因财耗身。"
            TenGodCategory.OFFICER -> "此运官杀主事，责任与压力同来，宜正职、资质、管理。"
            TenGodCategory.RESOURCE -> "此运印星主事，宜进修、置业、积累资历。"
        }
        val fit = if (element in a.favorableElements) {
            "运干属${element.cn}，与喜用相合，可为用。"
        } else {
            "运干属${element.cn}，与喜用相背，宜守成待时。"
        }
        return base + fit
    }

    // ---------------------------------------------------------- 免责

    private fun disclaimer(): Section = Section(
        "说明",
        listOf(
            "本盘排算依子平法通行口径，神煞、格局、调候各家取法不一，结果仅供传统文化研究与自我参照。",
            "解读由规则模板生成，不含预测性断言，也不构成任何医疗、投资或人生决策依据。"
        )
    )

    // ---------------------------------------------------------- 工具

    private fun seasonOf(zhi: DiZhi): String = when (zhi) {
        DiZhi.YIN, DiZhi.MAO, DiZhi.CHEN -> "春"
        DiZhi.SI, DiZhi.WU, DiZhi.WEI -> "夏"
        DiZhi.SHEN, DiZhi.YOU, DiZhi.XU -> "秋"
        DiZhi.HAI, DiZhi.ZI, DiZhi.CHOU -> "冬"
    }
}
