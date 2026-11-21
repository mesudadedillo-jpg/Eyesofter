package com.eyesofter.clinica;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Condiciones técnicas en que se hizo el tamizaje (pantalla, distancia, cámara,
 * optotipo). Se guardan con el resultado para que el profesional que lo lea sepa
 * cuánto confiar en él: un 20/20 medido a 1 m con la pantalla sin calibrar no vale
 * lo mismo que uno medido a 3 m con pantalla y cámara calibradas.
 */
public final class CondicionesPrueba {

    /** Cómo se obtuvo la densidad de píxeles (PPI) de la pantalla. */
    public enum MetodoPantalla {
        TARJETA("medida con tarjeta"),
        DIAGONAL("calculada con la diagonal capturada"),
        MANUAL("escrita a mano");

        private final String texto;
        MetodoPantalla(String texto) { this.texto = texto; }
        @Override public String toString() { return texto; }
    }

    /** Cómo se controló la distancia de la persona a la pantalla. */
    public enum MetodoDistancia {
        CAMARA_OJOS("cámara, posición de los ojos (YuNet)"),
        CAMARA_ROSTRO("cámara, ancho del rostro (método de respaldo)"),
        SIN_CAMARA("sin cámara: distancia colocada por el operador");

        private final String texto;
        MetodoDistancia(String texto) { this.texto = texto; }
        @Override public String toString() { return texto; }
    }

    /** De dónde salió la distancia interpupilar que usa la cámara para estimar la distancia. */
    public enum FuenteDp {
        MEDIDA_CON_CINTA("medida en esta sesión con cinta"),
        CAPTURADA("capturada por el operador"),
        POR_EDAD("estimada por edad"),
        PROMEDIO_ADULTO("promedio adulto (edad desconocida)");

        private final String texto;
        FuenteDp(String texto) { this.texto = texto; }
        @Override public String toString() { return texto; }
    }

    /** Si la persona usa lentes y si los traía puestos: cambia lo que significa el resultado. */
    public enum Correccion {
        NO_REGISTRADO("no se registró si usa lentes"),
        NO_USA("no usa lentes"),
        CON_SUS_LENTES("con sus lentes puestos"),
        SIN_SUS_LENTES("usa lentes pero se evaluó sin ellos");

        private final String texto;
        Correccion(String texto) { this.texto = texto; }
        @Override public String toString() { return texto; }
    }

    public Correccion correccion = Correccion.NO_REGISTRADO;
    public double ppi = Double.NaN;
    public MetodoPantalla metodoPantalla = MetodoPantalla.DIAGONAL;
    public double escalaPantalla = 1.0;
    /** null = no se pudo comprobar; false = la prueba se mostró en otra pantalla distinta a la calibrada. */
    public Boolean pantallaCalibradaCoincide;
    public double distanciaObjetivoM = Double.NaN;
    public double toleranciaCm = Double.NaN;
    public MetodoDistancia metodoDistancia = MetodoDistancia.SIN_CAMARA;
    public boolean camaraCalibrada;
    public FuenteDp fuenteDp = FuenteDp.PROMEDIO_ADULTO;
    public double dpUsadaMm = Double.NaN;
    public double distanciaRealPromedioM = Double.NaN;
    public double distanciaRealDesviacionM = Double.NaN;
    public String tipoOptotipo = "Letras";
    public double logMarLimitePantalla = Double.NaN;
    /** Fila más grande mostrada (1.0 = 20/200); mayor que 1.0 nunca, menor si las grandes no cupieron. */
    public double logMarFilaInicial = Double.NaN;
    public boolean brilloConfirmado;
    public String fuenteOptotipo = "";
    public boolean fuenteInstalada = true;

    /** Avisos que reducen la confianza del resultado (vacío si las condiciones fueron buenas). */
    public List<String> avisos() {
        List<String> avisos = new ArrayList<>();
        if (metodoPantalla != MetodoPantalla.TARJETA)
            avisos.add("Tamaño de pantalla no medido con tarjeta (" + metodoPantalla + "): el tamaño de las letras puede tener error.");
        if (Boolean.FALSE.equals(pantallaCalibradaCoincide))
            avisos.add("La prueba se mostró en una pantalla distinta a la calibrada.");
        if (metodoDistancia == MetodoDistancia.SIN_CAMARA)
            avisos.add("Distancia sin verificar por cámara: depende de la colocación del operador.");
        else if (!camaraCalibrada)
            avisos.add("Cámara sin calibrar con cinta: la distancia es aproximada (±15 %).");
        if (metodoDistancia != MetodoDistancia.SIN_CAMARA && fuenteDp == FuenteDp.PROMEDIO_ADULTO)
            avisos.add("Edad desconocida: la distancia se estimó con la distancia interpupilar de un adulto promedio.");
        if (!brilloConfirmado)
            avisos.add("No se confirmó el brillo de la pantalla al máximo y sin reflejos.");
        if (correccion == Correccion.NO_REGISTRADO)
            avisos.add("No se registró si la persona usa lentes: no se sabe si el resultado es con o sin corrección.");
        if (correccion == Correccion.SIN_SUS_LENTES)
            avisos.add("Usa lentes pero se evaluó sin ellos: el resultado no refleja su visión corregida; repetir con sus lentes.");
        if (!fuenteInstalada)
            avisos.add("La tipografía de optotipos (" + fuenteOptotipo + ") no está instalada; se usó una sustituta.");
        return avisos;
    }

    /** Texto breve, una línea por dato, para el informe y las observaciones. */
    public String resumen() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US, "Pantalla: %s PPI (%s, escalado %.0f %%). ",
                Double.isFinite(ppi) ? String.format(Locale.US, "%.1f", ppi) : "N/D", metodoPantalla, escalaPantalla * 100));
        sb.append(String.format(Locale.US, "Distancia objetivo: %s; control: %s",
                Double.isFinite(distanciaObjetivoM) ? String.format(Locale.US, "%.1f m", distanciaObjetivoM) : "N/D", metodoDistancia));
        if (metodoDistancia != MetodoDistancia.SIN_CAMARA) {
            sb.append(camaraCalibrada ? " calibrada" : " sin calibrar");
            if (Double.isFinite(distanciaRealPromedioM))
                sb.append(String.format(Locale.US, "; distancia real %.2f ± %.2f m", distanciaRealPromedioM,
                        Double.isFinite(distanciaRealDesviacionM) ? distanciaRealDesviacionM : 0.0));
            if (Double.isFinite(dpUsadaMm))
                sb.append(String.format(Locale.US, "; DP %.1f mm (%s)", dpUsadaMm, fuenteDp));
        }
        sb.append(". Lentes: ").append(correccion);
        sb.append(". Optotipo: ").append(tipoOptotipo);
        if (Double.isFinite(logMarFilaInicial) && logMarFilaInicial < 1.0 - 1e-9)
            sb.append(String.format(Locale.US, ". Fila inicial LogMAR %.1f (las más grandes no cabían)", logMarFilaInicial));
        if (Double.isFinite(logMarLimitePantalla))
            sb.append(String.format(Locale.US, ". Límite medible de la pantalla: LogMAR %.1f", logMarLimitePantalla));
        sb.append(brilloConfirmado ? ". Brillo confirmado." : ". Brillo no confirmado.");
        return sb.toString();
    }
}
