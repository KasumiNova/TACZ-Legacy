package com.tacz.legacy.client.render.debug

import com.tacz.legacy.common.infrastructure.mc.network.WeaponSessionCorrectionReason
import com.tacz.legacy.common.infrastructure.mc.network.WeaponSessionSyncClientRegistry
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

public class WeaponConsistencyDriftSampler {

    @SubscribeEvent
    public fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) {
            return
        }

        val minecraft = Minecraft.getMinecraft()
        val player = minecraft.player ?: return
        if (minecraft.world == null) {
            return
        }

        val localSessionId = WeaponRuntimeMcBridge.clientSessionIdForPlayer(player.uniqueID.toString())
        val authoritativeSessionId = WeaponRuntimeMcBridge.serverSessionIdForPlayer(player.uniqueID.toString())
        val localSnapshot = WeaponRuntimeMcBridge.sessionServiceOrNull()
            ?.debugSnapshot(localSessionId)
            ?.snapshot
        val synced = WeaponSessionSyncClientRegistry.get(authoritativeSessionId)
        val receipt = WeaponSessionSyncClientRegistry.receipt(authoritativeSessionId)

        val correctionReason = receipt?.correctionReason
            ?: synced?.correctionReason
            ?: WeaponSessionCorrectionReason.PERIODIC
        val driftFields = if (localSnapshot != null && synced != null) {
            WeaponConsistencyDiagnostics.countSnapshotDriftFields(localSnapshot, synced.snapshot)
        } else {
            0
        }

        WeaponConsistencyDiagnostics.recordSessionDrift(
            sessionId = localSessionId,
            driftFields = driftFields,
            correctionReason = correctionReason
        )
    }
}
