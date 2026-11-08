package com.bazi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.analysis.ElementScore
import com.bazi.app.ChartBundle
import com.bazi.engine.Pillar
import com.bazi.interpret.Interpretation
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(bundle: ChartBundle, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("命盘", "大运流年", "解读")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(bundle.chart.ganZhiText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (tab) {
                0 -> ChartTab(bundle)
                1 -> FortuneTab(bundle)
                else -> InterpretTab(bundle.interpretation)
            }
        }
    }
}

// ------------------------------------------------------------------ 命盘

@Composable
private fun ChartTab(bundle: ChartBundle) {
    val chart = bundle.chart
    val analysis = bundle.analysis

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { MomentCard(bundle) }
        item { PillarGrid(bundle) }

        item {
            Card(title = "五行力量") {
                Text(
                    analysis.strength.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(12.dp))
                ElementBars(analysis.strength.elementScores)
                if (analysis.missingElements.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "偏枯：" + analysis.missingElements.joinToString("、") { it.cn },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(title = "格局与调候") {
                LabeledLine("格局", analysis.pattern.displayName)
                Text(
                    analysis.pattern.basis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LabeledLine("调候", analysis.tiaohou.entry?.let { "主用${it.primary.cn}" } ?: "—")
                Text(
                    analysis.tiaohou.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                LabeledLine("喜用", analysis.favorableElements.joinToString("、") { it.cn })
            }
        }

        item {
            Card(title = "神煞") {
                if (analysis.shenSha.isEmpty()) {
                    Text("本局未见常用神煞。", style = MaterialTheme.typography.bodySmall)
                } else {
                    analysis.shenSha.forEach { hit ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(hit.name, Modifier.width(88.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                hit.positionText,
                                Modifier.width(96.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                hit.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(title = "刑冲合害") {
                if (analysis.relations.isEmpty()) {
                    Text("四支之间无明显冲合刑害。", style = MaterialTheme.typography.bodySmall)
                } else {
                    analysis.relations.distinctBy { it.note }.forEach { relation ->
                        Text(
                            "${relation.type.cn}　${relation.note}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            Text(
                "四柱：${chart.ganZhiText}　胎元 ${chart.fetalOrigin}　命宫 ${chart.ownSign}　身宫 ${chart.bodySign}　胎息 ${chart.fetalBreath}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MomentCard(bundle: ChartBundle) {
    val chart = bundle.chart
    val moment = chart.moment

    Card(title = "出生信息") {
        InfoRow("公历", format(moment.clockTime))
        InfoRow("农历", moment.lunar.text)
        if (moment.trueSolarTime != moment.clockTime) {
            InfoRow("真太阳时", format(moment.trueSolarTime))
            InfoRow(
                "校正量",
                "经度 %+.1f 分　均时差 %+.1f 分".format(
                    moment.correction.longitudeMinutes,
                    moment.correction.equationOfTimeMinutes
                )
            )
        }
        InfoRow("节气", "${moment.currentTerm.name}　${format(moment.currentTerm.time)}")
        InfoRow("出生地", "${chart.input.city.displayName}　${"%.2f".format(chart.input.city.longitude)}°E")
        InfoRow("起运", "${chart.startLuck.text}　${chart.startLuck.startTime.year} 年交运")
    }
}

@Composable
private fun PillarGrid(bundle: ChartBundle) {
    val chart = bundle.chart
    val line = MaterialTheme.colorScheme.outline

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(0.5.dp, line, RoundedCornerShape(10.dp))
            .padding(vertical = 10.dp)
    ) {
        Row4 { i ->
            Text(
                chart.pillars[i].position.cn,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row4 { i ->
            Text(
                chart.pillars[i].ganTenGod?.cn ?: "日主",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        ThinDivider()
        Row4 { i -> GanZhiChar(chart.pillars[i], true) }
        Row4 { i -> GanZhiChar(chart.pillars[i], false) }
        ThinDivider()
        Row4 { i -> HiddenStemColumn(chart.pillars[i]) }
        ThinDivider()
        Row4 { i ->
            Text(
                chart.pillars[i].naYin,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row4 { i ->
            Text(
                chart.pillars[i].growthPhase.cn,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GanZhiChar(pillar: Pillar, isGan: Boolean) {
    val text = if (isGan) pillar.gan.cn else pillar.zhi.cn
    val color = if (isGan) BaziTokens.wuXing(pillar.gan.wuXing) else BaziTokens.wuXing(pillar.zhi.wuXing)
    Text(text, fontSize = 30.sp, fontWeight = FontWeight.Medium, color = color)
}

@Composable
private fun HiddenStemColumn(pillar: Pillar) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (pillar.hiddenStems.isEmpty()) {
            Text("—", style = MaterialTheme.typography.labelSmall)
        } else {
            pillar.hiddenStems.forEach { hidden ->
                Text(
                    "${hidden.hidden.gan.cn}${hidden.tenGod.short}",
                    fontSize = 11.sp,
                    color = BaziTokens.wuXing(hidden.hidden.gan.wuXing)
                )
            }
        }
    }
}

@Composable
private fun ElementBars(scores: List<ElementScore>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        scores.forEach { element ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    element.wuXing.cn,
                    modifier = Modifier.width(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BaziTokens.wuXing(element.wuXing)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.outline)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(element.percent.toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(BaziTokens.wuXing(element.wuXing))
                    )
                }
                Text(
                    "%.1f%%".format(element.percent * 100),
                    modifier = Modifier.width(56.dp),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 解读

@Composable
private fun InterpretTab(interpretation: Interpretation) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(interpretation.sections.size) { index ->
            val section = interpretation.sections[index]
            Card(title = section.title) {
                section.paragraphs.filter { it.isNotBlank() }.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 通用

@Composable
internal fun Card(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                .padding(14.dp)
        ) {
            content()
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Text(
            label,
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
internal fun Row4(content: @Composable (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        repeat(4) { index ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                content(index)
            }
        }
    }
}

@Composable
internal fun ThinDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline
    )
}

internal fun format(time: LocalDateTime): String =
    "%04d-%02d-%02d %02d:%02d".format(time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute)
