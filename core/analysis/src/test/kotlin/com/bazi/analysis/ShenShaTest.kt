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
 * 三张**构造盘**（合法干支组合，非真实命例，不含任何出生时间或地点），
 * 逐柱集合必须**完全相等**：
 *
 * 构造盘一 丁酉 甲申 丙午 壬寅 4 / 5 / 8 / 8
 * 构造盘二 乙巳 丁卯 庚申 壬辰 4 / 2 / 7 / 5
 * 构造盘三 丁巳 戊申 丙辰 癸未 4 / 7 / 4 / 4
 *
 * 为什么要多张：单张盘只能证明「查得到」，不能证明「不乱查」。
 * 很多查法（天乙、国印、金舆、福星、亡神、红艳）是「年干还是日干」
 * 「年支还是日支」的取舍，一个样本里两种口径往往同时成立，
 * 只有换一张盘才看得出哪一种是错的。三张合起来覆盖全部 31 种神煞。
 */
@DisplayName("神煞：查法与宫位")
class ShenShaTest {

    /** 构造盘一：丁酉 甲申 丙午 壬寅。日柱丙午旬空寅卯。 */
    private fun caseA() = ShenShaInput(
        yearGan = TianGan.DING, yearZhi = DiZhi.YOU,
        monthGan = TianGan.JIA, monthZhi = DiZhi.SHEN,
        dayGan = TianGan.BING, dayZhi = DiZhi.WU,
        hourGan = TianGan.REN, hourZhi = DiZhi.YIN,
        yearNaYin = WuXing.HUO,
        male = true,
        voidBranches = listOf(DiZhi.YIN, DiZhi.MAO)
    )

    /** 构造盘二：乙巳 丁卯 庚申 壬辰。日柱庚申旬空子丑。 */
    private fun caseB() = ShenShaInput(
        yearGan = TianGan.YI, yearZhi = DiZhi.SI,
        monthGan = TianGan.DING, monthZhi = DiZhi.MAO,
        dayGan = TianGan.GENG, dayZhi = DiZhi.SHEN,
        hourGan = TianGan.REN, hourZhi = DiZhi.CHEN,
        yearNaYin = WuXing.HUO,
        male = false,
        voidBranches = listOf(DiZhi.ZI, DiZhi.CHOU)
    )

    /** 构造盘三：丁巳 戊申 丙辰 癸未。日柱丙辰旬空子丑。 */
    private fun caseC() = ShenShaInput(
        yearGan = TianGan.DING, yearZhi = DiZhi.SI,
        monthGan = TianGan.WU, monthZhi = DiZhi.SHEN,
        dayGan = TianGan.BING, dayZhi = DiZhi.CHEN,
        hourGan = TianGan.GUI, hourZhi = DiZhi.WEI,
        yearNaYin = WuXing.TU,
        male = true,
        voidBranches = listOf(DiZhi.ZI, DiZhi.CHOU)
    )

    private fun byPosition(input: ShenShaInput): Map<PillarPosition, Set<String>> {
        val hits = ShenShaAnalyzer.analyze(input)
        return PillarPosition.entries.associateWith { pos ->
            hits.filter { pos in it.positions }.map { it.name }.toSet()
        }
    }

    private fun assertPillars(
        input: ShenShaInput,
        year: Set<String>,
        month: Set<String>,
        day: Set<String>,
        hour: Set<String>
    ) {
        val got = byPosition(input)
        assertEquals(year, got.getValue(PillarPosition.YEAR), "年柱")
        assertEquals(month, got.getValue(PillarPosition.MONTH), "月柱")
        assertEquals(day, got.getValue(PillarPosition.DAY), "日柱")
        assertEquals(hour, got.getValue(PillarPosition.HOUR), "时柱")
    }

    @Test
    @DisplayName("构造盘一：丁酉 甲申 丙午 壬寅，逐柱集合相符")
    fun caseOne() {
        assertPillars(
            caseA(),
            year = setOf("天乙贵人", "太极贵人", "月德合", "童子煞"),
            month = setOf("亡神", "德秀贵人", "文昌", "金舆", "驿马"),
            day = setOf(
                "六秀日", "勾绞煞", "孤鸾煞", "德秀贵人",
                "桃花", "红鸾", "羊刃", "阴差阳错"
            ),
            hour = setOf(
                "劫煞", "国印贵人", "德秀贵人", "月德贵人",
                "福星贵人", "空亡", "童子煞", "红艳煞"
            )
        )
    }

    @Test
    @DisplayName("构造盘二：乙巳 丁卯 庚申 壬辰，逐柱集合相符")
    fun caseTwo() {
        assertPillars(
            caseB(),
            year = setOf("劫煞", "天德合", "德秀贵人", "金舆"),
            month = setOf("德秀贵人", "福星贵人"),
            day = setOf(
                "亡神", "八专日", "勾绞煞", "天乙贵人",
                "天德贵人", "孤辰", "禄神"
            ),
            hour = setOf("华盖", "国印贵人", "天喜", "寡宿", "德秀贵人")
        )
    }

    @Test
    @DisplayName("构造盘三：丁巳 戊申 丙辰 癸未，逐柱集合相符")
    fun caseThree() {
        assertPillars(
            caseC(),
            year = setOf("劫煞", "月德合", "禄神", "童子煞"),
            month = setOf("亡神", "勾绞煞", "天德合", "孤辰", "德秀贵人", "文昌", "金舆"),
            day = setOf("天喜", "寡宿", "德秀贵人", "童子煞"),
            hour = setOf("丧门", "天德贵人", "德秀贵人", "金舆")
        )
    }

    @Test
    @DisplayName("华盖不自指：四库之支属本局时，本柱不取自己")
    fun canopyNotSelf() {
        // 构造盘二的时支辰属申子辰，华盖正是辰。
        // 但基准是年支巳、日支申，不是辰，所以时柱取到华盖是对的。
        assertTrue("华盖" in byPosition(caseB()).getValue(PillarPosition.HOUR))
    }

    @Test
    @DisplayName("红艳煞只论日干，不并查年干")
    fun hongYanDayGanOnly() {
        // 构造盘一：丙日干见寅（时支）——正命中；若把年干丁（见午）一并算，
        // 日柱会多一项，通行工具并不列。
        assertTrue("红艳煞" in byPosition(caseA()).getValue(PillarPosition.HOUR))
        assertFalse("红艳煞" in byPosition(caseA()).getValue(PillarPosition.DAY))
    }

    @Test
    @DisplayName("将星不再生成——三合局中神恒中本支，只会制造噪声")
    fun noJiangXing() {
        val names = byPosition(caseA()).values.flatten() +
            byPosition(caseB()).values.flatten() +
            byPosition(caseC()).values.flatten()
        assertFalse("将星" in names, "不应出现将星，实得 $names")
    }

    @Test
    @DisplayName("日柱专有神煞不参与运柱查询")
    fun dayOnlyRulesSkipLuckPillars() {
        // 丙午正是阴差阳错、六秀日、孤鸾煞三条的干支；
        // 但作为大运或流年的一柱，它没有「日柱」身份，这三条不该出现。
        val marks = ShenShaAnalyzer.marksOn(caseA(), TianGan.BING, DiZhi.WU).map { it.name }
        assertFalse("阴差阳错" in marks, "运柱不应查阴差阳错，实得 $marks")
        assertFalse("六秀日" in marks, "运柱不应查六秀日，实得 $marks")
        assertFalse("孤鸾煞" in marks, "运柱不应查孤鸾煞，实得 $marks")
        assertTrue("羊刃" in marks, "运柱仍应查羊刃，实得 $marks")
    }

    @Test
    @DisplayName("同一神煞不重复列出")
    fun noDuplicateNames() {
        val names = ShenShaAnalyzer.analyze(caseC()).map { it.name }
        assertEquals(names.size, names.distinct().size, "出现重复神煞：$names")
    }

    @Test
    @DisplayName("纳音末字取五行，用于童子煞")
    fun naYinElement() {
        assertEquals(WuXing.TU, ShenShaInput.naYinElement("城头土"))
        assertEquals(WuXing.JIN, ShenShaInput.naYinElement("剑锋金"))
        assertEquals(WuXing.SHUI, ShenShaInput.naYinElement("天河水"))
    }
}
