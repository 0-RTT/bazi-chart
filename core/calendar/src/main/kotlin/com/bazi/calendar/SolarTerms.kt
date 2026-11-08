package com.bazi.calendar

import com.tyme.solar.SolarTerm
import com.tyme.solar.SolarTime

/**
 * 十二「节」的取法。
 *
 * 二十四节气里只有十二个是「节」，负责换月：立春、惊蛰、清明、立夏、芒种、小暑、
 * 立秋、白露、寒露、立冬、大雪、小寒。其余十二个是「气」，只管气候，不换月。
 *
 * 这是排流月与换月的唯一依据。之所以单独成文件，是因为「节」与「气」混淆是
 * 命理程序最常见的错误来源之一，把取法收在一处，便于校验。
 */
object SolarTerms {

    /** 十二节的固定顺序，与十二支一一对应（寅月至丑月）。 */
    val JIE_NAMES = listOf(
        "立春", "惊蛰", "清明", "立夏", "芒种", "小暑",
        "立秋", "白露", "寒露", "立冬", "大雪", "小寒"
    )

    /** 十二节对应的月支，寅月为岁首。 */
    val JIE_BRANCHES = listOf(
        "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥", "子", "丑"
    )

    /**
     * 取某公历年所辖的十二个「节」。
     *
     * 命理上的「丙午年」自立春起算，末日落在次年小寒，故丑月的时间点会跨到下一年一月，
     * 这是正常现象，不是越界。
     */
    fun jieOfYear(year: Int): List<TermInfo> {
        var term = startProbe(year)
        val out = mutableListOf<TermInfo>()

        repeat(30) {
            term = term.next(1)
            if (term.isJie()) {
                out += term.toInfo()
                if (out.size == 12) return out
            }
        }
        return out
    }

    /**
     * 找到该年立春。
     *
     * 从 1 月中旬开始试探：此时必在小寒与大寒之间，往后数个节气内必见立春。
     */
    private fun startProbe(year: Int): SolarTerm =
        SolarTime.fromYmdHms(year, 1, 15, 0, 0, 0).getTerm()

    private fun SolarTerm.toInfo(): TermInfo = TermInfo(
        name = getName(),
        time = getJulianDay().getSolarTime().toLocalDateTime(),
        isJie = true
    )
}
