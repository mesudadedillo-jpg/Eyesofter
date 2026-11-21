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

        public static ResultadoOjo desdeMotor(MotorSnellen motor) {
            int indice = motor.getIndiceUltimaFilaCorrecta();
            int aciertos = motor.getAciertosTotales();
            int total = motor.getLetrasEvaluadasTotales();
            if (indice < 0) {
                return new ResultadoOjo(-1, aciertos, total, "<20/200", null, null,
                        MotorSnellen.getNumeroNiveles());
            }

            double decimal = MotorSnellen.getNivelAgudeza(indice);
            int perdidas = Math.max(0, MotorSnellen.getNumeroNiveles() - 1 - indice);
            return new ResultadoOjo(indice, aciertos, total,
                    MotorSnellen.formatoSnellen(decimal),
                    TrigonometriaVisual.calcularLogMAR(decimal), null, perdidas);
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
        if (ojoDerecho != null && ojoIzquierdo != null &&
                Math.abs(ojoDerecho.getLineasPerdidas() - ojoIzquierdo.getLineasPerdidas()) >= UMBRAL_ASIMETRIA_LINEAS) {
            Ojo ojoMasLimitado = ojoDerecho.getLineasPerdidas() > ojoIzquierdo.getLineasPerdidas()
                    ? Ojo.DERECHO : Ojo.IZQUIERDO;
            alertas.add("ALERTA: asimetría de agudeza visual mayor a 2 líneas; valorar " + ojoMasLimitado.etiqueta().toLowerCase(Locale.ROOT) + " y descartar una causa de disminución visual.");
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
        boolean asimetria = ojoDerecho != null && ojoIzquierdo != null
                && Math.abs(ojoDerecho.getLineasPerdidas() - ojoIzquierdo.getLineasPerdidas()) >= UMBRAL_ASIMETRIA_LINEAS;
        if (asimetria || esAgudezaReducida(ojoDerecho) || esAgudezaReducida(ojoIzquierdo)) {
            return PrioridadSeguimiento.PREFERENTE;
        }
        if (ishiharaRespuestasDeficiencia >= UMBRAL_RESPUESTAS_DEFICIENCIA_COLOR) return PrioridadSeguimiento.PROGRAMADA;
        return PrioridadSeguimiento.SIN_BANDERAS;
    }

    private boolean esAgudezaMarcadamenteReducida(ResultadoOjo ojo) {
        return ojo != null && ojo.getIndiceUltimaFilaCorrecta() >= 0 && ojo.getIndiceUltimaFilaCorrecta() <= 1;
    }

    private boolean esAgudezaReducida(ResultadoOjo ojo) {
        return ojo != null && ojo.getIndiceUltimaFilaCorrecta() >= 0 && ojo.getIndiceUltimaFilaCorrecta() <= 4;
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
