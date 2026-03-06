package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.api.entity.IGunOperator
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * C2S: 射击模式切换请求。
 */
public class ClientMessagePlayerFireSelect() : IMessage, IMessageHandler<ClientMessagePlayerFireSelect, IMessage?> {

    override fun fromBytes(buf: ByteBuf) {}
    override fun toBytes(buf: ByteBuf) {}

    override fun onMessage(message: ClientMessagePlayerFireSelect, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        ctx.serverHandler.player.serverWorld.addScheduledTask {
            IGunOperator.fromLivingEntity(player).fireSelect()
        }
        return null
    }
}
