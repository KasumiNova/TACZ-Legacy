package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.entity.EntityLivingBase

/**
 * 服务端拉栓逻辑。与上游 TACZ LivingEntityBolt 行为一致。
 */
public class LivingEntityBolt(
    private val shooter: EntityLivingBase,
    private val data: ShooterDataHolder,
    private val draw: LivingEntityDrawGun,
) {
    /**
     * 执行拉栓操作。
     */
    public fun bolt() {
        val supplier = data.currentGunItem ?: return
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return

        // 过滤：开膛/闭膛枪不需要手动拉栓
        if (gunData.boltType != BoltType.MANUAL_ACTION) return
        // 已有膛内弹
        if (iGun.hasBulletInBarrel(currentGunItem)) return
        // 没弹药可上
        if (iGun.getCurrentAmmoCount(currentGunItem) <= 0) return
        // 正在换弹
        if (data.reloadStateType.isReloading()) return
        // 正在切枪
        if (draw.getDrawCoolDown() != 0L) return
        // 已在拉栓
        if (data.isBolting) return

        data.isBolting = true
        data.boltTimestamp = System.currentTimeMillis()
    }

    /**
     * 每 tick 检查拉栓是否完成。
     */
    public fun tickBolt() {
        if (!data.isBolting) return
        val supplier = data.currentGunItem ?: return
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return

        val elapsed = System.currentTimeMillis() - data.boltTimestamp
        if (elapsed >= (gunData.boltTimeS * 1000).toLong()) {
            // 完成拉栓：弹匣 -> 膛
            iGun.reduceCurrentAmmoCount(currentGunItem)
            iGun.setBulletInBarrel(currentGunItem, true)
            data.isBolting = false
            data.boltTimestamp = -1L
        }
    }
}
