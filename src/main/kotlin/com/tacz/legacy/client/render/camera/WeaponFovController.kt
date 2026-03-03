package com.tacz.legacy.client.render.camera

import com.tacz.legacy.client.render.item.LegacyGunItemStackRenderer
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.AbstractClientPlayer
import net.minecraft.item.ItemStack
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.tan

/**
 * TACZ 风格 FOV 对齐控制器：
 * - 世界渲染 FOV（useFovSetting=true）：按瞄准倍率做 magnification->fov，并做平滑。
 * - 手部物品 FOV（useFovSetting=false）：按 zoom_model_fov / scope views_fov 做 ADS 插值，并做平滑。
 */
public object WeaponFovController {

    private val worldFovDynamics: SmoothedValue = SmoothedValue()
    private val itemModelFovDynamics: SmoothedValue = SmoothedValue()
    private var lastWorldUpdateNanos: Long = 0L
    private var lastItemModelUpdateNanos: Long = 0L
    private var lastResolvedWorldFovDegrees: Float = DEFAULT_WORLD_FOV_DEGREES
    private var lastResolvedItemFovDegrees: Float = DEFAULT_ITEM_FOV_DEGREES

    @JvmStatic
    public fun modifyFov(
        minecraft: Minecraft?,
        partialTicks: Float,
        useFovSetting: Boolean,
        currentFov: Float
    ): Float {
        if (minecraft == null) {
            return currentFov
        }

        val deltaSeconds = resolveDeltaSeconds(useFovSetting)
        val player = minecraft.player as? AbstractClientPlayer
        val isLocalFirstPerson = player != null && minecraft.renderViewEntity === player
        val mainHand = player?.heldItemMainhand

        val targetFov = if (isLocalFirstPerson && mainHand != null && isLegacyGun(mainHand)) {
            resolveLegacyGunTargetFov(
                minecraft = minecraft,
                itemStack = mainHand,
                partialTicks = partialTicks,
                useFovSetting = useFovSetting,
                currentFov = currentFov
            )
        } else {
            currentFov
        }

        val resolved = if (useFovSetting) {
            worldFovDynamics.update(
                target = targetFov,
                deltaSeconds = deltaSeconds,
                damping = WORLD_FOV_DAMPING
            )
        } else {
            itemModelFovDynamics.update(
                target = targetFov,
                deltaSeconds = deltaSeconds,
                damping = ITEM_MODEL_FOV_DAMPING
            )
        }

        if (useFovSetting) {
            lastResolvedWorldFovDegrees = resolved
        } else {
            lastResolvedItemFovDegrees = resolved
        }

        return resolved.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)
    }

    @JvmStatic
    public fun currentDepthCompensationScale(): Float {
        return resolveDepthCompensationScale(
            itemFovDegrees = lastResolvedItemFovDegrees,
            worldFovDegrees = lastResolvedWorldFovDegrees
        )
    }

    private fun resolveLegacyGunTargetFov(
        minecraft: Minecraft,
        itemStack: ItemStack,
        partialTicks: Float,
        useFovSetting: Boolean,
        currentFov: Float
    ): Float {
        val aimingProgress = LegacyGunItemStackRenderer.resolveFirstPersonAimingProgressForFov(
            itemStack = itemStack,
            minecraft = minecraft,
            partialTicks = partialTicks
        ).coerceIn(0f, 1f)

        return if (useFovSetting) {
            val aimingZoom = LegacyGunItemStackRenderer.resolveAimingZoomForFov(itemStack)
            val magnification = 1f + (aimingZoom - 1f) * aimingProgress
            magnificationToFov(
                baseFovDegrees = currentFov,
                magnification = magnification
            )
        } else {
            val modelFov = LegacyGunItemStackRenderer.resolveModelFovForFov(
                itemStack = itemStack,
                defaultFov = currentFov
            )
            resolveModelFovTarget(
                currentFovDegrees = currentFov,
                modelFovDegrees = modelFov,
                aimingProgress = aimingProgress
            )
        }
    }

    internal fun magnificationToFov(baseFovDegrees: Float, magnification: Float): Float {
        val base = baseFovDegrees.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)
        val safeMagnification = magnification.takeIf { it > 0f }?.coerceAtLeast(1f) ?: 1f
        if (safeMagnification == 1f) {
            return base
        }

        val halfRadians = Math.toRadians((base / 2f).toDouble())
        val transformedHalf = atan(tan(halfRadians) / safeMagnification.toDouble())
        return Math.toDegrees(transformedHalf * 2.0).toFloat()
            .coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)
    }

    internal fun resolveModelFovTarget(
        currentFovDegrees: Float,
        modelFovDegrees: Float,
        aimingProgress: Float
    ): Float {
        val current = currentFovDegrees.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)
        val model = modelFovDegrees.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)
        val alpha = aimingProgress.coerceIn(0f, 1f)
        return lerp(current, model, alpha)
    }

    internal fun resolveDepthCompensationScale(itemFovDegrees: Float, worldFovDegrees: Float): Float {
        val item = itemFovDegrees.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)
        val world = worldFovDegrees.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)

        val itemHalfRadians = Math.toRadians((item / 2f).toDouble())
        val worldHalfRadians = Math.toRadians((world / 2f).toDouble())
        val itemTan = tan(itemHalfRadians)
        val worldTan = tan(worldHalfRadians)
        if (!itemTan.isFinite() || !worldTan.isFinite() || worldTan == 0.0) {
            return 1f
        }

        val ratio = (itemTan / worldTan).toFloat()
        return ratio
            .takeIf { it.isFinite() }
            ?.coerceIn(MIN_DEPTH_COMPENSATION_SCALE, MAX_DEPTH_COMPENSATION_SCALE)
            ?: 1f
    }

    private fun lerp(from: Float, to: Float, alpha: Float): Float =
        from + (to - from) * alpha

    private fun isLegacyGun(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }
        return stack.item is LegacyGunItem
    }

    private fun resolveDeltaSeconds(useFovSetting: Boolean): Float {
        val now = System.nanoTime()
        return if (useFovSetting) {
            val delta = resolveDeltaSecondsFromNanos(
                nowNanos = now,
                lastUpdateNanos = lastWorldUpdateNanos
            )
            lastWorldUpdateNanos = now
            delta
        } else {
            val delta = resolveDeltaSecondsFromNanos(
                nowNanos = now,
                lastUpdateNanos = lastItemModelUpdateNanos
            )
            lastItemModelUpdateNanos = now
            delta
        }
    }

    internal fun resolveDeltaSecondsFromNanos(nowNanos: Long, lastUpdateNanos: Long): Float {
        if (lastUpdateNanos <= 0L || nowNanos <= lastUpdateNanos) {
            return DEFAULT_DELTA_SECONDS
        }

        return ((nowNanos - lastUpdateNanos).toFloat() / NANOS_PER_SECOND)
            .coerceIn(MIN_DELTA_SECONDS, MAX_DELTA_SECONDS)
    }

    private class SmoothedValue {
        private var value: Float = Float.NaN

        fun update(target: Float, deltaSeconds: Float, damping: Float): Float {
            val clampedTarget = target.coerceIn(MIN_FOV_DEGREES, MAX_FOV_DEGREES)
            if (!value.isFinite()) {
                value = clampedTarget
                return value
            }

            val blend = (1f - exp((-damping * deltaSeconds).toDouble()).toFloat())
                .coerceIn(0f, 1f)
            value += (clampedTarget - value) * blend
            return value
        }
    }

    private const val NANOS_PER_SECOND: Float = 1_000_000_000f
    private const val DEFAULT_DELTA_SECONDS: Float = 1f / 20f
    private const val MIN_DELTA_SECONDS: Float = 1f / 1000f
    private const val MAX_DELTA_SECONDS: Float = 0.25f

    private const val WORLD_FOV_DAMPING: Float = 8.0f
    private const val ITEM_MODEL_FOV_DAMPING: Float = 8.0f

    private const val MIN_FOV_DEGREES: Float = 1f
    private const val MAX_FOV_DEGREES: Float = 179f
    private const val DEFAULT_WORLD_FOV_DEGREES: Float = 70f
    private const val DEFAULT_ITEM_FOV_DEGREES: Float = 70f
    private const val MIN_DEPTH_COMPENSATION_SCALE: Float = 0.01f
    private const val MAX_DEPTH_COMPENSATION_SCALE: Float = 100f
}
