package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.client.input.WeaponAimInputStateRegistry
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponAimState() : IMessage {

    public var isAiming: Boolean = false

    public constructor(isAiming: Boolean) : this() {
        this.isAiming = isAiming
    }

    override fun fromBytes(buf: ByteBuf) {
        isAiming = buf.readBoolean()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeBoolean(isAiming)
    }

    public class Handler : IMessageHandler<PacketWeaponAimState, IMessage> {

        override fun onMessage(message: PacketWeaponAimState, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.player ?: return null
            val sessionId = WeaponRuntimeMcBridge.serverSessionIdForPlayer(player.uniqueID.toString())

            player.serverWorld.addScheduledTask {
                WeaponAimInputStateRegistry.updateFromExternalSync(
                    sessionId = sessionId,
                    isAiming = message.isAiming
                )
            }
            return null
        }

    }

}
