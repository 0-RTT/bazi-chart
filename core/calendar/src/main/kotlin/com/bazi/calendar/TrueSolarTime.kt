package com.bazi.calendar

import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/**
 * 真太阳时校正。
 *
 * 排盘软件最常见的错误来源不是四柱公式，而是这里：
 * 用户输入的是钟表时间（东八区北京时间，基准经线 120°E），
 * 而时柱必须以出生地的**真太阳时**为准。
 *
 * 校正由两部分构成：
 * 1. 经度差：每偏离基准经线 1° 折 4 分钟。乌鲁木齐（87.62°E）与北京时间相差约 130 分钟，
 *    足以跨越两个时辰。
 * 2. 均时差（Equation of Time）：地球轨道偏心率与黄赤交角导致真太阳时与平太阳时之间
 *    在 ±16 分钟内波动，用 NOAA 近似式，精度约 ±10 秒。
 *
 * 夏令时不在此自动推断——中国 1986–1991 年实行夏令时，区间边界易错，
 * 交由用户显式填写 [SectConfig.dstOffsetMinutes]，宁可少猜也不猜错。
 */
object TrueSolarTime {

    /** 东八区标准经线。 */
    const val STANDARD_MERIDIAN = 120.0

    /**
     * 均时差（分钟），NOAA 近似式。
     *
     * @param time 当地钟表时刻，用于取年内日序
     */
    fun equationOfTimeMinutes(time: LocalDateTime): Double {
        val dayOfYear = time.dayOfYear
        val hour = time.hour
        val gamma = 2.0 * PI / 365.0 * (dayOfYear - 1 + (hour - 12) / 24.0)
        return 229.18 * (
            0.000075 +
                0.001868 * cos(gamma) -
                0.032077 * sin(gamma) -
                0.014615 * cos(2 * gamma) -
                0.040849 * sin(2 * gamma)
            )
    }

    /** 经度差折算的分钟数。 */
    fun longitudeMinutes(longitude: Double): Double =
        (longitude - STANDARD_MERIDIAN) * 4.0

    /**
     * 执行校正。
     *
     * @param clockTime 用户输入的钟表时间
     * @param longitude 出生地经度（东经为正）
     * @param apply     是否应用校正；为 false 时原样返回，仅给出校正量供参考
     */
    fun correct(
        clockTime: LocalDateTime,
        longitude: Double,
        dstMinutes: Int = 0,
        apply: Boolean = true
    ): CorrectedMoment {
        val correction = SolarCorrection(
            longitudeMinutes = longitudeMinutes(longitude),
            equationOfTimeMinutes = equationOfTimeMinutes(clockTime),
            dstMinutes = dstMinutes
        )
        val trueTime = if (apply) {
            clockTime.plusSeconds(round(correction.totalMinutes * 60.0).toLong())
        } else {
            clockTime
        }
        return CorrectedMoment(
            clockTime = clockTime,
            trueSolarTime = trueTime,
            correction = correction,
            applied = apply
        )
    }
}
