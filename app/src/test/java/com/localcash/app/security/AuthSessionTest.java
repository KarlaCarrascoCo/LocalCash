package com.localcash.app.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Prueba aislada de los cambios de estado de la sesión, sin Android ni SQLite. */
public class AuthSessionTest {

    @Before public void setUp() { AuthSession.clear(); }
    @After public void tearDown() { AuthSession.clear(); }

    @Test public void autenticarHabilitaLaSesion() {
        assertFalse(AuthSession.isAuthenticated());
        AuthSession.authenticate();
        assertTrue(AuthSession.isAuthenticated());
    }

    @Test public void segundoPlanoInvalidaUnaSesionAutenticada() {
        AuthSession.authenticate();
        AuthSession.invalidateForBackground();
        assertFalse(AuthSession.isAuthenticated());
    }
}
