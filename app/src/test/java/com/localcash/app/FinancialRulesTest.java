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

    @Test public void validaProductosDeListaYCalculaTotalesEnPesos() {
        assertTrue(FinancialRules.esProductoCompraValido("Tomates", 1.5d, "KG", null, 1200L, 1400L));
        assertTrue(FinancialRules.esProductoCompraValido("Bolsa", 2d, "OTRA", "bolsas", 0L, null));
        assertFalse(FinancialRules.esProductoCompraValido("", 1d, "UNIDADES", null, 0L, null));
        assertFalse(FinancialRules.esProductoCompraValido("Pan", 0d, "UNIDADES", null, 0L, null));
        assertFalse(FinancialRules.esProductoCompraValido("Pan", 1d, "OTRA", "", 0L, null));
        assertEquals(1800L, FinancialRules.totalProducto(1.5d, 1200L));
    }

    @Test public void permitePrecioAproximadoOpcionalYPagoPendiente() {
        assertTrue(FinancialRules.esProductoCompraValido("Naranjas", 2d, "KG", null, 0L, null));
        assertTrue(FinancialRules.esProductoCompraValido("Naranjas", 2d, "KG", null, 700L, null));
        assertTrue(FinancialRules.esProductoCompraValido("Naranjas", 1.5d, "KG", null, 700L, 700L));
        assertFalse(FinancialRules.esProductoCompraValido("Naranjas", 2d, "KG", null, -1L, null));
    }

    @Test public void soloPermiteFinalizarListasConProductosAdquiridos() {
        assertFalse(FinancialRules.puedeFinalizarCompra(0, 0));
        assertFalse(FinancialRules.puedeFinalizarCompra(2, 0));
        assertTrue(FinancialRules.puedeFinalizarCompra(2, 1));
        assertFalse(FinancialRules.puedeFinalizarCompra(1, 2));
    }
}
