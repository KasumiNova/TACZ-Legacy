package com.tacz.legacy.client.render.camera

import com.tacz.legacy.client.render.RenderPipelineRuntime
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.FOVUpdateEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * 对齐 TACZ 的「禁用移速属性 FOV」行为：
 * - 持枪且开关开启时，屏蔽移速属性带来的 FOV 波动；
 * - 仅保留飞行/疾跑基础系数。
 */
public class WeaponMovementFovHandler {

    @SubscribeEvent
    public fun onFovUpdate(event: FOVUpdateEvent) {
        val player = event.entity ?: return
        val config = RenderPipelineRuntime.currentConfig()
        if (!shouldSuppressMovementFov(
                configEnabled = config.disableMovementFovEffectWhenHoldingGun,
                player = player
            )
        ) {
            return
        }

        event.newfov = resolveBaseGunFovModifier(
            isFlying = player.capabilities?.isFlying == true,
            isSprinting = player.isSprinting
        )
    }

    internal fun shouldSuppressMovementFov(configEnabled: Boolean, player: EntityPlayer): Boolean {
        val mainHand = player.heldItemMainhand
        return shouldSuppressMovementFov(
            configEnabled = configEnabled,
            holdingLegacyGun = isLegacyGun(mainHand)
        )
    }

    internal fun shouldSuppressMovementFov(configEnabled: Boolean, holdingLegacyGun: Boolean): Boolean {
        if (!configEnabled) {
            return false
        }
        return holdingLegacyGun
    }

    internal fun resolveBaseGunFovModifier(isFlying: Boolean, isSprinting: Boolean): Float {
        var modifier = 1f
        if (isFlying) {
            modifier *= FLYING_FOV_FACTOR
        }
        if (isSprinting) {
            modifier *= SPRINTING_FOV_FACTOR
        }
        return modifier
    }

    private fun isLegacyGun(stack: ItemStack): Boolean {
        if (stack.isEmpty) {
            return false
        }
        return stack.item is LegacyGunItem
    }

    private companion object {
        private const val FLYING_FOV_FACTOR: Float = 1.1f
        private const val SPRINTING_FOV_FACTOR: Float = 1.15f
    }
}
