package com.localcash.app.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reglas puras para repartir y validar gastos compartidos en pesos enteros. */
public final class SharedExpenseRules {
    private SharedExpenseRules() { }

    public static final class DivisionEquitativa {
        private final long montoUsuario;
        private final long montoPorContacto;

        private DivisionEquitativa(long montoUsuario, long montoPorContacto) {
            this.montoUsuario = montoUsuario;
            this.montoPorContacto = montoPorContacto;
        }

        public long getMontoUsuario() { return montoUsuario; }
        public long getMontoPorContacto() { return montoPorContacto; }
    }

    /** El residuo de la división entera se asigna siempre al usuario creador del gasto. */
    public static DivisionEquitativa dividirEquitativamente(long montoTotal, int contactos) {
        int participantes = contactos + 1;
        if (montoTotal <= 0 || contactos < 1 || montoTotal < participantes) {
            throw new IllegalArgumentException("El total debe alcanzar para todos los participantes.");
        }
        long porContacto = montoTotal / participantes;
        long montoUsuario = porContacto + (montoTotal % participantes);
        return new DivisionEquitativa(montoUsuario, porContacto);
    }

    /** Valida una lista opcional de líneas con el formato "Descripción | monto". */
    public static List<String> validarDetalleOpcional(String detalle, long montoTotal) {
        List<String> resultado = new ArrayList<>();
        if (detalle == null || detalle.trim().isEmpty()) return resultado;
        long suma = 0L;
        String[] lineas = detalle.split("\\n");
        for (int indice = 0; indice < lineas.length; indice++) {
            String linea = lineas[indice].trim();
            if (linea.isEmpty()) continue;
            String[] partes = linea.split("\\|", -1);
            if (partes.length != 2 || partes[0].trim().isEmpty()) {
                throw new IllegalArgumentException("Línea " + (indice + 1)
                        + ": usa el formato Descripción | monto.");
            }
            long monto;
            try {
                monto = Long.parseLong(partes[1].trim());
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Línea " + (indice + 1)
                        + ": el monto debe ser un número entero positivo.");
            }
            if (monto <= 0) {
                throw new IllegalArgumentException("Línea " + (indice + 1)
                        + ": el monto debe ser mayor que cero.");
            }
            try {
                suma = Math.addExact(suma, monto);
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("La suma del detalle es demasiado alta.");
            }
            resultado.add(partes[0].trim() + " | " + monto);
        }
        if (resultado.isEmpty()) return resultado;
        if (suma != montoTotal) {
            throw new IllegalArgumentException("La suma de productos o servicios (" + suma
                    + ") no coincide con el total (" + montoTotal + ").");
        }
        return resultado;
    }

    public static void validarParticipantes(List<Long> contactoIds) {
        if (contactoIds == null || contactoIds.isEmpty()) {
            throw new IllegalArgumentException("Selecciona al menos un contacto.");
        }
        Set<Long> unicos = new HashSet<>();
        for (Long id : contactoIds) {
            if (id == null || id <= 0 || !unicos.add(id)) {
                throw new IllegalArgumentException("Los participantes deben ser contactos únicos.");
            }
        }
    }

    /** Exige que los montos de contactos y usuario sean positivos y sumen exactamente el total. */
    public static void validarAsignacionesPersonalizadas(long montoTotal, List<Long> contactoIds,
                                                          Map<Long, Long> montosContactos,
                                                          long montoUsuario) {
        validarParticipantes(contactoIds);
        if (montoTotal <= 0 || montosContactos == null || montosContactos.size() != contactoIds.size()
                || montoUsuario <= 0) {
            throw new IllegalArgumentException("Completa montos positivos para todos los participantes.");
        }
        long suma = montoUsuario;
        for (Long contactoId : contactoIds) {
            Long monto = montosContactos.get(contactoId);
            if (monto == null || monto <= 0) {
                throw new IllegalArgumentException("Cada contacto debe tener un monto positivo.");
            }
            try {
                suma = Math.addExact(suma, monto);
            } catch (ArithmeticException error) {
                throw new IllegalArgumentException("La suma de asignaciones es demasiado alta.");
            }
        }
        if (suma != montoTotal) {
            throw new IllegalArgumentException("Las asignaciones deben sumar exactamente " + montoTotal + ".");
        }
    }
}
