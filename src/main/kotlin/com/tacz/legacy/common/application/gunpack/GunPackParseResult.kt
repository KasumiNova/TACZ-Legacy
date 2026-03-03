package com.tacz.legacy.common.application.gunpack

import com.tacz.legacy.common.domain.gunpack.GunPackCompatibilityReport
import com.tacz.legacy.common.domain.gunpack.GunData

public data class GunPackParseResult(
    val gunData: GunData?,
    val report: GunPackCompatibilityReport
)