package com.tacz.legacy.client.tooltip

import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipDocument
import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipLine
import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipSegment
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.client.config.GuiUtils
import java.util.concurrent.ConcurrentHashMap

public object LegacyNativeRichTooltipRenderer {

    public fun render(
        document: LegacyRichTooltipDocument,
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int,
        eventFontRenderer: FontRenderer?
    ) {
        if (document.lines.isEmpty()) {
            return
        }

        val minecraft = Minecraft.getMinecraft() ?: return
        val fontRenderer = eventFontRenderer ?: minecraft.fontRenderer ?: return
        val preparedLines = normalizeLinesForRendering(document.lines, minecraft)
        if (preparedLines.isEmpty()) {
            return
        }

        val layout = buildLayout(preparedLines, fontRenderer)
        if (layout.lineLayouts.isEmpty()) {
            return
        }

        val tooltipWidth = layout.maxLineWidth
        val tooltipHeight = layout.totalHeight

        var tooltipX = mouseX + TOOLTIP_X_OFFSET
        var tooltipY = mouseY - TOOLTIP_Y_OFFSET

        if (tooltipX + tooltipWidth > screenWidth) {
            tooltipX -= TOOLTIP_FLIP_OFFSET + tooltipWidth
        }

        if (tooltipY + tooltipHeight + TOOLTIP_BOTTOM_MARGIN > screenHeight) {
            tooltipY = screenHeight - tooltipHeight - TOOLTIP_BOTTOM_MARGIN
        }
        if (tooltipY < TOOLTIP_TOP_MARGIN) {
            tooltipY = TOOLTIP_TOP_MARGIN
        }

        GlStateManager.disableRescaleNormal()
        RenderHelper.disableStandardItemLighting()
        GlStateManager.disableLighting()
        GlStateManager.disableDepth()

        drawTooltipBackground(
            x = tooltipX,
            y = tooltipY,
            width = tooltipWidth,
            height = tooltipHeight
        )

        var currentY = tooltipY
        layout.lineLayouts.forEachIndexed { index, lineLayout ->
            renderLine(
                minecraft = minecraft,
                fontRenderer = fontRenderer,
                line = lineLayout.line,
                x = tooltipX,
                y = currentY,
                lineHeight = lineLayout.height
            )

            currentY += lineLayout.height
            if (index != layout.lineLayouts.lastIndex) {
                currentY += LINE_SPACING
            }
        }

        GlStateManager.enableDepth()
        GlStateManager.enableLighting()
        RenderHelper.enableStandardItemLighting()
        GlStateManager.enableRescaleNormal()
    }

    private fun normalizeLinesForRendering(
        lines: List<LegacyRichTooltipLine>,
        minecraft: Minecraft
    ): List<LegacyRichTooltipLine> {
        return normalizeLinesForRendering(lines) { icon -> isIconResourceAvailable(minecraft, icon) }
    }

    internal fun normalizeLinesForRendering(
        lines: List<LegacyRichTooltipLine>,
        iconAvailabilityCheck: (LegacyRichTooltipSegment.Icon) -> Boolean
    ): List<LegacyRichTooltipLine> {
        return lines.mapNotNull { line ->
            val segments = line.segments.mapNotNull { segment ->
                when (segment) {
                    is LegacyRichTooltipSegment.Text -> segment
                    is LegacyRichTooltipSegment.Icon -> if (iconAvailabilityCheck(segment)) segment else null
                }
            }

            val normalizedLine = LegacyRichTooltipLine(segments)
            normalizedLine.takeUnless { it.isEmpty }
        }
    }

    private fun isIconResourceAvailable(minecraft: Minecraft, icon: LegacyRichTooltipSegment.Icon): Boolean {
        val resourcePath = icon.resourcePath
        iconResourceAvailabilityCache[resourcePath]?.let { cached -> return cached }

        val resource = ResourceLocation(icon.namespace, icon.texturePath)
        val available = runCatching {
            minecraft.resourceManager.getResource(resource).use { }
            true
        }.getOrDefault(false)

        if (iconResourceAvailabilityCache.size > MAX_ICON_CACHE_SIZE) {
            iconResourceAvailabilityCache.clear()
        }
        iconResourceAvailabilityCache[resourcePath] = available
        return available
    }

    private fun buildLayout(lines: List<LegacyRichTooltipLine>, fontRenderer: FontRenderer): LayoutResult {
        return buildLayout(lines, fontRenderer.FONT_HEIGHT) { text -> fontRenderer.getStringWidth(text) }
    }

    internal fun buildLayout(
        lines: List<LegacyRichTooltipLine>,
        fontHeight: Int,
        textWidthMeasurer: (String) -> Int
    ): LayoutResult {
        val lineLayouts = lines.map { line ->
            val lineWidth = computeLineWidth(line, textWidthMeasurer)
            val lineHeight = computeLineHeight(line, fontHeight)
            LineLayout(
                line = line,
                width = lineWidth,
                height = lineHeight
            )
        }

        val maxLineWidth = lineLayouts.maxOfOrNull { it.width } ?: 0
        val totalHeight = lineLayouts.sumOf { it.height } + (lineLayouts.size - 1).coerceAtLeast(0) * LINE_SPACING

        return LayoutResult(
            lineLayouts = lineLayouts,
            maxLineWidth = maxLineWidth,
            totalHeight = totalHeight
        )
    }

    internal fun computeLineWidth(line: LegacyRichTooltipLine, textWidthMeasurer: (String) -> Int): Int {
        var width = 0
        line.segments.forEachIndexed { index, segment ->
            when (segment) {
                is LegacyRichTooltipSegment.Text -> {
                    width += textWidthMeasurer(segment.text)
                }

                is LegacyRichTooltipSegment.Icon -> {
                    width += segment.size
                    if (index != line.segments.lastIndex) {
                        width += ICON_GAP
                    }
                }
            }
        }
        return width
    }

    internal fun computeLineHeight(line: LegacyRichTooltipLine, fontHeight: Int): Int {
        val iconHeight = line.segments
            .filterIsInstance<LegacyRichTooltipSegment.Icon>()
            .maxOfOrNull { it.size }
            ?: 0
        return maxOf(fontHeight, iconHeight)
    }

    private fun renderLine(
        minecraft: Minecraft,
        fontRenderer: FontRenderer,
        line: LegacyRichTooltipLine,
        x: Int,
        y: Int,
        lineHeight: Int
    ) {
        var cursorX = x

        line.segments.forEachIndexed { index, segment ->
            when (segment) {
                is LegacyRichTooltipSegment.Text -> {
                    val textY = y + ((lineHeight - fontRenderer.FONT_HEIGHT) / 2)
                    fontRenderer.drawStringWithShadow(
                        segment.text,
                        cursorX.toFloat(),
                        textY.toFloat(),
                        DEFAULT_TEXT_COLOR
                    )
                    cursorX += fontRenderer.getStringWidth(segment.text)
                }

                is LegacyRichTooltipSegment.Icon -> {
                    val iconY = y + ((lineHeight - segment.size) / 2)
                    runCatching {
                        minecraft.textureManager.bindTexture(ResourceLocation(segment.namespace, segment.texturePath))
                        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
                        Gui.drawScaledCustomSizeModalRect(
                            cursorX,
                            iconY,
                            0.0f,
                            0.0f,
                            SOURCE_ICON_SIZE,
                            SOURCE_ICON_SIZE,
                            segment.size,
                            segment.size,
                            SOURCE_ICON_SIZE.toFloat(),
                            SOURCE_ICON_SIZE.toFloat()
                        )
                    }

                    cursorX += segment.size
                    if (index != line.segments.lastIndex) {
                        cursorX += ICON_GAP
                    }
                }
            }
        }
    }

    private fun drawTooltipBackground(x: Int, y: Int, width: Int, height: Int) {
        val left = x - HORIZONTAL_PADDING
        val top = y - TOP_PADDING
        val right = x + width + HORIZONTAL_PADDING
        val bottom = y + height + BOTTOM_PADDING

        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, left, top - 1, right, top, BG_COLOR, BG_COLOR)
        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, left, bottom, right, bottom + 1, BG_COLOR, BG_COLOR)
        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, left, top, right, bottom, BG_COLOR, BG_COLOR)
        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, left - 1, top, left, bottom, BG_COLOR, BG_COLOR)
        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, right, top, right + 1, bottom, BG_COLOR, BG_COLOR)

        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, left, top + 1, left + 1, bottom - 1, BORDER_COLOR_START, BORDER_COLOR_END)
        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, right - 1, top + 1, right, bottom - 1, BORDER_COLOR_START, BORDER_COLOR_END)
        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, left, top, right, top + 1, BORDER_COLOR_START, BORDER_COLOR_START)
        GuiUtils.drawGradientRect(TOOLTIP_Z_LEVEL, left, bottom - 1, right, bottom, BORDER_COLOR_END, BORDER_COLOR_END)
    }

    internal data class LayoutResult(
        val lineLayouts: List<LineLayout>,
        val maxLineWidth: Int,
        val totalHeight: Int
    )

    internal data class LineLayout(
        val line: LegacyRichTooltipLine,
        val width: Int,
        val height: Int
    )

    private const val TOOLTIP_Z_LEVEL: Int = 300
    private const val DEFAULT_TEXT_COLOR: Int = 0xFFFFFF
    private const val MAX_ICON_CACHE_SIZE: Int = 4096

    private const val HORIZONTAL_PADDING: Int = 3
    private const val TOP_PADDING: Int = 4
    private const val BOTTOM_PADDING: Int = 4
    private const val LINE_SPACING: Int = 2
    private const val ICON_GAP: Int = 1

    private const val TOOLTIP_X_OFFSET: Int = 12
    private const val TOOLTIP_Y_OFFSET: Int = 12
    private const val TOOLTIP_FLIP_OFFSET: Int = 28
    private const val TOOLTIP_TOP_MARGIN: Int = 6
    private const val TOOLTIP_BOTTOM_MARGIN: Int = 6

    private const val SOURCE_ICON_SIZE: Int = 16

    private const val BG_COLOR: Int = -267386864
    private const val BORDER_COLOR_START: Int = 1347420415
    private const val BORDER_COLOR_END: Int = (BORDER_COLOR_START and 16711422) shr 1 or BORDER_COLOR_START and -16777216

    private val iconResourceAvailabilityCache: MutableMap<String, Boolean> = ConcurrentHashMap()
}
