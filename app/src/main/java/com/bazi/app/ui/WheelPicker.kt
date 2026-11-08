package com.bazi.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * 滚轮选择器。
 *
 * 实现方式：定高 LazyColumn + 上下对称留白，让「第 n 项」正好落在可视区正中；
 * 滚动停下后按当前偏移量算最近的一项，再动画吸附过去。
 *
 * 刻意没有用 foundation 的 snap fling 实验 API——那套接口在小版本间改过签名，
 * 这里用稳定 API 自己吸附，行为可控且不会因依赖升级而编译失败。
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
    val itemPx = with(LocalDensity.current) { itemHeight.toPx() }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = safeIndex)

    // 当前滚到第几项（含滚动中的小数偏移），用于实时高亮
    val currentIndex by remember(itemPx, lastIndex) {
        derivedStateOf {
            val offset = listState.firstVisibleItemScrollOffset
            (listState.firstVisibleItemIndex + (offset / itemPx).roundToInt())
                .coerceIn(0, lastIndex)
        }
    }

    // 外部值变化（切换历法、月份天数收缩等）时同步位置
    LaunchedEffect(safeIndex) {
        if (listState.firstVisibleItemIndex != safeIndex || listState.firstVisibleItemScrollOffset != 0) {
            listState.scrollToItem(safeIndex)
        }
    }

    // 停止滚动后吸附到最近一项
    LaunchedEffect(listState, itemPx, lastIndex) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) {
                val settled = (
                    listState.firstVisibleItemIndex +
                        (listState.firstVisibleItemScrollOffset / itemPx).roundToInt()
                    ).coerceIn(0, lastIndex)
                listState.animateScrollToItem(settled)
                if (settled != selectedIndex) onSelected(settled)
            }
        }
    }

    val fadeColor = MaterialTheme.colorScheme.surface

    Box(modifier.height(itemHeight * visibleCount)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = itemHeight * (visibleCount - 1) / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            userScrollEnabled = enabled
        ) {
            items(count = items.size) { index ->
                val focused = index == currentIndex
                Box(
                    modifier = Modifier.fillMaxWidth().height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontFamily = FontFamily.Serif,
                        fontSize = if (focused) (textSize.value * 1.16f).sp else textSize,
                        fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                        color = if (focused) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        maxLines = 1
                    )
                }
            }
        }

        // 中央选中带的两条细线
        val line = MaterialTheme.colorScheme.outline
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(itemHeight)
        ) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(line)
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(line)
            )
        }

        // 上下渐隐，制造「滚不完」的纵深
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(fadeColor, fadeColor.copy(alpha = 0f))
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(itemHeight)
                .background(
                    Brush.verticalGradient(
                        listOf(fadeColor.copy(alpha = 0f), fadeColor)
                    )
                )
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
