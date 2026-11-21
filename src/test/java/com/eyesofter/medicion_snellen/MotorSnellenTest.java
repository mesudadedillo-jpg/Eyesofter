package com.eyesofter.medicion_snellen;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class MotorSnellenTest {

    /** Simula a una persona: dado el nivel, cuántas letras lee. */
    /** Pantalla de alta densidad: todas las filas se pueden dibujar a 1 m. */
    private static final double PPI_ALTO = 300;

    private static MotorSnellen correr(IntUnaryOperator aciertosPorNivel) {
        return correr(aciertosPorNivel, 1.0, PPI_ALTO);
    }

    private static MotorSnellen correr(IntUnaryOperator aciertosPorNivel, double metros, double ppi) {
        MotorSnellen m = new MotorSnellen(null, null, new Random(7));
        m.configurarEntornoPrueba(metros, ppi);
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
        assertFalse(m.isLimitadoPorPantalla());
    }

    @Test
    void pantallaDeBajaDensidadNoMuestraFilasIlegibles() {
        // A 1 m y 96 PPI: 20/20 mide 5.5 px (sí), 20/16 mide 4.4 px (no: menos de 5 px).
        MotorSnellen m = correr(n -> 3, 1.0, 96);
        assertEquals(11, m.getNivelesMedibles());
        assertEquals(0.0, m.getLogMarLimitePantalla(), 1e-9);
        assertEquals(0.0, m.getLogMarPuntuado(), 1e-9);
        assertTrue(m.isLimitadoPorPantalla(), "leyó todo lo que la pantalla podía dibujar");
        for (MotorSnellen.FilaEvaluada f : m.getHistorial()) assertTrue(f.nivel() < 11);
    }

    @Test
    void aMedioMetroUnaLaptopNoLlegaA2032() {
        // 0.5 m y 96 PPI: solo se puede dibujar hasta 20/40 (LogMAR 0.3).
        assertEquals(0.3, MotorSnellen.mejorLogMarMedible(500, 96), 1e-9);
        double minima = com.eyesofter.formulas.TrigonometriaVisual.distanciaMinimaMmParaLogMar(0.2, 96);
        assertTrue(MotorSnellen.mejorLogMarMedible(minima + 1, 96) <= 0.2 + 1e-9);
    }

    @Test
    void personaConVisionBajaNoSeMarcaComoLimitada() {
        MotorSnellen m = correr(n -> n <= 7 ? 3 : 0, 1.0, 96);
        assertEquals(0.3, m.getLogMarPuntuado(), 1e-9);
        assertFalse(m.isLimitadoPorPantalla());
    }

    // --------------------------------------------------------------
    // E direccional
    // --------------------------------------------------------------

    private static MotorSnellen correrE(IntUnaryOperator aciertosPorNivel, long semilla) {
        MotorSnellen m = new MotorSnellen(null, null, new Random(semilla));
        m.configurarEntornoPrueba(1.0, PPI_ALTO);
        m.setTipoOptotipo(MotorSnellen.TipoOptotipo.E_DIRECCIONAL);
        int guardia = 0;
        while (m.haySiguienteFila() && guardia++ < 40) {
            MotorSnellen.Fila f = m.generarSiguienteFila();
            assertEquals(4, f.letras.length);
            for (int i = 1; i < f.letras.length; i++) assertNotEquals(f.letras[i - 1], f.letras[i], "sin dos direcciones iguales seguidas");
            m.registrarResultadoFila(f, aciertosPorNivel.applyAsInt(f.nivel));
        }
        return m;
    }

    @Test
    void eDireccionalPuntuaPorFilaCompleta() {
        // Lee 4/4 hasta 0.3, 2/4 en 0.2 (no pasa: se piden 3) → 0.3 exacto, sin sumar aciertos sueltos.
        MotorSnellen m = correrE(n -> n <= 7 ? 4 : n == 8 ? 2 : 0, 3);
        assertEquals(0.3, m.getLogMarPuntuado(), 1e-9);
        assertEquals(7, m.getIndiceUltimaFilaCorrecta());
    }

    @Test
    void eDireccionalTresDeCuatroPasaLaFila() {
        MotorSnellen m = correrE(n -> n <= 8 ? 3 : 0, 5);
        assertEquals(0.2, m.getLogMarPuntuado(), 1e-9);
    }

    @Test
    void adivinarConLaENoDaBuenaVision() {
        // Un niño que no ve nada y adivina (25 % por figura) casi nunca pasa 20/200 con el criterio 3/4.
        int pasan = 0;
        for (long semilla = 0; semilla < 400; semilla++) {
            Random azar = new Random(semilla * 31 + 1);
            MotorSnellen m = correrE(n -> {
                int aciertos = 0;
                for (int i = 0; i < 4; i++) if (azar.nextInt(4) == 0) aciertos++;
                return aciertos;
            }, semilla);
            if (m.getIndiceUltimaFilaCorrecta() >= 0) pasan++;
        }
        assertTrue(pasan < 400 * 0.10, "adivinando pasaron " + pasan + " de 400");
    }

    @Test
    void ladoDeLaEEnPixeles() {
        MotorSnellen m = new MotorSnellen(null, null);
        m.configurarEntornoPrueba(1.0, 96);
        // 20/200 a 1 m = 14.54 mm → 54.96 px; la E se dibuja con figuras: sin factor tipográfico.
        assertEquals(14.54 / 25.4 * 96, m.calcularLadoEEnPixeles(0.1), 0.05);
        m.setEscalaSalida(1.5);
        assertEquals(14.54 / 25.4 * 96 / 1.5, m.calcularLadoEEnPixeles(0.1), 0.05);
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

    @Test
    void siLaFila20200NoCabeSeEmpiezaEnLaPrimeraQueCabe() {
        // Laptop 15.6" FHD (141 PPI) a 4 m: una fila de 4 E de 20/200 mide ~2250 px; la ventana ~1840.
        MotorSnellen m = new MotorSnellen(null, null, new Random(2));
        m.configurarEntornoPrueba(4.0, 141.2);
        m.setTipoOptotipo(MotorSnellen.TipoOptotipo.E_DIRECCIONAL);
        m.setEspacioDisponible(1840, 480);
        assertTrue(m.getNivelInicial() > 0, "debe saltar las filas que no caben");
        int inicial = m.getNivelInicial();
        MotorSnellen.Fila primera = m.generarSiguienteFila();
        assertEquals(inicial, primera.nivel);
        assertTrue(7 * primera.tamanioPx <= 1840);
    }

    @Test
    void filasQueNoCupieronSeDanPorLeidasSiLeeLaPrimera() {
        MotorSnellen m = new MotorSnellen(null, null, new Random(4));
        m.configurarEntornoPrueba(4.0, 141.2);
        m.setEspacioDisponible(900, 300); // ventana chica: las letras grandes no caben
        int inicial = m.getNivelInicial();
        assertTrue(inicial > 0);
        // Lee perfecto hasta 20/40 (nivel 7) y nada después.
        int guardia = 0;
        while (m.haySiguienteFila() && guardia++ < 40) {
            MotorSnellen.Fila f = m.generarSiguienteFila();
            assertTrue(f.nivel >= inicial);
            m.registrarResultadoFila(f, f.nivel <= 7 ? 3 : 0);
        }
        assertEquals(0.3, m.getLogMarPuntuado(), 1e-9, "igual que si se hubieran mostrado todas");
    }

    @Test
    void noLeerLaPrimeraFilaMostradaNoEsMejorQue20200() {
        MotorSnellen m = new MotorSnellen(null, null, new Random(4));
        m.configurarEntornoPrueba(4.0, 141.2);
        m.setEspacioDisponible(900, 300);
        MotorSnellen.Fila f = m.generarSiguienteFila();
        m.registrarResultadoFila(f, 0);
        assertFalse(m.haySiguienteFila());
        assertEquals(-1, m.getIndiceUltimaFilaCorrecta());
        assertTrue(m.getLogMarPuntuado() > 1.0, "sin crédito por las filas que no se mostraron");
    }
}
