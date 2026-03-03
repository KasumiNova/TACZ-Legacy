package com.tacz.legacy.common.infrastructure.mc.registry

public object LegacyGunCategoryResolver {

    public fun resolveFromRawType(rawType: String?): LegacyGunTabType? {
        val normalized = rawType
            ?.trim()
            ?.lowercase()
            ?.substringAfter(':')
            ?.let(::normalizeToken)
            ?: return null
        return resolveByToken(normalized)
    }

    public fun resolve(gunId: String, sourceId: String): LegacyGunTabType {
        val fromPath = resolveFromSourcePath(sourceId)
        if (fromPath != null) {
            return fromPath
        }

        return resolveFromGunId(gunId)
    }

    internal fun resolveFromSourcePath(sourceId: String): LegacyGunTabType? {
        val normalizedSource = sourceId.trim().lowercase().replace('\\', '/')
        val markerIndex = normalizedSource.indexOf("/data/guns/")
        if (markerIndex < 0) {
            return null
        }

        val remainder = normalizedSource.substring(markerIndex + "/data/guns/".length)
        val folder = remainder.substringBefore('/').trim()
        if (folder.isEmpty() || folder.endsWith(".json")) {
            return null
        }

        return resolveByToken(folder)
    }

    internal fun resolveFromGunId(gunId: String): LegacyGunTabType {
        val token = normalizeToken(gunId)
        if (token.isEmpty()) {
            return LegacyGunTabType.UNKNOWN
        }

        return resolveByToken(token) ?: LegacyGunTabType.UNKNOWN
    }

    private fun resolveByToken(rawToken: String): LegacyGunTabType? {
        val token = normalizeToken(rawToken)
        if (token.isEmpty()) {
            return null
        }

        if (matchesAny(token, RPG_KEYWORDS)) {
            return LegacyGunTabType.RPG
        }
        if (matchesAny(token, SMG_KEYWORDS)) {
            return LegacyGunTabType.SMG
        }
        if (matchesAny(token, MG_KEYWORDS)) {
            return LegacyGunTabType.MG
        }
        if (matchesAny(token, SHOTGUN_KEYWORDS)) {
            return LegacyGunTabType.SHOTGUN
        }
        if (matchesAny(token, SNIPER_KEYWORDS)) {
            return LegacyGunTabType.SNIPER
        }
        if (matchesAny(token, PISTOL_KEYWORDS)) {
            return LegacyGunTabType.PISTOL
        }
        if (matchesAny(token, RIFLE_KEYWORDS)) {
            return LegacyGunTabType.RIFLE
        }

        return TAB_ALIAS[token]
    }

    private fun matchesAny(token: String, keywords: Set<String>): Boolean {
        return keywords.any { keyword ->
            token == keyword ||
                token.startsWith("${keyword}_") ||
                token.endsWith("_$keyword") ||
                token.contains("_${keyword}_") ||
                token.contains(keyword)
        }
    }

    private fun normalizeToken(raw: String): String {
        return raw.trim().lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')
    }

    private val TAB_ALIAS: Map<String, LegacyGunTabType> = mapOf(
        "pistol" to LegacyGunTabType.PISTOL,
        "sniper" to LegacyGunTabType.SNIPER,
        "rifle" to LegacyGunTabType.RIFLE,
        "shotgun" to LegacyGunTabType.SHOTGUN,
        "smg" to LegacyGunTabType.SMG,
        "rpg" to LegacyGunTabType.RPG,
        "mg" to LegacyGunTabType.MG,
        "lmg" to LegacyGunTabType.MG
    )

    private val PISTOL_KEYWORDS: Set<String> = setOf(
        "pistol", "handgun", "revolver", "glock", "usp", "deagle", "m1911", "m9"
    )

    private val SNIPER_KEYWORDS: Set<String> = setOf(
        "sniper", "awp", "svd", "barrett", "m24", "kar98", "dmr"
    )

    private val RIFLE_KEYWORDS: Set<String> = setOf(
        "rifle", "ak", "m4", "hk416", "scar", "famas", "aug", "g36", "qbz", "galil"
    )

    private val SHOTGUN_KEYWORDS: Set<String> = setOf(
        "shotgun", "spas", "m870", "s12k", "aa12", "ks23", "db"
    )

    private val SMG_KEYWORDS: Set<String> = setOf(
        "smg", "mp5", "ump", "vector", "uzi", "pp19", "p90", "mp7", "mac10"
    )

    private val RPG_KEYWORDS: Set<String> = setOf(
        "rpg", "rocket", "bazooka", "at4", "law", "panzer", "grenade_launcher", "m79"
    )

    private val MG_KEYWORDS: Set<String> = setOf(
        "mg", "lmg", "machinegun", "machine_gun", "m249", "pkm", "minigun", "mg42"
    )
}
