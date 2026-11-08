package com.bazi.engine

import com.bazi.calendar.BirthInput
import com.bazi.calendar.CalendarConverter
import com.bazi.calendar.Gender
import com.bazi.calendar.SolarTerms
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
            val cycleIndex = indexOf(cycle)
            Pillar(
                position = position,
                gan = gan,
                zhi = zhi,
                ganZhi = gan.cn + zhi.cn,
                naYin = NaYin.of(cycleIndex),
                ganTenGod = if (position == PillarPosition.DAY) null else TenGod.between(dayMaster, gan),
                hiddenStems = HiddenStemTable.of(zhi).map {
                    HiddenStemTenGod(it, TenGod.between(dayMaster, it.gan))
                },
                growthPhase = GrowthPhaseTable.of(dayMaster, zhi),
                ownGrowthPhase = GrowthPhaseTable.of(gan, zhi),
                voidBranches = voidBranches(cycleIndex),
                isEmptyBranch = zhi in voidBranches
            )
        }

        val startLuck = buildStartLuck(input, moment)
        val luckPillars = buildLuckPillars(input, moment, dayMaster, voidBranches)
        val annuals = buildAnnuals(input, luckPillars, dayMaster, voidBranches, annualSpan)

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
        dayMaster: TianGan,
        voidBranches: List<DiZhi>
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
                growthPhase = GrowthPhaseTable.of(dayMaster, zhi),
                naYin = NaYin.of(ganZhiIndexOf(gan, zhi)),
                isEmptyBranch = zhi in voidBranches
            )
            decade = decade.next(1)
        }
        return out
    }

    private fun buildAnnuals(
        input: BirthInput,
        luckPillars: List<LuckPillar>,
        dayMaster: TianGan,
        voidBranches: List<DiZhi>,
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
                luckGanZhi = luck?.ganZhi ?: "未起运",
                naYin = NaYin.of(ganZhiIndex),
                isEmptyBranch = zhi in voidBranches
            )
        }
    }

    /**
     * 流月。
     *
     * 十二个月建以「节」为界，寅月自立春起算。月干用五虎遁：
     * 甲己之年丙作首、乙庚之岁戊为头、丙辛必定寻庚起、丁壬壬位顺行流、戊癸之年甲寅求。
     * 丑月的终点落在次年立春，属正常的跨年，不是缺陷。
     */
    fun monthliesOf(
        chart: BaziChart,
        year: Int,
        today: java.time.LocalDate = java.time.LocalDate.now()
    ): List<MonthlyFortune> {
        val jie = SolarTerms.jieOfYear(year)
        if (jie.size < 12) return emptyList()
        val nextYearFirst = SolarTerms.jieOfYear(year + 1).firstOrNull()?.time

        val yearGanIndex = ((year - 4) % 10 + 10) % 10
        val firstMonthGan = (yearGanIndex % 5 * 2 + 2) % 10

        return (0 until 12).map { i ->
            val gan = TianGan.entries[(firstMonthGan + i) % 10]
            val zhi = DiZhi.of(SolarTerms.JIE_BRANCHES[i])
            val start = jie[i].time
            val end = if (i < 11) jie[i + 1].time else (nextYearFirst ?: start.plusDays(30))
            MonthlyFortune(
                index = i,
                monthName = MONTH_NAMES[i],
                jieName = SolarTerms.JIE_NAMES[i],
                gan = gan,
                zhi = zhi,
                ganZhi = gan.cn + zhi.cn,
                startTime = start,
                endTime = end,
                ganTenGod = TenGod.between(chart.dayMaster, gan),
                zhiMainTenGod = TenGod.between(chart.dayMaster, HiddenStemTable.mainStem(zhi)),
                naYin = NaYin.of(ganZhiIndexOf(gan, zhi)),
                isEmptyBranch = zhi in chart.voidBranches,
                isCurrent = !today.isBefore(start.toLocalDate()) && today.isBefore(end.toLocalDate())
            )
        }
    }

    private val MONTH_NAMES = arrayOf(
        "正月", "二月", "三月", "四月", "五月", "六月",
        "七月", "八月", "九月", "十月", "冬月", "腊月"
    )

    /**
     * 由天干地支反推六十甲子序号。
     *
     * 大运与流月的干支是现算的，没有 SixtyCycle 对象可查，故用同余条件解出。
     */
    fun ganZhiIndexOf(gan: TianGan, zhi: DiZhi): Int {
        for (i in 0 until 60) {
            if (i % 10 == gan.ordinal && i % 12 == zhi.ordinal) return i
        }
        return 0
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
