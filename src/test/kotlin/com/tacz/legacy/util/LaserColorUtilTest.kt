package com.tacz.legacy.util

import com.google.gson.Gson
import com.tacz.legacy.client.resource.pojo.display.LaserConfig
import com.tacz.legacy.common.item.LegacyItems
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class LaserColorUtilTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
        }
    }

    private val gson = Gson()

    @Test
    fun `gun custom laser color overrides config default`() {
        val config = gson.fromJson("""{"default_color":"0x00FF00"}""", LaserConfig::class.java)
        val stack = ItemStack(LegacyItems.MODERN_KINETIC_GUN)

        LegacyItems.MODERN_KINETIC_GUN.setLaserColor(stack, 0x123456)

        assertEquals(0x123456, LaserColorUtil.getLaserColor(stack, config))
    }

    @Test
    fun `attachment uses config default when custom color absent`() {
        val config = gson.fromJson("""{"default_color":"0x00FF00"}""", LaserConfig::class.java)
        val stack = ItemStack(LegacyItems.ATTACHMENT)

        assertEquals(0x00FF00, LaserColorUtil.getLaserColor(stack, config))
    }

    @Test
    fun `invalid laser config color falls back to white`() {
        val config = gson.fromJson("""{"default_color":"not-a-color"}""", LaserConfig::class.java)

        assertEquals(0xFFFFFF, config.defaultColor)
    }
}