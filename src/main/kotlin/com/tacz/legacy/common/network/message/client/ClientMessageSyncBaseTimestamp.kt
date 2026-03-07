package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.entity.IGunOperator
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * C2S: 客户端确认已更新 baseTimestamp，服务端在收到此确认后再更新自己的 baseTimestamp。
 * 与上游 TACZ ClientMessageSyncBaseTimestamp 行为一致。
 */
public class ClientMessageSyncBaseTimestamp() : IMessage, IMessageHandler<ClientMessageSyncBaseTimestamp, IMessage?> {
    override fun fromBytes(buf: ByteBuf) {}
    override fun toBytes(buf: ByteBuf) {}

    override fun onMessage(message: ClientMessageSyncBaseTimestamp, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        val receiveTimestamp = System.currentTimeMillis()
        player.serverWorld.addScheduledTask {
            val dataHolder = IGunOperator.fromLivingEntity(player).getDataHolder()
            dataHolder.baseTimestamp = receiveTimestamp
            TACZLegacy.logger.debug("Sync server baseTimestamp: {}", receiveTimestamp)
        }
        return null
    }
}
