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
 * <h3>Tipos de optotipo</h3>
 * <ul>
 *   <li>{@link TipoOptotipo#LETRAS}: 3 letras Snellen por fila; se lee una fila con 2/3.</li>
 *   <li>{@link TipoOptotipo#E_DIRECCIONAL}: E de Snellen girada (arriba, abajo, izquierda,
 *       derecha) para niños que aún no conocen las letras o personas que no leen.
 *       Con 4 opciones adivinar es más fácil (25 %), por eso hay 4 E por fila, se pide
 *       3/4 y la puntuación es por fila completa (sin sumar aciertos sueltos).</li>
 * </ul>
 *
 * <h3>Límite de la pantalla</h3>
 * Una fila cuyo optotipo mide menos de {@link TrigonometriaVisual#ALTURA_MINIMA_PX_FISICOS}
 * píxeles físicos no se muestra: medir con ella evaluaría el monitor y no la vista.
 * Si la persona lee la última fila que la pantalla sí puede dibujar, el resultado queda
 * marcado como "en el límite de la pantalla" (su agudeza real puede ser mejor).
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

    /** Direcciones de la E (hacia dónde apuntan sus "patitas"). */
    public static final String E_ARRIBA = "ARRIBA";
    public static final String E_ABAJO = "ABAJO";
    public static final String E_IZQUIERDA = "IZQUIERDA";
    public static final String E_DERECHA = "DERECHA";
    private static final String[] DIRECCIONES_E = {E_ARRIBA, E_ABAJO, E_IZQUIERDA, E_DERECHA};

    public enum TipoOptotipo {
        LETRAS("Letras (personas que leen)", 3, 2),
        E_DIRECCIONAL("E direccional (niños pequeños o personas que no leen)", 4, 3);

        private final String etiqueta;
        private final int optotiposPorFila;
        private final int aciertosMinimos;

        TipoOptotipo(String etiqueta, int optotiposPorFila, int aciertosMinimos) {
            this.etiqueta = etiqueta;
            this.optotiposPorFila = optotiposPorFila;
            this.aciertosMinimos = aciertosMinimos;
        }

        public int optotiposPorFila() { return optotiposPorFila; }
        public int aciertosMinimos() { return aciertosMinimos; }
        @Override public String toString() { return etiqueta; }
    }

    private final Random random;

    private double distanciaConfiguradaCm = 100.0;
    private double ppiMonitor = 96.0;
    private String fuenteOptotipo = "Arial";
    private double escalaSalida = 1.0;
    private TipoOptotipo tipo = TipoOptotipo.LETRAS;
    /** Niveles que la pantalla puede dibujar a la distancia configurada (desde el nivel 0). */
    private int nivelesMedibles = NIVELES_LOGMAR.length;
    /**
     * Primer nivel cuya fila cabe en la ventana. A 3-4 m en una laptop de alta resolución
     * la fila 20/200 puede ser más ancha que la pantalla; antes se dibujaba cortada.
     */
    private int nivelInicial = 0;
    private double anchoDisponiblePx = Double.NaN;
    private double altoDisponiblePx = Double.NaN;
    /** Ancho aproximado de una letra en negrita respecto al tamaño de fuente. */
    private static final double ANCHO_LETRA_EM = 0.72;

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
        recalcularNivelesMedibles();
    }

    public void setTipoOptotipo(TipoOptotipo nuevo) {
        this.tipo = nuevo == null ? TipoOptotipo.LETRAS : nuevo;
        recalcularNivelInicial();
        reiniciarInteractivo();
    }

    /** Espacio (píxeles lógicos) donde se dibuja la fila de optotipos. */
    public void setEspacioDisponible(double anchoPx, double altoPx) {
        this.anchoDisponiblePx = anchoPx;
        this.altoDisponiblePx = altoPx;
        recalcularNivelInicial();
    }

    private void recalcularNivelInicial() {
        nivelInicial = 0;
        if (!(anchoDisponiblePx > 0) || !(altoDisponiblePx > 0)) return;
        while (nivelInicial < nivelesMedibles - 1 && !cabe(nivelInicial)) nivelInicial++;
    }

    /** ¿La fila de ese nivel cabe a lo ancho (con su espaciado) y a lo alto? */
    private boolean cabe(int nivel) {
        double v = TrigonometriaVisual.decimalDesdeLogMAR(NIVELES_LOGMAR[nivel]);
        int n = tipo.optotiposPorFila();
        double ancho, alto;
        if (tipo == TipoOptotipo.E_DIRECCIONAL) {
            double lado = calcularLadoEEnPixeles(v);
            ancho = (2 * n - 1) * lado;
            alto = lado;
        } else {
            double fuente = calcularTamanioLetraEnPixeles(v);
            ancho = (2 * n - 1) * fuente * ANCHO_LETRA_EM;
            alto = fuente;
        }
        return ancho <= anchoDisponiblePx && alto <= altoDisponiblePx;
    }

    /** Primer nivel que se muestra (0 = 20/200). */
    public int getNivelInicial() { return nivelInicial; }
    public double getLogMarFilaInicial() { return NIVELES_LOGMAR[nivelInicial]; }

    public TipoOptotipo getTipoOptotipo() { return tipo; }
    public int getOptotiposPorFila() { return tipo.optotiposPorFila(); }
    public int getAciertosMinimosFila() { return tipo.aciertosMinimos(); }

    private void recalcularNivelesMedibles() {
        nivelesMedibles = contarNivelesMedibles(distanciaConfiguradaCm * 10.0, ppiMonitor);
        recalcularNivelInicial();
    }

    /** Niveles (desde 20/200) que se pueden dibujar con al menos la altura mínima en píxeles. */
    public static int contarNivelesMedibles(double distanciaMm, double ppiFisico) {
        int n = 0;
        while (n < NIVELES_LOGMAR.length && TrigonometriaVisual.esNivelRepresentable(distanciaMm, NIVELES_LOGMAR[n], ppiFisico)) n++;
        return Math.max(1, n);
    }

    /** Mejor LogMAR que esta pantalla puede medir a esta distancia. */
    public static double mejorLogMarMedible(double distanciaMm, double ppiFisico) {
        return NIVELES_LOGMAR[contarNivelesMedibles(distanciaMm, ppiFisico) - 1];
    }

    public int getNivelesMedibles() { return nivelesMedibles; }

    /** LogMAR de la fila más pequeña que esta pantalla puede dibujar a la distancia configurada. */
    public double getLogMarLimitePantalla() { return NIVELES_LOGMAR[nivelesMedibles - 1]; }

    /** ¿La pantalla impide mostrar filas que la escala normal sí tendría? */
    public boolean hayFilasNoMedibles() { return nivelesMedibles < NIVELES_LOGMAR.length; }

    /**
     * La persona leyó la fila más pequeña que la pantalla puede dibujar y existían
     * filas más pequeñas: su agudeza real puede ser mejor que la registrada.
     */
    public boolean isLimitadoPorPantalla() {
        return hayFilasNoMedibles() && getIndiceUltimaFilaCorrecta() == nivelesMedibles - 1;
    }

    /** Escalado de Windows de la pantalla donde se dibuja (1.25 = 125 %). */
    public void setEscalaSalida(double escala) {
        this.escalaSalida = escala > 0 ? escala : 1.0;
        recalcularNivelInicial();
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

    /**
     * Lado (alto = ancho) de la E direccional en píxeles LÓGICOS. Se dibuja con figuras
     * geométricas (no con una fuente), así que no necesita factor tipográfico.
     */
    public double calcularLadoEEnPixeles(double vDecimal) {
        double hMm = TrigonometriaVisual.calcularAlturaMM(distanciaConfiguradaCm * 10.0, TrigonometriaVisual.calcularMAR(vDecimal));
        return TrigonometriaVisual.calcularAlturaPixel(hMm, ppiMonitor) / escalaSalida;
    }

    /** Altura en píxeles físicos del optotipo de un nivel (para el informe y las pruebas). */
    public double calcularAlturaPxFisicos(double vDecimal) {
        return TrigonometriaVisual.calcularAlturaPixel(calcularAlturaMm(vDecimal), ppiMonitor);
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

    /**
     * Direcciones al azar sin dos iguales seguidas. No se usa una permutación de las 4
     * (cada una exactamente una vez) porque la última se podría deducir por descarte.
     */
    private String[] direccionesAleatorias(int cantidad) {
        String[] fila = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            String d;
            do { d = DIRECCIONES_E[random.nextInt(DIRECCIONES_E.length)]; }
            while (i > 0 && d.equals(fila[i - 1]));
            fila[i] = d;
        }
        return fila;
    }

    private String[] optotiposParaFila() {
        return tipo == TipoOptotipo.E_DIRECCIONAL
                ? direccionesAleatorias(tipo.optotiposPorFila())
                : letrasDistintas(tipo.optotiposPorFila());
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
        if (historial.isEmpty()) return nivelInicial;
        FilaEvaluada ultima = historial.get(historial.size() - 1);

        // ¿Seguimos en cribado? (todas las filas evaluadas fueron perfectas y en saltos)
        boolean cribado = true;
        for (int i = 0; i < historial.size(); i++) {
            FilaEvaluada f = historial.get(i);
            if (f.aciertos() < tipo.optotiposPorFila() || f.nivel() != nivelInicial + i * SALTO_CRIBADO) { cribado = false; break; }
        }
        if (cribado) {
            int salto = ultima.nivel() + SALTO_CRIBADO;
            if (salto < nivelesMedibles) return salto;
            // Ya no caben saltos: se afinan las filas restantes de una en una.
        }

        int ultimaPerfecta = nivelUltimaPerfectaContigua();
        int siguiente = Math.max(ultimaPerfecta + 1, nivelInicial);
        while (siguiente < nivelesMedibles && buscar(siguiente) != null) {
            FilaEvaluada ya = buscar(siguiente);
            if (ya.aciertos() < tipo.aciertosMinimos()) return -1;
            siguiente++;
        }
        return siguiente < nivelesMedibles ? siguiente : -1;
    }

    /** Última fila perfecta del cribado (las anteriores se dan por leídas). -1 si ninguna. */
    private int nivelUltimaPerfectaContigua() {
        int nivel = -1;
        for (int i = 0; i < historial.size(); i++) {
            FilaEvaluada f = historial.get(i);
            if (f.aciertos() == tipo.optotiposPorFila() && f.nivel() == nivelInicial + i * SALTO_CRIBADO) nivel = f.nivel();
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
        if (nivel < 0) nivel = nivelesMedibles - 1;
        double logMar = NIVELES_LOGMAR[nivel];
        double vDecimal = TrigonometriaVisual.decimalDesdeLogMAR(logMar);
        double tamanio = tipo == TipoOptotipo.E_DIRECCIONAL ? calcularLadoEEnPixeles(vDecimal) : calcularTamanioLetraEnPixeles(vDecimal);
        filaPendiente = new Fila(optotiposParaFila(), vDecimal, logMar, nivel, tamanio);
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
        // Filas más grandes que no cupieron: se dan por leídas si leyó la primera fila mostrada (criterio ETDRS).
        FilaEvaluada primera = buscar(nivelInicial);
        if (nivelInicial > 0 && primera != null && primera.aciertos() >= tipo.aciertosMinimos()) {
            total += nivelInicial * tipo.optotiposPorFila();
        }
        for (int nivel = nivelInicial; nivel <= ultimaPerfecta; nivel++) {
            FilaEvaluada f = buscar(nivel);
            total += f != null ? f.aciertos() : tipo.optotiposPorFila();
        }
        for (FilaEvaluada f : historial) if (f.nivel() > ultimaPerfecta) total += f.aciertos();
        return total;
    }

    /**
     * LogMAR final. NaN si no se evaluó ninguna fila.
     * <ul>
     *   <li>Letras: letra por letra (ETDRS).</li>
     *   <li>E direccional: LogMAR de la fila más pequeña leída (≥ 3/4); 1.1 si no leyó 20/200.
     *       Sumar aciertos sueltos premiaría las respuestas al azar (25 % con 4 opciones).</li>
     * </ul>
     */
    public double getLogMarPuntuado() {
        if (historial.isEmpty()) return Double.NaN;
        double valor;
        if (tipo == TipoOptotipo.E_DIRECCIONAL) {
            int indice = getIndiceUltimaFilaCorrecta();
            valor = indice < 0 ? NIVELES_LOGMAR[nivelInicial] + 0.1 : NIVELES_LOGMAR[indice];
        } else {
            valor = TrigonometriaVisual.calcularPuntuacionETDRS(NIVELES_LOGMAR[0], tipo.optotiposPorFila(), getAciertosPuntuados());
        }
        return Math.round(valor * 100.0) / 100.0;
    }

    /** Aciertos reales (solo filas mostradas). */
    public int getAciertosTotales() {
        int total = 0;
        for (FilaEvaluada f : historial) total += f.aciertos();
        return total;
    }

    public int getLetrasEvaluadasTotales() { return historial.size() * tipo.optotiposPorFila(); }

    public int getFilasEvaluadas() { return historial.size(); }

    /** Nivel más pequeño leído (≥ 2/3, o dado por leído en el cribado). -1 si no leyó 20/200. */
    public int getIndiceUltimaFilaCorrecta() {
        int mejor = nivelUltimaPerfectaContigua();
        for (FilaEvaluada f : historial) if (f.aciertos() >= tipo.aciertosMinimos()) mejor = Math.max(mejor, f.nivel());
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
