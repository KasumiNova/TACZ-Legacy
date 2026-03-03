package com.tacz.legacy.client.render.draw

public data class DrawCommandExecutionReport(
    val submitted: Int,
    val executed: Int,
    val failed: Int,
    val failedIds: List<String>
) {

    public val consumed: Int
        get() = executed + failed

}