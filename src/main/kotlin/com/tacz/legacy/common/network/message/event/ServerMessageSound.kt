package com.tacz.legacy.common.network.message.event

import io.netty.buffer.ByteBuf
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * S2C: 让客户端播放指定位置的枪械音效。
 */
public class ServerMessageSound() : IMessage, IMessageHandler<ServerMessageSound, IMessage?> {
    private var x: Double = 0.0
    private var y: Double = 0.0
    private var z: Double = 0.0
    private var soundId: String = ""
    private var volume: Float = 1.0f
    private var pitch: Float = 1.0f

    public constructor(x: Double, y: Double, z: Double, soundId: ResourceLocation, volume: Float, pitch: Float) : this() {
        this.x = x
        this.y = y
        this.z = z
        this.soundId = soundId.toString()
        this.volume = volume
        this.pitch = pitch
    }

    override fun fromBytes(buf: ByteBuf) {
        x = buf.readDouble()
        y = buf.readDouble()
        z = buf.readDouble()
        soundId = ByteBufUtils.readUTF8String(buf)
        volume = buf.readFloat()
        pitch = buf.readFloat()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeDouble(x)
        buf.writeDouble(y)
        buf.writeDouble(z)
        ByteBufUtils.writeUTF8String(buf, soundId)
        buf.writeFloat(volume)
        buf.writeFloat(pitch)
    }

    override fun onMessage(message: ServerMessageSound, ctx: MessageContext): IMessage? {
        val mc = net.minecraft.client.Minecraft.getMinecraft()
        mc.addScheduledTask {
            // 客户端播放音效
            // TODO: 通过 SoundEvent 或自定义播放器播放
        }
        return null
    }
}
