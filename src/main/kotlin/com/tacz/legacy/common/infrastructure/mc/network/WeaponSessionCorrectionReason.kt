package com.tacz.legacy.common.infrastructure.mc.network

public enum class WeaponSessionCorrectionReason(public val code: Byte) {
    PERIODIC(0),
    INPUT_ACCEPTED(1),
    INPUT_REJECTED(2),
    NO_SESSION(3),
    TIMESTAMP_OUT_OF_WINDOW(4),
    SHOOT_COOLDOWN(5),
    UNKNOWN(127);

    public companion object {
        public fun fromCode(code: Byte): WeaponSessionCorrectionReason =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}
