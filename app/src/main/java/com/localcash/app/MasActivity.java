package com.localcash.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.localcash.app.security.AuthSession;

/** Accesos a módulos offline complementarios. */
public final class MasActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!AuthSession.isAuthenticated()) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_mas);
        findViewById(R.id.button_more_back).setOnClickListener(v -> finish());
        findViewById(R.id.button_more_accounts).setOnClickListener(v -> abrir(CuentasActivity.class));
        findViewById(R.id.button_more_shopping).setOnClickListener(v -> abrir(ComprasActivity.class));
        findViewById(R.id.button_more_contacts).setOnClickListener(v -> abrir(ContactosActivity.class));
        findViewById(R.id.button_more_shared).setOnClickListener(v -> abrir(GastosCompartidosActivity.class));
        findViewById(R.id.button_more_map).setOnClickListener(v -> abrir(MapActivity.class));
        findViewById(R.id.button_more_history).setOnClickListener(v -> abrir(HistorialActivity.class));
        findViewById(R.id.button_more_security).setOnClickListener(v -> mostrarSeguridad());
        configurarNavegacion();
    }

    private void configurarNavegacion() {
        findViewById(R.id.button_more_nav_home).setOnClickListener(v -> abrir(MainActivity.class));
        findViewById(R.id.button_more_nav_history).setOnClickListener(v -> abrir(HistorialActivity.class));
        findViewById(R.id.button_more_nav_shopping).setOnClickListener(v -> abrir(ComprasActivity.class));
        findViewById(R.id.button_more_nav_more).setOnClickListener(v -> { });
    }

    private void abrir(Class<?> destination) {
        startActivity(new Intent(this, destination)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void mostrarSeguridad() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.security_title)
                .setMessage(R.string.security_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_lock_now, (dialog, which) -> {
                    AuthSession.clear();
                    startActivity(new Intent(this, WelcomeActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                })
                .show();
    }
}
