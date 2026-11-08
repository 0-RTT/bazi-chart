package com.bazi.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.app.ChartBundle
import com.bazi.engine.AnnualFortune
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
import com.bazi.engine.LuckPillar
import com.bazi.engine.MonthlyFortune
import java.time.LocalDate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/** 大运格定宽。68dp 恰好放下「庚午」两字与 20–29 这种年龄跨度。 */
private val CHIP_WIDTH = 68.dp

/** 大运格底部「当运」那一行的高度。写死是为了让十格严格等高，见 [LuckChip]。 */
private val CHIP_FOOT = 13.dp

/**
 * 运程页。
 *
 * 问真式的读法：上方一条横向大运带（可点选），下方铺开该步大运的十个流年。
 * 从业者查盘的动作是「先落到某步运，再看这十年」，所以这两块必须同屏联动。
 *
 * 三处动效处理都只为一件事——让「我点了什么、它变成了什么」之间有承接：
 * 1. 大运带选中后自动居中（[rememberLazyListState]），否则点到第 7 格时它可能已在屏幕外，
 *    点了却看不见点了哪个；
 * 2. 选中态底色与边框做 220ms 过渡，不是一帧之间翻色；
 * 3. 详情、流年、流月三块换内容时交叉淡入，而不是整块文字直接换掉。
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

    val luckRow = rememberLazyListState()
    LaunchedEffect(selectedIndex, chart) {
        // 首次布局之前 visibleItemsInfo 是空的，此时的滚动会被静默丢弃——
        // 进页面时当前运就停在屏幕外。等它产出可见项再滚。
        snapshotFlow { luckRow.layoutInfo.visibleItemsInfo.firstOrNull()?.size }
            .filterNotNull()
            .first()
        val info = luckRow.layoutInfo
        val firstVisible = info.visibleItemsInfo.firstOrNull() ?: return@LaunchedEffect
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        // 负偏移表示把该格往视口里拉；靠边的几格会被 clamp，正好停在端点。
        val offset = -((viewport - firstVisible.size) / 2).coerceAtLeast(0)
        luckRow.animateScrollToItem(selectedIndex, offset)
    }

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
                state = luckRow,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chart.luckPillars.size) { index ->
                    val luck = chart.luckPillars[index]
                    LuckChip(
                        luck = luck,
                        selected = index == selectedIndex,
                        current = index == currentLuckIndex,
                        onClick = {
                            // 重复点同一格不动：否则会把用户刚选好的流年冲回运首。
                            if (picked != index) {
                                picked = index
                                // 换了大运，流年口必须跟着换。
                                // 流月跟的是「选中的那一年」，而原先选的年份往往不在这步运里；
                                // 不重置的话流月会停在上一运的年份上——看起来就是
                                // 「点大运，流月不动」。含今年就用今年，否则落到这步运首年。
                                val span = chart.annuals.filter { it.luckIndex == index }
                                pickedYear = when {
                                    span.isEmpty() -> 0
                                    span.any { it.year == currentYear } -> 0
                                    else -> span.first().year
                                }
                            }
                        }
                    )
                }
            }
        }

        selectedLuck?.let { luck ->
            item {
                Crossfade(
                    targetState = luck.index,
                    animationSpec = Motion.swap,
                    label = "luckDetail"
                ) { index ->
                    val shown = chart.luckPillars.getOrNull(index)
                    if (shown != null) {
                        LuckDetailPanel(shown, index == currentLuckIndex)
                    }
                }
            }

            val years = chart.annuals.filter { it.luckIndex == luck.index }
            if (years.isNotEmpty()) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SectionTitle(
                            "流年",
                            trailing = "${years.first().year}–${years.last().year}"
                        )
                        Crossfade(
                            targetState = luck.index,
                            animationSpec = Motion.swap,
                            label = "annualSwap"
                        ) { index ->
                            // 同上：动画期间每帧重组，筛选结果要缓存。
                            val list = remember(chart, index) {
                                chart.annuals.filter { it.luckIndex == index }
                            }
                            Column {
                                list.chunked(5).forEach { row ->
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
    // 先按 target 判空：没有月份就整块不渲染，不留一个只有标题的空壳。
    val probe = remember(chart, year) { BaziCalculator.monthliesOf(chart, year) }
    if (probe.isEmpty()) return

    Column(Modifier.padding(horizontal = 16.dp)) {
        SectionTitle(
            "流月",
            trailing = if (year == currentYear) "$year 年 · 本年" else "$year 年"
        )
        // 只有月份块参与淡入淡出：标题里的年份要立刻变，跟着一起淡会看不清换到了哪一年。
        // 月份必须 remember：淡入淡出的 180ms 里 content 每帧重组，不缓存就是每帧
        // 重算十二个节气。
        Crossfade(targetState = year, animationSpec = Motion.swap, label = "monthSwap") { y ->
            val months = remember(chart, y) { BaziCalculator.monthliesOf(chart, y) }
            Column {
                months.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        row.forEach { m -> MonthCell(m, Modifier.weight(1f)) }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "月建以节为界：寅月自立春起算，丑月止于次年立春。" +
                        "格内日期为该节交节当天，逐年会有 0–1 天的漂移。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MonthCell(m: MonthlyFortune, modifier: Modifier = Modifier) {
    val highlight = m.isCurrent
    val cellBg by animateColorAsState(
        targetValue = if (highlight) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = Motion.state,
        label = "monthBg"
    )
    val cellLine by animateColorAsState(
        targetValue = if (highlight) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = Motion.state,
        label = "monthLine"
    )
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cellBg)
            .border(0.5.dp, cellLine, RoundedCornerShape(8.dp))
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
        // 节名与交节日是同一件事的两面，必须一起给：
        // 只写「2/4」，十年一步的大运切过去几乎不变（十年内该节日的月日多数只差 0–1 天），
        // 会让人以为数据没跟着年份走。写上节名，才读得出「哪一天交哪个节」。
        Text(
            "${m.jieName} ${m.startTime.monthValue}/${m.startTime.dayOfMonth}",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            m.ganTenGod.cn,
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

/**
 * 大运格。
 *
 * 三行固定内容（干支、年龄、起始年）加一行底注。底注这一行**不能用 if 掐掉**：
 * 早先的写法是 `if (current) { Text("当运") }`，于是当运那一格比邻格多出一行字，
 * 整条大运带的下沿就成了锯齿。现在无论是否当运，[CHIP_FOOT] 那一格都占满高度，
 * 只在当运时写字——十格永远等高。
 */
@Composable
private fun LuckChip(
    luck: LuckPillar,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit
) {
    val chipBg by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = Motion.state,
        label = "luckChipBg"
    )
    val chipLine by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = Motion.state,
        label = "luckChipLine"
    )
    // 边框粗细一起过渡：只做颜色不做粗细时，选中的瞬间仍会「硬」一下。
    val chipLineWidth by animateDpAsState(
        targetValue = if (selected) 1.dp else 0.5.dp,
        animationSpec = Motion.thickness,
        label = "luckChipLineWidth"
    )
    Column(
        modifier = Modifier
            .width(CHIP_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .background(chipBg)
            .border(chipLineWidth, chipLine, RoundedCornerShape(10.dp))
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
        Box(Modifier.height(CHIP_FOOT), contentAlignment = Alignment.Center) {
            Text(
                text = if (current) "当运" else "",
                fontSize = 9.sp,
                lineHeight = 9.sp,
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
    val lit = current || selected
    val cellBg by animateColorAsState(
        targetValue = if (lit) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = Motion.state,
        label = "annualBg"
    )
    val cellLine by animateColorAsState(
        targetValue = if (lit) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        },
        animationSpec = Motion.state,
        label = "annualLine"
    )
    val cellLineWidth by animateDpAsState(
        targetValue = if (selected) 1.dp else 0.5.dp,
        animationSpec = Motion.thickness,
        label = "annualLineWidth"
    )
    Box(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(cellBg)
            .border(cellLineWidth, cellLine, RoundedCornerShape(8.dp))
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
                annual.ganTenGod.cn,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
