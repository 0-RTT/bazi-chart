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
 * 全部表驱动：加一个神煞 = 加一条规则，不动判定逻辑。
 *
 * 口径由通行查法口诀确定（天乙、太极、月德、天德、桃花、驿马、华盖、亡神、
 * 劫煞等，见 `tools/verify/verify_shensha.py` 的 test_formulas），
 * 再由三张**构造盘**逐柱锁住「哪一柱命中」——构造盘均为合法干支组合，
 * 不含任何出生时间或地点。Kotlin 与 Python 两套独立实现必须同时精确复现，
 * 多一项少一项都算失败。
 *
 * 四条关键口径，都是把「单查」与「并查」两种写法逐柱对撞才分清的：
 *
 * 1. **天乙、国印、金舆、福星：年干与日干并查**。
 *    只取日干时，年干所主的那一组贵人会整批漏掉；只取年干则相反。
 *    两种写法都能在单张盘上「看起来成立」，必须换盘才分得清。
 * 2. **亡神、劫煞：年支与日支并查**。年支与日支常分属两个三合局，
 *    只取年支会漏掉日支所主的那一组。
 * 3. **红艳煞只看日干**。若并查年干，年干见辰而日干不见时，时柱会多出一项，
 *    通行工具并不列，故收窄为单查日干。
 * 4. **华盖要排除基准宫位**。四库之支属某三合局时，华盖正是它自己
 *    （亥卯未见未、申子辰见辰）；若不排除，该柱会「自己见自己」凭空多一项。
 *
 * 童子煞两法并查：纳音法（金木午卯、水火酉戌、土命辰巳）与
 * 季节法（春秋寅子、冬夏卯未辰）。只走纳音法会漏掉日支上的童子煞，
 * 只走季节法则会漏掉纳音所主的那一支，两法并用才齐。
 */
object ShenShaAnalyzer {

    // ---------------------------------------------------------------- 天干表

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

    /** 天乙贵人：甲戊庚牛羊、乙己鼠猴、丙丁猪鸡、壬癸兔蛇、辛逢马虎。 */
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

    /** 国印贵人：甲戌、乙亥、丙丑、丁寅、戊丑、己寅、庚辰、辛巳、壬未、癸申。 */
    private val GUO_YIN: Map<TianGan, DiZhi> = mapOf(
        TianGan.JIA to DiZhi.XU, TianGan.YI to DiZhi.HAI,
        TianGan.BING to DiZhi.CHOU, TianGan.DING to DiZhi.YIN,
        TianGan.WU to DiZhi.CHOU, TianGan.JI to DiZhi.YIN,
        TianGan.GENG to DiZhi.CHEN, TianGan.XIN to DiZhi.SI,
        TianGan.REN to DiZhi.WEI, TianGan.GUI to DiZhi.SHEN
    )

    /** 福星贵人：甲丙寅子、戊申、己未、丁亥、乙癸丑卯、庚午、辛巳、壬辰。 */
    private val FU_XING: Map<TianGan, List<DiZhi>> = mapOf(
        TianGan.JIA to listOf(DiZhi.YIN, DiZhi.ZI),
        TianGan.BING to listOf(DiZhi.YIN, DiZhi.ZI),
        TianGan.WU to listOf(DiZhi.SHEN),
        TianGan.JI to listOf(DiZhi.WEI),
        TianGan.DING to listOf(DiZhi.HAI),
        TianGan.YI to listOf(DiZhi.CHOU, DiZhi.MAO),
        TianGan.GUI to listOf(DiZhi.CHOU, DiZhi.MAO),
        TianGan.GENG to listOf(DiZhi.WU),
        TianGan.XIN to listOf(DiZhi.SI),
        TianGan.REN to listOf(DiZhi.CHEN)
    )

    /** 金舆：甲辰、乙巳、丙未、丁申、戊未、己申、庚戌、辛亥、壬丑、癸寅。 */
    private val JIN_YU: Map<TianGan, DiZhi> = mapOf(
        TianGan.JIA to DiZhi.CHEN, TianGan.YI to DiZhi.SI,
        TianGan.BING to DiZhi.WEI, TianGan.DING to DiZhi.SHEN,
        TianGan.WU to DiZhi.WEI, TianGan.JI to DiZhi.SHEN,
        TianGan.GENG to DiZhi.XU, TianGan.XIN to DiZhi.HAI,
        TianGan.REN to DiZhi.CHOU, TianGan.GUI to DiZhi.YIN
    )

    /** 红艳煞：甲午、乙午、丙寅、丁未、戊辰、己辰、庚戌、辛酉、壬子、癸申。只论日干。 */
    private val HONG_YAN: Map<TianGan, DiZhi> = mapOf(
        TianGan.JIA to DiZhi.WU, TianGan.YI to DiZhi.WU,
        TianGan.BING to DiZhi.YIN, TianGan.DING to DiZhi.WEI,
        TianGan.WU to DiZhi.CHEN, TianGan.JI to DiZhi.CHEN,
        TianGan.GENG to DiZhi.XU, TianGan.XIN to DiZhi.YOU,
        TianGan.REN to DiZhi.ZI, TianGan.GUI to DiZhi.SHEN
    )

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

    /** 天干五合、地支六合，供天德合、月德合取所合之字。 */
    private val GAN_HE: Map<TianGan, TianGan> = mapOf(
        TianGan.JIA to TianGan.JI, TianGan.JI to TianGan.JIA,
        TianGan.YI to TianGan.GENG, TianGan.GENG to TianGan.YI,
        TianGan.BING to TianGan.XIN, TianGan.XIN to TianGan.BING,
        TianGan.DING to TianGan.REN, TianGan.REN to TianGan.DING,
        TianGan.WU to TianGan.GUI, TianGan.GUI to TianGan.WU
    )

    private val ZHI_HE: Map<DiZhi, DiZhi> = mapOf(
        DiZhi.ZI to DiZhi.CHOU, DiZhi.CHOU to DiZhi.ZI,
        DiZhi.YIN to DiZhi.HAI, DiZhi.HAI to DiZhi.YIN,
        DiZhi.MAO to DiZhi.XU, DiZhi.XU to DiZhi.MAO,
        DiZhi.CHEN to DiZhi.YOU, DiZhi.YOU to DiZhi.CHEN,
        DiZhi.SI to DiZhi.SHEN, DiZhi.SHEN to DiZhi.SI,
        DiZhi.WU to DiZhi.WEI, DiZhi.WEI to DiZhi.WU
    )

    // ---------------------------------------------------------------- 月令表

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
     * 寅午戌月德丙丁、秀戊癸；申子辰月德壬癸戊己、秀丙辛甲；
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

    // ---------------------------------------------------------------- 地支表

    /**
     * 三合局分组，索引 0..3 依次为 申子辰、寅午戌、巳酉丑、亥卯未。
     * 桃花、驿马、华盖、亡神、劫煞、孤辰、寡宿全部由分组查表，
     * 避免五张独立的 Map 各写一遍还容易抄错。
     */
    private val GROUP_OF: Map<DiZhi, Int> = buildMap {
        listOf(DiZhi.SHEN, DiZhi.ZI, DiZhi.CHEN).forEach { put(it, 0) }
        listOf(DiZhi.YIN, DiZhi.WU, DiZhi.XU).forEach { put(it, 1) }
        listOf(DiZhi.SI, DiZhi.YOU, DiZhi.CHOU).forEach { put(it, 2) }
        listOf(DiZhi.HAI, DiZhi.MAO, DiZhi.WEI).forEach { put(it, 3) }
    }

    private val GROUP_PEACH = listOf(DiZhi.YOU, DiZhi.MAO, DiZhi.WU, DiZhi.ZI)
    private val GROUP_HORSE = listOf(DiZhi.YIN, DiZhi.SHEN, DiZhi.HAI, DiZhi.SI)
    private val GROUP_CANOPY = listOf(DiZhi.CHEN, DiZhi.XU, DiZhi.CHOU, DiZhi.WEI)
    private val GROUP_WANG = listOf(DiZhi.HAI, DiZhi.SI, DiZhi.SHEN, DiZhi.YIN)
    private val GROUP_JIE = listOf(DiZhi.SI, DiZhi.HAI, DiZhi.YIN, DiZhi.SHEN)

    /**
     * 孤辰、寡宿按**三会方**分组，不是三合局——这两者容易混：
     * 年支寅既在寅午戌（三合），又在寅卯辰（三会），孤辰寡宿取的是后者。
     * 若套用 [GROUP_OF] 的三合索引，卯年会被算成「寡宿戌」，实测没有。
     */
    private val GU_CHEN: Map<DiZhi, DiZhi> = mapOf(
        DiZhi.ZI to DiZhi.YIN, DiZhi.CHOU to DiZhi.YIN,
        DiZhi.YIN to DiZhi.SI, DiZhi.MAO to DiZhi.SI, DiZhi.CHEN to DiZhi.SI,
        DiZhi.SI to DiZhi.SHEN, DiZhi.WU to DiZhi.SHEN, DiZhi.WEI to DiZhi.SHEN,
        DiZhi.SHEN to DiZhi.HAI, DiZhi.YOU to DiZhi.HAI, DiZhi.XU to DiZhi.HAI,
        DiZhi.HAI to DiZhi.YIN
    )

    private val GUA_SU: Map<DiZhi, DiZhi> = mapOf(
        DiZhi.ZI to DiZhi.XU, DiZhi.CHOU to DiZhi.XU,
        DiZhi.YIN to DiZhi.CHOU, DiZhi.MAO to DiZhi.CHOU, DiZhi.CHEN to DiZhi.CHOU,
        DiZhi.SI to DiZhi.CHEN, DiZhi.WU to DiZhi.CHEN, DiZhi.WEI to DiZhi.CHEN,
        DiZhi.SHEN to DiZhi.WEI, DiZhi.YOU to DiZhi.WEI, DiZhi.XU to DiZhi.WEI,
        DiZhi.HAI to DiZhi.XU
    )

    /** 红鸾：子卯、丑寅、寅丑、卯子、辰亥、巳戌、午酉、未申、申未、酉午、戌巳、亥辰。 */
    private val HONG_LUAN: Map<DiZhi, DiZhi> = mapOf(
        DiZhi.ZI to DiZhi.MAO, DiZhi.CHOU to DiZhi.YIN,
        DiZhi.YIN to DiZhi.CHOU, DiZhi.MAO to DiZhi.ZI,
        DiZhi.CHEN to DiZhi.HAI, DiZhi.SI to DiZhi.XU,
        DiZhi.WU to DiZhi.YOU, DiZhi.WEI to DiZhi.SHEN,
        DiZhi.SHEN to DiZhi.WEI, DiZhi.YOU to DiZhi.WU,
        DiZhi.XU to DiZhi.SI, DiZhi.HAI to DiZhi.CHEN
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

    // ---------------------------------------------------------------- 柱表

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

    /** 八专日：只看日柱。 */
    private val BA_ZHUAN = setOf(
        "甲寅", "乙卯", "丁未", "戊戌", "己未", "庚申", "辛酉", "癸丑"
    )

    // ---------------------------------------------------------------- 规则

    /**
     * 一条查法。
     *
     * [scope] 非空表示只论某几宫，运柱不查；[match] 的第一参数为宫位，
     * 运柱传 null——华盖要据此排除「以日支查日支」的自指。
     */
    private class Rule(
        val name: String,
        val category: ShenShaCategory,
        val note: String,
        val scope: Set<PillarPosition>? = null,
        val match: (PillarPosition?, TianGan, DiZhi) -> Boolean
    )

    private val DAY_ONLY = setOf(PillarPosition.DAY)

    private fun rulesOf(input: ShenShaInput): List<Rule> {
        val out = mutableListOf<Rule>()
        val yGan = input.yearGan
        val dGan = input.dayGan
        val yZhi = input.yearZhi
        val dZhi = input.dayZhi
        val month = input.monthZhi

        // ---- 太极贵人：年干、日干并查
        run {
            val targets = (TAI_JI[yGan].orEmpty() + TAI_JI[dGan].orEmpty()).distinct()
            if (targets.isNotEmpty()) {
                out += Rule("太极贵人", ShenShaCategory.AUSPICIOUS, "年干${yGan.cn}、日干${dGan.cn}并查") { _, _, z ->
                    z in targets
                }
            }
        }

        // ---- 天乙贵人：年干、日干并查（有的柱只能由年干取到，单看日干会漏）
        run {
            val targets = (TIAN_YI[yGan].orEmpty() + TIAN_YI[dGan].orEmpty()).distinct()
            if (targets.isNotEmpty()) {
                out += Rule("天乙贵人", ShenShaCategory.AUSPICIOUS, "年干${yGan.cn}、日干${dGan.cn}并查") { _, _, z ->
                    z in targets
                }
            }
        }

        // ---- 国印贵人：年干、日干并查
        run {
            val targets = listOfNotNull(GUO_YIN[yGan], GUO_YIN[dGan]).distinct()
            if (targets.isNotEmpty()) {
                out += Rule("国印贵人", ShenShaCategory.AUSPICIOUS, "年干${yGan.cn}、日干${dGan.cn}并查") { _, _, z ->
                    z in targets
                }
            }
        }

        // ---- 福星贵人：年干、日干并查
        run {
            val targets = (FU_XING[yGan].orEmpty() + FU_XING[dGan].orEmpty()).distinct()
            if (targets.isNotEmpty()) {
                out += Rule("福星贵人", ShenShaCategory.AUSPICIOUS, "年干${yGan.cn}、日干${dGan.cn}并查") { _, _, z ->
                    z in targets
                }
            }
        }

        // ---- 金舆：年干、日干并查（日干取不到时，年干往往正命中）
        run {
            val targets = listOfNotNull(JIN_YU[yGan], JIN_YU[dGan]).distinct()
            if (targets.isNotEmpty()) {
                out += Rule("金舆", ShenShaCategory.AUSPICIOUS, "年干${yGan.cn}、日干${dGan.cn}并查") { _, _, z ->
                    z in targets
                }
            }
        }

        // ---- 天德贵人：按月令取，所取或干或支
        run {
            val gan = TIAN_DE_GAN[month]
            val zhi = TIAN_DE_ZHI[month]
            if (gan != null || zhi != null) {
                val label = gan?.cn ?: zhi!!.cn
                out += Rule("天德贵人", ShenShaCategory.AUSPICIOUS, "月令${month.cn}见${label}") { _, g, z ->
                    (gan != null && g == gan) || (zhi != null && z == zhi)
                }
            }
        }

        // ---- 天德合：天德所取之字取六合（干取五合，支取六合）
        run {
            val deGan = TIAN_DE_GAN[month]
            val deZhi = TIAN_DE_ZHI[month]
            if (deGan != null) {
                GAN_HE[deGan]?.let { he ->
                    out += Rule("天德合", ShenShaCategory.AUSPICIOUS, "月令${month.cn}天德${deGan.cn}，合${he.cn}") { _, g, _ ->
                        g == he
                    }
                }
            }
            if (deZhi != null) {
                ZHI_HE[deZhi]?.let { he ->
                    out += Rule("天德合", ShenShaCategory.AUSPICIOUS, "月令${month.cn}天德${deZhi.cn}，合${he.cn}") { _, _, z ->
                        z == he
                    }
                }
            }
        }

        YUE_DE[month]?.let { g ->
            out += Rule("月德贵人", ShenShaCategory.AUSPICIOUS, "月令${month.cn}见${g.cn}") { _, gan, _ -> gan == g }
        }

        // ---- 月德合：月德所取之干取五合
        YUE_DE[month]?.let { g ->
            GAN_HE[g]?.let { he ->
                out += Rule("月德合", ShenShaCategory.AUSPICIOUS, "月令${month.cn}月德${g.cn}，合${he.cn}") { _, gan, _ ->
                    gan == he
                }
            }
        }

        DE_XIU[month]?.let { gods ->
            out += Rule("德秀贵人", ShenShaCategory.AUSPICIOUS, "月令${month.cn}所取之德与秀") { _, gan, _ ->
                gan in gods
            }
        }

        WEN_CHANG[dGan]?.let { t ->
            out += Rule("文昌", ShenShaCategory.AUSPICIOUS, "以日干${dGan.cn}查") { _, _, z -> z == t }
        }
        LU_SHEN[dGan]?.let { t ->
            out += Rule("禄神", ShenShaCategory.AUSPICIOUS, "以日干${dGan.cn}查") { _, _, z -> z == t }
        }

        // ---- 红艳煞：只以日干查。并查年干会让时柱凭空多一项，故收窄。
        HONG_YAN[dGan]?.let { t ->
            out += Rule("红艳煞", ShenShaCategory.INAUSPICIOUS, "以日干${dGan.cn}查") { _, _, z -> z == t }
        }

        // ---- 日柱专有
        out += Rule("阴差阳错", ShenShaCategory.INAUSPICIOUS, "日柱干支所属", DAY_ONLY) { _, g, z ->
            g.cn + z.cn in YIN_CHA
        }
        out += Rule("六秀日", ShenShaCategory.AUSPICIOUS, "日柱干支所属", DAY_ONLY) { _, g, z ->
            g.cn + z.cn in LIU_XIU
        }
        out += Rule("孤鸾煞", ShenShaCategory.INAUSPICIOUS, "日柱干支所属", DAY_ONLY) { _, g, z ->
            g.cn + z.cn in GU_LUAN
        }
        out += Rule("八专日", ShenShaCategory.NEUTRAL, "日柱干支所属", DAY_ONLY) { _, g, z ->
            g.cn + z.cn in BA_ZHUAN
        }

        // ---- 天喜、红鸾、孤辰、寡宿：只以年支查
        TIAN_XI[yZhi]?.let { t ->
            out += Rule("天喜", ShenShaCategory.AUSPICIOUS, "以年支${yZhi.cn}查") { _, _, z -> z == t }
        }
        HONG_LUAN[yZhi]?.let { t ->
            out += Rule("红鸾", ShenShaCategory.AUSPICIOUS, "以年支${yZhi.cn}查") { _, _, z -> z == t }
        }
        GU_CHEN[yZhi]?.let { t ->
            out += Rule("孤辰", ShenShaCategory.INAUSPICIOUS, "以年支${yZhi.cn}查") { _, _, z -> z == t }
        }
        GUA_SU[yZhi]?.let { t ->
            out += Rule("寡宿", ShenShaCategory.INAUSPICIOUS, "以年支${yZhi.cn}查") { _, _, z -> z == t }
        }

        // ---- 丧门：年支顺行二位
        run {
            val target = yZhi.shift(2)
            out += Rule("丧门", ShenShaCategory.INAUSPICIOUS, "年支${yZhi.cn}顺行二位见${target.cn}") { _, _, z ->
                z == target
            }
        }

        // ---- 勾绞煞：以年支为主，阳男阴女前勾后绞，阴男阳女前绞后勾
        run {
            val yangYearMale = yGan.yang == input.male
            val front = yZhi.shift(3)
            val back = yZhi.shift(-3)
            val label = if (yangYearMale) "阳男阴女，命前后三辰" else "阴男阳女，命前后三辰"
            out += Rule("勾绞煞", ShenShaCategory.INAUSPICIOUS, "年支${yZhi.cn}，$label") { _, _, z ->
                z == front || z == back
            }
        }

        // ---- 桃花、驿马、华盖、亡神、劫煞：年支与日支并查。
        // 华盖排除基准宫位：日支未属亥卯未、华盖即未，不排除则日柱自己见自己。
        listOf(PillarPosition.YEAR to yZhi, PillarPosition.DAY to dZhi).forEach { (basePos, base) ->
            val idx = GROUP_OF[base] ?: return@forEach
            val label = if (basePos == PillarPosition.YEAR) "年支" else "日支"
            out += Rule("桃花", ShenShaCategory.NEUTRAL, "${label}${base.cn}所属三合局") { _, _, z ->
                z == GROUP_PEACH[idx]
            }
            out += Rule("驿马", ShenShaCategory.NEUTRAL, "${label}${base.cn}所属三合局") { _, _, z ->
                z == GROUP_HORSE[idx]
            }
            out += Rule("华盖", ShenShaCategory.NEUTRAL, "${label}${base.cn}所属三合局") { pos, _, z ->
                z == GROUP_CANOPY[idx] && pos != basePos
            }
            out += Rule("亡神", ShenShaCategory.INAUSPICIOUS, "${label}${base.cn}所属三合局") { _, _, z ->
                z == GROUP_WANG[idx]
            }
            out += Rule("劫煞", ShenShaCategory.INAUSPICIOUS, "${label}${base.cn}所属三合局") { _, _, z ->
                z == GROUP_JIE[idx]
            }
        }

        YANG_REN[dGan]?.let { t ->
            out += Rule("羊刃", ShenShaCategory.INAUSPICIOUS, "以日干${dGan.cn}查") { _, _, z -> z == t }
        }

        // ---- 童子煞：纳音法与季节法并查，两法各自成立即算
        run {
            val byNaYin = when (input.yearNaYin) {
                WuXing.JIN, WuXing.MU -> listOf(DiZhi.WU, DiZhi.MAO)
                WuXing.SHUI, WuXing.HUO -> listOf(DiZhi.YOU, DiZhi.XU)
                WuXing.TU -> listOf(DiZhi.CHEN, DiZhi.SI)
                null -> emptyList()
            }
            val bySeason = when (month) {
                DiZhi.YIN, DiZhi.MAO, DiZhi.CHEN, DiZhi.SHEN, DiZhi.YOU, DiZhi.XU ->
                    listOf(DiZhi.YIN, DiZhi.ZI)
                else -> listOf(DiZhi.MAO, DiZhi.WEI, DiZhi.CHEN)
            }
            val targets = (byNaYin + bySeason).distinct()
            if (targets.isNotEmpty()) {
                out += Rule(
                    "童子煞",
                    ShenShaCategory.INAUSPICIOUS,
                    "年纳音${input.yearNaYin?.cn ?: "—"}命与月令${month.cn}并查"
                ) { _, _, z -> z in targets }
            }
        }

        return out
    }

    /** 原局四柱的神煞，带宫位。同名规则（年支查、日支查各一条）合并为一处。 */
    fun analyze(input: ShenShaInput): List<ShenShaHit> {
        val merged = LinkedHashMap<String, ShenShaHit>()

        rulesOf(input).forEach { rule ->
            val positions = input.pillars
                .filter { (pos, g, z) -> (rule.scope == null || pos in rule.scope) && rule.match(pos, g, z) }
                .map { it.first }
            if (positions.isNotEmpty()) {
                val existing = merged[rule.name]
                merged[rule.name] = if (existing == null) {
                    ShenShaHit(rule.name, rule.category, positions, rule.note)
                } else {
                    existing.copy(positions = (existing.positions + positions).distinct())
                }
            }
        }

        val hits = merged.values.toMutableList()
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
            .filter { it.scope == null && it.match(null, gan, zhi) }
            .distinctBy { it.name }
            .map { ShenShaMark(it.name, it.category, it.note) }

    fun marksOn(chart: BaziChart, gan: TianGan, zhi: DiZhi): List<ShenShaMark> =
        marksOn(ShenShaInput.of(chart), gan, zhi)

    /** 多个运柱的神煞，按给定顺序逐柱返回。 */
    fun marksOf(chart: BaziChart, pillars: List<Pair<TianGan, DiZhi>>): List<List<ShenShaMark>> =
        pillars.map { (gan, zhi) -> marksOn(chart, gan, zhi) }
}
