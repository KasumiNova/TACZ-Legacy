package com.tacz.legacy.common.infrastructure.mc.network

import com.tacz.legacy.common.application.weapon.WeaponSessionDebugSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponSnapshot
import com.tacz.legacy.common.domain.weapon.WeaponState
import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponRuntimeMcBridge
import io.netty.buffer.ByteBuf
import net.minecraftforge.fml.common.FMLCommonHandler
import net.minecraftforge.fml.common.network.ByteBufUtils
import net.minecraftforge.fml.common.network.simpleimpl.IMessage
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext

public data class WeaponSessionSyncSnapshot(
    val sessionId: String,
    val sourceId: String,
    val gunId: String,
    val snapshot: WeaponSnapshot,
    val ackSequenceId: Int,
    val correctionReason: WeaponSessionCorrectionReason,
    val syncedAtEpochMillis: Long
)

public class PacketWeaponSessionSync() : IMessage {

    public var sessionId: String = ""
    public var sourceId: String = ""
    public var gunId: String = ""
    public var stateOrdinal: Byte = 0
    public var ammoInMagazine: Int = 0
    public var ammoReserve: Int = 0
    public var isTriggerHeld: Boolean = false
    public var reloadTicksRemaining: Int = 0
    public var cooldownTicksRemaining: Int = 0
    public var semiLocked: Boolean = false
    public var burstShotsRemaining: Int = 0
    public var totalShotsFired: Int = 0
    public var ackSequenceId: Int = -1
    public var correctionReasonCode: Byte = WeaponSessionCorrectionReason.PERIODIC.code
    public var syncedAtEpochMillis: Long = 0L

    override fun fromBytes(buf: ByteBuf) {
        sessionId = ByteBufUtils.readUTF8String(buf)
        sourceId = ByteBufUtils.readUTF8String(buf)
        gunId = ByteBufUtils.readUTF8String(buf)
        stateOrdinal = buf.readByte()
        ammoInMagazine = buf.readInt()
        ammoReserve = buf.readInt()
        isTriggerHeld = buf.readBoolean()
        reloadTicksRemaining = buf.readInt()
        cooldownTicksRemaining = buf.readInt()
        semiLocked = buf.readBoolean()
        burstShotsRemaining = buf.readInt()
        totalShotsFired = buf.readInt()
        ackSequenceId = buf.readInt()
        correctionReasonCode = buf.readByte()
        syncedAtEpochMillis = buf.readLong()
    }

    override fun toBytes(buf: ByteBuf) {
        ByteBufUtils.writeUTF8String(buf, sessionId)
        ByteBufUtils.writeUTF8String(buf, sourceId)
        ByteBufUtils.writeUTF8String(buf, gunId)
        buf.writeByte(stateOrdinal.toInt())
        buf.writeInt(ammoInMagazine)
        buf.writeInt(ammoReserve)
        buf.writeBoolean(isTriggerHeld)
        buf.writeInt(reloadTicksRemaining)
        buf.writeInt(cooldownTicksRemaining)
        buf.writeBoolean(semiLocked)
        buf.writeInt(burstShotsRemaining)
        buf.writeInt(totalShotsFired)
        buf.writeInt(ackSequenceId)
        buf.writeByte(correctionReasonCode.toInt())
        buf.writeLong(syncedAtEpochMillis)
    }

    public fun toSnapshotOrNull(): WeaponSessionSyncSnapshot? {
        val state = WeaponState.entries.getOrNull(stateOrdinal.toInt()) ?: return null
        val normalizedSessionId = sessionId.trim().ifBlank { return null }
        val normalizedSourceId = sourceId.trim().ifBlank { return null }
        val normalizedGunId = gunId.trim().ifBlank { return null }
        val correctionReason = WeaponSessionCorrectionReason.fromCode(correctionReasonCode)

        return WeaponSessionSyncSnapshot(
            sessionId = normalizedSessionId,
            sourceId = normalizedSourceId,
            gunId = normalizedGunId,
            snapshot = WeaponSnapshot(
                state = state,
                ammoInMagazine = ammoInMagazine,
                ammoReserve = ammoReserve,
                isTriggerHeld = isTriggerHeld,
                reloadTicksRemaining = reloadTicksRemaining,
                cooldownTicksRemaining = cooldownTicksRemaining,
                semiLocked = semiLocked,
                burstShotsRemaining = burstShotsRemaining,
                totalShotsFired = totalShotsFired
            ),
            ackSequenceId = ackSequenceId,
            correctionReason = correctionReason,
            syncedAtEpochMillis = syncedAtEpochMillis
        )
    }

    public class Handler : IMessageHandler<PacketWeaponSessionSync, IMessage> {

        override fun onMessage(message: PacketWeaponSessionSync, ctx: MessageContext): IMessage? {
            val snapshot = message.toSnapshotOrNull() ?: return null
            val worldThread = FMLCommonHandler.instance().getWorldThread(ctx.netHandler)
            worldThread.addScheduledTask {
                WeaponSessionSyncClientRegistry.upsert(snapshot)
                val clientSessionId = toClientSessionId(snapshot.sessionId)
                WeaponRuntimeMcBridge.reconcileClientSessionSnapshot(
                    sessionId = clientSessionId,
                    gunId = snapshot.gunId,
                    snapshot = snapshot.snapshot
                )
            }
            return null
        }

        private fun toClientSessionId(serverSessionId: String): String =
            "$serverSessionId:client"

    }

    public companion object {

        public fun fromDebugSnapshot(
            sessionId: String,
            debugSnapshot: WeaponSessionDebugSnapshot,
            ackSequenceId: Int = -1,
            correctionReason: WeaponSessionCorrectionReason = WeaponSessionCorrectionReason.PERIODIC,
            syncedAtEpochMillis: Long = System.currentTimeMillis()
        ): PacketWeaponSessionSync {
            val snapshot = debugSnapshot.snapshot
            return PacketWeaponSessionSync().apply {
                this.sessionId = sessionId
                this.sourceId = debugSnapshot.sourceId
                this.gunId = debugSnapshot.gunId
                this.stateOrdinal = snapshot.state.ordinal.toByte()
                this.ammoInMagazine = snapshot.ammoInMagazine
                this.ammoReserve = snapshot.ammoReserve
                this.isTriggerHeld = snapshot.isTriggerHeld
                this.reloadTicksRemaining = snapshot.reloadTicksRemaining
                this.cooldownTicksRemaining = snapshot.cooldownTicksRemaining
                this.semiLocked = snapshot.semiLocked
                this.burstShotsRemaining = snapshot.burstShotsRemaining
                this.totalShotsFired = snapshot.totalShotsFired
                this.ackSequenceId = ackSequenceId
                this.correctionReasonCode = correctionReason.code
                this.syncedAtEpochMillis = syncedAtEpochMillis
            }
        }

    }

}
