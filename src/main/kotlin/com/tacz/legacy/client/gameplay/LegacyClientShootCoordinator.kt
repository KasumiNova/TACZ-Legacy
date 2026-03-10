package com.tacz.legacy.client.gameplay

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.event.GunFireEvent
import com.tacz.legacy.api.event.GunShootEvent
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.event.FirstPersonRenderGunEvent
import com.tacz.legacy.client.event.TACZCameraRecoilHandler
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.sound.TACZClientGunSoundCoordinator
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerShoot
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.resource.GunCombatData
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.TACZGunSoundRouting
import com.tacz.legacy.sound.SoundManager
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemStack
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.relauncher.Side
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 客户端射击请求协调器。
 *
 * 对齐上游 LocalPlayerShoot：客户端只负责本地门禁、冷却预测、动画与发包，
 * 不在本地真实执行 operator.shoot() 来修改弹药/膛内状态。
 *
 * 点射模式 (BURST) 使用定时调度器在客户端播放连续动画/音效，
 * 与上游 doShoot + scheduleAtFixedRate 行为一致。
 */
internal object LegacyClientShootCoordinator {
    private var clientShootTimestampMs: Long = -1L
    private var clientLastShootTimestampMs: Long = -1L

    private val scheduledExecutor = Executors.newScheduledThreadPool(1) { runnable ->
        Thread(runnable, "TACZ-ClientShootScheduler").also { it.isDaemon = true }
    }
    private var activeBurstFuture: ScheduledFuture<*>? = null

    internal fun resetTiming() {
        clientShootTimestampMs = -1L
        clientLastShootTimestampMs = -1L
        cancelActiveBurst()
    }

    private fun cancelActiveBurst() {
        activeBurstFuture?.cancel(false)
        activeBurstFuture = null
    }

    /**
     * 脚本通过 adjustClientShootInterval 调整射击冷却时间戳。
     * 正值表示推迟下次射击 (增加冷却)，负值表示提前 (减少冷却)。
     */
    @JvmStatic
    public fun adjustClientShootTimestamp(alpha: Long) {
        if (clientShootTimestampMs > 0L) {
            clientShootTimestampMs += alpha
        }
    }

    /**
     * 返回客户端上一次射击的绝对系统时间戳（毫秒），用于状态机脚本。
     * 对应上游 LocalPlayerDataHolder.clientLastShootTimestamp。
     */
    @JvmStatic
    public fun getClientLastShootTimestampMs(): Long = clientLastShootTimestampMs

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
        val display = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)

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
        if (noAmmo) {
            TACZClientGunSoundCoordinator.playDryFireSound(player, display)
            return ShootResult.NO_AMMO
        }
        if (gunData.hasHeatData && iGun.isOverheatLocked(stack)) {
            TACZClientGunSoundCoordinator.playDryFireSound(player, display)
            return ShootResult.OVERHEATED
        }
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
        val fireEvent = GunFireEvent(player, stack, Side.CLIENT)
        val fireAccepted = !MinecraftForge.EVENT_BUS.post(fireEvent)
        if (fireAccepted) {
            if (triggerAnimation) {
                LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_SHOOT)
            }
            TACZClientGunSoundCoordinator.stopPlayGunSound(display, SoundManager.INSPECT_SOUND)
            if (TACZGunSoundRouting.resolveNearbyFireSoundProfile(stack).useSilenceSound) {
                TACZClientGunSoundCoordinator.playSilenceSound(player, display, gunData)
            } else {
                TACZClientGunSoundCoordinator.playShootSound(player, display, gunData)
            }
            // Record shoot timestamp for procedural recoil/sway animation
            FirstPersonRenderGunEvent.onShoot()
            TACZCameraRecoilHandler.onLocalGunFire(player, stack)
        }

        // 点射模式：调度后续动画/音效
        val fireMode = iGun.getFireMode(stack)
        if (fireMode == FireMode.BURST && gunData.burstCount > 1) {
            scheduleBurstFollowUp(player, stack, iGun, gunData, display)
        }

        return ShootResult.SUCCESS
    }

    /**
     * 点射模式客户端后续动画/声音调度。
     * 与上游 doShoot 使用 scheduleAtFixedRate 播放 count>0 的轮次一致。
     * 服务端通过 BurstFireTaskScheduler 执行实际子弹生成，
     * 这里只补播客户端动画与声音。
     */
    private fun scheduleBurstFollowUp(
        player: EntityPlayerSP,
        stack: ItemStack,
        iGun: IGun,
        gunData: GunCombatData,
        display: GunDisplayInstance?,
    ) {
        cancelActiveBurst()
        val period = gunData.getBurstShootIntervalMs().coerceAtLeast(1L)
        val maxCount = gunData.burstCount.coerceAtLeast(1)
        val counter = AtomicInteger(1) // first shot already played
        val useSilence = TACZGunSoundRouting.resolveNearbyFireSoundProfile(stack).useSilenceSound

        activeBurstFuture = scheduledExecutor.scheduleAtFixedRate({
            try {
                val count = counter.get()
                if (count >= maxCount || player.isDead) {
                    activeBurstFuture?.cancel(false)
                    return@scheduleAtFixedRate
                }
                if (gunData.hasHeatData && iGun.isOverheatLocked(stack)) {
                    activeBurstFuture?.cancel(false)
                    return@scheduleAtFixedRate
                }
                Minecraft.getMinecraft().addScheduledTask {
                    val fireEvent = GunFireEvent(player, stack, Side.CLIENT)
                    if (!MinecraftForge.EVENT_BUS.post(fireEvent)) {
                        LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_SHOOT)
                        TACZClientGunSoundCoordinator.stopPlayGunSound(display, SoundManager.INSPECT_SOUND)
                        if (useSilence) {
                            TACZClientGunSoundCoordinator.playSilenceSound(player, display, gunData)
                        } else {
                            TACZClientGunSoundCoordinator.playShootSound(player, display, gunData)
                        }
                        FirstPersonRenderGunEvent.onShoot()
                        TACZCameraRecoilHandler.onLocalGunFire(player, stack)
                    }
                }
                counter.incrementAndGet()
            } catch (_: Exception) {
                activeBurstFuture?.cancel(false)
            }
        }, period, period, TimeUnit.MILLISECONDS)
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