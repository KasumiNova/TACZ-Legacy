package com.tacz.legacy.common.infrastructure.mc.registry.item

import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangSnapshot
import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipParser
import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipSegment
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexEntry
import com.tacz.legacy.common.infrastructure.mc.gunpack.GunPackTooltipIndexPreInitScanner
import com.tacz.legacy.common.infrastructure.mc.gunpack.GunPackTooltipLangPreInitScanner
import org.apache.logging.log4j.LogManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator

public class LegacyIndexedTooltipSupportTest {

    @After
    public fun cleanupRuntimes() {
        GunPackTooltipIndexRuntime.registry().clear()
        GunPackTooltipLangRuntime.registry().clear()
    }

    // ─── toIconTokenResourceOrNull ──────────────────────────

    @Test
    public fun `to icon token resource should parse canonical asset texture path`() {
        val resource = LegacyIndexedTooltipSupport.toIconTokenResourceOrNull(
            "assets/tacz/textures/ammo/slot/9mm.png"
        )
        assertEquals("tacz:ammo/slot/9mm", resource)
    }

    @Test
    public fun `to icon token resource should normalize case and slashes`() {
        val resource = LegacyIndexedTooltipSupport.toIconTokenResourceOrNull(
            "assets/TACZ/textures/GUN\\slot\\AK47.PNG"
        )
        assertEquals("tacz:gun/slot/ak47", resource)
    }

    @Test
    public fun `to icon token resource should return null for non texture assets`() {
        assertNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull(
            "assets/tacz/display/guns/ak47_display.json"
        ))
    }

    @Test
    public fun `to icon token resource should return null for null input`() {
        assertNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull(null))
    }

    @Test
    public fun `to icon token resource should return null for blank input`() {
        assertNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull("  "))
    }

    @Test
    public fun `to icon token resource should return null for empty string`() {
        assertNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull(""))
    }

    @Test
    public fun `to icon token resource should accept jpg and jpeg extensions`() {
        assertNotNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull("assets/ns/textures/a.jpg"))
        assertNotNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull("assets/ns/textures/a.jpeg"))
    }

    @Test
    public fun `to icon token resource should reject unsupported extension`() {
        assertNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull("assets/ns/textures/a.bmp"))
    }

    // ─── resolveBlockIconTextureAssetPath ────────────────────

    @Test
    public fun `resolve block icon should prefer display id`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = "tacz:gun_smith_table", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = "tacz:workbench_a", type = null
        )
        assertEquals("assets/tacz/textures/block/workbench_a.png",
            LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    @Test
    public fun `resolve block icon should fallback to item id when display missing`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = "tacz:gun_smith_table", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = null, type = null
        )
        assertEquals("assets/tacz/textures/block/gun_smith_table.png",
            LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    @Test
    public fun `resolve block icon should reject invalid path characters`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = "tacz:gun smith table", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = null, type = null
        )
        assertNull(LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    @Test
    public fun `resolve block icon should return null for null entry`() {
        assertNull(LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(null))
    }

    @Test
    public fun `resolve block icon should return null for id without namespace colon`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = "nonamespace", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = null, type = null
        )
        assertNull(LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    @Test
    public fun `resolve block icon should return null for blank namespace`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = ":path", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = null, type = null
        )
        assertNull(LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    @Test
    public fun `resolve block icon should return null for blank path after colon`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = "tacz:", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = null, type = null
        )
        assertNull(LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    // ─── appendIconTokenLine ────────────────────────────────

    @Test
    public fun `appendIconTokenLine should not add line for null path`() {
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIconTokenLine(tooltip, null)
        assertTrue(tooltip.isEmpty())
    }

    @Test
    public fun `appendIconTokenLine should not add line for non matching path`() {
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIconTokenLine(tooltip, "not/valid/texture_path.ogg")
        assertTrue(tooltip.isEmpty())
    }

    @Test
    public fun `appendIconTokenLine should add icon token with default size 11`() {
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIconTokenLine(tooltip, "assets/tacz/textures/gun/slot/ak47.png")
        assertEquals("[icon:tacz:gun/slot/ak47,11]", tooltip[0])
    }

    @Test
    public fun `appendIconTokenLine should clamp size below min to 6`() {
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIconTokenLine(tooltip, "assets/tacz/textures/gun/ak47.png", size = 2)
        assertEquals("[icon:tacz:gun/ak47,6]", tooltip[0])
    }

    @Test
    public fun `appendIconTokenLine should clamp size above max to 32`() {
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIconTokenLine(tooltip, "assets/tacz/textures/gun/ak47.png", size = 100)
        assertEquals("[icon:tacz:gun/ak47,32]", tooltip[0])
    }

    // ─── appendIndexedLines ─────────────────────────────────

    @Test
    public fun `appendIndexedLines should do nothing when entry is null`() {
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "Name", null)
        assertTrue(tooltip.isEmpty())
    }

    @Test
    public fun `appendIndexedLines should skip name line when same as stack display name`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("n" to "Same Name")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", "n", null, null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "Same Name", entry)
        assertTrue(tooltip.isEmpty())
    }

    @Test
    public fun `appendIndexedLines should skip name line when same ignoring color codes`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("n" to "§eWeapon")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", "n", null, null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "Weapon", entry)
        assertTrue(tooltip.isEmpty())
    }

    @Test
    public fun `appendIndexedLines should add name when different from display`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("n" to "AK47 突击步枪")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", "n", null, null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "AK47", entry)
        assertEquals("§eAK47 突击步枪", tooltip[0])
    }

    @Test
    public fun `appendIndexedLines should keep section prefix on tooltip line`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("t" to "§c红色描述")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", null, "t", null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertEquals("§c红色描述", tooltip[0])
    }

    @Test
    public fun `appendIndexedLines should keep icon prefix uncolored`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("t" to "[icon:tacz:gun/ak47,11]")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", null, "t", null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertEquals("[icon:tacz:gun/ak47,11]", tooltip[0])
    }

    @Test
    public fun `appendIndexedLines should default color plain desc line`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("t" to "普通描述")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", null, "t", null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertEquals("§7普通描述", tooltip[0])
    }

    @Test
    public fun `appendIndexedLines should split multiline tooltip with backslash n`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("t" to "第一行\\n第二行")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", null, "t", null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertEquals(2, tooltip.size)
        assertTrue(tooltip[0].contains("第一行"))
        assertTrue(tooltip[1].contains("第二行"))
    }

    @Test
    public fun `appendIndexedLines should split multiline tooltip with backslash r n`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("t" to "行1\\r\\n行2")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", null, "t", null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertEquals(2, tooltip.size)
    }

    @Test
    public fun `appendIndexedLines should skip blank name key`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L, valuesByLocale = mapOf())
        )
        val entry = GunPackTooltipIndexEntry("id", "src", "  ", null, null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertTrue(tooltip.isEmpty())
    }

    @Test
    public fun `appendIndexedLines should skip null tooltip key`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L, valuesByLocale = mapOf())
        )
        val entry = GunPackTooltipIndexEntry("id", "src", null, null, null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertTrue(tooltip.isEmpty())
    }

    // ─── resolve*Entry ─────────────────────────────────────

    @Test
    public fun `resolveAttachmentEntry should return null for missing id`() {
        assertNull(LegacyIndexedTooltipSupport.resolveAttachmentEntry("tacz:missing"))
    }

    @Test
    public fun `resolveAmmoEntry should return null for missing id`() {
        assertNull(LegacyIndexedTooltipSupport.resolveAmmoEntry("tacz:missing"))
    }

    @Test
    public fun `resolveBlockEntry should return null for missing id`() {
        assertNull(LegacyIndexedTooltipSupport.resolveBlockEntry("tacz:missing"))
    }

    // ─── integration: pack scan + tooltip → parser ──────────

    @Test
    public fun `append indexed lines should fallback to gunpack lang runtime and keep icon token line`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(
                loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf(
                    "zh_cn" to mapOf(
                        "tacz.gun.ak47.name" to "AK47 突击步枪",
                        "tacz.gun.ak47.desc" to "[icon:tacz:gun/slot/ak47,11]\n基础伤害：7.5"
                    )
                )
            )
        )

        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(
            tooltip = tooltip,
            stackDisplayName = "AK47",
            entry = GunPackTooltipIndexEntry(
                itemId = "tacz:ak47",
                sourceId = "test",
                nameKey = "tacz.gun.ak47.name",
                tooltipKey = "tacz.gun.ak47.desc",
                displayId = null,
                type = "rifle"
            )
        )

        assertEquals("§eAK47 突击步枪", tooltip[0])
        assertEquals("[icon:tacz:gun/slot/ak47,11]", tooltip[1])
        assertEquals("§7基础伤害：7.5", tooltip[2])
    }

    @Test
    public fun `dynamic pack scan should load tooltip text and parser should produce icon segment`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-align-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            stageTooltipPack(packRoot)

            val indexSnapshot = GunPackTooltipIndexPreInitScanner().scan(
                configRoot, LogManager.getLogger("DynamicIndexTest")
            )
            val langSnapshot = GunPackTooltipLangPreInitScanner().scan(
                configRoot, LogManager.getLogger("DynamicLangTest")
            )
            GunPackTooltipIndexRuntime.registry().replace(indexSnapshot)
            GunPackTooltipLangRuntime.registry().replace(langSnapshot)

            val entry = indexSnapshot.findGunEntry("tacz:ak47")
            val tooltip = mutableListOf<String>()
            LegacyIndexedTooltipSupport.appendIndexedLines(
                tooltip = tooltip,
                stackDisplayName = "AK47",
                entry = entry
            )

            val document = LegacyRichTooltipParser.parseLines(tooltip)
            assertTrue(document.requiresCustomRender)
            assertTrue(document.lines.any { line ->
                line.segments.any { it is LegacyRichTooltipSegment.Icon }
            })
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    private fun stageTooltipPack(packRoot: Path) {
        Files.createDirectories(packRoot)
        Files.write(packRoot.resolve("gunpack.meta.json"), "{}".toByteArray(StandardCharsets.UTF_8))

        val gunIndex = packRoot
            .resolve("data")
            .resolve("tacz")
            .resolve("index")
            .resolve("guns")
            .resolve("ak47.json")
        Files.createDirectories(gunIndex.parent)
        Files.write(
            gunIndex,
            """
            {
              "name": "tacz.gun.ak47.name",
              "tooltip": "tacz.gun.ak47.desc"
            }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )

        val zhCn = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("lang")
            .resolve("zh_cn.json")
        Files.createDirectories(zhCn.parent)
        Files.write(
            zhCn,
            """
            {
              "tacz.gun.ak47.name": "AK47 突击步枪",
              "tacz.gun.ak47.desc": "[icon:tacz:gun/slot/ak47,11]\\n基础伤害：7.5"
            }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )
    }

    private fun deleteRecursively(root: Path) {
        if (!Files.exists(root)) {
            return
        }

        Files.walk(root).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { path ->
                Files.deleteIfExists(path)
            }
        }
    }

    // ─── additional edge-case coverage ──────────────────────

    @Test
    public fun `toIconTokenResourceOrNull should return null for path without textures directory`() {
        assertNull(LegacyIndexedTooltipSupport.toIconTokenResourceOrNull("assets/tacz/models/gun.png"))
    }

    @Test
    public fun `toIconTokenResourceOrNull should handle backslash path separators`() {
        val result = LegacyIndexedTooltipSupport.toIconTokenResourceOrNull("assets\\tacz\\textures\\gun.png")
        assertEquals("tacz:gun", result)
    }

    @Test
    public fun `resolveBlockIconTextureAssetPath should use displayId over itemId when both present`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = "tacz:default_block", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = "tacz:custom_block", type = null
        )
        assertEquals("assets/tacz/textures/block/custom_block.png",
            LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    @Test
    public fun `resolveBlockIconTextureAssetPath should return null when displayId is blank`() {
        val entry = GunPackTooltipIndexEntry(
            itemId = "tacz:table", sourceId = "test",
            nameKey = null, tooltipKey = null, displayId = "  ", type = null
        )
        // displayId "  " → substringBefore(':') = "  " → trim+lowercase = "" → blank → return null
        assertNull(LegacyIndexedTooltipSupport.resolveBlockIconTextureAssetPath(entry))
    }

    @Test
    public fun `appendIconTokenLine should work with valid path and custom clamped size`() {
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIconTokenLine(tooltip, "assets/tacz/textures/gun/ak47.png", size = 15)
        assertEquals("[icon:tacz:gun/ak47,15]", tooltip[0])
    }

    @Test
    public fun `appendIndexedLines should add both name and tooltip when both keys resolve`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("n" to "名称", "t" to "描述")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", "n", "t", null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "Different", entry)
        assertEquals(2, tooltip.size)
        assertEquals("§e名称", tooltip[0])
        assertEquals("§7描述", tooltip[1])
    }

    @Test
    public fun `appendIndexedLines localized name blank should be skipped`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("n" to "   ")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", "n", null, null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "Weapon", entry)
        assertTrue(tooltip.isEmpty())
    }

    @Test
    public fun `withDefaultColor should preserve leading icon token without adding color`() {
        GunPackTooltipLangRuntime.registry().replace(
            GunPackTooltipLangSnapshot(loadedAtEpochMillis = 0L,
                valuesByLocale = mapOf("zh_cn" to mapOf("t" to "  [icon:tacz:gun/ak47,11] with text")))
        )
        val entry = GunPackTooltipIndexEntry("id", "src", null, "t", null, null)
        val tooltip = mutableListOf<String>()
        LegacyIndexedTooltipSupport.appendIndexedLines(tooltip, "x", entry)
        assertTrue(tooltip[0].startsWith("[icon:"))
    }
}
