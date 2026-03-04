package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.TACZLegacy
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponWorkbenchRefreshScreen() : IMessage {

    override fun fromBytes(buf: ByteBuf) {
        // no-op
    }

    override fun toBytes(buf: ByteBuf) {
        // no-op
    }

    public class Handler : IMessageHandler<PacketWeaponWorkbenchRefreshScreen, IMessage> {

        override fun onMessage(message: PacketWeaponWorkbenchRefreshScreen, ctx: MessageContext): IMessage? {
            val worldThread = FMLCommonHandler.instance().getWorldThread(ctx.netHandler)
            worldThread.addScheduledTask {
                TACZLegacy.proxy.handleWeaponWorkbenchRefreshPacket()
            }
            return null
        }
    }
}
