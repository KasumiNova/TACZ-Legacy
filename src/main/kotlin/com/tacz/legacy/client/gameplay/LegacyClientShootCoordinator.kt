package com.tacz.legacy.client.gameplay

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.event.GunShootEvent
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.event.FirstPersonRenderGunEvent
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerShoot
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunCombatData
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemStack
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.relauncher.Side

/**
 * 客户端射击请求协调器。
 *
 * 对齐上游 LocalPlayerShoot：客户端只负责本地门禁、冷却预测、动画与发包，
 * 不在本地真实执行 operator.shoot() 来修改弹药/膛内状态。
 */
internal object LegacyClientShootCoordinator {
    private var clientShootTimestampMs: Long = -1L
    private var clientLastShootTimestampMs: Long = -1L

    internal fun resetTiming() {
        clientShootTimestampMs = -1L
        clientLastShootTimestampMs = -1L
    }

    internal fun attemptShoot(
        player: EntityPlayerSP,
        operator: IGunOperator,
        pitch: Float = player.rotationPitch,
        yaw: Float = player.rotationYaw,
        triggerAnimation: Boolean = true,
    ): ShootResult {
        val stack = player.heldItemMainhand
        val iGun = stack.item as? IGun ?: return ShootResult.NOT_GUN
        val gunId = iGun.getGunId(stack)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return ShootResult.ID_NOT_EXIST

        val coolDown = getClientShootCoolDown(stack, iGun, gunData)
        if (coolDown > 0L) return ShootResult.COOL_DOWN
        if (operator.getSynReloadState().stateType.isReloading()) return ShootResult.IS_RELOADING
        if (operator.getSynDrawCoolDown() != 0L) return ShootResult.IS_DRAWING
        if (operator.getSynIsBolting()) return ShootResult.IS_BOLTING
        if (operator.getSynMeleeCoolDown() != 0L) return ShootResult.IS_MELEE

        val boltType = gunData.boltType
        val useInventoryAmmo = iGun.useInventoryAmmo(stack)
        val hasAmmoInBarrel = iGun.hasBulletInBarrel(stack) && boltType != BoltType.OPEN_BOLT
        val hasInventoryAmmo = iGun.hasInventoryAmmo(player, stack, operator.needCheckAmmo()) || hasAmmoInBarrel
        val ammoCount = iGun.getCurrentAmmoCount(stack) + (if (hasAmmoInBarrel) 1 else 0)
        val noAmmo = (useInventoryAmmo && !hasInventoryAmmo) || (!useInventoryAmmo && ammoCount < 1)
        if (noAmmo) return ShootResult.NO_AMMO
        if (gunData.hasHeatData && iGun.isOverheatLocked(stack)) return ShootResult.OVERHEATED
        if (boltType == BoltType.MANUAL_ACTION && !hasAmmoInBarrel) return ShootResult.NEED_BOLT
        if (operator.getSynSprintTime() > 0f) return ShootResult.IS_SPRINTING

        val shootEvent = GunShootEvent(player, stack, Side.CLIENT)
        if (MinecraftForge.EVENT_BUS.post(shootEvent)) return ShootResult.FORGE_EVENT_CANCEL

        val now = System.currentTimeMillis()
        val relativeTimestamp = now - operator.getDataHolder().baseTimestamp
        operator.getDataHolder().lastShootTimestamp = operator.getDataHolder().shootTimestamp
        operator.getDataHolder().shootTimestamp = relativeTimestamp
        clientLastShootTimestampMs = clientShootTimestampMs
        clientShootTimestampMs = now

        TACZNetworkHandler.sendToServer(ClientMessagePlayerShoot(pitch, yaw, relativeTimestamp))
        if (triggerAnimation) {
            LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_SHOOT)
        }
        // Record shoot timestamp for procedural recoil/sway animation
        FirstPersonRenderGunEvent.onShoot()
        return ShootResult.SUCCESS
    }

    private fun getClientShootCoolDown(stack: ItemStack, iGun: IGun, gunData: GunCombatData): Long {
        if (clientShootTimestampMs < 0L) return 0L
        val fireMode = iGun.getFireMode(stack)
        val interval = if (fireMode == FireMode.BURST) {
            (gunData.burstMinInterval * 1000f).toLong()
        } else {
            gunData.getShootIntervalMs()
        }
        var coolDown = interval - (System.currentTimeMillis() - clientShootTimestampMs)
        coolDown -= 5L
        return if (coolDown < 0L) 0L else coolDown
    }
}