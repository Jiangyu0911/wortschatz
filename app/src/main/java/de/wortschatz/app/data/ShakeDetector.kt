package de.wortschatz.app.data

import kotlin.math.sqrt

/**
 * 摇一摇手势判定。
 *
 * 单独抽出来的原因：阈值这类参数没法靠界面截图验证，只能拿一串加速度数据跑一遍看结果。
 * 这里不依赖任何 Android 类型，喂 (x, y, z, 时间戳) 即可，见 ShakeDetectorTest。
 *
 * 用「相邻两帧加速度的变化率」而不是「相对重力的偏移量」来判定，原因是：
 * - 手机正常握持时，加速度里混着方向变化与重力分量，减去重力得到的偏移量对慢速摇动不敏感；
 * - 变化率只看「动得有多急」，与手机朝向无关：走路（约 2Hz、幅度 3m/s²）只有 40 上下，
 *   轻摇（3Hz、16m/s²）约 300，用力甩（4Hz、25m/s²）能到 600 以上，区分度足够。
 *
 * 单帧尖峰不算摇动（磕碰、轻敲屏幕都只有一两帧），需要 [windowMs] 内累计
 * [hitsNeeded] 帧超阈值；两次触发之间至少隔 [cooldownMs]，避免一次甩动连弹两个框。
 */
class ShakeDetector(
    /** 变化率阈值，单位 m/s³（相邻帧加速度差 ÷ 帧间隔） */
    private val threshold: Float = 200f,
    private val hitsNeeded: Int = 4,
    private val windowMs: Long = 600L,
    private val cooldownMs: Long = 1500L
) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastTs = -1L
    private var hits = 0
    private var windowStart = 0L
    // 初始值放到「很久以前」：否则开头 cooldownMs 内的第一下摇动会被自己挡住
    private var lastTrigger = -cooldownMs

    /** 已触发次数，便于测试与排查 */
    var triggerCount: Int = 0
        private set

    /**
     * 喂一帧加速度（单位 m/s²）与时刻（毫秒）。
     * 返回 true 表示这一帧构成了一次摇一摇。
     */
    fun onSample(x: Float, y: Float, z: Float, nowMs: Long): Boolean {
        if (lastTs < 0) {
            // 第一帧没有上一帧可比，只记录下来
            lastX = x; lastY = y; lastZ = z; lastTs = nowMs
            return false
        }
        val dt = (nowMs - lastTs).coerceAtLeast(1L)
        val dx = x - lastX
        val dy = y - lastY
        val dz = z - lastZ
        lastX = x; lastY = y; lastZ = z; lastTs = nowMs

        val rate = sqrt(dx * dx + dy * dy + dz * dz) * 1000f / dt
        if (rate < threshold) return false

        // 命中按时间窗口累计：窗口内凑够次数才算一次摇动，窗口一过重新计数
        if (hits == 0 || nowMs - windowStart > windowMs) {
            windowStart = nowMs
            hits = 0
        }
        hits++
        if (hits < hitsNeeded) return false
        if (nowMs - lastTrigger < cooldownMs) return false

        lastTrigger = nowMs
        hits = 0
        triggerCount++
        return true
    }

    /** 重新开始监听时调用：丢弃上一帧与累计计数 */
    fun reset() {
        lastTs = -1L
        hits = 0
        windowStart = 0L
        lastTrigger = -cooldownMs
    }
}
