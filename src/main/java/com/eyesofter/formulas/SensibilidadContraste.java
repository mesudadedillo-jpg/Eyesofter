package com.eyesofter.formulas;

/**
 * Lógica de la prueba de sensibilidad al contraste (tipo Pelli-Robson).
 *
 * <ul>
 *   <li>Tripletes con log CS = 0.00, 0.15, … 2.25 (16 tripletes).</li>
 *   <li>Contraste de Weber C = 10^(−log CS) sobre fondo blanco.</li>
 *   <li>Termina cuando un triplete tiene 2 o más letras no leídas.</li>
 *   <li>Puntuación letra por letra: cada letra vale 1/3 del paso entre tripletes.</li>
 * </ul>
 *
 * <h3>Contraste realmente dibujado</h3>
 * Con 8 bits por canal solo existen 256 grises. Los tripletes 1.95, 2.10 y 2.25 caen
 * los tres en el gris 254 (contraste real ≈ log CS 2.05) y 1.50 / 1.65 se dibujan
 * como 1.45 / 1.58. Antes se puntuaba con el valor nominal: se mostraba el mismo
 * gris tres veces y se acreditaba hasta 2.25, y cerca del corte normal/limítrofe
 * (1.65) el resultado salía corrido. Ahora: (1) solo se muestran tripletes con un
 * gris distinto al anterior y (2) cada letra se puntúa con el contraste real del
 * gris que se dibujó.
 */
public final class SensibilidadContraste {
    public static final int NUMERO_TRIPLETES = 16;
    public static final double PASO_LOG = 0.15;
    /** Tamaño angular de las letras (Pelli-Robson: 2.8°). */
    public static final double GRADOS_LETRA = 2.8;

    private int tripletes;
    private int letrasLeidas;
    private final java.util.List<Integer> letrasPorTriplete = new java.util.ArrayList<>();
    private boolean terminada;

    /** Nivel de gris (sRGB) para un contraste y el contraste que realmente se logra con 8 bits. */
    public record Gris(int valor8bits, double contrasteReal) { }

    public void reiniciar() { tripletes = 0; letrasLeidas = 0; letrasPorTriplete.clear(); terminada = false; }

    public void registrarTriplete(int leidas) {
        if (terminada) return;
        int n = Math.max(0, Math.min(3, leidas));
        letrasLeidas += n;
        letrasPorTriplete.add(n);
        tripletes++;
        if (n <= 1 || tripletes >= tripletesRepresentables()) terminada = true;
    }

    /**
     * Tripletes que se pueden dibujar con un gris distinto al anterior (con 8 bits: 14,
     * hasta el nominal 1.95). Los siguientes repetirían el mismo gris.
     */
    public static int tripletesRepresentables() {
        int n = 1;
        while (n < NUMERO_TRIPLETES
                && grisParaLogCs(logCsDeTriplete(n)).valor8bits() != grisParaLogCs(logCsDeTriplete(n - 1)).valor8bits()) n++;
        return n;
    }

    /** log CS del contraste que realmente se dibuja para el triplete (0 para el primero). */
    public static double logCsRealDeTriplete(int indice) {
        if (indice <= 0) return 0.0;
        return -Math.log10(grisParaLogCs(logCsDeTriplete(indice)).contrasteReal());
    }

    public boolean terminada() { return terminada; }
    public int getTripletesEvaluados() { return tripletes; }

    /**
     * Letra por letra con el contraste real: cada letra del triplete i vale 1/3 del
     * salto entre el contraste real del triplete anterior y el suyo. Con pasos
     * exactos de 0.15 es la fórmula clásica 0.05 · letras − 0.15.
     */
    public double getLogCs() {
        double valor = -PASO_LOG;
        double anterior = -PASO_LOG;
        for (int i = 0; i < letrasPorTriplete.size(); i++) {
            double real = logCsRealDeTriplete(i);
            valor += letrasPorTriplete.get(i) / 3.0 * (real - anterior);
            anterior = real;
        }
        return Math.max(0, Math.round(valor * 100.0) / 100.0);
    }

    public static double logCsDeTriplete(int indice) {
        return Math.round(indice * PASO_LOG * 100.0) / 100.0;
    }

    /**
     * Convierte el contraste de Weber a un gris sRGB de 8 bits (con la curva gamma sRGB,
     * para que el contraste de luminancia sea el pedido y no el del valor numérico).
     */
    public static Gris grisParaLogCs(double logCs) {
        double contraste = Math.pow(10, -logCs);
        double luminancia = Math.max(0, 1.0 - contraste);
        int valor = (int) Math.round(aSrgb(luminancia) * 255);
        valor = Math.max(0, Math.min(254, valor)); // 255 sería invisible (contraste 0)
        double real = 1.0 - aLineal(valor / 255.0);
        return new Gris(valor, real);
    }

    public static String interpretar(double logCs) {
        if (logCs >= 1.65) return "dentro de lo esperado";
        if (logCs >= 1.50) return "limítrofe";
        return "reducida: se sugiere valoración optométrica";
    }

    static double aSrgb(double lineal) {
        return lineal <= 0.0031308 ? 12.92 * lineal : 1.055 * Math.pow(lineal, 1 / 2.4) - 0.055;
    }

    static double aLineal(double srgb) {
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }
}
