package com.bazi.calendar

import com.tyme.eightchar.EightChar
import com.tyme.eightchar.provider.impl.DefaultEightCharProvider
import com.tyme.eightchar.provider.impl.LunarSect2EightCharProvider
import com.tyme.lunar.LunarDay
import com.tyme.lunar.LunarHour
import com.tyme.lunar.LunarYear
import com.tyme.solar.SolarTerm
import com.tyme.solar.SolarTime
import java.time.LocalDate
import java.time.LocalDateTime

/** 农历日期展示信息。 */
data class LunarDateInfo(
    val year: Int,
    val month: Int,
    val day: Int,
    val isLeapMonth: Boolean,
    val text: String,
    val yearGanZhi: String,
    val zodiac: String
)

/** 节气信息。 */
data class TermInfo(
    val name: String,
    val time: LocalDateTime,
    val isJie: Boolean
)

/** 一次输入的完整历法解析结果。 */
data class ResolvedMoment(
    val clockTime: LocalDateTime,
    val trueSolarTime: LocalDateTime,
    val correction: SolarCorrection,
    val lunar: LunarDateInfo,
    val currentTerm: TermInfo,
    val nextJie: TermInfo,
    val eightChar: EightChar
)

/**
 * 历法桥接层：唯一允许接触 tyme4kt 的地方。
 *
 * 上层（core:bazi / core:analysis）只看到 [ResolvedMoment]，
 * 未来若要换掉历法底座，只需替换本文件。
 */
object CalendarConverter {

    private val PROVIDER_LOCK = Any()

    private const val ZODIAC = "鼠牛虎兔龙蛇马羊猴鸡狗猪"

    /**
     * 把用户输入解析为可直接排盘的时刻。
     *
     * 农历输入先换算出公历钟表时间，再做真太阳时校正——顺序不能反。
     */
    fun resolve(input: BirthInput): ResolvedMoment {
        val clock: LocalDateTime = when (input.calendarType) {
            CalendarType.SOLAR -> LocalDateTime.of(
                input.year, input.month.coerceAtLeast(1), input.day,
                input.hour, input.minute
            )
            CalendarType.LUNAR -> LunarHour.fromYmdHms(
                input.year, input.month, input.day,
                input.hour, input.minute, 0
            ).getSolarTime().toLocalDateTime()
        }

        val corrected = TrueSolarTime.correct(
            clockTime = clock,
            longitude = input.city.longitude,
            dstMinutes = input.sect.dstOffsetMinutes,
            apply = input.sect.useTrueSolarTime
        )

        val solarTime = corrected.trueSolarTime.toSolarTime()
        val lunarDay = solarTime.getLunarHour().lunarDay
        val term = solarTime.getTerm()

        return ResolvedMoment(
            clockTime = clock,
            trueSolarTime = corrected.trueSolarTime,
            correction = corrected.correction,
            lunar = toLunarInfo(lunarDay),
            currentTerm = TermInfo(term.getName(), term.getJulianDay().getSolarTime().toLocalDateTime(), term.isJie()),
            nextJie = findNextJie(term),
            eightChar = withSect(input.sect) { solarTime.getLunarHour().getEightChar() }
        )
    }

    /** 按流派切换 23:00–23:59 的日柱归属。tyme 的 provider 是全局量，故加锁。 */
    private fun <T> withSect(sect: SectConfig, block: () -> T): T = synchronized(PROVIDER_LOCK) {
        LunarHour.provider = when (sect.ziHour) {
            ZiHourSect.LATE -> DefaultEightCharProvider()
            ZiHourSect.EARLY -> LunarSect2EightCharProvider()
        }
        block()
    }

    private fun findNextJie(current: SolarTerm): TermInfo {
        var term = current
        repeat(3) {
            term = term.next(1)
            if (term.isJie()) return term.toInfo()
        }
        return term.toInfo()
    }

    private fun SolarTerm.toInfo(): TermInfo = TermInfo(
        name = getName(),
        time = getJulianDay().getSolarTime().toLocalDateTime(),
        isJie = isJie()
    )

    private fun toLunarInfo(day: LunarDay): LunarDateInfo {
        val rawMonth = day.month
        val isLeap = rawMonth < 0
        val monthValue = if (isLeap) -rawMonth else rawMonth
        val yearGanZhi = LunarYear(day.year).getSixtyCycle().getName()
        val branchIndex = (day.year - 4) % 12
        return LunarDateInfo(
            year = day.year,
            month = monthValue,
            day = day.day,
            isLeapMonth = isLeap,
            text = buildString {
                // 公历年在前、干支年括注：农历年与公历年常常差一岁
                // （公历 1 月出生仍属上一农历年），只写干支会让人以为排错了年。
                append(day.year).append('年')
                if (isLeap) append("闰")
                append(LunarText.monthName(monthValue))
                append(LunarText.dayName(day.day))
                append("（").append(yearGanZhi).append("）")
            },
            yearGanZhi = yearGanZhi,
            zodiac = ZODIAC[((branchIndex % 12) + 12) % 12].toString()
        )
    }

}

/** tyme SolarTime → java.time。 */
fun SolarTime.toLocalDateTime(): LocalDateTime =
    LocalDateTime.of(
        LocalDate.of(year, month, day),
        java.time.LocalTime.of(hour, minute, second)
    )

/** java.time → tyme SolarTime。 */
fun LocalDateTime.toSolarTime(): SolarTime =
    SolarTime.fromYmdHms(year, monthValue, dayOfMonth, hour, minute, second)
