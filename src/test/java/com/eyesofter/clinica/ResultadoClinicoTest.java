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

    private static ResultadoClinico conEdad(double edad, double od, double oi) {
        ResultadoClinico r = con(od, oi);
        r.registrarEdad(edad);
        return r;
    }

    @Test
    void criterioPorEdad() {
        // 3 años: 20/50 es normal para la edad (antes se refería por ser peor que 20/40).
        assertEquals(ResultadoClinico.PrioridadSeguimiento.SIN_BANDERAS, conEdad(3.5, 0.4, 0.4).getPrioridadSeguimiento());
        assertEquals(ResultadoClinico.PrioridadSeguimiento.PREFERENTE, conEdad(3.5, 0.5, 0.4).getPrioridadSeguimiento());
        // 4 años: hay que leer 20/40.
        assertEquals(ResultadoClinico.PrioridadSeguimiento.SIN_BANDERAS, conEdad(4.2, 0.3, 0.3).getPrioridadSeguimiento());
        // 6 años: hay que leer 20/32; 20/40 ya se refiere.
        ResultadoClinico seis = conEdad(6, 0.3, 0.2);
        assertEquals(ResultadoClinico.PrioridadSeguimiento.PREFERENTE, seis.getPrioridadSeguimiento());
        assertTrue(seis.getAlertasComoTexto().contains("OD no leyó la línea 20/32"), seis.getAlertasComoTexto());
        // Adulto: se conserva el criterio de 20/40.
        assertEquals(ResultadoClinico.PrioridadSeguimiento.SIN_BANDERAS, conEdad(30, 0.2, 0.2).getPrioridadSeguimiento());
    }

    @Test
    void menorDeTresAniosAvisa() {
        assertTrue(conEdad(2.5, 0.4, 0.4).getAlertasComoTexto().contains("Menor de 3 años"));
    }

    private static ResultadoClinico limitado(double edad, double limitePantalla) {
        ResultadoClinico r = new ResultadoClinico();
        r.registrarEdad(edad);
        r.registrarOjo(ResultadoClinico.Ojo.DERECHO, ResultadoClinico.ResultadoOjo.desdeLogMar(limitePantalla, true));
        r.registrarOjo(ResultadoClinico.Ojo.IZQUIERDO, ResultadoClinico.ResultadoOjo.desdeLogMar(limitePantalla, true));
        CondicionesPrueba c = new CondicionesPrueba();
        c.logMarLimitePantalla = limitePantalla;
        c.metodoPantalla = CondicionesPrueba.MetodoPantalla.TARJETA;
        c.brilloConfirmado = true;
        r.registrarCondiciones(c);
        return r;
    }

    @Test
    void pantallaQueNoLlegaALaLineaEsNoConcluyente() {
        // Niño de 6 años que leyó todo lo que la pantalla podía dibujar (20/40): no se puede decir que pasa ni que falla.
        ResultadoClinico r = limitado(6, 0.3);
        assertEquals(ResultadoClinico.PrioridadSeguimiento.NO_CONCLUYENTE, r.getPrioridadSeguimiento());
        assertTrue(r.getAlertasComoTexto().contains("NO CONCLUYENTE"));
        assertFalse(r.getAlertasComoTexto().contains("no leyó la línea"));
    }

    @Test
    void pantallaSuficienteEnElLimiteNoEsBandera() {
        ResultadoClinico r = limitado(6, 0.0);
        assertEquals(ResultadoClinico.PrioridadSeguimiento.SIN_BANDERAS, r.getPrioridadSeguimiento());
        assertTrue(r.getAlertasComoTexto().contains("agudeza real puede ser mejor"));
        assertTrue(r.getOjoDerecho().resumen().contains("o mejor"));
    }

    @Test
    void avisosDeCondiciones() {
        ResultadoClinico r = con(0.0, 0.0);
        CondicionesPrueba c = new CondicionesPrueba();
        c.metodoPantalla = CondicionesPrueba.MetodoPantalla.DIAGONAL;
        c.metodoDistancia = CondicionesPrueba.MetodoDistancia.SIN_CAMARA;
        r.registrarCondiciones(c);
        String alertas = r.getAlertasComoTexto();
        assertTrue(alertas.contains("no medido con tarjeta"));
        assertTrue(alertas.contains("Distancia sin verificar"));
        assertTrue(alertas.contains("brillo"));
    }

    @Test
    void lentesYVozInfantil() {
        ResultadoClinico r = conEdad(6, 0.1, 0.1);
        CondicionesPrueba c = new CondicionesPrueba();
        c.correccion = CondicionesPrueba.Correccion.SIN_SUS_LENTES;
        r.registrarCondiciones(c);
        r.registrarMetodoEntrada("Reconocimiento de voz");
        String alertas = r.getAlertasComoTexto();
        assertTrue(alertas.contains("se evaluó sin ellos"));
        assertTrue(alertas.contains("menor de 8 años"));

        ResultadoClinico adulto = conEdad(30, 0.1, 0.1);
        adulto.registrarMetodoEntrada("Reconocimiento de voz");
        assertFalse(adulto.getAlertasComoTexto().contains("menor de 8 años"));
    }
}
