package com.eyesofter.ui;

import com.eyesofter.clinica.ResultadoClinico;
import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.Antropometria;
import com.eyesofter.formulas.TrigonometriaVisual;
import com.eyesofter.medicion_snellen.MotorSnellen;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;
import java.util.Locale;
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
    @FXML private Label lblResolucion;
    @FXML private Label lblLimite;
    @FXML private VBox panelPaciente;
    @FXML private ComboBox<MotorSnellen.TipoOptotipo> cmbOptotipo;
    @FXML private Label lblSugerenciaOptotipo;
    @FXML private TextField txtDp;
    @FXML private Label lblDpEstimada;
    @FXML private CheckBox chkBrillo;
    @FXML private Label lblEstadoCamara;

    /** PPI tal como se cargó (para saber si el operador lo escribió a mano). */
    private String ppiInicial = "";
    private String metodoPpiInicial = "DIAGONAL";

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

        Screen pantalla = IdentidadPantalla.deVentana(stage);
        double anchoPx = pantalla.getBounds().getWidth() * pantalla.getOutputScaleX();
        double altoPx = pantalla.getBounds().getHeight() * pantalla.getOutputScaleY();
        boolean medidaConTarjeta = "TARJETA".equals(AppConfig.METODO_PPI) && AppConfig.PPI_ESTANDAR_MONITOR > 0
                && !Boolean.FALSE.equals(IdentidadPantalla.coincideConCalibrada(pantalla));
        if (medidaConTarjeta) {
            // Se muestra el PPI medido tal cual (antes se recalculaba desde la diagonal redondeada).
            txtPPI.setText(String.format(Locale.US, "%.1f", AppConfig.PPI_ESTANDAR_MONITOR));
            lblResolucion.setText(String.format(Locale.US, "PPI medido con tarjeta en esta pantalla · resolución %.0f × %.0f px", anchoPx, altoPx));
            metodoPpiInicial = "TARJETA";
        } else {
            double ppiDetectado = TrigonometriaVisual.calcularPPI(anchoPx, altoPx, AppConfig.MONITOR_DIAGONAL_PULGADAS);
            if (ppiDetectado <= 0) ppiDetectado = pantalla.getDpi();
            txtPPI.setText(String.format(Locale.US, "%.1f", ppiDetectado));
            lblResolucion.setText(String.format(Locale.US, "Resolución detectada: %.0f × %.0f px · diagonal configurada: %.1f in · "
                    + "para máxima exactitud use “Medir con tarjeta”", anchoPx, altoPx, AppConfig.MONITOR_DIAGONAL_PULGADAS));
            metodoPpiInicial = "DIAGONAL";
        }
        ppiInicial = txtPPI.getText();
        txtPPI.textProperty().addListener((o, a, b) -> actualizarLimite());

        boolean hayPaciente = nombrePaciente != null && !nombrePaciente.isBlank();
        panelPaciente.setVisible(hayPaciente);
        panelPaciente.setManaged(hayPaciente);
        if (hayPaciente) prepararDatosPaciente();
        lblEstadoCamara.setText(AppConfig.FOCAL_CAMARA_NORMALIZADA > 0
                ? "Cámara calibrada para este equipo (se recalibra en la pantalla de posición con una cinta)."
                : "Cámara sin calibrar: la distancia por cámara será aproximada. Se calibra en la pantalla de posición con una cinta.");

        // Configurar el evento dinámico del Slider para actualizar el texto
        sliderDistancia.valueProperty().addListener((obs, oldVal, newVal) -> {
            lblValorDistancia.setText(String.format(java.util.Locale.US, "Distancia seleccionada: %.1f metros", newVal.doubleValue()));
        });
        // Se parte de la última distancia guardada (antes siempre volvía a 1.0 m).
        sliderDistancia.setValue(Math.max(sliderDistancia.getMin(), Math.min(sliderDistancia.getMax(), AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO)));
        lblValorDistancia.setText(String.format(java.util.Locale.US, "Distancia seleccionada: %.1f metros", sliderDistancia.getValue()));
        sliderDistancia.valueProperty().addListener((obs, a, b) -> actualizarLimite());
        actualizarLimite();
    }

    private void prepararDatosPaciente() {
        double edad = SesionEvaluacion.getEdadAnios();
        cmbOptotipo.setItems(FXCollections.observableArrayList(MotorSnellen.TipoOptotipo.values()));
        cmbOptotipo.setValue(SesionEvaluacion.getTipoOptotipo());
        cmbOptotipo.valueProperty().addListener((o, a, b) -> actualizarLimite());
        if (!Double.isFinite(edad)) {
            lblSugerenciaOptotipo.setText("Edad desconocida (sin fecha de nacimiento ni CURP válida): se usan criterios de adulto.");
        } else if (edad < ResultadoClinico.EDAD_MINIMA_OPTOTIPOS) {
            lblSugerenciaOptotipo.setText("Menor de 3 años: los optotipos no son confiables a esta edad. Se sugiere fotocribado o valoración pediátrica.");
        } else {
            lblSugerenciaOptotipo.setText(String.format(Locale.US, "Edad: %s · sugerido: %s", edadTexto(edad),
                    SesionEvaluacion.sugerirOptotipo(edad) == MotorSnellen.TipoOptotipo.E_DIRECCIONAL ? "E direccional" : "letras"));
        }
        lblDpEstimada.setText(String.format(Locale.US, "Sin DP medida se usará %.1f mm (%s). Influye en la distancia que mide la cámara.",
                Antropometria.dpEsperadaMm(edad), Double.isFinite(edad) ? "promedio para la edad" : "promedio adulto"));
        chkBrillo.setSelected(false);
    }

    private static String edadTexto(double edad) {
        int anios = (int) Math.floor(edad);
        int meses = (int) Math.floor((edad - anios) * 12);
        return anios < 6 ? anios + " a " + meses + " m" : anios + " años";
    }

    /**
     * Informa la línea más pequeña que esta pantalla puede dibujar a esta distancia y,
     * si no alcanza la línea de referencia para la edad, la distancia mínima necesaria.
     */
    private void actualizarLimite() {
        double ppi = leerPpi();
        if (ppi <= 0) { lblLimite.setText("Escriba un PPI válido (50 a 600)."); return; }
        double distanciaMm = sliderDistancia.getValue() * 1000.0;
        double limite = MotorSnellen.mejorLogMarMedible(distanciaMm, ppi);
        double linea = ResultadoClinico.lineaAprobatoriaParaEdad(SesionEvaluacion.getEdadAnios());
        double referencia = Double.isFinite(linea) ? linea : ResultadoClinico.LOGMAR_REDUCIDA - 0.1;
        String texto = String.format(Locale.US, "Con esta pantalla a %.1f m se puede medir hasta %s (LogMAR %.1f).",
                sliderDistancia.getValue(), TrigonometriaVisual.snellenDesdeLogMAR(limite), limite);
        if (limite > referencia + 1e-9) {
            double minimaM = TrigonometriaVisual.distanciaMinimaMmParaLogMar(referencia, ppi) / 1000.0;
            texto += String.format(Locale.US, "\n⚠ No alcanza la línea de referencia %s: el resultado podría salir “no concluyente”. "
                    + "Use al menos %.1f m o una pantalla de mayor resolución.", TrigonometriaVisual.snellenDesdeLogMAR(referencia),
                    Math.ceil(minimaM * 10) / 10.0);
            lblLimite.getStyleClass().setAll("label", "etiqueta-subtitulo", "texto-alerta");
        } else {
            lblLimite.getStyleClass().setAll("label", "etiqueta-subtitulo");
        }
        lblLimite.setText(texto);
    }

    private double leerPpi() {
        try {
            double ppi = Double.parseDouble(txtPPI.getText().replace(',', '.').trim());
            return ppi >= 50 && ppi <= 600 ? ppi : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Mide el PPI real con una tarjeta de tamaño estándar (ver {@link CalibradorPantalla}). */
    @FXML
    private void medirConTarjeta() {
        CalibradorPantalla.medirYGuardar(stagePrincipal).ifPresent(ppi -> {
            txtPPI.setText(String.format(java.util.Locale.US, "%.1f", ppi));
            ppiInicial = txtPPI.getText();
            metodoPpiInicial = "TARJETA";
            lblResolucion.setText(String.format(java.util.Locale.US,
                    "PPI medido con tarjeta: %.1f · diagonal equivalente: %.1f in", ppi, AppConfig.MONITOR_DIAGONAL_PULGADAS));
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
            ppi = Double.parseDouble(txtPPI.getText().replace(',', '.').trim());
            if (ppi < 50 || ppi > 600) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            System.err.println("[ADVERTENCIA]: PPI inválido, usando 96 por defecto.");
        }

        AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO = distanciaMetros;
        AppConfig.PPI_ESTANDAR_MONITOR = ppi;
        AppConfig.METODO_PPI = txtPPI.getText().equals(ppiInicial) ? metodoPpiInicial : "MANUAL";
        AppConfig.guardar();
        SesionEvaluacion.configurarEntorno(distanciaMetros, ppi);
        if (panelPaciente.isVisible()) {
            SesionEvaluacion.configurarTipoOptotipo(cmbOptotipo.getValue());
            SesionEvaluacion.confirmarBrillo(chkBrillo.isSelected());
            String dp = txtDp.getText() == null ? "" : txtDp.getText().replace(',', '.').trim();
            if (!dp.isEmpty()) {
                try {
                    double mm = Double.parseDouble(dp);
                    if (!Antropometria.esDpPlausible(mm)) throw new NumberFormatException();
                    SesionEvaluacion.capturarDp(mm);
                } catch (NumberFormatException ex) {
                    lblDpEstimada.setText("La DP debe estar entre 38 y 80 mm (o deje el campo vacío).");
                    lblDpEstimada.getStyleClass().setAll("label", "etiqueta-subtitulo", "texto-error");
                    return;
                }
            }
        }
        System.out.println("[SISTEMA]: entorno configurado. Distancia: " + distanciaMetros + "m | PPI: " + ppi);
        if (nombrePaciente == null || nombrePaciente.isBlank()) {
            Navegador.mostrar(stagePrincipal, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
        } else {
            ControladorVerificacionPerifericos.mostrarVentana(stagePrincipal);
        }
    }
}
