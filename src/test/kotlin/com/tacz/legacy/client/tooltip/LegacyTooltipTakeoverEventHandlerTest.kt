package com.tacz.legacy.client.tooltip

import com.tacz.legacy.common.application.gunpack.GunDisplayRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexEntry
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexRuntime
import com.tacz.legacy.common.application.gunpack.GunPackTooltipIndexSnapshot
import com.tacz.legacy.common.application.gunpack.GunPackTooltipLangRuntime
import com.tacz.legacy.common.application.tooltip.LegacyRichTooltipParser
import com.tacz.legacy.common.infrastructure.mc.gunpack.GunPackTooltipIndexPreInitScanner
import com.tacz.legacy.common.infrastructure.mc.gunpack.GunPackTooltipLangPreInitScanner
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoBoxItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAmmoItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyAttachmentItem
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.init.Bootstrap
import net.minecraft.init.Items
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import org.apache.logging.log4j.LogManager
import org.junit.After
import org.junit.BeforeClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class LegacyTooltipTakeoverEventHandlerTest {

    public companion object {
        @JvmStatic
        @BeforeClass
        public fun bootstrapMinecraftStatics() {
            Bootstrap.register()
        }
    }

    @After
    public fun cleanupRuntimes() {
        GunPackTooltipIndexRuntime.registry().clear()
        GunPackTooltipLangRuntime.registry().clear()
        GunDisplayRuntime.registry().clear()
    }

    @Test
    public fun `buildGeneratedLines should keep gun hint order`() {
        val gun = LegacyGunItem(registryPath = "ak47")
        val stack = ItemStack(gun)

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, gun.registryName!!) ?: emptyList()

        val fireIndex = lines.indexOf("§7左键：开火")
        val reloadIndex = lines.indexOf("§7潜行 + 右键：换弹")

        assertTrue(fireIndex >= 0)
        assertTrue(reloadIndex >= 0)
        assertTrue(fireIndex < reloadIndex)
        assertTrue(lines.none { it == "§8────────" })
    }

    @Test
    public fun `buildGeneratedLines should append ammo box stats in stable order`() {
        val ammoBox = LegacyAmmoBoxItem(
            registryPath = "ammo_box_9mm",
            ammoId = "9mm",
            roundsPerUse = 12,
            capacity = 90,
            iconTextureAssetPath = "assets/tacz/textures/ammo/slot/9mm.png"
        )
        val stack = ItemStack(ammoBox)
        stack.tagCompound = NBTTagCompound().apply {
            setInteger("remaining_rounds", 47)
        }

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, ammoBox.registryName!!) ?: emptyList()

        assertTrue(lines.size >= 4)
        assertEquals("[icon:tacz:ammo/slot/9mm,11]", lines[lines.size - 4])
        assertEquals("§8────────", lines[lines.size - 3])
        assertEquals("§8单次补充：+12", lines[lines.size - 2])
        assertEquals("§8剩余弹量：47/90", lines.last())
    }

    @Test
    public fun `buildGeneratedLines should generate block icon token from indexed display id`() {
        val block = object : Block(Material.ROCK) {}
        block.setRegistryName("tacz", "gun_smith_table")
        val itemBlock = ItemBlock(block)
        itemBlock.setRegistryName(block.registryName)

        GunPackTooltipIndexRuntime.registry().replace(
            GunPackTooltipIndexSnapshot(
                loadedAtEpochMillis = 0L,
                gunEntriesById = emptyMap(),
                attachmentEntriesById = emptyMap(),
                ammoEntriesById = emptyMap(),
                blockEntriesById = mapOf(
                    "tacz:gun_smith_table" to GunPackTooltipIndexEntry(
                        itemId = "tacz:gun_smith_table",
                        sourceId = "test",
                        nameKey = null,
                        tooltipKey = null,
                        displayId = "tacz:gun_smith_table",
                        type = null
                    )
                )
            )
        )

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(
            ItemStack(itemBlock),
            itemBlock.registryName!!
        ) ?: emptyList()

        assertTrue(lines.contains("[icon:tacz:block/gun_smith_table,11]"))
    }

    @Test
    public fun `mergeTooltipLines should deduplicate ignoring color and case`() {
        val base = mutableListOf(
            "§eAK47",
            "§7左键：开火"
        )

        LegacyTooltipTakeoverEventHandler.mergeTooltipLines(
            target = base,
            additions = listOf(
                "§7ak47",
                "§a左键：开火",
                "§8潜行 + 右键：换弹"
            )
        )

        assertEquals(
            listOf(
                "§eAK47",
                "§7左键：开火",
                "§8潜行 + 右键：换弹"
            ),
            base
        )
    }

    @Test
    public fun `buildGeneratedLines should insert separator and parse rich icon from dynamically loaded pack lang`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-dynamic-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            stageTooltipDirectoryPack(
                packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            )

            val logger = LogManager.getLogger("LegacyTooltipTakeoverEventHandlerDynamicTest")
            val indexSnapshot = GunPackTooltipIndexPreInitScanner().scan(configRoot, logger)
            val langSnapshot = GunPackTooltipLangPreInitScanner().scan(configRoot, logger)

            GunPackTooltipIndexRuntime.registry().replace(indexSnapshot)
            GunPackTooltipLangRuntime.registry().replace(langSnapshot)

            assertEquals(1, indexSnapshot.gunEntriesById.size)
            assertEquals(1, indexSnapshot.blockEntriesById.size)
            assertFalse(indexSnapshot.failedSources.isNotEmpty())
            assertEquals(1, langSnapshot.totalLocales)
            assertTrue(langSnapshot.totalEntries >= 4)

            val gun = LegacyGunItem(registryPath = "ak47")
            val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(ItemStack(gun), gun.registryName!!)
            assertNotNull(lines)

            val generated = lines ?: emptyList()
            assertTrue(generated.contains("§e动态AK47（测试）"))
            assertTrue(generated.contains("[icon:tacz:gun/slot/test_ak47,12]"))
            assertTrue(generated.contains("§7动态加载描述"))

            val separatorIndex = generated.indexOf("§8────────")
            val hintIndex = generated.indexOf("§7左键：开火")
            assertTrue(separatorIndex >= 0)
            assertTrue(hintIndex > separatorIndex)

            val parsed = LegacyRichTooltipParser.parseLines(generated)
            assertTrue(parsed.requiresCustomRender)
            val iconSegment = parsed.lines
                .flatMap { it.segments }
                .firstOrNull { segment ->
                    segment is com.tacz.legacy.common.application.tooltip.LegacyRichTooltipSegment.Icon
                } as? com.tacz.legacy.common.application.tooltip.LegacyRichTooltipSegment.Icon
            assertNotNull(iconSegment)
            assertEquals("tacz", iconSegment?.namespace)
            assertEquals("textures/gun/slot/test_ak47.png", iconSegment?.texturePath)
            assertEquals(12, iconSegment?.size)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `buildGeneratedLines should resolve block localized lines and rich icon from dynamically loaded pack`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-dynamic-block-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            stageTooltipDirectoryPack(
                packRoot = gameRoot.resolve("tacz").resolve("sample_pack")
            )

            val logger = LogManager.getLogger("LegacyTooltipTakeoverEventHandlerDynamicBlockTest")
            val indexSnapshot = GunPackTooltipIndexPreInitScanner().scan(configRoot, logger)
            val langSnapshot = GunPackTooltipLangPreInitScanner().scan(configRoot, logger)

            GunPackTooltipIndexRuntime.registry().replace(indexSnapshot)
            GunPackTooltipLangRuntime.registry().replace(langSnapshot)

            val block = object : Block(Material.ROCK) {}
            block.setRegistryName("tacz", "gun_smith_table")
            val itemBlock = ItemBlock(block)
            itemBlock.setRegistryName(block.registryName)

            val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(ItemStack(itemBlock), itemBlock.registryName!!)
            assertNotNull(lines)

            val generated = lines ?: emptyList()
            assertTrue(generated.contains("§e动态枪匠工作台（测试）"))
            assertTrue(generated.contains("[icon:tacz:block/gun_smith_table,10]"))
            assertTrue(generated.contains("§7来自动态枪包"))
            assertTrue(generated.contains("[icon:tacz:block/gun_smith_table,11]"))
            assertTrue(generated.none { it == "§8────────" })

            val parsed = LegacyRichTooltipParser.parseLines(generated)
            assertTrue(parsed.requiresCustomRender)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `buildGeneratedLines should load localized rich tooltip from nested root zip pack`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-zip-nested-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            stageTooltipZipPackWithNestedRoot(taczRoot.resolve("sample_pack_nested.zip"))

            val logger = LogManager.getLogger("LegacyTooltipTakeoverEventHandlerDynamicZipTest")
            val indexSnapshot = GunPackTooltipIndexPreInitScanner().scan(configRoot, logger)
            val langSnapshot = GunPackTooltipLangPreInitScanner().scan(configRoot, logger)

            GunPackTooltipIndexRuntime.registry().replace(indexSnapshot)
            GunPackTooltipLangRuntime.registry().replace(langSnapshot)

            assertEquals(1, indexSnapshot.gunEntriesById.size)
            assertEquals(1, indexSnapshot.blockEntriesById.size)
            assertTrue(indexSnapshot.failedSources.isEmpty())
            assertEquals(1, langSnapshot.totalLocales)
            assertTrue(langSnapshot.totalEntries >= 4)

            val gun = LegacyGunItem(registryPath = "ak47")
            val gunLines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(ItemStack(gun), gun.registryName!!) ?: emptyList()
            assertTrue(gunLines.contains("§e动态AK47（测试）"))
            assertTrue(gunLines.contains("[icon:tacz:gun/slot/test_ak47,12]"))
            assertTrue(gunLines.contains("§7动态加载描述"))
            assertTrue(gunLines.contains("§8────────"))

            val block = object : Block(Material.ROCK) {}
            block.setRegistryName("tacz", "gun_smith_table")
            val itemBlock = ItemBlock(block)
            itemBlock.setRegistryName(block.registryName)

            val blockLines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(ItemStack(itemBlock), itemBlock.registryName!!) ?: emptyList()
            assertTrue(blockLines.contains("§e动态枪匠工作台（测试）"))
            assertTrue(blockLines.contains("[icon:tacz:block/gun_smith_table,10]"))
            assertTrue(blockLines.contains("§7来自动态枪包"))
            assertTrue(blockLines.contains("[icon:tacz:block/gun_smith_table,11]"))

            assertTrue(LegacyRichTooltipParser.parseLines(gunLines).requiresCustomRender)
            assertTrue(LegacyRichTooltipParser.parseLines(blockLines).requiresCustomRender)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `buildGeneratedLines should keep first index and lang values when nested zip contains duplicates`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-zip-conflict-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)
            stageTooltipZipPackWithNestedRootConflicts(taczRoot.resolve("sample_pack_conflict_nested.zip"))

            val logger = LogManager.getLogger("LegacyTooltipTakeoverEventHandlerDynamicZipConflictTest")
            val indexSnapshot = GunPackTooltipIndexPreInitScanner().scan(configRoot, logger)
            val langSnapshot = GunPackTooltipLangPreInitScanner().scan(configRoot, logger)

            GunPackTooltipIndexRuntime.registry().replace(indexSnapshot)
            GunPackTooltipLangRuntime.registry().replace(langSnapshot)

            assertEquals(1, indexSnapshot.gunEntriesById.size)
            assertTrue(indexSnapshot.failedSources.isEmpty())
            assertEquals(1, langSnapshot.totalLocales)
            assertTrue(langSnapshot.failedSources.isEmpty())

            val gunEntry = indexSnapshot.findGunEntry("tacz:ak47")
            assertNotNull(gunEntry)
            assertEquals("tacz.test.conflict.ak47.name", gunEntry?.nameKey)
            assertEquals("tacz.test.conflict.ak47.tooltip", gunEntry?.tooltipKey)
            assertTrue(gunEntry?.sourceId?.contains("sample_pack/data/tacz/index/guns/ak47.json") == true)

            val gun = LegacyGunItem(registryPath = "ak47")
            val gunLines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(ItemStack(gun), gun.registryName!!) ?: emptyList()

            assertTrue(gunLines.contains("§e首选AK47（冲突测试）"))
            assertTrue(gunLines.contains("§7首选描述"))
            assertTrue(gunLines.none { it.contains("覆盖AK47（冲突测试）") })
            assertTrue(gunLines.none { it.contains("覆盖描述") })
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    @Test
    public fun `buildGeneratedLines should merge non-conflicting entries when directory and zip packs coexist`() {
        val gameRoot = Files.createTempDirectory("tacz-legacy-tooltip-multipack-")
        try {
            val configRoot = gameRoot.resolve("config")
            Files.createDirectories(configRoot)

            val taczRoot = gameRoot.resolve("tacz")
            Files.createDirectories(taczRoot)

            stageTooltipDirectoryPack(taczRoot.resolve("base_pack"))
            stageTooltipAddonZipPackWithNestedRoot(taczRoot.resolve("addon_pack_nested.zip"))

            val logger = LogManager.getLogger("LegacyTooltipTakeoverEventHandlerMultiPackTest")
            val indexSnapshot = GunPackTooltipIndexPreInitScanner().scan(configRoot, logger)
            val langSnapshot = GunPackTooltipLangPreInitScanner().scan(configRoot, logger)

            GunPackTooltipIndexRuntime.registry().replace(indexSnapshot)
            GunPackTooltipLangRuntime.registry().replace(langSnapshot)

            assertEquals(1, indexSnapshot.gunEntriesById.size)
            assertEquals(2, indexSnapshot.blockEntriesById.size)
            assertTrue(indexSnapshot.failedSources.isEmpty())
            assertEquals(1, langSnapshot.totalLocales)
            assertTrue(langSnapshot.totalEntries >= 6)

            val gun = LegacyGunItem(registryPath = "ak47")
            val gunLines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(ItemStack(gun), gun.registryName!!) ?: emptyList()
            assertTrue(gunLines.contains("§e动态AK47（测试）"))
            assertTrue(gunLines.contains("§7动态加载描述"))

            val steelTargetBlock = object : Block(Material.ROCK) {}
            steelTargetBlock.setRegistryName("tacz", "steel_target")
            val steelTargetItemBlock = ItemBlock(steelTargetBlock)
            steelTargetItemBlock.setRegistryName(steelTargetBlock.registryName)

            val steelTargetLines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(
                ItemStack(steelTargetItemBlock),
                steelTargetItemBlock.registryName!!
            ) ?: emptyList()
            assertTrue(steelTargetLines.contains("§e动态钢靶（附加包）"))
            assertTrue(steelTargetLines.contains("§7来自附加 zip 包"))
            assertTrue(steelTargetLines.contains("[icon:tacz:block/steel_target,10]"))
            assertTrue(steelTargetLines.contains("[icon:tacz:block/steel_target,11]"))

            assertTrue(LegacyRichTooltipParser.parseLines(gunLines).requiresCustomRender)
            assertTrue(LegacyRichTooltipParser.parseLines(steelTargetLines).requiresCustomRender)
        } finally {
            deleteRecursively(gameRoot)
        }
    }

    // ─── §7 补充覆盖：item 分支 + 边界路径 ─────────────────

    @Test
    public fun `buildGeneratedLines should return null for unknown item type`() {
        val stack = ItemStack(Items.DIAMOND)
        val result = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(
            stack, stack.item.registryName!!
        )
        assertNull(result)
    }

    @Test
    public fun `buildGeneratedLines should handle LegacyAttachmentItem`() {
        val attachment = LegacyAttachmentItem(
            registryPath = "scope_hamr",
            slot = WeaponAttachmentSlot.SCOPE,
            attachmentId = "tacz:scope_hamr",
            iconTextureAssetPath = "assets/tacz/textures/attachment/slot/scope_hamr.png"
        )
        val stack = ItemStack(attachment)

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, attachment.registryName!!)
        assertNotNull(lines)
        assertTrue(lines!!.contains("[icon:tacz:attachment/slot/scope_hamr,11]"))
        assertTrue(lines.none { it == "§8────────" })
    }

    @Test
    public fun `buildGeneratedLines should handle LegacyAmmoItem non-box`() {
        val ammo = LegacyAmmoItem(
            registryPath = "ammo_9mm",
            ammoId = "tacz:9mm",
            roundsPerItem = 1,
            iconTextureAssetPath = "assets/tacz/textures/ammo/slot/9mm.png"
        )
        val stack = ItemStack(ammo)

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, ammo.registryName!!)
        assertNotNull(lines)
        assertTrue(lines!!.contains("[icon:tacz:ammo/slot/9mm,11]"))
        assertTrue(lines.contains("§8单次补充：+1"))
        assertTrue(lines.none { it.contains("剩余弹量") })
    }

    @Test
    public fun `buildGeneratedLines should handle LegacyAmmoItem with roundsPerItem zero coerced to 1`() {
        val ammo = LegacyAmmoItem(
            registryPath = "ammo_zero",
            ammoId = "tacz:zero",
            roundsPerItem = 0
        )
        val stack = ItemStack(ammo)

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, ammo.registryName!!)
        assertNotNull(lines)
        assertTrue(lines!!.any { it.contains("单次补充：+1") })
    }

    @Test
    public fun `buildGeneratedLines should show default capacity when ammo box has no tag`() {
        val ammoBox = LegacyAmmoBoxItem(
            registryPath = "ammo_box_no_tag",
            ammoId = "9mm",
            roundsPerUse = 12,
            capacity = 60
        )
        val stack = ItemStack(ammoBox)
        // no tagCompound set

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, ammoBox.registryName!!)
        assertNotNull(lines)
        assertTrue(lines!!.any { it.contains("剩余弹量：60/60") })
    }

    @Test
    public fun `buildGeneratedLines should show default capacity when tag missing remaining_rounds key`() {
        val ammoBox = LegacyAmmoBoxItem(
            registryPath = "ammo_box_missing_key",
            ammoId = "9mm",
            roundsPerUse = 12,
            capacity = 60
        )
        val stack = ItemStack(ammoBox)
        stack.tagCompound = NBTTagCompound()

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, ammoBox.registryName!!)
        assertNotNull(lines)
        assertTrue(lines!!.any { it.contains("剩余弹量：60/60") })
    }

    @Test
    public fun `composeSections should return secondary only when primary is empty`() {
        val gun = LegacyGunItem(registryPath = "empty_primary_gun")
        val stack = ItemStack(gun)
        // No index entry registered → primarySection will be empty; secondarySection has hints

        val lines = LegacyTooltipTakeoverEventHandler.buildGeneratedLines(stack, gun.registryName!!)
        assertNotNull(lines)
        assertTrue(lines!!.contains("§7左键：开火"))
        assertTrue(lines.contains("§7潜行 + 右键：换弹"))
        assertTrue(lines.none { it == "§8────────" })
    }

    @Test
    public fun `mergeTooltipLines should skip blank normalized additions`() {
        val base = mutableListOf("§eItem")
        LegacyTooltipTakeoverEventHandler.mergeTooltipLines(
            target = base,
            additions = listOf("§c§r", "  ", "§7新行")
        )
        // "§c§r" normalizes to empty/blank → skipped; "  " normalizes to blank → skipped
        assertTrue(base.size <= 3)
        assertTrue(base.contains("§7新行"))
    }

    @Test
    public fun `mergeTooltipLines should not add to empty target when additions are blank`() {
        val base = mutableListOf<String>()
        LegacyTooltipTakeoverEventHandler.mergeTooltipLines(
            target = base,
            additions = listOf("§r")
        )
        assertTrue(base.isEmpty())
    }

    @Test
    public fun `mergeTooltipLines should handle empty additions gracefully`() {
        val base = mutableListOf("first")
        LegacyTooltipTakeoverEventHandler.mergeTooltipLines(
            target = base,
            additions = emptyList()
        )
        assertEquals(1, base.size)
    }

    private fun stageTooltipDirectoryPack(packRoot: Path) {
        Files.createDirectories(packRoot)
        Files.write(
            packRoot.resolve("gunpack.meta.json"),
            "{\"namespace\":\"sample_pack\"}".toByteArray(StandardCharsets.UTF_8)
        )

        val gunIndex = packRoot
            .resolve("data")
            .resolve("tacz")
            .resolve("index")
            .resolve("guns")
            .resolve("ak47.json")
        Files.createDirectories(gunIndex.parent)
        Files.write(gunIndex, sampleGunIndexJson().toByteArray(StandardCharsets.UTF_8))

        val blockIndex = packRoot
            .resolve("data")
            .resolve("tacz")
            .resolve("index")
            .resolve("blocks")
            .resolve("gun_smith_table.json")
        Files.createDirectories(blockIndex.parent)
        Files.write(blockIndex, sampleBlockIndexJson().toByteArray(StandardCharsets.UTF_8))

        val zhCnLang = packRoot
            .resolve("assets")
            .resolve("tacz")
            .resolve("lang")
            .resolve("zh_cn.lang")
        Files.createDirectories(zhCnLang.parent)
        Files.write(zhCnLang, sampleZhCnLang().toByteArray(StandardCharsets.UTF_8))
    }

    private fun stageTooltipZipPackWithNestedRoot(zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            writeZipEntry(out, "sample_pack/gunpack.meta.json", "{\"namespace\":\"sample_pack\"}")
            writeZipEntry(out, "sample_pack/data/tacz/index/guns/ak47.json", sampleGunIndexJson())
            writeZipEntry(out, "sample_pack/data/tacz/index/blocks/gun_smith_table.json", sampleBlockIndexJson())
            writeZipEntry(out, "sample_pack/assets/tacz/lang/zh_cn.lang", sampleZhCnLang())
        }
    }

    private fun stageTooltipZipPackWithNestedRootConflicts(zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            writeZipEntry(out, "sample_pack/gunpack.meta.json", "{\"namespace\":\"sample_pack\"}")
            writeZipEntry(out, "sample_pack/data/tacz/index/guns/ak47.json", sampleConflictGunIndexPrimaryJson())
            writeZipEntry(out, "sample_pack/override/data/tacz/index/guns/ak47.json", sampleConflictGunIndexOverrideJson())
            writeZipEntry(out, "sample_pack/assets/tacz/lang/zh_cn.lang", sampleConflictZhCnLangPrimary())
            writeZipEntry(out, "sample_pack/override/assets/tacz/lang/zh_cn.lang", sampleConflictZhCnLangOverride())
        }
    }

    private fun stageTooltipAddonZipPackWithNestedRoot(zipPath: Path) {
        ZipOutputStream(Files.newOutputStream(zipPath)).use { out ->
            writeZipEntry(out, "addon_pack/gunpack.meta.json", "{\"namespace\":\"addon_pack\"}")
            writeZipEntry(out, "addon_pack/data/tacz/index/blocks/steel_target.json", sampleAddonBlockIndexJson())
            writeZipEntry(out, "addon_pack/assets/tacz/lang/zh_cn.lang", sampleAddonZhCnLang())
        }
    }

    private fun writeZipEntry(out: ZipOutputStream, entryPath: String, content: String) {
        out.putNextEntry(ZipEntry(entryPath))
        out.write(content.toByteArray(StandardCharsets.UTF_8))
        out.closeEntry()
    }

    private fun sampleGunIndexJson(): String =
        """
        {
          "name": "tacz.test.dynamic.ak47.name",
          "tooltip": "tacz.test.dynamic.ak47.tooltip"
        }
        """.trimIndent()

    private fun sampleBlockIndexJson(): String =
        """
        {
          "name": "tacz.test.dynamic.block.gun_smith_table.name",
          "tooltip": "tacz.test.dynamic.block.gun_smith_table.tooltip",
          "display": "tacz:gun_smith_table"
        }
        """.trimIndent()

    private fun sampleZhCnLang(): String =
        """
        tacz.test.dynamic.ak47.name=动态AK47（测试）
        tacz.test.dynamic.ak47.tooltip=[icon:tacz:gun/slot/test_ak47,12]\\n动态加载描述
        tacz.test.dynamic.block.gun_smith_table.name=动态枪匠工作台（测试）
        tacz.test.dynamic.block.gun_smith_table.tooltip=[icon:tacz:block/gun_smith_table,10]\\n来自动态枪包
        """.trimIndent()

        private fun sampleConflictGunIndexPrimaryJson(): String =
                """
                {
                    "name": "tacz.test.conflict.ak47.name",
                    "tooltip": "tacz.test.conflict.ak47.tooltip"
                }
                """.trimIndent()

        private fun sampleConflictGunIndexOverrideJson(): String =
                """
                {
                    "name": "tacz.test.conflict.ak47.override.name",
                    "tooltip": "tacz.test.conflict.ak47.override.tooltip"
                }
                """.trimIndent()

        private fun sampleConflictZhCnLangPrimary(): String =
                """
                tacz.test.conflict.ak47.name=首选AK47（冲突测试）
                tacz.test.conflict.ak47.tooltip=首选描述
                """.trimIndent()

        private fun sampleConflictZhCnLangOverride(): String =
                """
                tacz.test.conflict.ak47.name=覆盖AK47（冲突测试）
                tacz.test.conflict.ak47.tooltip=覆盖描述
                tacz.test.conflict.ak47.override.name=覆盖索引名称
                tacz.test.conflict.ak47.override.tooltip=覆盖索引描述
                """.trimIndent()

        private fun sampleAddonBlockIndexJson(): String =
            """
            {
              "name": "tacz.test.dynamic.block.steel_target.name",
              "tooltip": "tacz.test.dynamic.block.steel_target.tooltip",
              "display": "tacz:steel_target"
            }
            """.trimIndent()

        private fun sampleAddonZhCnLang(): String =
            """
            tacz.test.dynamic.block.steel_target.name=动态钢靶（附加包）
            tacz.test.dynamic.block.steel_target.tooltip=[icon:tacz:block/steel_target,10]\\n来自附加 zip 包
            """.trimIndent()

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
}
