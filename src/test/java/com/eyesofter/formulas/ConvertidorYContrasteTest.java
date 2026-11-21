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
        // 33 + 2 + 1 = 36 letras → 0.05·36 − 0.15 = 1.65
        assertEquals(1.65, p.getLogCs(), 1e-9);
    }

    @Test
    void grisRespetaContrasteConGammaSrgb() {
        SensibilidadContraste.Gris g = SensibilidadContraste.grisParaLogCs(1.0); // contraste 10 %
        assertEquals(0.10, g.contrasteReal(), 0.01);
        assertEquals(0, SensibilidadContraste.grisParaLogCs(0.0).valor8bits());
    }
}
