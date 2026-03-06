package com.tacz.legacy.common.item

import com.tacz.legacy.common.registry.LegacyCreativeTabs
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.common.resource.TACZRuntimeSnapshot
import com.tacz.legacy.common.resource.DefaultGunPackExporter
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import net.minecraft.util.NonNullList
import net.minecraft.util.ResourceLocation
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RuntimeBackedItemPresentationTest {
    companion object {
        private lateinit var previousLocale: Locale

        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
            previousLocale = Locale.getDefault()
            Locale.setDefault(Locale.US)
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            Locale.setDefault(previousLocale)
            TACZGunPackRuntimeRegistry.clearForTests()
        }
    }

    @Test
    fun `default pack localizes runtime-backed item names and tooltips`() {
        withDefaultPackSnapshot { snapshot ->
            assertEquals(
                "AKM",
                TACZGunPackPresentation.localizedGunName(snapshot, ResourceLocation("tacz", "ak47"), localeCandidates = listOf("en_us")),
            )
            assertEquals(
                "TAC Zero Default Pack",
                TACZGunPackPresentation.localizedPackName(snapshot, ResourceLocation("tacz", "ak47"), localeCandidates = listOf("en_us")),
            )
            assertNotNull(TACZGunPackPresentation.resolveGunDisplayId(snapshot, ResourceLocation("tacz", "ak47")))

            val gunStacks = NonNullList.create<ItemStack>()
            LegacyItems.MODERN_KINETIC_GUN.getSubItems(LegacyCreativeTabs.GUNS, gunStacks)
            val akStack = gunStacks.first { LegacyItems.MODERN_KINETIC_GUN.getGunId(it) == ResourceLocation("tacz", "ak47") }
            assertEquals("AKM", LegacyItems.MODERN_KINETIC_GUN.getItemStackDisplayName(akStack))

            val gunTooltip = mutableListOf<String>()
            LegacyItems.MODERN_KINETIC_GUN.addInformation(akStack, null, gunTooltip, ITooltipFlag.TooltipFlags.ADVANCED)
            assertTrue(gunTooltip.isNotEmpty())
            assertTrue(gunTooltip.any { it.contains("TAC Zero Default Pack") })
            assertTrue(gunTooltip.any { it.contains("tacz:ak47") })

            val ammoStacks = NonNullList.create<ItemStack>()
            LegacyItems.AMMO.getSubItems(LegacyCreativeTabs.AMMO, ammoStacks)
            val ammoStack = ammoStacks.first { LegacyItems.AMMO.getAmmoId(it) == ResourceLocation("tacz", "762x39") }
            assertEquals("§e7.62x39mm Bullet", LegacyItems.AMMO.getItemStackDisplayName(ammoStack))

            val attachmentStacks = NonNullList.create<ItemStack>()
            LegacyItems.ATTACHMENT.getSubItems(LegacyCreativeTabs.PARTS, attachmentStacks)
            val attachmentStack = attachmentStacks.first { LegacyItems.ATTACHMENT.getAttachmentId(it) == ResourceLocation("tacz", "scope_acog_ta31") }
            assertEquals("§9TA31 2x ACOG", LegacyItems.ATTACHMENT.getItemStackDisplayName(attachmentStack))

            val blockStacks = NonNullList.create<ItemStack>()
            LegacyItems.GUN_SMITH_TABLE.getSubItems(LegacyCreativeTabs.DECORATION, blockStacks)
            val tableStack = blockStacks.first { LegacyItems.GUN_SMITH_TABLE.getBlockId(it) == ResourceLocation("tacz", "gun_smith_table") }
            assertEquals("TaCZ Gun Smith Table", LegacyItems.GUN_SMITH_TABLE.getItemStackDisplayName(tableStack))

            val blockTooltip = mutableListOf<String>()
            LegacyItems.GUN_SMITH_TABLE.addInformation(tableStack, null, blockTooltip, ITooltipFlag.TooltipFlags.ADVANCED)
            assertTrue(blockTooltip.any { it.contains("Where it all began") })
            assertTrue(blockTooltip.any { it.contains("Pistol") })
            assertTrue(blockTooltip.any { it.contains("TAC Zero Default Pack") })
            assertTrue(blockTooltip.any { it.contains("Filter: tacz:default") })
        }
    }

    @Test
    fun `custom pack recipes filters and tags feed presentation helpers`() {
        withCustomSnapshot { snapshot ->
            val gunId = ResourceLocation("demo", "test_rifle")
            val attachmentId = ResourceLocation("demo", "test_scope")
            val blockId = ResourceLocation("demo", "demo_table")

            assertTrue(TACZGunPackPresentation.allowsAttachment(snapshot, gunId, attachmentId))
            assertEquals(1, TACZGunPackPresentation.compatibleGunCount(snapshot, attachmentId))
            assertEquals(1, TACZGunPackPresentation.visibleRecipeCount(snapshot, blockId))
            assertEquals(
                "Demo Table",
                TACZGunPackPresentation.localizedBlockName(snapshot, blockId, localeCandidates = listOf("en_us")),
            )

            val attachmentStack = ItemStack(LegacyItems.ATTACHMENT)
            LegacyItems.ATTACHMENT.setAttachmentId(attachmentStack, attachmentId)
            val tooltip = mutableListOf<String>()
            LegacyItems.ATTACHMENT.addInformation(attachmentStack, null, tooltip, ITooltipFlag.TooltipFlags.NORMAL)
            assertTrue(tooltip.any { it.contains("Compatible guns: 1") })
        }
    }

    @Test
    fun `workbench preview falls back cleanly when no external recipes are loaded`() {
        withDefaultPackSnapshot { snapshot ->
            val blockId = ResourceLocation("tacz", "gun_smith_table")
            assertTrue(TACZGunPackPresentation.visibleRecipeCount(snapshot, blockId) >= 0)
            assertTrue(TACZGunPackPresentation.localizedWorkbenchTabs(snapshot, blockId).isNotEmpty())
            assertEquals(0, TACZGunPackPresentation.visibleRecipeCount(snapshot, ResourceLocation("tacz", "missing_table")))
        }
    }

    private fun withDefaultPackSnapshot(block: (TACZRuntimeSnapshot) -> Unit) {
        val gameDir = Files.createTempDirectory("tacz-present-default").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()
            DefaultGunPackExporter.exportIfNeeded(gameDir)
            block(TACZGunPackRuntimeRegistry.getSnapshot())
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            gameDir.deleteRecursively()
        }
    }

    private fun withCustomSnapshot(block: (TACZRuntimeSnapshot) -> Unit) {
        val gameDir = Files.createTempDirectory("tacz-present-custom").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()
            val root = File(gameDir, "tacz").apply { mkdirs() }
            createPresentationDemoPack(File(root, "presentation_demo.zip"))
            val snapshot = TACZGunPackRuntimeRegistry.reload(gameDir)
            block(snapshot)
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            gameDir.deleteRecursively()
        }
    }

    private fun createPresentationDemoPack(target: File) {
        FileOutputStream(target).use { output ->
            ZipOutputStream(output).use { zip ->
                writeEntry(zip, "gunpack.meta.json", """
                    {
                      "namespace": "demo"
                    }
                """.trimIndent())
                writeEntry(zip, "assets/demo/gunpack_info.json", """
                    {
                      "name": "pack.demo.name"
                    }
                """.trimIndent())
                writeEntry(zip, "assets/demo/lang/en_us.json", """
                    {
                      "pack.demo.name": "Demo Pack",
                      "demo.gun.test_rifle.name": "Demo Rifle",
                      "demo.attachment.test_scope.name": "Demo Scope",
                      "demo.block.demo_table.name": "Demo Table",
                      "demo.block.demo_table.desc": "Demo table tooltip",
                      "demo.tab.primary": "Primary"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/guns/test_rifle.json", """
                    {
                      "name": "demo.gun.test_rifle.name",
                      "display": "demo:test_rifle_display",
                      "data": "demo:test_rifle_data",
                      "type": "rifle",
                      "item_type": "modern_kinetic"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/guns/test_rifle_data.json", """
                    {
                      "ammo": "demo:test_round",
                      "ammo_amount": 30,
                      "rpm": 600,
                      "aim_time": 0.15,
                      "allow_attachment_types": ["scope"]
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/ammo/test_round.json", """
                    {
                      "name": "demo.ammo.test_round.name",
                      "display": "demo:test_round_display",
                      "stack_size": 16
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/attachments/test_scope.json", """
                    {
                      "name": "demo.attachment.test_scope.name",
                      "display": "demo:test_scope_display",
                      "data": "demo:test_scope_data",
                      "type": "scope"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/attachments/test_scope_data.json", "{}")
                writeEntry(zip, "data/demo/index/blocks/demo_table.json", """
                    {
                      "name": "demo.block.demo_table.name",
                      "display": "demo:demo_table_display",
                      "data": "demo:demo_table_data",
                      "tooltip": "demo.block.demo_table.desc",
                      "id": "tacz:gun_smith_table"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/blocks/demo_table_data.json", """
                    {
                      "filter": "demo:default",
                      "tabs": [
                        {
                          "id": "demo:primary",
                          "name": "demo.tab.primary",
                          "icon": {
                            "item": "tacz:modern_kinetic_gun",
                            "nbt": {
                              "GunId": "demo:test_rifle"
                            }
                          }
                        }
                      ]
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/recipe_filters/default.json", """
                    {
                      "whitelist": ["^demo:test_recipe$"],
                      "blacklist": []
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/recipes/test_recipe.json", """
                    {
                      "type": "tacz:gun_smith_table_crafting",
                      "result": {
                        "group": "demo:primary"
                      }
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/tacz_tags/allow_attachments/test_rifle.json", """
                    ["demo:test_scope"]
                """.trimIndent())
                writeEntry(zip, "assets/demo/display/guns/test_rifle_display.json", "{}")
                writeEntry(zip, "assets/demo/display/ammo/test_round_display.json", "{}")
                writeEntry(zip, "assets/demo/display/attachments/test_scope_display.json", "{}")
                writeEntry(zip, "assets/demo/display/blocks/demo_table_display.json", "{}")
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
