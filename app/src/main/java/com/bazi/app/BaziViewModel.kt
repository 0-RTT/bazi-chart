package com.bazi.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bazi.analysis.AnalysisResult
import com.bazi.analysis.ChartAnalyzer
import com.bazi.calendar.BirthInput
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.CityDatabase
import com.bazi.calendar.Gender
import com.bazi.calendar.MonthLength
import com.bazi.calendar.SectConfig
import com.bazi.calendar.ZiHourSect
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
import com.bazi.engine.DiZhi
import com.bazi.interpret.AiPromptBuilder
import com.bazi.interpret.Interpretation
import com.bazi.interpret.Interpreter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一次排盘的完整结果。 */
data class ChartBundle(
    val chart: BaziChart,
    val analysis: AnalysisResult,
    val interpretation: Interpretation,
    /** 排盘后自动生成的 AI 分析指令，可直接整段复制给大模型。 */
    val aiPrompt: String
)

/**
 * 录入状态。
 *
 * 日期时间全部用 Int（滚轮天然是索引），校验与夹取集中在 [normalized]，
 * 避免「2 月 31 日」这类输入流到引擎里才炸。
 */
data class UiState(
    val calendarType: CalendarType = CalendarType.SOLAR,
    val year: Int = 1990,
    val month: Int = 6,
    val day: Int = 15,
    val hour: Int = 12,
    val minute: Int = 0,
    val leapMonth: Boolean = false,
    val gender: Gender = Gender.MALE,
    val city: City = CityDatabase.default,
    val useTrueSolarTime: Boolean = true,
    val ziHour: ZiHourSect = ZiHourSect.LATE,
    val result: ChartBundle? = null,
    val loading: Boolean = false,
    val error: String? = null
) {
    /** 当前月天数，滚轮「日」列据此生成。 */
    val dayCount: Int get() = MonthLength.dayCount(calendarType, year, month, leapMonth)

    /** 输入时刻所属时辰，仅用于提示，不参与排盘。 */
    val shiChen: DiZhi get() = DiZhi.fromHourIndex((hour + 1) / 2)

    /** 农历该年的闰月；0 表示无闰月。 */
    val lunarLeap: Int
        get() = if (calendarType == CalendarType.LUNAR) MonthLength.lunarLeapMonth(year) else 0
}

class BaziViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setCalendarType(value: CalendarType) = update { it.copy(calendarType = value) }
    fun setYear(value: Int) = update { it.copy(year = value) }
    fun setMonth(value: Int) = update { it.copy(month = value, leapMonth = false) }
    fun setLunarMonth(value: Int, leap: Boolean) = update { it.copy(month = value, leapMonth = leap) }
    fun setDay(value: Int) = update { it.copy(day = value) }
    fun setHour(value: Int) = update { it.copy(hour = value) }
    fun setMinute(value: Int) = update { it.copy(minute = value) }
    fun setGender(value: Gender) = update { it.copy(gender = value) }
    fun setCity(value: City) = update { it.copy(city = value) }
    fun setTrueSolarTime(value: Boolean) = update { it.copy(useTrueSolarTime = value) }
    fun setZiHour(value: ZiHourSect) = update { it.copy(ziHour = value) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun update(block: (UiState) -> UiState) =
        _state.update { block(it).normalized() }

    fun calculate() {
        val s = _state.value
        if (s.year !in 1900..2100) {
            _state.update { it.copy(error = "当前版本支持 1900–2100 年") }
            return
        }

        val signedMonth = if (s.calendarType == CalendarType.LUNAR && s.leapMonth) -s.month else s.month
        val input = BirthInput(
            calendarType = s.calendarType,
            year = s.year,
            month = signedMonth,
            day = s.day,
            hour = s.hour,
            minute = s.minute,
            gender = s.gender,
            city = s.city,
            sect = SectConfig(
                ziHour = s.ziHour,
                useTrueSolarTime = s.useTrueSolarTime
            )
        )

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val outcome = withContext(Dispatchers.Default) {
                runCatching {
                    val chart = BaziCalculator.calculate(input)
                    val analysis = ChartAnalyzer.analyze(chart)
                    ChartBundle(
                        chart = chart,
                        analysis = analysis,
                        interpretation = Interpreter.interpret(chart, analysis),
                        aiPrompt = AiPromptBuilder.build(chart, analysis)
                    )
                }
            }
            outcome.fold(
                onSuccess = { bundle ->
                    _state.update { it.copy(loading = false, result = bundle) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = "该日期无法排盘：" +
                                (error.message ?: "请检查月、日与闰月设置是否正确")
                        )
                    }
                }
            )
        }
    }
}

/**
 * 夹取到合法日期。
 *
 * 换年（闰月变了）、换月（天数变了）、切历法，三种情况都要重算，
 * 否则滚轮上会残留一个超出范围的日子。
 */
private fun UiState.normalized(): UiState = when (calendarType) {
    CalendarType.SOLAR -> {
        val max = MonthLength.solarDayCount(year, month)
        copy(month = month.coerceIn(1, 12), day = day.coerceIn(1, max))
    }

    CalendarType.LUNAR -> {
        val leap = MonthLength.lunarLeapMonth(year)
        val m = month.coerceIn(1, 12)
        val keepLeap = leapMonth && leap == m
        val max = MonthLength.lunarDayCount(year, if (keepLeap) -m else m)
        copy(month = m, leapMonth = keepLeap, day = day.coerceIn(1, max))
    }
}
