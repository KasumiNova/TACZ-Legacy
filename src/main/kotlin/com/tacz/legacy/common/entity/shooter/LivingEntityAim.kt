package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.entity.EntityLivingBase

/**
 * 服务端瞄准逻辑。与上游 TACZ LivingEntityAim 行为一致。
 */
public class LivingEntityAim(
    private val shooter: EntityLivingBase,
    private val data: ShooterDataHolder,
    private val draw: LivingEntityDrawGun,
) {
    /**
     * 切换瞄准状态。
     */
    public fun aim(isAiming: Boolean) {
        val supplier = data.currentGunItem ?: return
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return

        // 切枪中不允许瞄准
        if (draw.getDrawCoolDown() != 0L) return
        // 在拉栓中不允许瞄准
        if (data.isBolting) return

        data.isAiming = isAiming
        data.aimingTimestamp = System.currentTimeMillis()
    }

    /**
     * 每 tick 更新瞄准进度（0.0 ~ 1.0）。
     */
    public fun tickAimingProgress() {
        val supplier = data.currentGunItem
        if (supplier == null) {
            data.aimingProgress = 0f
            return
        }
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun
        if (iGun == null) {
            data.aimingProgress = 0f
            return
        }
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId)
        if (gunData == null) {
            data.aimingProgress = 0f
            return
        }

        val aimTimeMs = (gunData.aimTimeS * 1000).toLong()
        if (aimTimeMs <= 0) {
            data.aimingProgress = if (data.isAiming) 1f else 0f
            return
        }

        val tickDeltaMs = 50L // 50ms per tick
        val progressPerTick = tickDeltaMs.toFloat() / aimTimeMs
        if (data.isAiming) {
            data.aimingProgress = (data.aimingProgress + progressPerTick).coerceIn(0f, 1f)
        } else {
            data.aimingProgress = (data.aimingProgress - progressPerTick).coerceIn(0f, 1f)
        }
    }
}
