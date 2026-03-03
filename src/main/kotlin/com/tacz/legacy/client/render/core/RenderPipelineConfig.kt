package com.tacz.legacy.client.render.core

public data class RenderPipelineConfig(
    val enableNewPipeline: Boolean = true,
    val enableVanillaFallback: Boolean = true,
    val disableMovementFovEffectWhenHoldingGun: Boolean = true,
    val enableDebugHud: Boolean = false,
    val enableModelPreviewOverlay: Boolean = false,
    val enableSpecialBlockModelProbe: Boolean = false,
    val enableDiagnostics: Boolean = true,
    val enablePhaseCompensation: Boolean = true
)
