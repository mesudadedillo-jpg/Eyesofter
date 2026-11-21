package com.eyesofter.ui;

import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.config.AppConfig;
import com.eyesofter.voz.DispositivosAudio;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;

import java.util.List;

/** Comprueba que el hardware requerido responda antes de comenzar el tamizaje. */
public class ControladorVerificacionPerifericos implements ControladorDeVista {
    @FXML private Label lblEstadoCamara;
    @FXML private Label lblEstadoMicrofono;
    @FXML private Label lblDetalleCamara;
    @FXML private Label lblNivelAudio;
    @FXML private ProgressBar barraNivelAudio;
    @FXML private ComboBox<String> cmbMicrofonos;
    @FXML private ComboBox<String> cmbCamaras;
    @FXML private Button btnContinuar;
    @FXML private Button btnVerificar;
    @FXML private Button btnPermisoCamara;
    @FXML private Button btnPermisoMicrofono;
    @FXML private Button btnVolver;

    private Stage stage;
    private boolean camaraDisponible;
    private boolean microfonoDisponible;
    private String detalleCamara;
    private String detalleMicrofono;

    public static void mostrarVentana(Stage stage) {
        Navegador.mostrar(stage, "/fxml/VerificacionPerifericos.fxml", "EYESOFTER · Verificación de periféricos");
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        habilitarControles();
        // La enumeración de audio puede quedar esperando a un controlador de Windows.
        // Dejar una opción utilizable y ejecutar esa consulta fuera del hilo gráfico.
        cmbMicrofonos.getItems().setAll("Micrófono predeterminado");
        cmbMicrofonos.setValue("Micrófono predeterminado");
        cmbCamaras.getItems().setAll("Cámara 0 (predeterminada)");
        cmbCamaras.setValue("Cámara " + AppConfig.INDICE_CAMARA_PREFERIDA
                + (AppConfig.INDICE_CAMARA_PREFERIDA == 0 ? " (predeterminada)" : ""));
        lblEstadoCamara.setText("Pendiente");
        lblEstadoMicrofono.setText("Pendiente");
        lblDetalleCamara.setText("Pulse “Verificar de nuevo” para comprobar la cámara.");
        lblNivelAudio.setText("Pulse “Verificar de nuevo” para comprobar el micrófono.");
        cargarMicrofonosEnSegundoPlano();
        cargarCamarasEnSegundoPlano();
    }

    /** Esta pantalla nunca bloquea la navegación por el estado del hardware. */
    private void habilitarControles() {
        for (Button boton : new Button[]{btnContinuar, btnVerificar, btnPermisoCamara, btnPermisoMicrofono, btnVolver}) {
            boton.setDisable(false);
            boton.setMouseTransparent(false);
        }
        cmbMicrofonos.setDisable(false);
        cmbMicrofonos.setMouseTransparent(false);
        cmbCamaras.setDisable(false);
        cmbCamaras.setMouseTransparent(false);
    }

    private void cargarCamarasEnSegundoPlano() {
        Task<List<Integer>> tarea = new Task<>() {
            @Override protected List<Integer> call() { return DetectorDistancia.listarCamarasDisponibles(); }
        };
        tarea.setOnSucceeded(evento -> {
            List<Integer> indices = tarea.getValue();
            if (indices == null || indices.isEmpty()) {
                cmbCamaras.getItems().setAll("No se detectaron cámaras");
                cmbCamaras.setValue("No se detectaron cámaras");
                return;
            }
            List<String> opciones = indices.stream().map(this::nombreCamara).toList();
            cmbCamaras.getItems().setAll(opciones);
            int preferida = AppConfig.INDICE_CAMARA_PREFERIDA;
            cmbCamaras.setValue(indices.contains(preferida) ? nombreCamara(preferida) : opciones.get(0));
            seleccionarCamara();
        });
        tarea.setOnFailed(evento -> lblDetalleCamara.setText("No se pudieron enumerar cámaras; pruebe la cámara predeterminada."));
        Thread hilo = new Thread(tarea, "deteccion-camaras-perifericos");
        hilo.setDaemon(true);
        hilo.start();
    }

    private String nombreCamara(int indice) {
        return "Cámara " + indice + (indice == 0 ? " (predeterminada)" : "");
    }

    private void cargarMicrofonosEnSegundoPlano() {
        Task<List<String>> tarea = new Task<>() {
            @Override protected List<String> call() { return DispositivosAudio.listarMicrofonos(); }
        };
        tarea.setOnSucceeded(evento -> {
            List<String> dispositivos = tarea.getValue();
            if (dispositivos == null || dispositivos.isEmpty()) {
                cmbMicrofonos.setPromptText("No se detectaron micrófonos; se usará el predeterminado");
                return;
            }
            cmbMicrofonos.getItems().setAll(dispositivos);
            String preferido = AppConfig.DISPOSITIVO_AUDIO_PREFERIDO;
            cmbMicrofonos.setValue(dispositivos.contains(preferido) ? preferido : dispositivos.get(0));
            DispositivosAudio.seleccionarMicrofono(cmbMicrofonos.getValue());
        });
        tarea.setOnFailed(evento -> System.err.println("[PERIFÉRICOS] No se pudieron enumerar micrófonos: " + tarea.getException()));
        Thread hilo = new Thread(tarea, "deteccion-microfonos-perifericos");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML
    public void verificarHardware() {
        habilitarControles();
        lblEstadoCamara.setText("En verificación");
        lblEstadoMicrofono.setText("En verificación");
        lblDetalleCamara.setText("Iniciando conexión con la cámara…");
        lblNivelAudio.setText("Midiendo señal de entrada…");
        barraNivelAudio.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        Task<Void> comprobacion = new Task<>() {
            @Override protected Void call() {
                comprobarCamara();
                comprobarMicrofono();
                return null;
            }
        };
        comprobacion.setOnSucceeded(evento -> actualizarEstado());
        comprobacion.setOnFailed(evento -> actualizarEstado());
        Thread hilo = new Thread(comprobacion, "verificacion-perifericos");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void comprobarCamara() {
        DetectorDistancia detector = null;
        try {
            detector = new DetectorDistancia(AppConfig.RUTA_MODELO_OPENCV);
            camaraDisponible = detector.puedeCapturarFotograma();
            detalleCamara = camaraDisponible
                    ? "Cámara detectada y capturando imagen. La medición de distancia estará disponible durante la calibración."
                    : "La cámara se abrió, pero no entregó imagen. Revise permisos, conexión y que otra aplicación no la esté usando.";
        } catch (Exception ex) {
            camaraDisponible = false;
            detalleCamara = "No fue posible acceder a la cámara. Revise el permiso y la conexión física.";
            System.err.println("❌ [DEBUG] ERROR DE CÁMARA DETALLADO:");
            ex.printStackTrace();
        } finally {
            if (detector != null) detector.apagarCamara();
        }
    }

    private void comprobarMicrofono() {
        try (DispositivosAudio.Captura captura = DispositivosAudio.abrirCaptura()) {
            captura.iniciar();
            microfonoDisponible = true;
            detalleMicrofono = "Micrófono activo: " + captura.getNombreDispositivo() + ".";
        } catch (Exception ex) {
            microfonoDisponible = false;
            detalleMicrofono = "No fue posible abrir el micrófono seleccionado. Revise el permiso de privacidad de Windows.";
            System.err.println("❌ [DEBUG] ERROR DE MICRÓFONO DETALLADO:");
            ex.printStackTrace();
        }
    }

    private void actualizarEstado() {
        barraNivelAudio.setProgress(microfonoDisponible ? 0.35 : 0.0);
        lblEstadoCamara.setText(camaraDisponible ? "Disponible" : "No disponible");
        lblEstadoMicrofono.setText(microfonoDisponible ? "Disponible" : "No disponible");
        lblEstadoCamara.getStyleClass().setAll("estado", camaraDisponible ? "estado-exito" : "estado-error");
        lblEstadoMicrofono.getStyleClass().setAll("estado", microfonoDisponible ? "estado-exito" : "estado-error");
        lblDetalleCamara.setText(detalleCamara);
        lblNivelAudio.setText(detalleMicrofono);
        // El botón siempre queda disponible: el hardware puede configurarse después
        // y el usuario debe poder continuar o volver sin quedar atrapado en esta vista.
        habilitarControles();
    }

    @FXML
    public void seleccionarMicrofono() {
        String nombre = cmbMicrofonos.getValue();
        if (nombre == null) return;
        DispositivosAudio.seleccionarMicrofono(nombre);
        AppConfig.guardar();
        verificarHardware();
    }

    @FXML
    public void seleccionarCamara() {
        String opcion = cmbCamaras.getValue();
        if (opcion == null || !opcion.startsWith("Cámara ")) return;
        try {
            AppConfig.INDICE_CAMARA_PREFERIDA = Integer.parseInt(opcion.substring("Cámara ".length()).trim().split(" ")[0]);
            AppConfig.guardar();
        } catch (NumberFormatException ignored) { }
    }

    @FXML
    public void solicitarPermisoCamara() {
        abrirConfiguracionPrivacidad("ms-settings:privacy-webcam", "cámara");
    }

    @FXML
    public void solicitarPermisoMicrofono() {
        abrirConfiguracionPrivacidad("ms-settings:privacy-microphone", "micrófono");
    }

    private void abrirConfiguracionPrivacidad(String uri, String dispositivo) {
        try {
            new ProcessBuilder("cmd", "/c", "start", "", uri).start();
            lblDetalleCamara.setText("Se abrió la configuración de privacidad de Windows para " + dispositivo
                    + ". Active el acceso para aplicaciones de escritorio y después vuelva a verificar.");
        } catch (Exception ex) {
            lblDetalleCamara.setText("No se pudo abrir la configuración de Windows. Busque “Privacidad de "
                    + dispositivo + "” en Configuración.");
        }
    }

    @FXML
    public void continuar() {
        if (!camaraDisponible || !microfonoDisponible) {
            javafx.scene.control.Alert aviso = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Puedes continuar; la prueba funcionará en modo manual si algún periférico no está disponible.", javafx.scene.control.ButtonType.OK);
            aviso.setHeaderText("Periféricos pendientes"); aviso.showAndWait();
        }
        if (SesionEvaluacion.getCurpPaciente().isBlank()) {
            Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
        } else {
            // Pasamos a la bienvenida interactiva para el alumno
            ControladorBienvenidaPaciente.mostrarVentana(stage, SesionEvaluacion.getNombrePaciente());
        }
    }

    @FXML
    public void volver() {
        Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
    }
}
