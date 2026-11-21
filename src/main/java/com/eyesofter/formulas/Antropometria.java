package com.eyesofter.formulas;

import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Valores de referencia de la distancia interpupilar (DP) según la edad.
 *
 * <h3>Por qué importa</h3>
 * La cámara estima la distancia a partir de la separación de los ojos en la imagen
 * (modelo pinhole: d = DP · f / p). Si se supone la misma DP para todos, un niño
 * (DP ≈ 50 mm) se "ve" más lejos que un adulto (DP ≈ 63 mm): la app le pide
 * acercarse, las letras le quedan más grandes y su agudeza sale mejor de lo real.
 * Usar la DP esperada para la edad reduce ese sesgo de ~20 % a ~5-6 % (la variación
 * natural entre personas de la misma edad, ±1 desviación estándar ≈ 3 mm).
 *
 * <h3>Fuente de los valores</h3>
 * Aproximación de los promedios publicados para población pediátrica
 * (MacLachlan y Howland, Ophthalmic Physiol Opt 2002; Pryor 1969) y adulta
 * (Dodgson 2004: 63.4 ± 3.8 mm; mujeres ≈ 62, hombres ≈ 65). Son valores de
 * referencia, no una norma para población mexicana: si la clínica mide la DP
 * con regla o pupilómetro, ese valor siempre tiene prioridad.
 *
 * <p>La estatura no se usa: aporta muy poca información adicional sobre la DP
 * una vez conocida la edad, y pedirla agregaría un paso más al tamizaje.</p>
 */
public final class Antropometria {

    /** DP adulta promedio (ambos sexos) cuando no se conoce la edad. */
    public static final double DP_ADULTO_MM = 63.0;
    /** Desviación estándar típica de la DP dentro de un mismo grupo de edad. */
    public static final double DE_DP_MM = 3.5;
    /** Rango plausible para aceptar una DP capturada o medida. */
    public static final double DP_MINIMA_MM = 38.0;
    public static final double DP_MAXIMA_MM = 80.0;

    /** DP media (mm) por edad cumplida, de 0 a 18 años. */
    private static final double[] DP_POR_EDAD_MM = {
            40.0, // 0
            43.0, // 1
            45.0, // 2
            47.0, // 3
            48.5, // 4
            50.0, // 5
            51.0, // 6
            52.0, // 7
            53.0, // 8
            54.0, // 9
            55.0, // 10
            56.0, // 11
            57.0, // 12
            58.0, // 13
            59.0, // 14
            60.0, // 15
            61.0, // 16
            61.5, // 17
            62.5  // 18
    };

    private Antropometria() { }

    /**
     * DP esperada para la edad. Interpola linealmente con meses si se conoce la
     * edad exacta en años decimales. Edad negativa (desconocida) → promedio adulto.
     */
    public static double dpEsperadaMm(double edadAnios) {
        if (!Double.isFinite(edadAnios) || edadAnios < 0) return DP_ADULTO_MM;
        if (edadAnios >= DP_POR_EDAD_MM.length - 1) {
            return edadAnios >= 20 ? DP_ADULTO_MM : DP_POR_EDAD_MM[DP_POR_EDAD_MM.length - 1];
        }
        int base = (int) Math.floor(edadAnios);
        double fraccion = edadAnios - base;
        return DP_POR_EDAD_MM[base] + fraccion * (DP_POR_EDAD_MM[base + 1] - DP_POR_EDAD_MM[base]);
    }

    public static boolean esDpPlausible(double mm) {
        return Double.isFinite(mm) && mm >= DP_MINIMA_MM && mm <= DP_MAXIMA_MM;
    }

    /** Edad en años decimales a partir de la fecha de nacimiento (ISO yyyy-MM-dd). NaN si no se puede. */
    public static double edadDesdeFecha(String fechaIso, LocalDate hoy) {
        if (fechaIso == null || fechaIso.isBlank()) return Double.NaN;
        try {
            LocalDate nacimiento = LocalDate.parse(fechaIso.trim());
            return edadDesdeFecha(nacimiento, hoy);
        } catch (DateTimeParseException e) {
            return Double.NaN;
        }
    }

    public static double edadDesdeFecha(LocalDate nacimiento, LocalDate hoy) {
        if (nacimiento == null || hoy == null || nacimiento.isAfter(hoy)) return Double.NaN;
        Period p = Period.between(nacimiento, hoy);
        return p.getYears() + p.getMonths() / 12.0 + p.getDays() / 365.25;
    }

    /**
     * Fecha de nacimiento codificada en una CURP mexicana (posiciones 5-10, AAMMDD).
     * El carácter 17 distingue el siglo: dígito → 1900-1999, letra → 2000 en adelante.
     *
     * @return la fecha o {@code null} si la CURP no tiene el formato esperado.
     */
    public static LocalDate fechaDesdeCurp(String curp) {
        if (curp == null) return null;
        String c = curp.trim().toUpperCase(Locale.ROOT);
        if (c.length() < 17) return null;
        String fecha = c.substring(4, 10);
        if (!fecha.chars().allMatch(Character::isDigit)) return null;
        char siglo = c.charAt(16);
        int anio2 = Integer.parseInt(fecha.substring(0, 2));
        int anio = Character.isDigit(siglo) ? 1900 + anio2 : Character.isLetter(siglo) ? 2000 + anio2 : -1;
        if (anio < 0) return null;
        try {
            return LocalDate.of(anio, Integer.parseInt(fecha.substring(2, 4)), Integer.parseInt(fecha.substring(4, 6)));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Edad con la mejor fuente disponible: fecha de nacimiento, luego CURP.
     * @return años decimales o NaN si no hay datos.
     */
    public static double edadPaciente(String fechaNacimientoIso, String curp, LocalDate hoy) {
        double edad = edadDesdeFecha(fechaNacimientoIso, hoy);
        if (Double.isFinite(edad)) return edad;
        LocalDate desdeCurp = fechaDesdeCurp(curp);
        return desdeCurp == null ? Double.NaN : edadDesdeFecha(desdeCurp, hoy);
    }
}
