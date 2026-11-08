package com.bazi.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.app.data.HistoryEntry

private const val COLLAPSED_COUNT = 3

/**
 * 最近排盘。
 *
 * 从业者一天要排十几张盘，客户盘还会反复查，重复输入是最没价值的动作。
 * 这里把每次排盘自动入栈，点一条即回填并立刻重排。
 */
@Composable
fun HistorySection(
    entries: List<HistoryEntry>,
    onReuse: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    if (entries.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) entries else entries.take(COLLAPSED_COUNT)

    Panel {
        SectionTitle(
            text = "最近排盘",
            trailing = if (entries.size > COLLAPSED_COUNT) {
                "共 ${entries.size} 条"
            } else {
                "点按即重排"
            },
            action = {
                Text(
                    "清空",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .clip(Radii.tag)
                        .clickable(onClick = onClear)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        )

        shown.forEachIndexed { index, entry ->
            if (index > 0) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            HistoryRow(entry = entry, onReuse = onReuse, onDelete = onDelete)
        }

        if (entries.size > COLLAPSED_COUNT) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (expanded) "收起" else "展开全部 ${entries.size} 条",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Radii.tag)
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    onReuse: (HistoryEntry) -> Unit,
    onDelete: (HistoryEntry) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onReuse(entry) }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.ganZhi,
                    fontFamily = FontFamily.Serif,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.width(7.dp))
                Tag(entry.sexText)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${entry.calendarText} ${entry.dateText} · ${entry.city.name}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.lunarText.isNotBlank()) {
                Text(
                    entry.lunarText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { onDelete(entry) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "删除此记录",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
