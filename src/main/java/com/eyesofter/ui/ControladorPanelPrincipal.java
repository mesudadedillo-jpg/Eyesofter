package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.time.LocalDate;

/** Acciones del panel principal para el personal operador. */
public class ControladorPanelPrincipal implements ControladorDeVista {
    private Stage stage;
    @FXML private Label lblFecha;
    @FXML private Label lblTotalPacientes;
    @FXML private Label lblTotalEvaluaciones;
    @FXML private Label lblPendientes;

    @Override public void setStage(Stage stage) {
        this.stage = stage;

        actualizarResumen();
    }

    @FXML private void iniciarNuevaSesion() {
        ControladorRegistro.mostrarVentana(stage);
    }

    @FXML private void consultarExpedientes() {
        Navegador.mostrar(stage, "/fxml/Expedientes.fxml", "EYESOFTER · Expedientes de tamizaje");
    }

    @FXML private void abrirAjustes() {
        ControladorConfiguracion.mostrarVentana(stage, "", "", "");
    }

    @FXML private void verificarPerifericos() {
        ControladorVerificacionPerifericos.mostrarVentana(stage);
    }

    @FXML private void cerrarSesion() {
        Navegador.mostrar(stage, "/fxml/Acceso.fxml", "EYESOFTER · Iniciar sesión");
    }

    private void actualizarResumen() {
        lblFecha.setText(LocalDate.now().toString());
        lblTotalPacientes.setText(String.valueOf(GestorBaseDatos.contarPacientes()));
        lblTotalEvaluaciones.setText(String.valueOf(GestorBaseDatos.contarEvaluaciones()));
        lblPendientes.setText("0");
    }
}
