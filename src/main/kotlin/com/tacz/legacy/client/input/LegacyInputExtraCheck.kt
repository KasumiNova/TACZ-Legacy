package com.tacz.legacy.client.input

import net.minecraft.client.Minecraft

internal object LegacyInputExtraCheck {
    internal fun isInGame(): Boolean {
        val mc = Minecraft.getMinecraft()
        return mc.player != null && mc.world != null && mc.currentScreen == null && mc.inGameHasFocus
    }
}
