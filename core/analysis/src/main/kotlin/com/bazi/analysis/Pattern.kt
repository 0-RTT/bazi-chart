package com.bazi.analysis

import com.bazi.engine.BaziChart
import com.bazi.engine.GrowthPhase
import com.bazi.engine.GrowthPhaseTable
import com.bazi.engine.HiddenStemTable
import com.bazi.engine.TenGod

/** 格局判定结果。 */
data class PatternResult(
    val name: String,
    val tenGod: TenGod?,
    /** 取格依据，逐项说明为什么是这个格。 */
    val basis: String,
    /** 是否由月令透干而成格（成格较实）。 */
    val transparent: Boolean,
    /** 建禄、羊刃等特殊格。 */
    val special: Boolean
) {
    val displayName: String
        get() = if (name.isBlank()) "未成格" else name
}

/**
 * 格局判定。
 *
 * 以月令为纲，取格优先序显式化：
 * 1. 月令本气与日主同五行 → 建禄 / 羊刃 / 月劫（禄刃格不入普通八格）
 * 2. 月令本气透干 → 以本气十神取格
 * 3. 月令中气透干 → 以中气十神取格
 * 4. 皆不透 → 以月令本气十神论
 *
 * 各家分歧主要就在这个优先序上，故把它写成显式分支而非隐式规则。
 */
object PatternAnalyzer {

    private val PATTERN_NAMES = mapOf(
        TenGod.ZHENG_GUAN to "正官格",
        TenGod.QI_SHA to "七杀格",
        TenGod.ZHENG_CAI to "正财格",
        TenGod.PIAN_CAI to "偏财格",
        TenGod.SHI_SHEN to "食神格",
        TenGod.SHANG_GUAN to "伤官格",
        TenGod.ZHENG_YIN to "正印格",
        TenGod.PIAN_YIN to "偏印格"
    )

    fun analyze(chart: BaziChart): PatternResult {
        val dayMaster = chart.dayMaster
        val monthZhi = chart.monthCommand
        val monthPhase = GrowthPhaseTable.of(dayMaster, monthZhi)
        val mainStem = HiddenStemTable.mainStem(monthZhi)
        val stems = HiddenStemTable.of(monthZhi)
        val visibleGans = chart.pillars.map { it.gan }

        if (mainStem.wuXing == dayMaster.wuXing) {
            val (name, why) = when (monthPhase) {
                GrowthPhase.LIN_GUAN -> "建禄格" to "月令${monthZhi.cn}为日主临官之地"
                GrowthPhase.DI_WANG -> "羊刃格" to "月令${monthZhi.cn}为日主帝旺之地"
                else -> "月劫格" to "月令${monthZhi.cn}本气${mainStem.cn}与日主同五行"
            }
            return PatternResult(
                name = name,
                tenGod = TenGod.between(dayMaster, mainStem),
                basis = why,
                transparent = mainStem in visibleGans,
                special = true
            )
        }

        val mainGod = TenGod.between(dayMaster, mainStem)
        if (mainStem in visibleGans) {
            return PatternResult(
                name = PATTERN_NAMES[mainGod] ?: "${mainGod.cn}格",
                tenGod = mainGod,
                basis = "月令${monthZhi.cn}本气${mainStem.cn}透干，取${mainGod.cn}格",
                transparent = true,
                special = false
            )
        }

        val middle = stems.getOrNull(1)
        if (middle != null && middle.gan in visibleGans) {
            val god = TenGod.between(dayMaster, middle.gan)
            if (god.category != TenGod.BI_JIAN.category) {
                return PatternResult(
                    name = PATTERN_NAMES[god] ?: "${god.cn}格",
                    tenGod = god,
                    basis = "月令${monthZhi.cn}中气${middle.gan.cn}透干，取${god.cn}格",
                    transparent = true,
                    special = false
                )
            }
        }

        return PatternResult(
            name = PATTERN_NAMES[mainGod] ?: "${mainGod.cn}格",
            tenGod = mainGod,
            basis = "月令${monthZhi.cn}本气${mainStem.cn}不透，以本气${mainGod.cn}论格",
            transparent = false,
            special = false
        )
    }
}
