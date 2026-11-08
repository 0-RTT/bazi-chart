package com.bazi.analysis

import com.bazi.engine.BaziChart
import com.bazi.engine.DiZhi
import com.bazi.engine.HiddenStemTable
import com.bazi.engine.TianGan

/** 调候用神条目。 */
data class TiaohouEntry(
    val primary: TianGan,
    val assists: List<TianGan>
)

data class TiaohouResult(
    val entry: TiaohouEntry?,
    /** 四柱中实际出现的用神（透干或藏支）。 */
    val present: List<TianGan>,
    val missing: List<TianGan>,
    val text: String
)

/**
 * 调候。
 *
 * 依《穷通宝鉴》通行本，本质是一张「十天干 × 十二月令」的用神查表，
 * 是命理知识中最适合做成纯数据的一类——故此处只放表，判定逻辑极薄。
 *
 * 说明：不同版本对辅助用神的取舍略有出入，本表以各月**主用**为先，
 * 其余按常用顺序排列，仅取前三参与「用神有无」判定。
 */
object TiaohouAnalyzer {

    private val TABLE: Map<TianGan, Map<DiZhi, TiaohouEntry>> = buildTable()

    fun analyze(chart: BaziChart): TiaohouResult {
        val entry = TABLE[chart.dayMaster]?.get(chart.monthCommand)
            ?: return TiaohouResult(null, emptyList(), emptyList(), "该组合暂无调候数据")

        val allGans = chart.pillars.map { it.gan } +
            chart.pillars.flatMap { HiddenStemTable.stems(it.zhi) }

        val candidates = listOf(entry.primary) + entry.assists
        val present = candidates.filter { it in allGans }
        val missing = candidates.filter { it !in allGans }

        val text = buildString {
            append("主用").append(entry.primary.cn)
            if (entry.assists.isNotEmpty()) {
                append("，辅以").append(entry.assists.joinToString("、") { it.cn })
            }
            append("。")
            if (present.isEmpty()) {
                append("四柱不见用神，调候乏力，需运程补足。")
            } else {
                append("柱中见").append(present.joinToString("、") { it.cn }).append("，")
                append(
                    when (present.size) {
                        1 -> "用神单一，力量有限。"
                        2 -> "用神得助，调候有功。"
                        else -> "用神重叠，调候有力。"
                    }
                )
            }
        }

        return TiaohouResult(entry, present, missing, text)
    }

    fun entryOf(dayMaster: TianGan, monthZhi: DiZhi): TiaohouEntry? =
        TABLE[dayMaster]?.get(monthZhi)

    // ------------------------------------------------------------ 数据表

    private fun buildTable(): Map<TianGan, Map<DiZhi, TiaohouEntry>> = mapOf(
        TianGan.JIA to mapOf(
            DiZhi.YIN to e("丙", "癸"), DiZhi.MAO to e("庚", "丙", "丁"),
            DiZhi.CHEN to e("庚", "丁", "壬"), DiZhi.SI to e("癸", "丁", "庚"),
            DiZhi.WU to e("癸", "丁", "庚"), DiZhi.WEI to e("癸", "丁", "庚"),
            DiZhi.SHEN to e("庚", "丁", "壬"), DiZhi.YOU to e("庚", "丁", "丙"),
            DiZhi.XU to e("庚", "甲", "丁"), DiZhi.HAI to e("庚", "丁", "丙"),
            DiZhi.ZI to e("丁", "庚", "丙"), DiZhi.CHOU to e("丁", "庚", "丙")
        ),
        TianGan.YI to mapOf(
            DiZhi.YIN to e("丙", "癸"), DiZhi.MAO to e("丙", "癸"),
            DiZhi.CHEN to e("癸", "丙", "戊"), DiZhi.SI to e("癸", "丙"),
            DiZhi.WU to e("癸", "丙"), DiZhi.WEI to e("癸", "丙"),
            DiZhi.SHEN to e("丙", "癸"), DiZhi.YOU to e("癸", "丙", "丁"),
            DiZhi.XU to e("癸", "辛"), DiZhi.HAI to e("丙", "戊"),
            DiZhi.ZI to e("丙"), DiZhi.CHOU to e("丙")
        ),
        TianGan.BING to mapOf(
            DiZhi.YIN to e("壬", "庚"), DiZhi.MAO to e("壬", "己"),
            DiZhi.CHEN to e("壬", "甲"), DiZhi.SI to e("壬", "癸", "庚"),
            DiZhi.WU to e("壬", "庚"), DiZhi.WEI to e("壬", "庚"),
            DiZhi.SHEN to e("壬", "戊"), DiZhi.YOU to e("壬", "癸"),
            DiZhi.XU to e("甲", "壬"), DiZhi.HAI to e("甲", "戊", "庚"),
            DiZhi.ZI to e("壬", "戊", "己"), DiZhi.CHOU to e("壬", "甲")
        ),
        TianGan.DING to mapOf(
            DiZhi.YIN to e("甲", "庚"), DiZhi.MAO to e("庚", "甲"),
            DiZhi.CHEN to e("甲", "庚"), DiZhi.SI to e("甲", "庚"),
            DiZhi.WU to e("壬", "庚", "癸"), DiZhi.WEI to e("甲", "壬", "庚"),
            DiZhi.SHEN to e("甲", "庚", "丙"), DiZhi.YOU to e("甲", "庚", "丙"),
            DiZhi.XU to e("甲", "庚", "戊"), DiZhi.HAI to e("甲", "庚"),
            DiZhi.ZI to e("甲", "庚"), DiZhi.CHOU to e("甲", "庚")
        ),
        TianGan.WU to mapOf(
            DiZhi.YIN to e("丙", "甲", "癸"), DiZhi.MAO to e("丙", "甲", "癸"),
            DiZhi.CHEN to e("甲", "丙", "癸"), DiZhi.SI to e("甲", "丙", "癸"),
            DiZhi.WU to e("壬", "甲", "丙"), DiZhi.WEI to e("癸", "丙", "甲"),
            DiZhi.SHEN to e("丙", "癸", "甲"), DiZhi.YOU to e("丙", "癸"),
            DiZhi.XU to e("甲", "丙", "癸"), DiZhi.HAI to e("甲", "丙"),
            DiZhi.ZI to e("丙", "甲"), DiZhi.CHOU to e("丙", "甲")
        ),
        TianGan.JI to mapOf(
            DiZhi.YIN to e("丙", "庚", "甲"), DiZhi.MAO to e("甲", "癸", "丙"),
            DiZhi.CHEN to e("丙", "癸", "甲"), DiZhi.SI to e("癸", "丙"),
            DiZhi.WU to e("癸", "丙"), DiZhi.WEI to e("癸", "丙"),
            DiZhi.SHEN to e("丙", "癸"), DiZhi.YOU to e("丙", "癸"),
            DiZhi.XU to e("甲", "丙", "癸"), DiZhi.HAI to e("丙", "甲", "戊"),
            DiZhi.ZI to e("丙", "甲", "戊"), DiZhi.CHOU to e("丙", "甲", "戊")
        ),
        TianGan.GENG to mapOf(
            DiZhi.YIN to e("戊", "甲", "壬"), DiZhi.MAO to e("丁", "甲", "庚"),
            DiZhi.CHEN to e("甲", "丁", "壬"), DiZhi.SI to e("壬", "戊", "丙"),
            DiZhi.WU to e("壬", "癸"), DiZhi.WEI to e("丁", "甲"),
            DiZhi.SHEN to e("丁", "甲"), DiZhi.YOU to e("丁", "甲", "丙"),
            DiZhi.XU to e("甲", "壬"), DiZhi.HAI to e("丁", "丙"),
            DiZhi.ZI to e("丁", "甲", "丙"), DiZhi.CHOU to e("丙", "丁", "甲")
        ),
        TianGan.XIN to mapOf(
            DiZhi.YIN to e("己", "壬", "庚"), DiZhi.MAO to e("壬", "甲"),
            DiZhi.CHEN to e("壬", "甲"), DiZhi.SI to e("壬", "甲", "癸"),
            DiZhi.WU to e("壬", "己", "癸"), DiZhi.WEI to e("壬", "庚", "甲"),
            DiZhi.SHEN to e("壬", "甲", "戊"), DiZhi.YOU to e("壬", "甲"),
            DiZhi.XU to e("壬", "甲"), DiZhi.HAI to e("壬", "丙"),
            DiZhi.ZI to e("丙", "戊", "壬"), DiZhi.CHOU to e("丙", "壬", "戊")
        ),
        TianGan.REN to mapOf(
            DiZhi.YIN to e("庚", "丙", "戊"), DiZhi.MAO to e("戊", "辛", "庚"),
            DiZhi.CHEN to e("甲", "庚"), DiZhi.SI to e("壬", "辛", "庚"),
            DiZhi.WU to e("癸", "庚", "辛"), DiZhi.WEI to e("辛", "甲"),
            DiZhi.SHEN to e("戊", "丁"), DiZhi.YOU to e("甲", "庚"),
            DiZhi.XU to e("甲", "丙"), DiZhi.HAI to e("戊", "丙", "庚"),
            DiZhi.ZI to e("戊", "丙"), DiZhi.CHOU to e("丙", "丁", "甲")
        ),
        TianGan.GUI to mapOf(
            DiZhi.YIN to e("辛", "丙"), DiZhi.MAO to e("庚", "辛"),
            DiZhi.CHEN to e("丙", "辛", "甲"), DiZhi.SI to e("辛"),
            DiZhi.WU to e("庚", "辛", "壬"), DiZhi.WEI to e("庚", "辛", "壬"),
            DiZhi.SHEN to e("丁"), DiZhi.YOU to e("辛", "丙"),
            DiZhi.XU to e("辛", "甲", "壬"), DiZhi.HAI to e("庚", "辛", "戊"),
            DiZhi.ZI to e("丙", "辛"), DiZhi.CHOU to e("丙", "丁")
        )
    )

    private fun e(primary: String, vararg assists: String): TiaohouEntry =
        TiaohouEntry(
            primary = TianGan.of(primary),
            assists = assists.map { TianGan.of(it) }
        )
}
