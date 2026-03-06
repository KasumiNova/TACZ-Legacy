package com.tacz.legacy.common.application.gunsmith

import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.common.item.LegacyItems
import com.tacz.legacy.common.resource.DefaultGunPackExporter
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LegacyGunSmithingRuntimeTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            TACZGunPackRuntimeRegistry.clearForTests()
        }
    }

    @Test
    fun `default pack recipes parse tag materials and gun result groups`() {
        withDefaultPackSnapshot {
            val recipe = LegacyGunSmithingRuntime.recipes().values.firstOrNull { parsed ->
                val gun = parsed.result.item as? IGun ?: return@firstOrNull false
                gun.getGunId(parsed.result) == ResourceLocation("tacz", "ak47")
            }
            assertNotNull(recipe)
            recipe!!
            assertEquals(ResourceLocation("tacz", "rifle"), recipe.group)
            assertEquals(3, recipe.materials.size)
            assertTrue(recipe.materials.all { it.ingredient.matchingStacks.isNotEmpty() })
            assertTrue(recipe.materials[0].ingredient.matchingStacks.any { stack -> stack.item == net.minecraft.init.Items.IRON_INGOT })
        }
    }

    @Test
    fun `gun recipe attachments fall back to direct attachment slots when allow tags are absent`() {
      withCustomSnapshot(includeAllowTags = false) {
            val recipes = LegacyGunSmithingRuntime.recipes()
            val rifleRecipe = recipes.values.first { parsed ->
                val gun = parsed.result.item as? IGun ?: return@first false
                gun.getGunId(parsed.result) == ResourceLocation("demo", "test_rifle")
            }
            assertEquals(ResourceLocation("tacz", "rifle"), rifleRecipe.group)
            assertEquals(
                ResourceLocation("demo", "test_scope"),
                LegacyItems.MODERN_KINETIC_GUN.getAttachmentId(rifleRecipe.result, AttachmentType.SCOPE),
            )
            assertTrue(rifleRecipe.result.tagCompound?.hasKey("AttachmentSCOPE") == true)
      }
    }

    @Test
    fun `by-hand filters use real ammo and attachment compatibility`() {
      withCustomSnapshot(includeAllowTags = true) {
            val heldGun = ItemStack(LegacyItems.MODERN_KINETIC_GUN)
            LegacyItems.MODERN_KINETIC_GUN.setGunId(heldGun, ResourceLocation("demo", "test_rifle"))
            val visible = LegacyGunSmithingRuntime.visibleRecipes(
                blockId = ResourceLocation("demo", "demo_table"),
                selectedTab = null,
                selectedNamespaces = emptySet(),
                searchText = "",
                heldStack = heldGun,
                byHandOnly = true,
            )
            assertTrue(visible.any { it.result.item == LegacyItems.ATTACHMENT })
            assertTrue(visible.any { it.result.item == LegacyItems.AMMO })
            assertFalse(visible.any { it.result.item == LegacyItems.MODERN_KINETIC_GUN })
        }
    }

    private fun withDefaultPackSnapshot(block: () -> Unit) {
        val gameDir = Files.createTempDirectory("tacz-gunsmith-default").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()
            DefaultGunPackExporter.exportIfNeeded(gameDir)
            TACZGunPackRuntimeRegistry.getSnapshot()
            block()
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            gameDir.deleteRecursively()
        }
    }

    private fun withCustomSnapshot(includeAllowTags: Boolean, block: () -> Unit) {
        val gameDir = Files.createTempDirectory("tacz-gunsmith-custom").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()
            val root = File(gameDir, "tacz").apply { mkdirs() }
        createGunsmithDemoPack(File(root, "gunsmith_demo.zip"), includeAllowTags)
            TACZGunPackRuntimeRegistry.reload(gameDir)
            block()
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            gameDir.deleteRecursively()
        }
    }

    private fun createGunsmithDemoPack(target: File, includeAllowTags: Boolean) {
        FileOutputStream(target).use { output ->
            ZipOutputStream(output).use { zip ->
                writeEntry(zip, "gunpack.meta.json", """
                    {
                      "namespace": "demo"
                    }
                """.trimIndent())
                writeEntry(zip, "assets/demo/gunpack_info.json", """
                    {
                      "name": "demo.pack.name",
                      "desc": "demo.pack.desc",
                      "version": "1.0.0",
                      "license": "MIT",
                      "authors": ["Copilot"],
                      "date": "2026-03-06"
                    }
                """.trimIndent())
                writeEntry(zip, "assets/demo/lang/en_us.json", """
                    {
                      "demo.pack.name": "Demo Pack",
                      "demo.pack.desc": "Demo gunsmith pack",
                      "demo.gun.test_rifle.name": "Demo Rifle",
                      "demo.attachment.test_scope.name": "Demo Scope",
                      "demo.ammo.test_round.name": "Demo Round",
                      "demo.block.demo_table.name": "Demo Table",
                      "demo.tab.rifle": "Rifle",
                      "demo.tab.scope": "Scope",
                      "demo.tab.ammo": "Ammo"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/guns/test_rifle.json", """
                    {
                      "name": "demo.gun.test_rifle.name",
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
                writeEntry(zip, "data/demo/index/attachments/test_scope.json", """
                    {
                      "name": "demo.attachment.test_scope.name",
                      "data": "demo:test_scope_data",
                      "type": "scope"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/attachments/test_scope_data.json", "{}")
                writeEntry(zip, "data/demo/index/ammo/test_round.json", """
                    {
                      "name": "demo.ammo.test_round.name",
                      "stack_size": 16
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/blocks/demo_table.json", """
                    {
                      "name": "demo.block.demo_table.name",
                      "data": "demo:demo_table_data",
                      "id": "tacz:gun_smith_table"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/blocks/demo_table_data.json", """
                    {
                      "filter": "demo:default",
                      "tabs": [
                        {
                          "id": "tacz:rifle",
                          "name": "demo.tab.rifle",
                          "icon": {
                            "item": "tacz:modern_kinetic_gun",
                            "nbt": {
                              "GunId": "demo:test_rifle"
                            }
                          }
                        },
                        {
                          "id": "tacz:scope",
                          "name": "demo.tab.scope",
                          "icon": {
                            "item": "tacz:attachment",
                            "nbt": {
                              "AttachmentId": "demo:test_scope"
                            }
                          }
                        },
                        {
                          "id": "tacz:ammo",
                          "name": "demo.tab.ammo",
                          "icon": {
                            "item": "tacz:ammo",
                            "nbt": {
                              "AmmoId": "demo:test_round"
                            }
                          }
                        }
                      ]
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/recipe_filters/default.json", """
                    {
                      "whitelist": [],
                      "blacklist": []
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/recipes/craft_scope.json", """
                    {
                      "type": "tacz:gun_smith_table_crafting",
                      "materials": [
                        {
                          "item": {
                            "item": "minecraft:stick"
                          },
                          "count": 2
                        }
                      ],
                      "result": {
                        "type": "attachment",
                        "id": "demo:test_scope"
                      }
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/recipes/craft_rifle.json", """
                    {
                      "type": "tacz:gun_smith_table_crafting",
                      "materials": [
                        {
                          "item": {
                            "item": "minecraft:stick"
                          },
                          "count": 4
                        }
                      ],
                      "result": {
                        "type": "gun",
                        "id": "demo:test_rifle",
                        "attachments": {
                          "scope": "demo:test_scope"
                        }
                      }
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/recipes/craft_ammo.json", """
                    {
                      "type": "tacz:gun_smith_table_crafting",
                      "materials": [
                        {
                          "item": {
                            "tag": "forge:ingots/iron"
                          },
                          "count": 1
                        }
                      ],
                      "result": {
                        "type": "ammo",
                        "id": "demo:test_round"
                      }
                    }
                """.trimIndent())
                    if (includeAllowTags) {
                      writeEntry(zip, "data/demo/tacz_tags/allow_attachments/test_rifle.json", """
                        ["demo:test_scope"]
                      """.trimIndent())
                    }
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
