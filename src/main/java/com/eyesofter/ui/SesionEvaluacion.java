package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.clinica.ResultadoClinico;
import com.eyesofter.voz.VozGuia;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado mínimo de una sesión de tamizaje. Centralizarlo evita trasladar datos
 * clínicos por controles visuales o cadenas de texto entre pantallas.
 */
public final class SesionEvaluacion {
    public enum ModoEvaluacion {
        ASISTIDA("Evaluación asistida"),
        AUTONOMA("Evaluación autónoma");

        private final String etiqueta;

        ModoEvaluacion(String etiqueta) { this.etiqueta = etiqueta; }

        @Override public String toString() { return etiqueta; }
    }
    public enum FaseEvaluacion {
        AGUDEZA_AMBOS("Agudeza visual", ResultadoClinico.Ojo.AMBOS),
        AGUDEZA_DERECHO("Agudeza visual", ResultadoClinico.Ojo.DERECHO),
        AGUDEZA_IZQUIERDO("Agudeza visual", ResultadoClinico.Ojo.IZQUIERDO),
        COLOR_AMBOS("Visión de colores", ResultadoClinico.Ojo.AMBOS);
        private final String prueba;
        private final ResultadoClinico.Ojo ojo;
        FaseEvaluacion(String prueba, ResultadoClinico.Ojo ojo) { this.prueba = prueba; this.ojo = ojo; }
        public String prueba() { return prueba; }
        public ResultadoClinico.Ojo ojo() { return ojo; }
        public boolean esAgudeza() { return this != COLOR_AMBOS; }
    }
    private static String curpPaciente = "";
    private static String nombrePaciente = "";
    private static String gradoGrupo = "";
    private static double distanciaMetros = AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO;
    private static double ppi = AppConfig.PPI_ESTANDAR_MONITOR;
    private static String resultadoSnellen = "Pendiente";
    private static String resultadoIshihara = "Pendiente";
    private static String recomendacion = "Pendiente de completar el tamizaje.";
    private static ModoEvaluacion modoEvaluacion = ModoEvaluacion.ASISTIDA;
    private static DetectorDistancia detectorDistancia;
    private static ResultadoClinico resultadoClinico = new ResultadoClinico();
    private static FaseEvaluacion faseActual = FaseEvaluacion.AGUDEZA_AMBOS;
    private static final List<Long> tiemposRespuestaMs = new ArrayList<>();
    private static boolean entradaManual;
    private static boolean entradaVoz;
    private static boolean activa;

    private SesionEvaluacion() { }

    public static void iniciar(String curp, String nombre, String grado) {
        curpPaciente = valor(curp);
        nombrePaciente = valor(nombre);
        gradoGrupo = valor(grado);
        resultadoSnellen = "Pendiente";
        resultadoIshihara = "Pendiente";
        recomendacion = "Pendiente de completar el tamizaje.";
        resultadoClinico = new ResultadoClinico();
        faseActual = FaseEvaluacion.AGUDEZA_AMBOS;
        tiemposRespuestaMs.clear();
        entradaManual = false;
        entradaVoz = false;
        activa = true;
    }

    public static String getCurpPaciente() { return curpPaciente; }
    public static String getNombrePaciente() { return nombrePaciente; }
    public static String getGradoGrupo() { return gradoGrupo; }
    public static double getDistanciaMetros() { return distanciaMetros; }
    public static double getPpi() { return ppi; }
    public static String getResultadoSnellen() { return resultadoSnellen; }
    public static String getResultadoIshihara() { return resultadoIshihara; }
    public static String getRecomendacion() { return recomendacion; }
    public static ModoEvaluacion getModoEvaluacion() { return modoEvaluacion; }
    public static DetectorDistancia getDetectorDistancia() { return detectorDistancia; }
    public static ResultadoClinico getResultadoClinico() { return resultadoClinico; }
    public static FaseEvaluacion getFaseActual() { return faseActual; }
    public static boolean estaActiva() { return activa; }
    public static void prepararFase(FaseEvaluacion fase) { faseActual = fase == null ? FaseEvaluacion.AGUDEZA_AMBOS : fase; }

    public static void configurarEntorno(double distancia, double densidadPpi) {
        distanciaMetros = distancia;
        ppi = densidadPpi;
    }

    public static void configurarModo(ModoEvaluacion modo) {
        modoEvaluacion = modo == null ? ModoEvaluacion.ASISTIDA : modo;
    }

    public static void conservarDetectorDistancia(DetectorDistancia detector) {
        detectorDistancia = detector;
    }

    public static void liberarDetectorDistancia() {
        if (detectorDistancia != null) detectorDistancia.apagarCamara();
        detectorDistancia = null;
    }

    public static void registrarSnellen(ResultadoClinico.Ojo ojo, ResultadoClinico.ResultadoOjo resultado) {
        resultadoClinico.registrarOjo(ojo, resultado);
        ResultadoClinico.ResultadoOjo od = resultadoClinico.getOjoDerecho();
        ResultadoClinico.ResultadoOjo oi = resultadoClinico.getOjoIzquierdo();
        resultadoSnellen = "OD: " + (od == null ? "Pendiente" : od.resumen()) +
                " | OI: " + (oi == null ? "Pendiente" : oi.resumen());
    }

    public static void registrarIshihara(int aciertos, int total, int respuestasDeficiencia) {
        resultadoClinico.registrarIshihara(aciertos, total, respuestasDeficiencia);
        resultadoIshihara = resultadoClinico.getIshiharaFormateado() + " respuestas concordantes"
                + (respuestasDeficiencia > 0 ? " · " + respuestasDeficiencia + " patrón(es) no concordante(s)" : "");
        actualizarConclusiones();
    }

    /** Conserva una muestra de fiabilidad calculada durante Snellen. */
    public static void registrarFiabilidad(double cumplimientoDistancia, int muestras, long promedioRespuestaMs) {
        resultadoClinico.registrarFiabilidad(cumplimientoDistancia, muestras, promedioRespuestaMs);
    }

    public static void registrarDistanciaPupilar(double mm, boolean calibrada) {
        resultadoClinico.registrarDistanciaPupilar(mm, calibrada);
    }
    public static void registrarTiempoRespuesta(long ms) { if (ms >= 0) tiemposRespuestaMs.add(ms); }
    public static long getPromedioRespuestaMs() { return tiemposRespuestaMs.isEmpty() ? -1 : Math.round(tiemposRespuestaMs.stream().mapToLong(Long::longValue).average().orElse(-1)); }
    public static void registrarEntradaManual() { entradaManual = true; }
    public static void registrarEntradaVoz() { entradaVoz = true; }
    public static String getMetodoEntrada() {
        if (entradaManual && entradaVoz) return "Mixto (manual y voz)";
        if (entradaVoz) return "Reconocimiento de voz";
        if (entradaManual) return "Registro manual";
        return modoEvaluacion == ModoEvaluacion.AUTONOMA ? "Reconocimiento de voz (sin respuesta válida)" : "No registrado";
    }
    public static void registrarCondicionesDistancia(double objetivoM, double toleranciaCm) { resultadoClinico.registrarCondicionesDistancia(objetivoM, toleranciaCm); }

    public static void actualizarConclusiones() {
        resultadoClinico.registrarMetodoEntrada(getMetodoEntrada());
        recomendacion = resultadoClinico.getRecomendacion();
    }

    /** Cierra la sesión activa sin modificar los resultados ya guardados en la base local. */
    public static void cerrar() {
        activa = false;
        VozGuia.cancelarPendientes();
        curpPaciente = "";
        nombrePaciente = "";
        gradoGrupo = "";
        liberarDetectorDistancia();
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
