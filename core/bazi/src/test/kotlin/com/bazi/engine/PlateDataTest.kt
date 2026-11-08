package com.bazi.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 四柱表数据的回归。
 *
 * 基准取自一张已核对过的命盘：己卯 甲戌 丙午 壬辰（乾造）。
 * 四柱的「自坐」与「空亡」都能逐柱手算，正好用来钉住两处最容易混的字段：
 *
 * - 自坐 = 本柱天干坐本柱地支的长生，与日主无关；
 * - 空亡 = 本柱自身干支所属之旬的两个空支，四柱各不相同。
 */
@DisplayName("四柱表：自坐与本柱空亡")
class PlateDataTest {

    @Test
    @DisplayName("自坐按本柱天干取长生，与日主无关")
    fun ownGrowthPhase() {
        assertEquals(GrowthPhase.BING, GrowthPhaseTable.of(TianGan.JI, DiZhi.MAO))
        assertEquals(GrowthPhase.YANG, GrowthPhaseTable.of(TianGan.JIA, DiZhi.XU))
        assertEquals(GrowthPhase.DI_WANG, GrowthPhaseTable.of(TianGan.BING, DiZhi.WU))
        assertEquals(GrowthPhase.MU, GrowthPhaseTable.of(TianGan.REN, DiZhi.CHEN))
    }

    @Test
    @DisplayName("星运与自坐在日柱必然相同，其余三柱不同")
    fun dayPillarCoincides() {
        // 日柱就是日主坐日支，两种取法算的是同一件事。
        assertEquals(
            GrowthPhaseTable.of(TianGan.BING, DiZhi.WU),
            GrowthPhaseTable.of(TianGan.BING, DiZhi.WU)
        )
        // 年柱若按日主取则是丙坐卯（沐浴），按自坐取则是己坐卯（病），二者必须不同。
        assertEquals(GrowthPhase.MU_YU, GrowthPhaseTable.of(TianGan.BING, DiZhi.MAO))
        assertEquals(GrowthPhase.BING, GrowthPhaseTable.of(TianGan.JI, DiZhi.MAO))
    }

    @Test
    @DisplayName("本柱空亡逐柱不同：己卯申酉、甲戌申酉、丙午寅卯、壬辰午未")
    fun perPillarVoidBranches() {
        fun void(index: Int) = BaziCalculator.voidBranches(index).map { it.cn }

        assertEquals(listOf("申", "酉"), void(15)) // 己卯
        assertEquals(listOf("申", "酉"), void(10)) // 甲戌
        assertEquals(listOf("寅", "卯"), void(42)) // 丙午
        assertEquals(listOf("午", "未"), void(28)) // 壬辰
    }

    @Test
    @DisplayName("每旬十支配两空支，六十甲子逐一圈查不漏")
    fun voidBranchesCoverAllCycles() {
        repeat(60) { index ->
            val void = BaziCalculator.voidBranches(index)
            assertEquals(2, void.size, "第 $index 旬空应恰为两支")
            // 空支不得是本柱自身地支——自己空自己没有意义。
            val own = DiZhi.entries[index % 12]
            assertEquals(false, own in void, "第 $index 的空支不应包含本柱地支 ${own.cn}")
        }
    }
}
