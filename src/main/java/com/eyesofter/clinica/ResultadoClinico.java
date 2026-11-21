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

    /** Nivel de seguimiento para orientar al usuario; no equivale a diagnóstico ni a una urgencia médica. */
    public enum PrioridadSeguimiento { SIN_BANDERAS, PROGRAMADA, PREFERENTE, PRIORITARIA }

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

        private ResultadoOjo(int indiceUltimaFilaCorrecta, int aciertos, int letrasEvaluadas,
                             String snellen, Double logMar, Double dioptriasEgger, int lineasPerdidas) {
            this.indiceUltimaFilaCorrecta = indiceUltimaFilaCorrecta;
            this.aciertos = aciertos;
            this.letrasEvaluadas = letrasEvaluadas;
            this.snellen = snellen;
            this.logMar = logMar;
            this.dioptriasEgger = dioptriasEgger;
            this.lineasPerdidas = lineasPerdidas;
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
                return new ResultadoOjo(-1, aciertos, total, "N/D", null, null, MotorSnellen.getNumeroNiveles());
            }
            int perdidas = Math.max(0, (int) Math.round(logMar / 0.1));
            String snellen = indice < 0 ? "<20/200" : TrigonometriaVisual.snellenDesdeLogMAR(logMar);
            return new ResultadoOjo(indice, aciertos, total, snellen, logMar, null, perdidas);
        }

        /** Para pruebas y vistas: resultado a partir de un LogMAR ya calculado. */
        public static ResultadoOjo desdeLogMar(double logMar) {
            int perdidas = Math.max(0, (int) Math.round(logMar / 0.1));
            int indice = logMar > 1.0 + 1e-9 ? -1 : 0;
            String snellen = indice < 0 ? "<20/200" : TrigonometriaVisual.snellenDesdeLogMAR(logMar);
            return new ResultadoOjo(indice, 0, 0, snellen, logMar, null, perdidas);
        }

        public int getIndiceUltimaFilaCorrecta() { return indiceUltimaFilaCorrecta; }
        public int getAciertos() { return aciertos; }
        public int getLetrasEvaluadas() { return letrasEvaluadas; }
        public String getSnellen() { return snellen; }
        public Double getLogMar() { return logMar; }
        public Double getDioptriasEgger() { return dioptriasEgger; }
        public int getLineasPerdidas() { return lineasPerdidas; }

        public String getLogMarFormateado() {
            return logMar == null ? "N/D" : String.format(Locale.US, "%.2f", logMar);
        }

        public String getDioptriasEggerFormateadas() {
            return dioptriasEgger == null ? "N/D" : String.format(Locale.US, "%.2f D", dioptriasEgger);
        }

        public String resumen() {
            return snellen + " | LogMAR " + getLogMarFormateado();
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
            alertas.add("ALERTA: OD no alcanzó la línea 20/200; requiere valoración profesional prioritaria.");
        }
        if (ojoIzquierdo != null && ojoIzquierdo.getIndiceUltimaFilaCorrecta() < 0) {
            alertas.add("ALERTA: OI no alcanzó la línea 20/200; requiere valoración profesional prioritaria.");
        }
        return Collections.unmodifiableList(alertas);
    }

    public PrioridadSeguimiento getPrioridadSeguimiento() {
        if (esAgudezaMarcadamenteReducida(ojoDerecho) || esAgudezaMarcadamenteReducida(ojoIzquierdo)) {
            return PrioridadSeguimiento.PRIORITARIA;
        }
        if (hayAsimetria() || esAgudezaReducida(ojoDerecho) || esAgudezaReducida(ojoIzquierdo)) {
            return PrioridadSeguimiento.PREFERENTE;
        }
        if (ishiharaRespuestasDeficiencia >= UMBRAL_RESPUESTAS_DEFICIENCIA_COLOR) return PrioridadSeguimiento.PROGRAMADA;
        return PrioridadSeguimiento.SIN_BANDERAS;
    }

    /** LogMAR ≥ 0.8 (20/125 o peor) o no alcanzó 20/200. */
    private boolean esAgudezaMarcadamenteReducida(ResultadoOjo ojo) {
        return ojo != null && ojo.getLogMar() != null && ojo.getLogMar() >= LOGMAR_MARCADAMENTE_REDUCIDA - 1e-9;
    }

    /** LogMAR ≥ 0.3 (20/40 o peor): referencia habitual en tamizaje escolar. */
    private boolean esAgudezaReducida(ResultadoOjo ojo) {
        return ojo != null && ojo.getLogMar() != null && ojo.getLogMar() >= LOGMAR_REDUCIDA - 1e-9;
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
            case PREFERENTE -> "Se identificaron hallazgos de agudeza visual o diferencia entre ambos ojos que ameritan una valoración completa. Programe consulta con un profesional de salud visual en las próximas semanas.";
            case PROGRAMADA -> "El tamiz cromático mostró respuestas no concordantes. Programe una evaluación de visión de color y valoración optométrica; este resultado no identifica por sí solo el tipo ni la causa de una deficiencia.";
            case SIN_BANDERAS -> "No se identificaron banderas automáticas en este tamizaje. Mantenga controles visuales periódicos y consulte antes si aparecen molestias o cambios en la visión.";
        };
    }
}
