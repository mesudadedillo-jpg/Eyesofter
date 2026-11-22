package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import javafx.stage.Screen;

import java.util.Locale;

/**
 * Identifica una pantalla por su resolución física, posición y escalado. El PPI
 * medido con la tarjeta pertenece a una pantalla concreta: si la prueba se muestra
 * en otra (un proyector, un segundo monitor), las letras tendrían otro tamaño.
 */
public final class IdentidadPantalla {

    private IdentidadPantalla() { }

    public static String de(Screen pantalla) {
        if (pantalla == null) return "";
        double escala = pantalla.getOutputScaleX() > 0 ? pantalla.getOutputScaleX() : 1.0;
        return String.format(Locale.US, "%.0fx%.0f@%.0f,%.0f/%.2f",
                pantalla.getBounds().getWidth() * escala, pantalla.getBounds().getHeight() * escala,
                pantalla.getBounds().getMinX(), pantalla.getBounds().getMinY(), escala);
    }

    /** null si nunca se registró una pantalla calibrada; si no, si coincide con la dada. */
    public static Boolean coincideConCalibrada(Screen pantalla) {
        if (AppConfig.PANTALLA_CALIBRADA == null || AppConfig.PANTALLA_CALIBRADA.isBlank()) return null;
        return AppConfig.PANTALLA_CALIBRADA.equals(de(pantalla));
    }

    /** Pantalla donde está una ventana (o la principal si no se puede saber). */
    public static Screen deVentana(javafx.stage.Window ventana) {
        if (ventana == null || Double.isNaN(ventana.getX()) || ventana.getWidth() <= 0) return Screen.getPrimary();
        return Screen.getScreensForRectangle(ventana.getX(), ventana.getY(), ventana.getWidth(), ventana.getHeight())
                .stream().findFirst().orElse(Screen.getPrimary());
    }
}
