package com.localcash.app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.localcash.app.security.AuthSession;
import com.localcash.app.ui.BiometricAuthenticator;

/** Pantalla de entrada: no abre SQLite ni expone información financiera. */
public final class WelcomeActivity extends AppCompatActivity {
    private MaterialButton fingerprintButton;
    private TextView status;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        AuthSession.clear();
        setContentView(R.layout.activity_welcome);
        fingerprintButton = findViewById(R.id.button_fingerprint_login);
        status = findViewById(R.id.text_welcome_status);
        fingerprintButton.setOnClickListener(view -> autenticar());
    }

    private void autenticar() {
        fingerprintButton.setEnabled(false);
        status.setText(R.string.biometric_waiting);
        BiometricAuthenticator.authenticate(this, new BiometricAuthenticator.Callback() {
            @Override public void onAuthenticated() {
                AuthSession.authenticate();
                startActivity(new Intent(WelcomeActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
            }
            @Override public void onError(CharSequence message) {
                status.setText(message);
                fingerprintButton.setEnabled(true);
            }
        });
    }
}
