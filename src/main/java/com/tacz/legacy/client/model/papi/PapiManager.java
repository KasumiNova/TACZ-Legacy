package com.tacz.legacy.client.model.papi;

import com.google.common.collect.Maps;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Function;

/**
 * Placeholder API manager for gun model text displays.
 * Port of upstream TACZ PapiManager.
 */
@SideOnly(Side.CLIENT)
public final class PapiManager {
    private static final Map<String, Function<ItemStack, String>> PAPI = Maps.newHashMap();
    @Nullable
    private static final Field I18N_LOCALE_FIELD = findI18nLocaleField();
    @Nullable
    private static final Field LOCALE_PROPERTIES_FIELD = findLocalePropertiesField();

    static {
        addPapi(PlayerNamePapi.NAME, new PlayerNamePapi());
        addPapi(AmmoCountPapi.NAME, new AmmoCountPapi());
    }

    public static void addPapi(String textKey, Function<ItemStack, String> function) {
        textKey = "%" + textKey + "%";
        PAPI.put(textKey, function);
    }

    public static String getTextShow(String textKey, ItemStack stack) {
        String text = resolveTextTemplate(textKey);
        for (Map.Entry<String, Function<ItemStack, String>> entry : PAPI.entrySet()) {
            String placeholder = entry.getKey();
            if (!text.contains(placeholder)) {
                continue;
            }
            String data = entry.getValue().apply(stack);
            text = text.replace(placeholder, data);
        }
        return text;
    }

    static String resolveTextTemplate(String textKey) {
        String translated = getTranslationOrNull(textKey);
        return translated != null ? translated : textKey;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static String getTranslationOrNull(String textKey) {
        if (I18N_LOCALE_FIELD == null || LOCALE_PROPERTIES_FIELD == null) {
            return null;
        }
        try {
            Object locale = I18N_LOCALE_FIELD.get(null);
            if (locale == null) {
                return null;
            }
            Object properties = LOCALE_PROPERTIES_FIELD.get(locale);
            if (!(properties instanceof Map)) {
                return null;
            }
            return ((Map<String, String>) properties).get(textKey);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    @Nullable
    private static Field findI18nLocaleField() {
        for (Field field : I18n.class.getDeclaredFields()) {
            if (field.getType().getName().equals("net.minecraft.client.resources.Locale")) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    @Nullable
    private static Field findLocalePropertiesField() {
        for (Field field : net.minecraft.client.resources.Locale.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    private PapiManager() {}
}
