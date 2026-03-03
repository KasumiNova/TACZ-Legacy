package com.tacz.legacy.common.infrastructure.mc.network

public data class WeaponSessionSyncReceipt(
    val sessionId: String,
    val ackSequenceId: Int,
    val correctionReason: WeaponSessionCorrectionReason,
    val syncedAtEpochMillis: Long
)

public object WeaponSessionSyncClientRegistry {

    private val snapshotsBySessionId: MutableMap<String, WeaponSessionSyncSnapshot> = linkedMapOf()
    private val receiptsBySessionId: MutableMap<String, WeaponSessionSyncReceipt> = linkedMapOf()

    @Synchronized
    public fun upsert(snapshot: WeaponSessionSyncSnapshot) {
        snapshotsBySessionId[snapshot.sessionId] = snapshot
        receiptsBySessionId[snapshot.sessionId] = WeaponSessionSyncReceipt(
            sessionId = snapshot.sessionId,
            ackSequenceId = snapshot.ackSequenceId,
            correctionReason = snapshot.correctionReason,
            syncedAtEpochMillis = snapshot.syncedAtEpochMillis
        )
    }

    @Synchronized
    public fun remove(sessionId: String): Boolean {
        val removedSnapshot = snapshotsBySessionId.remove(sessionId)
        val removedReceipt = receiptsBySessionId.remove(sessionId)
        return removedSnapshot != null || removedReceipt != null
    }

    @Synchronized
    public fun upsertReceipt(
        sessionId: String,
        ackSequenceId: Int,
        correctionReason: WeaponSessionCorrectionReason,
        syncedAtEpochMillis: Long = System.currentTimeMillis()
    ) {
        receiptsBySessionId[sessionId] = WeaponSessionSyncReceipt(
            sessionId = sessionId,
            ackSequenceId = ackSequenceId,
            correctionReason = correctionReason,
            syncedAtEpochMillis = syncedAtEpochMillis
        )
    }

    @Synchronized
    public fun clear() {
        snapshotsBySessionId.clear()
        receiptsBySessionId.clear()
    }

    @Synchronized
    public fun get(sessionId: String): WeaponSessionSyncSnapshot? = snapshotsBySessionId[sessionId]

    @Synchronized
    public fun receipt(sessionId: String): WeaponSessionSyncReceipt? = receiptsBySessionId[sessionId]

    @Synchronized
    public fun size(): Int = snapshotsBySessionId.size

    @Synchronized
    public fun allSessionIds(): List<String> = snapshotsBySessionId.keys.toList()

}
