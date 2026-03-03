package com.tacz.legacy.client.render.item

import com.tacz.legacy.client.render.camera.WeaponFovController
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

@SideOnly(Side.CLIENT)
public object FirstPersonFovCompensation {

    public fun currentScale(): Float {
        return sanitizeScale(WeaponFovController.currentDepthCompensationScale())
    }

    internal fun applyScale(x: Float, y: Float, z: Float, scale: Float): CompensatedVec3 {
        val safeScale = sanitizeScale(scale)
        return CompensatedVec3(
            x = x,
            y = y,
            z = z * safeScale
        )
    }

    internal fun sanitizeScale(scale: Float): Float {
        return scale
            .takeIf { it.isFinite() }
            ?.coerceIn(MIN_SCALE, MAX_SCALE)
            ?: 1f
    }

    internal data class CompensatedVec3(
        val x: Float,
        val y: Float,
        val z: Float
    )

    private const val MIN_SCALE: Float = 0.01f
    private const val MAX_SCALE: Float = 100f
}
