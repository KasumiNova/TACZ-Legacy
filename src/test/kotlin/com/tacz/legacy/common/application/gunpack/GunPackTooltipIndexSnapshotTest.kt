package com.tacz.legacy.common.application.gunpack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

public class GunPackTooltipIndexSnapshotTest {

    // ─── normalizeResourceIdOrNull ────────────────────────────

    @Test
    public fun `normalizeResourceIdOrNull should return null for null`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull(null))
    }

    @Test
    public fun `normalizeResourceIdOrNull should return null for blank`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull("   "))
    }

    @Test
    public fun `normalizeResourceIdOrNull should add default namespace when no colon`() {
        assertEquals("tacz:ak47", GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull("ak47"))
    }

    @Test
    public fun `normalizeResourceIdOrNull should use explicit namespace`() {
        assertEquals("custom:ak47", GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull("custom:ak47"))
    }

    @Test
    public fun `normalizeResourceIdOrNull should return null for empty namespace`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull(":path"))
    }

    @Test
    public fun `normalizeResourceIdOrNull should lowercase namespace and path`() {
        assertEquals("tacz:ak47", GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull("TACZ:AK47"))
    }

    @Test
    public fun `normalizeResourceIdOrNull should trim whitespace`() {
        assertEquals("tacz:ak47", GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull("  tacz:ak47  "))
    }

    // ─── normalizePath ────────────────────────────────────────

    @Test
    public fun `normalizePath should return null for null`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizePath(null))
    }

    @Test
    public fun `normalizePath should return null for empty string`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizePath(""))
    }

    @Test
    public fun `normalizePath should return null for blank`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizePath("   "))
    }

    @Test
    public fun `normalizePath should replace special chars with underscore`() {
        assertEquals("a_b_c", GunPackTooltipIndexSnapshot.normalizePath("a@b#c"))
    }

    @Test
    public fun `normalizePath should collapse consecutive underscores`() {
        assertEquals("a_b", GunPackTooltipIndexSnapshot.normalizePath("a___b"))
    }

    @Test
    public fun `normalizePath should collapse consecutive slashes`() {
        assertEquals("a/b", GunPackTooltipIndexSnapshot.normalizePath("a///b"))
    }

    @Test
    public fun `normalizePath should trim leading trailing underscores and slashes`() {
        assertEquals("path", GunPackTooltipIndexSnapshot.normalizePath("_/path/_"))
    }

    @Test
    public fun `normalizePath should lowercase`() {
        assertEquals("gun/ak47", GunPackTooltipIndexSnapshot.normalizePath("Gun/AK47"))
    }

    @Test
    public fun `normalizePath should preserve hyphens`() {
        assertEquals("m4-a1", GunPackTooltipIndexSnapshot.normalizePath("M4-A1"))
    }

    // ─── snapshot queries ─────────────────────────────────────

    @Test
    public fun `empty snapshot should have zero entries`() {
        val snapshot = GunPackTooltipIndexSnapshot.empty()
        assertEquals(0, snapshot.totalEntries)
        assertTrue(snapshot.failedSources.isEmpty())
    }

    @Test
    public fun `findGunEntry should return matching entry`() {
        val entry = GunPackTooltipIndexEntry("tacz:ak47", "src", "name", "tooltip", null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = mapOf("tacz:ak47" to entry),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = emptyMap(),
            blockEntriesById = emptyMap()
        )
        assertNotNull(snapshot.findGunEntry("tacz:ak47"))
        assertNull(snapshot.findGunEntry("tacz:missing"))
    }

    @Test
    public fun `findAttachmentEntry should return matching entry`() {
        val entry = GunPackTooltipIndexEntry("tacz:scope", "src", null, null, null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = emptyMap(),
            attachmentEntriesById = mapOf("tacz:scope" to entry),
            ammoEntriesById = emptyMap(),
            blockEntriesById = emptyMap()
        )
        assertNotNull(snapshot.findAttachmentEntry("tacz:scope"))
        assertNull(snapshot.findAttachmentEntry("tacz:missing"))
    }

    @Test
    public fun `findAmmoEntry should return matching entry`() {
        val entry = GunPackTooltipIndexEntry("tacz:9mm", "src", null, null, null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = emptyMap(),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = mapOf("tacz:9mm" to entry),
            blockEntriesById = emptyMap()
        )
        assertNotNull(snapshot.findAmmoEntry("tacz:9mm"))
    }

    @Test
    public fun `findBlockEntry should return matching entry`() {
        val entry = GunPackTooltipIndexEntry("tacz:table", "src", null, null, null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = emptyMap(),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = emptyMap(),
            blockEntriesById = mapOf("tacz:table" to entry)
        )
        assertNotNull(snapshot.findBlockEntry("tacz:table"))
    }

    @Test
    public fun `resolveGunTooltipKey should return tooltip key`() {
        val entry = GunPackTooltipIndexEntry("tacz:ak47", "src", "name", "tooltip.key", null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = mapOf("tacz:ak47" to entry),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = emptyMap(),
            blockEntriesById = emptyMap()
        )
        assertEquals("tooltip.key", snapshot.resolveGunTooltipKey("tacz:ak47"))
        assertNull(snapshot.resolveGunTooltipKey("tacz:missing"))
    }

    @Test
    public fun `resolveAttachmentTooltipKey should return tooltip key`() {
        val entry = GunPackTooltipIndexEntry("tacz:scope", "src", null, "att.tip", null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = emptyMap(),
            attachmentEntriesById = mapOf("tacz:scope" to entry),
            ammoEntriesById = emptyMap(),
            blockEntriesById = emptyMap()
        )
        assertEquals("att.tip", snapshot.resolveAttachmentTooltipKey("tacz:scope"))
    }

    @Test
    public fun `resolveAmmoTooltipKey should return tooltip key`() {
        val entry = GunPackTooltipIndexEntry("tacz:9mm", "src", null, "ammo.tip", null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = emptyMap(),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = mapOf("tacz:9mm" to entry),
            blockEntriesById = emptyMap()
        )
        assertEquals("ammo.tip", snapshot.resolveAmmoTooltipKey("tacz:9mm"))
    }

    @Test
    public fun `resolveBlockTooltipKey should return tooltip key`() {
        val entry = GunPackTooltipIndexEntry("tacz:table", "src", null, "block.tip", null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = emptyMap(),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = emptyMap(),
            blockEntriesById = mapOf("tacz:table" to entry)
        )
        assertEquals("block.tip", snapshot.resolveBlockTooltipKey("tacz:table"))
    }

    @Test
    public fun `totalEntries should sum all categories`() {
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = mapOf("tacz:a" to entry("tacz:a")),
            attachmentEntriesById = mapOf("tacz:b" to entry("tacz:b")),
            ammoEntriesById = mapOf("tacz:c" to entry("tacz:c")),
            blockEntriesById = mapOf("tacz:d" to entry("tacz:d"), "tacz:e" to entry("tacz:e"))
        )
        assertEquals(5, snapshot.totalEntries)
    }

    // ─── registry ─────────────────────────────────────────────

    @Test
    public fun `registry replace and clear should update snapshot`() {
        val registry = GunPackTooltipIndexRuntimeRegistry()
        assertEquals(0, registry.snapshot().totalEntries)

        val snap = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = mapOf("tacz:a" to entry("tacz:a")),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = emptyMap(),
            blockEntriesById = emptyMap()
        )
        registry.replace(snap)
        assertEquals(1, registry.snapshot().totalEntries)

        registry.clear()
        assertEquals(0, registry.snapshot().totalEntries)
    }

    private fun entry(id: String) = GunPackTooltipIndexEntry(id, "test", null, null, null, null)

    // ─── additional edge cases for branch coverage ──────────

    @Test
    public fun `normalizeResourceIdOrNull should return null for path only being special chars`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull("tacz:@#\$%"))
    }

    @Test
    public fun `normalizeResourceIdOrNull should handle no colon with default namespace`() {
        assertEquals("tacz:ak47", GunPackTooltipIndexSnapshot.normalizeResourceIdOrNull("ak47"))
    }

    @Test
    public fun `normalizePath should return null for path with only special characters`() {
        assertNull(GunPackTooltipIndexSnapshot.normalizePath("@#\$"))
    }

    @Test
    public fun `normalizePath should handle path with mixed valid and invalid chars`() {
        assertEquals("gun_ak47-v2", GunPackTooltipIndexSnapshot.normalizePath("gun@ak47-v2"))
    }

    @Test
    public fun `findGunEntry should normalize query with uppercase`() {
        val entry = GunPackTooltipIndexEntry("tacz:ak47", "src", "name", "tooltip", null, null)
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = mapOf("tacz:ak47" to entry),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = emptyMap(),
            blockEntriesById = emptyMap()
        )
        assertNotNull(snapshot.findGunEntry("TACZ:AK47"))
    }

    @Test
    public fun `findAttachmentEntry should return null for null-normalized query`() {
        val snapshot = GunPackTooltipIndexSnapshot.empty()
        assertNull(snapshot.findAttachmentEntry("   "))
    }

    @Test
    public fun `findAmmoEntry should return null for null-normalized query`() {
        val snapshot = GunPackTooltipIndexSnapshot.empty()
        assertNull(snapshot.findAmmoEntry("   "))
    }

    @Test
    public fun `findBlockEntry should return null for null-normalized query`() {
        val snapshot = GunPackTooltipIndexSnapshot.empty()
        assertNull(snapshot.findBlockEntry("   "))
    }

    @Test
    public fun `resolveAttachmentTooltipKey should return null for missing entry`() {
        val snapshot = GunPackTooltipIndexSnapshot.empty()
        assertNull(snapshot.resolveAttachmentTooltipKey("tacz:missing"))
    }

    @Test
    public fun `resolveAmmoTooltipKey should return null for missing entry`() {
        val snapshot = GunPackTooltipIndexSnapshot.empty()
        assertNull(snapshot.resolveAmmoTooltipKey("tacz:missing"))
    }

    @Test
    public fun `resolveBlockTooltipKey should return null for missing entry`() {
        val snapshot = GunPackTooltipIndexSnapshot.empty()
        assertNull(snapshot.resolveBlockTooltipKey("tacz:missing"))
    }

    @Test
    public fun `failedSources should be preserved`() {
        val snapshot = GunPackTooltipIndexSnapshot(
            loadedAtEpochMillis = 0L,
            gunEntriesById = emptyMap(),
            attachmentEntriesById = emptyMap(),
            ammoEntriesById = emptyMap(),
            blockEntriesById = emptyMap(),
            failedSources = setOf("bad_pack_a", "bad_pack_b")
        )
        assertEquals(2, snapshot.failedSources.size)
        assertTrue(snapshot.failedSources.contains("bad_pack_a"))
    }
}
