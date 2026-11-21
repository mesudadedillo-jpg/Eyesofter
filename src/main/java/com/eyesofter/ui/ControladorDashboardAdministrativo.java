package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Region;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Dashboard administrativo con cola de atención en vivo y agenda sincronizada. */
public class ControladorDashboardAdministrativo implements VistaAdministrativa {

    private enum Filtro { TODOS, ESPERA, EVALUACION, COMPLETADO }

    public static final class FilaCola {
        final GestorBaseDatos.PacienteInfo paciente;
        final GestorBaseDatos.CitaInfo cita;
        final boolean completadoHoy;

        FilaCola(GestorBaseDatos.PacienteInfo paciente, GestorBaseDatos.CitaInfo cita, boolean completadoHoy) {
            this.paciente = paciente;
            this.cita = cita;
            this.completadoHoy = completadoHoy;
        }

        String motivo() { return cita == null ? "Sin cita programada" : cita.tipo; }
        String hora() { return cita == null ? "—" : cita.hora; }
        String espera() {
            if (cita == null || cita.fecha == null) return "—";
            return cita.fecha.equals(LocalDate.now().toString()) ? "Hoy" : cita.fecha;
        }
        String estado() {
            if (completadoHoy) return "Completado";
            if (cita == null) return "Sin cita";
            return switch (cita.estado == null ? "EN_ESPERA" : cita.estado.toUpperCase()) {
                case "EN_EVALUACION" -> "En evaluación";
                case "COMPLETADA" -> "Completado";
                case "CANCELADA" -> "Cancelada";
                default -> "En espera";
            };
        }
    }

    @FXML private Label lblPacientes, lblEvaluaciones, lblEspera, lblEspeciales, lblFechaAgenda, lblSinAgenda;
    @FXML private Button btnIniciarSeleccion;
    @FXML private TableView<FilaCola> tablaCola;
    @FXML private TableColumn<FilaCola, String> colPaciente, colMotivo, colHora, colEspera, colEstado;
    @FXML private TableView<GestorBaseDatos.CitaInfo> tablaAgenda;
    @FXML private TableColumn<GestorBaseDatos.CitaInfo, String> agendaHora, agendaPaciente, agendaTipo;

    private ControladorPanelAdministrativo panel;
    private List<FilaCola> filas = List.of();
    private Timeline actualizacionAutomatica;
    private Filtro filtroActivo = Filtro.TODOS;
    private String seleccionCurp;

    @FXML private void initialize() {
        actualizarBotonIniciarSeleccion(null);
        colPaciente.setCellValueFactory(c -> texto(c.getValue().paciente.nombre + "\n" + c.getValue().paciente.curp));
        colMotivo.setCellValueFactory(c -> texto(c.getValue().motivo()));
        colHora.setCellValueFactory(c -> texto(c.getValue().hora()));
        colEspera.setCellValueFactory(c -> texto(c.getValue().espera()));
        colEstado.setCellValueFactory(c -> texto(c.getValue().estado()));
        agendaHora.setCellValueFactory(c -> texto(c.getValue().hora + " (" + c.getValue().fecha + ")"));
        agendaPaciente.setCellValueFactory(c -> texto(c.getValue().nombrePaciente));
        agendaTipo.setCellValueFactory(c -> texto(c.getValue().tipo));

        tablaCola.setRowFactory(tabla -> {
            TableRow<FilaCola> fila = new TableRow<>();
            fila.setOnMouseClicked(e -> {
                if (fila.isEmpty()) return;
                seleccionCurp = fila.getItem().paciente.curp;
                if (e.getClickCount() == 2) iniciarFila(fila.getItem());
            });
            return fila;
        });

        tablaCola.getSelectionModel().selectedItemProperty().addListener((observable, anterior, seleccion) -> {
            seleccionCurp = seleccion == null ? null : seleccion.paciente.curp;
            actualizarBotonIniciarSeleccion(seleccion);
        });

        // Menú contextual para gestión rápida en la Agenda
        tablaAgenda.setRowFactory(tabla -> {
            TableRow<GestorBaseDatos.CitaInfo> fila = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem mCompletar = new MenuItem("✔ Marcar como Completada");
            MenuItem mCancelar = new MenuItem("✖ Cancelar Cita");
            MenuItem mEliminar = new MenuItem("🗑 Eliminar Cita");

            mCompletar.setOnAction(e -> {
                if (!fila.isEmpty()) {
                    GestorBaseDatos.actualizarEstadoCita(fila.getItem().idCita, "COMPLETADA");
                    cargarDatos();
                }
            });
            mCancelar.setOnAction(e -> {
                if (!fila.isEmpty()) {
                    GestorBaseDatos.actualizarEstadoCita(fila.getItem().idCita, "CANCELADA");
                    cargarDatos();
                }
            });
            mEliminar.setOnAction(e -> {
                if (!fila.isEmpty()) {
                    GestorBaseDatos.eliminarCita(fila.getItem().idCita);
                    cargarDatos();
                }
            });

            menu.getItems().addAll(mCompletar, mCancelar, new SeparatorMenuItem(), mEliminar);
            fila.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu)
            );

            fila.setOnMouseClicked(e -> {
                if (!fila.isEmpty() && e.getClickCount() == 2) iniciarCita(fila.getItem());
            });
            return fila;
        });

        cargarDatos();

        actualizacionAutomatica = new Timeline(new KeyFrame(javafx.util.Duration.seconds(3), e -> cargarDatos()));
        actualizacionAutomatica.setCycleCount(Timeline.INDEFINITE);
        actualizacionAutomatica.play();
    }

    private void cargarDatos() {
        LocalDate hoy = LocalDate.now();
        List<GestorBaseDatos.CitaInfo> citasProximas = GestorBaseDatos.obtenerCitasProximas(hoy);
        List<GestorBaseDatos.EvaluacionInfo> evaluaciones = GestorBaseDatos.obtenerHistorialEvaluaciones();
        List<FilaCola> disponibles = new ArrayList<>();

        for (GestorBaseDatos.PacienteInfo paciente : GestorBaseDatos.obtenerPacientes("")) {
            GestorBaseDatos.CitaInfo cita = citasProximas.stream()
                    .filter(c -> paciente.curp.equalsIgnoreCase(c.curpPaciente) && !"CANCELADA".equalsIgnoreCase(c.estado))
                    .findFirst().orElse(null);

            boolean completadoHoy = evaluaciones.stream().anyMatch(e -> paciente.curp.equalsIgnoreCase(e.curpPaciente)
                    && e.fechaHora != null && e.fechaHora.startsWith(hoy.toString()));

            disponibles.add(new FilaCola(paciente, cita, completadoHoy));
        }

        filas = disponibles;
        lblPacientes.setText(String.valueOf(GestorBaseDatos.contarPacientes()));
        lblEvaluaciones.setText(String.valueOf(evaluaciones.size()));
        lblEspera.setText(String.valueOf(citasProximas.stream().filter(c -> "EN_ESPERA".equalsIgnoreCase(c.estado)).count()));
        lblEspeciales.setText(String.valueOf(citasProximas.stream().filter(c -> "Urgencia".equalsIgnoreCase(c.tipo) || "Seguimiento Clínico".equalsIgnoreCase(c.tipo)).count()));
        lblFechaAgenda.setText(hoy.toString());
        lblSinAgenda.setVisible(citasProximas.isEmpty());
        lblSinAgenda.setManaged(citasProximas.isEmpty());

        int indiceAgenda = tablaAgenda.getSelectionModel().getSelectedIndex();
        tablaAgenda.setItems(FXCollections.observableArrayList(citasProximas));
        if (indiceAgenda >= 0 && indiceAgenda < citasProximas.size()) {
            tablaAgenda.getSelectionModel().select(indiceAgenda);
        }

        aplicarFiltro(filtroActivo);
    }

    private void aplicarFiltro(Filtro filtro) {
        List<FilaCola> filtradas = filas.stream().filter(f -> switch (filtro) {
            case TODOS -> true;
            case ESPERA -> "En espera".equals(f.estado());
            case EVALUACION -> "En evaluación".equals(f.estado());
            case COMPLETADO -> f.completadoHoy;
        }).toList();
        filtroActivo = filtro;
        tablaCola.setItems(FXCollections.observableArrayList(filtradas));
        if (seleccionCurp != null) {
            tablaCola.getSelectionModel().select(filtradas.stream().filter(f -> seleccionCurp.equalsIgnoreCase(f.paciente.curp)).findFirst().orElse(null));
        }
    }

    private SimpleStringProperty texto(String valor) { return new SimpleStringProperty(valor == null || valor.isBlank() ? "—" : valor); }

    private void actualizarBotonIniciarSeleccion(FilaCola seleccion) {
        boolean haySeleccion = seleccion != null;
        btnIniciarSeleccion.setVisible(haySeleccion);
        btnIniciarSeleccion.setManaged(haySeleccion);
    }

    @Override
    public void setPanelAdministrativo(ControladorPanelAdministrativo panel) {
        this.panel = panel;
    }

    public void detenerActualizacionAutomatica() {
        if (actualizacionAutomatica != null) {
            actualizacionAutomatica.stop();
        }
    }

    @FXML private void nuevoTamizaje() { panel.iniciarTamizaje(); }

    @FXML private void iniciarSeleccion() {
        FilaCola fila = tablaCola.getSelectionModel().getSelectedItem();
        if (fila == null && seleccionCurp != null) {
            fila = filas.stream().filter(f -> seleccionCurp.equalsIgnoreCase(f.paciente.curp)).findFirst().orElse(null);
        }
        if (fila == null) return;
        iniciarFila(fila);
    }

    private void iniciarFila(FilaCola fila) {
        if (fila == null) return;
        seleccionCurp = fila.paciente.curp;

        Alert dialogoModo = new Alert(Alert.AlertType.CONFIRMATION);
        dialogoModo.setTitle("Método de Evaluación");
        dialogoModo.setHeaderText("Elige el método de evaluación");
        dialogoModo.setContentText("Paciente: " + fila.paciente.nombre + "\nSelecciona si la evaluación será asistida o autónoma:");

        ButtonType btnAsistida = new ButtonType("Asistida");
        ButtonType btnAutonoma = new ButtonType("Autónoma");
        ButtonType btnCancelar = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialogoModo.getButtonTypes().setAll(btnAsistida, btnAutonoma, btnCancelar);
        DialogPane pane = dialogoModo.getDialogPane();
        pane.setMinWidth(420);
        pane.setMinHeight(Region.USE_PREF_SIZE);

        dialogoModo.showAndWait().ifPresent(resultado -> {
            if (resultado == btnCancelar) return;
            if (fila.cita != null) GestorBaseDatos.actualizarEstadoCita(fila.cita.idCita, "EN_EVALUACION");

            SesionEvaluacion.ModoEvaluacion modoElegido = (resultado == btnAutonoma)
                    ? SesionEvaluacion.ModoEvaluacion.AUTONOMA
                    : SesionEvaluacion.ModoEvaluacion.ASISTIDA;

            panel.iniciarTamizajePaciente(fila.paciente, modoElegido);
        });
    }

    private void iniciarCita(GestorBaseDatos.CitaInfo cita) {
        if (cita == null) return;
        GestorBaseDatos.PacienteInfo paciente = GestorBaseDatos.buscarPacientePorCurp(cita.curpPaciente);
        if (paciente == null) return;
        FilaCola fila = filas.stream().filter(f -> paciente.curp.equalsIgnoreCase(f.paciente.curp)).findFirst().orElse(null);
        if (fila != null) iniciarFila(fila);
    }

    @FXML private void mostrarTodos() { aplicarFiltro(Filtro.TODOS); }
    @FXML private void mostrarEspera() { aplicarFiltro(Filtro.ESPERA); }
    @FXML private void mostrarEvaluacion() { aplicarFiltro(Filtro.EVALUACION); }
    @FXML private void mostrarCompletados() { aplicarFiltro(Filtro.COMPLETADO); }
    @FXML private void actualizar() { cargarDatos(); }
}