package com.tacz.legacy.client.gameplay

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.entity.ShootResult
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.client.input.LegacyInputExtraCheck
import com.tacz.legacy.client.input.LegacyKeyBindings
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerAim
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerBolt
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerDraw
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerFireSelect
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerMelee
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerReload
import com.tacz.legacy.common.network.message.client.ClientMessagePlayerShoot
import com.tacz.legacy.common.resource.GunDataAccessor
import com.tacz.legacy.common.item.LegacyRuntimeTooltipSupport
import com.tacz.legacy.common.config.InteractKeyConfigRead
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.EntityList
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumHand
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.text.TextComponentTranslation

internal object LegacyClientPlayerGunBridge {
    private var lastHeldGunSignature: String? = null
    private var lastShootSuccess: Boolean = false
    private var lastShootKeyDown: Boolean = false

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
        val operator = player as? IGunOperator ?: return

        syncHeldGun(player, operator)

        val inGame = LegacyInputExtraCheck.isInGame() && !player.isSpectator
        if (!inGame) {
            lastShootSuccess = false
            lastShootKeyDown = false
            if (operator.getSynIsAiming()) {
                setAimState(operator, false)
            }
            operator.tick()
            return
        }

        processAimInput(player, operator)
        processReloadInput(player, operator)
        processFireSelectInput(player, operator)
        processMeleeInput(player)
        processInteractInput(mc, player)
        processShootInput(player, operator)
        processAutoReload(player, operator)
        operator.tick()
    }

    private fun syncHeldGun(player: EntityPlayerSP, operator: IGunOperator): Unit {
        if (!IGun.mainHandHoldGun(player)) {
            if (lastHeldGunSignature != null) {
                operator.initialData()
                operator.getDataHolder().currentGunItem = null
                lastHeldGunSignature = null
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
            operator.draw { player.heldItemMainhand }
            TACZNetworkHandler.sendToServer(ClientMessagePlayerDraw())
            lastHeldGunSignature = signature
        }
    }

    private fun processAimInput(player: EntityPlayerSP, operator: IGunOperator): Unit {
        if (!IGun.mainHandHoldGun(player)) {
            if (operator.getSynIsAiming()) {
                setAimState(operator, false)
            }
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
            }
        }
    }

    private fun processMeleeInput(player: EntityPlayerSP): Unit {
        while (LegacyKeyBindings.MELEE.isPressed) {
            val operator = player as? IGunOperator ?: continue
            if (!operator.getSynIsAiming()) {
                TACZNetworkHandler.sendToServer(ClientMessagePlayerMelee())
            }
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
            val shouldAttempt = fireMode == FireMode.AUTO || isBurstAuto || !lastShootSuccess
            if (shouldAttempt) {
                val result = attemptShoot(player, operator)
                lastShootSuccess = result == ShootResult.SUCCESS
                if (result == ShootResult.NEED_BOLT && !operator.getSynIsBolting()) {
                    val before = operator.getSynIsBolting()
                    operator.bolt()
                    if (!before && operator.getSynIsBolting()) {
                        TACZNetworkHandler.sendToServer(ClientMessagePlayerBolt())
                    }
                } else if (result == ShootResult.UNKNOWN_FAIL && !lastShootKeyDown && fireMode == FireMode.UNKNOWN) {
                    player.sendMessage(TextComponentTranslation("message.tacz.fire_select.fail"))
                }
            }
        } else {
            lastShootSuccess = false
        }
        lastShootKeyDown = shootDown
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
        }
    }

    private fun attemptShoot(player: EntityPlayerSP, operator: IGunOperator): ShootResult {
        val timestamp = System.currentTimeMillis() - operator.getDataHolder().baseTimestamp
        val result = operator.shoot({ player.rotationPitch }, { player.rotationYaw }, timestamp)
        if (result == ShootResult.SUCCESS) {
            TACZNetworkHandler.sendToServer(ClientMessagePlayerShoot(player.rotationPitch, player.rotationYaw, timestamp))
        }
        return result
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
        lastShootSuccess = false
        lastShootKeyDown = false
    }
}
