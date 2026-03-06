package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.api.entity.IGunOperator
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * C2S: 瞄准切换。
 */
public class ClientMessagePlayerAim() : IMessage, IMessageHandler<ClientMessagePlayerAim, IMessage?> {
    private var isAiming: Boolean = false

    public constructor(isAiming: Boolean) : this() {
        this.isAiming = isAiming
    }

    override fun fromBytes(buf: ByteBuf) {
        isAiming = buf.readBoolean()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeBoolean(isAiming)
    }

    override fun onMessage(message: ClientMessagePlayerAim, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        ctx.serverHandler.player.serverWorld.addScheduledTask {
            IGunOperator.fromLivingEntity(player).aim(message.isAiming)
        }
        return null
    }
}
