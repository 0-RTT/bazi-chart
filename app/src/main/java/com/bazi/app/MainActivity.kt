package com.bazi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bazi.app.ui.BaziTheme
import com.bazi.app.ui.ChartScreen
import com.bazi.app.ui.InputScreen
import com.bazi.app.ui.Motion

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 super.onCreate 之前：让内容铺到状态栏与导航栏背后，
        // 状态栏不再由系统单独画一层深色，头顶那条直角分界随之消失。
        // 图标明暗按系统深浅色自动切换，与 BaziTheme 的 isSystemInDarkTheme() 同源。
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BaziTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BaziApp()
                }
            }
        }
    }
}

/**
 * 顶层路由。
 *
 * 路由完全由 ViewModel 的 [UiState.showResult] 决定，不在 Composable 里用
 * LaunchedEffect 监听取值——那套写法依赖对象相等性，重复排同一生辰时会失效。
 *
 * 进出命盘用「淡入 + 轻微横向位移」，而不是交叉淡入：
 * 录入页与命盘页是两种完全不同的版式（表单 vs 表格），纯交叉淡入会让两份
 * 内容在同一帧里叠着，看着像重影。加一点同向位移，眼睛读到的是「翻过去了一页」，
 * 而不是「两张纸透在了一起」。位移只取屏宽的 1/14，避免与列表自身滚动混淆。
 */
@Composable
fun BaziApp(viewModel: BaziViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bundle = state.result
    val onChart = state.showResult && bundle != null

    AnimatedContent(
        targetState = onChart,
        transitionSpec = {
            // 位移用 slideInHorizontally 的 animationSpec（类型是 IntOffset），
            // 不能直接复用 Motion.page（Float），所以按时长取值重建。
            val slide = tween<IntOffset>(Motion.pageMillis)
            (fadeIn(Motion.page) + slideInHorizontally(slide) { it / 14 })
                .togetherWith(fadeOut(Motion.page) + slideOutHorizontally(slide) { -it / 14 })
        },
        label = "route"
    ) { show ->
        if (show && bundle != null) {
            ChartScreen(bundle = bundle, onBack = viewModel::closeResult)
        } else {
            InputScreen(state = state, viewModel = viewModel)
        }
    }
}
