package com.eyesofter.formulas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrigonometriaVisualTest {

    @Test
    void letra2020a6MetrosMide873mm() {
        // Referencia clínica: optotipo 20/20 a 6 m = 5 minutos de arco = 8.73 mm.
        assertEquals(8.727, TrigonometriaVisual.calcularAlturaMM(6000, 1.0), 0.005);
    }

    @Test
    void letra20200a1MetroMide1454mm() {
        assertEquals(14.54, TrigonometriaVisual.calcularAlturaMM(1000, 10.0), 0.01);
    }

    @Test
    void aproximacionLinealCoincideConExacta() {
        double exacta = TrigonometriaVisual.calcularAlturaMM(3000, 5);
        double lineal = TrigonometriaVisual.calcularAlturaMMLineal(3000, 5);
        assertEquals(exacta, lineal, exacta * 1e-4);
    }

    @Test
    void tamanioLogicoDivideEntreEscalaDeWindows() {
        double fisico = TrigonometriaVisual.calcularTamanioFuenteAjustado(10, 144, 0.716);
        assertEquals(fisico / 1.5, TrigonometriaVisual.calcularTamanioFuenteLogico(10, 144, 0.716, 1.5), 1e-9);
    }

    @Test
    void ppiPorTarjeta() {
        // Tarjeta ID-1 de 85.60 mm que ocupa 337 px físicos → 100 PPI.
        assertEquals(100.0, TrigonometriaVisual.calcularPPIPorObjeto(337.0, 85.60), 0.01);
    }

    @Test
    void conversionesSnellen() {
        assertEquals("20/20", TrigonometriaVisual.snellenDesdeLogMAR(0.0));
        assertEquals("20/40", TrigonometriaVisual.snellenDesdeLogMAR(0.3));
        assertEquals("20/200", TrigonometriaVisual.snellenDesdeLogMAR(1.0));
        assertEquals("20/16", TrigonometriaVisual.snellenDesdeLogMAR(-0.1));
        assertEquals("20/63", TrigonometriaVisual.snellenDesdeLogMAR(0.5));
        assertEquals("6/12", TrigonometriaVisual.snellenMetricoDesdeLogMAR(0.3));
    }

    @Test
    void puntuacionEtdrsTresLetrasPorFila() {
        // Desde 1.0 con 33 letras correctas (11 filas completas) → 0.0
        assertEquals(0.0, TrigonometriaVisual.calcularPuntuacionETDRS(1.0, 3, 33), 1e-9);
        assertEquals(1.1, TrigonometriaVisual.calcularPuntuacionETDRS(1.0, 3, 0), 1e-9);
    }
}
