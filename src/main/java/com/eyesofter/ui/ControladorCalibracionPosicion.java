package com.eyesofter.ui;

import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.config.AppConfig;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Presenta en tiempo real la distancia estimada y habilita la prueba en rango. */
public class ControladorCalibracionPosicion implements ControladorDeVista {
    @FXML private Label lblObjetivo;
    @FXML private Label lblDistanciaActual;
    @FXML private Label lblIndicacion;
    @FXML private Label lblEstadoCamara;
    @FXML private Circle luzRoja;
    @FXML private Circle luzAmarilla;
    @FXML private Circle luzVerde;
    @FXML private Button btnContinuar;

    private Stage stage;
    private DetectorDistancia detector;
    private Timeline lectura;

    public static void mostrarVentana(Stage stage) {
        // Al usar Navegador, la transición SPA suave ya se aplica automáticamente
        Navegador.mostrar(stage, "/fxml/CalibracionPosicion.fxml", "EYESOFTER · Calibración de posición");
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        double distancia = SesionEvaluacion.getDistanciaMetros();
        lblObjetivo.setText(String.format("Distancia objetivo: %.1f m", distancia));
        // La cámara es opcional. No se abre automáticamente porque algunos
        // controladores de Windows devuelven errores RPC al reservarla.
        btnContinuar.setDisable(false);
        lblEstadoCamara.setText("Medición por cámara opcional.");
        lblIndicacion.setText("Puede iniciar el tamizaje en modo manual o activar la cámara con “Reintentar cámara”.");
    }

    private void iniciarDetector() {
        // Incluso mientras se intenta conectar la cámara el flujo clínico no
        // queda bloqueado: el tamizaje manual es una alternativa válida.
        btnContinuar.setDisable(false);
        lblEstadoCamara.setText("Conectando con la cámara…");

        Task<DetectorDistancia> tarea = new Task<>() {
            @Override
            protected DetectorDistancia call() throws Exception {
                // Si la ruta o la DLL fallan, lanzará una excepción aquí
                DetectorDistancia nuevo = new DetectorDistancia(AppConfig.RUTA_MODELO_OPENCV);
                nuevo.setDistanciaObjetivo(SesionEvaluacion.getDistanciaMetros() * 100.0);
                return nuevo;
            }
        };

        tarea.setOnSucceeded(evento -> {
            detector = tarea.getValue();
            lblEstadoCamara.setText("Cámara activa. Mantenga el rostro centrado en el encuadre.");
            iniciarLectura();
        });

        tarea.setOnFailed(evento -> {
            // ¡FUSIÓN CLAVE! Ahora imprimimos la causa real del fallo en la consola
            Throwable excepcion = tarea.getException();
            System.err.println("❌ [DEBUG] ERROR AL INICIAR LA CÁMARA (CALIBRACIÓN):");
            if (excepcion != null) excepcion.printStackTrace();

            lblEstadoCamara.setText("No fue posible iniciar la cámara. Verifique el equipo y reintente.");
            lblEstadoCamara.getStyleClass().setAll("estado", "estado-error");
        });

        Thread hilo = new Thread(tarea, "calibracion-distancia");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void iniciarLectura() {
        lectura = new Timeline(new KeyFrame(Duration.millis(700), evento -> {
            if (detector == null) return;

            // Timeline ya se ejecuta en el hilo de JavaFX, así que procesamos y actualizamos directamente
            boolean enRango = detector.validarPosicionPaciente();
            double cm = detector.getUltimaDistanciaCm();

            actualizarLectura(enRango, cm);
        }));
        lectura.setCycleCount(Timeline.INDEFINITE);
        lectura.play();
    }

    private void actualizarLectura(boolean enRango, double centimetros) {
        apagarLuces();

        if (centimetros < 0) {
            luzRoja.setStyle("-fx-fill: #D64545;");
            lblDistanciaActual.setText("Sin medición");
            lblIndicacion.setText("Ubique el rostro frente a la cámara para continuar.");
            btnContinuar.setDisable(false);
            return;
        }

        lblDistanciaActual.setText(String.format("%.2f m", centimetros / 100.0));
        double diferencia = Math.abs(centimetros / 100.0 - SesionEvaluacion.getDistanciaMetros());

        if (enRango) {
            luzVerde.setStyle("-fx-fill: #2FAE6B;");
            lblIndicacion.setText("Posición dentro del rango configurado. Puede iniciar el tamizaje.");
            lblIndicacion.getStyleClass().setAll("mensaje-calibracion", "texto-exito");
            btnContinuar.setDisable(false);

        } else if (diferencia <= 0.35) {
            luzAmarilla.setStyle("-fx-fill: #E6A23C;");
            lblIndicacion.setText(centimetros / 100.0 > SesionEvaluacion.getDistanciaMetros()
                    ? "Acérquese ligeramente al monitor." : "Aléjese ligeramente del monitor.");
            lblIndicacion.getStyleClass().setAll("mensaje-calibracion", "texto-alerta");
            btnContinuar.setDisable(false);

        } else {
            luzRoja.setStyle("-fx-fill: #D64545;");
            lblIndicacion.setText(centimetros / 100.0 > SesionEvaluacion.getDistanciaMetros()
                    ? "Acérquese al monitor para alcanzar la distancia objetivo." : "Aléjese del monitor para alcanzar la distancia objetivo.");
            lblIndicacion.getStyleClass().setAll("mensaje-calibracion", "texto-error");
            btnContinuar.setDisable(false);
        }
    }

    private void apagarLuces() {
        luzRoja.setStyle("-fx-fill: #F7D8D8;");
        luzAmarilla.setStyle("-fx-fill: #FCEBC4;");
        luzVerde.setStyle("-fx-fill: #D5F0E1;");
    }

    @FXML
    private void reintentar() {
        if (lectura != null) lectura.stop();
        if (detector != null) detector.apagarCamara();
        detector = null;
        iniciarDetector();
    }

    @FXML
    private void iniciarTamizaje() {
        if (lectura != null) lectura.stop();
        SesionEvaluacion.conservarDetectorDistancia(detector);
        detector = null;

        ControladorFaseEvaluacion.mostrar(stage, SesionEvaluacion.FaseEvaluacion.AGUDEZA_AMBOS);
    }

    @FXML
    private void volver() {
        if (lectura != null) lectura.stop();
        if (detector != null) detector.apagarCamara();
        SesionEvaluacion.liberarDetectorDistancia();

        ControladorVerificacionPerifericos.mostrarVentana(stage);
    }
}
