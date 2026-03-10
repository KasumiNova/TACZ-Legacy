package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.event.GunFireEvent
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.entity.EntityKineticBullet
import com.tacz.legacy.common.item.ModernKineticGunItem
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.event.ServerMessageGunFire
import com.tacz.legacy.common.resource.*
import com.tacz.legacy.sound.SoundManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.items.CapabilityItemHandler
import org.luaj.vm2.LuaFunction
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.util.function.BooleanSupplier
import java.util.function.Supplier

/**
 * 枪械数据脚本 API。与上游 TACZ ModernKineticGunScriptAPI 行为 1:1 对齐。
 * 脚本通过本 API 执行射击、弹药操作、换弹、拉栓、过热等全部逻辑。
 *
 * 所有公共方法都直接暴露给 Lua 脚本，不允许吞异常。
 */
@Suppress("unused") // Lua scripts call these methods via reflection
internal class TACZGunScriptAPI {
    lateinit var shooter: EntityLivingBase
    lateinit var dataHolder: ShooterDataHolder
    lateinit var itemStack: ItemStack
    var pitchSupplier: Supplier<Float>? = null
    var yawSupplier: Supplier<Float>? = null

    private var iGun: IGun? = null
    private var gunData: GunCombatData? = null
    private var scriptParamTable: LuaTable? = null

    fun initGunItem() {
        val item = itemStack.item
        if (item is IGun) {
            iGun = item
            val id = item.getGunId(itemStack)
            gunData = GunDataAccessor.getGunData(id)
            val params = gunData?.scriptParams
            if (params != null) {
                val t = LuaTable()
                for ((k, v) in params) {
                    t.set(k, CoerceJavaToLua.coerce(v))
                }
                scriptParamTable = t
            }
        } else {
            iGun = null
            gunData = null
            scriptParamTable = null
        }
    }

    // =====================================================================
    // 射击
    // =====================================================================

    fun shootOnce(consumeAmmo: Boolean) {
        val gun = iGun ?: return
        val data = gunData ?: return

        val fireMode = gun.getFireMode(itemStack)
        val cycles = if (fireMode == FireMode.BURST) data.burstCount.coerceAtLeast(1) else 1
        val period = if (fireMode == FireMode.BURST) data.getBurstShootIntervalMs() else 1L

        BurstFireTaskScheduler.addCycleTask(BooleanSupplier {
            performSingleFire(consumeAmmo)
        }, period, cycles)
    }

    private fun performSingleFire(consumeAmmo: Boolean): Boolean {
        if (shooter.isDead || shooter.heldItemMainhand !== itemStack || shooter.heldItemMainhand.isEmpty) {
            return false
        }
        val gun = iGun ?: return false
        val data = gunData ?: return false

        val logicalSide = if (shooter.world.isRemote) Side.CLIENT else Side.SERVER
        val fireEvent = GunFireEvent(shooter, itemStack, logicalSide)
        if (MinecraftForge.EVENT_BUS.post(fireEvent)) return true

        if (!shooter.world.isRemote) {
            TACZNetworkHandler.sendToTrackingEntity(ServerMessageGunFire(shooter.entityId, itemStack), shooter)
        }

        if (consumeAmmo) {
            if (!reduceAmmoOnce()) {
                return false
            }
        }

        // 过热处理：检查脚本是否定义了 handle_shoot_heat
        if (data.hasHeatData) {
            val script = resolveScript(data)
            val heatFunc = script?.let { checkFunction(it, "handle_shoot_heat") }
            if (heatFunc != null) {
                heatFunc.call(CoerceJavaToLua.coerce(this))
            } else {
                handleShootHeat()
            }
        }

        // 生成子弹
        if (!shooter.world.isRemote) {
            val bulletData = data.bulletData
            val gunId = gun.getGunId(itemStack)
            val snapshot = TACZGunPackRuntimeRegistry.getSnapshot()
            val gunDisplayId = TACZGunPackPresentation.resolveGunDisplayId(snapshot, gunId)
                ?: DefaultAssets.DEFAULT_GUN_DISPLAY_ID
            val nearbySoundProfile = TACZGunSoundRouting.resolveNearbyFireSoundProfile(itemStack)
            val ammoId = data.ammoId ?: DefaultAssets.EMPTY_AMMO_ID

            if (nearbySoundProfile.soundDistance > 0) {
                val soundKey = if (nearbySoundProfile.useSilenceSound) {
                    SoundManager.SILENCE_3P_SOUND
                } else {
                    SoundManager.SHOOT_3P_SOUND
                }
                SoundManager.sendSoundToNearby(
                    shooter, nearbySoundProfile.soundDistance, gunId, gunDisplayId, soundKey,
                    0.8f, 0.9f + shooter.world.rand.nextFloat() * 0.125f,
                )
            }

            val processedSpeed = bulletData.getProcessedSpeed()
            val bulletAmount = TACZBulletSpreadResolver.resolveBulletAmount(itemStack, gun, data)
            val pitchVal = pitchSupplier?.get() ?: shooter.rotationPitch
            val yawVal = yawSupplier?.get() ?: shooter.rotationYaw

            for (i in 0 until bulletAmount) {
                val isTracer = bulletData.hasTracerAmmo() && nextBulletIsTracer(bulletData.tracerCountInterval)
                val bullet = EntityKineticBullet(
                    shooter.world, shooter, bulletData, gunId, gunDisplayId, ammoId, isTracer,
                )
                bullet.applyShotgunDamageSpread(bulletAmount)
                TACZBulletSpreadResolver.applySpread(
                    shooter = shooter,
                    dataHolder = dataHolder,
                    gunItem = itemStack,
                    iGun = gun,
                    gunData = data,
                    bullet = bullet,
                    bulletIndex = i,
                    processedSpeed = processedSpeed,
                    pitch = pitchVal,
                    yaw = yawVal,
                    scriptApi = this,
                )
                shooter.world.spawnEntity(bullet)
            }
        }
        return true
    }

    // =====================================================================
    // 弹药操作
    // =====================================================================

    fun reduceAmmoOnce(): Boolean {
        val gun = iGun ?: return false
        val data = gunData ?: return false
        val boltType = data.boltType
        val hasAmmoInBarrel = gun.hasBulletInBarrel(itemStack) && boltType != BoltType.OPEN_BOLT
        val useInv = gun.useInventoryAmmo(itemStack)
        val hasInvAmmo = if (useInv) gun.hasInventoryAmmo(shooter, itemStack, isReloadingNeedConsumeAmmo()) else false
        val noAmmo = if (useInv) !hasInvAmmo else gun.getCurrentAmmoCount(itemStack) < 1

        return when (boltType) {
            BoltType.MANUAL_ACTION -> {
                if (!hasAmmoInBarrel) return false
                gun.setBulletInBarrel(itemStack, false)
                true
            }
            BoltType.CLOSED_BOLT -> {
                if (!noAmmo) {
                    if (useInv) return consumeAmmoFromPlayer(1) == 1
                    gun.reduceCurrentAmmoCount(itemStack)
                    return true
                }
                if (!hasAmmoInBarrel) return false
                gun.setBulletInBarrel(itemStack, false)
                true
            }
            BoltType.OPEN_BOLT -> {
                if (noAmmo) return false
                if (useInv) return consumeAmmoFromPlayer(1) == 1
                gun.reduceCurrentAmmoCount(itemStack)
                true
            }
        }
    }

    fun consumeAmmoFromPlayer(neededAmount: Int): Int {
        val gunItem = itemStack.item as? ModernKineticGunItem ?: return 0
        if (gunItem.useInventoryAmmo(itemStack) && !isReloadingNeedConsumeAmmo()) {
            return neededAmount
        }
        if (gunItem.useDummyAmmo(itemStack)) {
            val dummy = gunItem.getDummyAmmoAmount(itemStack)
            val consume = minOf(dummy, neededAmount)
            gunItem.setDummyAmmoAmount(itemStack, dummy - consume)
            return consume
        }
        val handler = shooter.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null) ?: return 0
        return gunItem.findAndExtractInventoryAmmo(handler, itemStack, neededAmount)
    }

    fun isShootingNeedConsumeAmmo(): Boolean {
        return com.tacz.legacy.api.entity.IGunOperator.fromLivingEntity(shooter)?.consumesAmmoOrNot() != false
    }

    fun isReloadingNeedConsumeAmmo(): Boolean {
        return com.tacz.legacy.api.entity.IGunOperator.fromLivingEntity(shooter)?.needCheckAmmo() != false
    }

    fun getNeededAmmoAmount(): Int {
        val gun = iGun ?: return 0
        val data = gunData ?: return 0
        return data.ammoAmount - gun.getCurrentAmmoCount(itemStack)
    }

    fun getAmmoAmount(): Int = iGun?.getCurrentAmmoCount(itemStack) ?: 0

    fun getMaxAmmoCount(): Int = gunData?.ammoAmount ?: 0

    fun getMagExtentLevel(): Int = 0

    fun hasAmmoToConsume(): Boolean {
        if (!isReloadingNeedConsumeAmmo()) return true
        val gun = iGun ?: return false
        if (gun.useDummyAmmo(itemStack)) return gun.getDummyAmmoAmount(itemStack) > 0
        return gun.hasInventoryAmmo(shooter, itemStack, true)
    }

    fun putAmmoInMagazine(amount: Int): Int {
        if (amount < 0) return 0
        val gun = iGun ?: return amount
        val data = gunData ?: return amount
        val maxAmmo = data.ammoAmount
        val currentAmmo = gun.getCurrentAmmoCount(itemStack)
        val newAmmo = currentAmmo + amount
        if (maxAmmo < newAmmo) {
            gun.setCurrentAmmoCount(itemStack, maxAmmo)
            return newAmmo - maxAmmo
        }
        gun.setCurrentAmmoCount(itemStack, newAmmo)
        return 0
    }

    fun removeAmmoFromMagazine(amount: Int): Int {
        if (amount < 0) return 0
        val gun = iGun ?: return 0
        val currentAmmo = gun.getCurrentAmmoCount(itemStack)
        if (currentAmmo < amount) {
            gun.setCurrentAmmoCount(itemStack, 0)
            return currentAmmo
        }
        gun.setCurrentAmmoCount(itemStack, currentAmmo - amount)
        return amount
    }

    fun getAmmoCountInMagazine(): Int = iGun?.getCurrentAmmoCount(itemStack) ?: 0

    fun hasAmmoInBarrel(): Boolean {
        val data = gunData ?: return false
        return data.boltType != BoltType.OPEN_BOLT && (iGun?.hasBulletInBarrel(itemStack) == true)
    }

    fun setAmmoInBarrel(ammoInBarrel: Boolean) {
        iGun?.setBulletInBarrel(itemStack, ammoInBarrel)
    }

    fun useInventoryAmmo(): Boolean = iGun?.useInventoryAmmo(itemStack) ?: false

    // =====================================================================
    // 时间与射击间隔
    // =====================================================================

    fun getShootInterval(): Long {
        val data = gunData ?: return 0L
        val fireMode = iGun?.getFireMode(itemStack) ?: return 0L
        val coolDown = if (fireMode == FireMode.BURST) {
            (data.burstMinInterval * 1000f).toLong()
        } else {
            data.getShootIntervalMs()
        }
        return (coolDown - 5).coerceAtLeast(0L)
    }

    fun getLastShootTimestamp(): Long {
        return dataHolder.lastShootTimestamp + dataHolder.baseTimestamp
    }

    fun getCurrentTimestamp(): Long = System.currentTimeMillis()

    fun adjustShootInterval(alpha: Long) {
        dataHolder.shootTimestamp += alpha
    }

    fun adjustReloadTime(alpha: Long) {
        dataHolder.reloadTimestamp -= alpha
    }

    fun adjustBoltTime(alpha: Long) {
        dataHolder.boltTimestamp -= alpha
    }

    fun getAimingProgress(): Float = dataHolder.aimingProgress

    fun getReloadStateType(): Int = dataHolder.reloadStateType.ordinal

    fun getFireMode(): Int = (iGun?.getFireMode(itemStack) ?: FireMode.UNKNOWN).ordinal

    // =====================================================================
    // 换弹时间
    // =====================================================================

    fun getReloadTime(): Long {
        if (dataHolder.reloadTimestamp == -1L) return 0L
        return System.currentTimeMillis() - dataHolder.reloadTimestamp
    }

    // =====================================================================
    // 拉栓时间
    // =====================================================================

    fun getBoltTime(): Long {
        if (!dataHolder.isBolting) return 0L
        return System.currentTimeMillis() - dataHolder.boltTimestamp
    }

    // =====================================================================
    // 脚本数据缓存
    // =====================================================================

    fun cacheScriptData(luaValue: LuaValue) {
        dataHolder.scriptData = luaValue
    }

    fun getCachedScriptData(): LuaValue {
        return dataHolder.scriptData as? LuaValue ?: LuaValue.NIL
    }

    fun getScriptParams(): LuaTable {
        return scriptParamTable ?: LuaTable()
    }

    // =====================================================================
    // 异步任务
    // =====================================================================

    fun safeAsyncTask(value: LuaValue, delayMs: Long, periodMs: Long, cycles: Int) {
        val func = value.checkfunction()
        BurstFireTaskScheduler.addCycleTask(
            BooleanSupplier { func.call().checkboolean() },
            delayMs, periodMs, cycles,
        )
    }

    // =====================================================================
    // 过热系统
    // =====================================================================

    fun handleShootHeat() {
        val data = gunData ?: return
        if (!data.hasHeatData) return
        val gun = iGun ?: return
        val newHeat = (gun.getHeatAmount(itemStack) + data.heatPerShot).coerceAtMost(data.heatMax)
        gun.setHeatAmount(itemStack, newHeat)
        if (newHeat >= data.heatMax) {
            gun.setOverheatLocked(itemStack, true)
        }
    }

    fun setHeatAmount(amount: Float) {
        iGun?.setHeatAmount(itemStack, amount)
    }

    fun getHeatAmount(): Float = iGun?.getHeatAmount(itemStack) ?: 0f

    fun hasHeatData(): Boolean = gunData?.hasHeatData == true

    fun getHeatMinRpm(): Float = if (gunData?.hasHeatData == true) gunData!!.heatMinRpmModifier else 0f
    fun getHeatMaxRpm(): Float = if (gunData?.hasHeatData == true) gunData!!.heatMaxRpmModifier else 0f
    fun getHeatMinInaccuracy(): Float = if (gunData?.hasHeatData == true) gunData!!.heatMinInaccuracy else 0f
    fun getHeatMaxInaccuracy(): Float = if (gunData?.hasHeatData == true) gunData!!.heatMaxInaccuracy else 0f

    fun getHeatMax(): Float = if (gunData?.hasHeatData == true) gunData!!.heatMax else 0f
    fun getHeatPerShot(): Float = if (gunData?.hasHeatData == true) gunData!!.heatPerShot else 0f

    fun isOverheatLocked(): Boolean = iGun?.isOverheatLocked(itemStack) == true

    fun setOverheatLocked(locked: Boolean) {
        iGun?.setOverheatLocked(itemStack, locked)
    }

    fun getOverheatTime(): Long = if (gunData?.hasHeatData == true) gunData!!.heatOverHeatTimeMs else 0L
    fun getCoolingDelay(): Long = if (gunData?.hasHeatData == true) gunData!!.heatCoolingDelayMs else 0L

    fun calcHeatReduction(heatTimestamp: Long): Float {
        val data = gunData ?: return 0f
        if (!data.hasHeatData) return 0f
        return ((System.currentTimeMillis() - heatTimestamp).toFloat() / 10000f) * data.heatCoolingMultiplier
    }

    // =====================================================================
    // Bolt
    // =====================================================================

    fun getBoltByInt(): Int {
        return when (gunData?.boltType) {
            BoltType.MANUAL_ACTION -> 1
            BoltType.CLOSED_BOLT -> 2
            BoltType.OPEN_BOLT -> 3
            else -> 0
        }
    }

    fun getBolt(): String = (gunData?.boltType ?: BoltType.OPEN_BOLT).name

    // =====================================================================
    // 配件
    // =====================================================================

    fun getAttachment(type: String): String {
        return DefaultAssets.EMPTY_AMMO_ID.toString()
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    private fun nextBulletIsTracer(tracerCountInterval: Int): Boolean {
        dataHolder.shootCount++
        if (tracerCountInterval == -1) return false
        return dataHolder.shootCount % (tracerCountInterval + 1) == 0
    }

    companion object {
        fun resolveScript(gunData: GunCombatData): LuaTable? {
            val scriptId = gunData.scriptId ?: return null
            return TACZDataScriptManager.getScript(scriptId)
        }

        fun checkFunction(table: LuaTable, name: String): LuaFunction? {
            val value = table.get(name)
            if (value.isfunction()) return value as LuaFunction
            if (value.isnil()) return null
            throw org.luaj.vm2.LuaError("bad argument: function or nil expected, got ${value.typename()}")
        }

        fun create(
            shooter: EntityLivingBase,
            dataHolder: ShooterDataHolder,
            gunItem: ItemStack,
            pitch: Supplier<Float>? = null,
            yaw: Supplier<Float>? = null,
        ): TACZGunScriptAPI {
            val api = TACZGunScriptAPI()
            api.shooter = shooter
            api.dataHolder = dataHolder
            api.itemStack = gunItem
            api.pitchSupplier = pitch
            api.yawSupplier = yaw
            api.initGunItem()
            return api
        }
    }
}
