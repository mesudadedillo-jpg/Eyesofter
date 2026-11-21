package com.eyesofter.ui;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/** El cierre siempre se confirma; la ventana no puede minimizarse. */
final class ConfirmacionSalida {
    private ConfirmacionSalida() { }
    static void mostrar(Stage stage) {
        Alert aviso = new Alert(Alert.AlertType.CONFIRMATION,
                "La aplicación se cerrará y la prueba activa se cancelará. ¿Deseas salir?", ButtonType.CANCEL, ButtonType.OK);
        aviso.setHeaderText("Salir de EYESOFTER");
        aviso.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> Platform.exit());
    }
}
