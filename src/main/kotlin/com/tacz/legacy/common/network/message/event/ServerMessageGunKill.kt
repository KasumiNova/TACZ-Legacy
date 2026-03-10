package com.tacz.legacy.common.network.message.event

import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.event.EntityKillByGunEvent
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

public class ServerMessageGunKill() : IMessage {
    private var bulletId: Int = -1
    private var killedEntityId: Int = -1
    private var attackerId: Int = -1
    private var gunId: ResourceLocation = DefaultAssets.DEFAULT_GUN_ID
    private var gunDisplayId: ResourceLocation = DefaultAssets.DEFAULT_GUN_DISPLAY_ID
    private var baseDamage: Float = 0.0f
    private var isHeadShot: Boolean = false
    private var headShotMultiplier: Float = 1.0f

    public constructor(
        bulletId: Int,
        killedEntityId: Int,
        attackerId: Int,
        gunId: ResourceLocation,
        gunDisplayId: ResourceLocation,
        baseDamage: Float,
        isHeadShot: Boolean,
        headShotMultiplier: Float,
    ) : this() {
        this.bulletId = bulletId
        this.killedEntityId = killedEntityId
        this.attackerId = attackerId
        this.gunId = gunId
        this.gunDisplayId = gunDisplayId
        this.baseDamage = baseDamage
        this.isHeadShot = isHeadShot
        this.headShotMultiplier = headShotMultiplier
    }

    override fun fromBytes(buf: ByteBuf) {
        bulletId = buf.readInt()
        killedEntityId = buf.readInt()
        attackerId = buf.readInt()
        gunId = ResourceLocation(ByteBufUtils.readUTF8String(buf))
        gunDisplayId = ResourceLocation(ByteBufUtils.readUTF8String(buf))
        baseDamage = buf.readFloat()
        isHeadShot = buf.readBoolean()
        headShotMultiplier = buf.readFloat()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(bulletId)
        buf.writeInt(killedEntityId)
        buf.writeInt(attackerId)
        ByteBufUtils.writeUTF8String(buf, gunId.toString())
        ByteBufUtils.writeUTF8String(buf, gunDisplayId.toString())
        buf.writeFloat(baseDamage)
        buf.writeBoolean(isHeadShot)
        buf.writeFloat(headShotMultiplier)
    }

    public class Handler : IMessageHandler<ServerMessageGunKill, IMessage?> {
        override fun onMessage(message: ServerMessageGunKill, ctx: MessageContext): IMessage? {
            Minecraft.getMinecraft().addScheduledTask {
                val world = Minecraft.getMinecraft().world ?: return@addScheduledTask
                val bullet = world.getEntityByID(message.bulletId)
                val killedEntity = world.getEntityByID(message.killedEntityId) as? EntityLivingBase
                val attacker = world.getEntityByID(message.attackerId) as? EntityLivingBase
                MinecraftForge.EVENT_BUS.post(
                    EntityKillByGunEvent(
                        bullet = bullet,
                        killedEntity = killedEntity,
                        attacker = attacker,
                        gunId = message.gunId,
                        gunDisplayId = message.gunDisplayId,
                        baseDamage = message.baseDamage,
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