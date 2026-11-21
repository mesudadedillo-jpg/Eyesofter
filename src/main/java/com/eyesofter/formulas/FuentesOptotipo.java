package com.eyesofter.formulas;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Catálogo de tipografías permitidas para renderizar optotipos, junto con su
 * "factor de corrección" (relación aproximada cap-height / em-square).
 *
 * IMPORTANTE (honestidad técnica): estos factores son aproximaciones
 * tipográficas de uso común, NO mediciones certificadas por el fabricante de
 * cada fuente. Antes de usar EYESOFTER en un entorno clínico real, se
 * recomienda calibrar el factor de cada fuente midiendo directamente en
 * pantalla (con una regla física) la altura que produce un tamaño de fuente
 * conocido, y ajustar el valor aquí. Sloan es la tipografía estándar de oro
 * para cartillas ETDRS reales; si la tienes instalada con licencia, agrégala
 * a este mapa con su nombre exacto de familia tal como la reconoce tu SO.
 */
public final class FuentesOptotipo {

    private FuentesOptotipo() {}

    private static final Map<String, Double> FACTOR_POR_FUENTE = new LinkedHashMap<>();
    static {
        FACTOR_POR_FUENTE.put("Arial", 0.716);
        FACTOR_POR_FUENTE.put("Verdana", 0.75);
        FACTOR_POR_FUENTE.put("Courier New", 0.62);
        FACTOR_POR_FUENTE.put("Tahoma", 0.73);
        FACTOR_POR_FUENTE.put("Sloan", 0.72); // usar solo si esta instalada con licencia valida
    }

    /** Nombres disponibles para mostrar en el ComboBox de la pantalla de Configuración. */
    public static String[] nombresDisponibles() {
        return FACTOR_POR_FUENTE.keySet().toArray(new String[0]);
    }

    /**
     * Si es true, el factor se mide sobre la tipografía que JavaFX realmente dibuja.
     * Lo activa la aplicación al arrancar; en pruebas automáticas (sin pantalla) se
     * usa la tabla. Importa porque si la fuente no está instalada (p. ej. Arial en
     * Linux) JavaFX usa otra con distinta altura de mayúscula y las letras saldrían
     * de otro tamaño sin que nadie lo note.
     */
    private static volatile boolean medirEnPantalla;
    private static final Map<String, Double> FACTOR_MEDIDO = new java.util.concurrent.ConcurrentHashMap<>();

    public static void activarMedicionEnPantalla() { medirEnPantalla = true; }

    /** Devuelve el factor de corrección de la fuente, o 0.716 (Arial) si no está catalogada. */
    public static double factorDeCorreccion(String nombreFuente) {
        if (medirEnPantalla && nombreFuente != null) {
            Double medido = FACTOR_MEDIDO.computeIfAbsent(nombreFuente, FuentesOptotipo::medirFactor);
            if (medido > 0) return medido;
        }
        return FACTOR_POR_FUENTE.getOrDefault(nombreFuente, 0.716);
    }

    /** Altura de tinta de la "E" mayúscula ÷ tamaño de fuente, medida con JavaFX. -1 si falla. */
    private static double medirFactor(String nombreFuente) {
        try {
            final double tamanio = 1000.0;
            javafx.scene.text.Text texto = new javafx.scene.text.Text("E");
            // Mismo peso que usa la prueba (CSS font-weight 900).
            texto.setFont(javafx.scene.text.Font.font(nombreFuente, javafx.scene.text.FontWeight.BLACK, tamanio));
            texto.setBoundsType(javafx.scene.text.TextBoundsType.VISUAL);
            double factor = texto.getLayoutBounds().getHeight() / tamanio;
            return factor > 0.5 && factor < 0.95 ? factor : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** ¿La familia está instalada en este equipo? Si no se puede comprobar, se asume que sí. */
    public static boolean estaInstalada(String nombreFuente) {
        if (nombreFuente == null) return false;
        try {
            return javafx.scene.text.Font.getFamilies().contains(nombreFuente);
        } catch (Throwable t) {
            return true;
        }
    }
}
