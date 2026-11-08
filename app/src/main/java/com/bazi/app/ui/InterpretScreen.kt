package com.bazi.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bazi.app.ChartBundle
import com.bazi.interpret.Interpretation

/** 断语页：规则模板生成的解读，按板块分卡。 */
@Composable
fun InterpretTab(interpretation: Interpretation) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(interpretation.sections.size) { index ->
            val section = interpretation.sections[index]
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        section.title,
                        fontFamily = FontFamily.Serif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "%02d".format(index + 1),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                section.paragraphs.filter { it.isNotBlank() }.forEach { paragraph ->
                    Text(
                        paragraph,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
            }
        }
        item {
            Text(
                "以上文字由内置规则模板生成，每条结论均可追溯到规则编号，不含预测性断言。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 盘面信息页。
 *
 * 排盘完成时这段文本已经生成好了（见 ChartBundle.aiPrompt），
 * 此处只负责呈现与复制——不做二次加工，也不联网。
 *
 * 文本只陈述盘面，不含任务指令，也不含规避性话语；古籍论断在断语页：
 * 读者要拿它做什么，由读者自己决定。
 */
@Composable
fun AiPromptTab(bundle: ChartBundle) {
    val context = LocalContext.current
    var copied by remember(bundle.aiPrompt) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 28.dp)
    ) {
        Panel {
            Text(
                "把这段盘面整段复制给任意大模型",
                fontFamily = FontFamily.Serif,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "含四柱与十神、藏干、纳音、空亡、四柱神煞，大运与流年的干支神煞，以及当年十二流月。" +
                    "全文只陈述盘面，不含任何任务指令；古籍论断在「断语」页。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("八字盘面", bundle.aiPrompt)
                    )
                    copied = true
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (copied) "已复制到剪贴板" else "复制全部内容", fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "共 ${bundle.aiPrompt.length} 字　·　离线生成，不上传任何数据",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(0.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            SelectionContainer {
                Text(
                    bundle.aiPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(2.dp))
            Text(
                "提示：全文较长，粘贴后请确认末尾的「流月」已完整带入。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
