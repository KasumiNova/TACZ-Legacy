package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.infrastructure.mc.workbench.WeaponWorkbenchSessionRegistry
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponWorkbenchSessionControl() : IMessage {

    public var actionCode: Byte = ACTION_CLOSE

    public constructor(actionCode: Byte) : this() {
        this.actionCode = actionCode
    }

    override fun fromBytes(buf: ByteBuf) {
        actionCode = buf.readByte()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeByte(actionCode.toInt())
    }

    public class Handler : IMessageHandler<PacketWeaponWorkbenchSessionControl, IMessage> {

        override fun onMessage(message: PacketWeaponWorkbenchSessionControl, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.player ?: return null
            player.serverWorld.addScheduledTask {
                handle(player, message)
            }
            return null
        }

        private fun handle(player: EntityPlayerMP, message: PacketWeaponWorkbenchSessionControl) {
            if (message.actionCode == ACTION_CLOSE) {
                WeaponWorkbenchSessionRegistry.end(player)
            }
        }
    }

    public companion object {
        private const val ACTION_CLOSE: Byte = 1

        public fun close(): PacketWeaponWorkbenchSessionControl = PacketWeaponWorkbenchSessionControl(ACTION_CLOSE)
    }
}
