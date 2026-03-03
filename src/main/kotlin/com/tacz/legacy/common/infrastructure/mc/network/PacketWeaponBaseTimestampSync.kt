package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponBaseTimestampSync() : IMessage {

    override fun fromBytes(buf: ByteBuf) {
        // no payload
    }

    override fun toBytes(buf: ByteBuf) {
        // no payload
    }

    public class Handler : IMessageHandler<PacketWeaponBaseTimestampSync, IMessage> {

        override fun onMessage(message: PacketWeaponBaseTimestampSync, ctx: MessageContext): IMessage? {
            val worldThread = FMLCommonHandler.instance().getWorldThread(ctx.netHandler)
            worldThread.addScheduledTask {
                LegacyNetworkHandler.updateClientBaseTimestamp(System.currentTimeMillis())
                LegacyNetworkHandler.sendWeaponBaseTimestampAckToServer()
            }
            return null
        }

    }

}
