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

    /** Devuelve el factor de corrección de la fuente, o 0.716 (Arial) si no está catalogada. */
    public static double factorDeCorreccion(String nombreFuente) {
        return FACTOR_POR_FUENTE.getOrDefault(nombreFuente, 0.716);
    }
}
