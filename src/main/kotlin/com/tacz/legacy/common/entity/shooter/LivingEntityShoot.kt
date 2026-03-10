package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.event.GunFireEvent
import com.tacz.legacy.api.event.GunShootEvent
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.entity.EntityKineticBullet
import com.tacz.legacy.common.item.ModernKineticGunItem
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageGunFire
import com.tacz.legacy.common.network.message.event.ServerMessageGunShoot
import com.tacz.legacy.common.network.message.event.ServerMessageSyncBaseTimestamp
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.BulletCombatData
import com.tacz.legacy.common.resource.GunCombatData
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZGunSoundRouting
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.sound.SoundManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraftforge.common.MinecraftForge
import com.tacz.legacy.api.entity.IGunOperator
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.items.CapabilityItemHandler
import java.util.function.BooleanSupplier
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
        if (LegacyConfigManager.server.serverShootCooldownCheck) {
            val coolDown = getShootCoolDown(timestamp)
            if (coolDown == -1L) return ShootResult.UNKNOWN_FAIL
            if (coolDown > 0) return ShootResult.COOL_DOWN
        }

        // 网络时间戳校验
        if (LegacyConfigManager.server.serverShootNetworkCheck && shooter is EntityPlayerMP) {
            val now = System.currentTimeMillis()
            var alpha = now - data.baseTimestamp - timestamp
            val isFirstTimedShoot = data.shootTimestamp < 0L && data.lastShootTimestamp < 0L
            if (isFirstTimedShoot && (alpha < -300 || alpha > 600)) {
                data.baseTimestamp = now - timestamp
                alpha = now - data.baseTimestamp - timestamp
            }
            if (alpha < -300 || alpha > 600) {
                // 时间戳偏移过大——发起双向同步握手，而不是单方面重置
                TACZNetworkHandler.sendToPlayer(ServerMessageSyncBaseTimestamp(), shooter)
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
        val gunOperator = IGunOperator.fromLivingEntity(shooter)
        val needCheckAmmo = gunOperator.needCheckAmmo()
        val boltType = gunData.boltType
        val hasAmmoInBarrel = iGun.hasBulletInBarrel(currentGunItem) && boltType != BoltType.OPEN_BOLT
        val ammoCount = iGun.getCurrentAmmoCount(currentGunItem) + (if (hasAmmoInBarrel) 1 else 0)
        val useInventoryAmmo = iGun.useInventoryAmmo(currentGunItem)
        val hasInventoryAmmo = iGun.hasInventoryAmmo(shooter, currentGunItem, needCheckAmmo) || hasAmmoInBarrel

        val noAmmo = (useInventoryAmmo && !hasInventoryAmmo) || (!useInventoryAmmo && ammoCount < 1)
        if (noAmmo) return ShootResult.NO_AMMO

        // 过热检查
        if (gunData.hasHeatData && iGun.isOverheatLocked(currentGunItem)) return ShootResult.OVERHEATED

        // 膛内子弹检查（手动拉栓枪）
        if (boltType == BoltType.MANUAL_ACTION && !hasAmmoInBarrel) return ShootResult.NEED_BOLT

        // 闭膛上膛逻辑
        if (boltType == BoltType.CLOSED_BOLT && !hasAmmoInBarrel) {
            if (useInventoryAmmo) {
                consumeAmmoFromPlayer(currentGunItem, 1, needCheckAmmo)
            } else {
                iGun.reduceCurrentAmmoCount(currentGunItem)
            }
            iGun.setBulletInBarrel(currentGunItem, true)
        }

        val logicalSide = if (shooter.world.isRemote) Side.CLIENT else Side.SERVER
        val shootEvent = GunShootEvent(shooter, currentGunItem, logicalSide)
        if (MinecraftForge.EVENT_BUS.post(shootEvent)) return ShootResult.FORGE_EVENT_CANCEL

        if (!shooter.world.isRemote) {
            TACZNetworkHandler.sendToTrackingEntity(ServerMessageGunShoot(shooter.entityId, currentGunItem), shooter)
        }

        data.lastShootTimestamp = data.shootTimestamp
        data.heatTimestamp = System.currentTimeMillis()
        data.shootTimestamp = timestamp

        // 检查是否有数据脚本
        val script = TACZGunScriptAPI.resolveScript(gunData)
        val shootFunc = script?.let { TACZGunScriptAPI.checkFunction(it, "shoot") }
        if (shootFunc != null) {
            // 脚本接管射击逻辑
            val api = TACZGunScriptAPI.create(shooter, data, currentGunItem, pitch, yaw)
            shootFunc.call(org.luaj.vm2.lib.jse.CoerceJavaToLua.coerce(api))
        } else {
            // 默认射击路径
            executeShoot(currentGunItem, iGun, gunData, pitch, yaw)
            handleShootHeat(currentGunItem, iGun, gunData)
        }

        return ShootResult.SUCCESS
    }

    /**
     * 每次射击后累积热量。与上游 TACZ ModernKineticGunScriptAPI.handleShootHeat 行为一致。
     */
    private fun handleShootHeat(gunItem: ItemStack, iGun: IGun, gunData: GunCombatData) {
        if (!gunData.hasHeatData) return
        val current = iGun.getHeatAmount(gunItem)
        val newHeat = (current + gunData.heatPerShot).coerceAtMost(gunData.heatMax)
        iGun.setHeatAmount(gunItem, newHeat)
        if (newHeat >= gunData.heatMax) {
            iGun.setOverheatLocked(gunItem, true)
        }
    }

    private fun executeShoot(
        gunItem: ItemStack,
        iGun: IGun,
        gunData: GunCombatData,
        pitch: Supplier<Float>,
        yaw: Supplier<Float>,
    ) {
        val fireMode = iGun.getFireMode(gunItem)
        if (fireMode == FireMode.BURST) {
            BurstFireTaskScheduler.addCycleTask(
                BooleanSupplier { performSingleFire(gunItem, iGun, gunData, pitch, yaw) },
                gunData.getBurstShootIntervalMs(),
                gunData.burstCount.coerceAtLeast(1),
            )
            return
        }

        performSingleFire(gunItem, iGun, gunData, pitch, yaw)
    }

    private fun performSingleFire(
        gunItem: ItemStack,
        iGun: IGun,
        gunData: GunCombatData,
        pitch: Supplier<Float>,
        yaw: Supplier<Float>,
    ): Boolean {
        if (shooter.isDead || shooter.heldItemMainhand !== gunItem || shooter.heldItemMainhand.isEmpty) {
            return false
        }

        val logicalSide = if (shooter.world.isRemote) Side.CLIENT else Side.SERVER
        val fireEvent = GunFireEvent(shooter, gunItem, logicalSide)
        if (MinecraftForge.EVENT_BUS.post(fireEvent)) {
            return true
        }

        if (!shooter.world.isRemote) {
            TACZNetworkHandler.sendToTrackingEntity(ServerMessageGunFire(shooter.entityId, gunItem), shooter)
        }

        // 弹药消耗（bolt-type-aware）
        if (!reduceAmmoOnce(gunItem, iGun, gunData)) {
            return false
        }

        // 生成子弹实体
        if (!shooter.world.isRemote) {
            val bulletData = gunData.bulletData
            val gunId = iGun.getGunId(gunItem)
            val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
            val gunDisplayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId) ?: DefaultAssets.DEFAULT_GUN_DISPLAY_ID
            val nearbySoundProfile = TACZGunSoundRouting.resolveNearbyFireSoundProfile(gunItem)
            val ammoId = gunData.ammoId ?: DefaultAssets.EMPTY_AMMO_ID

            if (nearbySoundProfile.soundDistance > 0) {
                val soundKey = if (nearbySoundProfile.useSilenceSound) {
                    SoundManager.SILENCE_3P_SOUND
                } else {
                    SoundManager.SHOOT_3P_SOUND
                }
                SoundManager.sendSoundToNearby(
                    shooter,
                    nearbySoundProfile.soundDistance,
                    gunId,
                    gunDisplayId,
                    soundKey,
                    0.8f,
                    0.9f + shooter.world.rand.nextFloat() * 0.125f,
                )
            }

            val processedSpeed = bulletData.getProcessedSpeed()
            val bulletAmount = TACZBulletSpreadResolver.resolveBulletAmount(gunItem, iGun, gunData)
            val pitchVal = pitch.get()
            val yawVal = yaw.get()

            for (i in 0 until bulletAmount) {
                val isTracer = bulletData.hasTracerAmmo() && nextBulletIsTracer(bulletData.tracerCountInterval)
                val bullet = EntityKineticBullet(
                    shooter.world, shooter, bulletData,
                    gunId, gunDisplayId, ammoId, isTracer,
                )
                bullet.applyShotgunDamageSpread(bulletAmount)
                TACZBulletSpreadResolver.applySpread(
                    shooter = shooter,
                    dataHolder = data,
                    gunItem = gunItem,
                    iGun = iGun,
                    gunData = gunData,
                    bullet = bullet,
                    bulletIndex = i,
                    processedSpeed = processedSpeed,
                    pitch = pitchVal,
                    yaw = yawVal,
                )
                shooter.world.spawnEntity(bullet)
            }
        }

        return true
    }

    /**
     * 消耗一发弹药。根据 bolt 类型决定消耗膛内还是弹匣内弹药。
     * 与上游 TACZ ModernKineticGunScriptAPI.reduceAmmoOnce 行为一致。
     * @return 是否成功消耗弹药
     */
    private fun reduceAmmoOnce(gunItem: ItemStack, iGun: IGun, gunData: GunCombatData): Boolean {
        val needCheckAmmo = IGunOperator.fromLivingEntity(shooter).needCheckAmmo()
        val boltType = gunData.boltType
        val hasAmmoInBarrel = iGun.hasBulletInBarrel(gunItem) && boltType != BoltType.OPEN_BOLT
        val useInventoryAmmo = iGun.useInventoryAmmo(gunItem)
        val hasInventoryAmmo = if (useInventoryAmmo) iGun.hasInventoryAmmo(shooter, gunItem, needCheckAmmo) else false
        val noAmmo = if (useInventoryAmmo) !hasInventoryAmmo else iGun.getCurrentAmmoCount(gunItem) < 1

        when (boltType) {
            BoltType.MANUAL_ACTION -> {
                if (!hasAmmoInBarrel) return false
                iGun.setBulletInBarrel(gunItem, false)
                return true
            }
            BoltType.CLOSED_BOLT -> {
                if (!noAmmo) {
                    if (useInventoryAmmo) {
                        return consumeAmmoFromPlayer(gunItem, 1, needCheckAmmo) == 1
                    }
                    iGun.reduceCurrentAmmoCount(gunItem)
                    return true
                }
                if (!hasAmmoInBarrel) return false
                iGun.setBulletInBarrel(gunItem, false)
                return true
            }
            BoltType.OPEN_BOLT -> {
                if (noAmmo) return false
                if (useInventoryAmmo) {
                    return consumeAmmoFromPlayer(gunItem, 1, needCheckAmmo) == 1
                }
                iGun.reduceCurrentAmmoCount(gunItem)
                return true
            }
        }
    }

    /**
     * 从射击者背包消耗弹药。
     * @return 实际消耗的弹药数量
     */
    private fun consumeAmmoFromPlayer(gunItem: ItemStack, neededAmount: Int, needCheckAmmo: Boolean = true): Int {
        val gunItemObj = gunItem.item
        if (gunItemObj !is ModernKineticGunItem) return 0
        // 创造模式不消耗弹药（与上游 TACZ 一致）
        if (!needCheckAmmo) return neededAmount
        if (gunItemObj.useDummyAmmo(gunItem)) {
            val dummy = gunItemObj.getDummyAmmoAmount(gunItem)
            val consume = minOf(dummy, neededAmount)
            gunItemObj.setDummyAmmoAmount(gunItem, dummy - consume)
            return consume
        }
        val handler = shooter.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ?: return 0
        return gunItemObj.findAndExtractInventoryAmmo(handler, gunItem, neededAmount)
    }

    /**
     * 曳光弹判定。与上游 nextBulletIsTracer 行为一致。
     */
    private fun nextBulletIsTracer(tracerCountInterval: Int): Boolean {
        data.shootCount++
        if (tracerCountInterval == -1) return false
        return data.shootCount % (tracerCountInterval + 1) == 0
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
            gunData.getBurstMinIntervalMs()
        } else {
            gunData.getShootIntervalMs()
        }

        var coolDown = shootInterval - interval
        coolDown -= 5 // 5ms window
        return if (coolDown < 0) 0L else coolDown
    }
}
