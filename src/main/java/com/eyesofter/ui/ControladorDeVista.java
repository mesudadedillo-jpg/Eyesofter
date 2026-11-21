package com.eyesofter.ui;

import javafx.stage.Stage;

/** Contrato común para las vistas FXML que requieren navegar sobre el mismo Stage. */
public interface ControladorDeVista {
    void setStage(Stage stage);
}
