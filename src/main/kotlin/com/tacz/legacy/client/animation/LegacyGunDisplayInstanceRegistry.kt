package com.tacz.legacy.client.animation

import com.tacz.legacy.api.client.animation.ObjectAnimation
import com.tacz.legacy.common.application.gunpack.GunDisplayDefinition
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * 按 sessionId + gunId 缓存 [LegacyGunDisplayInstance]。
 * 每个 session/gun 组合拥有独立的 AnimationController + StateMachine 实例。
 *
 * 对标 TACZ 上游的 TimelessAPI.getGunDisplay() 缓存 + TickAnimationEvent 的 init/exit 管理。
 */
@SideOnly(Side.CLIENT)
internal object LegacyGunDisplayInstanceRegistry {

    private val instancesByKey = linkedMapOf<String, InstanceEntry>()

    /**
     * 获取或创建 [LegacyGunDisplayInstance]。
     * 如果 gunId/scriptSource 变化，旧实例被替换。
     *
     * @param primaryAnimations 当前枪模的动画原型
     * @param defaultAnimations 默认动画原型（fallback）
     * @return 实例，或 null 如果创建失败
     */
    @Synchronized
    internal fun getOrCreate(
        sessionId: String,
        gunId: String,
        display: GunDisplayDefinition,
        primaryAnimations: List<ObjectAnimation>,
        defaultAnimations: List<ObjectAnimation>
    ): LegacyGunDisplayInstance? {
        val key = "$sessionId/$gunId"
        val scriptSource = display.stateMachineScriptContent?.trim()?.ifBlank { null } ?: return null
        val scriptId = display.stateMachinePath?.trim()?.ifBlank { null }
            ?: "${display.sourceId}#inline_state_machine"

        val existing = instancesByKey[key]
        if (existing != null && existing.gunId == gunId && existing.scriptFingerprint == scriptSource.hashCode()) {
            return existing.instance
        }

        // 新建实例
        val instance = LegacyGunDisplayInstance.create(
            primaryAnimations = primaryAnimations,
            defaultAnimations = defaultAnimations,
            scriptSource = scriptSource,
            scriptId = scriptId,
            stateMachineParams = display.stateMachineParams.ifEmpty { null }
        ) ?: return null

        instancesByKey[key] = InstanceEntry(
            gunId = gunId,
            scriptFingerprint = scriptSource.hashCode(),
            instance = instance
        )
        return instance
    }

    @Synchronized
    internal fun getExisting(sessionId: String, gunId: String): LegacyGunDisplayInstance? {
        return instancesByKey["$sessionId/$gunId"]?.instance
    }

    @Synchronized
    internal fun clearSession(sessionId: String) {
        val prefix = "$sessionId/"
        val keys = instancesByKey.keys.filter { it.startsWith(prefix) }
        keys.forEach { instancesByKey.remove(it) }
    }

    @Synchronized
    internal fun clear() {
        instancesByKey.clear()
    }

    private data class InstanceEntry(
        val gunId: String,
        val scriptFingerprint: Int,
        val instance: LegacyGunDisplayInstance
    )
}
