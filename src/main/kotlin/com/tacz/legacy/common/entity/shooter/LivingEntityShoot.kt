package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageGunShoot
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import java.util.function.Supplier

/**
 * 服务端射击逻辑。与上游 TACZ LivingEntityShoot 行为一致。
 */
public class LivingEntityShoot(
    private val shooter: EntityLivingBase,
    private val data: ShooterDataHolder,
    private val draw: LivingEntityDrawGun,
) {
    public fun shoot(pitch: Supplier<Float>, yaw: Supplier<Float>, timestamp: Long): ShootResult {
        val supplier = data.currentGunItem ?: return ShootResult.NOT_DRAW
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return ShootResult.NOT_GUN
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return ShootResult.ID_NOT_EXIST

        // 射击冷却检查
        val coolDown = getShootCoolDown(timestamp)
        if (coolDown == -1L) return ShootResult.UNKNOWN_FAIL
        if (coolDown > 0) return ShootResult.COOL_DOWN

        // 网络时间戳校验
        if (shooter is EntityPlayerMP) {
            val alpha = System.currentTimeMillis() - data.baseTimestamp - timestamp
            if (alpha < -300 || alpha > 600) {
                // 时间戳偏移过大，重置 base timestamp
                data.baseTimestamp = System.currentTimeMillis()
                return ShootResult.NETWORK_FAIL
            }
        }

        // 检查是否正在换弹
        if (data.reloadStateType.isReloading()) return ShootResult.IS_RELOADING
        // 检查是否在切枪
        if (draw.getDrawCoolDown() != 0L) return ShootResult.IS_DRAWING
        // 检查是否在拉栓
        if (data.isBolting) return ShootResult.IS_BOLTING
        // 检查是否在奔跑
        if (data.sprintTimeS > 0) return ShootResult.IS_SPRINTING

        // 弹药检查
        val boltType = gunData.boltType
        val hasAmmoInBarrel = iGun.hasBulletInBarrel(currentGunItem) && boltType != BoltType.OPEN_BOLT
        val ammoCount = iGun.getCurrentAmmoCount(currentGunItem) + (if (hasAmmoInBarrel) 1 else 0)
        val useInventoryAmmo = iGun.useInventoryAmmo(currentGunItem)
        val hasInventoryAmmo = iGun.hasInventoryAmmo(shooter, currentGunItem, true) || hasAmmoInBarrel

        val noAmmo = (useInventoryAmmo && !hasInventoryAmmo) || (!useInventoryAmmo && ammoCount < 1)
        if (noAmmo) return ShootResult.NO_AMMO

        // 过热检查
        if (gunData.hasHeatData && iGun.isOverheatLocked(currentGunItem)) return ShootResult.OVERHEATED

        // 膛内子弹检查（手动拉栓枪）
        if (boltType == BoltType.MANUAL_ACTION && !hasAmmoInBarrel) return ShootResult.NEED_BOLT

        // 闭膛上膛逻辑
        if (boltType == BoltType.CLOSED_BOLT && !hasAmmoInBarrel) {
            if (useInventoryAmmo) {
                // 从弹药库消耗 1 发作为膛内弹
            } else {
                iGun.reduceCurrentAmmoCount(currentGunItem)
            }
            iGun.setBulletInBarrel(currentGunItem, true)
        }

        // 广播射击事件
        TACZNetworkHandler.sendToTrackingEntity(ServerMessageGunShoot(shooter.entityId), shooter)

        data.lastShootTimestamp = data.shootTimestamp
        data.shootTimestamp = timestamp

        // 执行实际射击——消耗弹药、生成子弹实体
        executeShoot(currentGunItem, iGun, gunData, pitch, yaw)

        return ShootResult.SUCCESS
    }

    private fun executeShoot(
        gunItem: ItemStack,
        iGun: IGun,
        gunData: com.tacz.legacy.common.resource.GunCombatData,
        pitch: Supplier<Float>,
        yaw: Supplier<Float>,
    ) {
        // 消耗弹药
        val useInventoryAmmo = iGun.useInventoryAmmo(gunItem)
        if (useInventoryAmmo) {
            // 背包直读消耗 TODO: from inventory
        } else {
            if (iGun.hasBulletInBarrel(gunItem) && gunData.boltType != BoltType.OPEN_BOLT) {
                iGun.setBulletInBarrel(gunItem, false)
            } else {
                iGun.reduceCurrentAmmoCount(gunItem)
            }
        }

        // 生成子弹实体
        if (!shooter.world.isRemote) {
            val bullet = com.tacz.legacy.common.entity.EntityKineticBullet(shooter.world, shooter)

            val pitchVal = pitch.get()
            val yawVal = yaw.get()
            val speed = 3.0f
            bullet.shoot(shooter, pitchVal, yawVal, 0.0f, speed, 1.0f)
            shooter.world.spawnEntity(bullet)
        }
    }

    /**
     * 以当前时间戳查询射击冷却。
     */
    public fun getShootCoolDown(): Long {
        return getShootCoolDown(System.currentTimeMillis() - data.baseTimestamp)
    }

    /**
     * 根据指定 timestamp 查询射击冷却。
     */
    public fun getShootCoolDown(timestamp: Long): Long {
        val supplier = data.currentGunItem ?: return 0L
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return 0L
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return -1L
        val fireMode = iGun.getFireMode(currentGunItem)
        val interval = timestamp - data.shootTimestamp

        val shootInterval = if (fireMode == FireMode.BURST) {
            (gunData.burstMinInterval * 1000f).toLong()
        } else {
            gunData.getShootIntervalMs()
        }

        var coolDown = shootInterval - interval
        coolDown -= 5 // 5ms window
        return if (coolDown < 0) 0L else coolDown
    }
}
