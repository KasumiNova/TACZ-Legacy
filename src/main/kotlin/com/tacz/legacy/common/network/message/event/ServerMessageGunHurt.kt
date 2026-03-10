package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.event.EntityHurtByGunEvent
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import net.minecraftforge.fml.relauncher.Side

public class ServerMessageGunHurt() : IMessage {
    private var bulletId: Int = -1
    private var hurtEntityId: Int = -1
    private var attackerId: Int = -1
    private var gunId: ResourceLocation = DefaultAssets.DEFAULT_GUN_ID
    private var gunDisplayId: ResourceLocation = DefaultAssets.DEFAULT_GUN_DISPLAY_ID
    private var baseAmount: Float = 0.0f
    private var isHeadShot: Boolean = false
    private var headShotMultiplier: Float = 1.0f

    public constructor(
        bulletId: Int,
        hurtEntityId: Int,
        attackerId: Int,
        gunId: ResourceLocation,
        gunDisplayId: ResourceLocation,
        baseAmount: Float,
        isHeadShot: Boolean,
        headShotMultiplier: Float,
    ) : this() {
        this.bulletId = bulletId
        this.hurtEntityId = hurtEntityId
        this.attackerId = attackerId
        this.gunId = gunId
        this.gunDisplayId = gunDisplayId
        this.baseAmount = baseAmount
        this.isHeadShot = isHeadShot
        this.headShotMultiplier = headShotMultiplier
    }

    override fun fromBytes(buf: ByteBuf) {
        bulletId = buf.readInt()
        hurtEntityId = buf.readInt()
        attackerId = buf.readInt()
        gunId = ResourceLocation(ByteBufUtils.readUTF8String(buf))
        gunDisplayId = ResourceLocation(ByteBufUtils.readUTF8String(buf))
        baseAmount = buf.readFloat()
        isHeadShot = buf.readBoolean()
        headShotMultiplier = buf.readFloat()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(bulletId)
        buf.writeInt(hurtEntityId)
        buf.writeInt(attackerId)
        ByteBufUtils.writeUTF8String(buf, gunId.toString())
        ByteBufUtils.writeUTF8String(buf, gunDisplayId.toString())
        buf.writeFloat(baseAmount)
        buf.writeBoolean(isHeadShot)
        buf.writeFloat(headShotMultiplier)
    }

    public class Handler : IMessageHandler<ServerMessageGunHurt, IMessage?> {
        override fun onMessage(message: ServerMessageGunHurt, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().addScheduledTask {
                val world = Minecraft.getMinecraft().world ?: return@addScheduledTask
                val bullet = world.getEntityByID(message.bulletId)
                val hurtEntity = world.getEntityByID(message.hurtEntityId)
                val attacker = world.getEntityByID(message.attackerId) as? EntityLivingBase
                MinecraftForge.EVENT_BUS.post(
                    EntityHurtByGunEvent.Post(
                        bullet = bullet,
                        hurtEntity = hurtEntity,
                        attacker = attacker,
                        gunId = message.gunId,
                        gunDisplayId = message.gunDisplayId,
                        baseAmount = message.baseAmount,
                        isHeadShot = message.isHeadShot,
                        headShotMultiplier = message.headShotMultiplier,
                        logicalSide = Side.CLIENT,
                    ),
                )
            }
            return null
        }
    }
}