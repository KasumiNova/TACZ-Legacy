package com.tacz.legacy.common.network

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.common.network.message.client.*
import com.tacz.legacy.common.network.message.event.*
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper
import net.minecraftforge.fml.relauncher.Side

/**
 * 基于 1.12.2 SimpleNetworkWrapper 的网络管理器。
 * 注册所有战斗/枪械相关的 C2S 和 S2C 消息。
 */
public object TACZNetworkHandler {

    @JvmField
    public val CHANNEL: SimpleNetworkWrapper = NetworkRegistry.INSTANCE.newSimpleChannel(TACZLegacy.MOD_ID)
    private var packetId: Int = 0

    public fun init() {
        // Client -> Server
        registerC2S<ClientMessageGunSmithCraft>()
        registerC2S<ClientMessageRefitGun>()
        registerC2S<ClientMessageRefitGunCreative>()
        registerC2S<ClientMessageUnloadAttachment>()
        registerC2S<ClientMessageLaserColor>()
        registerC2S<ClientMessagePlayerShoot>()
        registerC2S<ClientMessagePlayerReload>()
        registerC2S<ClientMessagePlayerCancelReload>()
        registerC2S<ClientMessagePlayerAim>()
        registerC2S<ClientMessagePlayerBolt>()
        registerC2S<ClientMessagePlayerDraw>()
        registerC2S<ClientMessagePlayerMelee>()
        registerC2S<ClientMessagePlayerFireSelect>()
        registerC2S<ClientMessageSyncBaseTimestamp>()

        // Server -> Client (events)
        registerS2C(ServerMessageGunShoot.Handler::class.java, ServerMessageGunShoot::class.java)
        registerS2C(ServerMessageReload.Handler::class.java, ServerMessageReload::class.java)
        registerS2C(ServerMessageMelee.Handler::class.java, ServerMessageMelee::class.java)
        registerS2C(ServerMessageSound.Handler::class.java, ServerMessageSound::class.java)
        registerS2C(ServerMessageGunFire.Handler::class.java, ServerMessageGunFire::class.java)
        registerS2C(ServerMessageGunDraw.Handler::class.java, ServerMessageGunDraw::class.java)
        registerS2C(ServerMessageGunFireSelect.Handler::class.java, ServerMessageGunFireSelect::class.java)
        registerS2C(ServerMessageGunHurt.Handler::class.java, ServerMessageGunHurt::class.java)
        registerS2C(ServerMessageGunKill.Handler::class.java, ServerMessageGunKill::class.java)
        registerS2C(ServerMessageRefreshRefitScreen.Handler::class.java, ServerMessageRefreshRefitScreen::class.java)
        registerS2C(ServerMessageSyncBaseTimestamp.Handler::class.java, ServerMessageSyncBaseTimestamp::class.java)
    }

    /**
     * 发送消息到指定玩家。
     */
    @JvmStatic
    public fun sendToPlayer(msg: IMessage, player: EntityPlayerMP) {
        CHANNEL.sendTo(msg, player)
    }

    /**
     * 发送消息到追踪指定实体的所有玩家（不含实体自身如果是玩家）。
     */
    @JvmStatic
    public fun sendToTrackingEntity(msg: IMessage, entity: Entity) {
        val point = NetworkRegistry.TargetPoint(
            entity.world.provider.dimension,
            entity.posX,
            entity.posY,
            entity.posZ,
            128.0,
        )
        CHANNEL.sendToAllAround(msg, point)
    }

    /**
     * 发送消息到指定维度内的所有客户端。
     */
    @JvmStatic
    public fun sendToDimension(msg: IMessage, dimension: Int) {
        CHANNEL.sendToDimension(msg, dimension)
    }

    /**
     * 发送消息到服务端。
     */
    @JvmStatic
    public fun sendToServer(msg: IMessage) {
        CHANNEL.sendToServer(msg)
    }

    private inline fun <reified T> registerC2S() where T : IMessage, T : IMessageHandler<T, IMessage?> {
        CHANNEL.registerMessage(T::class.java, T::class.java, packetId++, Side.SERVER)
    }

    private fun <REQ : IMessage> registerS2C(
        handlerClass: Class<out IMessageHandler<REQ, IMessage?>>,
        messageClass: Class<REQ>,
    ) {
        CHANNEL.registerMessage(handlerClass, messageClass, packetId++, Side.CLIENT)
    }
}
