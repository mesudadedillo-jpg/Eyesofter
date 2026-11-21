package com.eyesofter.clinica;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultadoClinicoTest {

    private static ResultadoClinico con(double od, double oi) {
        ResultadoClinico r = new ResultadoClinico();
        r.registrarOjo(ResultadoClinico.Ojo.DERECHO, ResultadoClinico.ResultadoOjo.desdeLogMar(od));
        r.registrarOjo(ResultadoClinico.Ojo.IZQUIERDO, ResultadoClinico.ResultadoOjo.desdeLogMar(oi));
        return r;
    }

    @Test
    void sinBanderasConVisionNormal() {
        assertEquals(ResultadoClinico.PrioridadSeguimiento.SIN_BANDERAS, con(0.0, 0.1).getPrioridadSeguimiento());
    }

    @Test
    void asimetriaDeDosLineas() {
        ResultadoClinico r = con(0.0, 0.2);
        assertEquals(ResultadoClinico.PrioridadSeguimiento.PREFERENTE, r.getPrioridadSeguimiento());
        assertTrue(r.getAlertasComoTexto().contains("ojo izquierdo"));
    }

    @Test
    void agudezaReducidaYMarcadamenteReducida() {
        assertEquals(ResultadoClinico.PrioridadSeguimiento.PREFERENTE, con(0.3, 0.3).getPrioridadSeguimiento());
        assertEquals(ResultadoClinico.PrioridadSeguimiento.PRIORITARIA, con(0.8, 0.0).getPrioridadSeguimiento());
        assertEquals(ResultadoClinico.PrioridadSeguimiento.PRIORITARIA, con(1.07, 0.0).getPrioridadSeguimiento());
    }
}
