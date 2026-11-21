package com.eyesofter.medicion_snellen;

import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.formulas.FuentesOptotipo;
import com.eyesofter.formulas.TrigonometriaVisual;
import com.eyesofter.voz.ReconocedorOptotipos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Motor de la prueba de agudeza visual.
 *
 * <h3>Escala</h3>
 * Filas en pasos de 0.1 LogMAR, de 1.0 (20/200) a −0.1 (20/16), con 3 optotipos
 * distintos por fila. El tamaño de cada letra depende de la distancia, del PPI
 * físico del monitor, de la tipografía y del escalado de Windows.
 *
 * <h3>Recorrido (escalera rápida)</h3>
 * <ol>
 *   <li>Cribado: se empieza en 20/200 y, mientras la persona lee las 3 letras
 *       sin error, se salta 3 filas (0.3 LogMAR).</li>
 *   <li>Afinado: al primer error se vuelve a la fila siguiente a la última
 *       perfecta y se baja de una en una.</li>
 *   <li>Termina cuando una fila tiene 0 o 1 aciertos (fila no leída) o no quedan filas.</li>
 * </ol>
 *
 * <h3>Puntuación</h3>
 * Letra por letra (ETDRS): LogMAR = 1.0 + 0.1 − (0.1/3) · aciertos acumulados, contando
 * como leídas las filas saltadas por encima de la última fila perfecta.
 *
 * El recorrido se calcula a partir del historial de filas, por lo que
 * {@link #deshacerUltimaFila()} devuelve exactamente al estado anterior.
 */
public class MotorSnellen {
    private final ReconocedorOptotipos motorVoz;
    private final DetectorDistancia detectorDistancia;
    /** Letras Snellen reconocibles por voz en español. */
    private static final String[] LETRAS_VALIDAS = {"C", "D", "E", "F", "L", "O", "P", "T", "Z"};
    public static final int LETRAS_POR_FILA = 3;
    private static final int SALTO_CRIBADO = 3;
    /** Aciertos mínimos para considerar leída una fila (2 de 3). */
    public static final int ACIERTOS_MINIMOS_FILA = 2;

    /** LogMAR de cada nivel, de mayor (letra grande) a menor. */
    private static final double[] NIVELES_LOGMAR = {1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.0, -0.1};

    private final Random random;

    private double distanciaConfiguradaCm = 100.0;
    private double ppiMonitor = 96.0;
    private String fuenteOptotipo = "Arial";
    private double escalaSalida = 1.0;

    /** Una fila de optotipos ya dimensionados para la interfaz. */
    public static class Fila {
        public final String[] letras;
        public final double vDecimal;
        public final double logMar;
        public final int nivel;
        public final double tamanioPx;

        public Fila(String[] letras, double vDecimal, double tamanioPx) {
            this(letras, vDecimal, TrigonometriaVisual.calcularLogMAR(vDecimal), -1, tamanioPx);
        }

        public Fila(String[] letras, double vDecimal, double logMar, int nivel, double tamanioPx) {
            this.letras = letras;
            this.vDecimal = vDecimal;
            this.logMar = logMar;
            this.nivel = nivel;
            this.tamanioPx = tamanioPx;
        }
    }

    /** Fila ya evaluada (nivel + aciertos). */
    public record FilaEvaluada(int nivel, int aciertos) { }

    private final List<FilaEvaluada> historial = new ArrayList<>();
    private Fila filaPendiente;

    public MotorSnellen(ReconocedorOptotipos motorVoz, DetectorDistancia detectorDistancia) {
        this(motorVoz, detectorDistancia, new Random());
    }

    /** Constructor con semilla controlada (pruebas automáticas). */
    public MotorSnellen(ReconocedorOptotipos motorVoz, DetectorDistancia detectorDistancia, Random random) {
        this.motorVoz = motorVoz;
        this.detectorDistancia = detectorDistancia;
        this.random = random;
    }

    public void configurarEntornoPrueba(double distanciaMetros, double ppiCalculado) {
        configurarEntornoPrueba(distanciaMetros, ppiCalculado, fuenteOptotipo);
    }

    public void configurarEntornoPrueba(double distanciaMetros, double ppiCalculado, String nombreFuenteOptotipo) {
        this.distanciaConfiguradaCm = distanciaMetros * 100.0;
        this.ppiMonitor = ppiCalculado > 0 ? ppiCalculado : 96.0;
        this.fuenteOptotipo = (nombreFuenteOptotipo != null) ? nombreFuenteOptotipo : this.fuenteOptotipo;
        if (this.detectorDistancia != null) {
            this.detectorDistancia.setDistanciaObjetivo(this.distanciaConfiguradaCm);
        }
    }

    /** Escalado de Windows de la pantalla donde se dibuja (1.25 = 125 %). */
    public void setEscalaSalida(double escala) {
        this.escalaSalida = escala > 0 ? escala : 1.0;
    }

    /**
     * Tamaño de fuente en píxeles LÓGICOS de JavaFX para que la altura de la letra
     * subtienda 5′ × MAR a la distancia configurada.
     */
    public double calcularTamanioLetraEnPixeles(double vDecimal) {
        double mar = TrigonometriaVisual.calcularMAR(vDecimal);
        double dMm = distanciaConfiguradaCm * 10.0;
        double hMm = TrigonometriaVisual.calcularAlturaMM(dMm, mar);
        double factorFuente = FuentesOptotipo.factorDeCorreccion(fuenteOptotipo);
        return TrigonometriaVisual.calcularTamanioFuenteLogico(hMm, ppiMonitor, factorFuente, escalaSalida);
    }

    /** Altura física (mm) de la letra de un nivel: útil para verificar con una regla. */
    public double calcularAlturaMm(double vDecimal) {
        return TrigonometriaVisual.calcularAlturaMM(distanciaConfiguradaCm * 10.0, TrigonometriaVisual.calcularMAR(vDecimal));
    }

    public String[][] generarCartillaDinamica(int numFilas, int letrasPorFila) {
        String[][] cartilla = new String[numFilas][];
        for (int i = 0; i < numFilas; i++) cartilla[i] = letrasDistintas(letrasPorFila);
        return cartilla;
    }

    private String[] letrasDistintas(int cantidad) {
        List<String> bolsa = new ArrayList<>(List.of(LETRAS_VALIDAS));
        Collections.shuffle(bolsa, random);
        return bolsa.subList(0, Math.min(cantidad, bolsa.size())).toArray(new String[0]);
    }

    // =====================================================================
    // Recorrido fila por fila
    // =====================================================================

    public void reiniciarInteractivo() {
        historial.clear();
        filaPendiente = null;
    }

    /** Nivel que corresponde evaluar a continuación, o -1 si la prueba terminó. */
    public int calcularSiguienteNivel() {
        if (historial.isEmpty()) return 0;
        FilaEvaluada ultima = historial.get(historial.size() - 1);

        // ¿Seguimos en cribado? (todas las filas evaluadas fueron perfectas y en saltos)
        boolean cribado = true;
        for (int i = 0; i < historial.size(); i++) {
            FilaEvaluada f = historial.get(i);
            if (f.aciertos() < LETRAS_POR_FILA || f.nivel() != i * SALTO_CRIBADO) { cribado = false; break; }
        }
        if (cribado) {
            int salto = ultima.nivel() + SALTO_CRIBADO;
            if (salto < NIVELES_LOGMAR.length) return salto;
            // Ya no caben saltos: se afinan las filas restantes de una en una.
        }

        int ultimaPerfecta = nivelUltimaPerfectaContigua();
        int siguiente = ultimaPerfecta + 1;
        while (siguiente < NIVELES_LOGMAR.length && buscar(siguiente) != null) {
            FilaEvaluada ya = buscar(siguiente);
            if (ya.aciertos() < ACIERTOS_MINIMOS_FILA) return -1;
            siguiente++;
        }
        return siguiente < NIVELES_LOGMAR.length ? siguiente : -1;
    }

    /** Última fila perfecta del cribado (las anteriores se dan por leídas). -1 si ninguna. */
    private int nivelUltimaPerfectaContigua() {
        int nivel = -1;
        for (int i = 0; i < historial.size(); i++) {
            FilaEvaluada f = historial.get(i);
            if (f.aciertos() == LETRAS_POR_FILA && f.nivel() == i * SALTO_CRIBADO) nivel = f.nivel();
            else break;
        }
        return nivel;
    }

    private FilaEvaluada buscar(int nivel) {
        for (FilaEvaluada f : historial) if (f.nivel() == nivel) return f;
        return null;
    }

    public boolean haySiguienteFila() {
        return calcularSiguienteNivel() >= 0;
    }

    /** Genera la siguiente fila según la escalera. Llamar solo si {@link #haySiguienteFila()}. */
    public Fila generarSiguienteFila() {
        int nivel = calcularSiguienteNivel();
        if (nivel < 0) nivel = NIVELES_LOGMAR.length - 1;
        double logMar = NIVELES_LOGMAR[nivel];
        double vDecimal = TrigonometriaVisual.decimalDesdeLogMAR(logMar);
        filaPendiente = new Fila(letrasDistintas(LETRAS_POR_FILA), vDecimal, logMar, nivel, calcularTamanioLetraEnPixeles(vDecimal));
        return filaPendiente;
    }

    /** Compatibilidad: registro por letra (no altera la escalera). */
    public void registrarResultadoLetra(boolean acierto) { }

    /** Registra la fila mostrada con el número de letras leídas correctamente. */
    public void registrarResultadoFila(Fila fila, int aciertosFila) {
        if (fila == null) return;
        int nivel = fila.nivel >= 0 ? fila.nivel : nivelMasCercano(fila.logMar);
        int aciertos = Math.max(0, Math.min(fila.letras.length, aciertosFila));
        historial.removeIf(f -> f.nivel() == nivel);
        historial.add(new FilaEvaluada(nivel, aciertos));
        filaPendiente = null;
    }

    /** Elimina la última fila registrada (corrección del operador). @return true si había algo que deshacer. */
    public boolean deshacerUltimaFila() {
        if (historial.isEmpty()) return false;
        historial.remove(historial.size() - 1);
        filaPendiente = null;
        return true;
    }

    public List<FilaEvaluada> getHistorial() { return Collections.unmodifiableList(historial); }

    private static int nivelMasCercano(double logMar) {
        int mejor = 0;
        for (int i = 1; i < NIVELES_LOGMAR.length; i++) {
            if (Math.abs(NIVELES_LOGMAR[i] - logMar) < Math.abs(NIVELES_LOGMAR[mejor] - logMar)) mejor = i;
        }
        return mejor;
    }

    // =====================================================================
    // Resultados
    // =====================================================================

    /** Aciertos acumulados para la puntuación (filas saltadas del cribado cuentan como leídas). */
    public int getAciertosPuntuados() {
        int ultimaPerfecta = nivelUltimaPerfectaContigua();
        int total = 0;
        for (int nivel = 0; nivel <= ultimaPerfecta; nivel++) {
            FilaEvaluada f = buscar(nivel);
            total += f != null ? f.aciertos() : LETRAS_POR_FILA;
        }
        for (FilaEvaluada f : historial) if (f.nivel() > ultimaPerfecta) total += f.aciertos();
        return total;
    }

    /** LogMAR letra por letra. NaN si no se evaluó ninguna fila. */
    public double getLogMarPuntuado() {
        if (historial.isEmpty()) return Double.NaN;
        double valor = TrigonometriaVisual.calcularPuntuacionETDRS(NIVELES_LOGMAR[0], LETRAS_POR_FILA, getAciertosPuntuados());
        return Math.round(valor * 100.0) / 100.0;
    }

    /** Aciertos reales (solo filas mostradas). */
    public int getAciertosTotales() {
        int total = 0;
        for (FilaEvaluada f : historial) total += f.aciertos();
        return total;
    }

    public int getLetrasEvaluadasTotales() { return historial.size() * LETRAS_POR_FILA; }

    public int getFilasEvaluadas() { return historial.size(); }

    /** Nivel más pequeño leído (≥ 2/3, o dado por leído en el cribado). -1 si no leyó 20/200. */
    public int getIndiceUltimaFilaCorrecta() {
        int mejor = nivelUltimaPerfectaContigua();
        for (FilaEvaluada f : historial) if (f.aciertos() >= ACIERTOS_MINIMOS_FILA) mejor = Math.max(mejor, f.nivel());
        return mejor;
    }

    public static int getNumeroNiveles() { return NIVELES_LOGMAR.length; }

    public static double getNivelLogMar(int indice) {
        if (indice < 0 || indice >= NIVELES_LOGMAR.length) return Double.NaN;
        return NIVELES_LOGMAR[indice];
    }

    public static double getNivelAgudeza(int indice) {
        if (indice < 0 || indice >= NIVELES_LOGMAR.length) return 0;
        return TrigonometriaVisual.decimalDesdeLogMAR(NIVELES_LOGMAR[indice]);
    }

    /** Convierte un V_decimal a la notación clínica habitual "20/XX". */
    public static String formatoSnellen(double vDecimal) {
        return TrigonometriaVisual.snellenDesdeLogMAR(TrigonometriaVisual.calcularLogMAR(vDecimal));
    }

    public ReconocedorOptotipos getMotorVoz() { return motorVoz; }
}
