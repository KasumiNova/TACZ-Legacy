package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.event.GunDrawEvent
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.relauncher.Side

/**
 * S2C: 广播拔枪/收枪事件给其他客户端（用于第三人称动画/音效）。
 * 与上游 TACZ ServerMessageGunDraw 行为一致。
 */
public class ServerMessageGunDraw() : IMessage {
    private var entityId: Int = 0
    private var previousGunItem: ItemStack = ItemStack.EMPTY
    private var currentGunItem: ItemStack = ItemStack.EMPTY

    public constructor(entityId: Int, previousGunItem: ItemStack, currentGunItem: ItemStack) : this() {
        this.entityId = entityId
        this.previousGunItem = previousGunItem
        this.currentGunItem = currentGunItem
    }

    override fun fromBytes(buf: ByteBuf) {
        entityId = buf.readInt()
        previousGunItem = ByteBufUtils.readItemStack(buf)
        currentGunItem = ByteBufUtils.readItemStack(buf)
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entityId)
        ByteBufUtils.writeItemStack(buf, previousGunItem)
        ByteBufUtils.writeItemStack(buf, currentGunItem)
    }

    public class Handler : IMessageHandler<ServerMessageGunDraw, IMessage?> {
        override fun onMessage(message: ServerMessageGunDraw, ctx: MessageContext): IMessage? {
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                val world = mc.world ?: return@addScheduledTask
                val entity = world.getEntityByID(message.entityId)
                if (entity is EntityLivingBase) {
                    MinecraftForge.EVENT_BUS.post(
                        GunDrawEvent(entity, message.previousGunItem, message.currentGunItem, Side.CLIENT)
                    )
                }
            }
            return null
        }
    }
}
