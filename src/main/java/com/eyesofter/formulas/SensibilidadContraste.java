package com.eyesofter.formulas;

/**
 * Lógica de la prueba de sensibilidad al contraste (tipo Pelli-Robson).
 *
 * <ul>
 *   <li>Tripletes con log CS = 0.00, 0.15, … 2.25 (16 tripletes).</li>
 *   <li>Contraste de Weber C = 10^(−log CS) sobre fondo blanco.</li>
 *   <li>Termina cuando un triplete tiene 2 o más letras no leídas.</li>
 *   <li>Puntuación letra por letra: log CS = 0.05 · letras leídas − 0.15.</li>
 * </ul>
 */
public final class SensibilidadContraste {
    public static final int NUMERO_TRIPLETES = 16;
    public static final double PASO_LOG = 0.15;
    /** Tamaño angular de las letras (Pelli-Robson: 2.8°). */
    public static final double GRADOS_LETRA = 2.8;

    private int tripletes;
    private int letrasLeidas;
    private boolean terminada;

    /** Nivel de gris (sRGB) para un contraste y el contraste que realmente se logra con 8 bits. */
    public record Gris(int valor8bits, double contrasteReal) { }

    public void reiniciar() { tripletes = 0; letrasLeidas = 0; terminada = false; }

    public void registrarTriplete(int leidas) {
        if (terminada) return;
        int n = Math.max(0, Math.min(3, leidas));
        letrasLeidas += n;
        tripletes++;
        if (n <= 1 || tripletes >= NUMERO_TRIPLETES) terminada = true;
    }

    public boolean terminada() { return terminada; }
    public int getTripletesEvaluados() { return tripletes; }

    public double getLogCs() {
        double valor = 0.05 * letrasLeidas - 0.15;
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
