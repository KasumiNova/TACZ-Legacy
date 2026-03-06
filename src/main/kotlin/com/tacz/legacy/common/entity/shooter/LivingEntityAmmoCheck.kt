package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.entity.EntityLivingBase

/**
 * 弹药存量检查辅助。与上游 TACZ LivingEntityAmmoCheck 行为一致。
 * 提供 needCheckAmmo / consumesAmmoOrNot 查询。
 */
public class LivingEntityAmmoCheck(
    private val shooter: EntityLivingBase,
    private val data: ShooterDataHolder,
) {
    /**
     * 当前手持枪械是否需要检查弹药。
     * 创造模式/无限装填武器不需要。
     */
    public fun needCheckAmmo(): Boolean {
        val supplier = data.currentGunItem ?: return false
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return false
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return true

        if (gunData.isReloadInfinite) return false
        // 创造模式不检查弹药
        if (shooter is net.minecraft.entity.player.EntityPlayer && shooter.isCreative) return false
        return true
    }

    /**
     * 当前枪械是否消耗弹药（与 needCheckAmmo 不同——这里表示逻辑上会不会从背包/弹匣消耗）。
     */
    public fun consumesAmmoOrNot(): Boolean {
        val supplier = data.currentGunItem ?: return false
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return false
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return true

        if (gunData.isReloadInfinite) return false
        if (shooter is net.minecraft.entity.player.EntityPlayer && shooter.isCreative) return false
        return true
    }
}
