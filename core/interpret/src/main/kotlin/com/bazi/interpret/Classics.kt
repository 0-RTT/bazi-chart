package com.bazi.interpret

import com.bazi.analysis.AnalysisResult
import com.bazi.analysis.StrengthLevel
import com.bazi.engine.BaziChart
import com.bazi.engine.HiddenStemTable
import com.bazi.engine.TenGod
import com.bazi.engine.TenGodCategory
import com.bazi.engine.WuXing
import java.time.LocalDate

/**
 * 十部命理古籍的取向。
 *
 * 每一部只声明三件事：谁写的、信奉什么、拿什么当判据。
 * 针对具体命盘的论断由 [ClassicReader] 生成，不写死在枚举里。
 */
enum class Classic(
    val book: String,
    val author: String,
    val era: String,
    val school: String,
    val creed: String,
    val method: String
) {
    DI_TIAN_SUI(
        "滴天髓", "京图撰·刘基注", "宋明", "子平法·气势派",
        "旺则宜泄宜伤，衰则喜帮喜助",
        "看衰旺真机与气势流通，重全局气脉，不拘泥格名"
    ),
    QIONG_TONG_BAO_JIAN(
        "穷通宝鉴", "余春台辑", "清", "子平法·调候派",
        "论命先看寒暖燥湿，调候为急",
        "按月令与日干查用神，先解气候之偏，再论富贵"
    ),
    SAN_MING_TONG_HUI(
        "三命通会", "万民英", "明", "子平法·集大成",
        "兼收神煞、纳音、胎元、十二宫，诸法并用",
        "以日干为主而不废年命，神煞纳音皆作参看"
    ),
    BA_ZI_TI_YAO(
        "八字提要", "民国辑本", "民国", "子平法·月令举例",
        "按月令与日干逐条举例，以运程配合定成败",
        "先取月令当令之神，再看运程是否引动"
    ),
    ZI_PING_ZHEN_QUAN(
        "子平真诠", "沈孝瞻", "清", "子平法·格局派",
        "八字用神，专求月令",
        "以月令透干取格，专论格局成败救应，力斥滥用神煞"
    ),
    TIAN_YUAN_XIAN_WU(
        "天元咸巫", "古星命经", "唐宋", "星命古法",
        "以年命为根，参星曜纳音以定禀赋",
        "重年干年支与纳音，讲本命根基之厚薄"
    ),
    SHEN_FENG_TONG_KAO(
        "神峰通考", "张楠", "明", "子平法·病药派",
        "有病方为贵，无伤不是奇",
        "先寻命局之病，再求去病之药，反对拘泥格名与神煞"
    ),
    QIAN_LI_MING_GAO(
        "千里命稿", "韦千里", "民国", "子平法·实务派",
        "用神要论有力无力，行运要看助与不助",
        "以旺衰定用神，以用神之强弱与行运定吉凶，语取平实"
    ),
    WU_XING_JING_JI(
        "五行精纪", "廖中", "宋", "禄命古法",
        "以纳音五行为纲，论年命禀气",
        "重纳音生克与干支体性，与子平以日干论旺衰并行"
    ),
    LI_XU_ZHONG(
        "李虚中命书", "李虚中", "唐", "禄命古法·三命",
        "以年为本，年月日时为三命",
        "以年干为命主，月为父母兄弟，日为己身，与子平以日为主不同"
    )
}

/** 一家之言落在本盘上的结果。 */
data class ClassicView(
    val classic: Classic,
    /** 针对本盘的具体论断。 */
    val reading: String,
    /** 与别家分歧之处。 */
    val divergence: String
)

/** 一条真正的分歧轴。 */
data class DivergenceAxis(
    val topic: String,
    val camps: String,
    /** 落到本盘上，各种取法各自得出什么。 */
    val onThisChart: String
)

/**
 * 古籍分歧引擎。
 *
 * 目的不是判「哪家对」，而是把**同一张盘在不同方法论下会得出什么**摊开。
 * 每条论断都必须落在本盘的具体干支或分数上，不允许只报书名不给结论。
 */
object ClassicReader {

    fun read(
        chart: BaziChart,
        a: AnalysisResult,
        referenceYear: Int = LocalDate.now().year
    ): List<ClassicView> = listOf(
        diTianSui(chart, a),
        qiongTongBaoJian(chart, a),
        sanMingTongHui(chart, a),
        baZiTiYao(chart, a, referenceYear),
        ziPingZhenQuan(chart, a),
        tianYuanXianWu(chart, a),
        shenFengTongKao(chart, a),
        qianLiMingGao(chart, a),
        wuXingJingJi(chart, a),
        liXuZhong(chart, a)
    )

    fun divergences(chart: BaziChart, a: AnalysisResult): List<DivergenceAxis> = listOf(
        DivergenceAxis(
            topic = "命主是谁：日干还是年干",
            camps = "以日干为主：滴天髓、子平真诠、穷通宝鉴、神峰通考、千里命稿、八字提要、三命通会；" +
                "以年干为本：李虚中命书、五行精纪、天元咸巫",
            onThisChart = run {
                val yearElement = chart.yearPillar.gan.wuXing
                val yearPercent = a.strength.elementScores.first { it.wuXing == yearElement }.percent
                "按子平法看日主${chart.dayMaster.cn}${chart.dayMaster.wuXing.cn}，结论是${a.strength.level.cn}；" +
                    "若依古法改以年干${chart.yearPillar.gan.cn}${yearElement.cn}为命主，该气占 " +
                    "%.1f%%，".format(yearPercent * 100) +
                    if (yearPercent >= 0.3) "其势不弱，根基可取" else "其气偏轻，古法会判本命根基薄"
            }
        ),
        DivergenceAxis(
            topic = "用神从哪里取",
            camps = "月令格局取用：子平真诠；调候取用：穷通宝鉴；病药取用：神峰通考；气势顺逆取用：滴天髓",
            onThisChart = run {
                val patternUse = a.pattern.tenGod?.cn ?: "月令本气"
                val tiaoUse = a.tiaohou.entry?.primary?.cn ?: "无特定调候之需"
                val illness = diagnose(chart, a)
                "子平真诠会取${patternUse}为用；穷通宝鉴先取${tiaoUse}以解寒暖；" +
                    "神峰通考判「${illness.name}」，主张以${illness.cure}去病为先；" +
                    "滴天髓不问格名，只看气势宜泄宜帮。四条路子在同一个字上会分家。"
            }
        ),
        DivergenceAxis(
            topic = "神煞算不算数",
            camps = "列为参看：三命通会、五行精纪、天元咸巫；明确贬斥：子平真诠、神峰通考、滴天髓",
            onThisChart = run {
                val names = a.shenSha.filter { it.name != "空亡" }.map { it.name }.distinct()
                val text = if (names.isEmpty()) "本局未见常用神煞" else "本局见" + names.joinToString("、")
                "$text。三命通会会据此参断吉凶；子平真诠与神峰通考则认为神煞不足凭，" +
                    "格局与病药既已分明，神煞只作旁证"
            }
        ),
        DivergenceAxis(
            topic = "纳音用不用",
            camps = "以纳音论禀赋：五行精纪、李虚中命书、天元咸巫；视为古法不用：子平真诠以降的格局派",
            onThisChart = "年柱纳音${chart.yearPillar.naYin}、日柱纳音${chart.dayPillar.naYin}，" +
                "古法会以二者生克论根基与自身之气是否相承；格局派则完全不计纳音，只论干支五行"
        )
    )

    // ---------------------------------------------------------------- 十家

    private fun diTianSui(chart: BaziChart, a: AnalysisResult): ClassicView {
        val s = a.strength
        val me = chart.dayMaster.wuXing
        val strongest = s.elementScores.maxByOrNull { it.score }
        val weakest = s.elementScores.minByOrNull { it.score }

        val reading = buildString {
            when (s.level) {
                StrengthLevel.VERY_STRONG, StrengthLevel.STRONG, StrengthLevel.SLIGHTLY_STRONG -> {
                    append("日主${me.cn}气偏旺（帮身 %.1f%%），依「旺则宜泄宜伤」，".format(s.ratio * 100))
                    append("当以${me.generates().cn}泄其秀、${me.overcomes().cn}耗其势，")
                    append("忌再见${me.cn}与${me.generatedBy().cn}助旺。")
                }
                StrengthLevel.VERY_WEAK, StrengthLevel.WEAK, StrengthLevel.SLIGHTLY_WEAK -> {
                    append("日主${me.cn}气偏衰（帮身仅 %.1f%%），依「衰则喜帮喜助」，".format(s.ratio * 100))
                    append("当以${me.generatedBy().cn}生之、${me.cn}扶之，")
                    append("忌${me.overcomes().cn}与${me.generates().cn}再耗其力。")
                }
                StrengthLevel.BALANCED -> {
                    append("日主${me.cn}气中和（帮身 %.1f%%），不需强扶强抑，".format(s.ratio * 100))
                    append("真机在流通二字：何者为病、何者为药，令五行周流不滞便是。")
                }
            }
            if (strongest != null && weakest != null) {
                append("全局${strongest.wuXing.cn}占 %.1f%%、${weakest.wuXing.cn}仅 %.1f%%，".format(
                    strongest.percent * 100, weakest.percent * 100
                ))
                append(
                    if (strongest.percent - weakest.percent > 0.45) "气流偏于一侧，最忌再逢助强抑弱之运。"
                    else "五行尚能相接，气脉不算壅塞。"
                )
            }
        }

        return ClassicView(
            classic = Classic.DI_TIAN_SUI,
            reading = reading,
            divergence = "滴天髓不承认「格」有独立价值，与子平真诠专主月令取格正相反：" +
                "同一张盘，它先问「气往何处去」，子平真诠先问「月令何神当权」。"
        )
    }

    private fun qiongTongBaoJian(chart: BaziChart, a: AnalysisResult): ClassicView {
        val t = a.tiaohou
        val entry = t.entry
        val reading = buildString {
            if (entry == null) {
                append("该日干与月令组合的调候条目缺失，暂不能依本书立论。")
            } else {
                append("${chart.monthCommand.cn}月${chart.dayMaster.cn}${chart.dayMaster.wuXing.cn}，")
                append("本书定主用${entry.primary.cn}")
                if (entry.assists.isNotEmpty()) append("，辅以${entry.assists.joinToString("、") { it.cn }}")
                append("。")
                when {
                    t.present.isEmpty() ->
                        append("而四柱全不见用神，气候之偏无从调停，须待运来补足，此为先天欠缺处。")
                    t.present.size == 1 ->
                        append("柱中仅见${t.present.first().cn}，用神单弱，调候之功有限，须运程再助。")
                    else ->
                        append("柱中已见${t.present.joinToString("、") { it.cn }}，寒暖得济，格局才有舒展之地。")
                }
                append("本书的取舍与旺衰无关：纵日主极旺，若气候失宜，仍以调候为先。")
            }
        }
        return ClassicView(
            classic = Classic.QIONG_TONG_BAO_JIAN,
            reading = reading,
            divergence = "调候派第一句问的是「冷不冷、燥不燥」，不是「身强身弱」；" +
                "若调候用神与扶抑用神不是同一个字，本书会毫不犹豫取调候。"
        )
    }

    private fun sanMingTongHui(chart: BaziChart, a: AnalysisResult): ClassicView {
        val names = a.shenSha.filter { it.name != "空亡" }
        val reading = buildString {
            append("本书兼收诸法，四柱之外并看神煞、纳音、胎元命宫。")
            if (names.isEmpty()) {
                append("本局神煞寡淡，仅有${a.shenSha.joinToString("、") { it.name }.ifBlank { "常规旬空" }}，吉凶之应不显。")
            } else {
                append("本局" + names.joinToString("、") { "${it.name}（${it.positionText}）" } + "，")
                append("其中${names.first().name}最宜参看。")
            }
            append("胎元${chart.fetalOrigin}、命宫${chart.ownSign}、身宫${chart.bodySign}，三处与四柱互为表里。")
            append("年柱纳音${chart.yearPillar.naYin}、日柱纳音${chart.dayPillar.naYin}，纳音亦作一重参断。")
        }
        return ClassicView(
            classic = Classic.SAN_MING_TONG_HUI,
            reading = reading,
            divergence = "本书是「都收」的路子，与子平真诠「力斥神煞」、神峰通考「神煞谬说」直接对立：" +
                "同一颗天乙贵人，本书算一层福气，两家只当闲字。"
        )
    }

    private fun baZiTiYao(chart: BaziChart, a: AnalysisResult, referenceYear: Int): ClassicView {
        val entry = a.tiaohou.entry
        val main = entry?.primary?.cn ?: "当令之神"
        val primaryPresent = entry != null && a.tiaohou.present.contains(entry.primary)
        val assistPresent = entry != null && entry.assists.any { it in a.tiaohou.present }
        val current = chart.luckOfYear(referenceYear)

        val reading = buildString {
            append("${chart.monthCommand.cn}月生${chart.dayMaster.cn}${chart.dayMaster.wuXing.cn}，以${main}为纲")
            if (entry != null && entry.assists.isNotEmpty()) {
                append("，${entry.assists.first().cn}为佐")
            }
            append("。")
            append(
                when {
                    primaryPresent -> "柱中已具纲神，出而能任事。"
                    assistPresent -> "纲神不见而佐神在，力薄一等，须运助纲神方能发用。"
                    else -> "纲神与佐神俱缺，早岁多滞，须俟运至。"
                }
            )
            if (current != null) {
                append("现行${current.ganZhi}运，干为${current.ganTenGod.cn}，")
                append(
                    if (entry != null && current.gan == entry.primary) "恰是纲神得助之地，此运可作。"
                    else "与纲神不同气，宜守成待时。"
                )
            }
        }
        return ClassicView(
            classic = Classic.BA_ZI_TI_YAO,
            reading = reading,
            divergence = "本书与穷通宝鉴同重月令，但更看重「运来引动」：" +
                "同样的用神，柱中有与柱中无，在它笔下是两个完全不同的命。"
        )
    }

    private fun ziPingZhenQuan(chart: BaziChart, a: AnalysisResult): ClassicView {
        val p = a.pattern
        val breaker = findBreaker(chart, p.tenGod)

        val reading = buildString {
            append("依「八字用神，专求月令」，月令${chart.monthCommand.cn}本气")
            append("${HiddenStemTable.mainStem(chart.monthCommand).cn}，取${p.displayName}。")
            append(p.basis).append("。")
            append(if (p.transparent) "格神已透干，格有所立；" else "格神未透，格势偏虚，须运来引透；")
            if (breaker != null) {
                append("而天干见${breaker}，为破格之神，格既受伤，须待运中去此病神，格方能复。")
            } else {
                append("天干不见破格之神，格气得全，是为成格之象。")
            }
        }

        return ClassicView(
            classic = Classic.ZI_PING_ZHEN_QUAN,
            reading = reading,
            divergence = "本书是十家里最「唯格」的一路：格成则贱命亦许其贵，格破则贵格亦断其滞。" +
                "它与神峰通考的病药说、滴天髓的气势说，构成清代以来最大的三条分歧线。"
        )
    }

    private fun tianYuanXianWu(chart: BaziChart, a: AnalysisResult): ClassicView {
        val yearGan = chart.yearPillar.gan
        val yearZhi = chart.yearPillar.zhi
        val yearEl = yearGan.wuXing
        val percent = a.strength.elementScores.first { it.wuXing == yearEl }.percent

        val reading = buildString {
            append("古法以年命为根：年干${yearGan.cn}、年支${yearZhi.cn}，纳音${chart.yearPillar.naYin}，是为本命。")
            append("本命之气属${yearEl.cn}，在全局占 %.1f%%，".format(percent * 100))
            append(if (percent >= 0.3) "根基不薄，早年环境可倚。" else "其气轻浅，早年根基须自家另立。")
            append("再以年干与日干相较：年干${yearGan.cn}、日干${chart.dayMaster.cn}，")
            append(
                if (yearEl == chart.dayMaster.wuXing) "同气，禀受与自性相承；"
                else "异气，先天所禀与后天所成不同调，一生多在两者之间取舍。"
            )
        }

        return ClassicView(
            classic = Classic.TIAN_YUAN_XIAN_WU,
            reading = reading,
            divergence = "古法看的是「你生在哪一年」，子平看的是「你生在哪一天」：" +
                "一个论家世根基，一个论自身作为，两套语言说的不是同一件事。"
        )
    }

    private fun shenFengTongKao(chart: BaziChart, a: AnalysisResult): ClassicView {
        val illness = diagnose(chart, a)
        val reading = buildString {
            append("依「有病方为贵，无伤不是奇」，先寻病处：").append(illness.name).append("。")
            append(illness.detail).append("。")
            append("去病之药在").append(illness.cure).append("。")
            append(
                if (illness.name == NO_ILLNESS) "命局本自周流，无须强求病药，此谓平顺之造，不必以奇格相许。"
                else "运至${illness.cure}之地，病去而气顺，方是发越之时；若运再助病，则旧疾复作。"
            )
        }
        return ClassicView(
            classic = Classic.SHEN_FENG_TONG_KAO,
            reading = reading,
            divergence = "本书最激烈地反对两件事：拘泥格名、滥用神煞。它认为格不过是名、病才是实，" +
                "这一点与子平真诠、三命通会同时相左。"
        )
    }

    private fun qianLiMingGao(chart: BaziChart, a: AnalysisResult): ClassicView {
        val favorable = a.favorableElements
        val ranked = favorable.map { wx ->
            wx.cn to a.strength.elementScores.first { it.wuXing == wx }.percent
        }
        val best = ranked.maxByOrNull { it.second }
        val worst = ranked.minByOrNull { it.second }

        val reading = buildString {
            append("取用先看旺衰：日主${a.strength.level.cn}，宜")
            append(favorable.joinToString("、") { it.cn }).append("。")
            if (best != null) {
                append("其中${best.first}在柱中占 %.1f%%，".format(best.second * 100))
                append(if (best.second >= 0.2) "用神有力，" else "用神无力，")
                append(
                    if (best.second >= 0.2) "行运再助则事半功倍；"
                    else "只得借助运程，运不来则力不从心；"
                )
            }
            if (worst != null && worst.second < 0.08) {
                append("而${worst.first}几乎不见，此一路的欠缺要靠行运补。")
            }
            append("论命不说玄虚，只问用神有无力量、行运助不助它。")
        }

        return ClassicView(
            classic = Classic.QIAN_LI_MING_GAO,
            reading = reading,
            divergence = "民国实务派把「用神」从形而上的概念降为可称量的东西：有力即吉，无力即否。" +
                "与滴天髓讲气脉、子平真诠讲格调相比，它最不讲道理，却最便于实用。"
        )
    }

    private fun wuXingJingJi(chart: BaziChart, a: AnalysisResult): ClassicView {
        val yearEl = naYinElement(chart.yearPillar.naYin)
        val dayEl = naYinElement(chart.dayPillar.naYin)
        val hourEl = naYinElement(chart.hourPillar.naYin)

        val relation = when {
            yearEl == null || dayEl == null -> "两者生克无从判定"
            yearEl == dayEl -> "同气比和，禀受与自性一致，行事少内外之争"
            yearEl.generates() == dayEl -> "年生命，根基滋养自身，早年得荫"
            dayEl.generates() == yearEl -> "日生命，自身反哺根基，早年多耗"
            yearEl.overcomes() == dayEl -> "年克日，根基压制自身，早年多受约束"
            dayEl.overcomes() == yearEl -> "日克年，自身反制根基，早年多逆反"
            else -> "两者无直接生克，各循其气"
        }

        val reading = buildString {
            append("年柱纳音${chart.yearPillar.naYin}（${yearEl?.cn ?: "—"}），")
            append("日柱纳音${chart.dayPillar.naYin}（${dayEl?.cn ?: "—"}），")
            append("时柱纳音${chart.hourPillar.naYin}（${hourEl?.cn ?: "—"}）。")
            append("以纳音论，${relation}。")
            append("纳音所论是禀气之厚薄，与子平以日干论旺衰不是同一套语言：前者说材质，后者说力量。")
        }

        return ClassicView(
            classic = Classic.WU_XING_JING_JI,
            reading = reading,
            divergence = "纳音讲材质——「炉中火」与「山下火」同是火而性不同；" +
                "子平派自清代起就把纳音视为不急之务，两家对同一柱的解读可以完全不相干。"
        )
    }

    private fun liXuZhong(chart: BaziChart, a: AnalysisResult): ClassicView {
        val yearGan = chart.yearPillar.gan
        val monthGan = chart.monthPillar.gan
        val dayGan = chart.dayPillar.gan
        val yearEl = yearGan.wuXing
        val percent = a.strength.elementScores.first { it.wuXing == yearEl }.percent

        val reading = buildString {
            append("三命之法以年为本：年干${yearGan.cn}为命主，月干${monthGan.cn}为父母兄弟，日干${dayGan.cn}为己身。")
            append("以年干${yearGan.cn}论，其气属${yearEl.cn}，占全局 %.1f%%，".format(percent * 100))
            append(if (percent >= 0.3) "本命之气不弱，出身有可倚处。" else "本命之气单薄，出身所给予者有限。")
            append("再以年与日相较：")
            append(
                if (yearEl == chart.dayMaster.wuXing)
                    "同气，己身与所禀一脉相承，行事少有内在矛盾；"
                else
                    "异气，年干${yearGan.cn}与日干${dayGan.cn}不同气，古法会判此人一生须在" +
                        "「家世所期」与「自身所欲」之间反复取舍。"
            )
        }

        return ClassicView(
            classic = Classic.LI_XU_ZHONG,
            reading = reading,
            divergence = "这是十家里最根本的一条分歧：李虚中以年干为命主，子平法以日干为命主。" +
                "同一张盘，两家认的「我」根本不是同一个字，旺衰、用神、六亲的结论都可能完全不同——" +
                "不是谁算错，而是出发点不同。"
        )
    }

    // ---------------------------------------------------------------- 断病

    private const val NO_ILLNESS = "无明显之病"

    private data class Illness(val name: String, val detail: String, val cure: String)

    /**
     * 神峰通考式的「病」。
     *
     * 五类常见病按分数判：财重身轻、官杀混杂、印重泄路不通、食伤泄身太过、官杀攻身、财旺比劫争。
     * 都不成立则判无病——「无病」本身也是一个结论，不该为凑病药而硬找。
     */
    private fun diagnose(chart: BaziChart, a: AnalysisResult): Illness {
        val me = chart.dayMaster.wuXing
        fun pct(wx: WuXing): Double = a.strength.elementScores.first { it.wuXing == wx }.percent

        val self = pct(me)
        val output = pct(me.generates())
        val wealth = pct(me.overcomes())
        val officer = pct(me.overcomeBy())
        val resource = pct(me.generatedBy())
        val weak = a.strength.ratio < 0.45

        val officerGods = chart.pillars.mapNotNull { it.ganTenGod }
            .filter { it.category == TenGodCategory.OFFICER }
            .map { it.cn }
            .distinct()

        return when {
            weak && wealth >= 0.28 -> Illness(
                "财重身轻",
                "财星占 %.1f%% 而帮身仅 %.1f%%，身不任财，见财反为累".format(wealth * 100, a.strength.ratio * 100),
                "比劫与印绶——先扶其身，方能任财"
            )
            officerGods.size >= 2 -> Illness(
                "官杀混杂",
                "天干并见${officerGods.joinToString("与")}，官杀不分，制身之力杂而不专",
                "去其一留其一——或合杀、或用食伤制杀，使命局一清"
            )
            self + resource >= 0.62 && output + wealth <= 0.22 -> Illness(
                "印重身旺而泄路不通",
                "印与比劫合计 %.1f%%，食伤财星仅 %.1f%%，气聚而不散，才具难以落地".format(
                    (self + resource) * 100, (output + wealth) * 100
                ),
                "食伤与财星——开其泄路，令气有去处"
            )
            output >= 0.34 && weak -> Illness(
                "食伤泄身太过",
                "食伤占 %.1f%%，日主反弱，才思外溢而精力不继".format(output * 100),
                "印绶——以印制食伤，收其放逸"
            )
            officer >= 0.32 && weak -> Illness(
                "官杀攻身",
                "官杀占 %.1f%%，日主受制，压力长期大于承受力".format(officer * 100),
                "印绶化杀、比劫帮身——以印转其克为生"
            )
            wealth >= 0.3 && self >= 0.25 -> Illness(
                "财旺而比劫争",
                "财星占 %.1f%%、比劫占 %.1f%%，财为众所共逐，易起分夺".format(wealth * 100, self * 100),
                "官杀——以官杀制比劫，使财有所归"
            )
            else -> Illness(
                NO_ILLNESS,
                "五行分布" + a.strength.elementScores.joinToString("、") {
                    "${it.wuXing.cn}%.1f%%".format(it.percent * 100)
                } + "，未见一端独重而压制全局",
                "无须强求，顺其气势即可"
            )
        }
    }

    // ---------------------------------------------------------------- 工具

    /**
     * 查格神被何神所破。
     *
     * 只认四正格的破法：伤官见官、劫财夺财、财星破印、枭神夺食。
     * 七杀逢食神是「食神制杀」、伤官见正印是「伤官配印」，都是成格而非破格，故不判。
     */
    private fun findBreaker(chart: BaziChart, patternGod: TenGod?): String? {
        if (patternGod == null) return null
        val breaker = when (patternGod) {
            TenGod.ZHENG_GUAN -> TenGod.SHANG_GUAN
            TenGod.ZHENG_CAI, TenGod.PIAN_CAI -> TenGod.JIE_CAI
            TenGod.ZHENG_YIN, TenGod.PIAN_YIN -> TenGod.ZHENG_CAI
            TenGod.SHI_SHEN -> TenGod.PIAN_YIN
            else -> return null
        }
        return chart.pillars.mapNotNull { it.ganTenGod }.firstOrNull { it == breaker }?.cn
    }

    /** 纳音末字即五行，六十纳音无一例外。 */
    private fun naYinElement(naYin: String): WuXing? {
        val last = naYin.lastOrNull()?.toString() ?: return null
        return WuXing.entries.firstOrNull { it.cn == last }
    }
}
