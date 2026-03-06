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
        registerC2S<ClientMessagePlayerShoot>()
        registerC2S<ClientMessagePlayerReload>()
        registerC2S<ClientMessagePlayerAim>()
        registerC2S<ClientMessagePlayerBolt>()
        registerC2S<ClientMessagePlayerDraw>()
        registerC2S<ClientMessagePlayerMelee>()
        registerC2S<ClientMessagePlayerFireSelect>()

        // Server -> Client (events)
        registerS2C<ServerMessageGunShoot>()
        registerS2C<ServerMessageReload>()
        registerS2C<ServerMessageMelee>()
        registerS2C<ServerMessageSound>()
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
     * 发送消息到服务端。
     */
    @JvmStatic
    public fun sendToServer(msg: IMessage) {
        CHANNEL.sendToServer(msg)
    }

    private inline fun <reified T> registerC2S() where T : IMessage, T : IMessageHandler<T, IMessage?> {
        CHANNEL.registerMessage(T::class.java, T::class.java, packetId++, Side.SERVER)
    }

    private inline fun <reified T> registerS2C() where T : IMessage, T : IMessageHandler<T, IMessage?> {
        CHANNEL.registerMessage(T::class.java, T::class.java, packetId++, Side.CLIENT)
    }
}
