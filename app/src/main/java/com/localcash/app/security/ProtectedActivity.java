package com.localcash.app.security;

import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;

import com.localcash.app.WelcomeActivity;

/** Base común de toda pantalla que puede mostrar datos privados de LocalCash. */
public abstract class ProtectedActivity extends AppCompatActivity {

    private boolean redirectingToWelcome;

    /**
     * Debe llamarse antes de inicializar repositorios o dibujar información privada en onCreate.
     * El borrado de la pila evita volver a una pantalla protegida con el botón Atrás.
     */
    protected final boolean requireAuthenticatedSession() {
        if (AuthSession.isAuthenticated()) {
            return true;
        }
        redirectToWelcome();
        return false;
    }

    @Override
    protected void onStart() {
        super.onStart();
        requireAuthenticatedSession();
    }

    /** Bloquea la sesión actual y vuelve a una pila nueva que comienza en WelcomeActivity. */
    protected final void lockAndReturnToWelcome() {
        AuthSession.clear();
        redirectToWelcome();
    }

    private void redirectToWelcome() {
        if (redirectingToWelcome || isFinishing()) {
            return;
        }
        redirectingToWelcome = true;
        Intent intent = new Intent(this, WelcomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
