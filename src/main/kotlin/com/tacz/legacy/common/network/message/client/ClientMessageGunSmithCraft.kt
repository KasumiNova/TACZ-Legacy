package com.tacz.legacy.common.network.message.client

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.common.application.gunsmith.LegacyGunSmithingRuntime
import com.tacz.legacy.common.inventory.GunSmithTableContainer
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import java.nio.charset.StandardCharsets

/**
 * C2S: 工匠台配方制作请求。
 */
public class ClientMessageGunSmithCraft() : IMessage, IMessageHandler<ClientMessageGunSmithCraft, IMessage?> {
    private var windowId: Int = -1
    private var recipeId: ResourceLocation = DefaultAssets.EMPTY_GUN_ID

    public constructor(windowId: Int, recipeId: ResourceLocation) : this() {
        this.windowId = windowId
        this.recipeId = recipeId
    }

    override fun fromBytes(buf: ByteBuf) {
        windowId = buf.readInt()
        recipeId = ResourceLocation(readString(buf))
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(windowId)
        writeString(buf, recipeId.toString())
    }

    override fun onMessage(message: ClientMessageGunSmithCraft, ctx: MessageContext): IMessage? {
        val player: EntityPlayerMP = ctx.serverHandler.player
        player.serverWorld.addScheduledTask {
            val container = player.openContainer as? GunSmithTableContainer ?: return@addScheduledTask
            if (container.windowId != message.windowId || !container.canInteractWith(player)) {
                return@addScheduledTask
            }
            LegacyGunSmithingRuntime.craft(player, container.blockId, message.recipeId)
        }
        return null
    }

    private companion object {
        fun readString(buf: ByteBuf): String {
            val length = buf.readInt()
            val bytes = ByteArray(length)
            buf.readBytes(bytes)
            return String(bytes, StandardCharsets.UTF_8)
        }

        fun writeString(buf: ByteBuf, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            buf.writeInt(bytes.size)
            buf.writeBytes(bytes)
        }
    }
}
