package com.bazi.analysis

import com.bazi.engine.BaziChart
import com.bazi.engine.DiZhi
import com.bazi.engine.GrowthPhase
import com.bazi.engine.GrowthPhaseTable
import com.bazi.engine.HiddenStemTable
import com.bazi.engine.PillarPosition
import com.bazi.engine.TianGan
import com.bazi.engine.WuXing

/** 单条加减分明细。UI 逐条展示，保证结论可追溯。 */
data class ScoreItem(
    val label: String,
    val wuXing: WuXing,
    val delta: Double
)

data class ElementScore(
    val wuXing: WuXing,
    val score: Double,
    val percent: Double
)

enum class StrengthLevel(val cn: String) {
    VERY_WEAK("极弱"), WEAK("弱"), SLIGHTLY_WEAK("偏弱"), BALANCED("中和"),
    SLIGHTLY_STRONG("偏强"), STRONG("强"), VERY_STRONG("极强")
}

data class StrengthResult(
    val dayMasterElement: WuXing,
    /** 比劫 + 印 的得分，即「帮身之力」。 */
    val supportScore: Double,
    val totalScore: Double,
    val ratio: Double,
    val level: StrengthLevel,
    /** 得令：月令生扶日主。 */
    val hasCommand: Boolean,
    /** 得地：日支生扶日主。 */
    val hasGround: Boolean,
    /** 得势：天干中帮身者达两位以上。 */
    val hasMomentum: Boolean,
    val elementScores: List<ElementScore>,
    val items: List<ScoreItem>
) {
    val summary: String
        get() = buildString {
            append("日主")
            append(level.cn)
            append("（帮身占 ")
            append("%.1f".format(ratio * 100))
            append("%）")
            if (hasCommand) append("·得令")
            if (hasGround) append("·得地")
            if (hasMomentum) append("·得势")
        }
}

/**
 * 五行力量打分。
 *
 * 权重口径：天干各 1.0；地支按位置加权（月支 3.0 司权最重、日支 2.0、年时支各 1.0），
 * 支内再按藏干本气/中气/余气折算。这套权重是教学通行口径，
 * 关键在于**每一项都可解释**，而不是给一个黑盒分数。
 */
object ElementScorer {

    private const val GAN_WEIGHT = 1.0

    private val ZHI_WEIGHT = mapOf(
        PillarPosition.YEAR to 1.0,
        PillarPosition.MONTH to 3.0,
        PillarPosition.DAY to 2.0,
        PillarPosition.HOUR to 1.0
    )

    fun score(chart: BaziChart): StrengthResult {
        val items = mutableListOf<ScoreItem>()

        chart.pillars.forEach { pillar ->
            items += ScoreItem(
                label = "${pillar.position.cn}干 ${pillar.gan.cn}",
                wuXing = pillar.gan.wuXing,
                delta = GAN_WEIGHT
            )
            val zhiWeight = ZHI_WEIGHT.getValue(pillar.position)
            HiddenStemTable.of(pillar.zhi).forEach { hidden ->
                items += ScoreItem(
                    label = "${pillar.position.cn}支 ${pillar.zhi.cn}藏${hidden.gan.cn}(${hidden.role})",
                    wuXing = hidden.gan.wuXing,
                    delta = hidden.weight * zhiWeight
                )
            }
        }

        val byElement = WuXing.entries.associateWith { wx ->
            items.filter { it.wuXing == wx }.sumOf { it.delta }
        }
        val total = byElement.values.sum().coerceAtLeast(0.0001)

        val me = chart.dayMaster.wuXing
        val support = byElement.getValue(me) + byElement.getValue(me.generatedBy())
        val ratio = support / total

        val level = when {
            ratio < 0.30 -> StrengthLevel.VERY_WEAK
            ratio < 0.42 -> StrengthLevel.WEAK
            ratio < 0.48 -> StrengthLevel.SLIGHTLY_WEAK
            ratio <= 0.52 -> StrengthLevel.BALANCED
            ratio <= 0.58 -> StrengthLevel.SLIGHTLY_STRONG
            ratio <= 0.70 -> StrengthLevel.STRONG
            else -> StrengthLevel.VERY_STRONG
        }

        val monthMain = HiddenStemTable.mainStem(chart.monthCommand).wuXing
        val dayMain = HiddenStemTable.mainStem(chart.dayPillar.zhi).wuXing

        val supportiveGanCount = chart.pillars
            .filter { it.position != PillarPosition.DAY }
            .count { it.gan.wuXing == me || it.gan.wuXing == me.generatedBy() }

        return StrengthResult(
            dayMasterElement = me,
            supportScore = support,
            totalScore = total,
            ratio = ratio,
            level = level,
            hasCommand = monthMain == me || monthMain == me.generatedBy(),
            hasGround = dayMain == me || dayMain == me.generatedBy(),
            hasMomentum = supportiveGanCount >= 2,
            elementScores = WuXing.entries.map {
                ElementScore(it, byElement.getValue(it), byElement.getValue(it) / total)
            },
            items = items
        )
    }

    /** 五行缺失（用于提示，不作吉凶判定）。 */
    fun missingElements(result: StrengthResult): List<WuXing> =
        result.elementScores.filter { it.percent < 0.02 }.map { it.wuXing }
}

/** 日主在该支的长生状态，供 UI 直接取用。 */
fun growthPhaseOf(dayMaster: TianGan, zhi: DiZhi): GrowthPhase =
    GrowthPhaseTable.of(dayMaster, zhi)
