package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponItemStackRuntimeData
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import com.tacz.legacy.common.infrastructure.mc.workbench.WeaponWorkbenchSessionRegistry
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponAmmoDebugState() : IMessage {

    public var ammoInMagazine: Int = 0
    public var ammoReserve: Int = 0

    public constructor(ammoInMagazine: Int, ammoReserve: Int) : this() {
        this.ammoInMagazine = ammoInMagazine
        this.ammoReserve = ammoReserve
    }

    override fun fromBytes(buf: ByteBuf) {
        ammoInMagazine = buf.readInt()
        ammoReserve = buf.readInt()
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeInt(ammoInMagazine)
        buf.writeInt(ammoReserve)
    }

    public class Handler : IMessageHandler<PacketWeaponAmmoDebugState, IMessage> {

        override fun onMessage(message: PacketWeaponAmmoDebugState, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.player ?: return null
            player.serverWorld.addScheduledTask {
                handleMessage(player, message)
            }
            return null
        }

        private fun handleMessage(player: EntityPlayerMP, message: PacketWeaponAmmoDebugState) {
            val gunStack = resolveHeldGunStack(player)
            if (gunStack == null) {
                player.sendStatusMessage(TextComponentString("[TACZ] 未检测到枪械，无法调整弹药"), true)
                return
            }

            val gunId = gunStack.item.registryName
                ?.toString()
                ?.substringAfter(':')
                ?.trim()
                ?.lowercase()
                ?.ifBlank { null }
                ?: run {
                    player.sendStatusMessage(TextComponentString("[TACZ] 无法识别当前枪械 ID"), true)
                    return
                }
            val sessionCheck = WeaponWorkbenchSessionRegistry.validateIfSessionPresent(player, gunId)
            if (!sessionCheck.valid) {
                val reason = sessionCheck.reasonMessage.orEmpty()
                if (sessionCheck.hadSession) {
                    LegacyNetworkHandler.sendWeaponWorkbenchCloseToClient(player, reason)
                }
                if (reason.isNotBlank()) {
                    player.sendStatusMessage(TextComponentString("[TACZ] $reason"), true)
                }
                return
            }

            val definition = WeaponRuntime.registry().snapshot().findDefinition(gunId)
            val maxMagazine = definition?.spec?.magazineSize?.coerceAtLeast(1) ?: DEFAULT_MAGAZINE_SIZE
            val targetMagazine = message.ammoInMagazine.coerceIn(0, maxMagazine)
            val targetReserve = message.ammoReserve.coerceAtLeast(0)

            WeaponItemStackRuntimeData.writeAmmoState(
                stack = gunStack,
                ammoInMagazine = targetMagazine,
                ammoReserve = targetReserve,
                hasBulletInBarrel = targetMagazine > 0
            )

            val sessionId = WeaponRuntimeMcBridge.serverSessionIdForPlayer(player.uniqueID.toString())
            val service = WeaponRuntimeMcBridge.sessionServiceOrNull()
            val snapshot = service?.snapshot(sessionId)
            if (snapshot != null) {
                service.upsertAuthoritativeSnapshot(
                    sessionId = sessionId,
                    gunId = gunId,
                    snapshot = snapshot.copy(
                        ammoInMagazine = targetMagazine,
                        ammoReserve = targetReserve
                    ),
                    allowFallbackDefinition = true
                )
            }

            player.inventoryContainer.detectAndSendChanges()
            LegacyNetworkHandler.sendWeaponWorkbenchRefreshToClient(player)
            player.sendStatusMessage(
                TextComponentString("[TACZ] 弹药已更新: ${targetMagazine}/${maxMagazine} | 备弹 $targetReserve"),
                true
            )
        }

        private fun resolveHeldGunStack(player: EntityPlayerMP): ItemStack? {
            val mainHand = player.heldItemMainhand
            if (!mainHand.isEmpty && mainHand.item is LegacyGunItem) {
                return mainHand
            }
            val offHand = player.heldItemOffhand
            if (!offHand.isEmpty && offHand.item is LegacyGunItem) {
                return offHand
            }
            return null
        }
    }

    private companion object {
        private const val DEFAULT_MAGAZINE_SIZE: Int = 30
    }
}
