package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.reportes.GeneradorPDF;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;

/** Búsqueda y gestión del historial de evaluaciones clínicas. */
public class ControladorHistorialAdministrativo implements VistaAdministrativa {

    @FXML private TextField txtBuscar, txtFiltroCurp, txtFiltroNombre, txtFiltroGrado;
    @FXML private ListView<GestorBaseDatos.PacienteInfo> listaCoincidencias;
    @FXML private Label lblAyuda, lblSeleccion, lblRegistros;
    @FXML private TableView<GestorBaseDatos.EvaluacionInfo> tabla;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colFolio, colFecha, colSnellen, colIshihara, colEstado;
    @FXML private HBox panelSeleccion;
    @FXML private VBox panelHistorial;

    private GestorBaseDatos.PacienteInfo pacienteSeleccionado;

    @FXML private void initialize() {
        colFolio.setCellValueFactory(c -> texto("EV-" + c.getValue().idEvaluacion));
        colFecha.setCellValueFactory(c -> texto(c.getValue().fechaHora));
        colSnellen.setCellValueFactory(c -> texto("OD " + valor(c.getValue().agudezaDer) + " · OI " + valor(c.getValue().agudezaIzq)));
        colIshihara.setCellValueFactory(c -> texto(valor(c.getValue().resultadoIshihara)));
        colEstado.setCellValueFactory(c -> texto(valor(c.getValue().recomendacion)));

        listaCoincidencias.setCellFactory(lista -> new ListCell<>() {
            @Override protected void updateItem(GestorBaseDatos.PacienteInfo item, boolean vacio) {
                super.updateItem(item, vacio);
                setText(vacio || item == null ? null : item.curp + " — " + item.nombre);
            }
        });

        listaCoincidencias.getSelectionModel().selectedItemProperty().addListener((obs, anterior, paciente) -> seleccionarPaciente(paciente));
        txtBuscar.textProperty().addListener((obs, anterior, texto) -> buscar());

        for (TextField campo : new TextField[]{txtFiltroCurp, txtFiltroNombre, txtFiltroGrado}) {
            if (campo != null) campo.textProperty().addListener((obs, a, b) -> buscar());
        }

        // Menú contextual para Generar PDF o Eliminar Evaluación
        tabla.setRowFactory(t -> {
            TableRow<GestorBaseDatos.EvaluacionInfo> fila = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem mPdf = new MenuItem("📄 Generar / Ver PDF");
            MenuItem mEliminar = new MenuItem("🗑 Eliminar Evaluación");

            mPdf.setOnAction(e -> generarPdf());
            mEliminar.setOnAction(e -> {
                if (!fila.isEmpty()) confirmarEliminarEvaluacion(fila.getItem());
            });

            menu.getItems().addAll(mPdf, new SeparatorMenuItem(), mEliminar);
            fila.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu)
            );
            return fila;
        });

        panelSeleccion.setVisible(false); panelSeleccion.setManaged(false);
        panelHistorial.setVisible(false); panelHistorial.setManaged(false);
        buscar();
    }

    private void buscar() {
        String texto = txtBuscar.getText();
        String filtroCurp = textoDe(txtFiltroCurp);
        String filtroNombre = textoDe(txtFiltroNombre);
        String filtroGrado = textoDe(txtFiltroGrado);
        boolean vacio = (texto == null || texto.isBlank()) && filtroCurp.isBlank() && filtroNombre.isBlank() && filtroGrado.isBlank();
        var coincidencias = vacio ? java.util.List.<GestorBaseDatos.PacienteInfo>of() : GestorBaseDatos.obtenerPacientes(texto, filtroCurp, filtroNombre, filtroGrado, "", "", "");
        listaCoincidencias.setItems(FXCollections.observableArrayList(coincidencias));
        lblAyuda.setText(texto == null || texto.isBlank() ? "Selecciona un paciente para ver su historial de evaluaciones." :
                coincidencias.isEmpty() ? "No se encontraron pacientes con esa búsqueda." : "Selecciona una coincidencia para abrir su historial.");
    }

    private void seleccionarPaciente(GestorBaseDatos.PacienteInfo paciente) {
        if (paciente == null) return;
        pacienteSeleccionado = paciente;
        lblSeleccion.setText(paciente.curp + " · " + paciente.nombre + " · " + valor(paciente.gradoGrupo));
        refrescarTabla();
        panelSeleccion.setVisible(true); panelSeleccion.setManaged(true);
        panelHistorial.setVisible(true); panelHistorial.setManaged(true);
    }

    private void refrescarTabla() {
        if (pacienteSeleccionado == null) return;
        var evaluaciones = GestorBaseDatos.obtenerEvaluacionesPaciente(pacienteSeleccionado.curp);
        tabla.setItems(FXCollections.observableArrayList(evaluaciones));
        lblRegistros.setText(evaluaciones.size() + (evaluaciones.size() == 1 ? " registro encontrado" : " registros encontrados"));
    }

    private void confirmarEliminarEvaluacion(GestorBaseDatos.EvaluacionInfo eval) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, "¿Eliminar la evaluación EV-" + eval.idEvaluacion + "?", ButtonType.CANCEL, ButtonType.OK);
        alerta.setHeaderText("Confirmar eliminación de evaluación");
        alerta.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            boolean exito = GestorBaseDatos.eliminarEvaluacion(eval.idEvaluacion);
            if (exito) {
                mostrar("Evaluación eliminada", "El registro ha sido retirado.", Alert.AlertType.INFORMATION);
                refrescarTabla();
            } else {
                mostrar("Error", "No se pudo eliminar la evaluación.", Alert.AlertType.ERROR);
            }
        });
    }

    private SimpleStringProperty texto(String valor) { return new SimpleStringProperty(valor(valor)); }
    private String valor(String valor) { return valor == null || valor.isBlank() ? "N/D" : valor; }

    @FXML private void limpiar() {
        pacienteSeleccionado = null; txtBuscar.clear();
        limpiarCampo(txtFiltroCurp); limpiarCampo(txtFiltroNombre); limpiarCampo(txtFiltroGrado);
        listaCoincidencias.getSelectionModel().clearSelection(); tabla.getItems().clear();
        panelSeleccion.setVisible(false); panelSeleccion.setManaged(false);
        panelHistorial.setVisible(false); panelHistorial.setManaged(false);
    }

    private String textoDe(TextField campo) { return campo == null || campo.getText() == null ? "" : campo.getText(); }
    private void limpiarCampo(TextField campo) { if (campo != null) campo.clear(); }

    @FXML private void generarPdf() {
        GestorBaseDatos.EvaluacionInfo evaluacion = tabla.getSelectionModel().getSelectedItem();
        if (pacienteSeleccionado == null || evaluacion == null) {
            mostrar("Selecciona una evaluación", "Elige una evaluación del historial para generar su PDF.", Alert.AlertType.INFORMATION);
            return;
        }
        try {
            File archivo = GeneradorPDF.generarReporte(pacienteSeleccionado, evaluacion);
            GestorBaseDatos.registrarReportePdf(evaluacion.idEvaluacion, archivo.getAbsolutePath());
            mostrar("PDF generado", "Se guardó en: " + archivo.getAbsolutePath(), Alert.AlertType.INFORMATION);
        } catch (Exception e) {
            mostrar("No se pudo generar el PDF", e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void mostrar(String titulo, String mensaje, Alert.AlertType tipo) {
        Alert alerta = new Alert(tipo, mensaje, ButtonType.OK);
        alerta.setHeaderText(titulo);
        alerta.showAndWait();
    }

    @Override public void setPanelAdministrativo(ControladorPanelAdministrativo panel) { }
}