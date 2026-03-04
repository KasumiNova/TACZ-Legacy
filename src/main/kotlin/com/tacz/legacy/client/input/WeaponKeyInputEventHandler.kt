package com.tacz.legacy.client.input

import com.tacz.legacy.client.gui.WeaponDebugWorkbenchGuiScreen
import com.tacz.legacy.client.gui.WeaponGunsmithImmersiveRuntime
import com.tacz.legacy.client.gui.WeaponImmersiveWorkbenchGuiScreen
import com.tacz.legacy.common.domain.weapon.WeaponInput
import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.util.EnumHand
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.client.event.MouseEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Mouse

public class WeaponKeyInputEventHandler {

    private var triggerHeld: Boolean = false
    private var lastSyncedAimingIntent: Boolean? = null

    @SubscribeEvent
    public fun onKeyInput(event: InputEvent.KeyInputEvent) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        if (mc.currentScreen != null) {
            return
        }

        while (WeaponKeyBindings.reloadKey.isPressed) {
            WeaponRuntimeMcBridge.dispatchClientInput(player, WeaponInput.ReloadPressed)
        }

        while (WeaponKeyBindings.inspectKey.isPressed) {
            WeaponRuntimeMcBridge.dispatchClientInput(player, WeaponInput.InspectPressed)
        }

        while (WeaponKeyBindings.attachmentWorkbenchKey.isPressed) {
            if (!isHoldingLegacyGun(player)) {
                continue
            }
            mc.displayGuiScreen(WeaponDebugWorkbenchGuiScreen())
        }

        while (WeaponKeyBindings.immersiveWorkbenchKey.isPressed) {
            if (!isHoldingLegacyGun(player)) {
                continue
            }

            val gunId = currentGunId(player)
            WeaponGunsmithImmersiveRuntime.activate(gunId)
            mc.displayGuiScreen(WeaponImmersiveWorkbenchGuiScreen())
            player.sendStatusMessage(TextComponentString("[TACZ] 已进入沉浸式改枪模式（实验阶段）"), true)
        }
    }

    @SubscribeEvent
    public fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val mc = Minecraft.getMinecraft()
        val player = mc.player
        if (player == null || mc.world == null) {
            releaseTriggerIfNeeded(player)
            lastSyncedAimingIntent = null
            WeaponGunsmithImmersiveRuntime.deactivate()
            return
        }

        val holdingLegacyGun = isHoldingLegacyGun(player)
        val heldGunId = currentGunId(player)
        WeaponGunsmithImmersiveRuntime.deactivateIfGunChanged(heldGunId)
        val immersiveActive = WeaponGunsmithImmersiveRuntime.isActiveForGun(heldGunId)
        val useDown = mc.gameSettings.keyBindUseItem.isKeyDown || Mouse.isButtonDown(1)
        val aimingIntent = resolveAimIntentTarget(
            hasScreen = mc.currentScreen != null,
            holdingLegacyGun = holdingLegacyGun && !immersiveActive,
            useDown = useDown,
            isSwinging = player.isSwingInProgress
        )
        WeaponAimInputStateRegistry.updateFromClientInput(
            sessionId = sessionId(player.uniqueID.toString()),
            isAiming = aimingIntent
        )
        syncAimingIntentIfNeeded(aimingIntent)

        if (mc.currentScreen != null || !holdingLegacyGun) {
            releaseTriggerIfNeeded(player)
            return
        }

        if (immersiveActive) {
            releaseTriggerIfNeeded(player)
            suppressVanillaSwingAnimation(player)
            return
        }

        suppressVanillaSwingAnimation(player)

        val attackDown = mc.gameSettings.keyBindAttack.isKeyDown || Mouse.isButtonDown(0)
        if (attackDown && !triggerHeld) {
            WeaponRuntimeMcBridge.dispatchClientInput(player, WeaponInput.TriggerPressed)
            triggerHeld = true
            return
        }

        if (!attackDown) {
            releaseTriggerIfNeeded(player)
        }
    }

    @SubscribeEvent
    public fun onMouseInput(event: MouseEvent) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val shouldCancel = shouldCancelVanillaPrimaryMouseInput(
            button = event.button,
            buttonState = event.isButtonstate,
            hasScreen = mc.currentScreen != null,
            holdingLegacyGun = isHoldingLegacyGun(player)
        )
        if (!shouldCancel) {
            return
        }

        // 阻断原版 clickMouse() 链路，避免触发攻击冷却导致的“拿起/放下”视图动画。
        event.isCanceled = true
        suppressVanillaSwingAnimation(player)
    }

    internal fun shouldCancelVanillaPrimaryMouseInput(
        button: Int,
        buttonState: Boolean,
        hasScreen: Boolean,
        holdingLegacyGun: Boolean
    ): Boolean {
        if (button != LEFT_MOUSE_BUTTON || !buttonState) {
            return false
        }
        if (hasScreen) {
            return false
        }
        return holdingLegacyGun
    }

    internal fun resolveAimIntentTarget(
        hasScreen: Boolean,
        holdingLegacyGun: Boolean,
        useDown: Boolean,
        isSwinging: Boolean
    ): Boolean {
        if (hasScreen || !holdingLegacyGun) {
            return false
        }
        if (!useDown) {
            return false
        }
        if (isSwinging) {
            return false
        }
        return true
    }

    private fun isHoldingLegacyGun(player: EntityPlayerSP): Boolean {
        val stack = player.heldItemMainhand
        if (stack.isEmpty) {
            return false
        }
        return stack.item is LegacyGunItem
    }

    private fun currentGunId(player: EntityPlayerSP): String? {
        val stack = player.heldItemMainhand
        if (stack.isEmpty || stack.item !is LegacyGunItem) {
            return null
        }

        return stack.item.registryName
            ?.toString()
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
    }

    private fun releaseTriggerIfNeeded(player: EntityPlayerSP?) {
        if (!triggerHeld) {
            return
        }

        if (player != null) {
            WeaponRuntimeMcBridge.dispatchClientInput(player, WeaponInput.TriggerReleased)
        }
        triggerHeld = false
    }

    private fun syncAimingIntentIfNeeded(aimingIntent: Boolean) {
        if (!shouldSyncAimingIntent(lastSyncedAimingIntent, aimingIntent)) {
            return
        }
        LegacyNetworkHandler.sendWeaponAimStateToServer(aimingIntent)
        lastSyncedAimingIntent = aimingIntent
    }

    internal fun shouldSyncAimingIntent(lastSynced: Boolean?, current: Boolean): Boolean {
        return lastSynced == null || lastSynced != current
    }

    private fun suppressVanillaSwingAnimation(player: EntityPlayerSP) {
        if (!player.isSwingInProgress && player.swingProgress == 0f && player.prevSwingProgress == 0f) {
            return
        }

        player.isSwingInProgress = false
        player.swingProgressInt = 0
        player.swingProgress = 0f
        player.prevSwingProgress = 0f
        player.swingingHand = EnumHand.MAIN_HAND
    }

    private companion object {
        private const val LEFT_MOUSE_BUTTON: Int = 0

        private fun sessionId(playerUuid: String): String {
            return WeaponRuntimeMcBridge.clientSessionIdForPlayer(playerUuid)
        }
    }

}
