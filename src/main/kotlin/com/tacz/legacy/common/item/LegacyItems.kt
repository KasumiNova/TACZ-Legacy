package com.tacz.legacy.common.item

import com.tacz.legacy.TACZLegacy
import com.tacz.legacy.api.DefaultAssets
import com.tacz.legacy.api.item.IGun
import com.tacz.legacy.api.item.gun.FireMode
import com.tacz.legacy.api.item.gun.GunItemManager
import com.tacz.legacy.common.block.LegacyBlocks
import com.tacz.legacy.common.resource.TACZGunPackRuntimeRegistry
import com.tacz.legacy.common.registry.LegacyCreativeTabs
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.ResourceLocation
import net.minecraftforge.registries.IForgeRegistry

internal object LegacyItems {
    internal val MODERN_KINETIC_GUN: ModernKineticGunItem = ModernKineticGunItem().named("modern_kinetic_gun", LegacyCreativeTabs.GUNS)
    internal val AMMO: LegacySimpleItem = LegacySimpleItem().named("ammo", LegacyCreativeTabs.AMMO)
    internal val ATTACHMENT: LegacySimpleItem = LegacySimpleItem().named("attachment", LegacyCreativeTabs.PARTS)
    internal val AMMO_BOX: LegacySimpleItem = LegacySimpleItem(maxStackSize = 1).named("ammo_box", LegacyCreativeTabs.AMMO)
    internal val TARGET_MINECART: LegacySimpleItem = LegacySimpleItem(maxStackSize = 1).named("target_minecart", LegacyCreativeTabs.DECORATION)

    internal val GUN_SMITH_TABLE: ItemBlock = createBlockItem(LegacyBlocks.GUN_SMITH_TABLE)
    internal val WORKBENCH_A: ItemBlock = createBlockItem(LegacyBlocks.WORKBENCH_A)
    internal val WORKBENCH_B: ItemBlock = createBlockItem(LegacyBlocks.WORKBENCH_B)
    internal val WORKBENCH_C: ItemBlock = createBlockItem(LegacyBlocks.WORKBENCH_C)
    internal val TARGET: ItemBlock = createBlockItem(LegacyBlocks.TARGET)
    internal val STATUE: ItemBlock = createBlockItem(LegacyBlocks.STATUE)

    internal val allItems: List<Item> = listOf(
        MODERN_KINETIC_GUN,
        AMMO,
        ATTACHMENT,
        AMMO_BOX,
        TARGET_MINECART,
        GUN_SMITH_TABLE,
        WORKBENCH_A,
        WORKBENCH_B,
        WORKBENCH_C,
        TARGET,
        STATUE,
    )

    internal fun registerAll(registry: IForgeRegistry<Item>): Unit {
        allItems.forEach(registry::register)
        GunItemManager.clear()
        val itemTypes = TACZGunPackRuntimeRegistry.getSnapshot().gunItemTypes().ifEmpty { setOf(ModernKineticGunItem.TYPE_NAME) }
        itemTypes.forEach { typeName ->
            GunItemManager.registerGunItem(typeName, MODERN_KINETIC_GUN)
        }
        TACZLegacy.logger.info("[GunPackRuntime] Registered {} gun item type mapping(s): {}", itemTypes.size, itemTypes.joinToString())
    }

    private fun createBlockItem(block: net.minecraft.block.Block): ItemBlock = ItemBlock(block).apply {
        registryName = requireNotNull(block.registryName)
        setTranslationKey("${TACZLegacy.MOD_ID}.${requireNotNull(block.registryName).path}")
        setCreativeTab(LegacyCreativeTabs.DECORATION)
    }

    private fun <T : Item> T.named(path: String, tab: CreativeTabs): T {
        registryName = ResourceLocation(TACZLegacy.MOD_ID, path)
        setTranslationKey("${TACZLegacy.MOD_ID}.$path")
        setCreativeTab(tab)
        return this
    }
}

internal open class LegacySimpleItem(maxStackSize: Int = 64) : Item() {
    init {
        this.maxStackSize = maxStackSize
    }
}

internal class ModernKineticGunItem : Item(), IGun {
    init {
        maxStackSize = 1
    }

    override fun getGunId(stack: ItemStack): ResourceLocation {
        val explicitGunId = stack.tagCompound
            ?.getString(IGun.GUN_ID_TAG)
            ?.takeIf { it.isNotBlank() }
            ?.let(::ResourceLocation)
        return explicitGunId
            ?: TACZGunPackRuntimeRegistry.getSnapshot().resolveDefaultGunId(TYPE_NAME)
            ?: DefaultAssets.DEFAULT_GUN_ID
    }

    override fun setGunId(stack: ItemStack, gunId: ResourceLocation?) {
        if (gunId != null) {
            ensureTag(stack).setString(IGun.GUN_ID_TAG, gunId.toString())
        }
    }

    override fun getFireMode(stack: ItemStack): FireMode {
        val tag = stack.tagCompound ?: return FireMode.UNKNOWN
        val str = tag.getString(IGun.FIRE_MODE_TAG)
        return if (str.isBlank()) FireMode.UNKNOWN
        else try { FireMode.valueOf(str) } catch (_: IllegalArgumentException) { FireMode.UNKNOWN }
    }

    override fun setFireMode(stack: ItemStack, fireMode: FireMode?) {
        ensureTag(stack).setString(IGun.FIRE_MODE_TAG, (fireMode ?: FireMode.UNKNOWN).name)
    }

    override fun getCurrentAmmoCount(stack: ItemStack): Int {
        return stack.tagCompound?.getInteger(IGun.AMMO_COUNT_TAG) ?: 0
    }

    override fun setCurrentAmmoCount(stack: ItemStack, ammoCount: Int) {
        ensureTag(stack).setInteger(IGun.AMMO_COUNT_TAG, ammoCount.coerceAtLeast(0))
    }

    override fun reduceCurrentAmmoCount(stack: ItemStack) {
        if (!useInventoryAmmo(stack)) {
            setCurrentAmmoCount(stack, getCurrentAmmoCount(stack) - 1)
        }
    }

    override fun hasBulletInBarrel(stack: ItemStack): Boolean {
        return stack.tagCompound?.getBoolean(IGun.BULLET_IN_BARREL_TAG) ?: false
    }

    override fun setBulletInBarrel(stack: ItemStack, bulletInBarrel: Boolean) {
        ensureTag(stack).setBoolean(IGun.BULLET_IN_BARREL_TAG, bulletInBarrel)
    }

    override fun useInventoryAmmo(stack: ItemStack): Boolean {
        // 由 gun data 中的 reload.type 决定是否为背包直读
        val gunId = getGunId(stack)
        val gun = TACZGunPackRuntimeRegistry.getSnapshot().guns[gunId] ?: return false
        val reloadType = gun.data.raw.getAsJsonObject("reload")?.get("type")?.asString
        return reloadType == "inventory"
    }

    override fun hasInventoryAmmo(shooter: EntityLivingBase, stack: ItemStack, needCheckAmmo: Boolean): Boolean {
        if (!useInventoryAmmo(stack)) return false
        if (!needCheckAmmo) return true
        if (useDummyAmmo(stack)) return getDummyAmmoAmount(stack) > 0
        // 检查背包
        // TODO: full inventory ammo search (ammo items + ammo boxes)
        return false
    }

    override fun useDummyAmmo(stack: ItemStack): Boolean {
        return stack.tagCompound?.hasKey(IGun.DUMMY_AMMO_TAG) ?: false
    }

    override fun getDummyAmmoAmount(stack: ItemStack): Int {
        return (stack.tagCompound?.getInteger(IGun.DUMMY_AMMO_TAG) ?: 0).coerceAtLeast(0)
    }

    override fun setDummyAmmoAmount(stack: ItemStack, amount: Int) {
        ensureTag(stack).setInteger(IGun.DUMMY_AMMO_TAG, amount.coerceAtLeast(0))
    }

    override fun addDummyAmmoAmount(stack: ItemStack, amount: Int) {
        if (!useDummyAmmo(stack)) return
        val current = getDummyAmmoAmount(stack)
        setDummyAmmoAmount(stack, (current + amount).coerceAtLeast(0))
    }

    override fun hasAttachmentLock(stack: ItemStack): Boolean {
        return stack.tagCompound?.getBoolean(IGun.ATTACHMENT_LOCK_TAG) ?: false
    }

    override fun setAttachmentLock(stack: ItemStack, locked: Boolean) {
        ensureTag(stack).setBoolean(IGun.ATTACHMENT_LOCK_TAG, locked)
    }

    override fun isOverheatLocked(stack: ItemStack): Boolean {
        return stack.tagCompound?.getBoolean(IGun.OVERHEAT_LOCK_TAG) ?: false
    }

    override fun setOverheatLocked(stack: ItemStack, locked: Boolean) {
        ensureTag(stack).setBoolean(IGun.OVERHEAT_LOCK_TAG, locked)
    }

    override fun onEntitySwing(entityLiving: EntityLivingBase, stack: ItemStack): Boolean = true

    internal companion object {
        internal const val TYPE_NAME: String = "modern_kinetic"
    }
}

private fun ensureTag(stack: ItemStack): NBTTagCompound {
    val existing = stack.tagCompound
    if (existing != null) {
        return existing
    }
    val created = NBTTagCompound()
    stack.tagCompound = created
    return created
}
