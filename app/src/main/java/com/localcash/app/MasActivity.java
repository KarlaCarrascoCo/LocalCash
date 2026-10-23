package com.localcash.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.localcash.app.security.AuthSession;

/** Accesos a módulos offline complementarios. */
public final class MasActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_mas);
        findViewById(R.id.button_more_accounts).setOnClickListener(v -> startActivity(
                new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_OPEN_ACCOUNT_FORM, true)));
        findViewById(R.id.button_more_contacts).setOnClickListener(v -> startActivity(new Intent(this, ContactosActivity.class)));
        findViewById(R.id.button_more_shared).setOnClickListener(v -> startActivity(new Intent(this, GastosCompartidosActivity.class)));
        findViewById(R.id.button_more_map).setOnClickListener(v -> startActivity(new Intent(this, MapActivity.class)));
        findViewById(R.id.button_more_security).setOnClickListener(v -> mostrarSeguridad());
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
