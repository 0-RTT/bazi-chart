package com.bazi.engine

import com.bazi.calendar.BirthInput
import com.bazi.calendar.CalendarConverter
import com.bazi.calendar.Gender
import com.bazi.calendar.toLocalDateTime
import com.bazi.calendar.toSolarTime
import com.tyme.eightchar.ChildLimit
import com.tyme.sixtycycle.SixtyCycle

/**
 * 排盘主流程。
 *
 * 输入 [BirthInput]，输出完整 [BaziChart]。
 * 所有可能与流派相关的地方都通过 [com.bazi.calendar.SectConfig] 收敛，不在此处写死。
 */
object BaziCalculator {

    /** 默认推算十年大运。 */
    private const val LUCK_PILLAR_COUNT = 10

    /** 流年回溯一年、前瞻 defaultSpan 年。 */
    private const val DEFAULT_ANNUAL_SPAN = 100

    fun calculate(input: BirthInput, annualSpan: Int = DEFAULT_ANNUAL_SPAN): BaziChart {
        val moment = CalendarConverter.resolve(input)
        val eightChar = moment.eightChar

        val dayMaster = TianGan.of(eightChar.day.getHeavenStem().getName())
        val dayIndex = indexOf(eightChar.day)
        val voidBranches = voidBranches(dayIndex)

        val positionToCycle = listOf(
            PillarPosition.YEAR to eightChar.year,
            PillarPosition.MONTH to eightChar.month,
            PillarPosition.DAY to eightChar.day,
            PillarPosition.HOUR to eightChar.hour
        )

        val pillars = positionToCycle.map { (position, cycle) ->
            val gan = TianGan.of(cycle.getHeavenStem().getName())
            val zhi = DiZhi.of(cycle.getEarthBranch().getName())
            Pillar(
                position = position,
                gan = gan,
                zhi = zhi,
                ganZhi = gan.cn + zhi.cn,
                naYin = NaYin.of(indexOf(cycle)),
                ganTenGod = if (position == PillarPosition.DAY) null else TenGod.between(dayMaster, gan),
                hiddenStems = HiddenStemTable.of(zhi).map {
                    HiddenStemTenGod(it, TenGod.between(dayMaster, it.gan))
                },
                growthPhase = GrowthPhaseTable.of(dayMaster, zhi),
                isEmptyBranch = zhi in voidBranches
            )
        }

        val startLuck = buildStartLuck(input, moment)
        val luckPillars = buildLuckPillars(input, moment, dayMaster)
        val annuals = buildAnnuals(input, luckPillars, dayMaster, annualSpan)

        return BaziChart(
            input = input,
            moment = moment,
            pillars = pillars,
            dayMaster = dayMaster,
            startLuck = startLuck,
            luckPillars = luckPillars,
            annuals = annuals,
            fetalOrigin = eightChar.getFetalOrigin().getName(),
            fetalBreath = eightChar.getFetalBreath().getName(),
            ownSign = eightChar.getOwnSign().getName(),
            bodySign = eightChar.getBodySign().getName(),
            voidBranches = voidBranches,
            relations = BranchRelations.scan(pillars.map { it.position to it.zhi })
        )
    }

    private fun buildStartLuck(input: BirthInput, moment: com.bazi.calendar.ResolvedMoment): StartLuckInfo {
        val childLimit = ChildLimit.fromSolarTime(
            moment.trueSolarTime.toSolarTime(),
            if (input.gender == Gender.MALE) com.tyme.enums.Gender.MAN else com.tyme.enums.Gender.WOMAN
        )
        return StartLuckInfo(
            forward = childLimit.isForward(),
            yearCount = childLimit.getYearCount(),
            monthCount = childLimit.getMonthCount(),
            dayCount = childLimit.getDayCount(),
            startTime = childLimit.getEndTime().toLocalDateTime(),
            startAge = childLimit.getEndAge()
        )
    }

    private fun buildLuckPillars(
        input: BirthInput,
        moment: com.bazi.calendar.ResolvedMoment,
        dayMaster: TianGan
    ): List<LuckPillar> {
        val childLimit = ChildLimit.fromSolarTime(
            moment.trueSolarTime.toSolarTime(),
            if (input.gender == Gender.MALE) com.tyme.enums.Gender.MAN else com.tyme.enums.Gender.WOMAN
        )
        var decade = childLimit.getStartDecadeFortune()
        val out = mutableListOf<LuckPillar>()
        repeat(LUCK_PILLAR_COUNT) { i ->
            val cycle = decade.getSixtyCycle()
            val gan = TianGan.of(cycle.getHeavenStem().getName())
            val zhi = DiZhi.of(cycle.getEarthBranch().getName())
            out += LuckPillar(
                index = i,
                startAge = decade.getStartAge(),
                endAge = decade.getEndAge(),
                startYear = decade.getStartSixtyCycleYear().year,
                endYear = decade.getEndSixtyCycleYear().year,
                gan = gan,
                zhi = zhi,
                ganZhi = gan.cn + zhi.cn,
                ganTenGod = TenGod.between(dayMaster, gan),
                zhiMainTenGod = TenGod.between(dayMaster, HiddenStemTable.mainStem(zhi)),
                growthPhase = GrowthPhaseTable.of(dayMaster, zhi)
            )
            decade = decade.next(1)
        }
        return out
    }

    private fun buildAnnuals(
        input: BirthInput,
        luckPillars: List<LuckPillar>,
        dayMaster: TianGan,
        span: Int
    ): List<AnnualFortune> {
        val birthYear = input.year
        return (birthYear..birthYear + span).map { year ->
            val ganZhiIndex = ((year - 4) % 60 + 60) % 60
            val gan = TianGan.entries[ganZhiIndex % 10]
            val zhi = DiZhi.entries[ganZhiIndex % 12]
            val luck = luckPillars.firstOrNull { year in it.startYear..it.endYear }
            AnnualFortune(
                year = year,
                nominalAge = year - birthYear + 1,
                gan = gan,
                zhi = zhi,
                ganZhi = gan.cn + zhi.cn,
                ganTenGod = TenGod.between(dayMaster, gan),
                luckIndex = luck?.index ?: -1,
                luckGanZhi = luck?.ganZhi ?: "未起运"
            )
        }
    }

    /** 六十甲子序号。以 NAMES 反查，避免依赖库内部 index 的可见性。 */
    fun indexOf(cycle: SixtyCycle): Int {
        val i = SixtyCycle.NAMES.indexOf(cycle.getName())
        return if (i >= 0) i else 0
    }

    /**
     * 旬空（空亡）。
     *
     * 每一旬十天干配十二地支，多出的两个地支即空亡。
     */
    fun voidBranches(ganZhiIndex: Int): List<DiZhi> {
        val gan = ganZhiIndex % 10
        val zhi = ganZhiIndex % 12
        val first = ((zhi - gan + 10) % 12 + 12) % 12
        return listOf(DiZhi.entries[first], DiZhi.entries[(first + 1) % 12])
    }
}
