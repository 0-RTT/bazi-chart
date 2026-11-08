package com.bazi.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.analysis.AnalysisResult
import com.bazi.app.ChartBundle
import com.bazi.calendar.Gender
import com.bazi.engine.BaziChart
import com.bazi.engine.Pillar
import com.bazi.engine.PillarPosition
import com.bazi.engine.WuXing
import kotlinx.coroutines.launch

/** 行标宽度。够放「藏干」「神煞」两字，又不挤占四柱的横向空间。 */
private val RAIL_WIDTH = 52.dp

/**
 * 命盘主界面。
 *
 * 版式照传统排盘工具的表格式样：一张十一行的四柱表占满整屏宽度，
 * 行标在左，年、月、日、时四列等宽。天干地支是 30sp 衬线大字，按本字五行着色，
 * 右下角跟一个 12sp 的五行本字，整体读作「甲木」「午火」——颜色管扫视，字管确认。
 *
 * 刻意不在此页放旺衰、格局、调候、刑冲合害这些二次推论——
 * 那些属于「怎么看」，与「盘上是什么」分属两层，混在一屏会让表本身的读数变慢。
 * 神煞只作为表格的一行（哪个字落在哪一柱是「盘上是什么」），
 * 不另开面板。推论一类的解读已随断语页一并下线，「AI」页只给盘面文本。
 */
@Composable
fun ChartScreen(bundle: ChartBundle, onBack: () -> Unit) {
    val chart = bundle.chart
    // 断语页已下线：规则模板给出的判断站不住，与其给一份不准确的解读，
    // 不如只留可逐格核对的盘面与运程，解读交给使用者自己或外部模型。
    val tabs = listOf("命盘", "运程", "AI")
    val pager = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val goTab: (Int) -> Unit = { index ->
        scope.launch { pager.animateScrollToPage(index, animationSpec = Motion.page) }
    }

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

        // 三页并排交给 Pager，而不是「一帧换掉」或「交叉淡入」：
        // - 一帧换掉：视线要重新找落点，切换像闪了一下
        // - 交叉淡入：两页都是长列表，淡入淡出期间能同时看到两份错位的文字，
        //   这才是「动画效果很奇怪」的真正来源
        // Pager 的位移由手指直接驱动，跟手、可中途反悔，高刷屏上按帧插值，
        // 不存在两个内容混在一起的那一帧。
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            // 预组合左右各一页：否则第一次向左滑时，目标页要等手指已经动了才开始
            // 组合与测量，第一帧就是掉帧。三页都不重，多备一页的代价小于掉帧。
            beyondViewportPageCount = 1
        ) { page ->
            when (page) {
                0 -> ChartTab(bundle) { goTab(2) }
                1 -> FortuneTab(bundle)
                else -> AiPromptTab(bundle)
            }
        }

        BottomTabs(tabs = tabs, selected = pager.currentPage, onSelect = goTab)
    }
}

/**
 * 底部页签。
 *
 * 三个页签是「同一份数据的三种看法」，属于平级切换而不是「进入下一层」，
 * 放在拇指自然落点比放在标题下方少一次伸手；顶部 56dp 留给盘名与返回。
 *
 * 选中态用两道冗余编码：字色 + 上方一道短杠。只靠字色，强光下或色觉有差异时会糊。
 * 短杠的透明度与横向缩放都走 graphicsLayer——只改绘制层，不触发重排，
 * 每帧代价就是两次变换矩阵。
 */
@Composable
private fun BottomTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outline
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .navigationBarsPadding()
                .height(54.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, label ->
                val on = index == selected
                val fg by animateColorAsState(
                    targetValue = if (on) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = Motion.state,
                    label = "tabFg"
                )
                val mark by animateFloatAsState(
                    targetValue = if (on) 1f else 0f,
                    animationSpec = Motion.reveal,
                    label = "tabMark"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // 去掉涟漪：整块 54dp 高的涟漪会把这一栏的重量感放得太大，
                        // 短杠与字色的过渡已经把「点到了」说清楚了。
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { if (!on) onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .graphicsLayer {
                                alpha = mark
                                scaleX = mark
                            }
                            .clip(RoundedCornerShape(1.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        label,
                        fontSize = 13.sp,
                        fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                        color = fg,
                        maxLines = 1
                    )
                }
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
        contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { MomentPanel(bundle, Modifier.padding(horizontal = 16.dp)) }
        item { PlateTable(chart, analysis) }
        item { AuxPanel(chart, Modifier.padding(horizontal = 16.dp)) }
        item { AiEntry(onOpenAi, Modifier.padding(horizontal = 16.dp)) }
    }
}

/**
 * 四柱表。
 *
 * 十一行：日期（表头）、主星、天干、地支、藏干、副星、星运、自坐、空亡、纳音、神煞。
 * 行高按内容自适应（藏干、副星、神煞可以有多行），列间竖线用 [IntrinsicSize.Min]
 * 撑到行高，避免多行内容时竖线断开。
 */
@Composable
private fun PlateTable(chart: BaziChart, analysis: AnalysisResult) {
    val male = chart.input.gender == Gender.MALE
    val pillars = chart.pillars
    val surface = MaterialTheme.colorScheme.surface
    // 表头浅底。用 surfaceVariant 而不是设计里的 surfaceSunken——
    // 后者只是 BaziTokens 的私有 Token，没有进 ColorScheme，取不到。
    val sunken = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    // 神煞按宫位归集：一个神煞可能同时落在两柱，需按柱展开而不是按条列。
    // 「空亡」不在此列——它已有独立一行（按每柱旬空列出），再混进神煞栏是同一件事说两遍。
    val shaByPosition = remember(analysis) {
        analysis.shenSha
            .filter { it.name != "空亡" }
            .flatMap { hit -> hit.positions.map { it to hit.name } }
            .groupBy({ it.first }, { it.second })
    }

    Column(Modifier.fillMaxWidth().background(surface)) {

        // ---- 表头
        PlateRow(
            rail = "日期",
            pillars = pillars,
            minHeight = 40.dp,
            background = sunken
        ) { p ->
            Text(p.position.cn, fontSize = 11.sp, color = muted)
        }

        // ---- 主星：天干对日主的十神，日柱即日主本身
        PlateRow(rail = "主星", pillars = pillars, minHeight = 40.dp) { p ->
            Text(
                p.ganTenGod?.cn ?: if (male) "元男" else "元女",
                fontSize = 14.sp,
                color = if (p.ganTenGod == null) primary else MaterialTheme.colorScheme.onSurface
            )
        }

        // ---- 天干、地支：整表视觉重心
        PlateRow(rail = "天干", pillars = pillars, minHeight = 58.dp) { p ->
            GanZhiCell(p.gan.cn, p.gan.wuXing)
        }
        PlateRow(rail = "地支", pillars = pillars, minHeight = 58.dp) { p ->
            GanZhiCell(p.zhi.cn, p.zhi.wuXing)
        }

        // ---- 藏干：逐字上色，同一个字既是干也带五行属性
        PlateRow(rail = "藏干", pillars = pillars, minHeight = 34.dp, verticalPadding = 7.dp) { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                p.hiddenStems.forEach { hidden ->
                    Text(
                        hidden.hidden.gan.cn + hidden.hidden.gan.wuXing.cn,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = elementColor(hidden.hidden.gan.wuXing)
                    )
                }
            }
        }

        // ---- 副星：藏干对应的十神，本气深、中气余气淡
        PlateRow(rail = "副星", pillars = pillars, minHeight = 34.dp, verticalPadding = 7.dp) { p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                p.hiddenStems.forEachIndexed { index, hidden ->
                    Text(
                        hidden.tenGod.cn,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        color = if (index == 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            muted
                        }
                    )
                }
            }
        }

        // ---- 星运 / 自坐：前者为日主在各支的长生，后者为本柱天干坐本柱地支
        PlateRow(rail = "星运", pillars = pillars, minHeight = 38.dp) { p ->
            Text(p.growthPhase.cn, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        PlateRow(rail = "自坐", pillars = pillars, minHeight = 38.dp) { p ->
            Text(p.ownGrowthPhase.cn, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // ---- 空亡：每柱自身干支所属之旬的空亡两支，四柱各不相同
        PlateRow(rail = "空亡", pillars = pillars, minHeight = 38.dp) { p ->
            Text(
                p.voidBranches.joinToString("") { it.cn },
                fontSize = 12.sp,
                color = muted
            )
        }

        PlateRow(rail = "纳音", pillars = pillars, minHeight = 38.dp) { p ->
            Text(p.naYin, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        // ---- 神煞：按柱竖排，数量不定
        PlateRow(rail = "神煞", pillars = pillars, minHeight = 34.dp, verticalPadding = 8.dp) { p ->
            val names = shaByPosition[p.position].orEmpty()
            if (names.isEmpty()) {
                Text("—", fontSize = 11.sp, color = muted)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    names.forEach { name ->
                        Text(
                            name,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            color = muted
                        )
                    }
                }
            }
        }
    }
}

/** 全宽表格的一行：[rail] 为行标，其后四列等宽。 */
@Composable
private fun PlateRow(
    rail: String,
    pillars: List<Pillar>,
    minHeight: Dp,
    background: Color = Color.Transparent,
    verticalPadding: Dp = 6.dp,
    content: @Composable (Pillar) -> Unit
) {
    val lineColor = MaterialTheme.colorScheme.outline

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(vertical = verticalPadding)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(RAIL_WIDTH)
                .fillMaxHeight()
                .padding(start = 16.dp, end = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(rail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        pillars.forEachIndexed { index, pillar ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(lineColor.copy(alpha = 0.5f))
                )
            }
            Box(
                // fillMaxHeight 在内在高度测量阶段贡献 0，heightIn 提供下限，
                // 布局阶段再撑满行高——两件事互不打架。
                Modifier.weight(1f).fillMaxHeight().heightIn(min = minHeight),
                contentAlignment = Alignment.Center
            ) {
                content(pillar)
            }
        }
    }
}

/**
 * 干支大字 + 五行小字。
 *
 * 小字用「木火土金水」本字，与干支同色，排在右下角，整体读作「甲木」「午火」。
 * 先前这里画的是树、焰、山、菱、滴五种抽象轮廓，形状之间没有共同语言——
 * 不认字就看不懂，认字也得先记住五种图形。换成字之后，五行既是颜色也是文字，
 * 两种线索冗余表达同一个信息，扫视与细读都成立。
 *
 * 小字参与行宽但整体居中，故大字会左移约小字宽度的一半；在 30sp 的字号下
 * 这点偏移不到一个字宽的六分之一，肉眼几乎不可察，换来的是「甲木」二字的紧邻关系。
 */
@Composable
private fun GanZhiCell(text: String, wx: WuXing) {
    val color = elementColor(wx)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text,
            fontFamily = FontFamily.Serif,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
        Text(
            wx.cn,
            fontFamily = FontFamily.Serif,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 12.sp,
            color = color,
            modifier = Modifier.padding(start = 3.dp, bottom = 3.dp)
        )
    }
}

// ------------------------------------------------------------------ 出生信息

/** 出生信息块。左标签右值的表单式排布，从业者查盘第一眼要看的就是这一段。 */
@Composable
private fun MomentPanel(bundle: ChartBundle, modifier: Modifier = Modifier) {
    val chart = bundle.chart
    val moment = chart.moment
    val input = chart.input
    val male = input.gender == Gender.MALE

    Panel(modifier) {
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
        if (moment.solarTimeApplied) {
            InfoLine(
                "真太阳时",
                format(moment.trueSolarTime) +
                    "（%+.0f 分）".format(moment.correction.totalMinutes),
                labelWidth = 52.dp
            )
        } else {
            // 关闭时也占一行：时柱与开启校正的工具对不上时，原因就写在盘上。
            InfoLine("真太阳时", "未启用（按钟表时定时辰）", labelWidth = 52.dp)
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

/** 胎元、命宫、身宫、胎息与本命旬空。不属于四柱表，另置一行。 */
@Composable
private fun AuxPanel(chart: BaziChart, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Row(Modifier.fillMaxWidth()) {
            AuxCell("胎元", chart.fetalOrigin, Modifier.weight(1f))
            AuxCell("命宫", chart.ownSign, Modifier.weight(1f))
            AuxCell("身宫", chart.bodySign, Modifier.weight(1f))
            AuxCell("胎息", chart.fetalBreath, Modifier.weight(1f))
            AuxCell("旬空", chart.voidBranches.joinToString("") { it.cn }, Modifier.weight(1f))
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
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AiEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "分析文本已生成",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "分析指令 + 四柱、大运、流年、流月，可整段粘给大模型",
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
