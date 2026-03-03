package com.tacz.legacy.client.input

public enum class WeaponAimStateSource {
    CLIENT_INPUT,
    EXTERNAL_SYNC
}

public data class WeaponAimInputStateSnapshot(
    val isAiming: Boolean,
    val source: WeaponAimStateSource,
    val updatedAtMillis: Long
)

public object WeaponAimInputStateRegistry {

    private val stateBySessionId: MutableMap<String, WeaponAimInputStateSnapshot> = linkedMapOf()

    @Synchronized
    public fun updateFromClientInput(
        sessionId: String,
        isAiming: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        update(sessionId, isAiming, WeaponAimStateSource.CLIENT_INPUT, nowMillis)
    }

    @Synchronized
    public fun updateFromExternalSync(
        sessionId: String,
        isAiming: Boolean,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        update(sessionId, isAiming, WeaponAimStateSource.EXTERNAL_SYNC, nowMillis)
    }

    @Synchronized
    public fun resolve(
        sessionId: String,
        nowMillis: Long = System.currentTimeMillis(),
        staleAfterMillis: Long = DEFAULT_STALE_AFTER_MILLIS
    ): Boolean? {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return null
        val snapshot = stateBySessionId[normalizedSessionId] ?: return null
        val elapsed = nowMillis - snapshot.updatedAtMillis
        if (staleAfterMillis >= 0L && elapsed > staleAfterMillis) {
            return null
        }
        return snapshot.isAiming
    }

    @Synchronized
    public fun clearSession(sessionId: String) {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return
        stateBySessionId.remove(normalizedSessionId)
    }

    @Synchronized
    public fun clearAll() {
        stateBySessionId.clear()
    }

    @Synchronized
    internal fun snapshot(sessionId: String): WeaponAimInputStateSnapshot? {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return null
        return stateBySessionId[normalizedSessionId]
    }

    private fun update(
        sessionId: String,
        isAiming: Boolean,
        source: WeaponAimStateSource,
        nowMillis: Long
    ) {
        val normalizedSessionId = normalizeSessionId(sessionId) ?: return
        stateBySessionId[normalizedSessionId] = WeaponAimInputStateSnapshot(
            isAiming = isAiming,
            source = source,
            updatedAtMillis = nowMillis
        )
    }

    private fun normalizeSessionId(sessionId: String?): String? {
        return sessionId?.trim()?.ifBlank { null }
    }

}

private const val DEFAULT_STALE_AFTER_MILLIS: Long = 300L
