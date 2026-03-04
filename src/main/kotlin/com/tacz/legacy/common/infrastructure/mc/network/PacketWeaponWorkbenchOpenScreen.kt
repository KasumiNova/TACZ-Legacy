package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.TACZLegacy
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponWorkbenchOpenScreen() : IMessage {

    public var gunId: String = ""
    public var blockX: Int = 0
    public var blockY: Int = 0
    public var blockZ: Int = 0

    public constructor(gunId: String, blockX: Int, blockY: Int, blockZ: Int) : this() {
        this.gunId = gunId
        this.blockX = blockX
        this.blockY = blockY
        this.blockZ = blockZ
    }

    override fun fromBytes(buf: ByteBuf) {
        gunId = ByteBufUtils.readUTF8String(buf)
        blockX = buf.readInt()
        blockY = buf.readInt()
        blockZ = buf.readInt()
    }

    override fun toBytes(buf: ByteBuf) {
        ByteBufUtils.writeUTF8String(buf, gunId)
        buf.writeInt(blockX)
        buf.writeInt(blockY)
        buf.writeInt(blockZ)
    }

    public class Handler : IMessageHandler<PacketWeaponWorkbenchOpenScreen, IMessage> {

        override fun onMessage(message: PacketWeaponWorkbenchOpenScreen, ctx: MessageContext): IMessage? {
            val normalizedGunId = message.gunId.trim().lowercase().ifBlank { return null }
            val worldThread = FMLCommonHandler.instance().getWorldThread(ctx.netHandler)
            worldThread.addScheduledTask {
                TACZLegacy.proxy.handleWeaponWorkbenchOpenPacket(
                    gunId = normalizedGunId,
                    blockX = message.blockX,
                    blockY = message.blockY,
                    blockZ = message.blockZ
                )
            }
            return null
        }
    }
}
