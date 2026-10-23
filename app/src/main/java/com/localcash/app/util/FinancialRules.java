package com.localcash.app.util;

/** Reglas puras para validar montos y repartir gastos sin depender de Android o SQLite. */
public final class FinancialRules {

    private FinancialRules() { }

    public static long deudaPorContacto(long montoTotal, int participantes) {
        if (montoTotal <= 0 || participantes < 2) {
            throw new IllegalArgumentException("Datos de división inválidos.");
        }
        long deuda = montoTotal / participantes;
        if (deuda <= 0) {
            throw new IllegalArgumentException("Monto insuficiente para la división.");
        }
        return deuda;
    }

    public static boolean esMovimientoValido(String descripcion, long monto) {
        return descripcion != null && !descripcion.trim().isEmpty() && monto > 0;
    }

    public static boolean esCompraValida(String nombre, int cantidad, long precioEstimado) {
        return nombre != null && !nombre.trim().isEmpty() && cantidad > 0 && precioEstimado >= 0;
    }
}
