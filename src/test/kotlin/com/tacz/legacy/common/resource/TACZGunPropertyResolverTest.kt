package com.tacz.legacy.common.resource

import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.item.AttachmentItem
import com.tacz.legacy.common.item.ModernKineticGunItem
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class TACZGunPropertyResolverTest {
    companion object {
        private val GUN_ID = ResourceLocation("demo", "test_shotgun")
        private val ATTACHMENT_ID = ResourceLocation("demo", "slug_mag")

        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
        }
    }

    private var gameDir: File? = null

    @After
    fun tearDown() {
        TACZGunPackRuntimeRegistry.clearForTests()
        gameDir?.deleteRecursively()
        gameDir = null
    }

    @Test
    fun `slug tag forces extended mag shotgun to single pellet`() {
        loadDemoPack()
        val gunItem = ModernKineticGunItem()
        val gunStack = createGunStack(gunItem)
        val attachment = createAttachmentStack()
        val gunData = requireNotNull(GunDataAccessor.getGunData(GUN_ID))

        assertTrue(gunItem.allowAttachment(gunStack, attachment))
        gunItem.installAttachment(gunStack, attachment)

        assertEquals(1, TACZGunPropertyResolver.resolveBulletAmount(gunStack, gunItem, gunData))
    }

    @Test
    fun `inaccuracy profile and heat modifiers follow runtime data`() {
        loadDemoPack()
        val gunItem = ModernKineticGunItem()
        val gunStack = createGunStack(gunItem)
        gunItem.installAttachment(gunStack, createAttachmentStack())
        gunItem.setHeatAmount(gunStack, 5f)
        val gunData = requireNotNull(GunDataAccessor.getGunData(GUN_ID))

        val profile = TACZGunPropertyResolver.resolveInaccuracyProfile(gunStack, gunItem)

        assertEquals(1.5f, profile.getValue("stand"), 0.0001f)
        assertEquals(0.375f, profile.getValue("aim"), 0.0001f)
        assertEquals(1.5f, TACZGunPropertyResolver.resolveHeatInaccuracyMultiplier(gunStack, gunItem, gunData), 0.0001f)
        assertEquals(0.875f, TACZGunPropertyResolver.resolveHeatRpmModifier(gunStack, gunItem, gunData), 0.0001f)
    }

    @Test
    fun `camera recoil applies attachment modifiers and crawl multiplier`() {
        loadDemoPack()
        val gunItem = ModernKineticGunItem()
        val gunStack = createGunStack(gunItem)
        gunItem.installAttachment(gunStack, createAttachmentStack())
        val gunData = requireNotNull(GunDataAccessor.getGunData(GUN_ID))

        val standingRecoil = TACZGunPropertyResolver.resolveCameraRecoil(
            stack = gunStack,
            iGun = gunItem,
            gunData = gunData,
            aimingProgress = 0f,
            isCrawling = false,
        )
        val crawlRecoil = TACZGunPropertyResolver.resolveCameraRecoil(
            stack = gunStack,
            iGun = gunItem,
            gunData = gunData,
            aimingProgress = 0f,
            isCrawling = true,
        )

        assertNotNull(standingRecoil)
        assertNotNull(crawlRecoil)
        assertEquals(0.8, standingRecoil!!.pitch!!.value(30.0), 0.0001)
        assertEquals(0.3, standingRecoil.yaw!!.value(30.0), 0.0001)
        assertEquals(0.4, crawlRecoil!!.pitch!!.value(30.0), 0.0001)
        assertEquals(0.15, crawlRecoil.yaw!!.value(30.0), 0.0001)
    }

    private fun createGunStack(gunItem: ModernKineticGunItem): ItemStack {
        return ItemStack(gunItem).apply {
            gunItem.setGunId(this, GUN_ID)
            gunItem.setFireMode(this, FireMode.SEMI)
            gunItem.setCurrentAmmoCount(this, 8)
            gunItem.setBulletInBarrel(this, true)
        }
    }

    private fun createAttachmentStack(): ItemStack {
        val attachmentItem = AttachmentItem()
        return ItemStack(attachmentItem).apply {
            attachmentItem.setAttachmentId(this, ATTACHMENT_ID)
        }
    }

    private fun loadDemoPack() {
        val root = Files.createTempDirectory("tacz-gun-props").toFile()
        gameDir = root
        val packRoot = File(root, "tacz/demo_pack")
        writeJson(File(packRoot, "gunpack.meta.json"), """
            {
              "namespace": "demo"
            }
        """.trimIndent())
        writeJson(File(packRoot, "assets/demo/gunpack_info.json"), """
            {
              "name": "Demo Pack",
              "version": "1.0.0"
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/index/guns/test_shotgun.json"), """
            {
              "name": "Demo Shotgun",
              "display": "demo:test_shotgun_display",
              "data": "demo:test_shotgun_data",
              "type": "shotgun",
              "item_type": "modern_kinetic"
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/data/guns/test_shotgun_data.json"), """
            {
              "ammo": "demo:test_shell",
              "ammo_amount": 8,
              "rpm": 120,
              "fire_mode": ["semi"],
              "bullet": {
                "damage": 8,
                "speed": 90,
                "bullet_amount": 8
              },
              "inaccuracy": {
                "stand": 2.0,
                "move": 3.0,
                "sneak": 1.5,
                "lie": 1.0,
                "aim": 0.25
              },
              "fire_mode_adjust": {
                "semi": {
                  "other_inaccuracy": 1.0,
                  "aim_inaccuracy": 0.5
                }
              },
              "heat": {
                "max": 10,
                "per_shot": 1,
                "min_inaccuracy": 1.0,
                "max_inaccuracy": 2.0,
                "min_rpm_mod": 1.0,
                "max_rpm_mod": 0.75
              },
              "recoil": {
                "pitch": [
                  {
                    "time": 0.0,
                    "value": [1.0, 1.0]
                  },
                  {
                    "time": 0.05,
                    "value": [0.0, 0.0]
                  }
                ],
                "yaw": [
                  {
                    "time": 0.0,
                    "value": [0.5, 0.5]
                  },
                  {
                    "time": 0.05,
                    "value": [0.0, 0.0]
                  }
                ]
              },
              "crawl_recoil_multiplier": 0.5,
              "allow_attachment_types": ["extended_mag"]
            }
        """.trimIndent())
        writeJson(File(packRoot, "assets/demo/display/guns/test_shotgun_display.json"), """
            {
              "hud": "demo:gun/hud/test_shotgun"
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/index/attachments/slug_mag.json"), """
            {
              "name": "Slug Mag",
              "display": "demo:slug_mag_display",
              "data": "demo:slug_mag_data",
              "type": "extended_mag"
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/data/attachments/slug_mag_data.json"), """
            {
              "weight": 0.25,
              "extended_mag_level": 3,
              "inaccuracy": {
                "multiplier": 0.5
              },
              "recoil": {
                "pitch": {
                  "multiplier": 0.8
                },
                "yaw": {
                  "multiplier": 0.6
                }
              }
            }
        """.trimIndent())
        writeJson(File(packRoot, "assets/demo/display/attachments/slug_mag_display.json"), """
            {
              "slot": "demo:attachment/slot/slug_mag"
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/index/ammo/test_shell.json"), """
            {
              "name": "Demo Shell",
              "display": "demo:test_shell_display",
              "stack_size": 16
            }
        """.trimIndent())
        writeJson(File(packRoot, "assets/demo/display/ammo/test_shell_display.json"), """
            {
              "name": "Demo Shell"
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/tacz_tags/allow_attachments/test_shotgun.json"), """
            [
              "#tacz:intrinsic/slug"
            ]
        """.trimIndent())
        writeJson(File(packRoot, "data/tacz/tacz_tags/intrinsic/slug.json"), """
            [
              "demo:slug_mag"
            ]
        """.trimIndent())

        TACZGunPackRuntimeRegistry.clearForTests()
        TACZGunPackRuntimeRegistry.reload(root)
    }

    private fun writeJson(target: File, content: String) {
        target.parentFile.mkdirs()
        target.writeText(content, StandardCharsets.UTF_8)
    }
}