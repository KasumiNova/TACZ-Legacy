package com.tacz.legacy.client.model.papi;

import net.minecraft.client.resources.I18n;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.ItemStack;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class PapiManagerTest {
    private Field i18nLocaleField;
    private Field localePropertiesField;
    private Object originalLocale;

    @BeforeClass
    public static void bootstrapMinecraftRegistries() {
        Bootstrap.register();
    }

    @Before
    public void setUp() throws Exception {
        i18nLocaleField = findI18nLocaleField();
        localePropertiesField = findLocalePropertiesField();
        originalLocale = i18nLocaleField.get(null);
    }

    @After
    public void tearDown() throws Exception {
        i18nLocaleField.set(null, originalLocale);
    }

    @Test
    public void directPlaceholderTextDoesNotProduceFormatError() throws Exception {
        installLocale(new HashMap<>());

        assertEquals("", PapiManager.getTextShow("%ammo_count%", ItemStack.EMPTY));
    }

    @Test
    public void localizedTemplateRetainsPlaceholderForReplacement() throws Exception {
        Map<String, String> translations = new HashMap<>();
        translations.put("test.tacz.display", "AMMO %ammo_count%");
        installLocale(translations);

        assertEquals("AMMO ", PapiManager.getTextShow("test.tacz.display", ItemStack.EMPTY));
    }

    @Test
    public void untranslatedLiteralTextFallsBackToRawText() throws Exception {
        installLocale(new HashMap<>());

        assertEquals("ENERGY", PapiManager.getTextShow("ENERGY", ItemStack.EMPTY));
    }

    private void installLocale(Map<String, String> translations) throws Exception {
        net.minecraft.client.resources.Locale locale = new net.minecraft.client.resources.Locale();
        localePropertiesField.set(locale, new HashMap<>(translations));
        i18nLocaleField.set(null, locale);
    }

    private static Field findI18nLocaleField() {
        for (Field field : I18n.class.getDeclaredFields()) {
            if (field.getType().getName().equals("net.minecraft.client.resources.Locale")) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Unable to locate I18n locale field");
    }

    private static Field findLocalePropertiesField() {
        for (Field field : net.minecraft.client.resources.Locale.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Unable to locate Locale properties field");
    }
}