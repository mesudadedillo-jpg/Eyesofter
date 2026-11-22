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
     * Focal típica de una cámara web dividida entre el ancho del fotograma (campo de
     * visión horizontal ≈ 65°). Solo se usa mientras la cámara no se ha calibrado.
     */
    public static final double FOCAL_NORMALIZADA_POR_DEFECTO = 0.79;

    /** Distancia (mm) con la focal normalizada: d = DP · (f/ancho · ancho) / p. */
    public static double distanciaMm(double dpMm, double focalNormalizada, double anchoFotogramaPx, double separacionOjosPx) {
        if (separacionOjosPx <= 0 || focalNormalizada <= 0 || anchoFotogramaPx <= 0) return -1;
        return dpMm * focalNormalizada * anchoFotogramaPx / separacionOjosPx;
    }

    /**
     * Calibración con una distancia medida con cinta y la DP de ESA persona (medida o
     * estimada por su edad). Así la focal describe a la cámara y no a la cara de
     * quien calibró.
     */
    public static double focalNormalizada(double separacionOjosPx, double distanciaRealMm, double dpPersonaMm, double anchoFotogramaPx) {
        if (separacionOjosPx <= 0 || distanciaRealMm <= 0 || dpPersonaMm <= 0 || anchoFotogramaPx <= 0) return -1;
        return separacionOjosPx * distanciaRealMm / (dpPersonaMm * anchoFotogramaPx);
    }

    /** DP real (mm) cuando la cámara ya está calibrada y la distancia se midió con cinta. */
    public static double dpMedidaMm(double separacionOjosPx, double distanciaRealMm, double focalNormalizada, double anchoFotogramaPx) {
        if (separacionOjosPx <= 0 || distanciaRealMm <= 0 || focalNormalizada <= 0 || anchoFotogramaPx <= 0) return -1;
        return separacionOjosPx * distanciaRealMm / (focalNormalizada * anchoFotogramaPx);
    }

    /**
     * Rostro girado: la nariz se aleja del punto medio entre los ojos. Con la cabeza
     * girada los ojos se ven más juntos y la distancia saldría mayor que la real,
     * así que esa lectura se descarta.
     */
    public static boolean rostroGirado(double ojoAx, double ojoBx, double narizX, double separacionOjosPx) {
        if (separacionOjosPx <= 0) return true;
        double medio = (ojoAx + ojoBx) / 2.0;
        return Math.abs(narizX - medio) / separacionOjosPx > UMBRAL_GIRO;
    }

    /** Fracción máxima de la distancia que se tolera (10 % ≈ 0.04 LogMAR, menos de media fila). */
    public static final double TOLERANCIA_RELATIVA_MAXIMA = 0.10;

    /**
     * Tolerancia efectiva: la configurada, pero nunca más del 10 % de la distancia objetivo.
     * Un margen fijo de ±15 cm es ±5 % a 3 m pero ±19 % a 0.8 m (casi una fila de error).
     */
    public static double toleranciaEfectivaCm(double toleranciaConfiguradaCm, double distanciaObjetivoCm) {
        double relativa = distanciaObjetivoCm * TOLERANCIA_RELATIVA_MAXIMA;
        return Math.max(3.0, Math.min(toleranciaConfiguradaCm, relativa));
    }

    /** Desplazamiento de la nariz (fracción de la separación de los ojos) a partir del cual se considera girado (~20°). */
    public static final double UMBRAL_GIRO = 0.22;

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
