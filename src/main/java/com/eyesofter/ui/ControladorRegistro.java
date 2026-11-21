package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;

/**
 * Controlador de registro de paciente y persona responsable.
 * Conectado a Registro.fxml
 */
public class ControladorRegistro {

    private Stage stagePrincipal;

    // --- ELEMENTOS DEL AVENTURERO INYECTADOS DESDE FXML ---
    @FXML private ComboBox<String> cmbPacientesExistentes;
    @FXML private TextField txtCurpAlumno;
    @FXML private TextField txtNombreAlumno;
    @FXML private TextField txtEdad;
    @FXML private DatePicker dpFechaNacimiento;
    @FXML private TextField txtGradoGrupo;

    // --- ELEMENTOS DEL GUARDIÁN INYECTADOS DESDE FXML ---
    @FXML private ComboBox<String> cmbTutoresExistentes;
    @FXML private TextField txtCurpTutor;
    @FXML private TextField txtNombreTutor;
    @FXML private TextField txtTelefonoTutor;
    @FXML private TextField txtCorreoTutor;

    @FXML private Button btnContinuar;
    @FXML private ComboBox<SesionEvaluacion.ModoEvaluacion> cmbModoEvaluacion;

    public static void mostrarVentana(Stage stage) {

        GestorBaseDatos.inicializarBaseDatos();

        try {
            FXMLLoader loader = new FXMLLoader(ControladorRegistro.class.getResource("/fxml/Registro.fxml"));
            Parent root = loader.load();

            ControladorRegistro controlador = loader.getController();
            controlador.inicializar(stage);

            stage.setTitle("EYESOFTER · Registro de paciente");
            stage.setScene(new Scene(root));

            ModoKiosco.aplicar(stage);

            stage.show();
        } catch (Exception e) {
            System.err.println("⚠️ Error al cargar Registro.fxml. Verifica la ruta en resources.");
            e.printStackTrace();
        }
    }

    /**
     * Inicializa los ComboBox y sus eventos de autocompletado
     */
    private void inicializar(Stage stage) {
        this.stagePrincipal = stage;

        cargarPacientes(cmbPacientesExistentes);
        cargarTutores(cmbTutoresExistentes);
        cmbModoEvaluacion.setItems(FXCollections.observableArrayList(SesionEvaluacion.ModoEvaluacion.values()));
        cmbModoEvaluacion.setValue(SesionEvaluacion.ModoEvaluacion.ASISTIDA);
        cmbPacientesExistentes.setEditable(true);
        cmbTutoresExistentes.setEditable(true);
        txtEdad.textProperty().addListener((obs, anterior, edad) -> actualizarEstadoResponsable());

        // Acción: Autocompletar datos del Alumno al seleccionar
        cmbPacientesExistentes.setOnAction(e -> {
            String seleccion = cmbPacientesExistentes.getValue();
            if (seleccion != null && seleccion.contains(" | ")) {
                String[] datos = seleccion.split(" \\| ");
                if (datos.length >= 4) {
                    txtCurpAlumno.setText(datos[0]); // CURP
                    txtNombreAlumno.setText(datos[1]); // Nombre

                    try {
                        dpFechaNacimiento.setValue(LocalDate.parse(datos[2]));
                    } catch (Exception ex) {
                        dpFechaNacimiento.setValue(null);
                    }

                    txtGradoGrupo.setText(datos[3]); // Grado y Grupo
                    actualizarEdadDesdeFecha();
                }
            }
        });

        // Acción: Autocompletar datos del Tutor al seleccionar
        cmbTutoresExistentes.setOnAction(e -> {
            String seleccion = cmbTutoresExistentes.getValue();
            if (seleccion != null && seleccion.contains(" | ")) {
                String[] datos = seleccion.split(" \\| ");
                if (datos.length >= 4) {
                    txtCurpTutor.setText(datos[0]);
                    txtNombreTutor.setText(datos[1]);
                    txtTelefonoTutor.setText(datos[2]);
                    txtCorreoTutor.setText(datos[3]);
                }
            }
        });
    }

    /**
     * Acción conectada al botón de guardar y configurar la sesión.
     */
    @FXML
    private void guardarYContinuar() {
        String curpA = txtCurpAlumno.getText().trim();
        String nombreA = txtNombreAlumno.getText().trim();
        String gradoA = txtGradoGrupo.getText().trim();
        String fechaNacA = (dpFechaNacimiento.getValue() != null) ? dpFechaNacimiento.getValue().toString() : "";

        String curpT = txtCurpTutor.getText().trim();
        String nombreT = txtNombreTutor.getText().trim();
        String telT = txtTelefonoTutor.getText().trim();
        String correoT = txtCorreoTutor.getText().trim();
        SesionEvaluacion.ModoEvaluacion modo = cmbModoEvaluacion.getValue();

        if (curpA.isEmpty() || nombreA.isEmpty() || (!esMayorEdad() && (curpT.isEmpty() || nombreT.isEmpty()))) {
            showAlert("Información incompleta", esMayorEdad()
                    ? "Capture al menos la CURP y el nombre de la persona paciente."
                    : "Capture al menos la CURP y el nombre de la persona paciente y de su responsable.");
            return;
        }

        try {
            GestorBaseDatos.registrarTutor(curpT, nombreT, telT, correoT);
            GestorBaseDatos.registrarPaciente(curpA, nombreA, fechaNacA, gradoA, curpT);

            SesionEvaluacion.iniciar(curpA, nombreA, gradoA);
            SesionEvaluacion.configurarModo(modo);
            System.out.println("[DB EXITOSA]: paciente y responsable registrados.");

            ControladorConfiguracion.mostrarVentana(stagePrincipal, curpA, nombreA, gradoA);

        } catch (Exception ex) {
            showAlert("Error de Base de Datos", "Ocurrió un error al guardar: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @FXML private void buscarPorCurp() {
        GestorBaseDatos.PacienteInfo paciente = GestorBaseDatos.buscarPacientePorCurp(txtCurpAlumno.getText());
        if (paciente == null) { showAlert("Sin coincidencias", "No se encontró un registro con esa CURP o folio."); return; }
        txtCurpAlumno.setText(paciente.curp); txtNombreAlumno.setText(paciente.nombre); txtGradoGrupo.setText(paciente.gradoGrupo);
        try { dpFechaNacimiento.setValue(paciente.fechaNacimiento == null || paciente.fechaNacimiento.isBlank() ? null : LocalDate.parse(paciente.fechaNacimiento)); } catch (Exception ignored) { }
        txtCurpTutor.setText(paciente.curpTutor == null ? "" : paciente.curpTutor); txtNombreTutor.setText(paciente.nombreTutor == null ? "" : paciente.nombreTutor);
        txtTelefonoTutor.setText(paciente.telefonoTutor == null ? "" : paciente.telefonoTutor); txtCorreoTutor.setText(paciente.correoTutor == null ? "" : paciente.correoTutor);
        actualizarEdadDesdeFecha();
    }

    private void actualizarEdadDesdeFecha() {
        if (dpFechaNacimiento.getValue() == null) return;
        txtEdad.setText(String.valueOf(java.time.Period.between(dpFechaNacimiento.getValue(), LocalDate.now()).getYears()));
    }

    private boolean esMayorEdad() {
        try { return Integer.parseInt(txtEdad.getText().trim()) >= 18; } catch (Exception e) { return false; }
    }

    private void actualizarEstadoResponsable() {
        boolean deshabilitar = esMayorEdad();
        cmbTutoresExistentes.setDisable(deshabilitar); txtCurpTutor.setDisable(deshabilitar); txtNombreTutor.setDisable(deshabilitar);
        txtTelefonoTutor.setDisable(deshabilitar); txtCorreoTutor.setDisable(deshabilitar);
        if (deshabilitar) { txtCurpTutor.clear(); txtNombreTutor.clear(); txtTelefonoTutor.clear(); txtCorreoTutor.clear(); }
    }

    @FXML private void cancelar() { Navegador.mostrar(stagePrincipal, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control"); }

    // --- MÉTODOS PARA CARGAR DATOS ---
    private void cargarPacientes(ComboBox<String> comboBox) {
        ObservableList<String> listaPacientes = FXCollections.observableArrayList();
        for (GestorBaseDatos.PacienteInfo paciente : GestorBaseDatos.obtenerPacientes("")) {
            listaPacientes.add(paciente.curp + " | " + paciente.nombre + " | " +
                    (paciente.fechaNacimiento == null ? "" : paciente.fechaNacimiento) + " | " +
                    (paciente.gradoGrupo == null ? "" : paciente.gradoGrupo));
        }
        comboBox.setItems(listaPacientes);
    }

    private void cargarTutores(ComboBox<String> comboBox) {
        ObservableList<String> listaTutores = FXCollections.observableArrayList();

        for (GestorBaseDatos.PacienteInfo tutor : GestorBaseDatos.obtenerTutores()) {
            listaTutores.add(tutor.curp + " | " + tutor.nombreTutor + " | " +
                    (tutor.telefonoTutor == null ? "" : tutor.telefonoTutor) + " | " +
                    (tutor.correoTutor == null ? "" : tutor.correoTutor));
        }
        comboBox.setItems(listaTutores);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
