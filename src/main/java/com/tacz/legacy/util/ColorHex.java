package com.tacz.legacy.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hex color parsing utility. Port of upstream TACZ ColorHex.
 */
public final class ColorHex {
    private static final Pattern COLOR_HEX = Pattern.compile("^#([0-9A-Fa-f]{6})$");

    public static int colorTextToRgbInt(String colorText) {
        Matcher matcher = COLOR_HEX.matcher(colorText);
        if (!matcher.find()) {
            return 0xFFFFFF;
        }
        return Integer.parseInt(matcher.group(1), 16);
    }

    private ColorHex() {}
}
