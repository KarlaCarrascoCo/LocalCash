package com.localcash.app.settings;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Preferencia local de apariencia, separada de los datos financieros SQLite. */
public final class ThemePreferences {
    private static final String PREFERENCES_NAME = "localcash_appearance";
    private static final String KEY_DARK_MODE = "dark_mode";

    private ThemePreferences() { }

    public static void applySavedTheme(Context context) {
        AppCompatDelegate.setDefaultNightMode(nightModeFor(isDarkMode(context)));
    }

    public static boolean isDarkMode(Context context) {
        return preferences(context).getBoolean(KEY_DARK_MODE, false);
    }

    public static void setDarkMode(Context context, boolean darkMode) {
        preferences(context).edit().putBoolean(KEY_DARK_MODE, darkMode).commit();
        AppCompatDelegate.setDefaultNightMode(nightModeFor(darkMode));
    }

    static int nightModeFor(boolean darkMode) {
        return darkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO;
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
