package com.bazi.engine

import com.bazi.calendar.BirthInput
import com.bazi.calendar.ResolvedMoment
import java.time.LocalDateTime

/** 藏干 + 对应的十神。 */
data class HiddenStemTenGod(val hidden: HiddenStem, val tenGod: TenGod)

/** 一柱。 */
data class Pillar(
    val position: PillarPosition,
    val gan: TianGan,
    val zhi: DiZhi,
    val ganZhi: String,
    val naYin: String,
    /** 日柱天干为日主本身，故为 null。 */
    val ganTenGod: TenGod?,
    val hiddenStems: List<HiddenStemTenGod>,
    /** 日主在此支的十二长生状态。 */
    val growthPhase: GrowthPhase,
    /** 此支是否落空亡。 */
    val isEmptyBranch: Boolean
) {
    /** 本气十神，格局与运程判定中最常用的一个。 */
    val mainHiddenTenGod: TenGod get() = hiddenStems.first().tenGod
}

/** 起运信息。 */
data class StartLuckInfo(
    /** true = 顺排（阳男阴女）。 */
    val forward: Boolean,
    val yearCount: Int,
    val monthCount: Int,
    val dayCount: Int,
    /** 交运时刻（公历）。 */
    val startTime: LocalDateTime,
    /** 起运虚岁。 */
    val startAge: Int
) {
    val text: String
        get() = buildString {
            append(if (forward) "顺排" else "逆排").append("　")
            if (yearCount > 0) append(yearCount).append("年")
            if (monthCount > 0) append(monthCount).append("个月")
            if (dayCount > 0) append(dayCount).append("天")
            append("起运")
        }
}

/** 大运。 */
data class LuckPillar(
    val index: Int,
    val startAge: Int,
    val endAge: Int,
    val startYear: Int,
    val endYear: Int,
    val gan: TianGan,
    val zhi: DiZhi,
    val ganZhi: String,
    val ganTenGod: TenGod,
    val zhiMainTenGod: TenGod,
    val growthPhase: GrowthPhase,
    val naYin: String,
    /** 以本命日柱旬空衡量，运支是否落空。 */
    val isEmptyBranch: Boolean
)

/** 流年。 */
data class AnnualFortune(
    val year: Int,
    /** 虚岁。 */
    val nominalAge: Int,
    val gan: TianGan,
    val zhi: DiZhi,
    val ganZhi: String,
    val ganTenGod: TenGod,
    /** 所属大运序号，-1 表示尚未起运。 */
    val luckIndex: Int,
    val luckGanZhi: String,
    val naYin: String,
    val isEmptyBranch: Boolean
)

/**
 * 流月。
 *
 * 月建以「节」为界，寅月自立春起，丑月落于次年小寒。
 * [isCurrent] 由参考日期与节气时刻比较得出，仅用于在列表中标记。
 */
data class MonthlyFortune(
    /** 0 = 寅月（正月），11 = 丑月（腊月）。 */
    val index: Int,
    /** 正月、二月……腊月。 */
    val monthName: String,
    val gan: TianGan,
    val zhi: DiZhi,
    val ganZhi: String,
    /** 本节交节时刻，即本月起点。 */
    val startTime: LocalDateTime,
    /** 下一节交节时刻，即本月终点。 */
    val endTime: LocalDateTime,
    val ganTenGod: TenGod,
    val zhiMainTenGod: TenGod,
    val naYin: String,
    val isEmptyBranch: Boolean,
    val isCurrent: Boolean
)

/**
 * 命盘。引擎与 UI 之间唯一的契约对象。
 *
 * UI 只渲染、不计算；引擎只计算、不关心展示。
 */
data class BaziChart(
    val input: BirthInput,
    val moment: ResolvedMoment,
    val pillars: List<Pillar>,
    val dayMaster: TianGan,
    val startLuck: StartLuckInfo,
    val luckPillars: List<LuckPillar>,
    val annuals: List<AnnualFortune>,
    val fetalOrigin: String,
    val fetalBreath: String,
    /** 命宫。 */
    val ownSign: String,
    /** 身宫。 */
    val bodySign: String,
    val voidBranches: List<DiZhi>,
    val relations: List<BranchRelation>
) {
    fun pillar(position: PillarPosition): Pillar = pillars.first { it.position == position }

    val yearPillar: Pillar get() = pillar(PillarPosition.YEAR)
    val monthPillar: Pillar get() = pillar(PillarPosition.MONTH)
    val dayPillar: Pillar get() = pillar(PillarPosition.DAY)
    val hourPillar: Pillar get() = pillar(PillarPosition.HOUR)

    /** 四柱干支文本，如「丙寅 癸巳 癸酉 壬子」。 */
    val ganZhiText: String get() = pillars.joinToString(" ") { it.ganZhi }

    /** 月令（月支）。 */
    val monthCommand: DiZhi get() = monthPillar.zhi

    /** 查某年所属大运。 */
    fun luckOfYear(year: Int): LuckPillar? =
        luckPillars.firstOrNull { year in it.startYear..it.endYear }
}
