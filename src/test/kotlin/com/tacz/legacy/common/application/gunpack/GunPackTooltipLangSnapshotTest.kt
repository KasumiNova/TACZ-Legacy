package com.tacz.legacy.common.application.gunpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class GunPackTooltipLangSnapshotTest {

    // ─── normalizeLocale ──────────────────────────────────────

    @Test
    public fun `normalizeLocale should return null for null`() {
        assertNull(GunPackTooltipLangSnapshot.normalizeLocale(null))
    }

    @Test
    public fun `normalizeLocale should return null for blank`() {
        assertNull(GunPackTooltipLangSnapshot.normalizeLocale("   "))
    }

    @Test
    public fun `normalizeLocale should return null for invalid format`() {
        assertNull(GunPackTooltipLangSnapshot.normalizeLocale("english"))
        assertNull(GunPackTooltipLangSnapshot.normalizeLocale("e"))
        assertNull(GunPackTooltipLangSnapshot.normalizeLocale("en_us_extra"))
    }

    @Test
    public fun `normalizeLocale should convert hyphen to underscore`() {
        assertEquals("en_us", GunPackTooltipLangSnapshot.normalizeLocale("en-US"))
    }

    @Test
    public fun `normalizeLocale should lowercase`() {
        assertEquals("zh_cn", GunPackTooltipLangSnapshot.normalizeLocale("ZH_CN"))
    }

    @Test
    public fun `normalizeLocale should trim whitespace`() {
        assertEquals("zh_cn", GunPackTooltipLangSnapshot.normalizeLocale("  zh_cn  "))
    }

    // ─── resolve ──────────────────────────────────────────────

    @Test
    public fun `resolve should return null for null locale`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertNull(snapshot.resolve(null, "k"))
    }

    @Test
    public fun `resolve should return null for null key`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertNull(snapshot.resolve("zh_cn", null))
    }

    @Test
    public fun `resolve should return null for blank key`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertNull(snapshot.resolve("zh_cn", "  "))
    }

    @Test
    public fun `resolve should return null for invalid locale format`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertNull(snapshot.resolve("english", "k"))
    }

    @Test
    public fun `resolve should return value for valid locale and key`() {
        val snapshot = snapshotWith("zh_cn", mapOf("key" to "值"))
        assertEquals("值", snapshot.resolve("zh_cn", "key"))
    }

    @Test
    public fun `resolve should return null for missing key`() {
        val snapshot = snapshotWith("zh_cn", mapOf("key" to "值"))
        assertNull(snapshot.resolve("zh_cn", "missing"))
    }

    @Test
    public fun `resolve should return null for missing locale`() {
        val snapshot = snapshotWith("zh_cn", mapOf("key" to "值"))
        assertNull(snapshot.resolve("en_us", "key"))
    }

    // ─── resolvePreferred ─────────────────────────────────────

    @Test
    public fun `resolvePreferred should return null for null key`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertNull(snapshot.resolvePreferred(null))
    }

    @Test
    public fun `resolvePreferred should return null for blank key`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertNull(snapshot.resolvePreferred("  "))
    }

    @Test
    public fun `resolvePreferred should try zh_cn first then en_us`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "zh_cn" to mapOf("k" to "中文"),
                "en_us" to mapOf("k" to "English")
            )
        )
        assertEquals("中文", snapshot.resolvePreferred("k"))
    }

    @Test
    public fun `resolvePreferred should fall back to en_us when zh_cn missing`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "en_us" to mapOf("k" to "English")
            )
        )
        assertEquals("English", snapshot.resolvePreferred("k"))
    }

    @Test
    public fun `resolvePreferred should fall back to any locale when preferred locales miss`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "ja_jp" to mapOf("k" to "日本語")
            )
        )
        assertEquals("日本語", snapshot.resolvePreferred("k"))
    }

    @Test
    public fun `resolvePreferred should skip blank values in preferred locales`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "zh_cn" to mapOf("k" to "   "),
                "en_us" to mapOf("k" to "English")
            )
        )
        assertEquals("English", snapshot.resolvePreferred("k"))
    }

    @Test
    public fun `resolvePreferred should return null when no locale has key`() {
        val snapshot = snapshotWith("zh_cn", mapOf("other" to "v"))
        assertNull(snapshot.resolvePreferred("missing"))
    }

    // ─── snapshot properties ──────────────────────────────────

    @Test
    public fun `empty snapshot should have zero locales and entries`() {
        val snapshot = GunPackTooltipLangSnapshot.empty()
        assertEquals(0, snapshot.totalLocales)
        assertEquals(0, snapshot.totalEntries)
        assertTrue(snapshot.failedSources.isEmpty())
    }

    @Test
    public fun `totalLocales and totalEntries should be correct`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "zh_cn" to mapOf("a" to "1", "b" to "2"),
                "en_us" to mapOf("a" to "1")
            )
        )
        assertEquals(2, snapshot.totalLocales)
        assertEquals(3, snapshot.totalEntries)
    }

    // ─── registry ─────────────────────────────────────────────

    @Test
    public fun `registry replace and clear should update snapshot`() {
        val registry = GunPackTooltipLangRuntimeRegistry()
        assertEquals(0, registry.snapshot().totalLocales)

        val snap = snapshotWith("zh_cn", mapOf("k" to "v"))
        registry.replace(snap)
        assertEquals(1, registry.snapshot().totalLocales)

        registry.clear()
        assertEquals(0, registry.snapshot().totalLocales)
    }

    private fun snapshotWith(locale: String, entries: Map<String, String>): GunPackTooltipLangSnapshot =
        GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(locale to entries)
        )

    // ─── additional edge cases for branch coverage ──────────

    @Test
    public fun `resolve should normalize locale case when looking up`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertEquals("v", snapshot.resolve("ZH_CN", "k"))
    }

    @Test
    public fun `resolve should normalize locale hyphens when looking up`() {
        val snapshot = snapshotWith("en_us", mapOf("k" to "v"))
        assertEquals("v", snapshot.resolve("en-US", "k"))
    }

    @Test
    public fun `resolvePreferred with custom locale list`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "ja_jp" to mapOf("k" to "日本語"),
                "en_us" to mapOf("k" to "English")
            )
        )
        assertEquals("日本語", snapshot.resolvePreferred("k", listOf("ja_jp", "en_us")))
    }

    @Test
    public fun `resolvePreferred should skip blank values in any-locale fallback`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "ja_jp" to mapOf("k" to "   "),
                "ko_kr" to mapOf("k" to "한국어")
            )
        )
        // Both zh_cn and en_us missing; ja_jp blank; should find ko_kr
        assertEquals("한국어", snapshot.resolvePreferred("k"))
    }

    @Test
    public fun `resolvePreferred should return null when all locales have blank values`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = mapOf(
                "zh_cn" to mapOf("k" to "   "),
                "en_us" to mapOf("k" to "  ")
            )
        )
        assertNull(snapshot.resolvePreferred("k"))
    }

    @Test
    public fun `resolve should return null for blank locale`() {
        val snapshot = snapshotWith("zh_cn", mapOf("k" to "v"))
        assertNull(snapshot.resolve("  ", "k"))
    }

    @Test
    public fun `failedSources should be preserved`() {
        val snapshot = GunPackTooltipLangSnapshot(
            loadedAtEpochMillis = 0L,
            valuesByLocale = emptyMap(),
            failedSources = setOf("bad.zip")
        )
        assertEquals(1, snapshot.failedSources.size)
        assertTrue(snapshot.failedSources.contains("bad.zip"))
    }
}
