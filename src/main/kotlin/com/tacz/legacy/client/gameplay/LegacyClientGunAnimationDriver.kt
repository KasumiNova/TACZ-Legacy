package com.tacz.legacy.client.gameplay

import com.tacz.legacy.api.client.animation.statemachine.AnimationStateMachine
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.client.animation.statemachine.GunAnimationConstant
import com.tacz.legacy.client.animation.statemachine.GunAnimationStateContext
import com.tacz.legacy.client.resource.GunDisplayInstance
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.common.item.LegacyRuntimeTooltipSupport
import com.tacz.legacy.common.resource.GunDataAccessor
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.item.ItemStack
import kotlin.math.abs

internal object LegacyClientGunAnimationDriver {
    private var exitingStateMachine: AnimationStateMachine<GunAnimationStateContext>? = null
    private var exitingStack: ItemStack? = null
    private var exitingDisplay: GunDisplayInstance? = null

    internal fun resolveDisplayInstance(stack: ItemStack): GunDisplayInstance? {
        val iGun = stack.item as? IGun ?: return null
        val displayId = LegacyRuntimeTooltipSupport.resolveGunDisplayId(stack, iGun) ?: return null
        return TACZClientAssetManager.getGunDisplayInstance(displayId)
    }

    internal fun determineLoopInput(
        isSprinting: Boolean,
        isSneaking: Boolean,
        moveForward: Float,
        moveStrafe: Float,
        movementInputMissing: Boolean = false,
    ): String {
        if (movementInputMissing) {
            return GunAnimationConstant.INPUT_IDLE
        }
        if (!isSneaking && isSprinting) {
            return GunAnimationConstant.INPUT_RUN
        }
        val isMoving = abs(moveForward) > 0.01f || abs(moveStrafe) > 0.01f
        return if (!isSneaking && isMoving) {
            GunAnimationConstant.INPUT_WALK
        } else {
            GunAnimationConstant.INPUT_IDLE
        }
    }

    internal fun resolveMeleeAnimationInput(
        hasMuzzleMelee: Boolean,
        hasStockMelee: Boolean,
        defaultAnimationType: String?,
    ): String? {
        if (hasMuzzleMelee) {
            return GunAnimationConstant.INPUT_BAYONET_MUZZLE
        }
        if (hasStockMelee) {
            return GunAnimationConstant.INPUT_BAYONET_STOCK
        }
        return when (defaultAnimationType?.lowercase()) {
            "melee_stock" -> GunAnimationConstant.INPUT_BAYONET_STOCK
            null -> null
            else -> GunAnimationConstant.INPUT_BAYONET_PUSH
        }
    }

    internal fun determineMeleeInput(stack: ItemStack): String? {
        val iGun = stack.item as? IGun ?: return null
        val muzzleMelee = GunDataAccessor.getAttachmentMeleeData(iGun.getAttachmentId(stack, AttachmentType.MUZZLE)) != null
        val stockMelee = GunDataAccessor.getAttachmentMeleeData(iGun.getAttachmentId(stack, AttachmentType.STOCK)) != null
        val defaultAnimationType = GunDataAccessor.getGunData(iGun.getGunId(stack))?.meleeData?.defaultMeleeData?.animationType
        return resolveMeleeAnimationInput(muzzleMelee, stockMelee, defaultAnimationType)
    }

    internal fun prepareContext(
        stateMachine: AnimationStateMachine<GunAnimationStateContext>,
        stack: ItemStack,
        display: GunDisplayInstance?,
        partialTicks: Float,
    ): GunAnimationStateContext {
        var context = stateMachine.context
        if (context == null) {
            context = GunAnimationStateContext()
            stateMachine.setContext(context)
        }
        context.setCurrentGunItem(stack)
        context.setDisplay(display)
        context.setPartialTicks(partialTicks)
        return context
    }

    internal fun trigger(
        stateMachine: AnimationStateMachine<GunAnimationStateContext>,
        input: String,
        stack: ItemStack,
        display: GunDisplayInstance?,
        partialTicks: Float = 0f,
    ): Boolean {
        if (!stateMachine.isInitialized) {
            return false
        }
        prepareContext(stateMachine, stack, display, partialTicks)
        stateMachine.trigger(input)
        return true
    }

    internal fun triggerIfInitialized(stack: ItemStack, input: String, partialTicks: Float = 0f): Boolean {
        val display = resolveDisplayInstance(stack) ?: return false
        val stateMachine = display.animationStateMachine ?: return false
        return trigger(stateMachine, input, stack, display, partialTicks)
    }

    internal fun beginPutAway(stack: ItemStack, partialTicks: Float = 0f): Boolean {
        val display = resolveDisplayInstance(stack) ?: return false
        val stateMachine = display.animationStateMachine ?: return false
        if (!stateMachine.isInitialized) {
            return false
        }
        val putAwayTimeMs = resolvePutAwayTimeMs(stack)
        val context = prepareContext(stateMachine, stack, display, partialTicks)
        context.putAwayTime = putAwayTimeMs / 1000f
        stateMachine.trigger(GunAnimationConstant.INPUT_PUT_AWAY)
        stateMachine.exit()
        stateMachine.setExitingTime(putAwayTimeMs + 50L)
        exitingStateMachine = stateMachine
        exitingStack = stack.copy()
        exitingDisplay = display
        return true
    }

    internal fun visualUpdateHeldGun(player: EntityPlayerSP, partialTicks: Float): Boolean {
        val stack = player.heldItemMainhand
        val display = resolveDisplayInstance(stack) ?: return false
        val stateMachine = display.animationStateMachine ?: return false
        if (!stateMachine.isInitialized && stateMachine.exitingTime < System.currentTimeMillis()) {
            prepareContext(stateMachine, stack, display, partialTicks)
            stateMachine.initialize()
            stateMachine.trigger(GunAnimationConstant.INPUT_DRAW)
        }
        if (!stateMachine.isInitialized) {
            return false
        }
        prepareContext(stateMachine, stack, display, partialTicks)
        stateMachine.visualUpdate()
        return true
    }

    internal fun visualUpdateExitingAnimation(partialTicks: Float): Boolean {
        val stateMachine = exitingStateMachine ?: return false
        if (stateMachine.exitingTime < System.currentTimeMillis()) {
            clearTransientState()
            return false
        }
        val stack = exitingStack ?: run {
            clearTransientState()
            return false
        }
        val context = prepareContext(stateMachine, stack, exitingDisplay, partialTicks)
        context.putAwayTime = resolvePutAwayTimeMs(stack) / 1000f
        stateMachine.visualUpdate()
        return true
    }

    internal fun clearTransientState(): Unit {
        exitingStateMachine = null
        exitingStack = null
        exitingDisplay = null
    }

    internal fun tickLoopAnimation(player: EntityPlayerSP): Boolean {
        val stack = player.heldItemMainhand
        val display = resolveDisplayInstance(stack) ?: return false
        val stateMachine = display.animationStateMachine ?: return false
        val movement = player.movementInput
        val input = determineLoopInput(
            isSprinting = player.isSprinting,
            isSneaking = player.isSneaking,
            moveForward = movement?.moveForward ?: 0f,
            moveStrafe = movement?.moveStrafe ?: 0f,
            movementInputMissing = movement == null,
        )
        return trigger(stateMachine, input, stack, display)
    }

    internal fun resolvePutAwayTimeMs(stack: ItemStack): Long {
        val iGun = stack.item as? IGun ?: return 0L
        val gunData = GunDataAccessor.getGunData(iGun.getGunId(stack)) ?: return 0L
        return (gunData.putAwayTimeS * 1000f).toLong().coerceAtLeast(0L)
    }
}
