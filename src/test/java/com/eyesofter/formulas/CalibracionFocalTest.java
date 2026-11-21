package com.eyesofter.formulas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La cámara se calibra con una persona y se usa con otra. Estas pruebas simulan
 * la imagen con el modelo pinhole: separación en px = DP · f / d.
 */
class CalibracionFocalTest {
    private static final double ANCHO = 1280;
    private static final double FOCAL_REAL = 0.78; // focal / ancho de esta cámara

    private static double separacionPx(double dpMm, double distanciaMm) {
        return dpMm * FOCAL_REAL * ANCHO / distanciaMm;
    }

    @Test
    void calibrarConUnAdultoYMedirAUnNinoConSuDp() {
        double dpAdulto = 64, dpNino = 50;
        double focal = CalibracionFocal.focalNormalizada(separacionPx(dpAdulto, 1500), 1500, dpAdulto, ANCHO);
        assertEquals(FOCAL_REAL, focal, 1e-9);

        // El niño está realmente a 3 m.
        double estimada = CalibracionFocal.distanciaMm(dpNino, focal, ANCHO, separacionPx(dpNino, 3000));
        assertEquals(3000, estimada, 1e-6);
    }

    @Test
    void elSesgoAnteriorConUnaDpFijaParaTodos() {
        // Antes: DP fija de 55 mm al calibrar y al medir. Calibra un adulto (64 mm) y luego un niño de 5 años (50 mm).
        double fija = 55;
        double focal = CalibracionFocal.focalNormalizada(separacionPx(64, 1500), 1500, fija, ANCHO);
        double estimadaNino = CalibracionFocal.distanciaMm(fija, focal, ANCHO, separacionPx(50, 3000));
        // La app creía que el niño estaba 28 % más lejos: le pedía acercarse y las letras le quedaban grandes.
        assertEquals(3000 * 64 / 50.0, estimadaNino, 1e-6);
        assertTrue(estimadaNino > 3000 * 1.25);
    }

    @Test
    void medirDpConCintaYCamaraCalibrada() {
        double dp = CalibracionFocal.dpMedidaMm(separacionPx(58.5, 2000), 2000, FOCAL_REAL, ANCHO);
        assertEquals(58.5, dp, 1e-9);
    }

    @Test
    void laFocalNoDependeDeLaResolucion() {
        double focal720 = CalibracionFocal.focalNormalizada(separacionPx(62, 1000), 1000, 62, ANCHO);
        // Misma cámara abierta a 640 px de ancho: todo se ve a la mitad.
        double sep640 = separacionPx(62, 2000) * 640 / ANCHO;
        assertEquals(2000, CalibracionFocal.distanciaMm(62, focal720, 640, sep640), 1e-6);
    }

    @Test
    void rostroGirado() {
        assertFalse(CalibracionFocal.rostroGirado(100, 160, 131, 60));
        assertTrue(CalibracionFocal.rostroGirado(100, 160, 150, 60));
    }

    @Test
    void toleranciaProporcionalALaDistancia() {
        assertEquals(15.0, CalibracionFocal.toleranciaEfectivaCm(15, 300), 1e-9); // 3 m: se respeta la configurada
        assertEquals(8.0, CalibracionFocal.toleranciaEfectivaCm(15, 80), 1e-9);   // 0.8 m: 10 %, no 19 %
        assertEquals(5.0, CalibracionFocal.toleranciaEfectivaCm(15, 50), 1e-9);
    }
}
