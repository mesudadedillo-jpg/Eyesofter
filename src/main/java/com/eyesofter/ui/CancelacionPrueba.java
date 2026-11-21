package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

/** Cancela una sesión sin registrar resultados parciales como evaluación terminada. */
final class CancelacionPrueba {
    private CancelacionPrueba() { }
    static void solicitar(Stage stage) {
        solicitar(stage, null);
    }
    static void solicitar(Stage stage, Runnable alConfirmar) {
        Alert aviso = new Alert(Alert.AlertType.CONFIRMATION,
                "La prueba actual se cancelará y no se guardará como terminada. ¿Deseas continuar?", ButtonType.CANCEL, ButtonType.OK);
        aviso.setHeaderText("Cancelar prueba");
        aviso.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            if (alConfirmar != null) alConfirmar.run();
            GestorBaseDatos.obtenerCitasPaciente(SesionEvaluacion.getCurpPaciente()).stream()
                    .filter(c -> "EN_EVALUACION".equalsIgnoreCase(c.estado))
                    .findFirst().ifPresent(c -> GestorBaseDatos.actualizarEstadoCita(c.idCita, "EN_ESPERA"));
            SesionEvaluacion.cerrar();
            Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
        });
    }
}
