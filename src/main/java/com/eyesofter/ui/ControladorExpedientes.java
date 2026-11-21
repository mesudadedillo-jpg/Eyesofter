package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;

/** Consulta local de evaluaciones previamente almacenadas. */
public class ControladorExpedientes implements ControladorDeVista {
    @FXML private TableView<GestorBaseDatos.EvaluacionInfo> tablaExpedientes;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colPaciente;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colFecha;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colAgudeza;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colCromatico;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colRecomendacion;
    private Stage stage;

    @Override public void setStage(Stage stage) {
        this.stage = stage;
        colPaciente.setCellValueFactory(dato -> new SimpleStringProperty(valor(dato.getValue().nombrePaciente)));
        colFecha.setCellValueFactory(dato -> new SimpleStringProperty(valor(dato.getValue().fechaHora)));
        colAgudeza.setCellValueFactory(dato -> new SimpleStringProperty(valor(dato.getValue().agudezaIzq)));
        colCromatico.setCellValueFactory(dato -> new SimpleStringProperty(valor(dato.getValue().resultadoIshihara)));
        colRecomendacion.setCellValueFactory(dato -> new SimpleStringProperty(valor(dato.getValue().recomendacion)));
        actualizar();
        Timeline actualizacion = new Timeline(new KeyFrame(javafx.util.Duration.seconds(2), e -> actualizar()));
        actualizacion.setCycleCount(Timeline.INDEFINITE); actualizacion.play();
    }

    @FXML private void actualizar() {
        tablaExpedientes.getItems().setAll(GestorBaseDatos.obtenerHistorialEvaluaciones());
    }

    @FXML private void volver() {
        Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
    }

    private String valor(String texto) { return texto == null || texto.isBlank() ? "—" : texto; }
}
