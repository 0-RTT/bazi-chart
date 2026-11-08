package com.bazi.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 地支刑冲合害的回归。
 *
 * 这里盯的是一个真实缺陷：早前三刑判定写成「数组长度等于 2」，只有子卯能命中，
 * 寅巳申与丑戌未组内的两两相刑全部漏扫。所以下面的用例以这两组为重点。
 */
class BranchRelationsTest {

    private fun scan(vararg branches: DiZhi): List<BranchRelation> =
        BranchRelations.scan(
            branches.mapIndexed { i, zhi ->
                PillarPosition.entries[i] to zhi
            }
        )

    private fun notesOf(vararg branches: DiZhi): List<String> =
        scan(*branches).map { it.note }

    @Test
    @DisplayName("三刑组内两两即相刑：寅巳、巳申、寅申")
    fun yinSiShenPairwise() {
        assertTrue(notesOf(DiZhi.YIN, DiZhi.SI).contains("寅巳相刑"), "寅巳应相刑")
        assertTrue(notesOf(DiZhi.SI, DiZhi.SHEN).contains("巳申相刑"), "巳申应相刑")
        assertTrue(notesOf(DiZhi.YIN, DiZhi.SHEN, DiZhi.ZI).contains("寅申相刑"), "寅申应相刑")
    }

    @Test
    @DisplayName("三刑组内两两即相刑：丑戌、戌未、丑未")
    fun chouXuWeiPairwise() {
        assertTrue(notesOf(DiZhi.CHOU, DiZhi.XU).contains("丑戌相刑"), "丑戌应相刑")
        assertTrue(notesOf(DiZhi.XU, DiZhi.WEI).contains("戌未相刑"), "戌未应相刑")
        assertTrue(notesOf(DiZhi.CHOU, DiZhi.WEI, DiZhi.ZI).contains("丑未相刑"), "丑未应相刑")
    }

    @Test
    @DisplayName("三支俱全时合并为一条「三刑俱全」，不再罗列两两")
    fun fullXingCollapses() {
        val notes = notesOf(DiZhi.YIN, DiZhi.SI, DiZhi.SHEN, DiZhi.ZI)
        assertTrue(notes.any { it == "寅巳申三刑俱全" }, "应有三刑俱全：$notes")
        assertFalse(notes.any { it == "寅巳相刑" }, "三刑俱全后不应再列寅巳相刑：$notes")
    }

    @Test
    @DisplayName("子卯相刑、辰午酉亥自刑")
    fun ziMaoAndSelfXing() {
        assertTrue(notesOf(DiZhi.ZI, DiZhi.MAO).contains("子卯相刑"), "子卯应相刑")
        assertTrue(notesOf(DiZhi.CHEN, DiZhi.CHEN).any { it == "辰辰自刑" }, "辰辰应自刑")
        assertFalse(notesOf(DiZhi.YIN, DiZhi.YIN).any { it.contains("自刑") }, "寅不应自刑")
    }

    @Test
    @DisplayName("半合必含旺神：寅午为半合，寅戌不成半合")
    fun halfCombination() {
        val withCenter = scan(DiZhi.YIN, DiZhi.WU, DiZhi.ZI)
        assertTrue(
            withCenter.any { it.type == RelationType.BAN_HE && it.note == "寅午半合火局" },
            "寅午应半合火局：${withCenter.map { it.note }}"
        )
        val noCenter = scan(DiZhi.YIN, DiZhi.XU, DiZhi.ZI)
        assertFalse(
            noCenter.any { it.type == RelationType.BAN_HE },
            "寅戌无旺神，不应成半合：${noCenter.map { it.note }}"
        )
    }

    @Test
    @DisplayName("三支齐备时成三合局，不再报半合")
    fun fullCombinationBeatsHalf() {
        val rels = scan(DiZhi.YIN, DiZhi.WU, DiZhi.XU, DiZhi.ZI)
        assertTrue(rels.any { it.type == RelationType.SAN_HE && it.note == "三合火局" }, "应成三合火局")
        assertFalse(rels.any { it.type == RelationType.BAN_HE }, "成局后不应再报半合")
    }

    @Test
    @DisplayName("六合、六冲、六害")
    fun basicPairs() {
        assertTrue(notesOf(DiZhi.ZI, DiZhi.CHOU).contains("子丑六合"), "子丑六合")
        assertTrue(notesOf(DiZhi.ZI, DiZhi.WU).contains("子午相冲"), "子午相冲")
        assertTrue(notesOf(DiZhi.ZI, DiZhi.WEI).contains("子未相害"), "子未相害")
    }

    @Test
    @DisplayName("措辞为「子午相冲」而非「子相冲午」")
    fun wording() {
        val note = notesOf(DiZhi.ZI, DiZhi.WU).first { it.contains("冲") }
        assertEquals("子午相冲", note)
    }
}
