package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.infrastructure.mc.registry.item.LegacyGunItem
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentConflictRules
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponItemStackRuntimeData
import com.tacz.legacy.common.application.weapon.WeaponRuntime
import com.tacz.legacy.common.infrastructure.mc.workbench.WeaponWorkbenchSessionRegistry
import io.netty.buffer.ByteBuf
import net.minecraft.entity.player.EntityPlayerMP
import net.minecraft.item.ItemStack
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext
import java.nio.charset.StandardCharsets

public class PacketWeaponAttachmentAction() : IMessage {

    public var actionCode: Byte = CODE_INSTALL
    public var slotCode: Byte = SLOT_UNKNOWN
    public var attachmentId: String = ""

    public constructor(
        actionCode: Byte,
        slotCode: Byte,
        attachmentId: String = ""
    ) : this() {
        this.actionCode = actionCode
        this.slotCode = slotCode
        this.attachmentId = attachmentId
    }

    override fun fromBytes(buf: ByteBuf) {
        actionCode = buf.readByte()
        slotCode = buf.readByte()
        attachmentId = readString(buf)
    }

    override fun toBytes(buf: ByteBuf) {
        buf.writeByte(actionCode.toInt())
        buf.writeByte(slotCode.toInt())
        writeString(buf, attachmentId)
    }

    public class Handler : IMessageHandler<PacketWeaponAttachmentAction, IMessage> {

        override fun onMessage(message: PacketWeaponAttachmentAction, ctx: MessageContext): IMessage? {
            val player = ctx.serverHandler.player ?: return null
            player.serverWorld.addScheduledTask {
                handleMessage(player, message)
            }
            return null
        }

        private fun handleMessage(player: EntityPlayerMP, message: PacketWeaponAttachmentAction) {
            val action = decodeAction(message.actionCode) ?: return
            val slot = decodeSlot(message.slotCode) ?: return
            val gunStack = resolveHeldGunStack(player)
            if (gunStack == null) {
                player.sendStatusMessage(TextComponentString("[TACZ] 未检测到可装配配件的枪械"), true)
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

            when (action) {
                AttachmentAction.INSTALL -> {
                    val normalizedAttachmentId = message.attachmentId.trim().lowercase()
                    if (normalizedAttachmentId.isBlank()) {
                        player.sendStatusMessage(TextComponentString("[TACZ] 配件 ID 不能为空"), true)
                        return
                    }

                    val definition = WeaponRuntime.registry().snapshot().findDefinition(gunId)
                    if (definition == null) {
                        player.sendStatusMessage(TextComponentString("[TACZ] 未找到武器定义，无法安装配件"), true)
                        return
                    }

                    val snapshot = WeaponItemStackRuntimeData.readAttachmentSnapshot(gunStack)
                    val check = WeaponAttachmentConflictRules.validateInstall(
                        snapshot = snapshot,
                        slot = slot,
                        attachmentId = normalizedAttachmentId,
                        gunId = gunId,
                        definition = definition
                    )
                    if (!check.accepted) {
                        player.sendStatusMessage(
                            TextComponentString("[TACZ] ${check.reasonMessage ?: "配件安装失败"}"),
                            true
                        )
                        return
                    }

                    WeaponItemStackRuntimeData.writeAttachment(
                        stack = gunStack,
                        slot = slot,
                        attachmentId = normalizedAttachmentId
                    )
                    player.sendStatusMessage(
                        TextComponentString("[TACZ] 已安装 ${slot.name}: $normalizedAttachmentId"),
                        true
                    )
                }

                AttachmentAction.CLEAR -> {
                    WeaponItemStackRuntimeData.clearAttachment(
                        stack = gunStack,
                        slot = slot
                    )
                    player.sendStatusMessage(
                        TextComponentString("[TACZ] 已卸下 ${slot.name} 槽位配件"),
                        true
                    )
                }
            }

            player.inventoryContainer.detectAndSendChanges()
            LegacyNetworkHandler.sendWeaponWorkbenchRefreshToClient(player)
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

    private enum class AttachmentAction {
        INSTALL,
        CLEAR
    }

    public companion object {

        private const val CODE_INSTALL: Byte = 1
        private const val CODE_CLEAR: Byte = 2
        private const val SLOT_UNKNOWN: Byte = -1
        private const val MAX_ATTACHMENT_ID_BYTES: Int = 512

        public fun install(slot: WeaponAttachmentSlot, attachmentId: String): PacketWeaponAttachmentAction {
            return PacketWeaponAttachmentAction(
                actionCode = CODE_INSTALL,
                slotCode = encodeSlot(slot),
                attachmentId = attachmentId
            )
        }

        public fun clear(slot: WeaponAttachmentSlot): PacketWeaponAttachmentAction {
            return PacketWeaponAttachmentAction(
                actionCode = CODE_CLEAR,
                slotCode = encodeSlot(slot),
                attachmentId = ""
            )
        }

        internal fun encodeSlot(slot: WeaponAttachmentSlot): Byte = slot.ordinal.toByte()

        internal fun decodeSlot(slotCode: Byte): WeaponAttachmentSlot? {
            val index = slotCode.toInt()
            return WeaponAttachmentSlot.values().getOrNull(index)
        }

        private fun decodeAction(actionCode: Byte): AttachmentAction? = when (actionCode) {
            CODE_INSTALL -> AttachmentAction.INSTALL
            CODE_CLEAR -> AttachmentAction.CLEAR
            else -> null
        }

        private fun writeString(buf: ByteBuf, value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            val safeLength = bytes.size.coerceAtMost(MAX_ATTACHMENT_ID_BYTES)
            buf.writeShort(safeLength)
            buf.writeBytes(bytes, 0, safeLength)
        }

        private fun readString(buf: ByteBuf): String {
            if (buf.readableBytes() < 2) {
                return ""
            }

            val declaredLength = buf.readUnsignedShort()
            val readable = buf.readableBytes()
            val payloadLength = minOf(declaredLength, readable, MAX_ATTACHMENT_ID_BYTES)
            val bytes = ByteArray(payloadLength)
            buf.readBytes(bytes)

            val bytesToSkip = (declaredLength - payloadLength).coerceIn(0, buf.readableBytes())
            if (bytesToSkip > 0) {
                buf.skipBytes(bytesToSkip)
            }

            return String(bytes, StandardCharsets.UTF_8)
        }
    }
}
