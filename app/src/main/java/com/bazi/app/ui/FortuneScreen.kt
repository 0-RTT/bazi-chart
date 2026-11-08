package com.bazi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.app.ChartBundle
import java.time.LocalDate

/**
 * 大运与流年。
 *
 * 布局按"时间轴"读：起运信息 → 每步大运一张条 → 逐年流年表。
 * 从业者查盘时最常看的是"某年归哪步运、那年干支是什么"，所以这两列必须同屏可读。
 */
@Composable
fun FortuneTab(bundle: ChartBundle) {
    val chart = bundle.chart
    val currentYear = LocalDate.now().year
    val currentLuck = chart.luckOfYear(currentYear)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(title = "起运") {
                InfoRow("方向", if (chart.startLuck.forward) "顺排" else "逆排")
                InfoRow(
                    "起运",
                    buildString {
                        if (chart.startLuck.yearCount > 0) append("${chart.startLuck.yearCount} 年 ")
                        if (chart.startLuck.monthCount > 0) append("${chart.startLuck.monthCount} 个月 ")
                        if (chart.startLuck.dayCount > 0) append("${chart.startLuck.dayCount} 天")
                    }
                )
                InfoRow("交运", format(chart.startLuck.startTime))
                InfoRow("当前", currentLuck?.let { "行 ${it.ganZhi} 运（${it.startAge}–${it.endAge} 岁）" } ?: "尚未起运")
            }
        }

        item {
            Text(
                "大运",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(chart.luckPillars.size) { index ->
            val luck = chart.luckPillars[index]
            val isCurrent = currentLuck?.index == luck.index
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isCurrent) 1.5.dp else 0.5.dp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.width(78.dp)) {
                    Text(
                        "${luck.startAge}–${luck.endAge}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${luck.startYear}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    luck.ganZhi,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = BaziTokens.wuXing(luck.gan.wuXing)
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "干${luck.ganTenGod.cn}　支${luck.zhiMainTenGod.cn}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        luck.growthPhase.cn,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isCurrent) {
                    Text(
                        "当运",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text(
                "流年",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                HeaderCell("年份", 0.9f)
                HeaderCell("虚岁", 0.6f)
                HeaderCell("干支", 0.7f)
                HeaderCell("十神", 0.9f)
                HeaderCell("大运", 0.9f)
            }
        }

        items(chart.annuals.size) { index ->
            val annual = chart.annuals[index]
            val isCurrent = annual.year == currentYear
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BodyCell(annual.year.toString(), 0.9f)
                BodyCell(annual.nominalAge.toString(), 0.6f)
                BodyCell(annual.ganZhi, 0.7f, color = BaziTokens.wuXing(annual.gan.wuXing), bold = true)
                BodyCell(annual.ganTenGod.cn, 0.9f)
                BodyCell(annual.luckGanZhi, 0.9f)
            }
        }

        item {
            Spacer(Modifier.height(12.dp))
            Text(
                "流年干支以立春为界，非公历元旦。虚岁自出生年计为 1 岁。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BodyCell(
    text: String,
    weight: Float,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bold: Boolean = false
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal
    )
}
