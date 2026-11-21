package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.TrigonometriaVisual;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.stage.Screen;
import javafx.stage.Stage;

/**
 * Controlador de Configuración Física y Entorno del Sistema.
 * Conectado a Configuracion.fxml
 */
public class ControladorConfiguracion {

    @FXML private void volver() {
        if (stagePrincipal == null) return;
        Navegador.volverAlPanelAdministrativo(stagePrincipal);
    }
    @FXML private void salirAplicacion() { if (stagePrincipal != null) ConfirmacionSalida.mostrar(stagePrincipal); }

    // --- ELEMENTOS INYECTADOS DESDE FXML ---
    @FXML private Label lblAlumno;
    @FXML private Slider sliderDistancia;
    @FXML private Label lblValorDistancia;
    @FXML private TextField txtPPI;
    @FXML private TextField txtFocalCamara;
    @FXML private Label lblResolucion;

    private Stage stagePrincipal;
    private String nombrePaciente;

    /**
     * Método estático para cargar y mostrar la ventana desde otros controladores.
     */
    public static void mostrarVentana(Stage stage, String curpPaciente, String nombrePaciente, String gradoGrupo) {
        try {
            FXMLLoader loader = new FXMLLoader(ControladorConfiguracion.class.getResource("/fxml/Configuracion.fxml"));
            Parent root = loader.load();

            // Obtenemos el controlador instanciado por JavaFX para pasarle los datos
            ControladorConfiguracion controlador = loader.getController();
            controlador.inicializarDatos(stage, curpPaciente, nombrePaciente, gradoGrupo);

            stage.setTitle("EYESOFTER · Configuración del entorno");
            Scene escena = new Scene(root);
            TemaAplicacion.aplicar(escena);
            stage.setScene(escena);
            ModoKiosco.aplicar(stage);

            stage.show();
        } catch (Exception e) {
            System.err.println("⚠️ Error al cargar Configuracion.fxml");
            e.printStackTrace();
        }
    }

    /**
     * Inicializa los datos visuales antes de que el usuario interactúe.
     */
    private void inicializarDatos(Stage stage, String curpPaciente, String nombrePaciente, String gradoGrupo) {
        this.stagePrincipal = stage;
        this.nombrePaciente = nombrePaciente;

        // Actualizamos el título con el nombre del paciente
        lblAlumno.setText(nombrePaciente == null || nombrePaciente.isBlank()
                ? "Configuración general del equipo"
                : "Paciente: " + nombrePaciente + " (" + gradoGrupo + ")");

        Screen pantalla = Screen.getPrimary();
        double anchoPx = pantalla.getBounds().getWidth() * pantalla.getOutputScaleX();
        double altoPx = pantalla.getBounds().getHeight() * pantalla.getOutputScaleY();
        double ppiDetectado = TrigonometriaVisual.calcularPPI(anchoPx, altoPx, AppConfig.MONITOR_DIAGONAL_PULGADAS);
        if (ppiDetectado <= 0) ppiDetectado = pantalla.getDpi();
        txtPPI.setText(String.valueOf(Math.round(ppiDetectado)));
        txtFocalCamara.setText(AppConfig.DISTANCIA_FOCAL_CAMARA_PX > 0
                ? String.format("%.2f", AppConfig.DISTANCIA_FOCAL_CAMARA_PX) : "");
        lblResolucion.setText(String.format("Resolución detectada: %.0f × %.0f px · diagonal configurada: %.1f in", anchoPx, altoPx, AppConfig.MONITOR_DIAGONAL_PULGADAS));

        // Configurar el evento dinámico del Slider para actualizar el texto
        sliderDistancia.valueProperty().addListener((obs, oldVal, newVal) -> {
            lblValorDistancia.setText(String.format("Distancia seleccionada: %.1f Metros", newVal.doubleValue()));
        });
    }

    /**
     * Acción conectada al botón que guarda el entorno y continúa al hardware.
     */
    @FXML
    private void guardarYContinuar() {
        double distanciaMetros = sliderDistancia.getValue();
        double ppi = 96.0;

        try {
            ppi = Double.parseDouble(txtPPI.getText());
        } catch (NumberFormatException ex) {
            System.err.println("[ADVERTENCIA]: PPI inválido, usando 96 por defecto.");
        }

        double focalCamara = 0;
        String valorFocal = txtFocalCamara.getText() == null ? "" : txtFocalCamara.getText().trim();
        if (!valorFocal.isEmpty()) {
            try {
                focalCamara = Double.parseDouble(valorFocal.replace(',', '.'));
                if (focalCamara <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                System.err.println("[ADVERTENCIA]: focal de cámara inválida; la DP no se reportará.");
                focalCamara = 0;
            }
        }

        AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO = distanciaMetros;
        AppConfig.PPI_ESTANDAR_MONITOR = ppi;
        AppConfig.DISTANCIA_FOCAL_CAMARA_PX = focalCamara;
        AppConfig.guardar();
        SesionEvaluacion.configurarEntorno(distanciaMetros, ppi);
        System.out.println("[SISTEMA]: entorno configurado. Distancia: " + distanciaMetros + "m | PPI: " + ppi);
        if (nombrePaciente == null || nombrePaciente.isBlank()) {
            Navegador.mostrar(stagePrincipal, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
        } else {
            ControladorVerificacionPerifericos.mostrarVentana(stagePrincipal);
        }
    }
}
