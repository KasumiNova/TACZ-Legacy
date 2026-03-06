package com.tacz.legacy.client.event

import com.tacz.legacy.client.gameplay.LegacyClientPlayerGunBridge
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

internal object LegacyClientInputEventHandler {
    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent): Unit {
        if (event.phase != TickEvent.Phase.END) {
            return
        }
        LegacyClientPlayerGunBridge.onClientTick()
    }
}
