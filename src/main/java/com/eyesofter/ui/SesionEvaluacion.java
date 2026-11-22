package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.clinica.CondicionesPrueba;
import com.eyesofter.clinica.ResultadoClinico;
import com.eyesofter.formulas.Antropometria;
import com.eyesofter.medicion_snellen.MotorSnellen;
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
    private static String folioEnvio;
    private static String fechaNacimiento = "";
    private static double edadAnios = Double.NaN;
    /** DP escrita por el operador (regla o pupilómetro); NaN si no se capturó. */
    private static double dpCapturadaMm = Double.NaN;
    /** DP medida en esta sesión con la cámara calibrada y una distancia medida con cinta. */
    private static double dpMedidaCintaMm = Double.NaN;
    /** La cámara se calibró con cinta en esta sesión con este mismo paciente. */
    private static boolean distanciaVerificadaConCinta;
    private static MotorSnellen.TipoOptotipo tipoOptotipo = MotorSnellen.TipoOptotipo.LETRAS;
    private static boolean brilloConfirmado;
    private static Boolean pantallaCoincide;
    private static CondicionesPrueba.Correccion correccion = CondicionesPrueba.Correccion.NO_REGISTRADO;

    public static void registrarFolioEnvio(String folio) { folioEnvio = folio; }
    public static String getFolioEnvio() { return folioEnvio; }

    private SesionEvaluacion() { }

    public static void iniciar(String curp, String nombre, String grado) {
        iniciar(curp, nombre, grado, null);
    }

    /**
     * Inicia la sesión. La edad se toma de la fecha de nacimiento o, si falta, de la
     * CURP: con ella se estima la DP (para la cámara), se sugiere el optotipo y se
     * eligen los criterios de referencia.
     */
    public static void iniciar(String curp, String nombre, String grado, String fechaNacimientoIso) {
        fechaNacimiento = valor(fechaNacimientoIso);
        edadAnios = Antropometria.edadPaciente(fechaNacimiento, curp, java.time.LocalDate.now());
        dpCapturadaMm = Double.NaN;
        dpMedidaCintaMm = Double.NaN;
        distanciaVerificadaConCinta = false;
        brilloConfirmado = false;
        pantallaCoincide = null;
        correccion = CondicionesPrueba.Correccion.NO_REGISTRADO;
        logMarFilaInicial = Double.NaN;
        tipoOptotipo = sugerirOptotipo(edadAnios);
        curpPaciente = valor(curp);
        nombrePaciente = valor(nombre);
        gradoGrupo = valor(grado);
        resultadoSnellen = "Pendiente";
        resultadoIshihara = "Pendiente";
        recomendacion = "Pendiente de completar el tamizaje.";
        resultadoClinico = new ResultadoClinico();
        resultadoClinico.registrarEdad(edadAnios);
        faseActual = FaseEvaluacion.AGUDEZA_AMBOS;
        tiemposRespuestaMs.clear();
        entradaManual = false;
        entradaVoz = false;
        activa = true;
        folioEnvio = null;
        ControladorSnellen.reiniciarIntroduccion();
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

    public static double getEdadAnios() { return edadAnios; }

    /** Edad escrita por el operador cuando no hay fecha de nacimiento ni CURP válida. */
    public static void registrarEdadCapturada(double anios) {
        if (!Double.isFinite(anios) || anios < 0 || anios > 120) return;
        edadAnios = anios;
        resultadoClinico.registrarEdad(anios);
        tipoOptotipo = sugerirOptotipo(anios);
    }
    public static String getFechaNacimiento() { return fechaNacimiento; }

    /** Menores de 6 años: E direccional (muchos aún no reconocen las letras). */
    public static MotorSnellen.TipoOptotipo sugerirOptotipo(double edad) {
        return Double.isFinite(edad) && edad < ResultadoClinico.EDAD_MAXIMA_SUGERIR_E
                ? MotorSnellen.TipoOptotipo.E_DIRECCIONAL : MotorSnellen.TipoOptotipo.LETRAS;
    }

    public static MotorSnellen.TipoOptotipo getTipoOptotipo() { return tipoOptotipo; }
    public static void configurarTipoOptotipo(MotorSnellen.TipoOptotipo tipo) {
        tipoOptotipo = tipo == null ? MotorSnellen.TipoOptotipo.LETRAS : tipo;
    }

    public static void confirmarBrillo(boolean confirmado) { brilloConfirmado = confirmado; }
    public static void registrarCorreccion(CondicionesPrueba.Correccion c) {
        correccion = c == null ? CondicionesPrueba.Correccion.NO_REGISTRADO : c;
    }
    public static CondicionesPrueba.Correccion getCorreccion() { return correccion; }
    private static double logMarFilaInicial = Double.NaN;
    public static void registrarFilaInicial(double logMar) { logMarFilaInicial = logMar; }
    public static boolean isBrilloConfirmado() { return brilloConfirmado; }
    public static void registrarCoincidenciaPantalla(Boolean coincide) { pantallaCoincide = coincide; }

    /** DP capturada por el operador; se ignora si no es plausible. */
    public static void capturarDp(double mm) {
        dpCapturadaMm = Antropometria.esDpPlausible(mm) ? mm : Double.NaN;
        aplicarDpAlDetector();
    }

    public static void registrarDpMedidaConCinta(double mm) {
        if (Antropometria.esDpPlausible(mm)) dpMedidaCintaMm = mm;
        distanciaVerificadaConCinta = true;
        aplicarDpAlDetector();
    }

    /** Se calibró la cámara con este paciente: la distancia de la sesión ya es exacta para él. */
    public static void registrarCalibracionConPaciente() { distanciaVerificadaConCinta = true; }
    public static boolean isDistanciaVerificadaConCinta() { return distanciaVerificadaConCinta; }

    /** Mejor DP disponible: medida con cinta > capturada > tabla por edad > adulto promedio. */
    public static double getDpPaciente() {
        if (Double.isFinite(dpMedidaCintaMm)) return dpMedidaCintaMm;
        if (Double.isFinite(dpCapturadaMm)) return dpCapturadaMm;
        return Antropometria.dpEsperadaMm(edadAnios);
    }

    public static CondicionesPrueba.FuenteDp getFuenteDp() {
        if (Double.isFinite(dpMedidaCintaMm)) return CondicionesPrueba.FuenteDp.MEDIDA_CON_CINTA;
        if (Double.isFinite(dpCapturadaMm)) return CondicionesPrueba.FuenteDp.CAPTURADA;
        return Double.isFinite(edadAnios) ? CondicionesPrueba.FuenteDp.POR_EDAD : CondicionesPrueba.FuenteDp.PROMEDIO_ADULTO;
    }

    private static void aplicarDpAlDetector() {
        if (detectorDistancia != null) detectorDistancia.setDpPacienteMm(getDpPaciente());
    }

    /**
     * Reúne las condiciones técnicas de la prueba para guardarlas con el resultado.
     * Llamar al terminar las fases de agudeza (antes de liberar la cámara).
     */
    public static CondicionesPrueba construirCondiciones(double escalaPantalla) {
        CondicionesPrueba c = new CondicionesPrueba();
        c.ppi = ppi;
        try { c.metodoPantalla = CondicionesPrueba.MetodoPantalla.valueOf(AppConfig.METODO_PPI); }
        catch (IllegalArgumentException e) { c.metodoPantalla = CondicionesPrueba.MetodoPantalla.MANUAL; }
        c.escalaPantalla = escalaPantalla > 0 ? escalaPantalla : 1.0;
        c.pantallaCalibradaCoincide = pantallaCoincide;
        c.distanciaObjetivoM = distanciaMetros;
        c.toleranciaCm = com.eyesofter.formulas.CalibracionFocal.toleranciaEfectivaCm(AppConfig.TOLERANCIA_DISTANCIA_CM, distanciaMetros * 100.0);
        c.tipoOptotipo = tipoOptotipo == MotorSnellen.TipoOptotipo.E_DIRECCIONAL ? "E direccional" : "Letras";
        c.logMarLimitePantalla = MotorSnellen.mejorLogMarMedible(distanciaMetros * 1000.0, ppi);
        c.brilloConfirmado = brilloConfirmado;
        c.correccion = correccion;
        c.logMarFilaInicial = logMarFilaInicial;
        c.fuenteOptotipo = AppConfig.FUENTE_OPTOTIPO_DEFECTO;
        c.fuenteInstalada = tipoOptotipo == MotorSnellen.TipoOptotipo.E_DIRECCIONAL
                || com.eyesofter.formulas.FuentesOptotipo.estaInstalada(AppConfig.FUENTE_OPTOTIPO_DEFECTO);
        if (detectorDistancia != null) {
            c.metodoDistancia = detectorDistancia.getMetodo() == DetectorDistancia.Metodo.OJOS
                    ? CondicionesPrueba.MetodoDistancia.CAMARA_OJOS : CondicionesPrueba.MetodoDistancia.CAMARA_ROSTRO;
            c.camaraCalibrada = detectorDistancia.isFocalCalibrada() || distanciaVerificadaConCinta;
            c.fuenteDp = getFuenteDp();
            c.dpUsadaMm = detectorDistancia.getDpPacienteMm();
            c.distanciaRealPromedioM = detectorDistancia.getDistanciaPromedioCm() / 100.0;
            c.distanciaRealDesviacionM = detectorDistancia.getDesviacionDistanciaCm() / 100.0;
        } else {
            c.metodoDistancia = CondicionesPrueba.MetodoDistancia.SIN_CAMARA;
        }
        return c;
    }

    public static void configurarEntorno(double distancia, double densidadPpi) {
        distanciaMetros = distancia;
        ppi = densidadPpi;
    }

    public static void configurarModo(ModoEvaluacion modo) {
        modoEvaluacion = modo == null ? ModoEvaluacion.ASISTIDA : modo;
    }

    public static void conservarDetectorDistancia(DetectorDistancia detector) {
        detectorDistancia = detector;
        aplicarDpAlDetector();
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
