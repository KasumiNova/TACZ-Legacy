package com.tacz.legacy.common.application.gunpack

public data class ShellEjectTimingProfile(
    val fireTriggerMillis: Long?,
    val reloadTriggerMillis: Long?,
    val boltTriggerMillis: Long?
)

public object GunDisplayStateMachineSemantics {

    public const val INTRO_SHELL_EJECTING_TIME_KEY: String = "intro_shell_ejecting_time"
    public const val BOLT_SHELL_EJECTING_TIME_KEY: String = "bolt_shell_ejecting_time"
    public const val SCRIPT_SHOOT_FEED_TIME_KEY: String = "shoot_feed_time"
    public const val SCRIPT_FEED_TIME_KEY: String = "feed_time"
    public const val SCRIPT_BOLT_TIME_KEY: String = "bolt_time"
    public const val SCRIPT_BOLT_FEED_TIME_KEY: String = "bolt_feed_time"

    public fun isManualBoltStateMachine(stateMachinePath: String?): Boolean {
        val normalized = stateMachinePath
            ?.trim()
            ?.lowercase()
            ?.ifBlank { null }
            ?: return false
        return MANUAL_BOLT_STATE_MACHINE_TOKENS.any { token -> normalized.contains(token) }
    }

    public fun hasBoltShellEjectingTime(stateMachineParams: Map<String, Float>): Boolean {
        return resolveBoltShellEjectingTimeMillis(stateMachineParams) != null
    }

    public fun shouldPreferBoltCycleAfterFire(
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float> = emptyMap()
    ): Boolean {
        if (displayDefinition == null) {
            return false
        }
        if (!hasBoltClip(displayDefinition.animationBoltClipName, displayDefinition.animationClipNames)) {
            return false
        }
        return isManualBoltStateMachine(displayDefinition.stateMachinePath) ||
            hasBoltShellEjectingTime(displayDefinition.stateMachineParams) ||
            resolveScriptBoltTriggerMillis(gunScriptParams) != null
    }

    public fun resolveShellEjectTimingProfile(
        displayDefinition: GunDisplayDefinition?,
        gunScriptParams: Map<String, Float> = emptyMap()
    ): ShellEjectTimingProfile {
        val stateMachineParams = displayDefinition?.stateMachineParams.orEmpty()
        val preferBoltCycle = shouldPreferBoltCycleAfterFire(displayDefinition, gunScriptParams)
        return ShellEjectTimingProfile(
            fireTriggerMillis = if (preferBoltCycle) null else 0L,
            reloadTriggerMillis = resolveReloadShellTriggerMillis(stateMachineParams, gunScriptParams),
            boltTriggerMillis = if (preferBoltCycle) {
                resolveBoltShellEjectingTimeMillis(stateMachineParams)
                    ?: resolveScriptBoltTriggerMillis(gunScriptParams)
                    ?: 0L
            } else {
                null
            }
        )
    }

    public fun hasBoltClip(
        explicitBoltClipName: String?,
        animationClipNames: List<String>?
    ): Boolean {
        if (!explicitBoltClipName.isNullOrBlank()) {
            return true
        }
        return animationClipNames.orEmpty().any(::matchesBoltClipToken)
    }

    public fun matchesBoltClipToken(clipName: String): Boolean {
        val normalized = normalizeClipToken(clipName)
        if (normalized.isEmpty()) {
            return false
        }
        return BOLT_CLIP_KEYWORDS.any { keyword ->
            normalized == keyword ||
                normalized.endsWith("_$keyword") ||
                normalized.contains(keyword)
        }
    }

    public fun resolveIntroShellEjectingTimeMillis(stateMachineParams: Map<String, Float>): Long? {
        return resolveStateMachineParamMillis(stateMachineParams[INTRO_SHELL_EJECTING_TIME_KEY])
    }

    public fun resolveBoltShellEjectingTimeMillis(stateMachineParams: Map<String, Float>): Long? {
        return resolveStateMachineParamMillis(stateMachineParams[BOLT_SHELL_EJECTING_TIME_KEY])
    }

    public fun resolveReloadShellTriggerMillis(
        stateMachineParams: Map<String, Float>,
        gunScriptParams: Map<String, Float>
    ): Long? {
        return resolveIntroShellEjectingTimeMillis(stateMachineParams)
            ?: resolveStateMachineParamMillis(gunScriptParams[SCRIPT_SHOOT_FEED_TIME_KEY])
            ?: resolveStateMachineParamMillis(gunScriptParams[SCRIPT_FEED_TIME_KEY])
    }

    public fun resolveScriptBoltTriggerMillis(gunScriptParams: Map<String, Float>): Long? {
        return resolveStateMachineParamMillis(gunScriptParams[SCRIPT_BOLT_FEED_TIME_KEY])
            ?: resolveStateMachineParamMillis(gunScriptParams[SCRIPT_BOLT_TIME_KEY])
    }

    public fun resolveStateMachineParamMillis(valueSeconds: Float?): Long? {
        val normalized = valueSeconds?.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: return null
        return (normalized * 1000f).toLong().coerceAtLeast(0L)
    }

    private fun normalizeClipToken(raw: String): String =
        raw.trim()
            .lowercase()
            .map { ch -> if (ch.isLetterOrDigit()) ch else '_' }
            .joinToString(separator = "")
            .replace("__+".toRegex(), "_")
            .trim('_')

    private val MANUAL_BOLT_STATE_MACHINE_TOKENS: Set<String> = setOf(
        "manual_action_state_machine",
        "m870_state_machine",
        "spas_12_state_machine"
    )

    private val BOLT_CLIP_KEYWORDS: Set<String> = setOf("bolt", "blot", "pull_bolt", "charge")

}
