package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.item.gun.FireMode
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemStack
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

/**
 * S2C: 广播射击模式切换事件给其他客户端。
 * 与上游 TACZ ServerMessageSwapItem / fire‐select 行为一致。
 */
public class ServerMessageGunFireSelect() : IMessage {
    private var entityId: Int = 0
    private var gunItemStack: ItemStack = ItemStack.EMPTY
    private var fireModeOrdinal: Int = 0

    public constructor(entityId: Int, gunItemStack: ItemStack, fireMode: FireMode) : this() {
        this.entityId = entityId
        this.gunItemStack = gunItemStack
        this.fireModeOrdinal = fireMode.ordinal
    }

    public fun getFireMode(): FireMode {
        return FireMode.entries.getOrElse(fireModeOrdinal) { FireMode.UNKNOWN }
    }

    override fun fromBytes(buf: ByteBuf) {
        entityId = buf.readInt()
        gunItemStack = ByteBufUtils.readItemStack(buf)
        fireModeOrdinal = buf.readByte().toInt()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(entityId)
        ByteBufUtils.writeItemStack(buf, gunItemStack)
        buf.writeByte(fireModeOrdinal)
    }

    public class Handler : IMessageHandler<ServerMessageGunFireSelect, IMessage?> {
        override fun onMessage(message: ServerMessageGunFireSelect, ctx: MessageContext): IMessage? {
            val mc = Minecraft.getMinecraft()
            mc.addScheduledTask {
                // 客户端更新其他实体的射击模式 HUD / 动画
                // 具体对接由下游渲染 / HUD 系统消费此事件
            }
            return null
        }
    }
}
