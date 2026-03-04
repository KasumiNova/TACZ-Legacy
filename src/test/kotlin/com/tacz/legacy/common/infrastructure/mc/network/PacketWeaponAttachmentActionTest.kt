package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import io.netty.buffer.Unpooled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

public class PacketWeaponAttachmentActionTest {

    @Test
    public fun `codec should preserve install payload`() {
        val source = PacketWeaponAttachmentAction.install(
            slot = WeaponAttachmentSlot.SCOPE,
            attachmentId = "tacz:scope_red_dot"
        )
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponAttachmentAction()
        decoded.fromBytes(buf)

        assertEquals(WeaponAttachmentSlot.SCOPE, PacketWeaponAttachmentAction.decodeSlot(decoded.slotCode))
        assertEquals("tacz:scope_red_dot", decoded.attachmentId)
        assertFalse(buf.isReadable)
    }

    @Test
    public fun `codec should preserve clear payload`() {
        val source = PacketWeaponAttachmentAction.clear(WeaponAttachmentSlot.GRIP)
        val buf = Unpooled.buffer()
        source.toBytes(buf)

        val decoded = PacketWeaponAttachmentAction()
        decoded.fromBytes(buf)

        assertEquals(WeaponAttachmentSlot.GRIP, PacketWeaponAttachmentAction.decodeSlot(decoded.slotCode))
        assertEquals("", decoded.attachmentId)
        assertFalse(buf.isReadable)
    }
}
