package de.wortschatz.app.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.wortschatz.app.AppViewModel
import de.wortschatz.app.data.ShakeDetector

/** 两次触发的最小间隔，避免一次甩动连弹两次弹窗 */
private const val SHAKE_COOLDOWN_MS = 1500L

/**
 * 摇一摇撤销。
 *
 * 早前撤销是底部一条常驻提示条，学习页、拼写页、导入页都要给它留出高度，
 * 页面本来就满，键盘一弹起就把按钮挤走。改成摇一摇后，撤销不再占任何布局空间：
 *
 * - 只有存在可撤销操作时才注册传感器（省电，且走路时不会莫名弹窗）
 * - 摇动后先震动一下，再弹确认框，由用户决定是否回退
 * - 弹窗是系统级 Window，任何页面（含全屏的学习页、拼写页）都能弹出
 */
@Composable
fun ShakeUndoHost(vm: AppViewModel) {
    val context = LocalContext.current
    val label = vm.undoLabel
    var prompt by remember { mutableStateOf<String?>(null) }

    // 撤销条没了，功能就没了入口：第一次出现可撤销操作时提示一次
    LaunchedEffect(label) {
        if (label == null || !vm.shakeUndoEnabled) return@LaunchedEffect
        if (vm.shakeHintShown()) return@LaunchedEffect
        vm.markShakeHintShown()
        Toast.makeText(context, "摇一摇手机可以撤销这一步", Toast.LENGTH_SHORT).show()
    }

    ShakeSensor(
        enabled = vm.shakeUndoEnabled && label != null && prompt == null
    ) {
        val current = vm.undoLabel ?: return@ShakeSensor
        buzz(context)
        prompt = current
    }

    // 撤销有效期一过（undoLabel 变 null），弹窗自动收起，不会留下点了没反应的按钮
    val text = prompt
    if (text != null && label != null) {
        AlertDialog(
            onDismissRequest = { prompt = null },
            title = {
                Text(
                    text = "撤销这一步？",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = text,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    prompt = null
                    vm.undo()
                }) {
                    Text("撤销", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { prompt = null }) {
                    Text("不用了", fontSize = 14.sp)
                }
            }
        )
    }
}

/**
 * 监听加速度传感器，识别「摇一摇」手势。
 *
 * 判定细节在 [ShakeDetector]：单帧尖峰（磕碰、轻敲）不算，窗口内累计够次数才算一次摇动。
 * 这里只负责传感器注册/注销与生命周期绑定——页面退到后台就停，省电也不误触。
 */
@Composable
private fun ShakeSensor(enabled: Boolean, onShake: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnShake = rememberUpdatedState(onShake)
    // 冷却时间要跟着组合存活，而不是跟着监听器：弹窗弹出时监听器会注销重建，
    // 重建后如果冷却归零，弹窗刚关掉的那一下抖动尾巴会立刻再弹一次
    val lastTrigger = remember { longArrayOf(-SHAKE_COOLDOWN_MS) }

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose { }

        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) return@DisposableEffect onDispose { }

        var registered = false

        val listener = object : SensorEventListener {
            private val detector = ShakeDetector(cooldownMs = SHAKE_COOLDOWN_MS)

            override fun onSensorChanged(event: SensorEvent) {
                val v = event.values
                val triggered = detector.onSample(
                    x = v[0],
                    y = v[1],
                    z = v[2],
                    nowMs = SystemClock.elapsedRealtime()
                )
                if (!triggered) return
                // 冷却时间跨重注册共享：弹窗关掉后监听器会注销重建，
                // 若冷却归零，抖动的尾巴会立刻再弹一次
                val now = SystemClock.elapsedRealtime()
                if (now - lastTrigger[0] < SHAKE_COOLDOWN_MS) return
                lastTrigger[0] = now
                latestOnShake.value()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        fun start() {
            if (registered) return
            // GAME 档（约 20ms 一次）：摇动的峰值很短，UI 档会漏采样
            registered = manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }

        fun stop() {
            if (!registered) return
            manager.unregisterListener(listener)
            registered = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stop()
        }
    }
}

/** 摇动被识别时给一次短震动，让用户知道「手势收到了」 */
private fun buzz(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return

    if (!vibrator.hasVibrator()) return
    runCatching {
        vibrator.vibrate(VibrationEffect.createOneShot(35L, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
