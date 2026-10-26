package com.localcash.app.settings;

import static org.junit.Assert.assertEquals;

import androidx.appcompat.app.AppCompatDelegate;

import org.junit.Test;

public class ThemePreferencesTest {
    @Test public void seleccionaModoClaroDeFormaExplicita() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_NO, ThemePreferences.nightModeFor(false));
    }

    @Test public void seleccionaModoOscuroDeFormaExplicita() {
        assertEquals(AppCompatDelegate.MODE_NIGHT_YES, ThemePreferences.nightModeFor(true));
    }
}
