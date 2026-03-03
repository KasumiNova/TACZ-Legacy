package com.tacz.legacy.common.domain.gunpack

public data class GunData(
    val sourceId: String,
    val gunId: String,
    val ammoId: String,
    val ammoAmount: Int,
    val extendedMagAmmoAmount: List<Int>?,
    val canCrawl: Boolean,
    val canSlide: Boolean,
    val boltType: GunBoltType,
    val roundsPerMinute: Int,
    val aimTimeSeconds: Float = GunDefaults.AIM_TIME_SECONDS,
    val fireModes: Set<GunFireMode>,
    val crawlRecoilMultiplier: Float = GunDefaults.CRAWL_RECOIL_MULTIPLIER,
    val recoil: GunRecoilData = GunRecoilData(),
    val inaccuracy: GunInaccuracyData = GunInaccuracyData(),
    val bullet: GunBulletData,
    val reload: GunReloadData,
    val scriptParams: Map<String, Float> = emptyMap()
)

public data class GunRecoilData(
    val pitch: List<GunRecoilKeyFrameData> = emptyList(),
    val yaw: List<GunRecoilKeyFrameData> = emptyList()
)

public data class GunRecoilKeyFrameData(
    val timeSeconds: Float,
    val valueMin: Float,
    val valueMax: Float
)

public data class GunInaccuracyData(
    val stand: Float = GunDefaults.INACCURACY_STAND,
    val move: Float = GunDefaults.INACCURACY_MOVE,
    val sneak: Float = GunDefaults.INACCURACY_SNEAK,
    val lie: Float = GunDefaults.INACCURACY_LIE,
    val aim: Float = GunDefaults.INACCURACY_AIM
)

public data class GunBulletData(
    val lifeSeconds: Float,
    val bulletAmount: Int,
    val damage: Float,
    val speed: Float,
    val gravity: Float,
    val pierce: Int,
    val friction: Float = GunDefaults.BULLET_FRICTION,
    val extraDamage: GunExtraDamageData = GunExtraDamageData()
)

public data class GunExtraDamageData(
    val armorIgnore: Float = 0f,
    val headShotMultiplier: Float = 1f,
    val damageAdjust: List<GunDistanceDamagePair> = emptyList()
)

public data class GunDistanceDamagePair(
    val distance: Float,
    val damage: Float
)

public data class GunReloadData(
    val type: GunFeedType,
    val infinite: Boolean,
    val emptyTimeSeconds: Float,
    val tacticalTimeSeconds: Float
)

public enum class GunBoltType {
    OPEN_BOLT,
    CLOSED_BOLT,
    MANUAL_ACTION;

    public companion object {
        public fun fromSerialized(value: String): GunBoltType? =
            when (value.lowercase()) {
                "open_bolt" -> OPEN_BOLT
                "closed_bolt" -> CLOSED_BOLT
                "manual_action" -> MANUAL_ACTION
                else -> null
            }
    }
}

public enum class GunFireMode {
    AUTO,
    SEMI,
    BURST,
    UNKNOWN;

    public companion object {
        public fun fromSerialized(value: String): GunFireMode? =
            when (value.lowercase()) {
                "auto", "automatic", "full_auto" -> AUTO
                "semi", "semi_auto" -> SEMI
                "burst" -> BURST
                "unknown" -> UNKNOWN
                else -> null
            }
    }
}

public enum class GunFeedType {
    MAGAZINE,
    MANUAL,
    FUEL,
    INVENTORY;

    public companion object {
        public fun fromSerialized(value: String): GunFeedType? =
            when (value.lowercase()) {
                "magazine" -> MAGAZINE
                "manual" -> MANUAL
                "fuel" -> FUEL
                "inventory" -> INVENTORY
                else -> null
            }
    }
}

public object GunDefaults {
    public const val AMMO_AMOUNT: Int = 30
    public const val RPM: Int = 300
    public const val AIM_TIME_SECONDS: Float = 0.2f
    public const val CRAWL_RECOIL_MULTIPLIER: Float = 0.5f
    public const val INACCURACY_STAND: Float = 4.5f
    public const val INACCURACY_MOVE: Float = 5.0f
    public const val INACCURACY_SNEAK: Float = 2.5f
    public const val INACCURACY_LIE: Float = 1.5f
    public const val INACCURACY_AIM: Float = 0.15f
    public const val BULLET_LIFE_SECONDS: Float = 10.0f
    public const val BULLET_AMOUNT: Int = 1
    public const val BULLET_DAMAGE: Float = 5.0f
    public const val BULLET_SPEED: Float = 5.0f
    public const val BULLET_GRAVITY: Float = 0.0f
    public const val BULLET_FRICTION: Float = 0.01f
    public const val BULLET_PIERCE: Int = 1
    public const val RELOAD_EMPTY_TIME: Float = 2.5f
    public const val RELOAD_TACTICAL_TIME: Float = 2.0f
}