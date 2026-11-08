package com.bazi.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bazi.analysis.AnalysisResult
import com.bazi.analysis.ChartAnalyzer
import com.bazi.app.data.GeoLocator
import com.bazi.app.data.HistoryEntry
import com.bazi.app.data.HistoryStore
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
    /** 分析结果目前只用于命盘表的神煞行；旺衰、格局、调候不再上屏。 */
    val analysis: AnalysisResult,
    /** 盘面文本：任务段 + 排盘、大运、流年、流月四节数据。可直接整段复制给大模型。 */
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
    /** 默认关闭：与通行排盘工具一致，按钟表时定时辰。 */
    val useTrueSolarTime: Boolean = false,
    val ziHour: ZiHourSect = ZiHourSect.LATE,
    val result: ChartBundle? = null,
    /**
     * 是否展示结果页。
     *
     * 刻意做成显式状态而不是「result != null」：同一个生辰重复排盘时，
     * 新命盘与旧命盘内容完全相等，既会被 StateFlow 去重、也会让
     * LaunchedEffect(result) 的 key 不变，导致「再点一次排盘没反应」。
     */
    val showResult: Boolean = false,
    /** 每次成功排盘自增，保证状态一定发生变化，也便于排查。 */
    val revision: Int = 0,
    val history: List<HistoryEntry> = emptyList(),
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

    /** 滚轮上正在选的时间，用于在排盘前把「将被使用的值」明确显示出来。 */
    val selectionText: String
        get() = "%04d-%02d-%02d %02d:%02d".format(year, month, day, hour, minute)
}

class BaziViewModel(app: Application) : AndroidViewModel(app) {

    private val store = HistoryStore(app)

    private val _state = MutableStateFlow(UiState(history = runCatching { store.load() }.getOrDefault(emptyList())))
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

    /**
     * 按定位得到的经纬度设出生地。
     *
     * 城市名取最近的地级市（给人看），经度用**实测值**（给真太阳时算）。
     */
    fun setCityByCoords(longitude: Double, latitude: Double) =
        setCity(GeoLocator.toCity(longitude, latitude))
    fun setTrueSolarTime(value: Boolean) = update { it.copy(useTrueSolarTime = value) }
    fun setZiHour(value: ZiHourSect) = update { it.copy(ziHour = value) }
    fun clearError() = _state.update { it.copy(error = null) }

    /** 返回录入页。结果保留在状态里，但不展示。 */
    fun closeResult() = _state.update { it.copy(showResult = false) }

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
                    // 神煞在这里转成「宫位 → 名称」，让 core:interpret 不必依赖 core:analysis
                    val shenSha = analysis.shenSha
                        .flatMap { hit -> hit.positions.map { it to hit.name } }
                        .groupBy({ it.first }, { it.second })
                    ChartBundle(
                        chart = chart,
                        analysis = analysis,
                        aiPrompt = AiPromptBuilder.build(chart, shenSha = shenSha)
                    )
                }
            }
            outcome.fold(
                onSuccess = { bundle ->
                    val history = saveToHistory(bundle)
                    _state.update {
                        it.copy(
                            loading = false,
                            result = bundle,
                            // 命中结果页 + revision 自增：即使生辰完全没变，
                            // 状态也与上一次不同，UI 必然收到新的结果。
                            showResult = true,
                            revision = it.revision + 1,
                            history = history
                        )
                    }
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

    // ------------------------------------------------------------ 历史命例

    /** 一键复用：先回填录入状态，再立即排盘。 */
    fun reuse(entry: HistoryEntry) {
        _state.update {
            it.copy(
                calendarType = entry.calendarType,
                year = entry.year,
                month = entry.month,
                day = entry.day,
                hour = entry.hour,
                minute = entry.minute,
                leapMonth = entry.leapMonth,
                gender = entry.gender,
                city = entry.city,
                useTrueSolarTime = entry.useTrueSolarTime,
                ziHour = entry.ziHour
            ).normalized()
        }
        calculate()
    }

    fun deleteHistory(entry: HistoryEntry) {
        val next = runCatching { store.remove(entry.id) }.getOrDefault(_state.value.history - entry)
        _state.update { it.copy(history = next) }
    }

    fun clearHistory() {
        runCatching { store.clear() }
        _state.update { it.copy(history = emptyList()) }
    }

    private fun saveToHistory(bundle: ChartBundle): List<HistoryEntry> {
        val chart = bundle.chart
        val input = chart.input
        val entry = HistoryEntry(
            id = System.currentTimeMillis(),
            calendarType = input.calendarType,
            year = input.year,
            month = if (input.month < 0) -input.month else input.month,
            day = input.day,
            hour = input.hour,
            minute = input.minute,
            leapMonth = input.isLeapMonth,
            gender = input.gender,
            city = input.city,
            useTrueSolarTime = input.sect.useTrueSolarTime,
            ziHour = input.sect.ziHour,
            ganZhi = chart.ganZhiText,
            lunarText = chart.moment.lunar.text
        )
        return runCatching { store.add(entry) }.getOrDefault(_state.value.history)
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
        copy(year = year.coerceIn(1900, 2100), month = month.coerceIn(1, 12), day = day.coerceIn(1, max))
    }

    CalendarType.LUNAR -> {
        val leap = MonthLength.lunarLeapMonth(year)
        val m = month.coerceIn(1, 12)
        val keepLeap = leapMonth && leap == m
        val max = MonthLength.lunarDayCount(year, if (keepLeap) -m else m)
        copy(
            year = year.coerceIn(1900, 2100),
            month = m,
            leapMonth = keepLeap,
            day = day.coerceIn(1, max)
        )
    }
}
