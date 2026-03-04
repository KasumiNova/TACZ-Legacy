package com.tacz.legacy.client.gui

import com.tacz.legacy.common.infrastructure.mc.weapon.WeaponAttachmentSlot
import kotlin.math.pow

public object WeaponGunsmithImmersiveRuntime {

    private const val REFIT_TRANSFORM_SECONDS: Float = 0.25f
    private const val MAX_TICK_DELTA_MILLIS: Long = 250L

    @Volatile
    private var immersiveGunId: String? = null

    /**
     * 专供渲染层的 RefitTransform 过渡：允许在退出 GUI 后继续一小段“closing”动画。
     *
     * 注意：它不代表“沉浸式模式仍在启用”，不会用于输入抑制。
     */
    @Volatile
    private var refitGunId: String? = null

    @Volatile
    private var closing: Boolean = false

    @Volatile
    private var focusSlot: WeaponAttachmentSlot? = null

    @Volatile
    private var openingProgress: Float = 0.0f

    @Volatile
    private var openingTimestampMillis: Long = -1L

    @Volatile
    private var viewTransformProgress: Float = 1.0f

    @Volatile
    private var viewTransformTimestampMillis: Long = -1L

    @Volatile
    private var viewOldSlot: WeaponAttachmentSlot? = null

    @Volatile
    private var viewCurrentSlot: WeaponAttachmentSlot? = null

    @Volatile
    private var focusTarget: ModelFocusTransform = ModelFocusTransform.identity()

    @Volatile
    private var focusCurrent: ModelFocusTransform = ModelFocusTransform.identity()

    @Volatile
    private var focusTimestampMillis: Long = -1L

    public data class ModelFocusTransform(
        val translateX: Float,
        val translateY: Float,
        val translateZ: Float,
        val rotateXDegrees: Float,
        val rotateYDegrees: Float,
        val rotateZDegrees: Float,
        val uniformScale: Float
    ) {
        public companion object {
            public fun identity(): ModelFocusTransform {
                return ModelFocusTransform(
                    translateX = 0.0f,
                    translateY = 0.0f,
                    translateZ = 0.0f,
                    rotateXDegrees = 0.0f,
                    rotateYDegrees = 0.0f,
                    rotateZDegrees = 0.0f,
                    uniformScale = 1.0f
                )
            }
        }
    }

    public data class Snapshot(
        val enabled: Boolean,
        val gunId: String?
    )

    /**
     * 渲染层读取：对齐 TACZ 1.20 的 RefitTransform。
     * - openingProgress: 打开改枪界面时从 0→1（关闭时 1→0）
     * - oldSlot/currentSlot + transformProgress: 选择槽位时从旧视角插值到新视角
     */
    public data class RefitViewState(
        val openingProgress: Float,
        val oldSlot: WeaponAttachmentSlot?,
        val currentSlot: WeaponAttachmentSlot?,
        val transformProgress: Float
    )

    public fun snapshot(): Snapshot {
        val gun = immersiveGunId
        return Snapshot(enabled = gun != null, gunId = gun)
    }

    public fun toggle(currentGunId: String?): Snapshot {
        val normalizedGunId = normalizeGunId(currentGunId)
        if (normalizedGunId == null) {
            immersiveGunId = null
            clearRefitState()
            return snapshot()
        }

        immersiveGunId = if (immersiveGunId == normalizedGunId) null else normalizedGunId
        if (immersiveGunId == null) {
            // toggle 语义保持“立即退出”，不走 closing 动画（用于调试/快速切换）
            clearRefitState()
        } else {
            refitGunId = immersiveGunId
            beginOpeningAnimation()
        }
        return snapshot()
    }

    public fun activate(currentGunId: String?): Snapshot {
        val normalizedGunId = normalizeGunId(currentGunId)
        val previous = immersiveGunId
        immersiveGunId = normalizedGunId
        if (normalizedGunId == null) {
            clearRefitState()
            return snapshot()
        }

        if (previous != normalizedGunId) {
            // 换枪进入改枪：对齐 TACZ init() 的重置语义
            clearRefitState(clearActiveGun = false)
        }

        refitGunId = normalizedGunId
        beginOpeningAnimation()
        return snapshot()
    }

    public fun isActiveForGun(currentGunId: String?): Boolean {
        val active = immersiveGunId ?: return false
        val normalizedCurrent = normalizeGunId(currentGunId) ?: return false
        return active == normalizedCurrent
    }

    public fun deactivateIfGunChanged(currentGunId: String?) {
        val active = refitGunId ?: return
        val normalizedCurrent = normalizeGunId(currentGunId)
        if (normalizedCurrent != active) {
            immersiveGunId = null
            clearRefitState()
        }
    }

    public fun deactivate() {
        immersiveGunId = null
        clearRefitState()
    }

    /**
     * 退出沉浸式界面：立即结束“沉浸式启用”（恢复输入/瞄准等逻辑），
     * 但保留 refit 渲染状态并反向播放 openingProgress 以做 closing 过渡。
     */
    public fun requestClose() {
        // 先立即退出沉浸式语义（避免输入抑制卡住）
        immersiveGunId = null

        // 关闭时不再叠加“自动聚焦”
        focusSlot = null
        focusTarget = ModelFocusTransform.identity()
        focusCurrent = ModelFocusTransform.identity()
        focusTimestampMillis = -1L

        // 若没有 refit 过渡目标，直接清理
        if (refitGunId == null) {
            clearRefitState()
            return
        }

        closing = true
        openingTimestampMillis = -1L
        // 槽位视角插值允许自然收敛，不强制重置
        viewTransformTimestampMillis = -1L
    }

    internal fun resetForTests() {
        immersiveGunId = null
        clearRefitState()
    }

    /**
     * 由沉浸式改枪 UI 驱动的“自动模型聚焦”。
     *
     * 注意：这里的变换会在渲染层直接叠加到 FPS 枪模矩阵上，
     * 目标是模拟 TACZ 的“选择槽位后自动把枪械部位移到视野中心”。
     */
    public fun setModelFocusSlot(slot: WeaponAttachmentSlot?) {
        // 视角切换插值（对齐 TACZ changeRefitScreenView）
        if (immersiveGunId != null && slot != viewCurrentSlot) {
            viewOldSlot = viewCurrentSlot
            viewCurrentSlot = slot
            viewTransformProgress = 0.0f
            viewTransformTimestampMillis = -1L
        }

        focusSlot = slot
        focusTarget = resolveFocusTarget(slot)
    }

    /**
     * 在 GUI 的 tick（updateScreen）里调用即可，做简单平滑插值。
     */
    public fun tickModelFocus() {
        if (immersiveGunId == null) {
            return
        }

        val now = System.currentTimeMillis()
        tickRefitInterpolation(now)
        tickFocusInterpolation(now)
    }

    public fun resolveRefitViewStateForGun(currentGunId: String?): RefitViewState? {
        val active = refitGunId ?: return null
        val normalizedCurrent = normalizeGunId(currentGunId) ?: return null
        if (active != normalizedCurrent) {
            return null
        }

        // 让渲染读取也能推动插值，避免被 20tps 锁帧。
        tickRefitInterpolation(System.currentTimeMillis())
        return RefitViewState(
            openingProgress = openingProgress.coerceIn(0.0f, 1.0f),
            oldSlot = viewOldSlot,
            currentSlot = viewCurrentSlot,
            transformProgress = viewTransformProgress.coerceIn(0.0f, 1.0f)
        )
    }

    /**
     * 渲染层读取：若未启用沉浸式或未选中槽位，返回 null。
     */
    public fun resolveModelFocusTransform(): ModelFocusTransform? {
        if (immersiveGunId == null) {
            return null
        }
        if (focusSlot == null) {
            return null
        }

        // 同样用渲染读取推进插值，消除聚焦“跳 20fps”。
        tickFocusInterpolation(System.currentTimeMillis())
        return focusCurrent
    }

    private fun beginOpeningAnimation(nowMillis: Long = System.currentTimeMillis()) {
        closing = false
        if (refitGunId == null) {
            refitGunId = immersiveGunId
        }
        openingTimestampMillis = nowMillis
        // 如果是重新进入同一把枪的改枪界面，openingProgress 允许从 0 重新爬升
        if (openingProgress <= 0.0f) {
            openingProgress = 0.0f
        }
    }

    private fun tickRefitInterpolation(nowMillis: Long) {
        if (refitGunId == null) {
            return
        }

        // opening progress（界面存在时从 0→1；关闭时 1→0）
        if (openingTimestampMillis <= 0L) {
            openingTimestampMillis = nowMillis
        }
        val openingDeltaMillis = (nowMillis - openingTimestampMillis)
            .coerceIn(0L, MAX_TICK_DELTA_MILLIS)

        val openingStep = openingDeltaMillis.toFloat() / (REFIT_TRANSFORM_SECONDS * 1000.0f)
        openingProgress = if (closing) {
            (openingProgress - openingStep)
        } else {
            (openingProgress + openingStep)
        }
        if (openingProgress < 0.0f) {
            openingProgress = 0.0f
        }
        if (openingProgress > 1.0f) {
            openingProgress = 1.0f
        }
        openingTimestampMillis = nowMillis

        // closing 结束：彻底清理 refit 状态
        if (closing && openingProgress <= 0.0f) {
            clearRefitState()
            return
        }

        // transform progress（槽位切换时从 0→1；默认维持 1）
        if (viewTransformTimestampMillis <= 0L) {
            viewTransformTimestampMillis = nowMillis
        }
        val transformDeltaMillis = (nowMillis - viewTransformTimestampMillis)
            .coerceIn(0L, MAX_TICK_DELTA_MILLIS)
        if (viewTransformProgress < 1.0f) {
            viewTransformProgress += transformDeltaMillis.toFloat() / (REFIT_TRANSFORM_SECONDS * 1000.0f)
            if (viewTransformProgress > 1.0f) {
                viewTransformProgress = 1.0f
            }
        }
        viewTransformTimestampMillis = nowMillis
    }

    private fun tickFocusInterpolation(nowMillis: Long) {
        if (focusSlot == null) {
            return
        }

        if (focusTimestampMillis <= 0L) {
            focusTimestampMillis = nowMillis
            return
        }

        val deltaMillis = (nowMillis - focusTimestampMillis)
            .coerceIn(0L, MAX_TICK_DELTA_MILLIS)
        focusTimestampMillis = nowMillis

        // 以“每 50ms(1 tick) alpha=0.35”为基准，按真实 delta 做指数平滑。
        val baseAlphaPerTick = 0.35f
        val ticks = (deltaMillis.toFloat() / 50.0f).coerceIn(0.0f, 5.0f)
        val decay = (1.0 - baseAlphaPerTick.toDouble()).coerceIn(0.0, 1.0)
        val alpha = (1.0 - decay.pow(ticks.toDouble())).toFloat().coerceIn(0.0f, 1.0f)
        focusCurrent = lerp(focusCurrent, focusTarget, alpha)
    }

    private fun clearRefitState(clearActiveGun: Boolean = true) {
        if (clearActiveGun) {
            immersiveGunId = null
        }
        refitGunId = null
        closing = false
        focusSlot = null
        focusTarget = ModelFocusTransform.identity()
        focusCurrent = ModelFocusTransform.identity()
        focusTimestampMillis = -1L

        openingProgress = 0.0f
        openingTimestampMillis = -1L
        viewTransformProgress = 1.0f
        viewTransformTimestampMillis = -1L
        viewOldSlot = null
        viewCurrentSlot = null
    }

    private fun resolveFocusTarget(slot: WeaponAttachmentSlot?): ModelFocusTransform {
        if (slot == null) {
            return ModelFocusTransform.identity()
        }

        // 这些值是“温和的默认聚焦配置”，后续可按枪包/骨骼点进一步细化。
        return when (slot) {
            WeaponAttachmentSlot.SCOPE -> ModelFocusTransform(
                translateX = 0.0f,
                translateY = -0.015f,
                translateZ = 0.085f,
                rotateXDegrees = 6.0f,
                rotateYDegrees = 0.0f,
                rotateZDegrees = 0.0f,
                uniformScale = 1.10f
            )

            WeaponAttachmentSlot.MUZZLE -> ModelFocusTransform(
                translateX = -0.040f,
                translateY = -0.010f,
                translateZ = 0.060f,
                rotateXDegrees = 2.0f,
                rotateYDegrees = 10.0f,
                rotateZDegrees = 0.0f,
                uniformScale = 1.06f
            )

            WeaponAttachmentSlot.LASER -> ModelFocusTransform(
                translateX = -0.015f,
                translateY = -0.020f,
                translateZ = 0.070f,
                rotateXDegrees = 10.0f,
                rotateYDegrees = 4.0f,
                rotateZDegrees = 0.0f,
                uniformScale = 1.08f
            )

            WeaponAttachmentSlot.GRIP -> ModelFocusTransform(
                translateX = 0.020f,
                translateY = -0.030f,
                translateZ = 0.055f,
                rotateXDegrees = 16.0f,
                rotateYDegrees = -2.0f,
                rotateZDegrees = 0.0f,
                uniformScale = 1.10f
            )

            WeaponAttachmentSlot.EXTENDED_MAG -> ModelFocusTransform(
                translateX = 0.030f,
                translateY = -0.035f,
                translateZ = 0.060f,
                rotateXDegrees = 12.0f,
                rotateYDegrees = -6.0f,
                rotateZDegrees = 0.0f,
                uniformScale = 1.10f
            )

            WeaponAttachmentSlot.STOCK -> ModelFocusTransform(
                translateX = 0.065f,
                translateY = -0.015f,
                translateZ = 0.035f,
                rotateXDegrees = 4.0f,
                rotateYDegrees = -12.0f,
                rotateZDegrees = 0.0f,
                uniformScale = 1.06f
            )
        }
    }

    private fun lerp(current: ModelFocusTransform, target: ModelFocusTransform, alpha: Float): ModelFocusTransform {
        fun lerp1(a: Float, b: Float): Float = a + (b - a) * alpha
        return ModelFocusTransform(
            translateX = lerp1(current.translateX, target.translateX),
            translateY = lerp1(current.translateY, target.translateY),
            translateZ = lerp1(current.translateZ, target.translateZ),
            rotateXDegrees = lerp1(current.rotateXDegrees, target.rotateXDegrees),
            rotateYDegrees = lerp1(current.rotateYDegrees, target.rotateYDegrees),
            rotateZDegrees = lerp1(current.rotateZDegrees, target.rotateZDegrees),
            uniformScale = lerp1(current.uniformScale, target.uniformScale)
        )
    }

    private fun normalizeGunId(raw: String?): String? {
        return raw?.trim()?.lowercase()?.ifBlank { null }
    }
}
