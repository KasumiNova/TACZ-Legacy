package com.tacz.legacy.common.infrastructure.mc.workbench

import com.tacz.legacy.common.infrastructure.mc.network.LegacyNetworkHandler
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

public class WeaponWorkbenchSessionTickEventHandler {

    @SubscribeEvent
    public fun onPlayerTick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val player = event.player as? EntityPlayerMP ?: return
        if (player.world.isRemote) {
            return
        }
        if (player.ticksExisted % SESSION_CHECK_INTERVAL_TICKS != 0) {
            return
        }
        if (!WeaponWorkbenchSessionRegistry.hasActiveSession(player)) {
            return
        }

        val currentGunId = WeaponWorkbenchSessionRegistry.resolveHeldLegacyGunId(player).orEmpty()
        val result = WeaponWorkbenchSessionRegistry.validateIfSessionPresent(
            player = player,
            currentGunId = currentGunId
        )
        if (result.valid) {
            return
        }

        val reason = result.reasonMessage.orEmpty()
        if (result.hadSession) {
            LegacyNetworkHandler.sendWeaponWorkbenchCloseToClient(player, reason)
        }
        if (reason.isNotBlank()) {
            player.sendStatusMessage(net.minecraft.util.text.TextComponentString("[TACZ] $reason"), true)
        }
    }

    @SubscribeEvent
    public fun onPlayerLoggedOut(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.player as? EntityPlayerMP ?: return
        WeaponWorkbenchSessionRegistry.end(player)
    }

    private companion object {
        private const val SESSION_CHECK_INTERVAL_TICKS: Int = 5
    }
}
