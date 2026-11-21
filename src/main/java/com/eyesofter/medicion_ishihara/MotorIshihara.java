package com.eyesofter.medicion_ishihara;

import com.eyesofter.voz.ReconocedorOptotipos;

/**
 * Cerebro de la evaluación de Ishihara (Discromatopsias).
 * Controla la secuencia de láminas proyectadas y evalúa la percepción del color.
 */
public class MotorIshihara {
    private ReconocedorOptotipos motorVoz;

    // Representación de una lámina de Ishihara
    public static class Lamina {
        public final String rutaImagen;
        public final String numeroEsperado;
        public final String respuestaDaltonismo; // Lo que ve un paciente con daltonismo

        Lamina(String rutaImagen, String numeroEsperado, String respuestaDaltonismo) {
            this.rutaImagen = rutaImagen;
            this.numeroEsperado = numeroEsperado;
            this.respuestaDaltonismo = respuestaDaltonismo;
        }
    }

    public enum ResultadoRespuesta { ACIERTO, ERROR_DALTONISMO, NADA, TIMEOUT, RUIDO, ERROR_SISTEMA, INDETERMINADO }

    // Banco de láminas con los nombres exactos que configuraste
    private final Lamina[] laminasPrueba = {
            new Lamina("/ishihara/Ishihara_Control_12_VisionNormal.jpg", "12", "12"),
            new Lamina("/ishihara/Ishihara_6_Normal_5_DeficienciaRojoVerde.jpg", "6", "5"),
            new Lamina("/ishihara/Ishihara_42_Normal_2_DeuteranopiaProtanopia.jpg", "42", "2"),
            new Lamina("/ishihara/Ishihara_74_Normal_21_DeficienciaRojoVerde.jpg", "74", "21"),
            new Lamina("/ishihara/Ishihara_Oculta_Normal_SinNumero_Deficiencia_5.jpg", "nada", "5")
    };

    private int indiceActual = -1;
    private int aciertosTotales = 0;
    private int erroresDaltonismo = 0;

    public MotorIshihara(ReconocedorOptotipos motorVoz) {
        this.motorVoz = motorVoz;
    }

    // =====================================================================
    // API interactiva lámina-por-lámina (Usada por JavaFX)
    // =====================================================================

    public void reiniciarInteractivo() {
        indiceActual = -1;
        aciertosTotales = 0;
        erroresDaltonismo = 0;
        if (motorVoz != null) {
            motorVoz.setModo(ReconocedorOptotipos.ModoPrueba.ISHIHARA);
        }
    }

    public boolean haySiguienteLamina() {
        return (indiceActual + 1) < laminasPrueba.length;
    }

    public Lamina avanzarSiguienteLamina() {
        indiceActual++;
        return laminasPrueba[indiceActual];
    }

    public Lamina laminaActual() {
        return (indiceActual >= 0 && indiceActual < laminasPrueba.length) ? laminasPrueba[indiceActual] : null;
    }

    public int getTotalLaminas() {
        return laminasPrueba.length;
    }

    public int getIndiceActual() {
        return indiceActual;
    }

    /** Evalúa la respuesta contra la lógica clínica */
    public ResultadoRespuesta procesarRespuestaInteractiva(String respuestaAcustica) {
        Lamina actual = laminaActual();
        if (actual == null) return ResultadoRespuesta.ERROR_SISTEMA;

        String respuestaLimpia = respuestaAcustica.trim().toLowerCase();

        switch (respuestaLimpia) {
            case "timeout": return ResultadoRespuesta.TIMEOUT;
            case "ruido": return ResultadoRespuesta.RUIDO;
            case "error": return ResultadoRespuesta.ERROR_SISTEMA;
            case ReconocedorOptotipos.RESPUESTA_NADA:
                return ResultadoRespuesta.NADA;
            default:
                if (respuestaLimpia.equals(actual.numeroEsperado.toLowerCase())) {
                    aciertosTotales++;
                    return ResultadoRespuesta.ACIERTO;
                } else if (respuestaLimpia.equals(actual.respuestaDaltonismo.toLowerCase())) {
                    erroresDaltonismo++;
                    return ResultadoRespuesta.ERROR_DALTONISMO;
                }
                return ResultadoRespuesta.INDETERMINADO;
        }
    }

    public int getAciertosTotales() { return aciertosTotales; }
    public int getErroresDaltonismo() { return erroresDaltonismo; }

    public String getDiagnosticoPreliminar() {
        return (erroresDaltonismo >= 2)
                ? "Tamiz cromático con respuestas no concordantes; requiere confirmación profesional."
                : "Tamiz cromático sin patrón automático de alerta; no sustituye una evaluación profesional.";
    }
}
