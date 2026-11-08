package com.bazi.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/**
 * 盘面文本页。
 *
 * 排盘完成时这段文本已经生成好了（见 ChartBundle.aiPrompt），
 * 此处只负责呈现与复制——不做二次加工，也不联网。
 *
 * 文本分两段：**任务**（角色、多派交叉、七个维度、关键事件四项、建议三档）
 * 与**数据**（排盘、大运、流年、流月）。
 *
 * 任务段是写死的：这份文本的唯一用途就是粘给大模型，每次重打一遍要求，
 * 十次有八次会漏掉「关键事件要给时间范围」。数据段仍然只陈述事实——
 * 旺衰、格局、调候、喜用一概不给，那是模型该算的，先给了它只会顺着抄。
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
                "任务段写清分析方法、七个维度与输出要求；数据段给排盘（四柱、藏干、副星、" +
                    "纳音、空亡、神煞）、大运、流年、流月。推论一律不给，交给模型自己算。",
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
                Text(if (copied) "已复制到剪贴板" else "复制全部内容（任务 + 盘面）", fontSize = 14.sp)
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
                "提示：整段粘贴即可，任务段已包含输出格式要求；末尾「流月」别截断。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
