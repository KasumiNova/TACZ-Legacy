package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.entity.ReloadState
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * S2C: 广播换弹事件给其他客户端（用于第三人称动画/音效）。
 */
public class ServerMessageReload() : IMessage, IMessageHandler<ServerMessageReload, IMessage?> {
    private var entityId: Int = 0
    private var stateTypeOrdinal: Int = 0

    public constructor(entityId: Int, stateType: ReloadState.StateType) : this() {
        this.entityId = entityId
        this.stateTypeOrdinal = stateType.ordinal
    }

    override fun fromBytes(buf: ByteBuf) {
        entityId = buf.readInt()
        stateTypeOrdinal = buf.readByte().toInt()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entityId)
        buf.writeByte(stateTypeOrdinal)
    }

    override fun onMessage(message: ServerMessageReload, ctx: MessageContext): IMessage? {
        val mc = net.minecraft.client.Minecraft.getMinecraft()
        mc.addScheduledTask {
            // 客户端播放其他实体的换弹动画
            // TODO: 客户端动画/音效触发
        }
        return null
    }
}
