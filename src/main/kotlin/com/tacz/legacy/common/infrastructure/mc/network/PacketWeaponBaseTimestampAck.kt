package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponBaseTimestampAck() : IMessage {

    override fun fromBytes(buf: ByteBuf) {
        // no payload
    }

    override fun toBytes(buf: ByteBuf) {
        // no payload
    }

    public class Handler : IMessageHandler<PacketWeaponBaseTimestampAck, IMessage> {

        override fun onMessage(message: PacketWeaponBaseTimestampAck, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.player ?: return null
            val world = player.serverWorld
            world.addScheduledTask {
                val sessionId = "player:${player.uniqueID.toString().trim()}"
                PacketWeaponInput.recordServerBaseTimestamp(sessionId, System.currentTimeMillis())
            }
            return null
        }

    }

}
