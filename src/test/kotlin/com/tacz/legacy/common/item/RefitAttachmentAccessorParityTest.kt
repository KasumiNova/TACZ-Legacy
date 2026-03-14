package com.tacz.legacy.common.item

import com.tacz.legacy.api.item.attachment.AttachmentType
import com.tacz.legacy.common.resource.TACZAttachmentLaserConfigDefinition
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.common.resource.TACZRuntimeSnapshot
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

class RefitAttachmentAccessorParityTest {
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
    fun `attachment zoom number and laser color round trip through NBT`() {
        val stack = ItemStack(LegacyItems.ATTACHMENT)

        assertEquals(0, LegacyItems.ATTACHMENT.getZoomNumber(stack))
        assertFalse(LegacyItems.ATTACHMENT.hasCustomLaserColor(stack))
        assertEquals(0xFF0000, LegacyItems.ATTACHMENT.getLaserColor(stack))

        LegacyItems.ATTACHMENT.setZoomNumber(stack, 2)
        LegacyItems.ATTACHMENT.setLaserColor(stack, 0x112233)

        assertEquals(2, LegacyItems.ATTACHMENT.getZoomNumber(stack))
        assertTrue(LegacyItems.ATTACHMENT.hasCustomLaserColor(stack))
        assertEquals(0x112233, LegacyItems.ATTACHMENT.getLaserColor(stack))
    }

    @Test
    fun `aiming zoom uses installed scope zoom index then falls back to gun iron zoom`() {
        withCustomSnapshot { snapshot ->
            val gunStack = ItemStack(LegacyItems.MODERN_KINETIC_GUN)
            LegacyItems.MODERN_KINETIC_GUN.setGunId(gunStack, ResourceLocation("demo", "refit_rifle"))

            assertEquals(1.5f, LegacyItems.MODERN_KINETIC_GUN.getAimingZoom(gunStack), 0.0001f)

            val scopeStack = ItemStack(LegacyItems.ATTACHMENT)
            LegacyItems.ATTACHMENT.setAttachmentId(scopeStack, ResourceLocation("demo", "refit_scope"))
            LegacyItems.ATTACHMENT.setZoomNumber(scopeStack, 1)
            LegacyItems.MODERN_KINETIC_GUN.installAttachment(gunStack, scopeStack)

            assertEquals(ResourceLocation("demo", "refit_scope"), LegacyItems.MODERN_KINETIC_GUN.getAttachmentId(gunStack, AttachmentType.SCOPE))
            assertEquals(4.0f, LegacyItems.MODERN_KINETIC_GUN.getAimingZoom(gunStack), 0.0001f)

            LegacyItems.MODERN_KINETIC_GUN.unloadAttachment(gunStack, AttachmentType.SCOPE)

            val rootTag = requireNotNull(gunStack.tagCompound)
            assertTrue(rootTag.hasKey("AttachmentSCOPE", 10))
            assertTrue(LegacyItems.MODERN_KINETIC_GUN.getAttachment(gunStack, AttachmentType.SCOPE).isEmpty)
            assertEquals(1.5f, LegacyItems.MODERN_KINETIC_GUN.getAimingZoom(gunStack), 0.0001f)

            val laserConfig = TACZGunPackPresentation.resolveAttachmentLaserConfig(snapshot, ResourceLocation("demo", "refit_scope"))
            assertEquals(
                TACZAttachmentLaserConfigDefinition(
                    defaultColor = 0x00FF00,
                    canEdit = true,
                    length = 3,
                    width = 0.01f,
                    thirdPersonLength = 2.0f,
                    thirdPersonWidth = 0.008f,
                ),
                laserConfig,
            )
        }
    }

    @Test
    fun `builtin scope attachment id and aiming zoom resolve from runtime data`() {
        withCustomSnapshot {
            val gunStack = ItemStack(LegacyItems.MODERN_KINETIC_GUN)
            LegacyItems.MODERN_KINETIC_GUN.setGunId(gunStack, ResourceLocation("demo", "builtin_rifle"))

            assertEquals(
                ResourceLocation("demo", "builtin_scope"),
                LegacyItems.MODERN_KINETIC_GUN.getBuiltInAttachmentId(gunStack, AttachmentType.SCOPE),
            )

            val builtinAttachment = LegacyItems.MODERN_KINETIC_GUN.getBuiltinAttachment(gunStack, AttachmentType.SCOPE)
            assertFalse(builtinAttachment.isEmpty)
            assertEquals(
                ResourceLocation("demo", "builtin_scope"),
                LegacyItems.ATTACHMENT.getAttachmentId(builtinAttachment),
            )
            assertEquals(3.25f, LegacyItems.MODERN_KINETIC_GUN.getAimingZoom(gunStack), 0.0001f)
        }
    }

    private fun withCustomSnapshot(block: (TACZRuntimeSnapshot) -> Unit) {
        val gameDir = Files.createTempDirectory("tacz-refit-accessor").toFile()
        try {
            TACZGunPackRuntimeRegistry.clearForTests()
            val root = File(gameDir, "tacz").apply { mkdirs() }
            createRefitDemoPack(File(root, "refit_demo.zip"))
            val snapshot = TACZGunPackRuntimeRegistry.reload(gameDir)
            block(snapshot)
        } finally {
            TACZGunPackRuntimeRegistry.clearForTests()
            gameDir.deleteRecursively()
        }
    }

    private fun createRefitDemoPack(target: File) {
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
                      "pack.demo.name": "Refit Demo Pack"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/guns/refit_rifle.json", """
                    {
                      "name": "demo.gun.refit_rifle.name",
                      "display": "demo:refit_rifle_display",
                      "data": "demo:refit_rifle_data",
                      "type": "rifle",
                      "item_type": "modern_kinetic"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/guns/refit_rifle_data.json", """
                    {
                      "ammo": "demo:test_round",
                      "ammo_amount": 30,
                      "rpm": 600,
                      "aim_time": 0.15,
                      "allow_attachment_types": ["scope"]
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/guns/builtin_rifle.json", """
                    {
                      "name": "demo.gun.builtin_rifle.name",
                      "display": "demo:builtin_rifle_display",
                      "data": "demo:builtin_rifle_data",
                      "type": "rifle",
                      "item_type": "modern_kinetic"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/guns/builtin_rifle_data.json", """
                    {
                      "ammo": "demo:test_round",
                      "ammo_amount": 20,
                      "rpm": 480,
                      "aim_time": 0.2,
                      "allow_attachment_types": [],
                      "builtin_attachments": {
                        "scope": "demo:builtin_scope"
                      }
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/ammo/test_round.json", """
                    {
                      "name": "demo.ammo.test_round.name",
                      "display": "demo:test_round_display",
                      "stack_size": 16
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/index/attachments/refit_scope.json", """
                    {
                      "name": "demo.attachment.refit_scope.name",
                      "display": "demo:refit_scope_display",
                      "data": "demo:refit_scope_data",
                      "type": "scope"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/attachments/refit_scope_data.json", "{}")
                writeEntry(zip, "data/demo/index/attachments/builtin_scope.json", """
                    {
                      "name": "demo.attachment.builtin_scope.name",
                      "display": "demo:builtin_scope_display",
                      "data": "demo:builtin_scope_data",
                      "type": "scope"
                    }
                """.trimIndent())
                writeEntry(zip, "data/demo/data/attachments/builtin_scope_data.json", "{}")
                writeEntry(zip, "data/demo/tacz_tags/allow_attachments/refit_rifle.json", """
                    ["demo:refit_scope"]
                """.trimIndent())
                writeEntry(zip, "assets/demo/display/guns/refit_rifle_display.json", """
                    {
                      "iron_zoom": 1.5
                    }
                """.trimIndent())
                writeEntry(zip, "assets/demo/display/guns/builtin_rifle_display.json", "{}")
                writeEntry(zip, "assets/demo/display/ammo/test_round_display.json", "{}")
                writeEntry(zip, "assets/demo/display/attachments/refit_scope_display.json", """
                    {
                      "zoom": [2.0, 4.0],
                      "laser": {
                        "default_color": "0x00FF00",
                        "can_edit": true,
                        "length": 3,
                        "width": 0.01
                      }
                    }
                """.trimIndent())
                writeEntry(zip, "assets/demo/display/attachments/builtin_scope_display.json", """
                    {
                      "zoom": [3.25]
                    }
                """.trimIndent())
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }
}
