package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.reportes.GeneradorPDF;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Ficha administrativa enlazada al historial clínico y agenda del paciente.
 */
public class ControladorFichaPaciente {

    @FXML private Label lblNombre, lblFolio, lblGrado, lblTutor, lblTelefono, lblCorreo, lblCitas;
    @FXML private TableView<GestorBaseDatos.EvaluacionInfo> tablaEvaluaciones;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colFecha, colAgudeza, colIshihara, colOrientacion;

    private ControladorPanelAdministrativo panel;
    private GestorBaseDatos.PacienteInfo paciente;

    public void setPanelAdministrativo(ControladorPanelAdministrativo panel) {
        this.panel = panel;
    }

    public void setPaciente(GestorBaseDatos.PacienteInfo paciente) {
        this.paciente = paciente;

        lblNombre.setText(valor(paciente.nombre));
        lblFolio.setText(valor(paciente.curp));
        lblGrado.setText(valor(paciente.gradoGrupo));
        lblTutor.setText(valor(paciente.nombreTutor));
        lblTelefono.setText(valor(paciente.telefonoTutor));
        lblCorreo.setText(valor(paciente.correoTutor));

        // Configuración de tabla de evaluaciones
        colFecha.setCellValueFactory(c -> texto(c.getValue().fechaHora));
        colAgudeza.setCellValueFactory(c -> texto("OD " + valor(c.getValue().agudezaDer) + " · OI " + valor(c.getValue().agudezaIzq)));
        colIshihara.setCellValueFactory(c -> texto(c.getValue().resultadoIshihara));
        colOrientacion.setCellValueFactory(c -> texto(c.getValue().recomendacion));

        tablaEvaluaciones.setRowFactory(vista -> {
            TableRow<GestorBaseDatos.EvaluacionInfo> fila = new TableRow<>();
            fila.setOnMouseClicked(evento -> {
                if (evento.getClickCount() == 2 && !fila.isEmpty()) generarPdf();
            });
            return fila;
        });

        refrescarDatos();
    }

    private void refrescarDatos() {
        if (paciente == null) return;

        // Cargar evaluaciones
        tablaEvaluaciones.setItems(FXCollections.observableArrayList(
                GestorBaseDatos.obtenerEvaluacionesPaciente(paciente.curp)
        ));

        // Cargar y mostrar conteo de citas con desglose de pendientes
        List<GestorBaseDatos.CitaInfo> citas = GestorBaseDatos.obtenerCitasPaciente(paciente.curp);
        long pendientes = citas.stream().filter(c -> "EN_ESPERA".equalsIgnoreCase(c.estado)).count();
        lblCitas.setText(citas.size() + " citas registradas (" + pendientes + " pendientes)");
    }

    private String valor(String texto) {
        return texto == null || texto.isBlank() ? "—" : texto;
    }

    private SimpleStringProperty texto(String valor) {
        return new SimpleStringProperty(valor(valor));
    }

    @FXML
    private void volver() {
        panel.mostrarPacientes();
    }

    @FXML
    private void nuevoTamizaje() {
        Alert dialogoModo = new Alert(Alert.AlertType.CONFIRMATION);
        dialogoModo.setTitle("Método de Evaluación");
        dialogoModo.setHeaderText("Elige el método de evaluación");
        dialogoModo.setContentText("Paciente: " + paciente.nombre + "\nSelecciona si la evaluación será asistida o autónoma:");

        ButtonType btnAsistida = new ButtonType("Asistida");
        ButtonType btnAutonoma = new ButtonType("Autónoma");
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialogoModo.getButtonTypes().setAll(btnAsistida, btnAutonoma, btnCancelar);

        DialogPane pane = dialogoModo.getDialogPane();
        pane.setMinWidth(420);
        pane.setMinHeight(Region.USE_PREF_SIZE);

        dialogoModo.showAndWait().ifPresent(resultado -> {
            if (resultado == btnCancelar) return;

            SesionEvaluacion.ModoEvaluacion modoElegido = (resultado == btnAutonoma)
                    ? SesionEvaluacion.ModoEvaluacion.AUTONOMA
                    : SesionEvaluacion.ModoEvaluacion.ASISTIDA;

            panel.iniciarTamizajePaciente(paciente, modoElegido);
        });
    }

    @FXML
    private void generarPdf() {
        GestorBaseDatos.EvaluacionInfo evaluacion = tablaEvaluaciones.getSelectionModel().getSelectedItem();
        if (evaluacion == null) {
            aviso(Alert.AlertType.INFORMATION, "Selecciona una evaluación", "Elige una evaluación para generar su PDF.");
            return;
        }
        try {
            File archivo = GeneradorPDF.generarReporte(paciente, evaluacion);
            GestorBaseDatos.registrarReportePdf(evaluacion.idEvaluacion, archivo.getAbsolutePath());
            aviso(Alert.AlertType.INFORMATION, "PDF generado", "Se guardó en: " + archivo.getAbsolutePath());
        } catch (Exception e) {
            aviso(Alert.AlertType.ERROR, "No se pudo generar el PDF", e.getMessage());
        }
    }

    @FXML
    private void agendarCita() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Agendar cita");

        DatePicker fecha = new DatePicker(LocalDate.now()); // Por defecto HOY o el día que elija
        ComboBox<String> hora = new ComboBox<>(FXCollections.observableArrayList(
                "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "14:00", "14:30", "15:00", "15:30"
        ));
        hora.setValue("09:00");

        ComboBox<String> tipo = new ComboBox<>(FXCollections.observableArrayList(
                "Tamizaje Escolar Anual", "Primera vez", "Revisión Optométrica", "Seguimiento Clínico", "Control"
        ));
        tipo.setValue("Tamizaje Escolar Anual");

        TextArea notas = new TextArea();
        notas.setPromptText("Motivo de consulta o notas clínicas...");
        notas.setPrefRowCount(3);

        GridPane campos = new GridPane();
        campos.setHgap(12);
        campos.setVgap(10);
        campos.addRow(0, new Label("Paciente:"), new Label(paciente.nombre + " (" + paciente.curp + ")"));
        campos.addRow(1, new Label("Fecha:"), fecha);
        campos.addRow(2, new Label("Horario:"), hora);
        campos.addRow(3, new Label("Tipo:"), tipo);
        campos.addRow(4, new Label("Notas:"), notas);

        dialogo.getDialogPane().setContent(campos);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            if (fecha.getValue() == null || hora.getValue() == null || tipo.getValue() == null) {
                aviso(Alert.AlertType.WARNING, "Datos incompletos", "Por favor completa la fecha, hora y tipo de cita.");
                return;
            }

            boolean creada = GestorBaseDatos.agendarCita(
                    paciente.curp,
                    fecha.getValue(),
                    hora.getValue(),
                    tipo.getValue(),
                    notas.getText()
            );

            if (creada) {
                aviso(Alert.AlertType.INFORMATION, "Cita agendada", "La cita se guardó exitosamente para el día " + fecha.getValue() + " a las " + hora.getValue() + " hrs.");
                refrescarDatos();
            } else {
                aviso(Alert.AlertType.ERROR, "Horario no disponible", "Ya existe una cita programada en la fecha " + fecha.getValue() + " a las " + hora.getValue() + ". Por favor elige otro horario.");
            }
        });
    }

    /**
     * Modal interactivo para ver el historial y gestionar (Completar / Cancelar) las citas del paciente.
     */
    @FXML
    private void verHistorialCitas() {
        List<GestorBaseDatos.CitaInfo> citas = GestorBaseDatos.obtenerCitasPaciente(paciente.curp);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Historial de Citas · " + paciente.nombre);
        dialogo.getDialogPane().setMinWidth(620);
        dialogo.getDialogPane().setMinHeight(400);

        TableView<GestorBaseDatos.CitaInfo> tablaCitas = new TableView<>();

        TableColumn<GestorBaseDatos.CitaInfo, String> colF = new TableColumn<>("Fecha");
        colF.setCellValueFactory(c -> texto(c.getValue().fecha));

        TableColumn<GestorBaseDatos.CitaInfo, String> colH = new TableColumn<>("Hora");
        colH.setCellValueFactory(c -> texto(c.getValue().hora));

        TableColumn<GestorBaseDatos.CitaInfo, String> colT = new TableColumn<>("Tipo");
        colT.setCellValueFactory(c -> texto(c.getValue().tipo));

        TableColumn<GestorBaseDatos.CitaInfo, String> colE = new TableColumn<>("Estado");
        colE.setCellValueFactory(c -> texto(c.getValue().estado));

        TableColumn<GestorBaseDatos.CitaInfo, String> colN = new TableColumn<>("Notas");
        colN.setCellValueFactory(c -> texto(c.getValue().notas));

        tablaCitas.getColumns().addAll(colF, colH, colT, colE, colN);
        tablaCitas.setItems(FXCollections.observableArrayList(citas));

        // Menú contextual para cambiar de estado o eliminar
        ContextMenu menu = new ContextMenu();
        MenuItem itemCompletar = new MenuItem("✔ Marcar como Completada");
        MenuItem itemCancelar = new MenuItem("✖ Cancelar Cita");
        MenuItem itemEliminar = new MenuItem("🗑 Eliminar Cita");

        itemCompletar.setOnAction(e -> {
            GestorBaseDatos.CitaInfo sel = tablaCitas.getSelectionModel().getSelectedItem();
            if (sel != null) {
                GestorBaseDatos.actualizarEstadoCita(sel.idCita, "COMPLETADA");
                tablaCitas.setItems(FXCollections.observableArrayList(GestorBaseDatos.obtenerCitasPaciente(paciente.curp)));
                refrescarDatos();
            }
        });

        itemCancelar.setOnAction(e -> {
            GestorBaseDatos.CitaInfo sel = tablaCitas.getSelectionModel().getSelectedItem();
            if (sel != null) {
                GestorBaseDatos.actualizarEstadoCita(sel.idCita, "CANCELADA");
                tablaCitas.setItems(FXCollections.observableArrayList(GestorBaseDatos.obtenerCitasPaciente(paciente.curp)));
                refrescarDatos();
            }
        });

        itemEliminar.setOnAction(e -> {
            GestorBaseDatos.CitaInfo sel = tablaCitas.getSelectionModel().getSelectedItem();
            if (sel != null) {
                GestorBaseDatos.eliminarCita(sel.idCita);
                tablaCitas.setItems(FXCollections.observableArrayList(GestorBaseDatos.obtenerCitasPaciente(paciente.curp)));
                refrescarDatos();
            }
        });

        menu.getItems().addAll(itemCompletar, itemCancelar, new SeparatorMenuItem(), itemEliminar);
        tablaCitas.setContextMenu(menu);

        dialogo.getDialogPane().setContent(tablaCitas);
        dialogo.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialogo.showAndWait();
    }

    private void aviso(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo, mensaje, ButtonType.OK);
        alerta.setHeaderText(titulo);
        alerta.showAndWait();
    }
}