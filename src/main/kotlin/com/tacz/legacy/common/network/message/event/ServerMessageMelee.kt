package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.event.GunMeleeEvent
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
 * S2C: 广播近战事件给其他客户端（用于第三人称动画/音效）。
 * 与上游 TACZ ServerMessageGunMelee 行为一致。
 */
public class ServerMessageMelee() : IMessage {
    private var entityId: Int = 0
    private var gunItemStack: ItemStack = ItemStack.EMPTY

    public constructor(entityId: Int, gunItemStack: ItemStack) : this() {
        this.entityId = entityId
        this.gunItemStack = gunItemStack
    }

    override fun fromBytes(buf: ByteBuf) {
        entityId = buf.readInt()
        gunItemStack = ByteBufUtils.readItemStack(buf)
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entityId)
        ByteBufUtils.writeItemStack(buf, gunItemStack)
    }

    public class Handler : IMessageHandler<ServerMessageMelee, IMessage?> {
        override fun onMessage(message: ServerMessageMelee, ctx: MessageContext): IMessage? {
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                val world = mc.world ?: return@addScheduledTask
                val entity = world.getEntityByID(message.entityId)
                if (entity is EntityLivingBase) {
                    MinecraftForge.EVENT_BUS.post(GunMeleeEvent(entity, message.gunItemStack, Side.CLIENT))
                }
            }
            return null
        }
    }
}
