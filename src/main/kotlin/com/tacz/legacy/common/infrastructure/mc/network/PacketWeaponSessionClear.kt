package com.tacz.legacy.common.infrastructure.mc.network

import io.netty.buffer.ByteBuf
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public class PacketWeaponSessionClear() : IMessage {

    public var sessionId: String = ""
    public var ackSequenceId: Int = -1
    public var correctionReasonCode: Byte = WeaponSessionCorrectionReason.NO_SESSION.code

    public constructor(
        sessionId: String,
        ackSequenceId: Int = -1,
        correctionReason: WeaponSessionCorrectionReason = WeaponSessionCorrectionReason.NO_SESSION
    ) : this() {
        this.sessionId = sessionId
        this.ackSequenceId = ackSequenceId
        this.correctionReasonCode = correctionReason.code
    }

    override fun fromBytes(buf: ByteBuf) {
        sessionId = ByteBufUtils.readUTF8String(buf)
        ackSequenceId = buf.readInt()
        correctionReasonCode = buf.readByte()
    }

    override fun toBytes(buf: ByteBuf) {
        ByteBufUtils.writeUTF8String(buf, sessionId)
        buf.writeInt(ackSequenceId)
        buf.writeByte(correctionReasonCode.toInt())
    }

    public class Handler : IMessageHandler<PacketWeaponSessionClear, IMessage> {

        override fun onMessage(message: PacketWeaponSessionClear, ctx: MessageContext): IMessage? {
            val normalizedSessionId = message.sessionId.trim().ifBlank { return null }
            val reason = WeaponSessionCorrectionReason.fromCode(message.correctionReasonCode)
            val worldThread = FMLCommonHandler.instance().getWorldThread(ctx.netHandler)
            val clientSessionId = toClientSessionId(normalizedSessionId)
            worldThread.addScheduledTask {
                WeaponSessionSyncClientRegistry.remove(normalizedSessionId)
                WeaponSessionSyncClientRegistry.upsertReceipt(
                    sessionId = normalizedSessionId,
                    ackSequenceId = message.ackSequenceId,
                    correctionReason = reason
                )
                WeaponRuntimeMcBridge.clearClientSession(clientSessionId)
            }
            return null
        }

        private fun toClientSessionId(serverSessionId: String): String =
            "$serverSessionId:client"

    }

}
