package com.bazi.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime

/**
 * 通用版式件。
 *
 * 全站统一「薄边、大圆角（16dp）、无投影」的面板语言，
 * 让视觉重心落在干支与数字上，而不是容器本身。
 */
object Radii {
    val panel = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(12.dp)
    val tag = RoundedCornerShape(5.dp)
}

/**
 * 动效口径。
 *
 * 只留三档，因为动效一多就互相打架：
 * - [state]　就地变化（选中、高亮、边框粗细）；比手指的反应稍慢一点，但不能慢过知觉
 * - [swap]　内容整体替换（换大运、换流年、换流月）；只做交叉淡入，不做位移，
 *   因为这两块本身就在滚动容器里，再加位移会与惯性滚动抢注意力
 * - [page]　页面级迁移（页签切换、进出命盘）
 *
 * 缓动统一用 [FastOutSlowInEasing]：起步快，动作在按下的一瞬间就有回应；
 * 收尾慢，停下来的位置才看得清。线性或 EaseIn 在触屏上都会显得「黏」。
 */
internal object Motion {
    val state = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)
    val thickness = tween<Dp>(durationMillis = 220, easing = FastOutSlowInEasing)
    val swap = tween<Float>(durationMillis = 180, easing = FastOutSlowInEasing)
    val page = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radii.panel)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outline, Radii.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content
    )
}

/**
 * 页面头部。
 *
 * 不用 Material 的 TopAppBar：那套是 64dp 平板矩形 + 直角下沿，
 * 在纸感版式里显得硬。这里换成「圆形返回键 + 衬线标题 + 副标题」，
 * 高度收到 56dp，与卡片的大圆角语言对齐。
 *
 * 头部与内容之间不画分割线：一条贯穿屏宽的 0.5dp 直线，正好把「直角」
 * 重新请回来——留白本身就是分隔。配合 enableEdgeToEdge，状态栏、头部、
 * 内容共用同一层纸色，从屏幕顶端到第一张卡片之间没有任何硬边。
 */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontSize = 19.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        actions()
    }
}

/** 小节标题：左侧一道朱砂短线 + 标题，右侧可挂辅助文字或操作。 */
@Composable
fun SectionTitle(
    text: String,
    trailing: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (trailing != null || action != null) {
            Spacer(Modifier.weight(1f))
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (action != null) action()
    }
}

@Composable
fun HairLine(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 8.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline
    )
}

/** 「标签　值」一行。标签定宽，保证多行上下对齐。 */
@Composable
fun InfoLine(label: String, value: String, labelWidth: Dp = 62.dp) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            modifier = Modifier.width(labelWidth),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

/**
 * 分段选择器。
 * 用「轨道 + 浮起的小块」而非 Material 的 TabRow，视觉更接近纸质表单，
 * 也让页面顶部与内容里出现的是同一种控件语言。
 */
@Composable
fun Segmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(Radii.chip)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // 选中块与字色都过渡：原来是一帧之间从透明跳到纸色，眼睛会先被闪一下
            // 才认出选中的是哪个。220ms 的底色推移足以把「换了个页签」和
            // 「换了一张纸」区分开。
            val chipBg by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                animationSpec = Motion.state,
                label = "segmentBg"
            )
            val chipFg by animateColorAsState(
                targetValue = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = Motion.state,
                label = "segmentFg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(chipBg)
                    .clickable { if (!selected) onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = chipFg,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ToggleLine(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 小标签，用于喜用五行、十神分类这类短词。 */
@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier
            .clip(Radii.tag)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 五行色的小标签。 */
@Composable
fun ElementTag(wx: com.bazi.engine.WuXing) {
    Tag(wx.cn, elementColor(wx))
}

/**
 * 干支两字各按本字五行上色。
 *
 * 把「庚午」整串染成金色是错的读法：庚属金、午属火，两字分色才能一眼看出
 * 这一步运是「金坐火」。大运、流年、流月三处都用这个件，色彩口径保持一致。
 *
 * @param showElement 是否在右下角附五行小字。空间紧张处（流年格子）关掉。
 */
@Composable
fun GanZhiPair(
    gan: com.bazi.engine.TianGan,
    zhi: com.bazi.engine.DiZhi,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    showElement: Boolean = false
) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(
            gan.cn,
            fontFamily = FontFamily.Serif,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = elementColor(gan.wuXing)
        )
        Text(
            zhi.cn,
            fontFamily = FontFamily.Serif,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = elementColor(zhi.wuXing)
        )
        if (showElement) {
            Text(
                gan.wuXing.cn + zhi.wuXing.cn,
                fontFamily = FontFamily.Serif,
                fontSize = 11.sp,
                lineHeight = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 3.dp, bottom = 2.dp)
            )
        }
    }
}

internal fun format(time: LocalDateTime): String =
    "%04d-%02d-%02d %02d:%02d".format(
        time.year, time.monthValue, time.dayOfMonth, time.hour, time.minute
    )
