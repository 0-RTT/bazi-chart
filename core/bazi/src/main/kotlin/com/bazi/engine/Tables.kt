package com.bazi.engine

/**
 * 藏干。本气、中气、余气三层，权重用于五行力量打分。
 *
 * 权重取 1.0 / 0.5 / 0.2，是常见教学口径的折中值；
 * 不同流派差异集中在余气取舍上，故整体收敛在 [HiddenStemTable] 内，方便后续做成配置。
 */
data class HiddenStem(
    val gan: TianGan,
    val role: String,
    val weight: Double
)

object HiddenStemTable {

    private val TABLE: Map<DiZhi, List<HiddenStem>> = mapOf(
        DiZhi.ZI to listOf(HiddenStem(TianGan.GUI, "本气", 1.0)),
        DiZhi.CHOU to listOf(
            HiddenStem(TianGan.JI, "本气", 1.0),
            HiddenStem(TianGan.GUI, "中气", 0.5),
            HiddenStem(TianGan.XIN, "余气", 0.2)
        ),
        DiZhi.YIN to listOf(
            HiddenStem(TianGan.JIA, "本气", 1.0),
            HiddenStem(TianGan.BING, "中气", 0.5),
            HiddenStem(TianGan.WU, "余气", 0.2)
        ),
        DiZhi.MAO to listOf(HiddenStem(TianGan.YI, "本气", 1.0)),
        DiZhi.CHEN to listOf(
            HiddenStem(TianGan.WU, "本气", 1.0),
            HiddenStem(TianGan.YI, "中气", 0.5),
            HiddenStem(TianGan.GUI, "余气", 0.2)
        ),
        DiZhi.SI to listOf(
            HiddenStem(TianGan.BING, "本气", 1.0),
            HiddenStem(TianGan.GENG, "中气", 0.5),
            HiddenStem(TianGan.WU, "余气", 0.2)
        ),
        DiZhi.WU to listOf(
            HiddenStem(TianGan.DING, "本气", 1.0),
            HiddenStem(TianGan.JI, "中气", 0.5)
        ),
        DiZhi.WEI to listOf(
            HiddenStem(TianGan.JI, "本气", 1.0),
            HiddenStem(TianGan.DING, "中气", 0.5),
            HiddenStem(TianGan.YI, "余气", 0.2)
        ),
        DiZhi.SHEN to listOf(
            HiddenStem(TianGan.GENG, "本气", 1.0),
            HiddenStem(TianGan.REN, "中气", 0.5),
            HiddenStem(TianGan.WU, "余气", 0.2)
        ),
        DiZhi.YOU to listOf(HiddenStem(TianGan.XIN, "本气", 1.0)),
        DiZhi.XU to listOf(
            HiddenStem(TianGan.WU, "本气", 1.0),
            HiddenStem(TianGan.XIN, "中气", 0.5),
            HiddenStem(TianGan.DING, "余气", 0.2)
        ),
        DiZhi.HAI to listOf(
            HiddenStem(TianGan.REN, "本气", 1.0),
            HiddenStem(TianGan.JIA, "中气", 0.5)
        )
    )

    fun of(zhi: DiZhi): List<HiddenStem> = TABLE.getValue(zhi)

    /** 本气天干。 */
    fun mainStem(zhi: DiZhi): TianGan = TABLE.getValue(zhi).first().gan

    /** 支中所有天干。 */
    fun stems(zhi: DiZhi): List<TianGan> = TABLE.getValue(zhi).map { it.gan }
}

/** 纳音。六十甲子两两一组，共三十。 */
object NaYin {

    private val NAMES = arrayOf(
        "海中金", "炉中火", "大林木", "路旁土", "剑锋金",
        "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
        "泉中水", "屋上土", "霹雳火", "松柏木", "长流水",
        "沙中金", "山下火", "平地木", "壁上土", "金箔金",
        "覆灯火", "天河水", "大驿土", "钗钏金", "桑柘木",
        "大溪水", "沙中土", "天上火", "石榴木", "大海水"
    )

    /** @param ganZhiIndex 六十甲子序号 0..59 */
    fun of(ganZhiIndex: Int): String = NAMES[(ganZhiIndex / 2).coerceIn(0, 29)]

    fun of(gan: TianGan, zhi: DiZhi): String = of(ganZhiIndex(gan, zhi))

    /** 由干支反推六十甲子序号。 */
    fun ganZhiIndex(gan: TianGan, zhi: DiZhi): Int {
        var i = 0
        while (i < 60) {
            if (i % 10 == gan.index && i % 12 == zhi.index) return i
            i++
        }
        return 0
    }
}

/**
 * 十二长生。
 *
 * 阳干顺行、阴干逆行，起点为各干的「长生」之地。
 */
enum class GrowthPhase(val cn: String) {
    CHANG_SHENG("长生"), MU_YU("沐浴"), GUAN_DAI("冠带"), LIN_GUAN("临官"),
    DI_WANG("帝旺"), SHUAI("衰"), BING("病"), SI("死"), MU("墓"), JUE("绝"),
    TAI("胎"), YANG("养")
}

object GrowthPhaseTable {

    private val START: Map<TianGan, DiZhi> = mapOf(
        TianGan.JIA to DiZhi.HAI,
        TianGan.YI to DiZhi.WU,
        TianGan.BING to DiZhi.YIN,
        TianGan.DING to DiZhi.YOU,
        TianGan.WU to DiZhi.YIN,
        TianGan.JI to DiZhi.YOU,
        TianGan.GENG to DiZhi.SI,
        TianGan.XIN to DiZhi.ZI,
        TianGan.REN to DiZhi.SHEN,
        TianGan.GUI to DiZhi.MAO
    )

    fun of(gan: TianGan, zhi: DiZhi): GrowthPhase {
        val start = START.getValue(gan).index
        val offset = if (gan.yang) {
            (zhi.index - start + 12) % 12
        } else {
            (start - zhi.index + 12) % 12
        }
        return GrowthPhase.entries[offset]
    }

    /** 是否为有力之位（临官、帝旺、长生）。 */
    fun isStrong(phase: GrowthPhase): Boolean =
        phase == GrowthPhase.LIN_GUAN || phase == GrowthPhase.DI_WANG || phase == GrowthPhase.CHANG_SHENG
}

/** 地支关系的种类。 */
enum class RelationType(val cn: String) {
    LIU_HE("六合"), SAN_HE("三合"), SAN_HUI("三会"),
    LIU_CHONG("六冲"), XIANG_XING("相刑"), LIU_HAI("六害")
}

data class BranchRelation(
    val type: RelationType,
    val branches: List<DiZhi>,
    val positions: List<PillarPosition> = emptyList(),
    val element: WuXing? = null,
    val note: String = ""
)

/**
 * 地支刑冲合害。
 *
 * 全部以数据表表示，避免在业务代码里堆叠条件分支。
 */
object BranchRelations {

    private val LIU_HE = listOf(
        DiZhi.ZI to DiZhi.CHOU, DiZhi.YIN to DiZhi.HAI, DiZhi.MAO to DiZhi.XU,
        DiZhi.CHEN to DiZhi.YOU, DiZhi.SI to DiZhi.SHEN, DiZhi.WU to DiZhi.WEI
    ).map { (a, b) -> setOf(a, b) }

    private val LIU_HAI = listOf(
        DiZhi.ZI to DiZhi.WEI, DiZhi.CHOU to DiZhi.WU, DiZhi.YIN to DiZhi.SI,
        DiZhi.MAO to DiZhi.CHEN, DiZhi.SHEN to DiZhi.HAI, DiZhi.YOU to DiZhi.XU
    ).map { (a, b) -> setOf(a, b) }

    private val SAN_HE = listOf(
        Triple(DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN) to WuXing.SHUI,
        Triple(DiZhi.HAI, DiZhi.MAO, DiZhi.WEI) to WuXing.MU,
        Triple(DiZhi.YIN, DiZhi.WU, DiZhi.XU) to WuXing.HUO,
        Triple(DiZhi.SI, DiZhi.YOU, DiZhi.CHOU) to WuXing.JIN
    )

    private val SAN_HUI = listOf(
        Triple(DiZhi.YIN, DiZhi.MAO, DiZhi.CHEN) to WuXing.MU,
        Triple(DiZhi.SI, DiZhi.WU, DiZhi.WEI) to WuXing.HUO,
        Triple(DiZhi.SHEN, DiZhi.YOU, DiZhi.XU) to WuXing.JIN,
        Triple(DiZhi.HAI, DiZhi.ZI, DiZhi.CHOU) to WuXing.SHUI
    )

    /** 三刑组合（含自刑）。 */
    private val XING = listOf(
        listOf(DiZhi.YIN, DiZhi.SI, DiZhi.SHEN),
        listOf(DiZhi.CHOU, DiZhi.XU, DiZhi.WEI),
        listOf(DiZhi.ZI, DiZhi.MAO)
    )
    private val SELF_XING = setOf(DiZhi.CHEN, DiZhi.WU, DiZhi.YOU, DiZhi.HAI)

    fun between(a: DiZhi, b: DiZhi): List<RelationType> {
        if (a == b) return if (a in SELF_XING) listOf(RelationType.XIANG_XING) else emptyList()
        val out = mutableListOf<RelationType>()
        val pair = setOf(a, b)
        if (pair in LIU_HE) out += RelationType.LIU_HE
        if (pair in LIU_HAI) out += RelationType.LIU_HAI
        if ((a.index - b.index + 12) % 12 == 6) out += RelationType.LIU_CHONG
        if (XING.any { it.contains(a) && it.contains(b) && it.size == 2 }) out += RelationType.XIANG_XING
        return out
    }

    /** 扫描四柱地支，输出全部合会刑冲害。 */
    fun scan(pillars: List<Pair<PillarPosition, DiZhi>>): List<BranchRelation> {
        val result = mutableListOf<BranchRelation>()
        val present = pillars.map { it.second }.toSet()

        for (i in pillars.indices) {
            for (j in i + 1 until pillars.size) {
                val (posA, a) = pillars[i]
                val (posB, b) = pillars[j]
                for (type in between(a, b)) {
                    result += BranchRelation(
                        type = type,
                        branches = listOf(a, b),
                        positions = listOf(posA, posB),
                        note = "${a.cn}${type.cn}${b.cn}"
                    )
                }
            }
        }

        SAN_HE.forEach { (triple, element) ->
            if (present.containsAll(triple.toList())) {
                result += BranchRelation(
                    type = RelationType.SAN_HE,
                    branches = triple.toList(),
                    element = element,
                    note = "三合${element.cn}局"
                )
            }
        }

        SAN_HUI.forEach { (triple, element) ->
            if (present.containsAll(triple.toList())) {
                result += BranchRelation(
                    type = RelationType.SAN_HUI,
                    branches = triple.toList(),
                    element = element,
                    note = "三会${element.cn}方"
                )
            }
        }

        XING.forEach { group ->
            val hit = group.filter { it in present }
            if (hit.size >= 2) {
                result += BranchRelation(
                    type = RelationType.XIANG_XING,
                    branches = hit,
                    note = "三刑"
                )
            }
        }

        return result
    }
}
