package com.tacz.legacy.client.foundation;

import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TACZAsciiFontHelperTest {
    @Test
    public void shouldDisableUnicodeForAsciiTextAndFormattingCodes() {
        assertTrue(TACZAsciiFontHelper.shouldDisableUnicode("ABC 123"));
        assertTrue(TACZAsciiFontHelper.shouldDisableUnicode("§aABC §r123"));
        assertTrue(TACZAsciiFontHelper.shouldDisableUnicode("x64"));
    }

    @Test
    public void shouldKeepUnicodeForNonAsciiText() {
        assertFalse(TACZAsciiFontHelper.shouldDisableUnicode("中文"));
        assertFalse(TACZAsciiFontHelper.shouldDisableUnicode("☠ x 01"));
        assertFalse(TACZAsciiFontHelper.shouldDisableUnicode("∞"));
    }

    @Test
    public void iterableRequiresAllVisibleLinesToBeAscii() {
        assertTrue(TACZAsciiFontHelper.shouldDisableUnicode(Arrays.asList("ABC", "123", "§eREADY")));
        assertFalse(TACZAsciiFontHelper.shouldDisableUnicode(Arrays.asList("ABC", "中文")));
    }

    @Test
    public void temporaryFlagWrapperRestoresPreviousValue() {
        AtomicBoolean unicodeFlag = new AtomicBoolean(true);

        TACZAsciiFontHelper.runWithTemporaryUnicodeFlagDisabled(true, unicodeFlag::get, unicodeFlag::set, () -> {
            assertFalse(unicodeFlag.get());
        });

        assertTrue(unicodeFlag.get());
    }

    @Test
    public void temporaryFlagWrapperRestoresPreviousValueAfterException() {
        AtomicBoolean unicodeFlag = new AtomicBoolean(true);

        try {
            TACZAsciiFontHelper.runWithTemporaryUnicodeFlagDisabled(true, unicodeFlag::get, unicodeFlag::set, () -> {
                throw new IllegalStateException("boom");
            });
            fail("expected exception");
        } catch (IllegalStateException expected) {
            // expected
        }

        assertTrue(unicodeFlag.get());
    }
}