package com.bazi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bazi.app.ui.BaziTheme
import com.bazi.app.ui.ChartScreen
import com.bazi.app.ui.InputScreen

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
 */
@Composable
fun BaziApp(viewModel: BaziViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val bundle = state.result

    if (state.showResult && bundle != null) {
        ChartScreen(bundle = bundle, onBack = viewModel::closeResult)
    } else {
        InputScreen(state = state, viewModel = viewModel)
    }
}
