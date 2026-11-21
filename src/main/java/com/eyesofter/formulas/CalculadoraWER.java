package com.eyesofter.formulas;

/**
 * Evaluación de Confiabilidad y Umbrales de Seguridad Acústica.
 */
public class CalculadoraWER {

    // Umbral de corte clínico de seguridad (40% de tasa de error permitida)
    public static final double UMBRAL_LIMITE_WER = 0.40;

    /**
     * Fórmula 9: Tasa de Error de Palabras (Word Error Rate).
     * @param sustituciones (S) Palabras reconocidas erróneamente.
     * @param deleciones (D) Palabras omitidas por el paciente.
     * @param inserciones (I) Palabras o ruidos extra capturados (ej. muletillas ajenas).
     * @param n (N) Número total de palabras esperadas (En Snellen/Ishihara suele ser 1).
     * @return Índice de saturación ambiental.
     */
    public static double calcularWER(int sustituciones, int deleciones, int inserciones, int n) {
        if (n <= 0) return 1.0; // Evitar división por cero marcando error total
        return (double) (sustituciones + deleciones + inserciones) / n;
    }

    /**
     * Validador booleano para los controladores de flujo.
     * Informa de manera inminente si la saturación atenta contra el proceso auditivo.
     * @param wer Índice calculado.
     * @return true si el ambiente acústico es inestable y debe pausarse la prueba.
     */
    public static boolean esAmbienteInestable(double wer) {
        return wer > UMBRAL_LIMITE_WER;
    }

    /**
     * Método sobrecargado especializado para la validación rápida de inserciones.
     * Ideal para procesar la cadena cruda que emite Vosk con etiquetas "[unk]".
     */
    public static double calcularWERPorInserciones(int insercionesDetectadas) {
        // En EYESOFTER evaluamos de 1 en 1 grafía (N=1), por lo que las inserciones
        // son el principal factor de alteración de la tasa WER.
        int nEsperado = 1;
        return calcularWER(0, 0, insercionesDetectadas, nEsperado);
    }
}