package com.eyesofter.medicion_snellen;

import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.formulas.FuentesOptotipo;
import com.eyesofter.formulas.TrigonometriaVisual;
import com.eyesofter.voz.ReconocedorOptotipos;
import java.util.Random;

/**
 * Cerebro avanzado de la evaluación visual de Snellen.
 * Integra dimensiones trigonométricas dinámicas, validación de distancia (1m a 6m)
 * y control acústico robusto.
 *
 * NUEVO: además del flujo original de consola ({@link #iniciarPruebaSnellen}),
 * se agrega una API interactiva fila-por-fila ({@link #generarSiguienteFila()})
 * pensada para el carrusel vertical de la interfaz (fila anterior / actual /
 * siguiente) que usa {@code ControladorSnellen}. El tamaño de cada letra ahora
 * depende de los 3 factores solicitados:
 *   1) la distancia calibrada (configurarEntornoPrueba),
 *   2) el PPI del monitor, calculado a partir de su tamaño físico (Factor 2), y
 *   3) la tipografía elegida (Factor 3, ver {@link FuentesOptotipo}).
 */
public class MotorSnellen {
    private ReconocedorOptotipos motorVoz;
    private DetectorDistancia detectorDistancia; // Opcional si se acopla con la cámara
    private final String[] LETRAS_VALIDAS = {"C", "D", "E", "F", "L", "O", "P", "T", "Z"};
    private Random random;

    // Parámetros configurables del entorno físico
    private double distanciaConfiguradaCm = 100.0; // Por defecto 1 metro, ajustable hasta 6 metros
    private double ppiMonitor = 96.0; // Valor estándar de pantallas, ajustable según el monitor detectado
    private String fuenteOptotipo = "Arial"; // Factor 3: tipo de letra

    // --- Estado de la API interactiva (carrusel de filas) ---
    private static final double[] NIVELES_AGUDEZA = {0.1, 0.16, 0.25, 0.4, 0.5, 0.63, 0.8, 1.0};
    private int indiceNivelActual = -1;
    private int indiceUltimaFilaCorrecta = -1;
    private int aciertosTotales = 0;
    private int letrasEvaluadasTotales = 0;

    public MotorSnellen(ReconocedorOptotipos motorVoz, DetectorDistancia detectorDistancia) {
        this.motorVoz = motorVoz;
        this.detectorDistancia = detectorDistancia;
        this.random = new Random();
    }

    /**
     * Permite a la interfaz de configuración actualizar la distancia de la prueba (1m a 6m).
     * Se mantiene por compatibilidad; usa la fuente por defecto (Arial).
     */
    public void configurarEntornoPrueba(double distanciaMetros, double ppiCalculado) {
        configurarEntornoPrueba(distanciaMetros, ppiCalculado, fuenteOptotipo);
    }

    /**
     * Igual que {@link #configurarEntornoPrueba(double, double)} pero incluyendo
     * el Factor 3 (tipo de letra) explícitamente.
     */
    public void configurarEntornoPrueba(double distanciaMetros, double ppiCalculado, String nombreFuenteOptotipo) {
        this.distanciaConfiguradaCm = distanciaMetros * 100.0;
        this.ppiMonitor = ppiCalculado;
        this.fuenteOptotipo = (nombreFuenteOptotipo != null) ? nombreFuenteOptotipo : this.fuenteOptotipo;
        if (this.detectorDistancia != null) {
            this.detectorDistancia.setDistanciaObjetivo(this.distanciaConfiguradaCm);
        }
    }

    /**
     * Calcula dinámicamente el tamaño en píxeles que debe tener la letra en pantalla
     * aplicando las Fórmulas 4, 5 y 6 (Trigonometría Visual) + el factor de la fuente (Factor 3).
     * @param vDecimal Agudeza visual a evaluar en este nivel (ej. 1.0 para 20/20, 0.5 para 20/40).
     */
    public double calcularTamanioLetraEnPixeles(double vDecimal) {
        // Fórmula 4: MAR = 1 / V_decimal
        double mar = TrigonometriaVisual.calcularMAR(vDecimal);

        // Convertimos la distancia de centímetros a milímetros
        double dMm = distanciaConfiguradaCm * 10.0;

        // Fórmula 5: Altura física estricta en milímetros (h_mm)
        double hMm = TrigonometriaVisual.calcularAlturaMM(dMm, mar);

        // Fórmula 6 + Factor 3: tamaño de fuente ya corregido para la tipografía elegida
        double factorFuente = FuentesOptotipo.factorDeCorreccion(fuenteOptotipo);
        return TrigonometriaVisual.calcularTamanioFuenteAjustado(hMm, ppiMonitor, factorFuente);
    }

    public String[][] generarCartillaDinamica(int numFilas, int letrasPorFila) {
        String[][] cartilla = new String[numFilas][letrasPorFila];
        for (int i = 0; i < numFilas; i++) {
            for (int j = 0; j < letrasPorFila; j++) {
                cartilla[i][j] = LETRAS_VALIDAS[random.nextInt(LETRAS_VALIDAS.length)];
            }
        }
        return cartilla;
    }

    // =====================================================================
    // NUEVO: API interactiva fila-por-fila para el carrusel vertical de la UI
    // =====================================================================

    /** Una fila de 3 optotipos ya dimensionados para el carrusel de la interfaz. */
    public static class Fila {
        public final String[] letras;
        public final double vDecimal;
        public final double tamanioPx;

        public Fila(String[] letras, double vDecimal, double tamanioPx) {
            this.letras = letras;
            this.vDecimal = vDecimal;
            this.tamanioPx = tamanioPx;
        }
    }

    /** Reinicia el progreso interactivo (llamar al empezar una nueva evaluación). */
    public void reiniciarInteractivo() {
        indiceNivelActual = -1;
        indiceUltimaFilaCorrecta = -1;
        aciertosTotales = 0;
        letrasEvaluadasTotales = 0;
    }

    /** @return true si quedan niveles de agudeza por evaluar. */
    public boolean haySiguienteFila() {
        return (indiceNivelActual + 1) < NIVELES_AGUDEZA.length;
    }

    /**
     * Genera la siguiente fila de 3 letras (avanza el nivel de agudeza) ya con
     * su tamaño en píxeles calculado con los 3 factores. Úsalo para ir llenando
     * el carrusel (fila actual / siguiente) del ControladorSnellen.
     */
    public Fila generarSiguienteFila() {
        indiceNivelActual++;
        double vDecimal = NIVELES_AGUDEZA[Math.min(indiceNivelActual, NIVELES_AGUDEZA.length - 1)];
        String[] letras = new String[3];
        for (int i = 0; i < 3; i++) {
            letras[i] = LETRAS_VALIDAS[random.nextInt(LETRAS_VALIDAS.length)];
        }
        double tamanioPx = calcularTamanioLetraEnPixeles(vDecimal);
        return new Fila(letras, vDecimal, tamanioPx);
    }

    /** El controlador llama esto por cada letra ya evaluada (acierto o error) para el marcador final. */
    public void registrarResultadoLetra(boolean acierto) {
        letrasEvaluadasTotales++;
        if (acierto) aciertosTotales++;
    }

    /**
     * Registra una línea ya evaluada. Con tres optotipos, 2/3 correctos es el
     * criterio mínimo operativo para considerar que la línea fue leída. Así se
     * conserva la última línea aprobada que se convertirá a Snellen y LogMAR.
     */
    public void registrarResultadoFila(Fila fila, int aciertosFila) {
        if (fila == null) return;
        int aciertosSeguros = Math.max(0, Math.min(fila.letras.length, aciertosFila));
        letrasEvaluadasTotales += fila.letras.length;
        aciertosTotales += aciertosSeguros;
        if (aciertosSeguros >= 2) {
            indiceUltimaFilaCorrecta = indiceNivelActual;
        }
    }

    public int getAciertosTotales() {
        return aciertosTotales;
    }

    public int getLetrasEvaluadasTotales() {
        return letrasEvaluadasTotales;
    }

    public int getIndiceUltimaFilaCorrecta() {
        return indiceUltimaFilaCorrecta;
    }

    public static int getNumeroNiveles() {
        return NIVELES_AGUDEZA.length;
    }

    public static double getNivelAgudeza(int indice) {
        if (indice < 0 || indice >= NIVELES_AGUDEZA.length) return 0;
        return NIVELES_AGUDEZA[indice];
    }

    /** Convierte un V_decimal a la notación clínica habitual "20/XX". */
    public static String formatoSnellen(double vDecimal) {
        double mar = TrigonometriaVisual.calcularMAR(vDecimal);
        int denominador = (int) Math.round(20 * mar);
        return "20/" + denominador;
    }

    /**
     * Inicia el flujo clínico completo de Snellen con respaldo matemático y de visión artificial.
     * Se conserva como utilidad de consola / pruebas rápidas sin interfaz gráfica.
     */
    public void iniciarPruebaSnellen(String tipoOjo) { // tipoOjo: "GENERAL", "IZQUIERDO", "DERECHO"
        System.out.println("==================================================");
        System.out.println("   INICIANDO EXAMEN SNELLEN (" + tipoOjo + ")       ");
        System.out.println("   Distancia objetivo: " + (distanciaConfiguradaCm / 100.0) + " metros       ");
        System.out.println("==================================================");

        // 1. Activamos el interruptor acústico para asegurar que escuche letras
        motorVoz.setModo(ReconocedorOptotipos.ModoPrueba.SNELLEN);

        // Niveles de agudeza visual simulados (V_decimal: 0.5 = 20/40, 0.8 = 20/25, 1.0 = 20/20)
        String[][] cartilla = generarCartillaDinamica(NIVELES_AGUDEZA.length, 3);
        int aciertosLocal = 0;

        for (int fila = 0; fila < cartilla.length; fila++) {
            double vDecimalActual = NIVELES_AGUDEZA[fila];

            // Calculamos matemáticamente el tamaño exacto de la letra para este nivel a la distancia actual
            double tamanioPixeles = calcularTamanioLetraEnPixeles(vDecimalActual);
            System.out.printf("\n--- Nivel V_decimal: %.1f | Tamaño de renderizado: %.1f px ---\n", vDecimalActual, tamanioPixeles);

            // Recorrido de derecha a izquierda según tu instrucción
            for (int col = cartilla[fila].length - 1; col >= 0; col--) {
                String letraEsperada = cartilla[fila][col];
                boolean avanzarSiguienteLetra = false;

                while (!avanzarSiguienteLetra) {

                    // VALIDACIÓN DE POSTURA POR CÁMARA (Opcional pero activa)
                    if (detectorDistancia != null) {
                        boolean enPosicion = detectorDistancia.validarPosicionPaciente();
                        if (!enPosicion) {
                            System.out.println("🛑 [SEGURIDAD VISUAL]: El paciente se movió del rango permitido. Pausando prueba...");
                            System.out.println("🔊 [VOZ MECÁNICA]: 'Por favor, acomódate a la distancia correcta frente al monitor'.");
                            try { Thread.sleep(2000); } catch (InterruptedException e) {}
                            continue; // Repite el ciclo hasta que el alumno se acomode
                        }
                    }

                    // Renderizado en Pantalla (Aquí la UI de JavaFX leerá este tamaño en píxeles)
                    System.out.println("\n[PANTALLA JAVAFX]: Proyectando [" + letraEsperada + "] a " + tamanioPixeles + "px (Columna der->izq: " + (col + 1) + ")");

                    // Escuchamos la respuesta del usuario con el motor acústico
                    String respuestaAcustica = motorVoz.escucharOptotipo();

                    switch (respuestaAcustica) {
                        case "TIMEOUT":
                            System.out.println("⏳ [CONTROL]: Silencio. Voz mecánica: '¿Qué letra ves en la pantalla?'.");
                            break;

                        case "RUIDO":
                            System.out.println("🛑 [CONTROL]: Saturación ambiental (WER > 0.40). Pausando intento.");
                            break;

                        case "ERROR":
                            System.err.println("⚠️ [CRÍTICO]: Error en el búfer de voz.");
                            avanzarSiguienteLetra = true;
                            break;

                        default:
                            if (respuestaAcustica.equals(letraEsperada)) {
                                System.out.println("✅ [CONTROL]: ¡Acierto! Dijo [" + respuestaAcustica + "].");
                                aciertosLocal++;
                            } else {
                                System.out.println("❌ [CONTROL]: Fallo. Vio [" + respuestaAcustica + "] en vez de [" + letraEsperada + "].");
                            }
                            avanzarSiguienteLetra = true;
                            break;
                    }
                }
            }
        }

        System.out.println("\n==================================================");
        System.out.println("        FIN DE LA PRUEBA (" + tipoOjo + ")        ");
        System.out.println("        Aciertos: " + aciertosLocal + " / " + (cartilla.length * 3));
        System.out.println("==================================================");
    }
}
