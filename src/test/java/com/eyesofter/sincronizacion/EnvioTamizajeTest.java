package com.eyesofter.sincronizacion;

import com.eyesofter.clinica.ResultadoClinico;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnvioTamizajeTest {

    private static ResultadoClinico resultado(double od, double oi, int deficiencia) {
        ResultadoClinico r = new ResultadoClinico();
        r.registrarOjo(ResultadoClinico.Ojo.AMBOS, ResultadoClinico.ResultadoOjo.desdeLogMar(Math.min(od, oi)));
        r.registrarOjo(ResultadoClinico.Ojo.DERECHO, ResultadoClinico.ResultadoOjo.desdeLogMar(od));
        r.registrarOjo(ResultadoClinico.Ojo.IZQUIERDO, ResultadoClinico.ResultadoOjo.desdeLogMar(oi));
        r.registrarIshihara(5 - deficiencia, 5, deficiencia);
        return r;
    }

    @Test
    void semaforoSegunPrioridad() {
        assertEquals("VERDE", ColaSincronizacion.semaforo(resultado(0.0, 0.0, 0)));
        assertEquals("AMARILLO", ColaSincronizacion.semaforo(resultado(0.3, 0.0, 0)));
        assertEquals("AMARILLO", ColaSincronizacion.semaforo(resultado(0.0, 0.0, 2)));
        assertEquals("ROJO", ColaSincronizacion.semaforo(resultado(0.9, 0.0, 0)));
    }

    @Test
    void agudezaConFormatoDelContrato() {
        ResultadoClinico r = resultado(0.3, 1.2, 0);
        assertEquals("20/40", ColaSincronizacion.agudeza(r.getOjoDerecho()));
        assertNull(ColaSincronizacion.agudeza(r.getOjoIzquierdo()), "<20/200 no cabe en 20/NN: va en observaciones");
        assertTrue(ColaSincronizacion.observaciones(r).contains("<20/200"));
    }

    @Test
    void observacionesNoPasanDe500() {
        assertTrue(ColaSincronizacion.observaciones(resultado(0.9, 1.2, 3)).length() <= 500);
    }

    @Test
    void soloHttpsSalvoLocal() {
        assertNotNull(ClienteSuite.validarUrl("http://servidor.com"));
        assertNull(ClienteSuite.validarUrl("https://servidor.com"));
        assertNull(ClienteSuite.validarUrl("http://localhost:8080"));
    }
}
