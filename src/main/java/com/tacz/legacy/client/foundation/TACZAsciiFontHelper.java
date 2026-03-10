package com.tacz.legacy.client.foundation;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class TACZAsciiFontHelper {
    private TACZAsciiFontHelper() {
    }

    public static boolean shouldDisableUnicode(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == '\u00a7') {
                i++;
                continue;
            }
            if (current > 0x7F) {
                return false;
            }
        }
        return true;
    }

    public static boolean shouldDisableUnicode(Iterable<String> texts) {
        if (texts == null) {
            return false;
        }
        boolean sawText = false;
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            sawText = true;
            if (!shouldDisableUnicode(text)) {
                return false;
            }
        }
        return sawText;
    }

    public static void runWithTemporaryUnicodeFlagDisabled(FontRenderer font, String text, Runnable action) {
        runWithTemporaryUnicodeFlagDisabled(shouldDisableUnicode(text), font::getUnicodeFlag, font::setUnicodeFlag, action);
    }

    public static void runWithTemporaryUnicodeFlagDisabled(FontRenderer font, Iterable<String> texts, Runnable action) {
        runWithTemporaryUnicodeFlagDisabled(shouldDisableUnicode(texts), font::getUnicodeFlag, font::setUnicodeFlag, action);
    }

    static void runWithTemporaryUnicodeFlagDisabled(
        boolean shouldDisable,
        Supplier<Boolean> flagGetter,
        Consumer<Boolean> flagSetter,
        Runnable action
    ) {
        if (!shouldDisable) {
            action.run();
            return;
        }
        boolean previousFlag = flagGetter.get();
        flagSetter.accept(false);
        try {
            action.run();
        } finally {
            flagSetter.accept(previousFlag);
        }
    }

    public static <T> T supplyWithTemporaryUnicodeFlagDisabled(FontRenderer font, String text, Supplier<T> action) {
        return supplyWithTemporaryUnicodeFlagDisabled(shouldDisableUnicode(text), font::getUnicodeFlag, font::setUnicodeFlag, action);
    }

    public static <T> T supplyWithTemporaryUnicodeFlagDisabled(FontRenderer font, Iterable<String> texts, Supplier<T> action) {
        return supplyWithTemporaryUnicodeFlagDisabled(shouldDisableUnicode(texts), font::getUnicodeFlag, font::setUnicodeFlag, action);
    }

    static <T> T supplyWithTemporaryUnicodeFlagDisabled(
        boolean shouldDisable,
        Supplier<Boolean> flagGetter,
        Consumer<Boolean> flagSetter,
        Supplier<T> action
    ) {
        if (!shouldDisable) {
            return action.get();
        }
        boolean previousFlag = flagGetter.get();
        flagSetter.accept(false);
        try {
            return action.get();
        } finally {
            flagSetter.accept(previousFlag);
        }
    }

    public static int getStringWidth(FontRenderer font, String text) {
        if (text == null) {
            return 0;
        }
        return supplyWithTemporaryUnicodeFlagDisabled(font, text, () -> font.getStringWidth(text));
    }

    public static String trimStringToWidth(FontRenderer font, String text, int width) {
        if (text == null) {
            return "";
        }
        return supplyWithTemporaryUnicodeFlagDisabled(font, text, () -> font.trimStringToWidth(text, width));
    }

    public static String trimStringToWidth(FontRenderer font, String text, int width, boolean reverse) {
        if (text == null) {
            return "";
        }
        return supplyWithTemporaryUnicodeFlagDisabled(font, text, () -> font.trimStringToWidth(text, width, reverse));
    }

    public static List<String> listFormattedStringToWidth(FontRenderer font, String text, int width) {
        return supplyWithTemporaryUnicodeFlagDisabled(font, text, () -> font.listFormattedStringToWidth(text, width));
    }

    public static int drawString(FontRenderer font, String text, int x, int y, int color) {
        if (text == null) {
            return 0;
        }
        return supplyWithTemporaryUnicodeFlagDisabled(font, text, () -> font.drawString(text, x, y, color));
    }

    public static int drawString(FontRenderer font, String text, float x, float y, int color, boolean shadow) {
        if (text == null) {
            return 0;
        }
        return supplyWithTemporaryUnicodeFlagDisabled(font, text, () -> font.drawString(text, x, y, color, shadow));
    }

    public static int drawStringWithShadow(FontRenderer font, String text, float x, float y, int color) {
        if (text == null) {
            return 0;
        }
        return supplyWithTemporaryUnicodeFlagDisabled(font, text, () -> font.drawStringWithShadow(text, x, y, color));
    }

    public static int drawCenteredStringWithShadow(FontRenderer font, String text, int centerX, int y, int color) {
        int width = getStringWidth(font, text);
        return drawStringWithShadow(font, text, centerX - width / 2.0f, y, color);
    }

    public static void renderItemOverlayIntoGUI(RenderItem itemRender, FontRenderer font, ItemStack stack, int x, int y, String altText) {
        runWithTemporaryUnicodeFlagDisabled(
            altText == null || shouldDisableUnicode(altText),
            font::getUnicodeFlag,
            font::setUnicodeFlag,
            () -> itemRender.renderItemOverlayIntoGUI(font, stack, x, y, altText)
        );
    }
}