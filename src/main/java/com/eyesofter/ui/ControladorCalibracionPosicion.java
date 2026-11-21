package com.eyesofter.ui;

import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.config.AppConfig;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import com.eyesofter.voz.ReconocedorOptotipos;

import java.io.ByteArrayInputStream;
import java.util.Locale;
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
    @FXML private Button btnCamara;
    @FXML private Button btnCalibrar;
    @FXML private ImageView vistaCamara;
    @FXML private Label lblSinVista;

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
        lblObjetivo.setText(String.format(Locale.US, "Distancia objetivo: %.1f m", distancia));
        apagarLuces();
        // La cámara es opcional. No se abre automáticamente porque algunos
        // controladores de Windows devuelven errores RPC al reservarla.
        btnContinuar.setDisable(false);
        lblEstadoCamara.setText("Medición por cámara opcional.");
        lblIndicacion.setText("Puede iniciar el tamizaje sin cámara o activarla para vigilar la distancia automáticamente.");
        // Mientras se coloca a la persona, el modelo de voz se carga en segundo plano.
        ReconocedorOptotipos.precargarEnSegundoPlano();
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
                nuevo.setDpPacienteMm(SesionEvaluacion.getDpPaciente());
                return nuevo;
            }
        };

        tarea.setOnSucceeded(evento -> {
            detector = tarea.getValue();
            btnCamara.setText("📷 Reiniciar cámara");
            btnCalibrar.setDisable(false);
            String metodo = detector.getMetodo() == DetectorDistancia.Metodo.OJOS ? "" : " (método de respaldo: falta el modelo YuNet)";
            lblEstadoCamara.setText(detector.isFocalCalibrada()
                    ? String.format(Locale.US, "Cámara activa y calibrada%s. DP usada: %.1f mm (%s).", metodo,
                        SesionEvaluacion.getDpPaciente(), SesionEvaluacion.getFuenteDp())
                    : "Cámara activa SIN calibrar" + metodo + ": la distancia es aproximada (±15 %). Coloque a la persona a la distancia objetivo, "
                        + "mídala con cinta y presione “Calibrar a esta distancia”. Sin cinta: 4 hojas tamaño carta a lo largo miden 1.12 m.");
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

    /** La lectura se hace en el hilo de visión; la interfaz solo pinta el resultado. */
    private void iniciarLectura() {
        lectura = new Timeline(new KeyFrame(Duration.millis(400), evento -> {
            DetectorDistancia actual = detector;
            if (actual == null) return;
            actual.evaluarEnSegundoPlano(condicion -> {
                if (detector != actual) return;
                actualizarLectura(condicion == DetectorDistancia.CondicionPrueba.CORRECTA
                        || condicion == DetectorDistancia.CondicionPrueba.RUIDO_VISUAL, actual.getUltimaDistanciaCm());
                if (condicion == DetectorDistancia.CondicionPrueba.MULTIPLES_PERSONAS)
                    lblIndicacion.setText("Se detectan varias personas: solo la persona evaluada debe estar frente a la cámara.");
                byte[] jpg = actual.vistaPreviaJpg();
                if (jpg != null) {
                    vistaCamara.setImage(new Image(new ByteArrayInputStream(jpg)));
                    lblSinVista.setVisible(false);
                }
            });
        }));
        lectura.setCycleCount(Timeline.INDEFINITE);
        lectura.play();
    }

    /**
     * Calibración de la cámara: con la persona a una distancia medida con cinta,
     * se calcula la focal que hace coincidir la estimación con la realidad y se guarda.
     */
    @FXML
    private void calibrarDistancia() {
        if (detector == null) return;
        if (detector.getLecturasParaCalibrar() < 5) {
            lblIndicacion.setText("Aún no hay suficientes lecturas del rostro. Espere un par de segundos frente a la cámara.");
            return;
        }
        TextInputDialog dialogo = new TextInputDialog(String.format(Locale.US, "%.2f", SesionEvaluacion.getDistanciaMetros()));
        dialogo.setHeaderText("Calibrar la cámara");
        dialogo.setContentText("Distancia real medida con cinta (metros), de los ojos a la pantalla:");
        dialogo.showAndWait().ifPresent(texto -> {
            try {
                double metros = Double.parseDouble(texto.replace(',', '.').trim());
                if (metros < 0.3 || metros > 7) throw new NumberFormatException();
                if (detector.isFocalCalibrada() && !elegirRecalibrar()) {
                    medirDpDelPaciente(metros);
                    return;
                }
                double focal = detector.calibrarConDistanciaReal(metros * 100.0, SesionEvaluacion.getDpPaciente());
                if (focal <= 0) { lblIndicacion.setText("No se pudo calibrar: no se detecta el rostro con estabilidad."); return; }
                AppConfig.FOCAL_CAMARA_NORMALIZADA = focal;
                AppConfig.METODO_FOCAL_CAMARA = detector.getMetodo().name();
                AppConfig.guardar();
                SesionEvaluacion.registrarCalibracionConPaciente();
                lblEstadoCamara.setText(String.format(Locale.US,
                        "Cámara calibrada con DP %.1f mm (%s). Queda guardada para este equipo; para este paciente la distancia ya es exacta.",
                        SesionEvaluacion.getDpPaciente(), SesionEvaluacion.getFuenteDp()));
            } catch (NumberFormatException ex) {
                lblIndicacion.setText("Escriba una distancia válida en metros, por ejemplo 1.5");
            }
        });
    }

    /** Con la cámara ya calibrada se ofrece medir la DP del paciente (opción recomendada) o recalibrar. */
    private boolean elegirRecalibrar() {
        javafx.scene.control.ButtonType medir = new javafx.scene.control.ButtonType("Medir la DP de este paciente");
        javafx.scene.control.ButtonType recalibrar = new javafx.scene.control.ButtonType("Recalibrar la cámara del equipo");
        javafx.scene.control.Alert alerta = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "La cámara ya está calibrada. Con la distancia medida se puede calcular la distancia entre pupilas (DP) "
                        + "real de este paciente, así la distancia ya no depende de la tabla por edad.", medir, recalibrar);
        alerta.setHeaderText("¿Qué desea hacer con esta medición?");
        if (stage != null) alerta.initOwner(stage);
        return alerta.showAndWait().orElse(medir) == recalibrar;
    }

    private void medirDpDelPaciente(double metros) {
        double dp = detector.medirDpConDistanciaReal(metros * 100.0);
        if (!com.eyesofter.formulas.Antropometria.esDpPlausible(dp)) {
            lblIndicacion.setText("No se pudo medir la DP (lectura fuera de rango). Verifique que la persona mire de frente y repita.");
            return;
        }
        SesionEvaluacion.registrarDpMedidaConCinta(dp);
        detector.setDpPacienteMm(dp);
        lblEstadoCamara.setText(String.format(Locale.US, "DP del paciente medida: %.1f mm. La distancia de esta sesión usa este valor.", dp));
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

        lblDistanciaActual.setText(String.format(Locale.US, "%.2f m", centimetros / 100.0));
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
