package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import javafx.scene.Scene;

/** Tema global persistente para que no se pierda al cambiar de módulo. */
public final class TemaAplicacion {
    private static boolean oscuro;

    private TemaAplicacion() { }

    public static void cargar() {
        oscuro = AppConfig.TEMA_OSCURO;
    }

    public static void aplicar(Scene scene, boolean usarOscuro) {
        oscuro = usarOscuro;
        boolean cambio = AppConfig.TEMA_OSCURO != oscuro;
        AppConfig.TEMA_OSCURO = oscuro;
        if (cambio) AppConfig.guardar();
        if (scene == null || scene.getRoot() == null) return;
        var clases = scene.getRoot().getStyleClass();
        if (oscuro && !clases.contains("tema-oscuro")) clases.add("tema-oscuro");
        if (!oscuro) clases.remove("tema-oscuro");
    }

    public static void aplicar(Scene scene) { aplicar(scene, oscuro); }
    public static boolean esOscuro() { return oscuro; }
}
