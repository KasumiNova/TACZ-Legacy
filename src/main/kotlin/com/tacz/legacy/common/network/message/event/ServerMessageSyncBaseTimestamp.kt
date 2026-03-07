package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.network.TACZNetworkHandler
import com.tacz.legacy.common.network.message.client.ClientMessageSyncBaseTimestamp
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * S2C: 服务端要求客户端重新同步 baseTimestamp。
 * 与上游 TACZ ServerMessageSyncBaseTimestamp 行为一致：
 * 1. 服务端发 → 客户端收到后记录当前 currentTimeMillis 为新的 clientBaseTimestamp
 * 2. 客户端回复 ClientMessageSyncBaseTimestamp → 服务端收到后记录当前 currentTimeMillis 为新的 data.baseTimestamp
 * 这样两侧基准时间几乎同步（差值为网络往返延迟）。
 */
public class ServerMessageSyncBaseTimestamp : IMessage {
    override fun fromBytes(buf: ByteBuf) {}
    override fun toBytes(buf: ByteBuf) {}

    public class Handler : IMessageHandler<ServerMessageSyncBaseTimestamp, IMessage?> {
        override fun onMessage(message: ServerMessageSyncBaseTimestamp, ctx: MessageContext): IMessage? {
            // 在网络线程上捕获当前时间，然后在主线程上更新
            val receiveTimestamp = System.currentTimeMillis()
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                val player = mc.player ?: return@addScheduledTask
                val operator = com.tacz.legacy.api.entity.IGunOperator.fromLivingEntity(player)
                operator.getDataHolder().baseTimestamp = receiveTimestamp
                TACZLegacy.logger.debug("Sync client baseTimestamp: {}", receiveTimestamp)
            }
            // 立即回复确认（在网络线程上）
            TACZNetworkHandler.sendToServer(ClientMessageSyncBaseTimestamp())
            return null
        }
    }
}
