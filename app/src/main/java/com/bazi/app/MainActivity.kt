package com.bazi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bazi.app.ui.BaziTheme
import com.bazi.app.ui.ChartScreen
import com.bazi.app.ui.InputScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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

@Composable
fun BaziApp(viewModel: BaziViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showChart by rememberSaveable { mutableStateOf(false) }
    val bundle = state.result

    // 以命盘对象本身为 key：每次重新排盘都会产生新对象，页面自动回到命盘；
    // 用户主动返回时 bundle 未变，不会又被弹回结果页。
    LaunchedEffect(bundle) {
        if (bundle != null) showChart = true
    }

    if (showChart && bundle != null) {
        ChartScreen(bundle = bundle, onBack = { showChart = false })
    } else {
        InputScreen(state = state, viewModel = viewModel)
    }
}
