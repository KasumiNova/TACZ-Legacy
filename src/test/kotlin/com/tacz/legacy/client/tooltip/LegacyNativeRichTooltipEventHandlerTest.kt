package com.tacz.legacy.client.tooltip

import net.minecraft.client.gui.FontRenderer
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraftforge.client.event.RenderTooltipEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test

public class LegacyNativeRichTooltipEventHandlerTest {

    public companion object {
        @JvmStatic
        @BeforeClass
        public fun bootstrapMinecraftStatics() {
            Bootstrap.register()
        }
    }

    @Test
    public fun `shouldInterceptTooltip should return false for null`() {
        assertFalse(LegacyNativeRichTooltipEventHandler.shouldInterceptTooltip(null))
    }

    @Test
    public fun `shouldInterceptTooltip should return false for empty list`() {
        assertFalse(LegacyNativeRichTooltipEventHandler.shouldInterceptTooltip(emptyList()))
    }

    @Test
    public fun `shouldInterceptTooltip should return false when no icon token found`() {
        assertFalse(LegacyNativeRichTooltipEventHandler.shouldInterceptTooltip(
            listOf("§eWeapon Name", "§7Damage: 7.5")
        ))
    }

    @Test
    public fun `shouldInterceptTooltip should return true when icon token present`() {
        assertTrue(LegacyNativeRichTooltipEventHandler.shouldInterceptTooltip(
            listOf("§eWeapon", "[icon:tacz:gun/ak47,11]")
        ))
    }

    @Test
    public fun `shouldInterceptTooltip should detect icon in any line position`() {
        assertTrue(LegacyNativeRichTooltipEventHandler.shouldInterceptTooltip(
            listOf("First line", "Second line", "[icon:tacz:x,10] third")
        ))
    }

    @Test
    public fun `shouldInterceptTooltip should detect icon mid-line`() {
        assertTrue(LegacyNativeRichTooltipEventHandler.shouldInterceptTooltip(
            listOf("Damage [icon:tacz:dmg,8] 7.5")
        ))
    }

    @Test
    public fun `onRenderTooltipPre should return when stack is empty`() {
        val event = preEvent(
            stack = ItemStack.EMPTY,
            lines = listOf("[icon:tacz:gun/ak47,11]")
        )

        LegacyNativeRichTooltipEventHandler.onRenderTooltipPre(event)

        assertFalse(event.isCanceled)
    }

    @Test
    public fun `onRenderTooltipPre should return when stack item registry name is null`() {
        val item = Item()
        val event = preEvent(
            stack = ItemStack(item),
            lines = listOf("[icon:tacz:gun/ak47,11]")
        )

        LegacyNativeRichTooltipEventHandler.onRenderTooltipPre(event)

        assertFalse(event.isCanceled)
    }

    @Test
    public fun `onRenderTooltipPre should return when item namespace is not tacz`() {
        val item = Item().apply {
            setRegistryName("minecraft", "stone_like")
        }
        val event = preEvent(
            stack = ItemStack(item),
            lines = listOf("[icon:tacz:gun/ak47,11]")
        )

        LegacyNativeRichTooltipEventHandler.onRenderTooltipPre(event)

        assertFalse(event.isCanceled)
    }

    @Test
    public fun `onRenderTooltipPre should return when tooltip has no icon marker`() {
        val taczItem = Item().apply {
            setRegistryName("tacz", "ak47_no_icon")
        }
        val event = preEvent(
            stack = ItemStack(taczItem),
            lines = listOf("§eAK47", "§7Damage: 7.5")
        )

        LegacyNativeRichTooltipEventHandler.onRenderTooltipPre(event)

        assertFalse(event.isCanceled)
    }

    @Test
    public fun `onRenderTooltipPre should return when marker exists but parser cannot resolve icon`() {
        val taczItem = Item().apply {
            setRegistryName("tacz", "ak47_bad_marker")
        }
        val event = preEvent(
            stack = ItemStack(taczItem),
            lines = listOf("Damage [icon:broken] 7.5")
        )

        LegacyNativeRichTooltipEventHandler.onRenderTooltipPre(event)

        assertFalse(event.isCanceled)
    }

    @Test
    public fun `onRenderTooltipPre should reach cancel branch for rich icon tooltip`() {
        val taczItem = Item().apply {
            setRegistryName("tacz", "ak47_rich")
        }
        val event = preEvent(
            stack = ItemStack(taczItem),
            lines = listOf("§eAK47", "[icon:tacz:gun/slot/ak47,11]")
        )

        try {
            LegacyNativeRichTooltipEventHandler.onRenderTooltipPre(event)
            fail("Expected UnsupportedOperationException in unit-test environment without Forge cancelable ASM patch")
        } catch (expected: UnsupportedOperationException) {
            assertTrue(expected.message?.contains("non-cancelable event") == true)
        }
    }

    private fun preEvent(stack: ItemStack, lines: List<String>): RenderTooltipEvent.Pre {
        val ctor = RenderTooltipEvent.Pre::class.java.getConstructor(
            ItemStack::class.java,
            List::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            FontRenderer::class.java
        )
        return ctor.newInstance(
            stack,
            lines,
            100,
            60,
            320,
            240,
            -1,
            null
        )
    }
}
