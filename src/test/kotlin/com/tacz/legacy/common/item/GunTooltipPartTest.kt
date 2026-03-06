package com.tacz.legacy.common.item

import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import org.junit.Assert.assertEquals
import org.junit.BeforeClass
import org.junit.Test

class GunTooltipPartTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
        }
    }

    @Test
    fun `hide flags use upstream tag key and enum masks`() {
        assertEquals("HideFlags", GunTooltipPart.HIDE_FLAGS_TAG)
        GunTooltipPart.entries.forEachIndexed { index, part ->
            assertEquals(1 shl index, part.mask)
        }
    }

    @Test
    fun `hide flags round trip on stack nbt`() {
        val stack = ItemStack(ModernKineticGunItem())
        val mask = GunTooltipPart.DESCRIPTION.mask or GunTooltipPart.PACK_INFO.mask

        GunTooltipPart.setHideFlags(stack, mask)

        assertEquals(mask, GunTooltipPart.getHideFlags(stack))
    }
}
