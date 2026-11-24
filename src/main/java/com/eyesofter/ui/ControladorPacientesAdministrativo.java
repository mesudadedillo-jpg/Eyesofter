package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.db.GestorGrupos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;

/** Directorio local con soporte para Alta, Consulta, Edición y Eliminación de Pacientes. */
public class ControladorPacientesAdministrativo implements VistaAdministrativa {

    @FXML private TextField txtBuscar;
    @FXML private TextField txtFiltroCurp, txtFiltroNombre, txtFiltroGrupo, txtFiltroTutor, txtFiltroTelefono, txtFiltroCorreo;
    @FXML private TableView<GestorBaseDatos.PacienteInfo> tablaPacientes;
    @FXML private TableColumn<GestorBaseDatos.PacienteInfo, String> colCurp, colNombre, colGrado, colTutor, colTelefono, colCorreo, colEstado;

    private ControladorPanelAdministrativo panel;
    private String seleccionCurp;

    @FXML private void initialize() {
        colCurp.setCellValueFactory(c -> propiedad(c.getValue().curp));
        colNombre.setCellValueFactory(c -> propiedad(c.getValue().nombre));
        colGrado.setCellValueFactory(c -> propiedad(c.getValue().gradoGrupo));
        colTutor.setCellValueFactory(c -> propiedad(c.getValue().nombreTutor));
        colTelefono.setCellValueFactory(c -> propiedad(c.getValue().telefonoTutor));
        colCorreo.setCellValueFactory(c -> propiedad(c.getValue().correoTutor));
        colEstado.setCellValueFactory(c -> propiedad(estadoActual(c.getValue())));

        txtBuscar.textProperty().addListener((obs, anterior, texto) -> cargar());
        for (TextField filtro : new TextField[]{txtFiltroCurp, txtFiltroNombre, txtFiltroGrupo, txtFiltroTutor, txtFiltroTelefono, txtFiltroCorreo}) {
            if (filtro != null) filtro.textProperty().addListener((obs, anterior, texto) -> cargar());
        }

        // Configuración de menú contextual (Clic derecho para Editar, Eliminar y Abrir Ficha)
        tablaPacientes.setRowFactory(tabla -> {
            TableRow<GestorBaseDatos.PacienteInfo> fila = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem mFicha = new MenuItem("👁 Ver Ficha Clínica");
            MenuItem mEditar = new MenuItem("✎ Editar Paciente");
            MenuItem mEliminar = new MenuItem("🗑 Eliminar Paciente");

            mFicha.setOnAction(e -> { if (!fila.isEmpty()) panel.mostrarFicha(fila.getItem()); });
            mEditar.setOnAction(e -> { if (!fila.isEmpty()) editarPacienteModal(fila.getItem()); });
            mEliminar.setOnAction(e -> { if (!fila.isEmpty()) confirmarEliminacionPaciente(fila.getItem()); });

            menu.getItems().addAll(mFicha, mEditar, new SeparatorMenuItem(), mEliminar);
            fila.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu)
            );

            fila.setOnMouseClicked(evento -> {
                if (fila.isEmpty()) return;
                seleccionCurp = fila.getItem().curp;
                if (evento.getClickCount() == 2) verFicha();
            });
            return fila;
        });

        cargar();
        ActualizacionPeriodica.mientrasVisible(tablaPacientes, javafx.util.Duration.seconds(4), this::cargar);
    }

    private SimpleStringProperty propiedad(String valor) {
        return new SimpleStringProperty(valor == null || valor.isBlank() ? "—" : valor);
    }

    private String estadoActual(GestorBaseDatos.PacienteInfo paciente) {
        boolean tieneCitaHoy = GestorBaseDatos.obtenerCitas(LocalDate.now()).stream()
                .anyMatch(c -> paciente.curp.equalsIgnoreCase(c.curpPaciente) && "EN_ESPERA".equalsIgnoreCase(c.estado));
        if (tieneCitaHoy) return "En espera";
        return GestorBaseDatos.obtenerEvaluacionesPaciente(paciente.curp).isEmpty() ? "Pendiente" : "Completado";
    }

    private void cargar() {
        var pacientes = GestorBaseDatos.obtenerPacientes(
                txtBuscar.getText(),
                txtFiltroCurp == null ? "" : txtFiltroCurp.getText(),
                txtFiltroNombre == null ? "" : txtFiltroNombre.getText(),
                txtFiltroGrupo == null ? "" : txtFiltroGrupo.getText(),
                txtFiltroTutor == null ? "" : txtFiltroTutor.getText(),
                txtFiltroTelefono == null ? "" : txtFiltroTelefono.getText(),
                txtFiltroCorreo == null ? "" : txtFiltroCorreo.getText()
        );
        tablaPacientes.setItems(FXCollections.observableArrayList(pacientes));
        if (seleccionCurp != null) {
            tablaPacientes.getSelectionModel().select(pacientes.stream().filter(p -> seleccionCurp.equalsIgnoreCase(p.curp)).findFirst().orElse(null));
        }
    }

    @Override public void setPanelAdministrativo(ControladorPanelAdministrativo panel) { this.panel = panel; }

    @FXML private void nuevoPaciente() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Nuevo paciente");
        TextField curp = new TextField(); curp.setPromptText("CURP del paciente");
        TextField nombre = new TextField(); nombre.setPromptText("Ej: Mateo Hernández García");
        TextField grado = new TextField(); grado.setPromptText("Ej: 1° Primaria");
        TextField grupo = new TextField(); grupo.setPromptText("Ej: A");
        TextField tutor = new TextField(); tutor.setPromptText("Ej: Luis Alberto Hernández");
        TextField telefono = new TextField(); telefono.setPromptText("Ej: 7751124589");
        TextField correo = new TextField(); correo.setPromptText("Ej: tutor@gmail.com");
        ComboBox<GestorGrupos.GrupoInfo> grupoEscolar = comboGrupos(null);
        // Con grupo escolar, el grado y la letra los pone el grupo.
        grado.disableProperty().bind(conGrupo(grupoEscolar));
        grupo.disableProperty().bind(conGrupo(grupoEscolar));

        GridPane campos = new GridPane(); campos.setHgap(12); campos.setVgap(10);
        campos.addRow(0, new Label("CURP *"), curp);
        campos.addRow(1, new Label("Nombre completo *"), nombre);
        campos.addRow(2, new Label("Grupo escolar"), grupoEscolar);
        campos.addRow(3, new Label("Grado"), grado);
        campos.addRow(4, new Label("Grupo"), grupo);
        campos.addRow(5, new Label("Nombre de tutor"), tutor);
        campos.addRow(6, new Label("Teléfono"), telefono);
        campos.addRow(7, new Label("Correo electrónico"), correo);
        dialogo.getDialogPane().setContent(campos);

        ButtonType agregar = new ButtonType("Agregar paciente", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, agregar);
        dialogo.setResizable(true);
        dialogo.getDialogPane().setMinWidth(580);

        dialogo.showAndWait().filter(r -> r == agregar).ifPresent(r -> {
            if (curp.getText().isBlank() || nombre.getText().isBlank()) {
                aviso(Alert.AlertType.WARNING, "Datos incompletos", "La CURP y el nombre completo son obligatorios.");
                return;
            }
            boolean guardado = GestorBaseDatos.registrarPacienteRapido(curp.getText(), nombre.getText(), grado.getText(), grupo.getText(), tutor.getText(), telefono.getText(), correo.getText());
            if (guardado && grupoElegido(grupoEscolar) != null) {
                GestorGrupos.inscribir(grupoElegido(grupoEscolar).id(), java.util.List.of(curp.getText()));
            }
            if (guardado) cargar();
            else aviso(Alert.AlertType.ERROR, "Error", "No se pudo registrar al paciente.");
        });
    }

    private void editarPacienteModal(GestorBaseDatos.PacienteInfo paciente) {
        if (paciente == null) return;
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Editar paciente · " + paciente.curp);

        TextField nombre = new TextField(paciente.nombre);
        TextField gradoGrupo = new TextField(paciente.gradoGrupo);
        TextField tutor = new TextField(paciente.nombreTutor == null ? "" : paciente.nombreTutor);
        TextField telefono = new TextField(paciente.telefonoTutor == null ? "" : paciente.telefonoTutor);
        TextField correo = new TextField(paciente.correoTutor == null ? "" : paciente.correoTutor);
        ComboBox<GestorGrupos.GrupoInfo> grupoEscolar = comboGrupos(paciente.idGrupo);
        gradoGrupo.disableProperty().bind(conGrupo(grupoEscolar));

        GridPane campos = new GridPane(); campos.setHgap(12); campos.setVgap(10);
        campos.addRow(0, new Label("CURP (No editable):"), new Label(paciente.curp));
        campos.addRow(1, new Label("Nombre completo:"), nombre);
        campos.addRow(2, new Label("Grupo escolar:"), grupoEscolar);
        campos.addRow(3, new Label("Grado y Grupo:"), gradoGrupo);
        campos.addRow(4, new Label("Nombre Tutor:"), tutor);
        campos.addRow(5, new Label("Teléfono Tutor:"), telefono);
        campos.addRow(6, new Label("Correo Tutor:"), correo);
        dialogo.getDialogPane().setContent(campos);

        ButtonType guardar = new ButtonType("Guardar Cambios", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);

        dialogo.showAndWait().filter(r -> r == guardar).ifPresent(r -> {
            if (nombre.getText().isBlank()) {
                aviso(Alert.AlertType.WARNING, "Nombre requerido", "El nombre no puede estar vacío.");
                return;
            }
            // Actualizar tutor si se especificó
            if (paciente.curpTutor != null && !paciente.curpTutor.isBlank()) {
                GestorBaseDatos.actualizarTutor(paciente.curpTutor, tutor.getText(), telefono.getText(), correo.getText());
            }
            // Primero el grupo: al quitarlo, el grado capturado se vuelve a guardar tal cual.
            GestorGrupos.GrupoInfo elegido = grupoElegido(grupoEscolar);
            Integer nuevoGrupo = elegido == null ? null : elegido.id();
            if (!java.util.Objects.equals(nuevoGrupo, paciente.idGrupo)) {
                if (nuevoGrupo == null) GestorGrupos.quitar(paciente.curp);
                else GestorGrupos.inscribir(nuevoGrupo, java.util.List.of(paciente.curp));
            }
            boolean exito = GestorBaseDatos.actualizarPaciente(paciente.curp, nombre.getText(), paciente.fechaNacimiento, gradoGrupo.getText(), paciente.curpTutor);
            if (exito) {
                aviso(Alert.AlertType.INFORMATION, "Paciente actualizado", "Los datos se guardaron correctamente.");
                cargar();
            } else {
                aviso(Alert.AlertType.ERROR, "Error", "No se pudo actualizar el paciente.");
            }
        });
    }

    private void confirmarEliminacionPaciente(GestorBaseDatos.PacienteInfo paciente) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar eliminación");
        alert.setHeaderText("¿Eliminar al paciente " + paciente.nombre + "?");
        alert.setContentText("Esta acción eliminará su expediente completo, citas y evaluaciones asociadas.");

        alert.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            boolean eliminado = GestorBaseDatos.eliminarPaciente(paciente.curp);
            if (eliminado) {
                aviso(Alert.AlertType.INFORMATION, "Eliminado", "El paciente ha sido eliminado del sistema.");
                cargar();
            } else {
                aviso(Alert.AlertType.ERROR, "Error", "No se pudo eliminar el paciente.");
            }
        });
    }

    @FXML private void actualizar() { cargar(); }
    @FXML private void limpiarFiltro() {
        seleccionCurp = null;
        txtBuscar.clear();
        for (TextField f : new TextField[]{txtFiltroCurp, txtFiltroNombre, txtFiltroGrupo, txtFiltroTutor, txtFiltroTelefono, txtFiltroCorreo}) {
            if (f != null) f.clear();
        }
    }

    @FXML private void verFicha() {
        GestorBaseDatos.PacienteInfo paciente = tablaPacientes.getSelectionModel().getSelectedItem();
        if (paciente == null) {
            aviso(Alert.AlertType.INFORMATION, "Selecciona un paciente", "Elige una fila para abrir su ficha.");
            return;
        }
        panel.mostrarFicha(paciente);
    }

    /** Opción "Sin grupo" de las listas (id -1). */
    private static final GestorGrupos.GrupoInfo SIN_GRUPO = new GestorGrupos.GrupoInfo(-1, "", 0, "", "", null, true, null, 0);

    /** Lista de grupos activos con "Sin grupo" al principio y el grupo actual elegido. */
    private ComboBox<GestorGrupos.GrupoInfo> comboGrupos(Integer actual) {
        ComboBox<GestorGrupos.GrupoInfo> combo = new ComboBox<>();
        combo.getItems().add(SIN_GRUPO);
        combo.getItems().addAll(GestorGrupos.listar(true));
        combo.setValue(combo.getItems().stream().filter(g -> actual != null && g.id() == actual).findFirst().orElse(SIN_GRUPO));
        combo.setMaxWidth(Double.MAX_VALUE);
        javafx.util.Callback<ListView<GestorGrupos.GrupoInfo>, ListCell<GestorGrupos.GrupoInfo>> celdas = lista -> new ListCell<>() {
            @Override protected void updateItem(GestorGrupos.GrupoInfo g, boolean vacio) {
                super.updateItem(g, vacio);
                setText(vacio || g == null ? null : g.id() < 0 ? "Sin grupo (escribir grado a mano)" : g.toString());
            }
        };
        combo.setCellFactory(celdas);
        combo.setButtonCell(celdas.call(null));
        combo.setTooltip(new Tooltip("Con grupo, el grado cambia solo cuando el grupo pasa de año."));
        return combo;
    }

    /** true si en la lista hay un grupo real elegido. */
    private static javafx.beans.binding.BooleanBinding conGrupo(ComboBox<GestorGrupos.GrupoInfo> combo) {
        return javafx.beans.binding.Bindings.createBooleanBinding(
                () -> combo.getValue() != null && combo.getValue().id() > 0, combo.valueProperty());
    }

    private static GestorGrupos.GrupoInfo grupoElegido(ComboBox<GestorGrupos.GrupoInfo> combo) {
        return combo.getValue() == null || combo.getValue().id() < 0 ? null : combo.getValue();
    }

    private void aviso(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo, mensaje, ButtonType.OK);
        alerta.setHeaderText(titulo);
        alerta.showAndWait();
    }
}