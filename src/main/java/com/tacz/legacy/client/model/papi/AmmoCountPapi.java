package com.tacz.legacy.client.model.papi;

import com.tacz.legacy.api.item.IGun;
import com.tacz.legacy.common.resource.BoltType;
import com.tacz.legacy.common.resource.GunCombatData;
import com.tacz.legacy.common.resource.GunDataAccessor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.function.Function;

/**
 * Resolves ammo count text for gun model text display.
 * Port of upstream TACZ AmmoCountPapi.
 */
public class AmmoCountPapi implements Function<ItemStack, String> {
    public static final String NAME = "ammo_count";

    @Override
    public String apply(ItemStack stack) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun != null) {
            ResourceLocation gunId = iGun.getGunId(stack);
            GunCombatData gunData = GunDataAccessor.getGunData(gunId);
            if (gunData == null) {
                return "";
            }
            int ammoCount = iGun.getCurrentAmmoCount(stack)
                    + (iGun.hasBulletInBarrel(stack) && gunData.getBoltType() != BoltType.OPEN_BOLT ? 1 : 0);
            return String.valueOf(ammoCount);
        }
        return "";
    }
}
