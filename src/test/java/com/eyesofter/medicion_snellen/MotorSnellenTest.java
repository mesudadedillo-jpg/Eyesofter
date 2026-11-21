package com.eyesofter.medicion_snellen;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class MotorSnellenTest {

    /** Simula a una persona: dado el nivel, cuántas letras lee. */
    private static MotorSnellen correr(IntUnaryOperator aciertosPorNivel) {
        MotorSnellen m = new MotorSnellen(null, null, new Random(7));
        m.configurarEntornoPrueba(1.0, 96);
        m.reiniciarInteractivo();
        int guardia = 0;
        while (m.haySiguienteFila() && guardia++ < 40) {
            MotorSnellen.Fila f = m.generarSiguienteFila();
            assertEquals(3, java.util.Set.of(f.letras).size(), "las letras de una fila no se repiten");
            m.registrarResultadoFila(f, aciertosPorNivel.applyAsInt(f.nivel));
        }
        assertTrue(guardia < 40, "la prueba siempre termina");
        return m;
    }

    @Test
    void visionPerfectaLlegaA2016ConPocasFilas() {
        MotorSnellen m = correr(n -> 3);
        assertEquals(-0.1, m.getLogMarPuntuado(), 1e-9);
        assertTrue(m.getFilasEvaluadas() <= 6, "el cribado rápido ahorra filas: " + m.getFilasEvaluadas());
    }

    @Test
    void personaQueLeeHasta2040() {
        // Lee perfecto hasta LogMAR 0.3 (nivel 7) y nada a partir de 0.2.
        MotorSnellen m = correr(n -> n <= 7 ? 3 : 0);
        assertEquals(0.3, m.getLogMarPuntuado(), 1e-9);
        assertEquals(7, m.getIndiceUltimaFilaCorrecta());
    }

    @Test
    void puntuacionLetraPorLetraConFilaParcial() {
        // Perfecto hasta 0.3, 2/3 en 0.2, 1/3 en 0.1 → 0.3 − 0.1·(3/3) … = 0.3 − 0.033·3 = 0.20
        MotorSnellen m = correr(n -> n <= 7 ? 3 : n == 8 ? 2 : n == 9 ? 1 : 0);
        assertEquals(0.20, m.getLogMarPuntuado(), 1e-9);
    }

    @Test
    void noLee20200() {
        MotorSnellen m = correr(n -> 1);
        assertEquals(-1, m.getIndiceUltimaFilaCorrecta());
        assertEquals(1, m.getFilasEvaluadas());
        assertTrue(m.getLogMarPuntuado() > 1.0);
    }

    @Test
    void deshacerVuelveAlEstadoAnterior() {
        MotorSnellen m = new MotorSnellen(null, null, new Random(1));
        m.configurarEntornoPrueba(1.0, 96);
        MotorSnellen.Fila a = m.generarSiguienteFila();
        m.registrarResultadoFila(a, 3);
        int siguienteAntes = m.calcularSiguienteNivel();
        MotorSnellen.Fila b = m.generarSiguienteFila();
        m.registrarResultadoFila(b, 0);
        // El salto 20/200 → 20/100 falló: se vuelve a afinar desde 20/160.
        assertEquals(1, m.calcularSiguienteNivel());
        assertTrue(m.deshacerUltimaFila());
        assertEquals(siguienteAntes, m.calcularSiguienteNivel());
    }

    @Test
    void tamanioEnPixelesCorrecto() {
        MotorSnellen m = new MotorSnellen(null, null);
        m.configurarEntornoPrueba(1.0, 96, "Arial");
        // 20/200 a 1 m = 14.54 mm → 54.96 px físicos a 96 PPI → ÷ 0.716 cap-height
        assertEquals(14.54 / 25.4 * 96 / 0.716, m.calcularTamanioLetraEnPixeles(0.1), 0.05);
        m.setEscalaSalida(1.25);
        assertEquals(14.54 / 25.4 * 96 / 0.716 / 1.25, m.calcularTamanioLetraEnPixeles(0.1), 0.05);
    }
}
