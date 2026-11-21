package com.eyesofter.formulas;

/**
 * Módulo matemático para la calibración analítica y medición vectorial
 * utilizando algoritmos de visión artificial.
 */
public class CalibracionFocal {

    // Constantes antropométricas por defecto (IPD en milímetros)
    public static final double IPD_PROMEDIO_ADULTO_MM = 62.0;
    public static final double IPD_PROMEDIO_INFANTIL_MM = 55.0;

    /**
     * Fórmula 1: Aislamiento de la Constante Intrínseca de Calibración Focal (f).
     * @param pCalib Separación de centros pupilares en píxeles.
     * @param dCalib Distancia física real de calibración en cm.
     * @param ipdReal Distancia Interpupilar real en mm.
     * @return Distancia focal (f) en píxeles.
     */
    public static double calcularDistanciaFocal(double pCalib, double dCalib, double ipdReal) {
        // Convertimos dCalib de cm a mm para mantener consistencia de unidades
        double dCalibMm = dCalib * 10.0;
        return (pCalib * dCalibMm) / ipdReal;
    }

    /**
     * Fórmula 2: Ecuación Iterativa de Cálculo Dinámico en Tiempo Real (d).
     * @param ipdReal Distancia Interpupilar real en mm.
     * @param f Distancia focal calibrada del lente.
     * @param pActual Magnitud del vector interpupilar actual en píxeles.
     * @return Distancia real (d) en centímetros.
     */
    public static double calcularDistanciaZ(double ipdReal, double f, double pActual) {
        if (pActual <= 0) return -1; // Evitar división por cero
        double distanciaMm = (ipdReal * f) / pActual;
        return distanciaMm / 10.0; // Devolvemos en centímetros para la interfaz
    }

    /**
     * Fórmula 3: Expresión Pitagórica para Derivar la Magnitud del Vector Interpupilar.
     * @param x1 Coordenada X del ojo izquierdo.
     * @param y1 Coordenada Y del ojo izquierdo.
     * @param x2 Coordenada X del ojo derecho.
     * @param y2 Coordenada Y del ojo derecho.
     * @return Distancia euclidiana en píxeles (P_actual).
     */
    public static double calcularMagnitudPupilar(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }
}
