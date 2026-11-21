package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.reportes.GeneradorPDF;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import javafx.stage.Stage;
import java.nio.file.*;

/** Reporte local calculado solo con evaluaciones y citas existentes. */
public class ControladorReportesAdministrativo implements VistaAdministrativa {
    @FXML private Label lblEvaluaciones, lblAtendidos, lblCancelados, lblEspera, lblRegistros, lblSinDatos;
    @FXML private ComboBox<String> cmbPeriodo;
    @FXML private DatePicker dpInicio, dpFin;
    @FXML private TableView<GestorBaseDatos.EvaluacionInfo> tabla;
    @FXML private TableColumn<GestorBaseDatos.EvaluacionInfo, String> colFolio, colPaciente, colFecha, colOD, colOI, colIshihara, colResultado;
    @FXML private ListView<String> listaPdfs;

    @FXML private void initialize() {
        cmbPeriodo.setItems(FXCollections.observableArrayList("Hoy", "Esta semana", "Este mes", "Rango personalizado")); cmbPeriodo.setValue("Este mes");
        dpInicio.setValue(LocalDate.now().withDayOfMonth(1)); dpFin.setValue(LocalDate.now());
        cmbPeriodo.setOnAction(e -> aplicarPeriodo()); dpInicio.setOnAction(e -> cargar()); dpFin.setOnAction(e -> cargar());
        colFolio.setCellValueFactory(c -> texto("EV-" + c.getValue().idEvaluacion));
        colPaciente.setCellValueFactory(c -> texto(c.getValue().nombrePaciente));
        colFecha.setCellValueFactory(c -> texto(c.getValue().fechaHora));
        colOD.setCellValueFactory(c -> texto(valor(c.getValue().agudezaDer)));
        colOI.setCellValueFactory(c -> texto(valor(c.getValue().agudezaIzq)));
        colIshihara.setCellValueFactory(c -> texto(valor(c.getValue().resultadoIshihara)));
        colResultado.setCellValueFactory(c -> texto(valor(c.getValue().recomendacion)));
        cargar();
        Timeline actualizacion = new Timeline(new KeyFrame(javafx.util.Duration.seconds(2), e -> cargar()));
        actualizacion.setCycleCount(Timeline.INDEFINITE); actualizacion.play();
    }
    private void aplicarPeriodo() {
        LocalDate hoy = LocalDate.now();
        switch (cmbPeriodo.getValue()) {
            case "Hoy" -> { dpInicio.setValue(hoy); dpFin.setValue(hoy); }
            case "Esta semana" -> { dpInicio.setValue(hoy.minusDays(hoy.getDayOfWeek().getValue() - 1)); dpFin.setValue(hoy); }
            case "Este mes" -> { dpInicio.setValue(hoy.withDayOfMonth(1)); dpFin.setValue(hoy); }
            default -> { }
        }
        cargar();
    }
    private void cargar() {
        LocalDate inicio = dpInicio.getValue(), fin = dpFin.getValue();
        List<GestorBaseDatos.EvaluacionInfo> evaluaciones = GestorBaseDatos.obtenerHistorialEvaluaciones().stream().filter(e -> dentroDelPeriodo(e.fechaHora, inicio, fin)).toList();
        lblEvaluaciones.setText(String.valueOf(evaluaciones.size()));
        lblAtendidos.setText(String.valueOf(new HashSet<>(evaluaciones.stream().map(e -> e.curpPaciente).toList()).size()));
        lblCancelados.setText("0"); // no existe un estado cancelado persistido; no se inventa un valor.
        lblEspera.setText(String.valueOf(GestorBaseDatos.obtenerCitas(LocalDate.now()).stream().filter(c -> "EN_ESPERA".equalsIgnoreCase(c.estado)).count()));
        tabla.setItems(FXCollections.observableArrayList(evaluaciones));
        lblRegistros.setText(evaluaciones.size() + (evaluaciones.size() == 1 ? " registro" : " registros"));
        lblSinDatos.setVisible(evaluaciones.isEmpty()); lblSinDatos.setManaged(evaluaciones.isEmpty());
        File carpeta = new File("expedientes_pdf"); String[] archivos = carpeta.isDirectory() ? carpeta.list((dir, nombre) -> nombre.toLowerCase().endsWith(".pdf")) : new String[0];
        listaPdfs.setItems(FXCollections.observableArrayList(archivos == null ? List.of() : Arrays.stream(archivos).sorted().toList()));
        listaPdfs.setOnMouseClicked(e -> { if (e.getClickCount() == 2) abrirPdf(); });
        tabla.setOnMouseClicked(e -> { if (e.getClickCount() == 2) verEvaluacionSeleccionada(); });
        MenuItem ver = new MenuItem("◈ Ver expediente dinámico");
        ver.setOnAction(e -> verEvaluacionSeleccionada());
        tabla.setContextMenu(new ContextMenu(ver));
    }
    private boolean dentroDelPeriodo(String fechaHora, LocalDate inicio, LocalDate fin) {
        try { LocalDate fecha = LocalDate.parse(fechaHora.substring(0, 10)); return (inicio == null || !fecha.isBefore(inicio)) && (fin == null || !fecha.isAfter(fin)); }
        catch (Exception e) { return false; }
    }
    private String valor(String valor) { return valor == null || valor.isBlank() ? "N/D" : valor; }
    private SimpleStringProperty texto(String valor) { return new SimpleStringProperty(valor(valor)); }
    @FXML private void actualizar() { cargar(); }
    @FXML private void abrirPdf() {
        String seleccionado = listaPdfs.getSelectionModel().getSelectedItem();
        if (seleccionado == null) { aviso(Alert.AlertType.INFORMATION, "Selecciona un PDF", "Elige un expediente de la lista para abrirlo."); return; }
        File archivo = new File("expedientes_pdf", seleccionado);
        try {
            if (!archivo.isFile()) throw new IllegalStateException("El archivo no está disponible en este equipo.");
            VisorPdfWindow.mostrar((Stage) listaPdfs.getScene().getWindow(), archivo);
        } catch (Exception e) { aviso(Alert.AlertType.ERROR, "No se pudo abrir el PDF", e.getMessage()); }
    }
    private void verEvaluacionSeleccionada() {
        GestorBaseDatos.EvaluacionInfo evaluacion = tabla.getSelectionModel().getSelectedItem();
        if (evaluacion == null) { aviso(Alert.AlertType.INFORMATION, "Selecciona una evaluación", "Haz clic sobre una evaluación para verla."); return; }
        try {
            GestorBaseDatos.PacienteInfo paciente = GestorBaseDatos.buscarPacientePorCurp(evaluacion.curpPaciente);
            if (paciente == null) throw new IllegalStateException("No se encontró la persona paciente asociada.");
            File temporal = File.createTempFile("eyesofter-expediente-", ".pdf"); temporal.deleteOnExit();
            File generado = GeneradorPDF.generarReporteEn(paciente, evaluacion, temporal);
            VisorPdfWindow.mostrar((Stage) tabla.getScene().getWindow(), generado);
        } catch (Exception ex) { aviso(Alert.AlertType.ERROR, "No se pudo preparar la vista previa", ex.getMessage()); }
    }
    @FXML private void abrirDescargados() {
        Dialog<ButtonType> dialogo = new Dialog<>(); dialogo.setTitle("◈ PDFs descargados"); dialogo.setResizable(true);
        ListView<String> lista = new ListView<>(listaPdfs.getItems()); lista.setPrefSize(760, 420);
        Button ver = new Button("▣ Ver"); Button eliminar = new Button("⌫ Eliminar"); Button memoria = new Button("⇩ Copiar a memoria"); Button cerrar = new Button("✕ Cerrar");
        HBox acciones = new HBox(10, ver, eliminar, memoria); javafx.scene.layout.VBox contenido = new javafx.scene.layout.VBox(12, new Label("Selecciona un expediente"), lista, acciones); contenido.setPadding(new javafx.geometry.Insets(12));
        dialogo.getDialogPane().setContent(contenido); dialogo.getDialogPane().getButtonTypes().add(cerrar.getText().equals("✕ Cerrar") ? ButtonType.CLOSE : ButtonType.CLOSE);
        ver.setOnAction(e -> { String n = lista.getSelectionModel().getSelectedItem(); if (n != null) VisorPdfWindow.mostrar((Stage) dialogo.getDialogPane().getScene().getWindow(), new File("expedientes_pdf", n)); });
        eliminar.setOnAction(e -> { String n = lista.getSelectionModel().getSelectedItem(); if (n != null) { try { Files.deleteIfExists(new File("expedientes_pdf", n).toPath()); lista.getItems().remove(n); cargar(); } catch (Exception ex) { aviso(Alert.AlertType.ERROR, "No se pudo eliminar", ex.getMessage()); } } });
        memoria.setOnAction(e -> { String n = lista.getSelectionModel().getSelectedItem(); if (n != null) copiarAMemoria(new File("expedientes_pdf", n)); });
        dialogo.showAndWait();
    }
    private void copiarAMemoria(File archivo) {
        try {
            Path destino = null;
            String sistema = new File(System.getProperty("user.dir")).toPath().getRoot().toString();
            for (File raiz : File.listRoots()) {
                if (!raiz.toPath().toString().equalsIgnoreCase(sistema) && raiz.canWrite()) { destino = raiz.toPath(); break; }
            }
            if (destino == null) { aviso(Alert.AlertType.INFORMATION, "Memoria no encontrada", "Conecta una memoria USB para copiar el expediente."); return; }
            if (destino == null) { aviso(Alert.AlertType.INFORMATION, "Memoria no encontrada", "Conecta una memoria USB para copiar el expediente."); return; }
            Path carpeta = destino.resolve("EYESOFTER").resolve("Expedientes"); Files.createDirectories(carpeta); Files.copy(archivo.toPath(), carpeta.resolve(archivo.getName()), StandardCopyOption.REPLACE_EXISTING); aviso(Alert.AlertType.INFORMATION, "Copia completada", "Guardado en EYESOFTER/Expedientes.");
        } catch (Exception e) { aviso(Alert.AlertType.ERROR, "No se pudo copiar", e.getMessage()); }
    }
    private void aviso(Alert.AlertType tipo, String titulo, String mensaje) { Alert alerta = new Alert(tipo, mensaje, ButtonType.OK); alerta.setHeaderText(titulo); alerta.showAndWait(); }
    @Override public void setPanelAdministrativo(ControladorPanelAdministrativo panel) { }
}
