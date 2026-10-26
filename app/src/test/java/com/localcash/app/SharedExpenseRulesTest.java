package com.localcash.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

import com.localcash.app.util.SharedExpenseRules;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class SharedExpenseRulesTest {
    @Test public void divideEnPesosEnterosYEntregaResiduoAlUsuario() {
        SharedExpenseRules.DivisionEquitativa division =
                SharedExpenseRules.dividirEquitativamente(500_000L, 2);
        assertEquals(166_668L, division.getMontoUsuario());
        assertEquals(166_666L, division.getMontoPorContacto());
    }

    @Test public void aceptaDetalleOpcionalYRechazaLineasOSumasInvalidas() {
        assertTrue(SharedExpenseRules.validarDetalleOpcional("", 500_000L).isEmpty());
        assertEquals(2, SharedExpenseRules.validarDetalleOpcional(
                "Hospedaje | 300000\nTransporte | 200000", 500_000L).size());
        assertThrows(IllegalArgumentException.class, () ->
                SharedExpenseRules.validarDetalleOpcional("Hospedaje y transporte", 500_000L));
        assertThrows(IllegalArgumentException.class, () ->
                SharedExpenseRules.validarDetalleOpcional("Hospedaje | 200000", 500_000L));
    }

    @Test public void validaAsignacionesExactasYParticipantesUnicos() {
        Map<Long, Long> montos = new HashMap<>();
        montos.put(4L, 150_000L); montos.put(7L, 150_000L);
        SharedExpenseRules.validarAsignacionesPersonalizadas(500_000L,
                Arrays.asList(4L, 7L), montos, 200_000L);
        assertThrows(IllegalArgumentException.class, () ->
                SharedExpenseRules.validarAsignacionesPersonalizadas(500_000L,
                        Arrays.asList(4L, 7L), montos, 100_000L));
        assertThrows(IllegalArgumentException.class, () ->
                SharedExpenseRules.validarParticipantes(Arrays.asList(4L, 4L)));
    }
}
