package de.wortschatz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import de.wortschatz.app.ui.WortschatzApp
import de.wortschatz.app.ui.theme.WortschatzTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 交给系统在第一帧后自动撤掉启动图。
        // 早前用 setKeepOnScreenCondition 等一个「首帧已画」的标记，
        // 结果那条条件永远等不到（界面还没开始绘制就不会再有下一帧），会一直卡在启动图。
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // ViewModel 需在主题外层创建：主题模式存在其中，切换时要能触发重组
            val vm: AppViewModel = viewModel()
            val dark = when (vm.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            WortschatzTheme(darkTheme = dark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WortschatzApp(vm = vm)
                }
            }
        }
    }
}
