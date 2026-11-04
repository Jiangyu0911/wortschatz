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
 * 静止、走路、手腕抖动、磕碰、单向甩动都不能触发；真实往复摇动必须触发；
 * 冷却期内不能连触发。
 */
class ShakeDetectorTest {

    /**
     * 以 50Hz（SENSOR_DELAY_GAME 的 20ms）采样，喂一段正弦往复振动，
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

    /** 喂一段给定的加速度序列（50Hz），返回触发次数 */
    private fun feedSequence(detector: ShakeDetector, values: List<Float>, startMs: Long = 0L): Int {
        var triggers = 0
        values.forEachIndexed { i, v ->
            if (detector.onSample(0f, v, 0f, startMs + i * 20L)) triggers++
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
    fun `手腕抖动不会触发`() {
        val detector = ShakeDetector()
        // 4Hz、幅度 8 m/s²：握着手机打字、上下楼时手的抖动量级
        assertEquals(0, feed(detector, amplitude = 8f, frequencyHz = 4f, durationMs = 2000))
    }

    @Test
    fun `放到桌上磕一下不会触发`() {
        val detector = ShakeDetector()
        // 静置若干帧后一次冲击，再回到静止
        val values = List(10) { GRAVITY } + listOf(GRAVITY + 20f, GRAVITY + 30f) + List(10) { GRAVITY }
        assertEquals(0, feedSequence(detector, values))
    }

    @Test
    fun `单向甩一下不会触发`() {
        val detector = ShakeDetector()
        // 从口袋掏出手机、或单手把手机往前一送：加速到位后停住，没有往复
        val values = List(5) { GRAVITY } +
            List(6) { GRAVITY + 18f } +
            listOf(GRAVITY + 18f, GRAVITY + 9f, GRAVITY) +
            List(10) { GRAVITY }
        assertEquals(0, feedSequence(detector, values))
    }

    @Test
    fun `刻意往复摇动会触发`() {
        val detector = ShakeDetector()
        // 约 4Hz、幅度 25 m/s²：明显甩一下手机的量级
        assertTrue(feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 800) >= 1)
    }

    @Test
    fun `中等力度的摇动也会触发`() {
        val detector = ShakeDetector()
        // 3Hz、幅度 20 m/s²：不算用力，但确实是来回摇
        assertTrue(feed(detector, amplitude = 20f, frequencyHz = 3f, durationMs = 1000) >= 1)
    }

    @Test
    fun `很轻的晃动不触发`() {
        val detector = ShakeDetector()
        // 3Hz、幅度 12 m/s²：只是随手晃一下
        assertEquals(0, feed(detector, amplitude = 12f, frequencyHz = 3f, durationMs = 1000))
    }

    @Test
    fun `冷却时间内不会连续触发`() {
        val detector = ShakeDetector()
        feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 500, startMs = 0)
        val first = detector.triggerCount
        assertTrue(first >= 1)

        // 紧接着再摇：仍在冷却期（2000ms）内，不应再触发
        feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 500, startMs = 700)
        assertEquals(first, detector.triggerCount)

        // 冷却过去之后再摇，应该能触发
        feed(detector, amplitude = 25f, frequencyHz = 4f, durationMs = 600, startMs = 3500)
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
