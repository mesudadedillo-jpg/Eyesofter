package com.eyesofter.clinica;

import com.eyesofter.formulas.TrigonometriaVisual;
import com.eyesofter.medicion_snellen.MotorSnellen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Resultado clínico estructurado de una sesión de tamizaje.
 *
 * <p>Este objeto no formula un diagnóstico. En particular, la estimación por
 * la regla de Egger describe únicamente la magnitud aproximada del desenfoque
 * esférico a partir de líneas perdidas; no determina signo, cilindro, eje ni
 * sustituye una refracción realizada por personal de salud visual.</p>
 */
public final class ResultadoClinico {
    public static final double MINIMO_CUMPLIMIENTO_DISTANCIA_PCT = 90.0;
    public static final int UMBRAL_ASIMETRIA_LINEAS = 2;
    public static final int UMBRAL_RESPUESTAS_DEFICIENCIA_COLOR = 2;
    public static final double LOGMAR_MARCADAMENTE_REDUCIDA = 0.8;
    public static final double LOGMAR_REDUCIDA = 0.3;

    /**
     * Línea que un niño debe leer para pasar el tamizaje según su edad (criterios de
     * referencia pediátrica AAPOS/AAP 2016: 3 años → 20/50, 4 años → 20/40,
     * 5 años o más → 20/32). Son configurables: un oftalmólogo puede ajustarlos.
     * Antes se usaba 20/40 para todas las edades: a los 3 años eso enviaba a
     * consulta a niños con visión normal para su edad, y de los 5 años en adelante
     * dejaba pasar a niños que no leían la línea 20/32.
     */
    public static final double LINEA_APROBATORIA_3_ANIOS = 0.4;
    public static final double LINEA_APROBATORIA_4_ANIOS = 0.3;
    public static final double LINEA_APROBATORIA_5_A_17_ANIOS = 0.2;
    /** Por debajo de esta edad los optotipos no son confiables (se sugiere fotocribado o valoración). */
    public static final double EDAD_MINIMA_OPTOTIPOS = 3.0;
    /** Edad hasta la que se sugiere la E direccional en lugar de letras. */
    public static final double EDAD_MAXIMA_SUGERIR_E = 6.0;

    /** Nivel de seguimiento para orientar al usuario; no equivale a diagnóstico ni a una urgencia médica. */
    public enum PrioridadSeguimiento { SIN_BANDERAS, NO_CONCLUYENTE, PROGRAMADA, PREFERENTE, PRIORITARIA }

    /**
     * LogMAR de la línea aprobatoria para la edad, o NaN si no aplica un criterio
     * pediátrico (adultos, edad desconocida o menores de 3 años).
     */
    public static double lineaAprobatoriaParaEdad(double edadAnios) {
        if (!Double.isFinite(edadAnios) || edadAnios < EDAD_MINIMA_OPTOTIPOS || edadAnios >= 18) return Double.NaN;
        if (edadAnios < 4) return LINEA_APROBATORIA_3_ANIOS;
        if (edadAnios < 5) return LINEA_APROBATORIA_4_ANIOS;
        return LINEA_APROBATORIA_5_A_17_ANIOS;
    }

    public enum Ojo {
        AMBOS("Ambos ojos", "AO"),
        DERECHO("Ojo derecho", "OD"),
        IZQUIERDO("Ojo izquierdo", "OI");

        private final String etiqueta;
        private final String abreviatura;

        Ojo(String etiqueta, String abreviatura) {
            this.etiqueta = etiqueta;
            this.abreviatura = abreviatura;
        }

        public String etiqueta() { return etiqueta; }
        public String abreviatura() { return abreviatura; }
    }

    /** Resultado de un ojo con equivalencias clínicas derivadas de Snellen. */
    public static final class ResultadoOjo {
        private final int indiceUltimaFilaCorrecta;
        private final int aciertos;
        private final int letrasEvaluadas;
        private final String snellen;
        private final Double logMar;
        private final Double dioptriasEgger;
        private final int lineasPerdidas;
        /** LogMAR de la fila más pequeña leída (criterio de línea); NaN si no leyó 20/200. */
        private final double logMarFilaLeida;
        private final boolean limitadoPorPantalla;

        private ResultadoOjo(int indiceUltimaFilaCorrecta, int aciertos, int letrasEvaluadas,
                             String snellen, Double logMar, Double dioptriasEgger, int lineasPerdidas,
                             double logMarFilaLeida, boolean limitadoPorPantalla) {
            this.indiceUltimaFilaCorrecta = indiceUltimaFilaCorrecta;
            this.aciertos = aciertos;
            this.letrasEvaluadas = letrasEvaluadas;
            this.snellen = snellen;
            this.logMar = logMar;
            this.dioptriasEgger = dioptriasEgger;
            this.lineasPerdidas = lineasPerdidas;
            this.logMarFilaLeida = logMarFilaLeida;
            this.limitadoPorPantalla = limitadoPorPantalla;
        }

        /**
         * Construye el resultado con la puntuación letra por letra del motor.
         * Las "líneas perdidas" se miden respecto a 20/20 (LogMAR 0.0) en pasos de 0.1.
         */
        public static ResultadoOjo desdeMotor(MotorSnellen motor) {
            int indice = motor.getIndiceUltimaFilaCorrecta();
            int aciertos = motor.getAciertosTotales();
            int total = motor.getLetrasEvaluadasTotales();
            double logMar = motor.getLogMarPuntuado();
            if (!Double.isFinite(logMar)) {
                return new ResultadoOjo(-1, aciertos, total, "N/D", null, null, MotorSnellen.getNumeroNiveles(), Double.NaN, false);
            }
            int perdidas = Math.max(0, (int) Math.round(logMar / 0.1));
            // Si la fila 20/200 no cupo en la pantalla, "no leyó la primera fila" es "<" esa fila, no "<20/200".
            String snellen = indice < 0 ? "<" + TrigonometriaVisual.snellenDesdeLogMAR(motor.getLogMarFilaInicial())
                    : TrigonometriaVisual.snellenDesdeLogMAR(logMar);
            return new ResultadoOjo(indice, aciertos, total, snellen, logMar, null, perdidas,
                    indice < 0 ? Double.NaN : MotorSnellen.getNivelLogMar(indice), motor.isLimitadoPorPantalla());
        }

        /** Para pruebas y vistas: resultado a partir de un LogMAR ya calculado. */
        public static ResultadoOjo desdeLogMar(double logMar) {
            return desdeLogMar(logMar, false);
        }

        /** Igual que {@link #desdeLogMar(double)}, indicando si el resultado tocó el límite de la pantalla. */
        public static ResultadoOjo desdeLogMar(double logMar, boolean limitadoPorPantalla) {
            int perdidas = Math.max(0, (int) Math.round(logMar / 0.1));
            int indice = logMar > 1.0 + 1e-9 ? -1 : 0;
            String snellen = indice < 0 ? "<20/200" : TrigonometriaVisual.snellenDesdeLogMAR(logMar);
            double fila = indice < 0 ? Double.NaN : Math.round(logMar * 10.0) / 10.0;
            return new ResultadoOjo(indice, 0, 0, snellen, logMar, null, perdidas, fila, limitadoPorPantalla);
        }

        public int getIndiceUltimaFilaCorrecta() { return indiceUltimaFilaCorrecta; }
        public int getAciertos() { return aciertos; }
        public int getLetrasEvaluadas() { return letrasEvaluadas; }
        public String getSnellen() { return snellen; }
        public Double getLogMar() { return logMar; }
        public Double getDioptriasEgger() { return dioptriasEgger; }
        public int getLineasPerdidas() { return lineasPerdidas; }
        public double getLogMarFilaLeida() { return logMarFilaLeida; }
        /** La persona leyó la fila más pequeña que la pantalla podía dibujar: su agudeza real puede ser mejor. */
        public boolean isLimitadoPorPantalla() { return limitadoPorPantalla; }

        public String getLogMarFormateado() {
            return logMar == null ? "N/D" : String.format(Locale.US, "%.2f", logMar);
        }

        public String getDioptriasEggerFormateadas() {
            return dioptriasEgger == null ? "N/D" : String.format(Locale.US, "%.2f D", dioptriasEgger);
        }

        public String resumen() {
            return snellen + (limitadoPorPantalla ? " o mejor (límite de la pantalla)" : "") + " | LogMAR " + getLogMarFormateado();
        }

        /** Agudeza decimal equivalente (NaN si no hay LogMAR). */
        public double getDecimal() {
            return logMar == null ? Double.NaN : TrigonometriaVisual.decimalDesdeLogMAR(logMar);
        }
    }

    private ResultadoOjo ojoDerecho;
    private ResultadoOjo ojoIzquierdo;
    private ResultadoOjo ambosOjos;
    private int ishiharaAciertos;
    private int ishiharaTotal;
    private int ishiharaRespuestasDeficiencia;
    private double cumplimientoDistanciaPct = Double.NaN;
    private int muestrasDistancia;
    private long tiempoRespuestaPromedioMs = -1;
    private double distanciaPupilarMm = Double.NaN;
    private boolean distanciaPupilarCalibrada;
    private double distanciaObjetivoMetros = Double.NaN;
    private double toleranciaDistanciaCm = Double.NaN;
    private String metodoEntrada = "No registrado";
    private double edadAnios = Double.NaN;
    private CondicionesPrueba condiciones;

    public void registrarOjo(Ojo ojo, ResultadoOjo resultado) {
        if (ojo == Ojo.DERECHO) ojoDerecho = resultado;
        else if (ojo == Ojo.IZQUIERDO) ojoIzquierdo = resultado;
        else ambosOjos = resultado;
    }

    public void registrarIshihara(int aciertos, int total, int respuestasDeficiencia) {
        ishiharaAciertos = Math.max(0, aciertos);
        ishiharaTotal = Math.max(0, total);
        ishiharaRespuestasDeficiencia = Math.max(0, respuestasDeficiencia);
    }

    public void registrarFiabilidad(double cumplimientoPct, int muestras, long promedioMs) {
        cumplimientoDistanciaPct = cumplimientoPct;
        muestrasDistancia = Math.max(0, muestras);
        tiempoRespuestaPromedioMs = promedioMs;
    }

    public void registrarDistanciaPupilar(double milimetros, boolean calibrada) {
        distanciaPupilarMm = milimetros;
        distanciaPupilarCalibrada = calibrada;
    }

    public void registrarCondicionesDistancia(double objetivoMetros, double toleranciaCm) {
        distanciaObjetivoMetros = objetivoMetros;
        toleranciaDistanciaCm = toleranciaCm;
    }

    /** Edad en años decimales (NaN si se desconoce). Ajusta los criterios de referencia. */
    public void registrarEdad(double anios) { edadAnios = anios; }
    public double getEdadAnios() { return edadAnios; }

    public void registrarCondiciones(CondicionesPrueba c) { condiciones = c; }
    public CondicionesPrueba getCondiciones() { return condiciones; }

    public String getEdadFormateada() {
        if (!Double.isFinite(edadAnios)) return "N/D";
        int anios = (int) Math.floor(edadAnios);
        int meses = (int) Math.floor((edadAnios - anios) * 12);
        return anios < 6 && meses > 0 ? anios + " años " + meses + " meses" : anios + " años";
    }

    public void registrarMetodoEntrada(String metodo) {
        metodoEntrada = metodo == null || metodo.isBlank() ? "No registrado" : metodo;
    }

    public ResultadoOjo getOjoDerecho() { return ojoDerecho; }
    public ResultadoOjo getOjoIzquierdo() { return ojoIzquierdo; }
    public ResultadoOjo getAmbosOjos() { return ambosOjos; }
    public int getIshiharaAciertos() { return ishiharaAciertos; }
    public int getIshiharaTotal() { return ishiharaTotal; }
    public int getIshiharaRespuestasDeficiencia() { return ishiharaRespuestasDeficiencia; }
    public double getCumplimientoDistanciaPct() { return cumplimientoDistanciaPct; }
    public int getMuestrasDistancia() { return muestrasDistancia; }
    public long getTiempoRespuestaPromedioMs() { return tiempoRespuestaPromedioMs; }
    public double getDistanciaPupilarMm() { return distanciaPupilarMm; }
    public boolean isDistanciaPupilarCalibrada() { return distanciaPupilarCalibrada; }
    public double getDistanciaObjetivoMetros() { return distanciaObjetivoMetros; }
    public double getToleranciaDistanciaCm() { return toleranciaDistanciaCm; }
    public String getMetodoEntrada() { return metodoEntrada; }

    public String getIshiharaFormateado() {
        return ishiharaTotal <= 0 ? "N/D" : ishiharaAciertos + "/" + ishiharaTotal;
    }

    public String getCumplimientoDistanciaFormateado() {
        return Double.isFinite(cumplimientoDistanciaPct)
                ? String.format(Locale.US, "%.0f%%", cumplimientoDistanciaPct) : "N/D";
    }

    public String getTiempoRespuestaFormateado() {
        return tiempoRespuestaPromedioMs < 0 ? "N/D"
                : String.format(Locale.US, "%.2f s", tiempoRespuestaPromedioMs / 1000.0);
    }

    public String getDistanciaPupilarFormateada() {
        return distanciaPupilarCalibrada && Double.isFinite(distanciaPupilarMm)
                ? String.format(Locale.US, "%.1f mm", distanciaPupilarMm) : "N/D";
    }

    /** Una evaluación clínica solo se persiste cuando todas las fases requeridas concluyeron. */
    public boolean estaCompleto() {
        return ojoDerecho != null && ojoIzquierdo != null && ambosOjos != null && ishiharaTotal > 0;
    }

    public String getFasesPendientes() {
        List<String> pendientes = new ArrayList<>();
        if (ambosOjos == null) pendientes.add("agudeza binocular");
        if (ojoDerecho == null) pendientes.add("ojo derecho");
        if (ojoIzquierdo == null) pendientes.add("ojo izquierdo");
        if (ishiharaTotal <= 0) pendientes.add("tamiz cromático");
        return String.join(", ", pendientes);
    }

    public List<String> generarAlertas() {
        List<String> alertas = new ArrayList<>();
        if (hayAsimetria()) {
            Ojo ojoMasLimitado = ojoDerecho.getLogMar() > ojoIzquierdo.getLogMar()
                    ? Ojo.DERECHO : Ojo.IZQUIERDO;
            alertas.add("ALERTA: diferencia entre ojos de 2 líneas o más; valorar " + ojoMasLimitado.etiqueta().toLowerCase(Locale.ROOT) + " y descartar una causa de disminución visual.");
        }
        if (ishiharaRespuestasDeficiencia >= UMBRAL_RESPUESTAS_DEFICIENCIA_COLOR) {
            alertas.add("ALERTA: tamiz de color positivo; respuestas compatibles con deficiencia de color. Requiere confirmación con evaluación optométrica.");
        }
        if (Double.isFinite(cumplimientoDistanciaPct) && cumplimientoDistanciaPct < MINIMO_CUMPLIMIENTO_DISTANCIA_PCT) {
            alertas.add("Fiabilidad limitada: cumplimiento de distancia menor a " + (int) MINIMO_CUMPLIMIENTO_DISTANCIA_PCT + "%.");
        }
        if (ojoDerecho != null && ojoDerecho.getIndiceUltimaFilaCorrecta() < 0) {
            alertas.add("ALERTA: OD no alcanzó la línea más grande mostrada (" + ojoDerecho.getSnellen().replace("<", "") + "); requiere valoración profesional prioritaria.");
        }
        if (ojoIzquierdo != null && ojoIzquierdo.getIndiceUltimaFilaCorrecta() < 0) {
            alertas.add("ALERTA: OI no alcanzó la línea más grande mostrada (" + ojoIzquierdo.getSnellen().replace("<", "") + "); requiere valoración profesional prioritaria.");
        }
        double linea = lineaAprobatoriaParaEdad(edadAnios);
        if (Double.isFinite(linea)) {
            for (ResultadoOjo ojo : new ResultadoOjo[]{ojoDerecho, ojoIzquierdo}) {
                if (ojo == null || ojo.getLogMar() == null) continue;
                String nombre = ojo == ojoDerecho ? "OD" : "OI";
                if (noPasaLinea(ojo, linea) && !ojo.isLimitadoPorPantalla() && !esAgudezaMarcadamenteReducida(ojo)) {
                    alertas.add("ALERTA: " + nombre + " no leyó la línea " + TrigonometriaVisual.snellenDesdeLogMAR(linea)
                            + ", esperada para " + getEdadFormateada() + "; referir a valoración.");
                }
            }
        }
        if (Double.isFinite(edadAnios) && edadAnios < EDAD_MINIMA_OPTOTIPOS) {
            alertas.add("Menor de 3 años: la agudeza con optotipos no es confiable a esta edad; se sugiere fotocribado o valoración por oftalmología pediátrica.");
        }
        if (Double.isFinite(edadAnios) && edadAnios < EDAD_MAXIMA_SUGERIR_E && condiciones != null
                && !condiciones.tipoOptotipo.toLowerCase(Locale.ROOT).startsWith("e ")) {
            alertas.add("Se usaron letras en un niño menor de 6 años: un resultado bajo puede deberse a que no conoce las letras; repetir con la E direccional.");
        }
        if (lineaNoVerificable()) {
            alertas.add("NO CONCLUYENTE: la pantalla, a esta distancia, no puede dibujar la línea de referencia "
                    + TrigonometriaVisual.snellenDesdeLogMAR(lineaDeReferencia()) + "; repita a mayor distancia o en una pantalla de más resolución.");
        } else {
            for (ResultadoOjo ojo : new ResultadoOjo[]{ojoDerecho, ojoIzquierdo}) {
                if (ojo != null && ojo.isLimitadoPorPantalla())
                    alertas.add((ojo == ojoDerecho ? "OD" : "OI") + " leyó la fila más pequeña que la pantalla puede mostrar; su agudeza real puede ser mejor.");
            }
        }
        if (condiciones != null) alertas.addAll(condiciones.avisos());
        return Collections.unmodifiableList(alertas);
    }

    /** Línea de referencia: la de la edad o, en adultos/edad desconocida, la de agudeza reducida. */
    private double lineaDeReferencia() {
        double linea = lineaAprobatoriaParaEdad(edadAnios);
        return Double.isFinite(linea) ? linea : LOGMAR_REDUCIDA - 0.1;
    }

    /** No leyó la línea aprobatoria (criterio de fila: la mayoría de los optotipos de esa fila). */
    private static boolean noPasaLinea(ResultadoOjo ojo, double linea) {
        double fila = ojo.getLogMarFilaLeida();
        return !Double.isFinite(fila) || fila > linea + 1e-9;
    }

    /**
     * La persona leyó todo lo que la pantalla podía dibujar, pero la pantalla no
     * llega a la línea de referencia: no se puede afirmar que "pasa".
     */
    public boolean lineaNoVerificable() {
        if (condiciones == null || !Double.isFinite(condiciones.logMarLimitePantalla)) return false;
        if (condiciones.logMarLimitePantalla <= lineaDeReferencia() + 1e-9) return false;
        for (ResultadoOjo ojo : new ResultadoOjo[]{ojoDerecho, ojoIzquierdo}) {
            if (ojo != null && ojo.isLimitadoPorPantalla()) return true;
        }
        return false;
    }

    public PrioridadSeguimiento getPrioridadSeguimiento() {
        if (esAgudezaMarcadamenteReducida(ojoDerecho) || esAgudezaMarcadamenteReducida(ojoIzquierdo)) {
            return PrioridadSeguimiento.PRIORITARIA;
        }
        if (hayAsimetria() || esAgudezaReducida(ojoDerecho) || esAgudezaReducida(ojoIzquierdo)) {
            return PrioridadSeguimiento.PREFERENTE;
        }
        if (lineaNoVerificable()) return PrioridadSeguimiento.NO_CONCLUYENTE;
        if (ishiharaRespuestasDeficiencia >= UMBRAL_RESPUESTAS_DEFICIENCIA_COLOR) return PrioridadSeguimiento.PROGRAMADA;
        return PrioridadSeguimiento.SIN_BANDERAS;
    }

    /** LogMAR ≥ 0.8 (20/125 o peor) o no alcanzó 20/200. */
    private boolean esAgudezaMarcadamenteReducida(ResultadoOjo ojo) {
        return ojo != null && ojo.getLogMar() != null && ojo.getLogMar() >= LOGMAR_MARCADAMENTE_REDUCIDA - 1e-9;
    }

    /**
     * Niños de 3 a 17 años: no leyó la línea aprobatoria para su edad.
     * Adultos o edad desconocida: LogMAR ≥ 0.3 (20/40 o peor).
     */
    private boolean esAgudezaReducida(ResultadoOjo ojo) {
        if (ojo == null || ojo.getLogMar() == null) return false;
        // Leyó todo lo que la pantalla podía dibujar: su agudeza es igual o mejor, no se puede afirmar que sea baja.
        if (ojo.isLimitadoPorPantalla()) return false;
        double linea = lineaAprobatoriaParaEdad(edadAnios);
        if (Double.isFinite(linea)) return noPasaLinea(ojo, linea);
        return ojo.getLogMar() >= LOGMAR_REDUCIDA - 1e-9;
    }

    /** Diferencia entre ojos en LogMAR (NaN si falta alguno). */
    public double getDiferenciaInterocularLogMar() {
        if (ojoDerecho == null || ojoIzquierdo == null || ojoDerecho.getLogMar() == null || ojoIzquierdo.getLogMar() == null) return Double.NaN;
        return Math.abs(ojoDerecho.getLogMar() - ojoIzquierdo.getLogMar());
    }

    private boolean hayAsimetria() {
        double dif = getDiferenciaInterocularLogMar();
        return Double.isFinite(dif) && dif >= UMBRAL_ASIMETRIA_LINEAS * 0.1 - 1e-9;
    }

    public String getAlertasComoTexto() {
        List<String> alertas = generarAlertas();
        return alertas.isEmpty() ? "Sin banderas automáticas de referencia inmediata." : String.join(" | ", alertas);
    }

    public String getRecomendacion() {
        return switch (getPrioridadSeguimiento()) {
            case PRIORITARIA -> "Se detectó una agudeza visual marcadamente reducida. Solicite valoración prioritaria con optometría u oftalmología; si la disminución fue súbita o hay dolor, ojo rojo, trauma o pérdida visual reciente, busque atención médica inmediata.";
            case NO_CONCLUYENTE -> "El tamizaje no es concluyente: la persona leyó todo lo que la pantalla podía mostrar a esta distancia, pero la pantalla no alcanza la línea de referencia. Repita la prueba a mayor distancia o en una pantalla de mayor resolución.";
            case PREFERENTE -> "Se identificaron hallazgos de agudeza visual o diferencia entre ambos ojos que ameritan una valoración completa. Programe consulta con un profesional de salud visual en las próximas semanas.";
            case PROGRAMADA -> "El tamiz cromático mostró respuestas no concordantes. Programe una evaluación de visión de color y valoración optométrica; este resultado no identifica por sí solo el tipo ni la causa de una deficiencia.";
            case SIN_BANDERAS -> "No se identificaron banderas automáticas en este tamizaje. Mantenga controles visuales periódicos y consulte antes si aparecen molestias o cambios en la visión.";
        };
    }
}
