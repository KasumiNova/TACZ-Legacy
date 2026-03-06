package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.api.entity.IGunOperator
import com.tacz.legacy.api.item.IGun
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import java.util.function.Supplier

/**
 * C2S: 切枪/拔枪请求。
 */
public class ClientMessagePlayerDraw() : IMessage, IMessageHandler<ClientMessagePlayerDraw, IMessage?> {

    override fun fromBytes(buf: ByteBuf) {}
    override fun toBytes(buf: ByteBuf) {}

    override fun onMessage(message: ClientMessagePlayerDraw, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        ctx.serverHandler.player.serverWorld.addScheduledTask {
            val mainHandStack = player.heldItemMainhand
            if (mainHandStack.item is IGun) {
                IGunOperator.fromLivingEntity(player).draw(Supplier { mainHandStack })
            }
        }
        return null
    }
}
