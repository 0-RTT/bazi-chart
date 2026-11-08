package com.bazi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.analysis.AnalysisResult
import com.bazi.analysis.ElementScore
import com.bazi.app.ChartBundle
import com.bazi.calendar.Gender
import com.bazi.engine.BaziChart
import com.bazi.engine.Pillar

private val RAIL_WIDTH = 26.dp

/**
 * 命盘主界面。
 *
 * 版式取「碑帖表格」的路子：四柱居中做成一张真正的表，行列皆有细线，
 * 天干地支用衬线大字并按五行着色；其余分析以纸质面板分块挂在下方。
 * 顶部与录入页共用 [ScreenHeader]，页面内的切换用 [Segmented] 胶囊，
 * 保证两屏是同一套控件语言。
 */
@Composable
fun ChartScreen(bundle: ChartBundle, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val chart = bundle.chart
    val tabs = listOf("命盘", "运程", "断语", "AI")

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            title = chart.ganZhiText,
            subtitle = buildString {
                append(if (chart.input.gender == Gender.MALE) "乾造" else "坤造")
                append(" · 日主").append(chart.dayMaster.cn).append(chart.dayMaster.wuXing.cn)
                append(" · 属").append(chart.moment.lunar.zodiac)
            },
            onBack = onBack
        )

        Segmented(
            options = tabs,
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )

        // 用 weight 精确给出剩余高度：否则可滚动内容会按整屏高度测量，
        // 末尾几十 dp 滚不到底。
        Box(Modifier.weight(1f).navigationBarsPadding()) {
            when (tab) {
                0 -> ChartTab(bundle) { tab = 3 }
                1 -> FortuneTab(bundle)
                2 -> InterpretTab(bundle.interpretation)
                else -> AiPromptTab(bundle)
            }
        }
    }
}

// ------------------------------------------------------------------ 命盘

@Composable
private fun ChartTab(bundle: ChartBundle, onOpenAi: () -> Unit) {
    val chart = bundle.chart
    val analysis = bundle.analysis

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MomentPanel(bundle) }
        item { PlatePanel(chart) }
        item { ElementPanel(analysis) }
        item { PatternPanel(analysis) }
        if (analysis.shenSha.isNotEmpty()) item { ShenShaPanel(analysis) }
        item { RelationPanel(analysis) }
        item { AuxPanel(chart) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(onClick = onOpenAi)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "已生成 AI 分析指令",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "含四柱、十神、藏干、旺衰、格局、调候、大运流年，可直接复制给大模型",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    "查看",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/** 出生信息块。左标签右值的表单式排布，从业者查盘第一眼要看的就是这一段。 */
@Composable
private fun MomentPanel(bundle: ChartBundle) {
    val chart = bundle.chart
    val moment = chart.moment
    val input = chart.input
    val male = input.gender == Gender.MALE

    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (male) "乾造" else "坤造",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "日主 ${chart.dayMaster.cn}${chart.dayMaster.wuXing.cn}",
                fontFamily = FontFamily.Serif,
                fontSize = 14.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                "属${moment.lunar.zodiac}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HairLine()

        InfoLine("公历", format(moment.clockTime), labelWidth = 52.dp)
        InfoLine("农历", moment.lunar.text, labelWidth = 52.dp)
        if (moment.trueSolarTime != moment.clockTime) {
            InfoLine(
                "真太阳时",
                format(moment.trueSolarTime) +
                    "（%+.0f 分）".format(moment.correction.totalMinutes),
                labelWidth = 52.dp
            )
        }
        InfoLine("节气", "${moment.currentTerm.name}　${format(moment.currentTerm.time)}", labelWidth = 52.dp)
        InfoLine("出生地", "${input.city.displayName}　%.2f°E".format(input.city.longitude), labelWidth = 52.dp)
        InfoLine(
            "起运",
            "${chart.startLuck.text}　${chart.startLuck.startTime.year} 年交运",
            labelWidth = 52.dp
        )
    }
}

/** 四柱表。整屏的视觉主体。 */
@Composable
private fun PlatePanel(chart: BaziChart) {
    val outline = MaterialTheme.colorScheme.outline
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radii.panel)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, outline, Radii.panel)
            .padding(vertical = 10.dp, horizontal = 2.dp)
    ) {
        PlateRow(null, chart.pillars, 20.dp) { p ->
            Text(p.position.cn, fontSize = 11.sp, color = muted)
        }

        PlateDivider()

        PlateRow("十神", chart.pillars, 22.dp) { p ->
            Text(
                p.ganTenGod?.cn ?: "日主",
                fontSize = 12.sp,
                color = if (p.ganTenGod == null) primary else MaterialTheme.colorScheme.onSurface
            )
        }
        PlateRow(null, chart.pillars, 44.dp) { p ->
            Text(
                p.gan.cn,
                fontFamily = FontFamily.Serif,
                fontSize = 31.sp,
                fontWeight = FontWeight.Medium,
                color = elementColor(p.gan.wuXing)
            )
        }
        PlateRow(null, chart.pillars, 44.dp) { p ->
            Text(
                p.zhi.cn,
                fontFamily = FontFamily.Serif,
                fontSize = 31.sp,
                fontWeight = FontWeight.Medium,
                color = elementColor(p.zhi.wuXing)
            )
        }

        PlateDivider()

        PlateRow("藏干", chart.pillars, 18.dp) { p -> HiddenStemText(p) }
        PlateRow("支神", chart.pillars, 18.dp) { p ->
            Text(
                p.hiddenStems.joinToString("") { it.tenGod.short },
                fontSize = 10.sp,
                color = muted
            )
        }
        PlateRow("星运", chart.pillars, 20.dp) { p ->
            Text(p.growthPhase.cn, fontSize = 10.sp, color = muted)
        }
        PlateRow("空亡", chart.pillars, 20.dp) { p ->
            Text(
                if (p.isEmptyBranch) "空" else "—",
                fontSize = 10.sp,
                color = if (p.isEmptyBranch) primary else muted
            )
        }
        PlateRow("纳音", chart.pillars, 20.dp) { p ->
            Text(p.naYin, fontSize = 10.sp, color = muted)
        }
    }
}

@Composable
private fun PlateRow(
    rail: String?,
    pillars: List<Pillar>,
    height: Dp,
    content: @Composable (Pillar) -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(RAIL_WIDTH).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
            if (rail != null) {
                Text(
                    rail,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        pillars.forEachIndexed { index, pillar ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(lineColor.copy(alpha = 0.55f))
                )
            }
            Box(
                Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                content(pillar)
            }
        }
    }
}

@Composable
private fun PlateDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline
    )
}

/** 藏干逐字上色：同一个字既是干，也带着五行属性，颜色本身就是信息。 */
@Composable
private fun HiddenStemText(pillar: Pillar) {
    if (pillar.hiddenStems.isEmpty()) {
        Text("—", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val dark = isSystemInDarkTheme()
    val text = buildAnnotatedString {
        pillar.hiddenStems.forEachIndexed { index, hidden ->
            if (index > 0) append(" ")
            withStyle(SpanStyle(color = BaziTokens.wuXing(hidden.hidden.gan.wuXing, dark))) {
                append(hidden.hidden.gan.cn)
            }
        }
    }
    Text(text, fontSize = 10.sp, maxLines = 1)
}

// ------------------------------------------------------------------ 五行

@Composable
private fun ElementPanel(analysis: AnalysisResult) {
    val strength = analysis.strength
    Panel {
        SectionTitle("五行力量")
        StackedBar(strength.elementScores)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            strength.elementScores.forEach { element ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        element.wuXing.cn,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = elementColor(element.wuXing)
                    )
                    Text(
                        "%.1f%%".format(element.percent * 100),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HairLine()

        Text(strength.summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (strength.hasCommand) Tag("得令", MaterialTheme.colorScheme.secondary)
            if (strength.hasGround) Tag("得地", MaterialTheme.colorScheme.secondary)
            if (strength.hasMomentum) Tag("得势", MaterialTheme.colorScheme.secondary)
            if (!strength.hasCommand) Tag("失令")
        }

        if (analysis.missingElements.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "偏枯：" + analysis.missingElements.joinToString("、") { it.cn } + " 近乎不见",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 五行占比条。用一条整宽堆叠条代替五条进度条，密度更高也更像「气色图」。 */
@Composable
private fun StackedBar(scores: List<ElementScore>) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(9.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        scores.filter { it.percent > 0.0005 }.forEach { element ->
            Box(
                Modifier
                    .weight(element.percent.toFloat())
                    .fillMaxHeight()
                    .background(elementColor(element.wuXing))
            )
        }
    }
}

// ------------------------------------------------------------------ 格局

@Composable
private fun PatternPanel(analysis: AnalysisResult) {
    Panel {
        SectionTitle("格局与调候")

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                analysis.pattern.displayName,
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (analysis.pattern.transparent) "格神透干" else "格神未透",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            analysis.pattern.basis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HairLine()

        Text(
            "调候",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(analysis.tiaohou.text, style = MaterialTheme.typography.bodyMedium)

        HairLine()

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "喜用",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                analysis.favorableElements.forEach { ElementTag(it) }
            }
        }
    }
}

@Composable
private fun ShenShaPanel(analysis: AnalysisResult) {
    Panel {
        SectionTitle("神煞", trailing = "共 ${analysis.shenSha.size} 项")
        analysis.shenSha.forEach { hit ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    hit.name,
                    Modifier.width(62.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    hit.positionText,
                    Modifier.width(62.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    hit.note,
                    Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RelationPanel(analysis: AnalysisResult) {
    Panel {
        SectionTitle("刑冲合害")
        if (analysis.relations.isEmpty()) {
            Text(
                "四柱地支之间无明显冲合刑害，结构相对平顺。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            analysis.relations.distinctBy { it.type to it.note }.forEach { relation ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Tag(relation.type.cn)
                    Spacer(Modifier.width(8.dp))
                    Text(relation.note, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    val positions = relation.positions.joinToString("·") { it.cn }
                    if (positions.isNotBlank()) {
                        Text(
                            positions,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuxPanel(chart: BaziChart) {
    Panel {
        Row(Modifier.fillMaxWidth()) {
            AuxCell("胎元", chart.fetalOrigin, Modifier.weight(1f))
            AuxCell("命宫", chart.ownSign, Modifier.weight(1f))
            AuxCell("身宫", chart.bodySign, Modifier.weight(1f))
            AuxCell("胎息", chart.fetalBreath, Modifier.weight(1f))
            AuxCell(
                "空亡",
                chart.voidBranches.joinToString("") { it.cn },
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AuxCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            fontFamily = FontFamily.Serif,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
