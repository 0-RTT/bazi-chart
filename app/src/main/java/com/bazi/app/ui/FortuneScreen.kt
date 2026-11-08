package com.bazi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.app.ChartBundle
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
import com.bazi.engine.AnnualFortune
import com.bazi.engine.LuckPillar
import com.bazi.engine.MonthlyFortune
import java.time.LocalDate

/**
 * 运程页。
 *
 * 问真式的读法：上方一条横向大运带（可点选），下方铺开该步大运的十个流年。
 * 从业者查盘的动作是「先落到某步运，再看这十年」，所以这两块必须同屏联动。
 */
@Composable
fun FortuneTab(bundle: ChartBundle) {
    val chart = bundle.chart
    val currentYear = LocalDate.now().year
    val currentLuckIndex = chart.luckOfYear(currentYear)?.index ?: -1

    var picked by rememberSaveable { mutableIntStateOf(-1) }
    // 选中哪一年，下方的流月就展开哪一年；未选时跟当前年份走。
    var pickedYear by rememberSaveable { mutableIntStateOf(-1) }
    val selectedYear = if (pickedYear > 0) pickedYear else currentYear
    val selectedIndex = if (picked >= 0 && picked <= chart.luckPillars.lastIndex) {
        picked
    } else {
        currentLuckIndex.coerceAtLeast(0)
    }
    val selectedLuck = chart.luckPillars.getOrNull(selectedIndex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { StartLuckPanel(bundle, currentYear) }

        item {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("大运", trailing = if (chart.startLuck.forward) "顺排" else "逆排")
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chart.luckPillars.size) { index ->
                    val luck = chart.luckPillars[index]
                    LuckChip(
                        luck = luck,
                        selected = index == selectedIndex,
                        current = index == currentLuckIndex,
                        onClick = { picked = index }
                    )
                }
            }
        }

        selectedLuck?.let { luck ->
            item { LuckDetailPanel(luck, currentLuckIndex == selectedIndex) }

            val years = chart.annuals.filter { it.luckIndex == luck.index }
            if (years.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle(
                            "流年",
                            trailing = "${years.first().year}–${years.last().year}"
                        )
                        years.chunked(5).forEach { row ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                row.forEach { annual ->
                                    AnnualCell(
                                        annual = annual,
                                        current = annual.year == currentYear,
                                        selected = annual.year == selectedYear,
                                        onClick = { pickedYear = annual.year },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        item { MonthSection(chart, selectedYear, currentYear) }

        item {
            Text(
                "流年干支以立春为界，非公历元旦；虚岁自出生年计为 1 岁。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * 流月。
 *
 * 大运 → 流年 → 流月是三级下钻，缺了最后一层，「今年这几个月怎么样」还得另开一个工具。
 * 月份固定十二个，三列四行：三列让每格宽到能放下「正月」与干支而不挤。
 */
@Composable
private fun MonthSection(chart: BaziChart, year: Int, currentYear: Int) {
    val months = remember(chart, year) { BaziCalculator.monthliesOf(chart, year) }
    if (months.isEmpty()) return

    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle(
            "流月",
            trailing = if (year == currentYear) "$year 年 · 本年" else "$year 年"
        )
        months.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                row.forEach { m -> MonthCell(m, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "月建以节为界，寅月自立春起算，丑月止于次年立春。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonthCell(m: MonthlyFortune, modifier: Modifier = Modifier) {
    val highlight = m.isCurrent
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                0.5.dp,
                if (highlight) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.monthName,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
            GanZhiPair(m.gan, m.zhi, fontSize = 15.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            m.ganTenGod.short + "　" + m.startTime.monthValue + "/" + m.startTime.dayOfMonth,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StartLuckPanel(bundle: ChartBundle, currentYear: Int) {
    val chart = bundle.chart
    val current = chart.luckOfYear(currentYear)

    Panel(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle("起运")
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                InfoLine("方向", if (chart.startLuck.forward) "顺排" else "逆排", labelWidth = 40.dp)
                InfoLine("起运", luckLengthText(chart.startLuck), labelWidth = 40.dp)
            }
            Column(Modifier.weight(1f)) {
                InfoLine("交运", "${chart.startLuck.startTime.year} 年", labelWidth = 40.dp)
                InfoLine(
                    "当前",
                    current?.let { "${it.ganZhi} 运" } ?: "未起运",
                    labelWidth = 40.dp
                )
            }
        }
    }
}

private fun luckLengthText(luck: com.bazi.engine.StartLuckInfo): String = buildString {
    if (luck.yearCount > 0) append("${luck.yearCount} 年 ")
    if (luck.monthCount > 0) append("${luck.monthCount} 月 ")
    if (luck.dayCount > 0) append("${luck.dayCount} 天")
}

@Composable
private fun LuckChip(
    luck: LuckPillar,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GanZhiPair(luck.gan, luck.zhi, fontSize = 20.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            "${luck.startAge}–${luck.endAge}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "${luck.startYear}",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (current) {
            Spacer(Modifier.height(2.dp))
            Text(
                "当运",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LuckDetailPanel(luck: LuckPillar, isCurrent: Boolean) {
    Panel(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GanZhiPair(luck.gan, luck.zhi, fontSize = 26.sp, showElement = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${luck.startAge}–${luck.endAge} 岁　${luck.startYear}–${luck.endYear} 年",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "干${luck.ganTenGod.cn}　支${luck.zhiMainTenGod.cn}　${luck.growthPhase.cn}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isCurrent) Tag("当运", MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AnnualCell(
    annual: AnnualFortune,
    current: Boolean,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (current || selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                if (selected) 1.dp else 0.5.dp,
                if (current || selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GanZhiPair(annual.gan, annual.zhi, fontSize = 16.sp)
            Text(
                "${annual.year}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                annual.ganTenGod.short,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
