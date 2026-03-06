package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.entity.ReloadState
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageReload
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.entity.EntityLivingBase

/**
 * 服务端换弹逻辑。与上游 TACZ LivingEntityReload 行为一致。
 */
public class LivingEntityReload(
    private val shooter: EntityLivingBase,
    private val data: ShooterDataHolder,
    private val draw: LivingEntityDrawGun,
) {
    /**
     * 发起换弹操作。
     */
    public fun reload() {
        val supplier = data.currentGunItem ?: return
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return

        // 已经在换弹中
        if (data.reloadStateType.isReloading()) return
        // 还没有完成切枪
        if (draw.getDrawCoolDown() != 0L) return
        // 在拉栓
        if (data.isBolting) return

        val maxAmmo = gunData.ammoAmount
        val currentAmmo = iGun.getCurrentAmmoCount(currentGunItem)
        val hasBulletInBarrel = iGun.hasBulletInBarrel(currentGunItem)

        // 满弹判定
        val isBarrelFull = hasBulletInBarrel || gunData.boltType == BoltType.OPEN_BOLT
        if (currentAmmo >= maxAmmo && isBarrelFull) return

        // 弹药来源检查
        val needCheck = !gunData.isReloadInfinite
        if (needCheck) {
            val useInventoryAmmo = iGun.useInventoryAmmo(currentGunItem)
            if (useInventoryAmmo) {
                if (!iGun.hasInventoryAmmo(shooter, currentGunItem, needCheck)) return
            } else if (iGun.useDummyAmmo(currentGunItem)) {
                if (iGun.getDummyAmmoAmount(currentGunItem) <= 0) return
            }
        }

        // 判定空仓换弹 vs 战术换弹
        val isEmpty = currentAmmo <= 0 && !hasBulletInBarrel
        if (isEmpty) {
            data.reloadStateType = ReloadState.StateType.EMPTY_RELOAD_FEEDING
            data.reloadTimestamp = System.currentTimeMillis()

            // 调度 feeding -> finishing 定时器
            scheduleFeedingToFinishing(
                feedTimeMs = (gunData.emptyReloadFeedingTimeS * 1000).toLong(),
                finishTimeMs = (gunData.emptyReloadFinishingTimeS * 1000).toLong(),
                isEmpty = true,
                gunData = gunData,
            )
        } else {
            data.reloadStateType = ReloadState.StateType.TACTICAL_RELOAD_FEEDING
            data.reloadTimestamp = System.currentTimeMillis()

            scheduleFeedingToFinishing(
                feedTimeMs = (gunData.reloadFeedingTimeS * 1000).toLong(),
                finishTimeMs = (gunData.reloadFinishingTimeS * 1000).toLong(),
                isEmpty = false,
                gunData = gunData,
            )
        }

        // 广播换弹开始
        TACZNetworkHandler.sendToTrackingEntity(ServerMessageReload(shooter.entityId, data.reloadStateType), shooter)
    }

    /**
     * 服务端 tick：检查换弹阶段转移和完成。
     */
    public fun tickReload() {
        if (!data.reloadStateType.isReloading()) return
        val supplier = data.currentGunItem ?: return
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return

        val elapsed = System.currentTimeMillis() - data.reloadTimestamp

        when (data.reloadStateType) {
            ReloadState.StateType.EMPTY_RELOAD_FEEDING -> {
                if (elapsed >= (gunData.emptyReloadFeedingTimeS * 1000).toLong()) {
                    data.reloadStateType = ReloadState.StateType.EMPTY_RELOAD_FINISHING
                    fillAmmo(iGun, currentGunItem, gunData, isEmpty = true)
                }
            }
            ReloadState.StateType.EMPTY_RELOAD_FINISHING -> {
                if (elapsed >= ((gunData.emptyReloadFeedingTimeS + gunData.emptyReloadFinishingTimeS) * 1000).toLong()) {
                    completeReload(iGun, currentGunItem, gunData, isEmpty = true)
                }
            }
            ReloadState.StateType.TACTICAL_RELOAD_FEEDING -> {
                if (elapsed >= (gunData.reloadFeedingTimeS * 1000).toLong()) {
                    data.reloadStateType = ReloadState.StateType.TACTICAL_RELOAD_FINISHING
                    fillAmmo(iGun, currentGunItem, gunData, isEmpty = false)
                }
            }
            ReloadState.StateType.TACTICAL_RELOAD_FINISHING -> {
                if (elapsed >= ((gunData.reloadFeedingTimeS + gunData.reloadFinishingTimeS) * 1000).toLong()) {
                    completeReload(iGun, currentGunItem, gunData, isEmpty = false)
                }
            }
            else -> {}
        }
    }

    /**
     * 取消换弹。
     */
    public fun cancelReload(): Boolean {
        if (!data.reloadStateType.isReloading()) return false
        val wasFeedingOnly = data.reloadStateType == ReloadState.StateType.EMPTY_RELOAD_FEEDING
            || data.reloadStateType == ReloadState.StateType.TACTICAL_RELOAD_FEEDING
        data.reloadStateType = ReloadState.StateType.NOT_RELOADING
        data.reloadTimestamp = -1L
        return wasFeedingOnly // 如果还在 feeding 阶段取消，弹药未注入
    }

    public fun getReloadState(): ReloadState {
        val countDown = if (data.reloadTimestamp > 0) {
            data.reloadTimestamp + getExpectedReloadLength() - System.currentTimeMillis()
        } else {
            ReloadState.NOT_RELOADING_COUNTDOWN
        }
        return ReloadState(data.reloadStateType, countDown.coerceAtLeast(ReloadState.NOT_RELOADING_COUNTDOWN))
    }

    private fun getExpectedReloadLength(): Long {
        val supplier = data.currentGunItem ?: return 0L
        val currentGunItem = supplier.get()
        val iGun = currentGunItem.item as? IGun ?: return 0L
        val gunId = iGun.getGunId(currentGunItem)
        val gunData = GunDataAccessor.getGunData(gunId) ?: return 0L
        return when (data.reloadStateType) {
            ReloadState.StateType.EMPTY_RELOAD_FEEDING,
            ReloadState.StateType.EMPTY_RELOAD_FINISHING ->
                ((gunData.emptyReloadFeedingTimeS + gunData.emptyReloadFinishingTimeS) * 1000).toLong()
            ReloadState.StateType.TACTICAL_RELOAD_FEEDING,
            ReloadState.StateType.TACTICAL_RELOAD_FINISHING ->
                ((gunData.reloadFeedingTimeS + gunData.reloadFinishingTimeS) * 1000).toLong()
            else -> 0L
        }
    }

    private fun fillAmmo(
        iGun: IGun,
        gunItem: net.minecraft.item.ItemStack,
        gunData: com.tacz.legacy.common.resource.GunCombatData,
        isEmpty: Boolean,
    ) {
        val maxAmmo = gunData.ammoAmount
        iGun.setCurrentAmmoCount(gunItem, maxAmmo)
    }

    private fun completeReload(
        iGun: IGun,
        gunItem: net.minecraft.item.ItemStack,
        gunData: com.tacz.legacy.common.resource.GunCombatData,
        isEmpty: Boolean,
    ) {
        // 战术换弹时膛内弹不变；空仓换弹后需要上膛（开膛不需要）
        if (isEmpty && gunData.boltType == BoltType.CLOSED_BOLT) {
            iGun.reduceCurrentAmmoCount(gunItem)
            iGun.setBulletInBarrel(gunItem, true)
        }
        data.reloadStateType = ReloadState.StateType.NOT_RELOADING
        data.reloadTimestamp = -1L
    }

    @Suppress("UNUSED_PARAMETER")
    private fun scheduleFeedingToFinishing(
        feedTimeMs: Long,
        finishTimeMs: Long,
        isEmpty: Boolean,
        gunData: com.tacz.legacy.common.resource.GunCombatData,
    ) {
        // tick-based 检查在 tickReload() 中完成
    }
}
