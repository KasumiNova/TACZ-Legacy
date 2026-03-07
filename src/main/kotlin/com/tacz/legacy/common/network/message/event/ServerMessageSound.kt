package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.client.resource.TACZClientAssetManager
import com.tacz.legacy.client.sound.GunSoundPlayManager
import com.tacz.legacy.common.resource.TACZGunPackPresentation
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * S2C: 让客户端播放枪械音效。
 * 与上游 TACZ ServerMessageSound 行为一致（entityId + gunId + soundName + volume + pitch + distance）。
 */
public class ServerMessageSound() : IMessage {
    public var entityId: Int = 0
        private set
    public var gunId: ResourceLocation = DefaultAssets.DEFAULT_GUN_ID
        private set
    public var gunDisplayId: ResourceLocation = DefaultAssets.DEFAULT_GUN_DISPLAY_ID
        private set
    public var soundName: String = ""
        private set
    public var volume: Float = 1.0f
        private set
    public var pitch: Float = 1.0f
        private set
    public var distance: Int = 64
        private set

    public constructor(
        entityId: Int,
        gunId: ResourceLocation,
        gunDisplayId: ResourceLocation,
        soundName: String,
        volume: Float,
        pitch: Float,
        distance: Int,
    ) : this() {
        this.entityId = entityId
        this.gunId = gunId
        this.gunDisplayId = gunDisplayId
        this.soundName = soundName
        this.volume = volume
        this.pitch = pitch
        this.distance = distance
    }

    public constructor(
        entityId: Int,
        gunId: ResourceLocation,
        soundName: String,
        volume: Float,
        pitch: Float,
        distance: Int,
    ) : this(entityId, gunId, DefaultAssets.DEFAULT_GUN_DISPLAY_ID, soundName, volume, pitch, distance)

    override fun fromBytes(buf: ByteBuf) {
        entityId = buf.readInt()
        gunId = ResourceLocation(ByteBufUtils.readUTF8String(buf))
        gunDisplayId = ResourceLocation(ByteBufUtils.readUTF8String(buf))
        soundName = ByteBufUtils.readUTF8String(buf)
        volume = buf.readFloat()
        pitch = buf.readFloat()
        distance = buf.readInt()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entityId)
        ByteBufUtils.writeUTF8String(buf, gunId.toString())
        ByteBufUtils.writeUTF8String(buf, gunDisplayId.toString())
        ByteBufUtils.writeUTF8String(buf, soundName)
        buf.writeFloat(volume)
        buf.writeFloat(pitch)
        buf.writeInt(distance)
    }

    public class Handler : IMessageHandler<ServerMessageSound, IMessage?> {
        override fun onMessage(message: ServerMessageSound, ctx: MessageContext): IMessage? {
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                val world = mc.world ?: return@addScheduledTask
                val entity = world.getEntityByID(message.entityId) as? EntityLivingBase ?: return@addScheduledTask
                val fallbackDisplayId = TACZGunPackPresentation.resolveGunDisplayId(
                    TACZGunPackRuntimeRegistry.getSnapshot(),
                    message.gunId,
                )
                val displayId = if (message.gunDisplayId != DefaultAssets.DEFAULT_GUN_DISPLAY_ID) {
                    message.gunDisplayId
                } else {
                    fallbackDisplayId ?: message.gunDisplayId
                }
                val display = TACZClientAssetManager.getGunDisplayInstance(displayId) ?: return@addScheduledTask
                val soundId = display.getSound(message.soundName) ?: return@addScheduledTask
                GunSoundPlayManager.playClientSound(entity, soundId, message.volume, message.pitch, message.distance)
            }
            return null
        }
    }
}
