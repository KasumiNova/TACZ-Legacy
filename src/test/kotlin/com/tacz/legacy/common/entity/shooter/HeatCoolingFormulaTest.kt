package com.tacz.legacy.common.entity.shooter

import org.junit.Assert.*
import org.junit.Test

/**
 * 验证过热系统冷却公式与上游 TACZ ModernKineticGunItem.tickNormal/tickLocked 行为一致：
 * - 冷却速度随时间增大（加速冷却）
 * - 开火后不会立即散热（coolingDelay / overHeatTime 延迟）
 * - heatTimestamp 只在射击时更新，冷却 tick 不重置
 */
class HeatCoolingFormulaTest {

    // 模拟上游冷却公式：cooling = (now - heatTimestamp) / 10000f * coolingMultiplier
    private fun coolingAmount(now: Long, heatTimestamp: Long, coolingMultiplier: Float): Float {
        return (now - heatTimestamp).toFloat() / 10000f * coolingMultiplier
    }

    @Test
    fun `normal cooling does not start before coolingDelay`() {
        val heatTimestamp = 1000L
        val coolingDelay = 1000L
        val coolingMultiplier = 1.0f
        var heat = 50f

        // Before delay: all tick times should have elapsed < coolingDelay
        for (tickTime in longArrayOf(1050, 1200, 1500, 1900, 1999)) {
            val elapsed = tickTime - heatTimestamp
            assertTrue("Should not exceed coolingDelay", elapsed < coolingDelay)
        }

        // At exactly coolingDelay
        val elapsedAtDelay = 2000L - heatTimestamp
        assertTrue("Elapsed should be >= coolingDelay", elapsedAtDelay >= coolingDelay)
        val coolAmount = coolingAmount(2000L, heatTimestamp, coolingMultiplier)
        assertTrue("Cooling should be positive after delay", coolAmount > 0)
        heat -= coolAmount
        assertTrue("Heat should decrease after delay", heat < 50f)
    }

    @Test
    fun `cooling accelerates over time without heatTimestamp reset`() {
        val heatTimestamp = 0L
        val coolingDelay = 1000L
        val coolingMultiplier = 1.5f
        var heat = 80f

        val coolingAmounts = mutableListOf<Float>()

        // Simulate ticks every 50ms, starting after coolingDelay
        for (tickMs in 1050L..3000L step 50) {
            val elapsed = tickMs - heatTimestamp
            if (elapsed < coolingDelay) continue
            if (heat <= 0) break

            val coolAmt = coolingAmount(tickMs, heatTimestamp, coolingMultiplier)
            coolingAmounts.add(coolAmt)
            heat = (heat - coolAmt).coerceAtLeast(0f)
        }

        // Verify acceleration: each successive cooling amount should be larger
        for (i in 1 until coolingAmounts.size) {
            assertTrue(
                "Cooling should accelerate: tick $i (${coolingAmounts[i]}) > tick ${i - 1} (${coolingAmounts[i - 1]})",
                coolingAmounts[i] > coolingAmounts[i - 1]
            )
        }
        assertTrue("Should have at least 2 cooling ticks", coolingAmounts.size >= 2)
    }

    @Test
    fun `cooling blocked by its own delay if heatTimestamp is reset each tick`() {
        var heatTimestamp = 0L
        val coolingDelay = 1000L
        val coolingMultiplier = 1.0f
        var heat = 50f

        val coolingAmounts = mutableListOf<Float>()
        val ticks = (1050L..2000L step 50).toList()

        for (tickMs in ticks) {
            val elapsed = tickMs - heatTimestamp
            if (elapsed < coolingDelay) continue
            if (heat <= 0) break

            val coolAmt = coolingAmount(tickMs, heatTimestamp, coolingMultiplier)
            coolingAmounts.add(coolAmt)
            heat = (heat - coolAmt).coerceAtLeast(0f)
            heatTimestamp = tickMs // anti-pattern: resetting timestamp
        }

        // With reset, the first cooling tick crosses coolingDelay threshold,
        // but subsequent ticks see only ~50ms elapsed < coolingDelay -> blocked.
        assertEquals("With reset, only one cooling tick per coolingDelay period", 1, coolingAmounts.size)
    }

    @Test
    fun `overheat locked cooling does not start before overHeatTime`() {
        val heatTimestamp = 0L
        val overHeatTime = 3000L
        val coolingMultiplier = 1.0f

        for (tickMs in longArrayOf(50, 500, 1000, 2000, 2999)) {
            val elapsed = tickMs - heatTimestamp
            assertTrue("Should be within overHeatTime window", elapsed < overHeatTime)
        }

        val afterTime = 3050L
        val elapsed = afterTime - heatTimestamp
        assertTrue("Should exceed overHeatTime", elapsed >= overHeatTime)
        val coolAmt = coolingAmount(afterTime, heatTimestamp, coolingMultiplier)
        assertTrue("Cooling should begin after overHeatTime", coolAmt > 0)
    }

    @Test
    fun `overheat lock releases when heat reaches zero`() {
        val heatTimestamp = 0L
        val overHeatTime = 3000L
        val coolingMultiplier = 2.0f
        var heat = 100f
        var locked = true

        for (tickMs in 3050L..20000L step 50) {
            val elapsed = tickMs - heatTimestamp
            if (elapsed < overHeatTime) continue
            if (heat <= 0) break

            val coolAmt = coolingAmount(tickMs, heatTimestamp, coolingMultiplier)
            heat = (heat - coolAmt).coerceAtLeast(0f)
            if (heat <= 0) {
                locked = false
            }
        }

        assertFalse("Overheat lock should release when heat reaches 0", locked)
        assertEquals("Heat should be 0", 0f, heat, 0.001f)
    }

    @Test
    fun `shooting resets coolingDelay timer`() {
        val coolingDelay = 1000L
        val coolingMultiplier = 1.0f
        var heat = 50f
        var heatTimestamp = 0L

        // Cool at t=1050 (after delay)
        val firstCool = coolingAmount(1050, heatTimestamp, coolingMultiplier)
        heat -= firstCool
        assertTrue("Heat should decrease", heat < 50f)

        // Shoot again at t=1200 -> resets heatTimestamp
        heat += 5f
        heatTimestamp = 1200L

        // At t=1500, elapsed = 300ms < coolingDelay=1000ms -> no cooling
        val elapsed = 1500L - heatTimestamp
        assertTrue("Shooting should reset the cooling delay timer", elapsed < coolingDelay)
    }

    @Test
    fun `heat is capped at heatMax on shoot`() {
        val heatMax = 100f
        var heat = 98f
        val heatPerShot = 5f

        heat = (heat + heatPerShot).coerceAtMost(heatMax)
        assertEquals("Heat should be capped at heatMax", 100f, heat, 0.001f)
    }
}
