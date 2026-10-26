package com.localcash.app.security;

/** Sesión de autenticación solo en memoria; nunca persiste acceso en disco. */
public final class AuthSession {
    private static volatile boolean authenticated;
    private AuthSession() { }
    public static boolean isAuthenticated() { return authenticated; }
    public static void authenticate() { authenticated = true; }
    public static void clear() { authenticated = false; }
    /** Se invoca cuando Android confirma que ninguna ventana de LocalCash permanece visible. */
    public static void invalidateForBackground() { clear(); }
}
