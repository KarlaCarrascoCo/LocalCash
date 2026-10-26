package com.localcash.app;

import android.app.Application;
import android.content.ComponentCallbacks2;

import com.localcash.app.security.AuthSession;
import com.localcash.app.settings.ThemePreferences;

/** Invalida la sesión solamente cuando Android informa que la interfaz completa quedó oculta. */
public final class LocalCashApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePreferences.applySavedTheme(this);
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            AuthSession.invalidateForBackground();
        }
    }
}
