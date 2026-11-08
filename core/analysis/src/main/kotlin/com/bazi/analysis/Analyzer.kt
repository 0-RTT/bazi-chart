package com.bazi.analysis

import com.bazi.engine.BaziChart
import com.bazi.engine.BranchRelation
import com.bazi.engine.WuXing

/** 命盘分析总成。 */
data class AnalysisResult(
    val strength: StrengthResult,
    val pattern: PatternResult,
    val tiaohou: TiaohouResult,
    val shenSha: List<ShenShaHit>,
    val relations: List<BranchRelation>,
    val missingElements: List<WuXing>
) {
    /** 喜用倾向：身弱取印比，身强取财官食伤。 */
    val favorableElements: List<WuXing>
        get() {
            val me = strength.dayMasterElement
            return when (strength.level) {
                StrengthLevel.VERY_WEAK, StrengthLevel.WEAK, StrengthLevel.SLIGHTLY_WEAK ->
                    listOf(me.generatedBy(), me)
                StrengthLevel.BALANCED ->
                    listOf(me.generatedBy(), me.generates(), me.overcomes())
                StrengthLevel.SLIGHTLY_STRONG, StrengthLevel.STRONG, StrengthLevel.VERY_STRONG ->
                    // 财（我克）、官杀（克我）、食伤（我生）。官杀必须用 overcomeBy()，
                    // 早前误写成 generatedBy().overcomes()，实际取到的是食伤，官杀从未入选。
                    listOf(me.overcomes(), me.overcomeBy(), me.generates())
            }
        }
}

/** 分析入口。只算一次打分，避免重复计算。 */
object ChartAnalyzer {

    fun analyze(chart: BaziChart): AnalysisResult {
        val strength = ElementScorer.score(chart)
        return AnalysisResult(
            strength = strength,
            pattern = PatternAnalyzer.analyze(chart),
            tiaohou = TiaohouAnalyzer.analyze(chart),
            shenSha = ShenShaAnalyzer.analyze(chart),
            relations = chart.relations,
            missingElements = ElementScorer.missingElements(strength)
        )
    }
}
