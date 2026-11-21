package com.eyesofter.formulas;

/**
 * Módulo de dimensionamiento matemático de optotipos y algoritmos evaluativos ETDRS.
 */
public class TrigonometriaVisual {

    /**
     * Fórmula 4: Conversión Estándar Inversa a Ángulo Mínimo de Resolución Visual (MAR).
     */
    public static double calcularMAR(double vDecimal) {
        if (vDecimal <= 0) return 0;
        return 1.0 / vDecimal;
    }

    /**
     * Fórmula 5: Ecuación Trigonométrica para la Altura Física Objetiva (h_mm).
     * @param d Distancia del paciente a la pantalla en milímetros.
     * @param mar Ángulo Mínimo de Resolución Visual.
     * @return Altura estricta del optotipo en milímetros.
     */
    public static double calcularAlturaMM(double d, double mar) {
        // Uso de la versión trigonométrica exacta para mayor rigor científico
        return 2 * d * Math.tan((mar * Math.PI) / 8640.0);
    }

    /**
     * Algoritmia linealizada de la Fórmula 5 (Alto rendimiento en CPU).
     * Útil si el renderizado en JavaFX sufre caídas de fotogramas.
     */
    public static double calcularAlturaMMLineal(double d, double mar) {
        return (d * mar * Math.PI) / 4320.0;
    }

    /**
     * Calcula la densidad de píxeles del monitor (PPI). Este es el "Factor 2"
     * (tamaño del monitor) del dimensionamiento de optotipos: a partir de la
     * resolución (detectada por JavaFX Screen) y la diagonal física en pulgadas
     * (dato que el operador ingresa, porque el sistema operativo no siempre lo
     * expone con fiabilidad) se obtiene el PPI real del equipo.
     * @param resX Resolución horizontal de la pantalla (ej. 1920).
     * @param resY Resolución vertical de la pantalla (ej. 1080).
     * @param pulgadasDiagonal Tamaño físico de la pantalla en pulgadas (ej. 15.6).
     */
    public static double calcularPPI(double resX, double resY, double pulgadasDiagonal) {
        if (pulgadasDiagonal <= 0) return 0;
        return Math.sqrt(Math.pow(resX, 2) + Math.pow(resY, 2)) / pulgadasDiagonal;
    }

    /**
     * Fórmula 6: Transformación Transescalar de milímetros a Matriz de Píxeles.
     */
    public static double calcularAlturaPixel(double hMm, double ppi) {
        // 25.4 es la constante de conversión exacta de pulgadas a milímetros
        return (hMm / 25.4) * ppi;
    }

    /**
     * NUEVO — Fórmula 6-b: igual que calcularAlturaPixel, pero corrigiendo por
     * el "Factor 3" (tipo de letra). El tamaño de fuente (point size) que hay
     * que aplicar en pantalla NO es igual a la altura óptica real que exige la
     * fórmula 6, porque cada tipografía tiene una relación distinta entre su
     * "em square" (el tamaño nominal) y la altura real de sus glifos (cap-height).
     * Por eso dividimos la altura en píxeles requerida entre el factor de
     * corrección de la fuente elegida (ver {@link FuentesOptotipo}), y así el
     * tamaño de fuente que se aplica en la interfaz sí produce físicamente la
     * altura óptica correcta a la distancia y PPI configurados.
     *
     * @param hMm Altura física requerida en milímetros (fórmula 5).
     * @param ppi Densidad de píxeles del monitor (fórmula "calcularPPI").
     * @param factorCorreccionFuente Relación cap-height/em de la tipografía elegida (ver FuentesOptotipo).
     * @return Tamaño de fuente en puntos/píxeles a aplicar en el Label de JavaFX.
     */
    public static double calcularTamanioFuenteAjustado(double hMm, double ppi, double factorCorreccionFuente) {
        double alturaPixelRequerida = calcularAlturaPixel(hMm, ppi);
        if (factorCorreccionFuente <= 0) factorCorreccionFuente = 1.0;
        return alturaPixelRequerida / factorCorreccionFuente;
    }

    /**
     * Fórmula 7: Codificación Computarizada LogMAR.
     */
    public static double calcularLogMAR(double vDecimal) {
        if (vDecimal <= 0) return 0;
        return -Math.log10(vDecimal);
    }

    /**
     * Fórmula 8: Adición Estocástica Penalizante por Letra (Criterio ETDRS).
     * @param logMarInicial El valor LogMAR de la fila que se está evaluando.
     * @param nAciertos Cantidad de letras leídas correctamente en esa fila.
     * @return Fracción fina de agudeza visual.
     */
    public static double calcularPuntuacionETDRS(double logMarInicial, int nAciertos) {
        // Cada acierto recompensa restando 0.02 a la escala logarítmica
        return logMarInicial - (nAciertos * 0.02);
    }
}
