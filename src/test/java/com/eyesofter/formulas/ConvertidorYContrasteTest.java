package com.eyesofter.formulas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConvertidorYContrasteTest {

    @Test
    void convierteSnellenADecimalYLogMar() {
        ConvertidorAgudeza.Equivalencias e = ConvertidorAgudeza.convertir("20/40", ConvertidorAgudeza.Notacion.SNELLEN);
        assertEquals(0.5, e.decimal(), 1e-9);
        assertEquals(0.301, e.logMar(), 0.001);
        assertEquals("6/12", e.snellenMetrico());
    }

    @Test
    void convierteMetricoYDecimal() {
        assertEquals("20/20", ConvertidorAgudeza.convertir("6/6", ConvertidorAgudeza.Notacion.SNELLEN).snellenPies());
        assertEquals("20/25", ConvertidorAgudeza.convertir("0,8", ConvertidorAgudeza.Notacion.DECIMAL).snellenPies());
        assertThrows(IllegalArgumentException.class, () -> ConvertidorAgudeza.convertir("20-40", ConvertidorAgudeza.Notacion.SNELLEN));
    }

    @Test
    void contrastePelliRobsonLetraPorLetra() {
        SensibilidadContraste p = new SensibilidadContraste();
        for (int i = 0; i < 11; i++) p.registrarTriplete(3); // 0.00 … 1.50 completos
        p.registrarTriplete(2);                             // 1.65: 2 de 3
        assertFalse(p.terminada());
        p.registrarTriplete(1);                             // 1.80: 1 de 3 → termina
        assertTrue(p.terminada());
        // Nominal habría sido 0.05·36 − 0.15 = 1.65, pero con 8 bits los tripletes 1.50, 1.65 y 1.80
        // se dibujan como 1.45, 1.58 y 1.75: se puntúa con lo que realmente se mostró.
        assertEquals(1.59, p.getLogCs(), 1e-9);
    }

    @Test
    void soloSeMuestranTripletesConGrisDistinto() {
        // 1.95, 2.10 y 2.25 caen en el mismo gris 254: solo se muestra el primero de ellos.
        assertEquals(14, SensibilidadContraste.tripletesRepresentables());
        assertEquals(254, SensibilidadContraste.grisParaLogCs(SensibilidadContraste.logCsDeTriplete(13)).valor8bits());
        SensibilidadContraste p = new SensibilidadContraste();
        for (int i = 0; i < 16; i++) p.registrarTriplete(3);
        assertTrue(p.terminada());
        assertEquals(14, p.getTripletesEvaluados());
        // Lo máximo que se puede acreditar es el contraste del gris 254 (≈ 2.05), no 2.25.
        assertEquals(2.05, p.getLogCs(), 0.01);
    }

    @Test
    void conPasosExactosEsLaFormulaClasica() {
        SensibilidadContraste p = new SensibilidadContraste();
        for (int i = 0; i < 5; i++) p.registrarTriplete(3);   // 0.00 … 0.60, casi exactos en 8 bits
        p.registrarTriplete(1);
        assertEquals(0.05 * 16 - 0.15, p.getLogCs(), 0.02);
    }

    @Test
    void grisRespetaContrasteConGammaSrgb() {
        SensibilidadContraste.Gris g = SensibilidadContraste.grisParaLogCs(1.0); // contraste 10 %
        assertEquals(0.10, g.contrasteReal(), 0.01);
        assertEquals(0, SensibilidadContraste.grisParaLogCs(0.0).valor8bits());
    }
}
