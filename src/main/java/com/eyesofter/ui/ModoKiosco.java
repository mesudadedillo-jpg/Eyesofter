package com.eyesofter.ui;

import javafx.application.Platform;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;

/**
 * Configura la ventana principal como una aplicación de escritorio única, siempre en
 * pantalla completa.
 *
 * <p>JavaFX sale de la pantalla completa con Esc por defecto. Como la ventana no tiene
 * bordes ni se puede redimensionar, al pulsar Esc quedaba pequeña y sin forma de
 * agrandarla. Ahora Esc no sale de la pantalla completa (sigue sirviendo para "volver"
 * dentro de la aplicación) y, si algo la saca, se restaura sola.</p>
 */
public final class ModoKiosco {
    private static Stage instalado;
    private ModoKiosco() { }

    public static void aplicar(Stage stage) {
        stage.setFullScreenExitHint("");
        stage.setFullScreenExitKeyCombination(KeyCombination.NO_MATCH);
        if (!stage.isFullScreen()) stage.setFullScreen(true);
        if (instalado != stage) {
            instalado = stage;
            stage.iconifiedProperty().addListener((obs, anterior, minimizada) -> {
                if (minimizada) stage.setIconified(false);
            });
            stage.fullScreenProperty().addListener((obs, antes, completa) -> {
                if (!completa && stage.isShowing()) Platform.runLater(() -> stage.setFullScreen(true));
            });
        }
        stage.setIconified(false);
    }
}
