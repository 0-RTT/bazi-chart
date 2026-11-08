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
import com.bazi.calendar.SectConfig
import com.bazi.calendar.ZiHourSect
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
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
    val interpretation: Interpretation
)

data class UiState(
    val calendarType: CalendarType = CalendarType.SOLAR,
    val year: String = "1990",
    val month: String = "1",
    val day: String = "1",
    val hour: String = "12",
    val minute: String = "0",
    val leapMonth: Boolean = false,
    val gender: Gender = Gender.MALE,
    val city: City = CityDatabase.default,
    val useTrueSolarTime: Boolean = true,
    val ziHour: ZiHourSect = ZiHourSect.LATE,
    val result: ChartBundle? = null,
    val loading: Boolean = false,
    val error: String? = null
)

class BaziViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun setCalendarType(value: CalendarType) = _state.update { it.copy(calendarType = value, leapMonth = false) }
    fun setYear(value: String) = _state.update { it.copy(year = value.filter { c -> c.isDigit() }.take(4)) }
    fun setMonth(value: String) = _state.update { it.copy(month = value.filter { c -> c.isDigit() }.take(2)) }
    fun setDay(value: String) = _state.update { it.copy(day = value.filter { c -> c.isDigit() }.take(2)) }
    fun setHour(value: String) = _state.update { it.copy(hour = value.filter { c -> c.isDigit() }.take(2)) }
    fun setMinute(value: String) = _state.update { it.copy(minute = value.filter { c -> c.isDigit() }.take(2)) }
    fun setLeapMonth(value: Boolean) = _state.update { it.copy(leapMonth = value) }
    fun setGender(value: Gender) = _state.update { it.copy(gender = value) }
    fun setCity(value: City) = _state.update { it.copy(city = value) }
    fun setTrueSolarTime(value: Boolean) = _state.update { it.copy(useTrueSolarTime = value) }
    fun setZiHour(value: ZiHourSect) = _state.update { it.copy(ziHour = value) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun calculate() {
        val s = _state.value
        val year = s.year.trim().toIntOrNull()
        val month = s.month.trim().toIntOrNull()
        val day = s.day.trim().toIntOrNull()
        val hour = s.hour.trim().toIntOrNull() ?: 0
        val minute = s.minute.trim().toIntOrNull() ?: 0

        if (year == null || month == null || day == null) {
            _state.update { it.copy(error = "出生时间未填写完整") }
            return
        }
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) {
            _state.update { it.copy(error = "时间取值超出范围") }
            return
        }
        if (year < 1900 || year > 2100) {
            _state.update { it.copy(error = "当前版本支持 1900–2100 年") }
            return
        }

        val monthValue = if (s.calendarType == CalendarType.LUNAR && s.leapMonth) -month else month
        val input = BirthInput(
            calendarType = s.calendarType,
            year = year,
            month = monthValue,
            day = day,
            hour = hour,
            minute = minute,
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
                    ChartBundle(chart, analysis, Interpreter.interpret(chart, analysis))
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
                            error = error.message ?: "排盘失败，请检查输入的日期是否存在"
                        )
                    }
                }
            )
        }
    }
}
