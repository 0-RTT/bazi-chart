package com.bazi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.bazi.app.data.GeoLocator
import com.bazi.app.UiState
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.CityDatabase
import com.bazi.calendar.Gender
import com.bazi.calendar.LunarText
import com.bazi.calendar.ZiHourSect
import kotlinx.coroutines.launch

private val SOLAR_MONTHS = (1..12).map { "$it" }
private val YEARS = (1900..2100).map { "$it" }
private val HOURS = (0..23).map { "%02d".format(it) }
private val MINUTES = (0..59).map { "%02d".format(it) }

/** 农历月选项：月号 + 是否闰月，闰月紧跟在本月之后。 */
private fun lunarMonthOptions(leap: Int): List<Pair<String, Pair<Int, Boolean>>> =
    buildList {
        for (m in 1..12) {
            add(LunarText.monthName(m) to (m to false))
            if (m == leap) add("闰" + LunarText.monthName(m) to (m to true))
        }
    }

@Composable
fun InputScreen(state: UiState, viewModel: BaziViewModel) {
    var showCityPicker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = "八字排盘",
            subtitle = "子平法 · 全离线"
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
    // 农历的「日」用中文（初一…三十），公历保持阿拉伯数字：
    // 两套历法各有自己的书写习惯，混用会显得外行。
    val dayLabels = remember(state.dayCount, state.calendarType) {
        if (state.calendarType == CalendarType.SOLAR) {
            (1..state.dayCount).map { "$it" }
        } else {
            (1..state.dayCount).map { LunarText.dayName(it) }
        }
    }

    val monthLabel = (if (state.leapMonth) "闰" else "") + LunarText.monthName(state.month)
    val summary = if (state.calendarType == CalendarType.SOLAR) {
        "公历 ${state.selectionText}"
    } else {
        "农历 ${state.year} 年 $monthLabel${LunarText.dayName(state.day)} %02d:%02d".format(state.hour, state.minute)
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
                        if (state.useTrueSolarTime) "　已开真太阳时，时辰可能变动" else "　按钟表时定时辰",
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locateHint by remember { mutableStateOf<String?>(null) }

    fun locate() {
        locating = true
        locateHint = null
        scope.launch {
            val location = GeoLocator.locate(context)
            locating = false
            if (location == null) {
                // 三种可能：系统定位关着、室内没信号、机型太老（26–29 只能取最后已知位置）
                locateHint = "没取到定位。可打开系统定位后重试，或直接手选城市"
            } else {
                viewModel.setCityByCoords(location.longitude, location.latitude)
                locateHint = "已按定位取经度 %.2f°E".format(location.longitude)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) locate() else locateHint = "未授予定位权限，可按城市手选"
    }

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
            Spacer(Modifier.width(2.dp))
            TextButton(
                onClick = {
                    if (locating) return@TextButton
                    if (GeoLocator.hasPermission(context)) {
                        locate()
                    } else {
                        permissionLauncher.launch(GeoLocator.PERMISSION)
                    }
                },
                enabled = !locating
            ) {
                Text(
                    if (locating) "定位中…" else "定位",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        locateHint?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        HairLine()

        ToggleLine(
            title = "真太阳时校正",
            subtitle = "按经度差与均时差换算。贴着时辰边界出生时，开启会改变时柱",
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
    var province by remember { mutableStateOf<String?>(null) }
    val results = CityDatabase.search(keyword, province)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(18.dp),
        title = { Text("选择出生地", fontFamily = FontFamily.Serif) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("输入城市名或省份") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                // 381 个地级市平铺太长，先按省收一遍；点同一省再点一次取消
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        ProvinceChip("全部", province == null) { province = null }
                    }
                    items(CityDatabase.provinces) { name ->
                        ProvinceChip(name, province == name) {
                            province = if (province == name) null else name
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
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
                    if (results.isEmpty()) {
                        item {
                            Text(
                                "没找到。试试省份名，或只输一个字",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
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

/** 省份筛选小片。与分段选择器同一套圆角与配色，只做单选。 */
@Composable
private fun ProvinceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}
