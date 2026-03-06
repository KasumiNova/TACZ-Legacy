package com.tacz.legacy.common.entity.shooter

import com.tacz.legacy.api.entity.ReloadState
import net.minecraft.item.ItemStack
import java.util.function.Supplier

/**
 * 每个 LivingEntity 持有的枪械操作状态数据。
 * 与上游 TACZ ShooterDataHolder 字段一致。
 */
public class ShooterDataHolder {
    @JvmField public var baseTimestamp: Long = System.currentTimeMillis()
    @JvmField public var shootTimestamp: Long = -1L
    @JvmField public var lastShootTimestamp: Long = -1L
    @JvmField public var meleeTimestamp: Long = -1L
    @JvmField public var meleePrepTickCount: Int = -1
    @JvmField public var drawTimestamp: Long = -1L
    @JvmField public var boltTimestamp: Long = -1L
    @JvmField public var isBolting: Boolean = false
    @JvmField public var aimingProgress: Float = 0f
    @JvmField public var aimingTimestamp: Long = -1L
    @JvmField public var isAiming: Boolean = false
    @JvmField public var reloadTimestamp: Long = -1L
    @JvmField public var reloadStateType: ReloadState.StateType = ReloadState.StateType.NOT_RELOADING
    @JvmField public var currentGunItem: Supplier<ItemStack>? = null
    @JvmField public var currentPutAwayTimeS: Float = 0f
    @JvmField public var sprintTimeS: Float = 0f
    @JvmField public var sprintTimestamp: Long = -1L
    @JvmField public var knockbackStrength: Double = -1.0
    @JvmField public var shootCount: Int = 0
    @JvmField public var isCrawling: Boolean = false

    public fun initialData() {
        shootTimestamp = -1L
        lastShootTimestamp = -1L
        meleeTimestamp = -1L
        meleePrepTickCount = -1
        drawTimestamp = -1L
        boltTimestamp = -1L
        isBolting = false
        aimingProgress = 0f
        aimingTimestamp = -1L
        isAiming = false
        reloadTimestamp = -1L
        reloadStateType = ReloadState.StateType.NOT_RELOADING
        sprintTimeS = 0f
        sprintTimestamp = -1L
        shootCount = 0
    }
}
