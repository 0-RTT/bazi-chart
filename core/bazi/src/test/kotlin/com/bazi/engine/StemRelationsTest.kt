package com.bazi.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 天干关系。
 *
 * 与地支关系分开的理据在于两者的判定完全不同：地支有六合、三合、半合、三会、
 * 六冲、相刑、六害七类；天干只论五合与相冲，没有刑，也没有半合。
 *
 * 这里只钉「有没有合」与「合化何物」，**不钉化与不化**——那要看月令、
 * 要看有无妒合争合，各家分寸不一，属于读盘人的判断，不属于引擎。
 */
class StemRelationsTest {

    @Test
    @DisplayName("五合十干两两成对：甲己土、乙庚金、丙辛水、丁壬木、戊癸火")
    fun combinePairs() {
        val pairs = listOf(
            TianGan.JIA to TianGan.JI to WuXing.TU,
            TianGan.YI to TianGan.GENG to WuXing.JIN,
            TianGan.BING to TianGan.XIN to WuXing.SHUI,
            TianGan.DING to TianGan.REN to WuXing.MU,
            TianGan.WU to TianGan.GUI to WuXing.HUO
        )
        pairs.forEach { (pair, element) ->
            val (a, b) = pair
            assertTrue(StemRelations.combines(a, b), "${a.cn}${b.cn}应五合")
            assertTrue(StemRelations.combines(b, a), "${b.cn}${a.cn}应五合（合无方向）")
            assertEquals(element, StemRelations.combineElement(a, b), "${a.cn}${b.cn}合化${element.cn}")
            assertEquals(element, StemRelations.combineElement(b, a), "反向合化应相同")
        }
        // 五合恰是十干一一配对，共五组
        val seen = mutableSetOf<Set<TianGan>>()
        TianGan.entries.forEach { a ->
            TianGan.entries.forEach { b ->
                if (a != b && StemRelations.combines(a, b)) seen += setOf(a, b)
            }
        }
        assertEquals(5, seen.size, "五合应恰为五组，实得 ${seen.size}")
        assertTrue(seen.all { it.size == 2 }, "五合不应出现自合")
    }

    @Test
    @DisplayName("相冲四组：甲庚、乙辛、丙壬、丁癸；戊己居中不冲")
    fun clashPairs() {
        val four = listOf(
            TianGan.JIA to TianGan.GENG,
            TianGan.YI to TianGan.XIN,
            TianGan.BING to TianGan.REN,
            TianGan.DING to TianGan.GUI
        )
        four.forEach { (a, b) ->
            assertTrue(StemRelations.clashes(a, b), "${a.cn}${b.cn}应相冲")
            assertTrue(StemRelations.clashes(b, a), "相冲无方向")
        }
        // 戊己属中央土，四隅之冲不涉中央
        listOf(TianGan.WU, TianGan.JI).forEach { gan ->
            TianGan.entries.forEach { other ->
                if (other != gan) {
                    assertFalse(StemRelations.clashes(gan, other), "${gan.cn}${other.cn}不应相冲")
                }
            }
        }
        // 冲与合不重叠：同一对干支不能既合又冲
        TianGan.entries.forEach { a ->
            TianGan.entries.forEach { b ->
                if (a != b) {
                    assertFalse(
                        StemRelations.combines(a, b) && StemRelations.clashes(a, b),
                        "${a.cn}${b.cn}不能既合又冲"
                    )
                }
            }
        }
    }

    @Test
    @DisplayName("不合的两干不返回合化五行")
    fun noCombineNoElement() {
        assertNull(StemRelations.combineElement(TianGan.JIA, TianGan.YI))
        assertNull(StemRelations.combineElement(TianGan.BING, TianGan.BING))
        assertFalse(StemRelations.combines(TianGan.BING, TianGan.BING), "同干不自合")
    }

    @Test
    @DisplayName("全盘枚举：四干六对，索引与柱位一致")
    fun betweenIndexes() {
        // 年甲、月己、日丙、时壬：甲己五合，丙壬相冲
        val gans = listOf(TianGan.JIA, TianGan.JI, TianGan.BING, TianGan.REN)
        val rels = StemRelations.between(gans)

        val combine = rels.single { it.type == "五合" }
        assertEquals(0, combine.aIndex, "甲在年柱")
        assertEquals(1, combine.bIndex, "己在月柱")
        assertEquals(WuXing.TU, combine.element, "甲己合化土")

        val clash = rels.single { it.type == "相冲" }
        assertEquals(2, clash.aIndex, "丙在日柱")
        assertEquals(3, clash.bIndex, "壬在时柱")

        // 六对里只有这两对成立
        assertEquals(2, rels.size, "应只有一组五合与一组相冲，实得 $rels")
    }

    @Test
    @DisplayName("无关系时返回空表，不返回 null 也不抛异常")
    fun emptyResult() {
        // 甲甲丙戊：同干不自合，戊不冲
        val rels = StemRelations.between(
            listOf(TianGan.JIA, TianGan.JIA, TianGan.BING, TianGan.WU)
        )
        assertTrue(rels.isEmpty(), "不应有关系，实得 $rels")
    }

    @Test
    @DisplayName("合化五行与地支六合的口径各自独立，互不干扰")
    fun independentFromBranch() {
        // 地支午未六合无化气（engine 记为 LIU_HE，element 为空）；
        // 天干甲己五合化土。两者都叫「合」，但一张表管一件事。
        val ganHe = StemRelations.combineElement(TianGan.JIA, TianGan.JI)
        assertEquals(WuXing.TU, ganHe)
        val branchHe = BranchRelations.scan(
            listOf(PillarPosition.DAY to DiZhi.WU, PillarPosition.HOUR to DiZhi.WEI)
        ).filter { it.type == RelationType.LIU_HE }
        assertTrue(branchHe.isNotEmpty(), "午未应六合")
    }
}
