package com.bazi.analysis

import com.bazi.calendar.Gender
import com.bazi.engine.BaziChart
import com.bazi.engine.DiZhi
import com.bazi.engine.PillarPosition
import com.bazi.engine.TianGan
import com.bazi.engine.WuXing

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

/** 单柱上的神煞，用于大运、流年、流月等没有宫位可言的柱。 */
data class ShenShaMark(
    val name: String,
    val category: ShenShaCategory,
    val note: String
)

/**
 * 神煞所需的全部输入。
 *
 * 神煞只依赖八字本身、年柱纳音与性别，与出生时刻、地点都无关，
 * 因此单独抽出来：一是便于按四柱直接回归（不必先排盘），
 * 二是明确了「运柱能查什么」——凡挂 [scope] 的规则（如阴差阳错只论日柱）
 * 不参与运柱查询。
 */
data class ShenShaInput(
    val yearGan: TianGan, val yearZhi: DiZhi,
    val monthGan: TianGan, val monthZhi: DiZhi,
    val dayGan: TianGan, val dayZhi: DiZhi,
    val hourGan: TianGan, val hourZhi: DiZhi,
    /** 年柱纳音五行，童子煞依此取支。 */
    val yearNaYin: WuXing?,
    val male: Boolean,
    /** 日柱旬空两支。 */
    val voidBranches: List<DiZhi>
) {
    /** 四柱按宫位排列，扫描时顺序固定，输出顺序才稳定。 */
    val pillars: List<Triple<PillarPosition, TianGan, DiZhi>>
        get() = listOf(
            Triple(PillarPosition.YEAR, yearGan, yearZhi),
            Triple(PillarPosition.MONTH, monthGan, monthZhi),
            Triple(PillarPosition.DAY, dayGan, dayZhi),
            Triple(PillarPosition.HOUR, hourGan, hourZhi)
        )

    companion object {
        fun of(chart: BaziChart): ShenShaInput {
            val y = chart.yearPillar
            val m = chart.monthPillar
            val d = chart.dayPillar
            val h = chart.hourPillar
            return ShenShaInput(
                yearGan = y.gan, yearZhi = y.zhi,
                monthGan = m.gan, monthZhi = m.zhi,
                dayGan = d.gan, dayZhi = d.zhi,
                hourGan = h.gan, hourZhi = h.zhi,
                yearNaYin = naYinElement(y.naYin),
                male = chart.input.gender == Gender.MALE,
                voidBranches = chart.voidBranches
            )
        }

        /** 纳音名一律以五行字收尾（城头土、剑锋金），据末字取五行。 */
        fun naYinElement(name: String): WuXing? =
            WuXing.entries.firstOrNull { name.endsWith(it.cn) }
    }
}

/** 地支前后偏移，供勾绞煞取「命前三辰、命后三辰」。 */
private fun DiZhi.shift(n: Int): DiZhi = DiZhi.entries[((index + n) % 12 + 12) % 12]

/**
 * 神煞。
 *
 * 名目繁多、查法各异，是流派分歧最集中的区域，因此**全部表驱动**：
 * 加一个神煞 = 加一条规则，不动判定逻辑。
 *
 * 本表的取法以常见排盘工具的实际输出为准，逐条注明基准字，
 * 便于日后与别的口径对照时定位差异。
 *
 * 三处容易错的，单独说明：
 *
 * 1. **将星不收**。将星取三合局中神（寅午戌见午），而查法基准支自己
 *    必是四柱之一，于是「年支取年支、日支取日支」恒中两柱，噪声大于信息。
 *    常见工具亦不列此项。
 * 2. **天喜只以年支查**，不以日支查。若兼看日支，本例年柱会多出一个天喜，
 *    与通行结果不符。
 * 3. **童子煞按年柱纳音取**（金木马卯、水火鸡犬、土命辰巳），不看季节。
 *    季节口径（春秋寅子、冬夏卯未辰）在本例得出不同答案，非通行取法。
 */
object ShenShaAnalyzer {

    // ---------------------------------------------------------------- 表

    /** 太极贵人：甲乙子午、丙丁卯酉、戊己四季、庚辛寅亥、壬癸巳申。 */
    private val TAI_JI: Map<TianGan, List<DiZhi>> = mapOf(
        TianGan.JIA to listOf(DiZhi.ZI, DiZhi.WU),
        TianGan.YI to listOf(DiZhi.ZI, DiZhi.WU),
        TianGan.BING to listOf(DiZhi.MAO, DiZhi.YOU),
        TianGan.DING to listOf(DiZhi.MAO, DiZhi.YOU),
        TianGan.WU to listOf(DiZhi.CHEN, DiZhi.XU, DiZhi.CHOU, DiZhi.WEI),
        TianGan.JI to listOf(DiZhi.CHEN, DiZhi.XU, DiZhi.CHOU, DiZhi.WEI),
        TianGan.GENG to listOf(DiZhi.YIN, DiZhi.HAI),
        TianGan.XIN to listOf(DiZhi.YIN, DiZhi.HAI),
        TianGan.REN to listOf(DiZhi.SI, DiZhi.SHEN),
        TianGan.GUI to listOf(DiZhi.SI, DiZhi.SHEN)
    )

    private val TIAN_YI: Map<TianGan, List<DiZhi>> = mapOf(
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

    /**
     * 天德贵人：按月支取，所取之物或为天干、或为地支。
     * 寅丁、卯申、辰壬、巳辛、午亥、未甲、申癸、酉寅、戌丙、亥乙、子巳、丑庚。
     */
    private val TIAN_DE_GAN: Map<DiZhi, TianGan> = mapOf(
        DiZhi.YIN to TianGan.DING,
        DiZhi.CHEN to TianGan.REN,
        DiZhi.SI to TianGan.XIN,
        DiZhi.WEI to TianGan.JIA,
        DiZhi.SHEN to TianGan.GUI,
        DiZhi.XU to TianGan.BING,
        DiZhi.HAI to TianGan.YI,
        DiZhi.CHOU to TianGan.GENG
    )
    private val TIAN_DE_ZHI: Map<DiZhi, DiZhi> = mapOf(
        DiZhi.MAO to DiZhi.SHEN,
        DiZhi.WU to DiZhi.HAI,
        DiZhi.YOU to DiZhi.YIN,
        DiZhi.ZI to DiZhi.SI
    )

    /** 月德贵人：寅午戌见丙、申子辰见壬、亥卯未见甲、巳酉丑见庚。 */
    private val YUE_DE: Map<DiZhi, TianGan> = mapOf(
        DiZhi.YIN to TianGan.BING, DiZhi.WU to TianGan.BING, DiZhi.XU to TianGan.BING,
        DiZhi.SHEN to TianGan.REN, DiZhi.ZI to TianGan.REN, DiZhi.CHEN to TianGan.REN,
        DiZhi.HAI to TianGan.JIA, DiZhi.MAO to TianGan.JIA, DiZhi.WEI to TianGan.JIA,
        DiZhi.SI to TianGan.GENG, DiZhi.YOU to TianGan.GENG, DiZhi.CHOU to TianGan.GENG
    )

    /**
     * 德秀贵人：按月支三合局取「德」「秀」两组天干，命中任一即成。
     *
     * 寅午戌月德丙丁、秀戊癸；申子辰月德壬癸戊己、秀丙辛甲己；
     * 巳酉丑月德庚辛、秀乙庚；亥卯未月德甲乙、秀丁壬。
     */
    private val DE_XIU: Map<DiZhi, List<TianGan>> = buildMap {
        listOf(DiZhi.YIN, DiZhi.WU, DiZhi.XU).forEach {
            put(it, listOf(TianGan.BING, TianGan.DING, TianGan.WU, TianGan.GUI))
        }
        listOf(DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN).forEach {
            put(it, listOf(TianGan.REN, TianGan.GUI, TianGan.WU, TianGan.JI, TianGan.BING, TianGan.XIN, TianGan.JIA))
        }
        listOf(DiZhi.SI, DiZhi.YOU, DiZhi.CHOU).forEach {
            put(it, listOf(TianGan.GENG, TianGan.XIN, TianGan.YI))
        }
        listOf(DiZhi.HAI, DiZhi.MAO, DiZhi.WEI).forEach {
            put(it, listOf(TianGan.JIA, TianGan.YI, TianGan.DING, TianGan.REN))
        }
    }

    private val WEN_CHANG: Map<TianGan, DiZhi> = mapOf(
        TianGan.JIA to DiZhi.SI, TianGan.YI to DiZhi.WU,
        TianGan.BING to DiZhi.SHEN, TianGan.WU to DiZhi.SHEN,
        TianGan.DING to DiZhi.YOU, TianGan.JI to DiZhi.YOU,
        TianGan.GENG to DiZhi.HAI, TianGan.XIN to DiZhi.ZI,
        TianGan.REN to DiZhi.YIN, TianGan.GUI to DiZhi.MAO
    )

    private val LU_SHEN: Map<TianGan, DiZhi> = mapOf(
        TianGan.JIA to DiZhi.YIN, TianGan.YI to DiZhi.MAO,
        TianGan.BING to DiZhi.SI, TianGan.WU to DiZhi.SI,
        TianGan.DING to DiZhi.WU, TianGan.JI to DiZhi.WU,
        TianGan.GENG to DiZhi.SHEN, TianGan.XIN to DiZhi.YOU,
        TianGan.REN to DiZhi.HAI, TianGan.GUI to DiZhi.ZI
    )

    private val YANG_REN: Map<TianGan, DiZhi> = mapOf(
        TianGan.JIA to DiZhi.MAO, TianGan.YI to DiZhi.YIN,
        TianGan.BING to DiZhi.WU, TianGan.WU to DiZhi.WU,
        TianGan.DING to DiZhi.SI, TianGan.JI to DiZhi.SI,
        TianGan.GENG to DiZhi.YOU, TianGan.XIN to DiZhi.SHEN,
        TianGan.REN to DiZhi.ZI, TianGan.GUI to DiZhi.HAI
    )

    /** 年支三合局为纲：桃花、驿马、华盖。 */
    private data class GroupRule(val peach: DiZhi, val horse: DiZhi, val canopy: DiZhi)

    private val GROUPS: List<Pair<Set<DiZhi>, GroupRule>> = listOf(
        setOf(DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN) to GroupRule(DiZhi.YOU, DiZhi.YIN, DiZhi.CHEN),
        setOf(DiZhi.YIN, DiZhi.WU, DiZhi.XU) to GroupRule(DiZhi.MAO, DiZhi.SHEN, DiZhi.XU),
        setOf(DiZhi.SI, DiZhi.YOU, DiZhi.CHOU) to GroupRule(DiZhi.WU, DiZhi.HAI, DiZhi.CHOU),
        setOf(DiZhi.HAI, DiZhi.MAO, DiZhi.WEI) to GroupRule(DiZhi.ZI, DiZhi.SI, DiZhi.WEI)
    )

    /** 天喜：只以年支查。子酉、丑申、寅未、卯午、辰巳、巳辰、午卯、未寅、申丑、酉子、戌亥、亥戌。 */
    private val TIAN_XI: Map<DiZhi, DiZhi> = mapOf(
        DiZhi.ZI to DiZhi.YOU, DiZhi.CHOU to DiZhi.SHEN,
        DiZhi.YIN to DiZhi.WEI, DiZhi.MAO to DiZhi.WU,
        DiZhi.CHEN to DiZhi.SI, DiZhi.SI to DiZhi.CHEN,
        DiZhi.WU to DiZhi.MAO, DiZhi.WEI to DiZhi.YIN,
        DiZhi.SHEN to DiZhi.CHOU, DiZhi.YOU to DiZhi.ZI,
        DiZhi.XU to DiZhi.HAI, DiZhi.HAI to DiZhi.XU
    )

    /** 阴差阳错：只看日柱。 */
    private val YIN_CHA = setOf(
        "丙子", "丁丑", "戊寅", "辛卯", "壬辰", "癸巳",
        "丙午", "丁未", "戊申", "辛酉", "壬戌", "癸亥"
    )

    /** 六秀日：只看日柱。 */
    private val LIU_XIU = setOf("丙午", "丁未", "戊子", "戊午", "己丑", "己未")

    /** 孤鸾煞：只看日柱。 */
    private val GU_LUAN = setOf(
        "乙巳", "丁巳", "辛亥", "戊申", "甲寅", "戊午", "壬子", "丙午"
    )

    // ---------------------------------------------------------------- 规则

    /** 一条查法：[scope] 非空表示只论某几宫，运柱不查。 */
    private class Rule(
        val name: String,
        val category: ShenShaCategory,
        val note: String,
        val scope: Set<PillarPosition>? = null,
        val match: (TianGan, DiZhi) -> Boolean
    )

    private val DAY_ONLY = setOf(PillarPosition.DAY)

    private fun rulesOf(input: ShenShaInput): List<Rule> {
        val out = mutableListOf<Rule>()
        val dm = input.dayGan
        val month = input.monthZhi

        // ---- 太极贵人：年干、日干双查，取并集。
        // 本例年干己取四季、日干丙取卯酉，合并后年、月、时三柱皆中，日柱不中。
        run {
            val targets = (TAI_JI[input.yearGan].orEmpty() + TAI_JI[dm].orEmpty()).distinct()
            if (targets.isNotEmpty()) {
                out += Rule("太极贵人", ShenShaCategory.AUSPICIOUS, "年干${input.yearGan.cn}、日干${dm.cn}并查") {
                    _, z -> z in targets
                }
            }
        }

        TIAN_YI[dm]?.let { targets ->
            out += Rule("天乙贵人", ShenShaCategory.AUSPICIOUS, "以日干${dm.cn}查") { _, z -> z in targets }
        }

        // ---- 天德贵人：按月令取，所取或干或支。
        run {
            val gan = TIAN_DE_GAN[month]
            val zhi = TIAN_DE_ZHI[month]
            if (gan != null || zhi != null) {
                val label = gan?.cn ?: zhi!!.cn
                out += Rule("天德贵人", ShenShaCategory.AUSPICIOUS, "月令${month.cn}见${label}") { g, z ->
                    (gan != null && g == gan) || (zhi != null && z == zhi)
                }
            }
        }

        YUE_DE[month]?.let { g ->
            out += Rule("月德贵人", ShenShaCategory.AUSPICIOUS, "月令${month.cn}见${g.cn}") { gan, _ -> gan == g }
        }

        DE_XIU[month]?.let { gods ->
            out += Rule("德秀贵人", ShenShaCategory.AUSPICIOUS, "月令${month.cn}所取之德与秀") { gan, _ ->
                gan in gods
            }
        }

        WEN_CHANG[dm]?.let { t ->
            out += Rule("文昌", ShenShaCategory.AUSPICIOUS, "以日干${dm.cn}查") { _, z -> z == t }
        }
        LU_SHEN[dm]?.let { t ->
            out += Rule("禄神", ShenShaCategory.AUSPICIOUS, "以日干${dm.cn}查") { _, z -> z == t }
        }

        out += Rule("阴差阳错", ShenShaCategory.INAUSPICIOUS, "日柱干支所属", DAY_ONLY) { g, z -> g.cn + z.cn in YIN_CHA }
        out += Rule("六秀日", ShenShaCategory.AUSPICIOUS, "日柱干支所属", DAY_ONLY) { g, z -> g.cn + z.cn in LIU_XIU }
        out += Rule("孤鸾煞", ShenShaCategory.INAUSPICIOUS, "日柱干支所属", DAY_ONLY) { g, z -> g.cn + z.cn in GU_LUAN }

        // ---- 勾绞煞：以年支为主，阳男阴女前勾后绞，阴男阳女前绞后勾。
        // 名目同为勾绞煞，故两辰皆收，不必区分勾与绞。
        run {
            val yangYearMale = input.yearGan.yang == input.male
            val front = input.yearZhi.shift(3)
            val back = input.yearZhi.shift(-3)
            val label = if (yangYearMale) "阳男阴女，命前后三辰" else "阴男阳女，命前后三辰"
            out += Rule("勾绞煞", ShenShaCategory.INAUSPICIOUS, "年支${input.yearZhi.cn}，$label") { _, z ->
                z == front || z == back
            }
        }

        TIAN_XI[input.yearZhi]?.let { t ->
            out += Rule("天喜", ShenShaCategory.AUSPICIOUS, "以年支${input.yearZhi.cn}查") { _, z -> z == t }
        }

        // ---- 桃花、驿马、华盖：年支与日支双查。
        GROUPS
            .filter { (keys, _) -> input.yearZhi in keys || input.dayZhi in keys }
            .map { it.second }
            .distinct()
            .forEach { rule ->
                out += Rule("桃花", ShenShaCategory.NEUTRAL, "年支、日支三合局") { _, z -> z == rule.peach }
                out += Rule("驿马", ShenShaCategory.NEUTRAL, "年支、日支三合局") { _, z -> z == rule.horse }
                out += Rule("华盖", ShenShaCategory.NEUTRAL, "年支、日支三合局") { _, z -> z == rule.canopy }
            }

        YANG_REN[dm]?.let { t ->
            out += Rule("羊刃", ShenShaCategory.INAUSPICIOUS, "以日干${dm.cn}查") { _, z -> z == t }
        }

        // ---- 童子煞：按年柱纳音五行取支。
        run {
            val targets = when (input.yearNaYin) {
                WuXing.JIN, WuXing.MU -> listOf(DiZhi.WU, DiZhi.MAO)
                WuXing.SHUI, WuXing.HUO -> listOf(DiZhi.YOU, DiZhi.XU)
                WuXing.TU -> listOf(DiZhi.CHEN, DiZhi.SI)
                null -> emptyList()
            }
            if (targets.isNotEmpty()) {
                out += Rule(
                    "童子煞",
                    ShenShaCategory.INAUSPICIOUS,
                    "年纳音${input.yearNaYin?.cn}命，见" + targets.joinToString("") { it.cn }
                ) { _, z -> z in targets }
            }
        }

        return out
    }

    /** 原局四柱的神煞，带宫位。 */
    fun analyze(input: ShenShaInput): List<ShenShaHit> {
        val hits = mutableListOf<ShenShaHit>()

        rulesOf(input).forEach { rule ->
            val positions = input.pillars
                .filter { (pos, g, z) ->
                    (rule.scope == null || pos in rule.scope) && rule.match(g, z)
                }
                .map { it.first }
            if (positions.isNotEmpty()) {
                hits += ShenShaHit(rule.name, rule.category, positions, rule.note)
            }
        }

        val voidPositions = input.pillars
            .filter { (_, _, z) -> z in input.voidBranches }
            .map { it.first }
        if (voidPositions.isNotEmpty()) {
            hits += ShenShaHit(
                name = "空亡",
                category = ShenShaCategory.NEUTRAL,
                positions = voidPositions,
                note = "日柱旬空：${input.voidBranches.joinToString("、") { it.cn }}"
            )
        }

        return hits
    }

    fun analyze(chart: BaziChart): List<ShenShaHit> = analyze(ShenShaInput.of(chart))

    /** 任意一柱（大运、流年、流月）上的神煞。日柱专有的几项不参与。 */
    fun marksOn(input: ShenShaInput, gan: TianGan, zhi: DiZhi): List<ShenShaMark> =
        rulesOf(input)
            .filter { it.scope == null && it.match(gan, zhi) }
            .map { ShenShaMark(it.name, it.category, it.note) }

    fun marksOn(chart: BaziChart, gan: TianGan, zhi: DiZhi): List<ShenShaMark> =
        marksOn(ShenShaInput.of(chart), gan, zhi)

    /** 多个运柱的神煞，按给定顺序逐柱返回。 */
    fun marksOf(chart: BaziChart, pillars: List<Pair<TianGan, DiZhi>>): List<List<ShenShaMark>> =
        pillars.map { (gan, zhi) -> marksOn(chart, gan, zhi) }
}
