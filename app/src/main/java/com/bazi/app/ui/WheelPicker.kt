package com.bazi.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 滚轮选择器。
 *
 * ### 为什么不用 LazyColumn
 *
 * 上一版用「定高 LazyColumn + contentPadding 留白」做居中，靠
 * `firstVisibleItemIndex + firstVisibleItemScrollOffset / itemHeight` 反推选中项。
 * 这个反推是错的：`firstVisibleItemIndex` 指的是**第一个可见项**，
 * 而居中项前面永远还压着 `visibleCount / 2` 项（它们被画在 contentPadding 区域里）。
 * 于是「视觉上停在 1985」会被读成「1983」，再经 `scrollToItem` 重新定位后偏差继续累积，
 * 用户看到的结果就是「怎么选都还是默认那个年份」。
 *
 * 这一版把位置变成一个连续的 Float（单位＝行）：
 * - 第 `i` 行的 y 偏移 = `(i - position) * itemHeight + centerTop`，反向可解；
 * - 选中项 = `position.roundToInt()`，与绘制用的同一个量，不存在两套口径；
 * - 停手后按速度做投影再吸附，全程只碰这一个 Float，行为完全可验算。
 */
@Composable
fun WheelColumn(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemHeight: Dp = 38.dp,
    textSize: TextUnit = 15.sp,
    visibleCount: Int = 5,
    enabled: Boolean = true
) {
    if (items.isEmpty()) return

    val lastIndex = items.lastIndex
    val safeIndex = selectedIndex.coerceIn(0, lastIndex)
    val density = LocalDensity.current
    val itemPx = with(density) { itemHeight.toPx() }
    val centerTopPx = with(density) { (itemHeight * (visibleCount - 1) / 2).toPx() }

    // 唯一的位置状态：单位是「行」，整数即选中项
    var position by remember { mutableFloatStateOf(safeIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }

    // 外部值变化（复用历史、切换历法、月天数收缩）时归位；拖动中不抢
    LaunchedEffect(safeIndex, lastIndex) {
        if (!dragging && position.roundToInt() != safeIndex) {
            animate(
                initialValue = position,
                targetValue = safeIndex.toFloat(),
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
            ) { value, _ -> position = value }
        }
    }

    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = MaterialTheme.colorScheme.outline
    val fadeColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .height(itemHeight * visibleCount)
            .clipToBounds()
            .draggable(
                state = rememberDraggableState { delta ->
                    // 手指下滑 → 露出更小的序号
                    position = (position - delta / itemPx).coerceIn(0f, lastIndex.toFloat())
                },
                orientation = Orientation.Vertical,
                enabled = enabled,
                onDragStarted = { dragging = true },
                onDragStopped = { velocity ->
                    dragging = false
                    // 用速度做一次短程投影，手感才有惯性；再用固定时长吸附到整行
                    val projected = position - velocity / itemPx * 0.12f
                    val target = projected.roundToInt().coerceIn(0, lastIndex)
                    animate(
                        initialValue = position,
                        targetValue = target.toFloat(),
                        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                    ) { value, _ -> position = value }
                    position = target.toFloat()
                    if (target != selectedIndex) onSelected(target)
                }
            )
    ) {
        // 只画可视区附近的若干行，位置由 position 直接推出
        val start = (position.toInt() - visibleCount).coerceAtLeast(0)
        val end = (position.toInt() + visibleCount).coerceAtMost(lastIndex)
        for (index in start..end) {
            val distance = abs(index - position)
            val dy = ((index - position) * itemPx + centerTopPx).roundToInt()
            val focused = distance < 0.5f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .offset { IntOffset(0, dy) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = items[index],
                    fontFamily = FontFamily.Serif,
                    fontSize = if (focused) (textSize.value * 1.16f).sp else textSize,
                    fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                    color = if (focused) ink else muted.copy(alpha = (1f - distance / 2.6f).coerceIn(0.18f, 0.9f)),
                    maxLines = 1
                )
            }
        }

        // 中央选中带的两条细线
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, centerTopPx.roundToInt()) }
                .fillMaxWidth()
                .height(0.5.dp)
                .background(lineColor)
        )
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (centerTopPx + itemPx).roundToInt()) }
                .fillMaxWidth()
                .height(0.5.dp)
                .background(lineColor)
        )

        // 上下渐隐，制造纵深
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(Brush.verticalGradient(listOf(fadeColor, fadeColor.copy(alpha = 0f))))
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(Brush.verticalGradient(listOf(fadeColor.copy(alpha = 0f), fadeColor)))
        )
    }
}

/** 带单位标注的滚轮列。 */
@Composable
fun WheelUnit(
    label: String,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    textSize: TextUnit = 15.sp
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WheelColumn(
            items = items,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            textSize = textSize
        )
    }
}
