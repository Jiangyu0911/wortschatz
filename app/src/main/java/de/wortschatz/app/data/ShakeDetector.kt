package de.wortschatz.app.data

import kotlin.math.sqrt

/**
 * 摇一摇手势判定。
 *
 * 单独抽出来的原因：阈值这类参数没法靠界面截图验证，只能拿一串加速度数据跑一遍看结果。
 * 这里不依赖任何 Android 类型，喂 (x, y, z, 时间戳) 即可，见 ShakeDetectorTest。
 *
 * 三个判据叠加，缺一不可：
 *
 * 1. **变化率够大**：看相邻两帧加速度的变化率（m/s³），而不是相对重力的偏移量。
 *    偏移量依赖手机朝向，慢速摇动偏移不明显；变化率只看「动得有多急」，与朝向无关。
 * 2. **持续够久**：单帧尖峰（磕碰、轻敲屏幕）不算，需在 [windowMs] 内累计 [hitsNeeded] 次命中。
 * 3. **方向来回翻转**：摇动一定是往复的，而日常动作（从口袋掏出、放下手机、单手甩一下）
 *    大多是**单向**加速后停止，只会有零星几次命中而不反转。要求累计 [reversalsNeeded]
 *    次方向反转才能触发——这条是把误触真正挡掉的关键，只调阈值做不到。
 *
 * 两次触发之间至少隔 [cooldownMs]，避免一次甩动连弹两个确认框。
 *
 * 灵敏度调优（真机上「太灵」或「摇不动」时改这里）：
 * - 太灵敏：`threshold` 调高（350–400），或 `reversalsNeeded` 加到 3
 * - 摇不动：`threshold` 调低（250 左右），或 `hitsNeeded` 减到 5
 */
class ShakeDetector(
    /** 变化率阈值，单位 m/s³（相邻帧加速度差 ÷ 帧间隔）。300 约等于 20ms 内变化 6 m/s² */
    private val threshold: Float = 300f,
    /** 窗口内需要的命中次数 */
    private val hitsNeeded: Int = 6,
    /** 需要的方向反转次数（一个完整来回算两次） */
    private val reversalsNeeded: Int = 2,
    /** 命中累计窗口：超过这个时间重新开始计数 */
    private val windowMs: Long = 800L,
    private val cooldownMs: Long = COOLDOWN_MS
) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private var lastTs = -1L

    private var hits = 0
    private var reversals = 0
    private var windowStart = 0L

    /** 上一次命中时的加速度变化向量，用来判断这次是不是反着来 */
    private var lastHitDx = 0f
    private var lastHitDy = 0f
    private var lastHitDz = 0f
    private var hasHitDir = false

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

        // 命中按时间窗口累计：窗口一过就重新计数，避免零星几次动作慢慢攒够
        if (hits == 0 || nowMs - windowStart > windowMs) {
            windowStart = nowMs
            hits = 0
            reversals = 0
            hasHitDir = false
        }

        // 与上一次命中的变化方向反号，说明手来回翻了一次
        if (hasHitDir && dx * lastHitDx + dy * lastHitDy + dz * lastHitDz < 0f) {
            reversals++
        }
        lastHitDx = dx; lastHitDy = dy; lastHitDz = dz; hasHitDir = true
        hits++

        if (hits < hitsNeeded || reversals < reversalsNeeded) return false
        if (nowMs - lastTrigger < cooldownMs) return false

        lastTrigger = nowMs
        hits = 0
        reversals = 0
        hasHitDir = false
        triggerCount++
        return true
    }

    /** 重新开始监听时调用：丢弃上一帧与累计计数 */
    fun reset() {
        lastTs = -1L
        hits = 0
        reversals = 0
        hasHitDir = false
        windowStart = 0L
        lastTrigger = -cooldownMs
    }

    companion object {
        /** 两次触发的默认间隔；界面层的跨监听器守卫也用这个值，避免两处各写一个数 */
        const val COOLDOWN_MS = 2000L
    }
}
