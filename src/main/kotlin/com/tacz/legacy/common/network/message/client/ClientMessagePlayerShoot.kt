package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.api.entity.IGunOperator
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import java.util.function.Supplier

/**
 * C2S: 射击请求。
 */
public class ClientMessagePlayerShoot() : IMessage, IMessageHandler<ClientMessagePlayerShoot, IMessage?> {
    private var pitch: Float = 0f
    private var yaw: Float = 0f
    private var timestamp: Long = 0L

    public constructor(pitch: Float, yaw: Float, timestamp: Long) : this() {
        this.pitch = pitch
        this.yaw = yaw
        this.timestamp = timestamp
    }

    override fun fromBytes(buf: ByteBuf) {
        pitch = buf.readFloat()
        yaw = buf.readFloat()
        timestamp = buf.readLong()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeFloat(pitch)
        buf.writeFloat(yaw)
        buf.writeLong(timestamp)
    }

    override fun onMessage(message: ClientMessagePlayerShoot, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        ctx.serverHandler.player.serverWorld.addScheduledTask {
            val operator = IGunOperator.fromLivingEntity(player)
            operator.shoot(Supplier { message.pitch }, Supplier { message.yaw }, message.timestamp)
        }
        return null
    }
}
