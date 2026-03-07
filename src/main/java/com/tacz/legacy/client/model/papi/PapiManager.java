package com.tacz.legacy.client.model.papi;

import com.google.common.collect.Maps;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;
import java.util.function.Function;

/**
 * Placeholder API manager for gun model text displays.
 * Port of upstream TACZ PapiManager.
 */
@SideOnly(Side.CLIENT)
public final class PapiManager {
    private static final Map<String, Function<ItemStack, String>> PAPI = Maps.newHashMap();

    static {
        addPapi(PlayerNamePapi.NAME, new PlayerNamePapi());
        addPapi(AmmoCountPapi.NAME, new AmmoCountPapi());
    }

    public static void addPapi(String textKey, Function<ItemStack, String> function) {
        textKey = "%" + textKey + "%";
        PAPI.put(textKey, function);
    }

    public static String getTextShow(String textKey, ItemStack stack) {
        String text = I18n.format(textKey);
        for (Map.Entry<String, Function<ItemStack, String>> entry : PAPI.entrySet()) {
            String placeholder = entry.getKey();
            String data = entry.getValue().apply(stack);
            text = text.replace(placeholder, data);
        }
        return text;
    }

    private PapiManager() {}
}
