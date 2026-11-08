package com.bazi.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bazi.app.BaziViewModel
import com.bazi.app.UiState
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.CityDatabase
import com.bazi.calendar.Gender
import com.bazi.calendar.ZiHourSect
import com.bazi.engine.DiZhi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputScreen(state: UiState, viewModel: BaziViewModel) {
    var showCityPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("八字排盘") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            SectionLabel("历法")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.calendarType == CalendarType.SOLAR,
                    onClick = { viewModel.setCalendarType(CalendarType.SOLAR) },
                    label = { Text("公历") }
                )
                FilterChip(
                    selected = state.calendarType == CalendarType.LUNAR,
                    onClick = { viewModel.setCalendarType(CalendarType.LUNAR) },
                    label = { Text("农历") }
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("出生日期")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField(state.year, viewModel::setYear, "年", Modifier.weight(1.2f))
                NumberField(state.month, viewModel::setMonth, "月", Modifier.weight(1f))
                NumberField(state.day, viewModel::setDay, "日", Modifier.weight(1f))
            }

            if (state.calendarType == CalendarType.LUNAR) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = state.leapMonth, onCheckedChange = viewModel::setLeapMonth)
                    Spacer(Modifier.width(10.dp))
                    Text("该月为闰月", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("出生时间")
            OutlinedTextField(
                value = state.hour,
                onValueChange = viewModel::setHour,
                label = { Text("时") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = state.minute,
                onValueChange = viewModel::setMinute,
                label = { Text("分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.weight(2f))
            Spacer(Modifier.height(8.dp))
            val hourValue = state.hour.toIntOrNull() ?: 0
            val currentZhi = DiZhi.fromHourIndex((hourValue + 1) / 2)
            Text(
                "当前时辰：${currentZhi.cn}时（${currentZhi.hourRange}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(DiZhi.entries.toList()) { zhi ->
                    FilterChip(
                        selected = currentZhi == zhi,
                        onClick = { viewModel.setHour((zhi.index * 2).toString()) },
                        label = { Text(zhi.cn) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("性别")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.gender == Gender.MALE,
                    onClick = { viewModel.setGender(Gender.MALE) },
                    label = { Text("男") }
                )
                FilterChip(
                    selected = state.gender == Gender.FEMALE,
                    onClick = { viewModel.setGender(Gender.FEMALE) },
                    label = { Text("女") }
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("出生地")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { showCityPicker = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    "城市",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(state.city.displayName, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.clickable { showCityPicker = true }) {
                Text(
                    "经度 ${"%.2f".format(state.city.longitude)}°E　点击更换",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(20.dp))
            SectionLabel("流派与校正")
            ToggleRow(
                title = "真太阳时校正",
                subtitle = "按出生地经度与均时差换算，时柱以此为准",
                checked = state.useTrueSolarTime,
                onChange = viewModel::setTrueSolarTime
            )
            ToggleRow(
                title = "23 时后算次日",
                subtitle = "关闭则 23:00–23:59 仍归当日（早子时派）",
                checked = state.ziHour == ZiHourSect.LATE,
                onChange = { viewModel.setZiHour(if (it) ZiHourSect.LATE else ZiHourSect.EARLY) }
            )

            state.error?.let { message ->
                Spacer(Modifier.height(16.dp))
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = viewModel::calculate,
                enabled = !state.loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (state.loading) "排盘中…" else "开始排盘")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "全程离线计算，命例数据不出设备。结果仅供传统文化研究与自我参照。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    suffix: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(suffix) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CityPickerDialog(onDismiss: () -> Unit, onPick: (City) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    val results = CityDatabase.search(keyword)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择出生地") },
        text = {
            Column {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    placeholder = { Text("输入城市名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(results) { city ->
                        Text(
                            text = city.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(city) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
