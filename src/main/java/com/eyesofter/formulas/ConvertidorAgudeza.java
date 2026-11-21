package com.eyesofter.formulas;

import java.util.Locale;

/**
 * Conversión entre notaciones de agudeza visual: Snellen (20/x, 6/x), decimal,
 * LogMAR y MAR, más el tamaño del optotipo equivalente a una distancia dada.
 */
public final class ConvertidorAgudeza {

    public enum Notacion {
        SNELLEN("Snellen (20/40, 6/12…)"),
        DECIMAL("Decimal (0.5, 1.0…)"),
        LOGMAR("LogMAR (0.3, 0.0…)");

        private final String etiqueta;
        Notacion(String etiqueta) { this.etiqueta = etiqueta; }
        @Override public String toString() { return etiqueta; }
    }

    /** Resultado de una conversión. */
    public record Equivalencias(double logMar, double decimal, double mar, String snellenPies, String snellenMetrico) {
        public String descripcion() {
            return String.format(Locale.US, "%s  ·  %s  ·  decimal %.2f  ·  LogMAR %.2f  ·  MAR %.2f′",
                    snellenPies, snellenMetrico, decimal, logMar, mar);
        }
    }

    private ConvertidorAgudeza() { }

    /**
     * @throws IllegalArgumentException si el texto no es válido para la notación.
     */
    public static Equivalencias convertir(String texto, Notacion notacion) {
        if (texto == null || texto.isBlank()) throw new IllegalArgumentException("Escriba un valor.");
        String t = texto.trim().replace(',', '.');
        double logMar;
        switch (notacion) {
            case SNELLEN -> {
                String[] partes = t.split("/");
                if (partes.length != 2) throw new IllegalArgumentException("Use el formato 20/40 o 6/12.");
                double num = Double.parseDouble(partes[0].trim());
                double den = Double.parseDouble(partes[1].trim());
                if (num <= 0 || den <= 0) throw new IllegalArgumentException("Los valores deben ser positivos.");
                logMar = Math.log10(den / num);
            }
            case DECIMAL -> {
                double dec = Double.parseDouble(t);
                if (dec <= 0 || dec > 4) throw new IllegalArgumentException("El decimal debe estar entre 0 y 4.");
                logMar = TrigonometriaVisual.calcularLogMAR(dec);
            }
            default -> {
                logMar = Double.parseDouble(t);
                if (logMar < -0.6 || logMar > 3) throw new IllegalArgumentException("El LogMAR debe estar entre −0.6 y 3.");
            }
        }
        double decimal = TrigonometriaVisual.decimalDesdeLogMAR(logMar);
        return new Equivalencias(logMar, decimal, 1.0 / decimal,
                TrigonometriaVisual.snellenDesdeLogMAR(logMar), TrigonometriaVisual.snellenMetricoDesdeLogMAR(logMar));
    }

    /** Altura (mm) de la letra para esa agudeza a la distancia indicada (metros). */
    public static double alturaLetraMm(Equivalencias e, double distanciaMetros) {
        return TrigonometriaVisual.calcularAlturaMM(distanciaMetros * 1000.0, e.mar());
    }
}
