package com.bazi.analysis

import com.bazi.engine.BaziChart
import com.bazi.engine.DiZhi
import com.bazi.engine.PillarPosition
import com.bazi.engine.TianGan

enum class ShenShaCategory(val cn: String) {
    AUSPICIOUS("吉神"), INAUSPICIOUS("凶煞"), NEUTRAL("中性")
}

data class ShenShaHit(
    val name: String,
    val category: ShenShaCategory,
    val positions: List<PillarPosition>,
    val note: String
) {
    val positionText: String get() = positions.joinToString("、") { it.cn }
}

/**
 * 神煞。
 *
 * 神煞名目繁多、查法各异，是流派分歧最集中的区域，因此**全部表驱动**：
 * 增加一个神煞 = 增加一张表，不动判定逻辑。
 */
object ShenShaAnalyzer {

    private val TIAN_YI = mapOf(
        TianGan.JIA to listOf(DiZhi.CHOU, DiZhi.WEI),
        TianGan.WU to listOf(DiZhi.CHOU, DiZhi.WEI),
        TianGan.GENG to listOf(DiZhi.CHOU, DiZhi.WEI),
        TianGan.YI to listOf(DiZhi.ZI, DiZhi.SHEN),
        TianGan.JI to listOf(DiZhi.ZI, DiZhi.SHEN),
        TianGan.BING to listOf(DiZhi.HAI, DiZhi.YOU),
        TianGan.DING to listOf(DiZhi.HAI, DiZhi.YOU),
        TianGan.REN to listOf(DiZhi.MAO, DiZhi.SI),
        TianGan.GUI to listOf(DiZhi.MAO, DiZhi.SI),
        TianGan.XIN to listOf(DiZhi.WU, DiZhi.YIN)
    )

    private val WEN_CHANG = mapOf(
        TianGan.JIA to DiZhi.SI, TianGan.YI to DiZhi.WU,
        TianGan.BING to DiZhi.SHEN, TianGan.WU to DiZhi.SHEN,
        TianGan.DING to DiZhi.YOU, TianGan.JI to DiZhi.YOU,
        TianGan.GENG to DiZhi.HAI, TianGan.XIN to DiZhi.ZI,
        TianGan.REN to DiZhi.YIN, TianGan.GUI to DiZhi.MAO
    )

    private val YANG_REN = mapOf(
        TianGan.JIA to DiZhi.MAO, TianGan.YI to DiZhi.YIN,
        TianGan.BING to DiZhi.WU, TianGan.WU to DiZhi.WU,
        TianGan.DING to DiZhi.SI, TianGan.JI to DiZhi.SI,
        TianGan.GENG to DiZhi.YOU, TianGan.XIN to DiZhi.SHEN,
        TianGan.REN to DiZhi.ZI, TianGan.GUI to DiZhi.HAI
    )

    private val LU_SHEN = mapOf(
        TianGan.JIA to DiZhi.YIN, TianGan.YI to DiZhi.MAO,
        TianGan.BING to DiZhi.SI, TianGan.WU to DiZhi.SI,
        TianGan.DING to DiZhi.WU, TianGan.JI to DiZhi.WU,
        TianGan.GENG to DiZhi.SHEN, TianGan.XIN to DiZhi.YOU,
        TianGan.REN to DiZhi.HAI, TianGan.GUI to DiZhi.ZI
    )

    /** 以年支（兼日支）三合局为纲的四类神煞。 */
    private data class GroupRule(
        val peach: DiZhi,
        val horse: DiZhi,
        val canopy: DiZhi,
        val general: DiZhi
    )

    private val GROUPS = listOf(
        setOf(DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN) to GroupRule(DiZhi.YOU, DiZhi.YIN, DiZhi.CHEN, DiZhi.ZI),
        setOf(DiZhi.YIN, DiZhi.WU, DiZhi.XU) to GroupRule(DiZhi.MAO, DiZhi.SHEN, DiZhi.XU, DiZhi.WU),
        setOf(DiZhi.SI, DiZhi.YOU, DiZhi.CHOU) to GroupRule(DiZhi.WU, DiZhi.HAI, DiZhi.CHOU, DiZhi.YOU),
        setOf(DiZhi.HAI, DiZhi.MAO, DiZhi.WEI) to GroupRule(DiZhi.ZI, DiZhi.SI, DiZhi.WEI, DiZhi.MAO)
    )

    private val YUE_DE = mapOf(
        setOf(DiZhi.YIN, DiZhi.WU, DiZhi.XU) to TianGan.BING,
        setOf(DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN) to TianGan.REN,
        setOf(DiZhi.HAI, DiZhi.MAO, DiZhi.WEI) to TianGan.JIA,
        setOf(DiZhi.SI, DiZhi.YOU, DiZhi.CHOU) to TianGan.GENG
    )

    fun analyze(chart: BaziChart): List<ShenShaHit> {
        val hits = mutableListOf<ShenShaHit>()
        val branches = chart.pillars.map { it.position to it.zhi }
        val dayMaster = chart.dayMaster

        fun collect(name: String, category: ShenShaCategory, targets: List<DiZhi>, note: String) {
            val positions = branches.filter { it.second in targets }.map { it.first }
            if (positions.isNotEmpty()) hits += ShenShaHit(name, category, positions, note)
        }

        TIAN_YI[dayMaster]?.let { collect("天乙贵人", ShenShaCategory.AUSPICIOUS, it, "以日干${dayMaster.cn}查") }
        WEN_CHANG[dayMaster]?.let { collect("文昌", ShenShaCategory.AUSPICIOUS, listOf(it), "以日干${dayMaster.cn}查") }
        LU_SHEN[dayMaster]?.let { collect("禄神", ShenShaCategory.AUSPICIOUS, listOf(it), "以日干${dayMaster.cn}查") }
        YANG_REN[dayMaster]?.let { collect("羊刃", ShenShaCategory.INAUSPICIOUS, listOf(it), "以日干${dayMaster.cn}查") }

        val groupBases = listOfNotNull(
            chart.yearPillar.zhi,
            chart.dayPillar.zhi
        )
        val matchedRules = GROUPS.filter { (keys, _) -> groupBases.any { it in keys } }.map { it.second }
        matchedRules.forEach { rule ->
            collect("桃花", ShenShaCategory.NEUTRAL, listOf(rule.peach), "依三合局查")
            collect("驿马", ShenShaCategory.NEUTRAL, listOf(rule.horse), "依三合局查")
            collect("华盖", ShenShaCategory.NEUTRAL, listOf(rule.canopy), "依三合局查")
            collect("将星", ShenShaCategory.AUSPICIOUS, listOf(rule.general), "依三合局查")
        }

        YUE_DE.entries.firstOrNull { (keys, _) -> chart.monthCommand in keys }
            ?.value
            ?.let { gan ->
                val positions = chart.pillars.filter { it.gan == gan }.map { it.position }
                if (positions.isNotEmpty()) {
                    hits += ShenShaHit("月德贵人", ShenShaCategory.AUSPICIOUS, positions, "月令见${gan.cn}")
                }
            }

        val voidPositions = chart.pillars.filter { it.isEmptyBranch }.map { it.position }
        if (voidPositions.isNotEmpty()) {
            hits += ShenShaHit(
                name = "空亡",
                category = ShenShaCategory.NEUTRAL,
                positions = voidPositions,
                note = "日柱旬空：${chart.voidBranches.joinToString("、") { it.cn }}"
            )
        }

        return hits
    }
}
