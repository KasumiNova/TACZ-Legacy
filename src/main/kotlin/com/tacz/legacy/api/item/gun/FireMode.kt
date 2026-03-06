package com.tacz.legacy.api.item.gun

import com.google.gson.annotations.SerializedName

/**
 * 开火模式枚举，与上游 TACZ 保持一致。
 */
public enum class FireMode {
    @SerializedName("auto")
    AUTO,

    @SerializedName("semi")
    SEMI,

    @SerializedName("burst")
    BURST,

    @SerializedName("unknown")
    UNKNOWN,
}
