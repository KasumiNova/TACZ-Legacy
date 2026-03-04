package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.TACZLegacy
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponWorkbenchCloseScreen() : IMessage {

    public var reasonMessage: String = ""

    public constructor(reasonMessage: String) : this() {
        this.reasonMessage = reasonMessage
    }

    override fun fromBytes(buf: ByteBuf) {
        reasonMessage = ByteBufUtils.readUTF8String(buf)
    }

    override fun toBytes(buf: ByteBuf) {
        ByteBufUtils.writeUTF8String(buf, reasonMessage)
    }

    public class Handler : IMessageHandler<PacketWeaponWorkbenchCloseScreen, IMessage> {

        override fun onMessage(message: PacketWeaponWorkbenchCloseScreen, ctx: MessageContext): IMessage? {
            val reason = message.reasonMessage.trim()
            val worldThread = FMLCommonHandler.instance().getWorldThread(ctx.netHandler)
            worldThread.addScheduledTask {
                TACZLegacy.proxy.handleWeaponWorkbenchClosePacket(reason)
            }
            return null
        }
    }
}
