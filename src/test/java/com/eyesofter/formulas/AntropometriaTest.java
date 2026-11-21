package com.eyesofter.formulas;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class AntropometriaTest {

    @Test
    void laDpCreceConLaEdad() {
        double anterior = 0;
        for (int edad = 0; edad <= 20; edad++) {
            double dp = Antropometria.dpEsperadaMm(edad);
            assertTrue(dp >= anterior, "DP no decreciente a los " + edad);
            anterior = dp;
        }
        assertEquals(50.0, Antropometria.dpEsperadaMm(5), 1e-9);
        assertEquals(Antropometria.DP_ADULTO_MM, Antropometria.dpEsperadaMm(35), 1e-9);
        assertEquals(Antropometria.DP_ADULTO_MM, Antropometria.dpEsperadaMm(Double.NaN), 1e-9);
        // Interpolación con meses: 5.5 años está entre 5 y 6.
        assertEquals(50.5, Antropometria.dpEsperadaMm(5.5), 1e-9);
    }

    @Test
    void fechaDesdeCurp() {
        // CURP de ejemplo con el formato oficial (no son personas reales). El carácter 17 es
        // letra para nacidos desde 2000 y dígito para nacidos antes.
        assertEquals(LocalDate.of(2017, 9, 15), Antropometria.fechaDesdeCurp("MARV170915MDFTSAA4"));
        assertEquals(LocalDate.of(1985, 3, 2), Antropometria.fechaDesdeCurp("GOMA850302HDFRRN09"));
        assertNull(Antropometria.fechaDesdeCurp("ABC"));
        assertNull(Antropometria.fechaDesdeCurp("MARV171315MDFTSAA4"), "mes 13 no es válido");
    }

    @Test
    void edadPrefiereLaFechaYUsaLaCurpComoRespaldo() {
        LocalDate hoy = LocalDate.of(2026, 9, 15);
        assertEquals(9.0, Antropometria.edadPaciente("", "MARV170915MDFTSAA4", hoy), 1e-6);
        assertEquals(6.0, Antropometria.edadPaciente("2020-09-15", "MARV170915MDFTSAA4", hoy), 1e-6);
        assertTrue(Double.isNaN(Antropometria.edadPaciente("", "", hoy)));
    }
}
