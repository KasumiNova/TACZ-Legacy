package com.tacz.legacy.client.render.frame

public enum class FramePhase {
    PREPARE,
    PRE_UPDATE,
    UPDATE,
    RENDER_OPAQUE,
    RENDER_TRANSLUCENT,
    POST_UPDATE,
    RENDER_OVERLAY;

    public companion object {
        public fun defaultOrder(): List<FramePhase> = entries.toList()
    }
}
