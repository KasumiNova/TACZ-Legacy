package com.tacz.legacy.client.tooltip

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipParser
import net.minecraftforge.client.event.RenderTooltipEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.Side

@Mod.EventBusSubscriber(modid = TACZLegacy.MOD_ID, value = [Side.CLIENT])
public object LegacyNativeRichTooltipEventHandler {

    @JvmStatic
    @SubscribeEvent
    public fun onRenderTooltipPre(event: RenderTooltipEvent.Pre) {
        val stack = event.stack
        if (stack.isEmpty) {
            return
        }

        val registryName = stack.item.registryName ?: return
        if (!registryName.namespace.equals(TACZLegacy.MOD_ID, ignoreCase = true)) {
            return
        }

        val lines = event.lines
        if (!shouldInterceptTooltip(lines)) {
            return
        }

        val document = LegacyRichTooltipParser.parseLines(lines)
        if (!document.requiresCustomRender) {
            return
        }

        event.isCanceled = true
        LegacyNativeRichTooltipRenderer.render(
            document = document,
            mouseX = event.x,
            mouseY = event.y,
            screenWidth = event.screenWidth,
            screenHeight = event.screenHeight,
            eventFontRenderer = event.fontRenderer
        )
    }

    internal fun shouldInterceptTooltip(lines: List<String>?): Boolean {
        if (lines.isNullOrEmpty()) {
            return false
        }
        return lines.any { line -> line.contains(ICON_TOKEN_MARKER) }
    }

    private const val ICON_TOKEN_MARKER: String = "[icon:"
}
