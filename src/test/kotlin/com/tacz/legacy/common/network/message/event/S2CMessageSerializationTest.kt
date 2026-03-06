package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.item.gun.FireMode
import io.netty.buffer.Unpooled
import net.minecraft.init.Bootstrap
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

/**
 * S2C 消息序列化/反序列化回归测试。
 * 验证 toBytes/fromBytes 的正确性和字段完整保留。
 */
class S2CMessageSerializationTest {

    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() {
            Bootstrap.register()
        }
    }

    @Test
    fun `ServerMessageGunShoot round-trips entityId and stack`() {
        val msg = ServerMessageGunShoot(42, ItemStack.EMPTY)
        val buf = Unpooled.buffer()
        msg.toBytes(buf)

        val deserialized = ServerMessageGunShoot()
        deserialized.fromBytes(buf)
        // 通过反射获取 entityId 校验
        val entityIdField = ServerMessageGunShoot::class.java.getDeclaredField("entityId")
        entityIdField.isAccessible = true
        assertEquals(42, entityIdField.getInt(deserialized))
    }

    @Test
    fun `ServerMessageGunFire round-trips entityId and stack`() {
        val msg = ServerMessageGunFire(99, ItemStack.EMPTY)
        val buf = Unpooled.buffer()
        msg.toBytes(buf)

        val deserialized = ServerMessageGunFire()
        deserialized.fromBytes(buf)
        val entityIdField = ServerMessageGunFire::class.java.getDeclaredField("entityId")
        entityIdField.isAccessible = true
        assertEquals(99, entityIdField.getInt(deserialized))
    }

    @Test
    fun `ServerMessageGunDraw round-trips entityId and both stacks`() {
        val msg = ServerMessageGunDraw(55, ItemStack.EMPTY, ItemStack.EMPTY)
        val buf = Unpooled.buffer()
        msg.toBytes(buf)

        val deserialized = ServerMessageGunDraw()
        deserialized.fromBytes(buf)
        val entityIdField = ServerMessageGunDraw::class.java.getDeclaredField("entityId")
        entityIdField.isAccessible = true
        assertEquals(55, entityIdField.getInt(deserialized))
    }

    @Test
    fun `ServerMessageGunFireSelect round-trips entityId and fireMode`() {
        val msg = ServerMessageGunFireSelect(77, ItemStack.EMPTY, FireMode.BURST)
        val buf = Unpooled.buffer()
        msg.toBytes(buf)

        val deserialized = ServerMessageGunFireSelect()
        deserialized.fromBytes(buf)
        assertEquals(FireMode.BURST, deserialized.getFireMode())
    }

    @Test
    fun `ServerMessageReload round-trips entityId and stack`() {
        val msg = ServerMessageReload(10, ItemStack.EMPTY)
        val buf = Unpooled.buffer()
        msg.toBytes(buf)

        val deserialized = ServerMessageReload()
        deserialized.fromBytes(buf)
        val entityIdField = ServerMessageReload::class.java.getDeclaredField("entityId")
        entityIdField.isAccessible = true
        assertEquals(10, entityIdField.getInt(deserialized))
    }

    @Test
    fun `ServerMessageMelee round-trips entityId and stack`() {
        val msg = ServerMessageMelee(123, ItemStack.EMPTY)
        val buf = Unpooled.buffer()
        msg.toBytes(buf)

        val deserialized = ServerMessageMelee()
        deserialized.fromBytes(buf)
        val entityIdField = ServerMessageMelee::class.java.getDeclaredField("entityId")
        entityIdField.isAccessible = true
        assertEquals(123, entityIdField.getInt(deserialized))
    }

    @Test
    fun `ServerMessageSound round-trips all fields`() {
        val gunId = ResourceLocation("tacz", "ak47")
        val displayId = DefaultAssets.DEFAULT_GUN_DISPLAY_ID
        val msg = ServerMessageSound(44, gunId, displayId, "fire", 0.8f, 1.2f, 128)
        val buf = Unpooled.buffer()
        msg.toBytes(buf)

        val deserialized = ServerMessageSound()
        deserialized.fromBytes(buf)
        assertEquals(44, deserialized.entityId)
        assertEquals(gunId, deserialized.gunId)
        assertEquals(displayId, deserialized.gunDisplayId)
        assertEquals("fire", deserialized.soundName)
        assertEquals(0.8f, deserialized.volume, 0.001f)
        assertEquals(1.2f, deserialized.pitch, 0.001f)
        assertEquals(128, deserialized.distance)
    }

    @Test
    fun `ServerMessageSound convenience constructor sets default displayId`() {
        val gunId = ResourceLocation("tacz", "m4a1")
        val msg = ServerMessageSound(1, gunId, "shoot", 1.0f, 1.0f, 64)
        assertEquals(DefaultAssets.DEFAULT_GUN_DISPLAY_ID, msg.gunDisplayId)
    }
}
