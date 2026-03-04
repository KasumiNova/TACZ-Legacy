package com.tacz.legacy.client.tooltip

import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipLine
import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class LegacyNativeRichTooltipRendererTest {

    /** Stub text width measurer: each character = 6 pixels (like MC's default). */
    private val stubTextWidth: (String) -> Int = { it.length * 6 }
    private val stubFontHeight: Int = 9

    private fun icon(ns: String = "tacz", path: String = "textures/gun/ak47.png", size: Int = 16) =
        LegacyRichTooltipSegment.Icon(ns, path, size)

    // ─── computeLineWidth ───────────────────────────────────

    @Test
    public fun `computeLineWidth should return 0 for empty line`() {
        val line = LegacyRichTooltipLine(emptyList())
        assertEquals(0, LegacyNativeRichTooltipRenderer.computeLineWidth(line, stubTextWidth))
    }

    @Test
    public fun `computeLineWidth should compute text-only width`() {
        val line = LegacyRichTooltipLine(listOf(
            LegacyRichTooltipSegment.Text("Hello")
        ))
        assertEquals(30, LegacyNativeRichTooltipRenderer.computeLineWidth(line, stubTextWidth))
    }

    @Test
    public fun `computeLineWidth should compute icon-only width without trailing gap`() {
        val line = LegacyRichTooltipLine(listOf(icon(size = 16)))
        // Single icon at end: no ICON_GAP
        assertEquals(16, LegacyNativeRichTooltipRenderer.computeLineWidth(line, stubTextWidth))
    }

    @Test
    public fun `computeLineWidth should add icon gap between icon and next segment`() {
        val line = LegacyRichTooltipLine(listOf(
            icon(size = 16),
            LegacyRichTooltipSegment.Text("AK47")
        ))
        // icon (16) + ICON_GAP (1) + text (4*6=24) = 41
        assertEquals(41, LegacyNativeRichTooltipRenderer.computeLineWidth(line, stubTextWidth))
    }

    @Test
    public fun `computeLineWidth should compute mixed segments width`() {
        val line = LegacyRichTooltipLine(listOf(
            LegacyRichTooltipSegment.Text("Damage: "),
            icon(path = "textures/icon/dmg.png", size = 10),
            LegacyRichTooltipSegment.Text(" 7.5")
        ))
        // text "Damage: " (8*6=48) + icon (10) + ICON_GAP (1) + text " 7.5" (4*6=24) = 83
        assertEquals(83, LegacyNativeRichTooltipRenderer.computeLineWidth(line, stubTextWidth))
    }

    // ─── computeLineHeight ──────────────────────────────────

    @Test
    public fun `computeLineHeight should return font height for text-only line`() {
        val line = LegacyRichTooltipLine(listOf(
            LegacyRichTooltipSegment.Text("Hello")
        ))
        assertEquals(9, LegacyNativeRichTooltipRenderer.computeLineHeight(line, stubFontHeight))
    }

    @Test
    public fun `computeLineHeight should return icon size when larger than font height`() {
        val line = LegacyRichTooltipLine(listOf(icon(size = 16)))
        assertEquals(16, LegacyNativeRichTooltipRenderer.computeLineHeight(line, stubFontHeight))
    }

    @Test
    public fun `computeLineHeight should return font height when icon is smaller`() {
        val line = LegacyRichTooltipLine(listOf(icon(size = 6)))
        assertEquals(9, LegacyNativeRichTooltipRenderer.computeLineHeight(line, stubFontHeight))
    }

    @Test
    public fun `computeLineHeight should use max of multiple icons`() {
        val line = LegacyRichTooltipLine(listOf(
            icon(ns = "a", path = "t/a.png", size = 8),
            icon(ns = "b", path = "t/b.png", size = 20)
        ))
        assertEquals(20, LegacyNativeRichTooltipRenderer.computeLineHeight(line, stubFontHeight))
    }

    @Test
    public fun `computeLineHeight should return font height for empty line`() {
        val line = LegacyRichTooltipLine(emptyList())
        assertEquals(9, LegacyNativeRichTooltipRenderer.computeLineHeight(line, stubFontHeight))
    }

    // ─── buildLayout ────────────────────────────────────────

    @Test
    public fun `buildLayout should return empty layout for empty lines`() {
        val result = LegacyNativeRichTooltipRenderer.buildLayout(emptyList(), stubFontHeight, stubTextWidth)
        assertTrue(result.lineLayouts.isEmpty())
        assertEquals(0, result.maxLineWidth)
        assertEquals(0, result.totalHeight)
    }

    @Test
    public fun `buildLayout should compute single line layout`() {
        val line = LegacyRichTooltipLine(listOf(
            LegacyRichTooltipSegment.Text("Hello World")
        ))
        val result = LegacyNativeRichTooltipRenderer.buildLayout(listOf(line), stubFontHeight, stubTextWidth)
        assertEquals(1, result.lineLayouts.size)
        assertEquals(66, result.maxLineWidth) // 11 * 6 = 66
        assertEquals(9, result.totalHeight) // single line, fontHeight=9
    }

    @Test
    public fun `buildLayout should include line spacing between multiple lines`() {
        val line1 = LegacyRichTooltipLine(listOf(LegacyRichTooltipSegment.Text("A")))
        val line2 = LegacyRichTooltipLine(listOf(LegacyRichTooltipSegment.Text("B")))
        val result = LegacyNativeRichTooltipRenderer.buildLayout(listOf(line1, line2), stubFontHeight, stubTextWidth)
        assertEquals(2, result.lineLayouts.size)
        // totalHeight = 9 + 2 (LINE_SPACING) + 9 = 20
        assertEquals(20, result.totalHeight)
        assertEquals(6, result.maxLineWidth) // max of "A"(6) and "B"(6)
    }

    @Test
    public fun `buildLayout should pick max line width across lines`() {
        val short = LegacyRichTooltipLine(listOf(LegacyRichTooltipSegment.Text("Hi")))
        val long = LegacyRichTooltipLine(listOf(LegacyRichTooltipSegment.Text("Hello World!")))
        val result = LegacyNativeRichTooltipRenderer.buildLayout(listOf(short, long), stubFontHeight, stubTextWidth)
        assertEquals(72, result.maxLineWidth) // 12 * 6 = 72
    }

    // ─── normalizeLinesForRendering ─────────────────────────

    @Test
    public fun `normalizeLinesForRendering should keep all text segments`() {
        val line = LegacyRichTooltipLine(listOf(
            LegacyRichTooltipSegment.Text("Hello")
        ))
        val result = LegacyNativeRichTooltipRenderer.normalizeLinesForRendering(listOf(line)) { true }
        assertEquals(1, result.size)
        assertEquals(1, result[0].segments.size)
    }

    @Test
    public fun `normalizeLinesForRendering should remove unavailable icons`() {
        val line = LegacyRichTooltipLine(listOf(icon(size = 16)))
        val result = LegacyNativeRichTooltipRenderer.normalizeLinesForRendering(listOf(line)) { false }
        // Icon removed → line is empty → line removed
        assertTrue(result.isEmpty())
    }

    @Test
    public fun `normalizeLinesForRendering should keep available icons`() {
        val line = LegacyRichTooltipLine(listOf(icon(size = 16)))
        val result = LegacyNativeRichTooltipRenderer.normalizeLinesForRendering(listOf(line)) { true }
        assertEquals(1, result.size)
    }

    @Test
    public fun `normalizeLinesForRendering should keep line when text remains after icon removal`() {
        val line = LegacyRichTooltipLine(listOf(
            icon(size = 16),
            LegacyRichTooltipSegment.Text("Hello")
        ))
        val result = LegacyNativeRichTooltipRenderer.normalizeLinesForRendering(listOf(line)) { false }
        assertEquals(1, result.size)
        assertEquals(1, result[0].segments.size)
        assertTrue(result[0].segments[0] is LegacyRichTooltipSegment.Text)
    }

    @Test
    public fun `normalizeLinesForRendering should handle empty input`() {
        val result = LegacyNativeRichTooltipRenderer.normalizeLinesForRendering(emptyList()) { true }
        assertTrue(result.isEmpty())
    }

    @Test
    public fun `normalizeLinesForRendering should selectively filter icons`() {
        val available = icon(ns = "tacz", path = "textures/gun/ak47.png", size = 16)
        val unavailable = icon(ns = "tacz", path = "textures/gun/missing.png", size = 16)
        val line = LegacyRichTooltipLine(listOf(available, unavailable))

        val result = LegacyNativeRichTooltipRenderer.normalizeLinesForRendering(listOf(line)) { iconSegment ->
            iconSegment.resourcePath == "tacz:textures/gun/ak47.png"
        }
        assertEquals(1, result.size)
        assertEquals(1, result[0].segments.size)
        assertEquals("tacz:textures/gun/ak47.png", (result[0].segments[0] as LegacyRichTooltipSegment.Icon).resourcePath)
    }
}
