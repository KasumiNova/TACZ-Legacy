package com.tacz.legacy.client.gameplay

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.client.other.KeepingItemRenderer
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.gui.GunRefitScreen
import com.tacz.legacy.client.input.LegacyInputExtraCheck
import com.tacz.legacy.client.input.LegacyKeyBindings
import com.tacz.legacy.client.sound.TACZClientGunSoundCoordinator
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.application.refit.LegacyGunRefitRuntime
import com.tacz.legacy.common.foundation.FocusedSmokeRuntime
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerAim
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerBolt
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerCancelReload
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerDraw
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerFireSelect
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerMelee
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerReload
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerShoot
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.resource.BoltType
import com.tacz.legacy.common.item.LegacyRuntimeTooltipSupport
import com.tacz.legacy.common.config.InteractKeyConfigRead
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.EntityList
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.text.TextComponentTranslation

internal object LegacyClientPlayerGunBridge {
    private var lastHeldGunSignature: String? = null
    private var lastHeldGunStack: ItemStack? = null
    private var lastShootSuccess: Boolean = false
    private var lastShootKeyDown: Boolean = false
    private var lastSprinting: Boolean = false

    internal fun onClientTick(): Unit {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: run {
            resetTransientState()
            return
        }
        val world = mc.world ?: run {
            resetTransientState()
            return
        }
        val operator = IGunOperator.fromLivingEntity(player)

        syncHeldGun(player, operator)
        processRefitInput(mc, player)

        val inGame = LegacyInputExtraCheck.isInGame() && !player.isSpectator
        if (!inGame) {
            lastShootSuccess = false
            lastShootKeyDown = false
            lastSprinting = false
            if (FocusedSmokeRuntime.isForcedAimActive && IGun.mainHandHoldGun(player)) {
                setAimState(operator, true)
            } else if (operator.getSynIsAiming()) {
                setAimState(operator, false)
            }
            operator.tick()
            return
        }

        processSprintReloadCancel(player, operator)
        processAimInput(player, operator)
        processReloadInput(player, operator)
        processFireSelectInput(player, operator)
        processInspectInput(player, operator)
        processMeleeInput(player)
        processInteractInput(mc, player)
        processShootInput(player, operator)
        processAutoReload(player, operator)
        tickClientAutoBolt(player, operator)
        operator.tick()
        LegacyClientGunAnimationDriver.tickLoopAnimation(player)
    }

    private fun syncHeldGun(player: EntityPlayerSP, operator: IGunOperator): Unit {
        if (!IGun.mainHandHoldGun(player)) {
            if (lastHeldGunSignature != null) {
                lastHeldGunStack?.let(::beginPutAwayAndKeep)
                operator.initialData()
                operator.getDataHolder().currentGunItem = null
                LegacyClientShootCoordinator.resetTiming()
                lastHeldGunSignature = null
                lastHeldGunStack = null
            }
            return
        }
        val mainHand = player.heldItemMainhand
        val iGun = mainHand.item as? IGun ?: return
        val signature = buildString {
            append(player.inventory.currentItem)
            append('|')
            append(iGun.getGunId(mainHand))
        }
        val holder = operator.getDataHolder()
        if (signature != lastHeldGunSignature || holder.currentGunItem == null) {
            lastHeldGunStack?.let(::beginPutAwayAndKeep)
            operator.draw { player.heldItemMainhand }
            TACZNetworkHandler.sendToServer(ClientMessagePlayerDraw())
            LegacyClientGunAnimationDriver.triggerIfInitialized(mainHand, GunAnimationConstant.INPUT_DRAW)
            val display = LegacyClientGunAnimationDriver.resolveDisplayInstance(mainHand)
            TACZClientGunSoundCoordinator.playDrawSound(player, display)
            LegacyClientShootCoordinator.resetTiming()
            lastHeldGunSignature = signature
        }
        lastHeldGunStack = mainHand.copy()
    }

    private fun processSprintReloadCancel(player: EntityPlayerSP, operator: IGunOperator): Unit {
        val sprinting = player.isSprinting
        if (sprinting && !lastSprinting) {
            cancelReloadIfNeeded(player.heldItemMainhand, operator)
        }
        lastSprinting = sprinting
    }

    private fun processAimInput(player: EntityPlayerSP, operator: IGunOperator): Unit {
        if (!IGun.mainHandHoldGun(player)) {
            if (operator.getSynIsAiming()) {
                setAimState(operator, false)
            }
            return
        }
        if (FocusedSmokeRuntime.isForcedAimActive) {
            setAimState(operator, true)
            return
        }
        if (LegacyConfigManager.client.holdToAim) {
            val shouldAim = LegacyKeyBindings.AIM.isKeyDown
            if (operator.getSynIsAiming() != shouldAim) {
                setAimState(operator, shouldAim)
            }
            return
        }
        while (LegacyKeyBindings.AIM.isPressed) {
            setAimState(operator, !operator.getSynIsAiming())
        }
    }

    private fun processReloadInput(player: EntityPlayerSP, operator: IGunOperator): Unit {
        while (LegacyKeyBindings.RELOAD.isPressed) {
            val stack = player.heldItemMainhand
            val iGun = stack.item as? IGun ?: continue
            if (iGun.useInventoryAmmo(stack)) {
                continue
            }
            val before = operator.getSynReloadState().stateType
            operator.reload()
            if (before != operator.getSynReloadState().stateType) {
                TACZNetworkHandler.sendToServer(ClientMessagePlayerReload())
                LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_RELOAD)
                val display = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)
                val noAmmo = iGun.getCurrentAmmoCount(stack) <= 0
                TACZClientGunSoundCoordinator.playReloadSound(player, display, noAmmo)
            }
        }
    }

    private fun processFireSelectInput(player: EntityPlayerSP, operator: IGunOperator): Unit {
        while (LegacyKeyBindings.FIRE_SELECT.isPressed) {
            val stack = player.heldItemMainhand
            val iGun = stack.item as? IGun ?: continue
            val before = iGun.getFireMode(stack)
            operator.fireSelect()
            if (before != iGun.getFireMode(stack)) {
                TACZNetworkHandler.sendToServer(ClientMessagePlayerFireSelect())
                LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_FIRE_SELECT)
            }
        }
    }

    private fun processInspectInput(player: EntityPlayerSP, operator: IGunOperator): Unit {
        while (LegacyKeyBindings.INSPECT.isPressed) {
            val stack = player.heldItemMainhand
            if (stack.item !is IGun) {
                continue
            }
            if (operator.getSynDrawCoolDown() != 0L) {
                continue
            }
            if (operator.getSynReloadState().stateType.isReloading()) {
                continue
            }
            if (operator.getSynIsBolting()) {
                continue
            }
            LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_INSPECT)
        }
    }

    private fun processRefitInput(mc: Minecraft, player: EntityPlayerSP) {
        while (LegacyKeyBindings.REFIT.isPressed) {
            when (mc.currentScreen) {
                is GunRefitScreen -> {
                    mc.displayGuiScreen(null)
                    continue
                }
                null -> Unit
                else -> continue
            }
            if (!LegacyInputExtraCheck.isInGame() || player.isSpectator) {
                continue
            }
            if (!IGun.mainHandHoldGun(player)) {
                continue
            }
            val gunStack = player.heldItemMainhand
            if (!LegacyGunRefitRuntime.canOpenRefit(gunStack)) {
                continue
            }
            mc.displayGuiScreen(GunRefitScreen())
        }
    }

    private fun processMeleeInput(player: EntityPlayerSP): Unit {
        while (LegacyKeyBindings.MELEE.isPressed) {
            val stack = player.heldItemMainhand
            val operator = IGunOperator.fromLivingEntity(player)
            if (operator.getSynDrawCoolDown() != 0L) {
                continue
            }
            if (operator.getSynReloadState().stateType.isReloading()) {
                continue
            }
            if (operator.getSynIsBolting()) {
                continue
            }
            if (operator.getSynMeleeCoolDown() > 0L) {
                continue
            }
            val meleeInput = LegacyClientGunAnimationDriver.determineMeleeInput(stack) ?: continue
            LegacyClientGunAnimationDriver.triggerIfInitialized(stack, meleeInput)
            TACZNetworkHandler.sendToServer(ClientMessagePlayerMelee())
        }
    }

    private fun processInteractInput(mc: Minecraft, player: EntityPlayerSP): Unit {
        while (LegacyKeyBindings.INTERACT.isPressed) {
            if (!IGun.mainHandHoldGun(player)) {
                continue
            }
            val hit = mc.objectMouseOver ?: continue
            when (hit.typeOfHit) {
                RayTraceResult.Type.BLOCK -> {
                    val pos = hit.blockPos ?: continue
                    val state = player.world.getBlockState(pos)
                    if (state.block.registryName?.let(InteractKeyConfigRead::canInteractBlock) != true) {
                        continue
                    }
                    val world = mc.world ?: continue
                    val result = mc.playerController.processRightClickBlock(player, world, pos, hit.sideHit, hit.hitVec, EnumHand.MAIN_HAND)
                    if (result == EnumActionResult.PASS) {
                        mc.playerController.processRightClick(player, world, EnumHand.MAIN_HAND)
                    }
                }
                RayTraceResult.Type.ENTITY -> {
                    val entity = hit.entityHit ?: continue
                    if (EntityList.getKey(entity)?.let(InteractKeyConfigRead::canInteractEntity) != true) {
                        continue
                    }
                    val result = mc.playerController.interactWithEntity(player, entity, hit, EnumHand.MAIN_HAND)
                    if (result == EnumActionResult.PASS) {
                        mc.playerController.processRightClick(player, player.world, EnumHand.MAIN_HAND)
                    }
                }
                else -> Unit
            }
        }
    }

    /**
     * 客户端自动拉拴 tick。与上游 LocalPlayerBolt.tickAutoBolt() 行为一致：
     * 每 tick 检查是否需要拉拴，自动触发拉拴操作，并在膛内弹药同步到位后结束拉拴状态。
     */
    private fun tickClientAutoBolt(player: EntityPlayerSP, operator: IGunOperator) {
        val data = operator.getDataHolder()
        val stack = player.heldItemMainhand
        val iGun = stack.item as? IGun ?: run {
            data.isBolting = false
            return
        }

        // 尝试触发拉拴（与上游 bolt() 条件对齐）
        if (!data.isBolting) {
            val gunId = iGun.getGunId(stack)
            val gunData = GunDataAccessor.getGunData(gunId) ?: return
            if (gunData.boltType != BoltType.MANUAL_ACTION) return
            val hasAmmoInBarrel = iGun.hasBulletInBarrel(stack)
            if (hasAmmoInBarrel) return
            val useInventoryAmmo = iGun.useInventoryAmmo(stack)
            val hasInventoryAmmo = iGun.hasInventoryAmmo(player, stack, operator.needCheckAmmo())
            val noAmmo = (useInventoryAmmo && !hasInventoryAmmo) ||
                (!useInventoryAmmo && iGun.getCurrentAmmoCount(stack) < 1)
            if (noAmmo) return
            if (operator.getSynReloadState().stateType.isReloading()) return
            if (operator.getSynDrawCoolDown() != 0L) return

            // 触发拉拴
            operator.bolt()
            if (data.isBolting) {
                TACZNetworkHandler.sendToServer(ClientMessagePlayerBolt())
                LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_BOLT)
                val display = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)
                TACZClientGunSoundCoordinator.playBoltSound(player, display)
            }
        }

        // 对于客户端来说，膛内弹药被填入的状态同步到客户端的瞬间，bolt 过程才算完全结束
        if (data.isBolting && iGun.hasBulletInBarrel(stack)) {
            data.isBolting = false
        }
    }

    private fun processShootInput(player: EntityPlayerSP, operator: IGunOperator): Unit {
        val stack = player.heldItemMainhand
        val iGun = stack.item as? IGun ?: run {
            lastShootSuccess = false
            lastShootKeyDown = false
            return
        }
        val shootDown = LegacyKeyBindings.SHOOT.isKeyDown
        val gunId = iGun.getGunId(stack)
        val fireMode = iGun.getFireMode(stack)
        val isBurstAuto = fireMode == FireMode.BURST && LegacyRuntimeTooltipSupport.isContinuousBurst(gunId)

        if (shootDown) {
            player.isSprinting = false
            cancelReloadIfNeeded(stack, operator)
            val shouldAttempt = fireMode == FireMode.AUTO || isBurstAuto || !lastShootSuccess
            if (shouldAttempt) {
                val result = attemptShoot(player, operator)
                lastShootSuccess = result == ShootResult.SUCCESS
                if (result == ShootResult.NEED_BOLT && !operator.getSynIsBolting()) {
                    val before = operator.getSynIsBolting()
                    operator.bolt()
                    if (!before && operator.getSynIsBolting()) {
                        TACZNetworkHandler.sendToServer(ClientMessagePlayerBolt())
                        LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_BOLT)
                        val display = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)
                        TACZClientGunSoundCoordinator.playBoltSound(player, display)
                    }
                } else if (result == ShootResult.UNKNOWN_FAIL && !lastShootKeyDown && fireMode == FireMode.UNKNOWN) {
                    player.sendMessage(TextComponentTranslation("message.tacz.fire_select.fail"))
                }
            }
        } else {
            lastShootSuccess = false
            TACZClientGunSoundCoordinator.resetDryFireSound()
        }
        lastShootKeyDown = shootDown
    }

    private fun beginPutAwayAndKeep(stack: ItemStack) {
        val putAwayTimeMs = LegacyClientGunAnimationDriver.resolvePutAwayTimeMs(stack)
        val started = LegacyClientGunAnimationDriver.beginPutAway(stack)
        if (started && putAwayTimeMs > 0L) {
            val display = LegacyClientGunAnimationDriver.resolveDisplayInstance(stack)
            TACZClientGunSoundCoordinator.playPutAwaySound(Minecraft.getMinecraft().player, display)
            KeepingItemRenderer.getRenderer()?.keep(stack.copy(), putAwayTimeMs)
            if (System.getProperty("tacz.focusedSmoke", "false").toBoolean()) {
                val gunId = (stack.item as? IGun)?.getGunId(stack)
                TACZLegacy.logger.info("[FocusedSmoke] KEEP_ITEM_RENDER armed gun={} durationMs={}", gunId ?: "unknown", putAwayTimeMs)
            }
        }
    }

    private fun processAutoReload(player: EntityPlayerSP, operator: IGunOperator): Unit {
        if (!LegacyConfigManager.client.autoReload || player.ticksExisted % 5 != 0) {
            return
        }
        val stack = player.heldItemMainhand
        val iGun = stack.item as? IGun ?: return
        if (iGun.useInventoryAmmo(stack)) {
            return
        }
        val gunData = GunDataAccessor.getGunData(iGun.getGunId(stack)) ?: return
        val ammoCount = LegacyRuntimeTooltipSupport.getCurrentAmmoWithBarrel(stack, iGun, gunData)
        if (ammoCount > 0) {
            return
        }
        val before = operator.getSynReloadState().stateType
        operator.reload()
        if (before != operator.getSynReloadState().stateType) {
            TACZNetworkHandler.sendToServer(ClientMessagePlayerReload())
            LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_RELOAD)
        }
    }

    private fun attemptShoot(player: EntityPlayerSP, operator: IGunOperator): ShootResult {
        return LegacyClientShootCoordinator.attemptShoot(player, operator)
    }

    private fun cancelReloadIfNeeded(stack: net.minecraft.item.ItemStack, operator: IGunOperator): Boolean {
        val before = operator.getSynReloadState().stateType
        if (!before.isReloading()) {
            return false
        }
        operator.cancelReload()
        if (before != operator.getSynReloadState().stateType) {
            TACZNetworkHandler.sendToServer(ClientMessagePlayerCancelReload())
            LegacyClientGunAnimationDriver.triggerIfInitialized(stack, GunAnimationConstant.INPUT_CANCEL_RELOAD)
            return true
        }
        return false
    }

    private fun setAimState(operator: IGunOperator, aiming: Boolean): Unit {
        if (operator.getSynIsAiming() == aiming) {
            return
        }
        operator.aim(aiming)
        TACZNetworkHandler.sendToServer(ClientMessagePlayerAim(aiming))
    }

    private fun resetTransientState(): Unit {
        lastHeldGunSignature = null
        lastHeldGunStack = null
        lastShootSuccess = false
        lastShootKeyDown = false
        lastSprinting = false
        LegacyClientShootCoordinator.resetTiming()
        LegacyClientGunAnimationDriver.clearTransientState()
    }
}
