package com.eyesofter.ui;

import javafx.stage.Stage;

/** Configura la ventana principal como una aplicación de escritorio única. */
public final class ModoKiosco {
    private static boolean bloqueoInstalado;
    private ModoKiosco() { }

    public static void aplicar(Stage stage) {
        // EYESOFTER opera permanentemente en modo kiosco. No se sustituye por
        // una ventana maximizada: eso permite que Windows la reduzca al cambiar
        // de vista y rompe el flujo de evaluación.
        stage.setResizable(false);
        stage.setFullScreenExitHint("");
        if (!stage.isFullScreen()) stage.setFullScreen(true);
        if (!bloqueoInstalado) {
            bloqueoInstalado = true;
            stage.iconifiedProperty().addListener((obs, anterior, minimizada) -> {
                if (minimizada) stage.setIconified(false);
            });
        }
        stage.setIconified(false);
    }
}
