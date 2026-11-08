package com.bazi.calendar

import java.time.LocalDateTime

/** 输入历法类型。 */
enum class CalendarType { SOLAR, LUNAR }

enum class Gender { MALE, FEMALE }

/**
 * 子时流派。
 *
 * 23:00–23:59 出生时，日柱算次日还是当日，两派皆有。
 * 默认取 [LATE]（通行子平法：23 时起即为次日子时）。
 */
enum class ZiHourSect { LATE, EARLY }

/**
 * 流派配置。所有存在分歧的口径都收敛到这里，绝不散落在算法内部。
 */
data class SectConfig(
    val ziHour: ZiHourSect = ZiHourSect.LATE,
    val useTrueSolarTime: Boolean = true,
    /**
     * 夏令时手工扣减分钟数。1986–1991 年中国实行夏令时，
     * 若出生时间来自当时钟表且未还原，应填 -60。
     */
    val dstOffsetMinutes: Int = 0
)

/** 出生地。经度东经为正。 */
data class City(
    val name: String,
    val province: String = "",
    val longitude: Double,
    val latitude: Double = 0.0
) {
    val displayName: String get() = if (province.isBlank() || province == name) name else "$province·$name"
}

/** 真太阳时校正明细，逐项保留以便在 UI 上解释给用户看。 */
data class SolarCorrection(
    val longitudeMinutes: Double,
    val equationOfTimeMinutes: Double,
    val dstMinutes: Int
) {
    val totalMinutes: Double get() = longitudeMinutes + equationOfTimeMinutes - dstMinutes
}

/** 校正前后的时刻对照。 */
data class CorrectedMoment(
    val clockTime: LocalDateTime,
    val trueSolarTime: LocalDateTime,
    val correction: SolarCorrection,
    val applied: Boolean
)

/**
 * 排盘输入。
 *
 * @param month 公历传 1..12；农历闰月传负数（如闰二月传 -2）
 */
data class BirthInput(
    val calendarType: CalendarType,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val gender: Gender,
    val city: City,
    val sect: SectConfig = SectConfig()
) {
    val isLeapMonth: Boolean get() = calendarType == CalendarType.LUNAR && month < 0
}
