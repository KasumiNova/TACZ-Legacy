package com.tacz.legacy.common.network.message.event

import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * S2C: 广播射击事件给其他客户端（用于第三人称动画/音效）。
 */
public class ServerMessageGunShoot() : IMessage, IMessageHandler<ServerMessageGunShoot, IMessage?> {
    private var entityId: Int = 0

    public constructor(entityId: Int) : this() {
        this.entityId = entityId
    }

    override fun fromBytes(buf: ByteBuf) {
        entityId = buf.readInt()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entityId)
    }

    override fun onMessage(message: ServerMessageGunShoot, ctx: MessageContext): IMessage? {
        val mc = net.minecraft.client.Minecraft.getMinecraft()
        mc.addScheduledTask {
            // 客户端播放其他实体的射击效果（枪口闪光、音效等）
            // TODO: 客户端动画/音效触发
        }
        return null
    }
}
