package com.tacz.legacy.client.gui.widget

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.renderer.GlStateManager

/**
 * 纯文本按钮：不绘制原版按钮背景，只保留 hover/disabled 的文字反馈。
 * 用于沉浸式覆盖层 UI，避免出现原版灰色按钮底。
 */
public class FlatTextButton(
    buttonId: Int,
    x: Int,
    y: Int,
    widthIn: Int,
    heightIn: Int,
    buttonText: String,
    private val align: Align = Align.CENTER,
    private val drawHoverUnderline: Boolean = true
) : GuiButton(buttonId, x, y, widthIn, heightIn, buttonText) {

    public enum class Align {
        LEFT,
        CENTER,
        RIGHT
    }

    override fun drawButton(mc: Minecraft, mouseX: Int, mouseY: Int, partialTicks: Float) {
        if (!visible) {
            return
        }

        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height

        val fr = mc.fontRenderer
        val textWidth = fr.getStringWidth(displayString)

        val textX = when (align) {
            Align.LEFT -> x + 4
            Align.CENTER -> x + (width - textWidth) / 2
            Align.RIGHT -> x + width - 4 - textWidth
        }
        val textY = y + (height - 8) / 2

        val color = when {
            !enabled -> 0x888888
            hovered -> 0xFFFFFF
            else -> 0xDADADA
        }

        GlStateManager.disableBlend()
        fr.drawStringWithShadow(displayString, textX.toFloat(), textY.toFloat(), color)

        if (hovered && enabled && drawHoverUnderline) {
            val underlineY = y + height - 2
            drawRect(textX, underlineY, textX + textWidth, underlineY + 1, 0xAAFFFFFF.toInt())
        }
    }
}
