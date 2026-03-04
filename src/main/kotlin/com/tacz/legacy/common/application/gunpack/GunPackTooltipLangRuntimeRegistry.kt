package com.tacz.legacy.common.application.gunpack

public data class GunPackTooltipLangSnapshot(
    val loadedAtEpochMillis: Long,
    val valuesByLocale: Map<String, Map<String, String>>,
    val failedSources: Set<String> = emptySet()
) {

    public val totalLocales: Int
        get() = valuesByLocale.size

    public val totalEntries: Int
        get() = valuesByLocale.values.sumOf { it.size }

    public fun resolve(locale: String?, key: String?): String? {
        val normalizedLocale = normalizeLocale(locale) ?: return null
        val normalizedKey = key?.trim()?.ifBlank { null } ?: return null
        return valuesByLocale[normalizedLocale]?.get(normalizedKey)
    }

    public fun resolvePreferred(
        key: String?,
        preferredLocales: List<String> = DEFAULT_LOCALE_FALLBACK
    ): String? {
        val normalizedKey = key?.trim()?.ifBlank { null } ?: return null
        preferredLocales.forEach { locale ->
            val value = resolve(locale, normalizedKey)
            if (!value.isNullOrBlank()) {
                return value
            }
        }

        return valuesByLocale.values.asSequence()
            .mapNotNull { localeMap -> localeMap[normalizedKey] }
            .firstOrNull { it.isNotBlank() }
    }

    public companion object {
        public val DEFAULT_LOCALE_FALLBACK: List<String> = listOf("zh_cn", "en_us")

        public fun empty(atEpochMillis: Long = System.currentTimeMillis()): GunPackTooltipLangSnapshot =
            GunPackTooltipLangSnapshot(
                loadedAtEpochMillis = atEpochMillis,
                valuesByLocale = emptyMap(),
                failedSources = emptySet()
            )

        internal fun normalizeLocale(raw: String?): String? {
            val normalized = raw
                ?.trim()
                ?.lowercase()
                ?.replace('-', '_')
                ?.ifBlank { null }
                ?: return null
            return if (LOCALE_REGEX.matches(normalized)) normalized else null
        }

        private val LOCALE_REGEX: Regex = Regex("^[a-z]{2}_[a-z]{2}$")
    }
}

public class GunPackTooltipLangRuntimeRegistry {

    @Volatile
    private var latestSnapshot: GunPackTooltipLangSnapshot = GunPackTooltipLangSnapshot.empty()

    @Synchronized
    public fun replace(snapshot: GunPackTooltipLangSnapshot): GunPackTooltipLangSnapshot {
        latestSnapshot = snapshot
        return latestSnapshot
    }

    @Synchronized
    public fun clear(): GunPackTooltipLangSnapshot {
        latestSnapshot = GunPackTooltipLangSnapshot.empty()
        return latestSnapshot
    }

    public fun snapshot(): GunPackTooltipLangSnapshot = latestSnapshot
}

public object GunPackTooltipLangRuntime {

    private val registry: GunPackTooltipLangRuntimeRegistry = GunPackTooltipLangRuntimeRegistry()

    public fun registry(): GunPackTooltipLangRuntimeRegistry = registry
}
