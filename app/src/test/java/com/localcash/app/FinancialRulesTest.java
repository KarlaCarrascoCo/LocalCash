package com.localcash.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.localcash.app.util.FinancialRules;

import org.junit.Test;

public class FinancialRulesTest {

    @Test public void divideElGastoEntreTodosLosParticipantes() {
        assertEquals(2_500L, FinancialRules.deudaPorContacto(10_000L, 4));
    }

    @Test public void rechazaDivisionInvalida() {
        assertThrows(IllegalArgumentException.class, () -> FinancialRules.deudaPorContacto(100L, 1));
        assertThrows(IllegalArgumentException.class, () -> FinancialRules.deudaPorContacto(0L, 2));
    }

    @Test public void validaMovimientosYCompras() {
        assertTrue(FinancialRules.esMovimientoValido("Sueldo", 1000L));
        assertFalse(FinancialRules.esMovimientoValido(" ", 1000L));
        assertFalse(FinancialRules.esMovimientoValido("Pago", 0L));
        assertTrue(FinancialRules.esCompraValida("Pan", 2, 1500L));
        assertFalse(FinancialRules.esCompraValida("Pan", 0, 1500L));
        assertFalse(FinancialRules.esCompraValida("Pan", 1, -1L));
    }
}
