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
    @FXML private Label lblSemaforo;
    @FXML private Label lblEnvio;
    @FXML private Button btnMatricula;
    private javafx.animation.Timeline seguimientoEnvio;
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
        mostrarSemaforo();
        iniciarSeguimientoEnvio();
    }

    /** C-RC-10: el semáforo no depende solo del color; lleva ícono y texto. */
    private void mostrarSemaforo() {
        String s = com.eyesofter.sincronizacion.ColaSincronizacion.semaforo(SesionEvaluacion.getResultadoClinico());
        switch (s) {
            case "VERDE" -> { lblSemaforo.setText("●  Verde · Saludable"); lblSemaforo.getStyleClass().add("semaforo-verde"); }
            case "AMARILLO" -> { lblSemaforo.setText("▲  Amarillo · En observación"); lblSemaforo.getStyleClass().add("semaforo-amarillo"); }
            default -> { lblSemaforo.setText("■  Rojo · Atención urgente requerida"); lblSemaforo.getStyleClass().add("semaforo-rojo"); }
        }
        lblSemaforo.setAccessibleText("Resultado del semáforo: " + lblSemaforo.getText());
    }

    private void iniciarSeguimientoEnvio() {
        String folio = SesionEvaluacion.getFolioEnvio();
        if (folio == null) { lblEnvio.setText("El resultado se guardó solo en este equipo."); return; }
        actualizarEnvio(folio);
        seguimientoEnvio = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2), e -> actualizarEnvio(folio)));
        seguimientoEnvio.setCycleCount(30);
        seguimientoEnvio.play();
    }

    private void actualizarEnvio(String folio) {
        Task<com.eyesofter.sincronizacion.ColaSincronizacion.Envio> t = new Task<>() {
            @Override protected com.eyesofter.sincronizacion.ColaSincronizacion.Envio call() {
                return com.eyesofter.sincronizacion.ColaSincronizacion.buscar(folio);
            }
        };
        t.setOnSucceeded(e -> {
            var envio = t.getValue();
            boolean pedirMatricula = false;
            if (!com.eyesofter.sincronizacion.ClienteSuite.estaConfigurado()) {
                lblEnvio.setText("⚠ Guardado en este equipo. Vincule este equipo con el portal (Configuración) para que el tutor reciba el aviso.");
            } else if (envio == null || envio.estado() == com.eyesofter.sincronizacion.ColaSincronizacion.Estado.PENDIENTE) {
                lblEnvio.setText("⟳ Enviando al portal… " + (envio != null && envio.ultimoError() != null ? "(" + envio.ultimoError() + " Se reintentará solo.)" : ""));
            } else if (envio.estado() == com.eyesofter.sincronizacion.ColaSincronizacion.Estado.ENVIADO) {
                lblEnvio.setText("✓ Enviado al portal" + (envio.casoFolio() != null ? " · caso " + envio.casoFolio() : "") + ". El tutor recibirá el aviso.");
                if (seguimientoEnvio != null) seguimientoEnvio.stop();
            } else {
                lblEnvio.setText("⚠ " + envio.ultimoError());
                pedirMatricula = true;
            }
            btnMatricula.setVisible(pedirMatricula);
            btnMatricula.setManaged(pedirMatricula);
        });
        Thread hilo = new Thread(t, "estado-envio");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML private void indicarMatricula() {
        DialogoMatricula.pedir(stage, SesionEvaluacion.getCurpPaciente(), SesionEvaluacion.getNombrePaciente()).ifPresent(m -> {
            lblEnvio.setText("⟳ Reintentando el envío…");
            if (seguimientoEnvio != null) { seguimientoEnvio.stop(); seguimientoEnvio.playFromStart(); }
        });
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
        if (seguimientoEnvio != null) seguimientoEnvio.stop();
        SesionEvaluacion.cerrar();
        Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
    }
}
