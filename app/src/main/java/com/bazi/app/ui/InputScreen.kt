package com.bazi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.app.BaziViewModel
import com.bazi.app.UiState
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.CityDatabase
import com.bazi.calendar.Gender
import com.bazi.calendar.ZiHourSect

private val LUNAR_MONTHS = arrayOf(
    "正月", "二月", "三月", "四月", "五月", "六月",
    "七月", "八月", "九月", "十月", "冬月", "腊月"
)
private val SOLAR_MONTHS = (1..12).map { "$it" }
private val YEARS = (1900..2100).map { "$it" }
private val HOURS = (0..23).map { "%02d".format(it) }
private val MINUTES = (0..59).map { "%02d".format(it) }

/** 农历月选项：月号 + 是否闰月，闰月紧跟在本月之后。 */
private fun lunarMonthOptions(leap: Int): List<Pair<String, Pair<Int, Boolean>>> =
    buildList {
        for (m in 1..12) {
            add(LUNAR_MONTHS[m - 1] to (m to false))
            if (m == leap) add("闰${LUNAR_MONTHS[m - 1]}" to (m to true))
        }
    }

@Composable
fun InputScreen(state: UiState, viewModel: BaziViewModel) {
    var showCityPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = "八字排盘",
            subtitle = "子平法 · 真太阳时 · 全离线"
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 24.dp)
        ) {
            if (state.history.isNotEmpty()) {
                HistorySection(
                    entries = state.history,
                    onReuse = viewModel::reuse,
                    onDelete = viewModel::deleteHistory,
                    onClear = viewModel::clearHistory
                )
                Spacer(Modifier.height(10.dp))
            }

            BirthPanel(state, viewModel)
            Spacer(Modifier.height(10.dp))
            PlacePanel(state, viewModel) { showCityPicker = true }

            state.error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = viewModel::calculate,
                enabled = !state.loading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    if (state.loading) "排盘中…" else "开始排盘",
                    fontSize = 15.sp,
                    letterSpacing = 4.sp
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "全程离线计算，命例只存本机。结果依子平法通行口径，仅供传统文化研究与自我参照。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showCityPicker) {
        CityPickerDialog(
            onDismiss = { showCityPicker = false },
            onPick = {
                viewModel.setCity(it)
                showCityPicker = false
            }
        )
    }
}

// ------------------------------------------------------------------ 生辰

@Composable
private fun BirthPanel(state: UiState, viewModel: BaziViewModel) {
    val leap = state.lunarLeap
    val lunarMonths = remember(leap) { lunarMonthOptions(leap) }
    val monthLabels = if (state.calendarType == CalendarType.SOLAR) {
        SOLAR_MONTHS
    } else {
        lunarMonths.map { it.first }
    }
    val monthIndex = if (state.calendarType == CalendarType.SOLAR) {
        (state.month - 1).coerceIn(0, 11)
    } else {
        lunarMonths
            .indexOfFirst { it.second.first == state.month && it.second.second == state.leapMonth }
            .coerceAtLeast(0)
    }
    val dayLabels = remember(state.dayCount) { (1..state.dayCount).map { "$it" } }

    val monthLabel = (if (state.leapMonth) "闰" else "") + LUNAR_MONTHS[state.month - 1]
    val summary = if (state.calendarType == CalendarType.SOLAR) {
        "公历 ${state.selectionText}"
    } else {
        "农历 ${state.year} 年 $monthLabel ${state.day} 日 %02d:%02d".format(state.hour, state.minute)
    }

    Panel {
        Segmented(
            options = listOf("公历", "农历"),
            selectedIndex = if (state.calendarType == CalendarType.SOLAR) 0 else 1,
            onSelect = {
                viewModel.setCalendarType(if (it == 0) CalendarType.SOLAR else CalendarType.LUNAR)
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth()) {
            WheelUnit(
                label = "年",
                items = YEARS,
                selectedIndex = (state.year - 1900).coerceIn(0, YEARS.lastIndex),
                onSelected = { viewModel.setYear(1900 + it) },
                modifier = Modifier.weight(1.5f),
                textSize = 17.sp
            )
            WheelUnit(
                label = "月",
                items = monthLabels,
                selectedIndex = monthIndex,
                onSelected = {
                    if (state.calendarType == CalendarType.SOLAR) {
                        viewModel.setMonth(it + 1)
                    } else {
                        val option = lunarMonths[it]
                        viewModel.setLunarMonth(option.second.first, option.second.second)
                    }
                },
                modifier = Modifier.weight(1f),
                textSize = 16.sp
            )
            WheelUnit(
                label = "日",
                items = dayLabels,
                selectedIndex = (state.day - 1).coerceIn(0, dayLabels.lastIndex),
                onSelected = { viewModel.setDay(it + 1) },
                modifier = Modifier.weight(0.95f),
                textSize = 16.sp
            )
            WheelUnit(
                label = "时",
                items = HOURS,
                selectedIndex = state.hour,
                onSelected = viewModel::setHour,
                modifier = Modifier.weight(0.95f),
                textSize = 16.sp
            )
            WheelUnit(
                label = "分",
                items = MINUTES,
                selectedIndex = state.minute,
                onSelected = viewModel::setMinute,
                modifier = Modifier.weight(0.95f),
                textSize = 16.sp
            )
        }

        Spacer(Modifier.height(10.dp))

        // 把「将被使用的值」明确写出来：滚轮看得见，排盘用的就是这一行
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    summary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${state.shiChen.cn}时 · ${state.shiChen.hourRange}" +
                        (if (state.useTrueSolarTime) "　按真太阳时定时辰" else ""),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        HairLine()

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "性别",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(14.dp))
            Segmented(
                options = listOf("乾造（男）", "坤造（女）"),
                selectedIndex = if (state.gender == Gender.MALE) 0 else 1,
                onSelect = { viewModel.setGender(if (it == 0) Gender.MALE else Gender.FEMALE) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ------------------------------------------------------------------ 出生地

@Composable
private fun PlacePanel(state: UiState, viewModel: BaziViewModel, onPickCity: () -> Unit) {
    Panel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onPickCity)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "出生地",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(state.city.displayName, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "%.2f°E".format(state.city.longitude),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HairLine()

        ToggleLine(
            title = "真太阳时校正",
            subtitle = "按经度差与均时差换算，时柱以此为准",
            checked = state.useTrueSolarTime,
            onChange = viewModel::setTrueSolarTime
        )
        ToggleLine(
            title = "23 时后算次日",
            subtitle = "关闭则 23:00–23:59 仍归当日（早子时派）",
            checked = state.ziHour == ZiHourSect.LATE,
            onChange = { viewModel.setZiHour(if (it) ZiHourSect.LATE else ZiHourSect.EARLY) }
        )
    }
}

// ------------------------------------------------------------------ 城市选择

@Composable
private fun CityPickerDialog(onDismiss: () -> Unit, onPick: (City) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    val results = CityDatabase.search(keyword)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("选择出生地", fontFamily = FontFamily.Serif) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("输入城市名") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(results) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(city) }
                                .padding(vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(city.displayName, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.weight(1f))
                            Text(
                                "%.2f°E".format(city.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
