package com.bazi.analysis

import com.bazi.engine.DiZhi
import com.bazi.engine.PillarPosition
import com.bazi.engine.TianGan
import com.bazi.engine.WuXing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 神煞查法回归。
 *
 * 基准盘「己卯 甲戌 丙午 壬辰」（乾造，年纳音城头土）取自实际排盘工具的实测结果，
 * 各柱神煞数量悬殊（2 / 2 / 9 / 2），七种查法基准全都考到：
 *
 * - 年干查（太极贵人取四季）
 * - 日干查（太极取卯酉、月德取丙、羊刃取午）
 * - 月令查（天德取丙、德秀含丙）
 * - 年支查（天喜取午、勾绞取午与子、桃花取午、华盖取戌）
 * - 日支查（桃花取卯、华盖取戌）
 * - 年纳音查（土命童子煞取辰巳）
 * - 日柱干支查（阴差阳错、六秀日、孤鸾煞）
 */
@DisplayName("神煞：查法与宫位")
class ShenShaTest {

    private fun input(): ShenShaInput = ShenShaInput(
        yearGan = TianGan.JI, yearZhi = DiZhi.MAO,
        monthGan = TianGan.JIA, monthZhi = DiZhi.XU,
        dayGan = TianGan.BING, dayZhi = DiZhi.WU,
        hourGan = TianGan.REN, hourZhi = DiZhi.CHEN,
        yearNaYin = WuXing.TU,
        male = true,
        voidBranches = listOf(DiZhi.YIN, DiZhi.MAO)
    )

    private fun byPosition(): Map<PillarPosition, List<String>> {
        val hits = ShenShaAnalyzer.analyze(input())
        return PillarPosition.entries.associateWith { pos ->
            hits.filter { pos in it.positions }.map { it.name }
        }
    }

    @Test
    @DisplayName("年柱：太极贵人、桃花、空亡")
    fun year() {
        assertEquals(
            listOf("太极贵人", "桃花", "空亡"),
            byPosition().getValue(PillarPosition.YEAR)
        )
    }

    @Test
    @DisplayName("月柱：太极贵人、华盖")
    fun month() {
        assertEquals(
            listOf("太极贵人", "华盖"),
            byPosition().getValue(PillarPosition.MONTH)
        )
    }

    @Test
    @DisplayName("日柱：九项俱全，顺序与通行排盘一致")
    fun day() {
        assertEquals(
            listOf(
                "天德贵人", "月德贵人", "德秀贵人", "阴差阳错",
                "六秀日", "孤鸾煞", "勾绞煞", "天喜", "羊刃"
            ),
            byPosition().getValue(PillarPosition.DAY)
        )
    }

    @Test
    @DisplayName("时柱：太极贵人、童子煞")
    fun hour() {
        assertEquals(
            listOf("太极贵人", "童子煞"),
            byPosition().getValue(PillarPosition.HOUR)
        )
    }

    @Test
    @DisplayName("将星不再生成——三合局中神恒中本支，只会制造噪声")
    fun noJiangXing() {
        val names = byPosition().values.flatten()
        assertFalse("将星" in names, "不应出现将星，实得 $names")
    }

    @Test
    @DisplayName("日柱专有神煞不参与运柱查询")
    fun dayOnlyRulesSkipLuckPillars() {
        // 丙午正是阴差阳错、六秀日、孤鸾煞三条的干支；
        // 但作为大运或流年的一柱，它没有「日柱」身份，这三条不该出现。
        val marks = ShenShaAnalyzer.marksOn(input(), TianGan.BING, DiZhi.WU).map { it.name }
        assertFalse("阴差阳错" in marks, "运柱不应查阴差阳错，实得 $marks")
        assertFalse("六秀日" in marks, "运柱不应查六秀日，实得 $marks")
        assertFalse("孤鸾煞" in marks, "运柱不应查孤鸾煞，实得 $marks")
        assertTrue("羊刃" in marks, "运柱仍应查羊刃，实得 $marks")
    }

    @Test
    @DisplayName("纳音末字取五行，用于童子煞")
    fun naYinElement() {
        assertEquals(WuXing.TU, ShenShaInput.naYinElement("城头土"))
        assertEquals(WuXing.JIN, ShenShaInput.naYinElement("剑锋金"))
        assertEquals(WuXing.SHUI, ShenShaInput.naYinElement("天河水"))
    }
}
