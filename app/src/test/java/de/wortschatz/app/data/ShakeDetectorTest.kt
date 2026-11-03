package de.wortschatz.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/**
 * 摇一摇判定逻辑的回归测试。
 *
 * 阈值这类参数没法靠界面截图验证，用合成加速度序列把几种常见情形固定下来：
 * 静止、走路、磕碰都不能触发；真实甩动必须触发；冷却期内不能连触发。
 */
class ShakeDetectorTest {

    /**
     * 以 50Hz（SENSOR_DELAY_GAME 的 20ms）采样，喂一段正弦振动，
     * 返回这段时间里触发的次数。
     */
    private fun feed(
        detector: ShakeDetector,
        amplitude: Float,
        frequencyHz: Float,
        durationMs: Int,
        startMs: Long = 0L
    ): Int {
        var triggers = 0
        var t = 0
        while (t <= durationMs) {
            val seconds = t / 1000f
            // 静止读数是「支撑力」= 重力，方向取 y 轴
            val value = GRAVITY + amplitude * sin(2 * PI.toFloat() * frequencyHz * seconds)
            if (detector.onSample(0f, value, 0f, startMs + t)) triggers++
            t += 20
        }
        return triggers
    }

    @Test
    fun `静止放着不会触发`() {
        val detector = ShakeDetector()
        repeat(100) { i ->
            assertFalse(detector.onSample(0f, GRAVITY, 0f, i * 20L))
        }
        assertEquals(0, detector.triggerCount)
    }

    @Test
    fun `走路时的轻微晃动不会触发`() {
        val detector = ShakeDetector()
        // 约 2Hz、幅度 3 m/s²：手机放口袋里走路的量级
        assertEquals(0, feed(detector, amplitude = 3f, frequencyHz = 2f, durationMs = 3000))
    }

    @Test
    fun `放到桌上磕一下不会触发`() {
        val detector = ShakeDetector()
        // 静置若干帧后一次冲击，再回到静止：只有起手和收手两帧超阈值，凑不够 4 帧
        var t = 0L
        repeat(10) {
            detector.onSample(0f, GRAVITY, 0f, t)
            t += 20
        }
        detector.onSample(0f, 30f, 0f, t)
        t += 20
        detector.onSample(0f, GRAVITY, 0f, t)

        assertEquals(0, detector.triggerCount)
    }

    @Test
    fun `刻意甩动会触发`() {
        val detector = ShakeDetector()
        // 约 4Hz、幅度 25 m/s²：明显甩一下手机的量级
        assertTrue(feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 600) >= 1)
    }

    @Test
    fun `慢一些的摇动也能触发`() {
        val detector = ShakeDetector()
        // 3Hz、幅度 16 m/s²：不算用力但确实在摇
        assertTrue(feed(detector, amplitude = 16f, frequencyHz = 3f, durationMs = 800) >= 1)
    }

    @Test
    fun `冷却时间内不会连续触发`() {
        val detector = ShakeDetector()
        feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 400, startMs = 0)
        val first = detector.triggerCount
        assertTrue(first >= 1)

        // 紧接着再甩：仍在冷却期（1500ms）内，不应再触发
        feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 400, startMs = 500)
        assertEquals(first, detector.triggerCount)

        // 冷却过去之后再甩，应该能触发
        feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 400, startMs = 3000)
        assertTrue(detector.triggerCount > first)
    }

    @Test
    fun `重置后第一帧不参与判定`() {
        val detector = ShakeDetector()
        assertFalse(detector.onSample(0f, 40f, 0f, 0L))
        detector.reset()
        assertFalse(detector.onSample(0f, 40f, 0f, 10_000L))
        assertEquals(0, detector.triggerCount)
    }

    private companion object {
        const val GRAVITY = 9.8f
    }
}
