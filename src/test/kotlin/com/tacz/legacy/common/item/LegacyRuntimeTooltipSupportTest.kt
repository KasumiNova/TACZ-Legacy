package com.tacz.legacy.common.item

import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.common.config.LegacyConfigManager
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

class LegacyRuntimeTooltipSupportTest {
    companion object {
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
        LegacyConfigManager.client.enableTaczIdInTooltip = true
    }

    @Test
    fun `gun tooltip resolves runtime text hide flags`() {
        loadDemoPack()
        val gunItem = ModernKineticGunItem()
        val stack = ItemStack(gunItem)
        val gunId = ResourceLocation("demo", "test_rifle")

        gunItem.setGunId(stack, gunId)
        gunItem.setCurrentAmmoCount(stack, 12)
        gunItem.setBulletInBarrel(stack, true)
        gunItem.setFireMode(stack, FireMode.AUTO)

        assertEquals("Demo Rifle", LegacyRuntimeTooltipSupport.resolveDisplayName(stack, "fallback"))
        assertTrue(LegacyRuntimeTooltipSupport.isContinuousBurst(gunId))

        val tooltip = mutableListOf<String>()
        LegacyRuntimeTooltipSupport.appendTooltip(stack, tooltip, advanced = true)

        assertTrue(tooltip.any { it.contains("Reliable demo rifle.") })
        assertTrue(tooltip.any { it.contains("Demo Round") && it.contains("13/31") })
        assertTrue(tooltip.any { it.contains("Fire Mode") && it.contains("AUTO") })
        assertTrue(tooltip.any { it.contains("Damage") && it.contains("8") })
        assertTrue(tooltip.any { it.contains("AP Ratio") && it.contains("25") })
        assertTrue(tooltip.any { it.contains("Headshot") && it.contains("1.5") })
        assertTrue(tooltip.any { it.contains("Demo Pack") })
        assertTrue(tooltip.any { it.contains("demo:test_rifle") })
    }

    @Test
    fun `hide flags suppress matching gun tooltip sections`() {
        loadDemoPack()
        val gunItem = ModernKineticGunItem()
        val stack = ItemStack(gunItem)
        val gunId = ResourceLocation("demo", "test_rifle")

        gunItem.setGunId(stack, gunId)
        gunItem.setCurrentAmmoCount(stack, 12)
        gunItem.setBulletInBarrel(stack, true)
        gunItem.setFireMode(stack, FireMode.AUTO)
        GunTooltipPart.setHideFlags(stack, GunTooltipPart.DESCRIPTION.mask or GunTooltipPart.PACK_INFO.mask)

        val tooltip = mutableListOf<String>()
        LegacyRuntimeTooltipSupport.appendTooltip(stack, tooltip, advanced = true)

        assertFalse(tooltip.any { it.contains("Reliable demo rifle.") })
        assertFalse(tooltip.any { it.contains("Demo Pack") })
        assertTrue(tooltip.any { it.contains("demo:test_rifle") })
    }

    @Test
    fun `ammo box tooltip shows count usage and runtime id`() {
        loadDemoPack()
        val ammoBoxItem = AmmoBoxItem()
        val stack = ItemStack(ammoBoxItem)
        val ammoId = ResourceLocation("demo", "test_round")

        ammoBoxItem.setAmmoId(stack, ammoId)
        ammoBoxItem.setAmmoCount(stack, 42)

        val tooltip = mutableListOf<String>()
        LegacyRuntimeTooltipSupport.appendTooltip(stack, tooltip, advanced = true)

        assertTrue(tooltip.any { it.contains("42") })
        assertTrue(tooltip.any { it.contains("Deposit Ammo") || it.contains("存入弹药") })
        assertTrue(tooltip.any { it.contains("demo:test_round") })
    }

    private fun loadDemoPack() {
        val root = Files.createTempDirectory("tacz-tooltip-runtime").toFile()
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
        writeJson(File(packRoot, "data/demo/index/guns/test_rifle.json"), """
            {
              "name": "Demo Rifle",
              "tooltip": "Reliable demo rifle.",
              "display": "demo:test_rifle_display",
              "data": "demo:test_rifle_data",
              "type": "rifle",
              "item_type": "modern_kinetic"
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/data/guns/test_rifle_data.json"), """
            {
              "ammo": "demo:test_round",
              "ammo_amount": 30,
              "rpm": 600,
              "weight": 3.4,
              "aim_time": 0.15,
              "draw_time": 0.2,
              "put_away_time": 0.2,
              "bolt": "closed_bolt",
              "fire_mode": ["auto", "burst"],
              "burst_data": {
                "continuous_shoot": true,
                "count": 3,
                "min_interval": 0.1
              },
              "bullet": {
                "damage": 8,
                "extra_damage": {
                  "armor_ignore": 0.25,
                  "head_shot_multiplier": 1.5
                }
              },
              "reload": {
                "type": "magazine"
              },
              "allow_attachment_types": []
            }
        """.trimIndent())
        writeJson(File(packRoot, "data/demo/index/ammo/test_round.json"), """
            {
              "name": "Demo Round",
              "tooltip": "Standard range ammo.",
              "display": "demo:test_round_display",
              "stack_size": 64
            }
        """.trimIndent())
        writeJson(File(packRoot, "assets/demo/display/guns/test_rifle_display.json"), """
            {
              "hud": "demo:gun/hud/test_rifle"
            }
        """.trimIndent())

        TACZGunPackRuntimeRegistry.clearForTests()
        TACZGunPackRuntimeRegistry.reload(root)
        LegacyConfigManager.client.enableTaczIdInTooltip = true
    }

    private fun writeJson(target: File, content: String) {
        target.parentFile.mkdirs()
        target.writeText(content, StandardCharsets.UTF_8)
    }
}
