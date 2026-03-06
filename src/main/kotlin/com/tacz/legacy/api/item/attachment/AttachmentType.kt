package com.tacz.legacy.api.item.attachment

import com.google.gson.annotations.SerializedName
import java.util.Locale

public enum class AttachmentType(
    public val serializedName: String,
) {
    @SerializedName("scope")
    SCOPE("scope"),

    @SerializedName("muzzle")
    MUZZLE("muzzle"),

    @SerializedName("stock")
    STOCK("stock"),

    @SerializedName("grip")
    GRIP("grip"),

    @SerializedName("laser")
    LASER("laser"),

    @SerializedName("extended_mag")
    EXTENDED_MAG("extended_mag"),

    NONE("none");

    public companion object {
        @JvmStatic
        public fun fromSerializedName(rawValue: String?): AttachmentType {
            val normalized = rawValue?.trim()?.lowercase(Locale.ROOT).orEmpty()
            return values().firstOrNull { it.serializedName == normalized } ?: NONE
        }
    }
}