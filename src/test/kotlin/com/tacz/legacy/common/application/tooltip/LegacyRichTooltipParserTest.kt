package com.tacz.legacy.common.application.tooltip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

public class LegacyRichTooltipParserTest {

    @Test
    public fun `parse should keep plain text lines as text segments`() {
        val document = LegacyRichTooltipParser.parseLines(
            listOf(
                "§eAK47",
                "§7基础伤害：7.5"
            )
        )

        assertFalse(document.requiresCustomRender)
        assertEquals(2, document.lines.size)
        assertEquals(1, document.lines[0].segments.size)
        val firstSegment = document.lines[0].segments[0] as LegacyRichTooltipSegment.Text
        assertEquals("§eAK47", firstSegment.text)
    }

    @Test
    public fun `parse should recognize icon token and normalize texture path`() {
        val document = LegacyRichTooltipParser.parseLines(
            listOf("[icon:tacz:gun/slot/ak47,14] §7可用")
        )

        assertTrue(document.requiresCustomRender)
        assertEquals(1, document.lines.size)

        val segments = document.lines[0].segments
        assertEquals(2, segments.size)

        val icon = segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals("tacz", icon.namespace)
        assertEquals("textures/gun/slot/ak47.png", icon.texturePath)
        assertEquals(14, icon.size)

        val text = segments[1] as LegacyRichTooltipSegment.Text
        assertEquals(" §7可用", text.text)
    }

    @Test
    public fun `parse should clamp icon size and keep invalid token as text`() {
        val document = LegacyRichTooltipParser.parseLines(
            listOf(
                "[icon:tacz:textures/gui/test.png,99]",
                "[icon:broken_token]"
            )
        )

        assertTrue(document.requiresCustomRender)

        val clampedIcon = document.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals(32, clampedIcon.size)
        assertEquals("textures/gui/test.png", clampedIcon.texturePath)

        val invalidAsText = document.lines[1].segments[0] as LegacyRichTooltipSegment.Text
        assertEquals("[icon:broken_token]", invalidAsText.text)
    }

    // ─── §1 补充覆盖 ─────────────────────────────────────────

    @Test
    public fun `parseLines with empty list should return EMPTY document`() {
        val doc = LegacyRichTooltipParser.parseLines(emptyList())
        assertEquals(0, doc.lines.size)
        assertFalse(doc.requiresCustomRender)
    }

    @Test
    public fun `parseLine with empty string should yield single blank Text segment`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf(""))
        assertEquals(1, doc.lines.size)
        val seg = doc.lines[0].segments
        assertEquals(1, seg.size)
        assertTrue(seg[0] is LegacyRichTooltipSegment.Text)
        assertEquals("", (seg[0] as LegacyRichTooltipSegment.Text).text)
    }

    @Test
    public fun `icon without size should default to 10`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals(10, icon.size)
    }

    @Test
    public fun `icon size below minimum should clamp to 6`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47,1]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals(6, icon.size)
    }

    @Test
    public fun `icon path with backslashes should normalize to forward slashes`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:textures\\gun\\ak47]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals("textures/gun/ak47.png", icon.texturePath)
    }

    @Test
    public fun `icon path already ending in png should not append again`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:textures/gun/ak47.png]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals("textures/gun/ak47.png", icon.texturePath)
    }

    @Test
    public fun `icon path with leading trailing slashes should be trimmed`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:/gun/ak47/]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals("textures/gun/ak47.png", icon.texturePath)
    }

    @Test
    public fun `line with text before and after icon should produce three segments`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("前缀[icon:tacz:gun/ak47,10]后缀"))
        val segs = doc.lines[0].segments
        assertEquals(3, segs.size)
        assertEquals("前缀", (segs[0] as LegacyRichTooltipSegment.Text).text)
        assertTrue(segs[1] is LegacyRichTooltipSegment.Icon)
        assertEquals("后缀", (segs[2] as LegacyRichTooltipSegment.Text).text)
    }

    @Test
    public fun `line with multiple icons should produce five segments`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("前[icon:a:b,10]中[icon:c:d]后"))
        val segs = doc.lines[0].segments
        assertEquals(5, segs.size)
        assertTrue(segs[1] is LegacyRichTooltipSegment.Icon)
        assertTrue(segs[3] is LegacyRichTooltipSegment.Icon)
    }

    @Test
    public fun `namespace should be lowercased`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:TACZ:Gun/AK47]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals("tacz", icon.namespace)
    }

    @Test
    public fun `path should be lowercased`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:Gun/AK47]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertTrue(icon.texturePath.contains("gun/ak47"))
    }

    @Test
    public fun `icon resourcePath should combine namespace colon texturePath`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals("tacz:textures/gun/ak47.png", icon.resourcePath)
    }

    @Test
    public fun `requiresCustomRender false when only text`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("a", "b"))
        assertFalse(doc.requiresCustomRender)
    }

    @Test
    public fun `multi-line parse preserves order`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("first", "second", "third"))
        assertEquals(3, doc.lines.size)
        assertEquals("first", (doc.lines[0].segments[0] as LegacyRichTooltipSegment.Text).text)
        assertEquals("third", (doc.lines[2].segments[0] as LegacyRichTooltipSegment.Text).text)
    }

    // ─── LegacyRichTooltipLine.isEmpty ────────────────────────

    @Test
    public fun `line isEmpty true for empty segments list`() {
        assertTrue(LegacyRichTooltipLine(emptyList()).isEmpty)
    }

    @Test
    public fun `line isEmpty true for blank text only`() {
        assertTrue(LegacyRichTooltipLine(listOf(LegacyRichTooltipSegment.Text("   "))).isEmpty)
    }

    @Test
    public fun `line isEmpty false for icon segment`() {
        assertFalse(
            LegacyRichTooltipLine(
                listOf(LegacyRichTooltipSegment.Icon("tacz", "textures/gun/ak47.png", 10))
            ).isEmpty
        )
    }

    @Test
    public fun `line isEmpty false for non-blank text`() {
        assertFalse(LegacyRichTooltipLine(listOf(LegacyRichTooltipSegment.Text("hello"))).isEmpty)
    }

    // ─── EMPTY singleton ──────────────────────────────────────

    @Test
    public fun `EMPTY document has no lines and does not require custom render`() {
        assertEquals(0, LegacyRichTooltipDocument.EMPTY.lines.size)
        assertFalse(LegacyRichTooltipDocument.EMPTY.requiresCustomRender)
    }

    // ─── icon size boundary ─────────────────────────────────

    @Test
    public fun `icon size exactly at min boundary should be kept`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47,6]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals(6, icon.size)
    }

    @Test
    public fun `icon size exactly at max boundary should be kept`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47,32]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals(32, icon.size)
    }

    @Test
    public fun `icon size just above max should clamp to 32`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47,33]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals(32, icon.size)
    }

    @Test
    public fun `icon size just below min should clamp to 6`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47,5]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals(6, icon.size)
    }

    // ─── icon text path edge cases ──────────────────────────

    @Test
    public fun `icon with path already having textures prefix and png extension`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:textures/gun/ak47.png]"))
        val icon = doc.lines[0].segments[0] as LegacyRichTooltipSegment.Icon
        assertEquals("textures/gun/ak47.png", icon.texturePath)
    }

    @Test
    public fun `line with only trailing text after icon`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf("[icon:tacz:gun/ak47] trail"))
        val segs = doc.lines[0].segments
        assertEquals(2, segs.size)
        assertTrue(segs[0] is LegacyRichTooltipSegment.Icon)
        assertEquals(" trail", (segs[1] as LegacyRichTooltipSegment.Text).text)
    }

    @Test
    public fun `document with mix of icon and non-icon lines`() {
        val doc = LegacyRichTooltipParser.parseLines(listOf(
            "§ePlain line",
            "[icon:tacz:gun/ak47,14] text",
            "Another plain"
        ))
        assertTrue(doc.requiresCustomRender)
        assertEquals(3, doc.lines.size)
    }

    @Test
    public fun `line isEmpty true when all text segments are blank`() {
        val line = LegacyRichTooltipLine(listOf(
            LegacyRichTooltipSegment.Text(""),
            LegacyRichTooltipSegment.Text("   ")
        ))
        assertTrue(line.isEmpty)
    }

    @Test
    public fun `line isEmpty false when at least one text segment non-blank`() {
        val line = LegacyRichTooltipLine(listOf(
            LegacyRichTooltipSegment.Text(""),
            LegacyRichTooltipSegment.Text("x")
        ))
        assertFalse(line.isEmpty)
    }
}
