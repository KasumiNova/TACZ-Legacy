package com.tacz.legacy.client.render.camera

import com.tacz.legacy.common.domain.gunpack.GunRecoilKeyFrameData
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.random.Random

internal data class WeaponRecoilPoint(
    val timeMillis: Float,
    val value: Float
)

internal class WeaponRecoilCurve(
    private val points: List<WeaponRecoilPoint>
) {

    fun sample(timeMillis: Float): Float? {
        if (points.isEmpty()) {
            return null
        }

        val clampedTime = timeMillis.coerceAtLeast(0f)
        if (clampedTime > points.last().timeMillis) {
            return null
        }

        if (clampedTime <= points.first().timeMillis) {
            return points.first().value
        }

        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (clampedTime > current.timeMillis) {
                continue
            }

            val range = (current.timeMillis - previous.timeMillis).coerceAtLeast(TIME_EPSILON_MILLIS)
            val alpha = ((clampedTime - previous.timeMillis) / range).coerceIn(0f, 1f)
            return previous.value + (current.value - previous.value) * alpha
        }

        return points.last().value
    }

    companion object {
        private const val TIME_EPSILON_MILLIS: Float = 1e-3f
    }
}

internal object WeaponRecoilCurves {

    fun buildCurve(
        keyframes: List<GunRecoilKeyFrameData>,
        modifier: Float,
        random: Random = Random.Default
    ): WeaponRecoilCurve? {
        if (keyframes.isEmpty()) {
            return null
        }

        val normalizedModifier = modifier.coerceAtLeast(0f)
        val sorted = keyframes.sortedBy { it.timeSeconds.coerceAtLeast(0f) }
        if (sorted.isEmpty()) {
            return null
        }

        val points = mutableListOf(WeaponRecoilPoint(timeMillis = 0f, value = 0f))
        sorted.forEach { frame ->
            val rawTime = frame.timeSeconds.coerceAtLeast(0f) * MILLIS_PER_SECOND + START_OFFSET_MILLIS
            val resolvedTime = max(rawTime, points.last().timeMillis + MIN_SEGMENT_MILLIS)
            val minValue = min(frame.valueMin, frame.valueMax)
            val maxValue = max(frame.valueMin, frame.valueMax)
            val sampledValue = if (maxValue <= minValue) {
                minValue
            } else {
                random.nextFloat() * (maxValue - minValue) + minValue
            }

            points += WeaponRecoilPoint(
                timeMillis = resolvedTime,
                value = sampledValue * normalizedModifier
            )
        }

        return WeaponRecoilCurve(points)
    }

    fun resolveRecoilModifier(
        aimingProgress: Float,
        aimingZoom: Float,
        crawlRecoilMultiplier: Float,
        isCrawlingLike: Boolean
    ): Float {
        val clampedProgress = aimingProgress.coerceIn(0f, 1f)
        val normalizedZoom = aimingZoom
            .takeIf { it > 0f }
            ?.coerceAtLeast(1f)
            ?: 1f

        var modifier = 1f - clampedProgress + (clampedProgress / sqrt(normalizedZoom))
        if (isCrawlingLike) {
            modifier *= crawlRecoilMultiplier.coerceAtLeast(0f)
        }
        return modifier.coerceAtLeast(0f)
    }

    private const val MILLIS_PER_SECOND: Float = 1000f
    private const val START_OFFSET_MILLIS: Float = 30f
    private const val MIN_SEGMENT_MILLIS: Float = 0.001f
}
