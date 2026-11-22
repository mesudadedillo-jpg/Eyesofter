package com.eyesofter.formulas;

/**
 * Módulo de dimensionamiento matemático de optotipos y algoritmos evaluativos ETDRS.
 *
 * <p>Convención: un optotipo de agudeza decimal V subtiende 5 minutos de arco × MAR
 * en su altura total, con MAR = 1 / V (minutos de arco). A 6 m, una letra 20/20
 * mide 8.73 mm.</p>
 */
public class TrigonometriaVisual {

    /** Minutos de arco que subtiende la altura de un optotipo con MAR = 1. */
    public static final double MINUTOS_ARCO_OPTOTIPO = 5.0;
    /** Milímetros por pulgada (constante exacta). */
    public static final double MM_POR_PULGADA = 25.4;

    /**
     * Fórmula 4: Conversión Estándar Inversa a Ángulo Mínimo de Resolución Visual (MAR).
     */
    public static double calcularMAR(double vDecimal) {
        if (vDecimal <= 0) return 0;
        return 1.0 / vDecimal;
    }

    /**
     * Fórmula 5: Ecuación Trigonométrica para la Altura Física Objetiva (h_mm).
     *
     * <p>h = 2 · d · tan(θ / 2), con θ = 5′ · MAR = MAR · π / 2160 rad.
     * Por eso el argumento de la tangente es MAR · π / 4320.
     * (La versión anterior usaba π / 8640 y producía letras de la mitad del tamaño.)</p>
     *
     * @param d Distancia del paciente a la pantalla en milímetros.
     * @param mar Ángulo Mínimo de Resolución Visual (minutos de arco).
     * @return Altura estricta del optotipo en milímetros.
     */
    public static double calcularAlturaMM(double d, double mar) {
        double mitadAnguloRad = (MINUTOS_ARCO_OPTOTIPO * mar / 2.0) * Math.PI / (180.0 * 60.0);
        return 2 * d * Math.tan(mitadAnguloRad);
    }

    /**
     * Aproximación lineal (ángulo pequeño) de la Fórmula 5: h ≈ d · MAR · π / 2160.
     * La diferencia con la versión exacta es menor a 0.001 % en el rango clínico.
     */
    public static double calcularAlturaMMLineal(double d, double mar) {
        return (d * mar * Math.PI) / 2160.0;
    }

    /**
     * Calcula la densidad de píxeles del monitor (PPI) a partir de la resolución
     * FÍSICA y la diagonal en pulgadas.
     */
    public static double calcularPPI(double resX, double resY, double pulgadasDiagonal) {
        if (pulgadasDiagonal <= 0) return 0;
        return Math.sqrt(Math.pow(resX, 2) + Math.pow(resY, 2)) / pulgadasDiagonal;
    }

    /**
     * PPI medido directamente: un objeto de tamaño físico conocido ocupa cierto
     * número de píxeles físicos en pantalla (p. ej. una tarjeta bancaria ISO/IEC 7810
     * ID-1 mide 85.60 mm de ancho).
     */
    public static double calcularPPIPorObjeto(double pixelesFisicos, double milimetrosReales) {
        if (pixelesFisicos <= 0 || milimetrosReales <= 0) return 0;
        return pixelesFisicos / (milimetrosReales / MM_POR_PULGADA);
    }

    /**
     * Fórmula 6: Transformación de milímetros a píxeles FÍSICOS.
     */
    public static double calcularAlturaPixel(double hMm, double ppi) {
        return (hMm / MM_POR_PULGADA) * ppi;
    }

    /**
     * Fórmula 6-b: tamaño de fuente (em) que produce la altura de mayúscula requerida,
     * corrigiendo por la relación cap-height/em de la tipografía (ver {@link FuentesOptotipo}).
     * El resultado está en píxeles físicos; para JavaFX use
     * {@link #calcularTamanioFuenteLogico(double, double, double, double)}.
     */
    public static double calcularTamanioFuenteAjustado(double hMm, double ppi, double factorCorreccionFuente) {
        double alturaPixelRequerida = calcularAlturaPixel(hMm, ppi);
        if (factorCorreccionFuente <= 0) factorCorreccionFuente = 1.0;
        return alturaPixelRequerida / factorCorreccionFuente;
    }

    /**
     * Igual que la fórmula 6-b, pero expresado en píxeles LÓGICOS de JavaFX.
     * Windows aplica un escalado (125 %, 150 %…): JavaFX multiplica cada "px" de CSS
     * por ese factor. Si no se divide, las letras salen ampliadas en esa proporción.
     *
     * @param escalaSalida Factor de escalado de la pantalla (Screen.getOutputScaleY()), 1.0 si no hay.
     */
    public static double calcularTamanioFuenteLogico(double hMm, double ppiFisico, double factorCorreccionFuente, double escalaSalida) {
        double escala = escalaSalida > 0 ? escalaSalida : 1.0;
        return calcularTamanioFuenteAjustado(hMm, ppiFisico, factorCorreccionFuente) / escala;
    }

    /**
     * Altura mínima (en píxeles FÍSICOS) para que un optotipo conserve su forma.
     * Un optotipo mide 5 trazos de alto; por debajo de 5 px cada trazo ocupa menos
     * de un píxel y letras como C, O y D se vuelven indistinguibles: la fila deja de
     * medir agudeza y pasa a medir la resolución del monitor.
     */
    public static final double ALTURA_MINIMA_PX_FISICOS = 5.0;

    /** Altura en píxeles físicos de un optotipo de LogMAR dado a la distancia dada (mm). */
    public static double alturaPxFisicos(double distanciaMm, double logMar, double ppiFisico) {
        double mar = Math.pow(10, logMar);
        return calcularAlturaPixel(calcularAlturaMM(distanciaMm, mar), ppiFisico);
    }

    /** ¿La pantalla puede dibujar ese nivel sin perder la forma del optotipo? */
    public static boolean esNivelRepresentable(double distanciaMm, double logMar, double ppiFisico) {
        return alturaPxFisicos(distanciaMm, logMar, ppiFisico) >= ALTURA_MINIMA_PX_FISICOS - 1e-9;
    }

    /**
     * Distancia mínima (mm) a la que un nivel LogMAR alcanza la altura mínima en
     * píxeles. Sirve para sugerir "aléjese a X m" cuando la pantalla tiene poca densidad.
     */
    public static double distanciaMinimaMmParaLogMar(double logMar, double ppiFisico) {
        if (ppiFisico <= 0) return Double.NaN;
        double alturaMm = ALTURA_MINIMA_PX_FISICOS / ppiFisico * MM_POR_PULGADA;
        double mitadAnguloRad = (MINUTOS_ARCO_OPTOTIPO * Math.pow(10, logMar) / 2.0) * Math.PI / (180.0 * 60.0);
        return alturaMm / (2 * Math.tan(mitadAnguloRad));
    }

    /**
     * Fórmula 7: Codificación Computarizada LogMAR.
     */
    public static double calcularLogMAR(double vDecimal) {
        if (vDecimal <= 0) return 0;
        return -Math.log10(vDecimal);
    }

    /** Inversa de la fórmula 7: agudeza decimal a partir de LogMAR. */
    public static double decimalDesdeLogMAR(double logMar) {
        return Math.pow(10, -logMar);
    }

    /** Denominadores Snellen estándar (pies, numerador 20). */
    private static final int[] DENOMINADORES_SNELLEN = {10, 12, 16, 20, 25, 32, 40, 50, 63, 80, 100, 125, 160, 200, 250, 320, 400};

    /** Convierte LogMAR a la notación "20/XX" redondeando al denominador estándar más cercano (en escala log). */
    public static String snellenDesdeLogMAR(double logMar) {
        double exacto = 20 * Math.pow(10, logMar);
        int mejor = DENOMINADORES_SNELLEN[0];
        double menor = Double.MAX_VALUE;
        for (int d : DENOMINADORES_SNELLEN) {
            double dif = Math.abs(Math.log10(d) - Math.log10(exacto));
            if (dif < menor) { menor = dif; mejor = d; }
        }
        return "20/" + mejor;
    }

    /** Equivalente métrico "6/XX" redondeado a un decimal útil. */
    public static String snellenMetricoDesdeLogMAR(double logMar) {
        double denominador = 6 * Math.pow(10, logMar);
        return denominador >= 10 ? String.format(java.util.Locale.US, "6/%.0f", denominador)
                : String.format(java.util.Locale.US, "6/%.1f", denominador);
    }

    /**
     * Fórmula 8: Puntuación letra por letra (criterio ETDRS).
     *
     * <p>LogMAR = LogMAR_fila_superior + 0.1 − (0.1 / letrasPorFila) · aciertosTotales,
     * donde aciertosTotales cuenta todas las letras leídas desde la fila superior
     * (incluidas las filas superiores que se dieron por leídas).</p>
     *
     * @param logMarFilaSuperior LogMAR de la fila más grande considerada.
     * @param letrasPorFila Optotipos por fila (5 en ETDRS, 3 en EYESOFTER).
     * @param aciertosTotales Letras correctas acumuladas desde esa fila.
     */
    public static double calcularPuntuacionETDRS(double logMarFilaSuperior, int letrasPorFila, int aciertosTotales) {
        if (letrasPorFila <= 0) letrasPorFila = 5;
        return logMarFilaSuperior + 0.1 - (0.1 / letrasPorFila) * Math.max(0, aciertosTotales);
    }

    /**
     * Compatibilidad con la versión anterior (5 letras por fila: 0.02 por letra).
     */
    public static double calcularPuntuacionETDRS(double logMarInicial, int nAciertos) {
        return logMarInicial - (nAciertos * 0.02);
    }
}
