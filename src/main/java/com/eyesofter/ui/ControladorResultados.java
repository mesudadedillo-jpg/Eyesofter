package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.reportes.GeneradorPDF;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.io.File;

/** Cierre de la sesión y generación controlada del expediente PDF. */
public class ControladorResultados implements ControladorDeVista {
    @FXML private Label lblPaciente;
    @FXML private Label lblSnellen;
    @FXML private Label lblIshihara;
    @FXML private Label lblRecomendacion;
    @FXML private Label lblEstadoDocumento;
    @FXML private Button btnGenerarPdf;
    private Stage stage;
    private File expedienteGenerado;

    public static void mostrarVentana(Stage stage) {
        Navegador.mostrar(stage, "/fxml/Resultados.fxml", "EYESOFTER · Resultados de tamizaje");
    }

    @Override public void setStage(Stage stage) {
        this.stage = stage;
        lblPaciente.setText(SesionEvaluacion.getNombrePaciente().isBlank()
                ? "Paciente sin identificación" : SesionEvaluacion.getNombrePaciente());
        lblSnellen.setText(SesionEvaluacion.getResultadoSnellen());
        lblIshihara.setText(SesionEvaluacion.getResultadoIshihara());
        lblRecomendacion.setText(SesionEvaluacion.getRecomendacion());
    }

    @FXML private void generarExpediente() {
        btnGenerarPdf.setDisable(true);
        lblEstadoDocumento.setText("Generando expediente clínico…");
        Task<File> tarea = new Task<>() {
            @Override protected File call() throws Exception {
                GestorBaseDatos.PacienteInfo paciente = GestorBaseDatos.buscarPacientePorCurp(SesionEvaluacion.getCurpPaciente());
                GestorBaseDatos.EvaluacionInfo evaluacion = GestorBaseDatos.obtenerUltimaEvaluacion(SesionEvaluacion.getCurpPaciente());
                return GeneradorPDF.generarReporte(paciente, evaluacion);
            }
        };
        tarea.setOnSucceeded(evento -> {
            expedienteGenerado = tarea.getValue();
            lblEstadoDocumento.setText("Expediente generado: " + expedienteGenerado.getName());
            btnGenerarPdf.setText("Expediente generado");
        });
        tarea.setOnFailed(evento -> {
            lblEstadoDocumento.setText("No fue posible generar el expediente: " + tarea.getException().getMessage());
            btnGenerarPdf.setDisable(false);
        });
        Thread hilo = new Thread(tarea, "generar-expediente");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML private void finalizar() {
        SesionEvaluacion.cerrar();
        Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
    }
}
