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
    LIU_HE("六合"), SAN_HE("三合"), BAN_HE("半合"), SAN_HUI("三会"),
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

    /** 三合局，顺序为「生、旺、墓」，中间一支是旺神，半合必须含它。 */
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

    /**
     * 三刑两组：寅巳申、丑戌未。
     *
     * 组内**任取两支**即构成相刑，不必三支俱全。旧实现把判定写成「数组长度等于 2」，
     * 于是只有子卯这一组能命中，寅巳、巳申、寅申、丑戌、戌未、丑未六对全被跳过——
     * 这就是「刑冲合害一栏不全」的根源。
     */
    private val SAN_XING = listOf(
        Triple(DiZhi.YIN, DiZhi.SI, DiZhi.SHEN),
        Triple(DiZhi.CHOU, DiZhi.XU, DiZhi.WEI)
    )

    /** 子卯相刑，只有两支，无第三支可补。 */
    private val ZI_MAO = setOf(DiZhi.ZI, DiZhi.MAO)

    /** 自刑：辰午酉亥四支，重现即成。 */
    private val SELF_XING = setOf(DiZhi.CHEN, DiZhi.WU, DiZhi.YOU, DiZhi.HAI)

    fun between(a: DiZhi, b: DiZhi): List<RelationType> {
        if (a == b) return if (a in SELF_XING) listOf(RelationType.XIANG_XING) else emptyList()
        val out = mutableListOf<RelationType>()
        val pair = setOf(a, b)
        if (pair in LIU_HE) out += RelationType.LIU_HE
        if (pair in LIU_HAI) out += RelationType.LIU_HAI
        if ((a.index - b.index + 12) % 12 == 6) out += RelationType.LIU_CHONG
        if (pair == ZI_MAO) out += RelationType.XIANG_XING
        if (SAN_XING.any { a in it.toList() && b in it.toList() }) out += RelationType.XIANG_XING
        return out
    }

    /** 扫描四柱地支，输出全部合会刑冲害。 */
    fun scan(pillars: List<Pair<PillarPosition, DiZhi>>): List<BranchRelation> {
        val result = mutableListOf<BranchRelation>()
        val present = pillars.map { it.second }.toSet()

        fun positionsOf(branches: List<DiZhi>): List<PillarPosition> =
            pillars.filter { it.second in branches }.map { it.first }

        // ---- 两两关系：六合、六冲、六害、子卯刑、三刑组内两两
        for (i in pillars.indices) {
            for (j in i + 1 until pillars.size) {
                val (posA, a) = pillars[i]
                val (posB, b) = pillars[j]
                if (a == b) continue // 同支另按自刑处理，避免与自刑重复
                for (type in between(a, b)) {
                    result += BranchRelation(
                        type = type,
                        branches = listOf(a, b),
                        positions = listOf(posA, posB),
                        note = pairNote(a, b, type)
                    )
                }
            }
        }

        // ---- 三刑俱全：三支齐备时给一条整体记录，并吸收同组的两两记录
        val fullXing = mutableListOf<List<DiZhi>>()
        SAN_XING.forEach { triple ->
            val hit = triple.toList().filter { it in present }
            if (hit.size == 3) {
                fullXing += hit
                result += BranchRelation(
                    type = RelationType.XIANG_XING,
                    branches = hit,
                    positions = positionsOf(hit),
                    note = hit.joinToString("") { it.cn } + "三刑俱全"
                )
            }
        }
        result.removeAll { rel ->
            rel.type == RelationType.XIANG_XING && rel.branches.size == 2 &&
                fullXing.any { it.containsAll(rel.branches) }
        }

        // ---- 自刑：同一支出现两次以上
        val counts = pillars.groupingBy { it.second }.eachCount()
        SELF_XING.forEach { zhi ->
            val n = counts[zhi] ?: 0
            if (n >= 2) {
                result += BranchRelation(
                    type = RelationType.XIANG_XING,
                    branches = List(n) { zhi },
                    positions = positionsOf(listOf(zhi)),
                    note = zhi.cn + zhi.cn + "自刑"
                )
            }
        }

        // ---- 三合局；不成局时检半合（半合必含旺神）
        SAN_HE.forEach { (triple, element) ->
            val t = triple.toList()
            if (present.containsAll(t)) {
                result += BranchRelation(
                    type = RelationType.SAN_HE,
                    branches = t,
                    positions = positionsOf(t),
                    element = element,
                    note = "三合" + element.cn + "局"
                )
            } else {
                listOf(t[0] to t[1], t[1] to t[2]).forEach { (x, y) ->
                    if (x in present && y in present) {
                        result += BranchRelation(
                            type = RelationType.BAN_HE,
                            branches = listOf(x, y),
                            positions = positionsOf(listOf(x, y)),
                            element = element,
                            note = x.cn + y.cn + "半合" + element.cn + "局"
                        )
                    }
                }
            }
        }

        // ---- 三会方
        SAN_HUI.forEach { (triple, element) ->
            if (present.containsAll(triple.toList())) {
                result += BranchRelation(
                    type = RelationType.SAN_HUI,
                    branches = triple.toList(),
                    positions = positionsOf(triple.toList()),
                    element = element,
                    note = "三会" + element.cn + "方"
                )
            }
        }

        return result
    }

    /** 二元关系的措辞：把「相冲」挂在两支之后，读作「子午相冲」而不是「子相冲午」。 */
    private fun pairNote(a: DiZhi, b: DiZhi, type: RelationType): String = when (type) {
        RelationType.LIU_HE -> a.cn + b.cn + "六合"
        RelationType.LIU_CHONG -> a.cn + b.cn + "相冲"
        RelationType.LIU_HAI -> a.cn + b.cn + "相害"
        RelationType.XIANG_XING -> a.cn + b.cn + "相刑"
        else -> a.cn + b.cn + type.cn
    }
}
