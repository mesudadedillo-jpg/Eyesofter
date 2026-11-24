package com.eyesofter.ui;

import com.eyesofter.sincronizacion.ClienteSuite;
import com.eyesofter.sincronizacion.ColaSincronizacion;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Optional;

/**
 * Pide la matrícula del portal de un alumno. Permite escribirla o buscarla en el
 * portal por nombre ({@code GET /api/escritorio/alumnos}) para no capturarla mal.
 */
public final class DialogoMatricula {
    private DialogoMatricula() { }

    public static Optional<String> pedir(Window propietario, String curp, String nombre) {
        return elegir(propietario, curp, nombre).map(e -> e.matricula);
    }

    /** Resultado: la matrícula escrita y, si se eligió de la búsqueda, el alumno del portal completo. */
    public static final class Eleccion {
        public final String matricula;
        public final ClienteSuite.AlumnoPortal alumno;
        Eleccion(String matricula, ClienteSuite.AlumnoPortal alumno) { this.matricula = matricula; this.alumno = alumno; }
    }

    /** Como {@link #pedir}, pero devuelve también los datos del alumno elegido para llenar el registro. */
    public static Optional<Eleccion> elegir(Window propietario, String curp, String nombre) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        if (propietario != null) dialogo.initOwner(propietario);
        dialogo.setTitle("Matrícula del portal");
        dialogo.setHeaderText("Matrícula de " + (nombre == null || nombre.isBlank() ? curp : nombre));

        TextField matricula = new TextField(ColaSincronizacion.obtenerMatricula(curp));
        matricula.setPromptText("Ej. PRI2026001");
        matricula.setAccessibleText("Matrícula del alumno en el portal");
        TextField busqueda = new TextField(nombre == null ? "" : nombre);
        busqueda.setPromptText("Nombre o apellido para buscar en el portal");
        HBox.setHgrow(busqueda, Priority.ALWAYS);
        Button buscar = new Button("Buscar en el portal");
        ListView<ClienteSuite.AlumnoPortal> resultados = new ListView<>();
        resultados.setPrefHeight(180);
        resultados.setPlaceholder(new Label(ClienteSuite.estaConfigurado()
                ? "Busque por nombre para elegir al alumno."
                : "Vincule este equipo con el portal (Configuración) para poder buscar."));
        Label estado = new Label();
        estado.setWrapText(true);
        buscar.setDisable(!ClienteSuite.estaConfigurado());
        buscar.setOnAction(e -> {
            estado.setText("Buscando…");
            buscar.setDisable(true);
            String q = busqueda.getText();
            Thread t = new Thread(() -> {
                try {
                    List<ClienteSuite.AlumnoPortal> lista = ClienteSuite.buscarAlumnos(q);
                    Platform.runLater(() -> {
                        resultados.getItems().setAll(lista);
                        estado.setText(lista.isEmpty() ? "No se encontraron alumnos con ese nombre." : lista.size() + " resultado(s). Elija uno.");
                        buscar.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> { estado.setText(ex.getMessage()); buscar.setDisable(false); });
                }
            }, "buscar-alumno-portal");
            t.setDaemon(true);
            t.start();
        });
        ClienteSuite.AlumnoPortal[] elegido = new ClienteSuite.AlumnoPortal[1];
        resultados.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> { if (b != null) { matricula.setText(b.matricula()); elegido[0] = b; } });

        VBox contenido = new VBox(10,
                new Label("Matrícula"), matricula,
                new Label("¿No la sabe? Búsquela:"), new HBox(8, busqueda, buscar), resultados, estado);
        contenido.setPadding(new Insets(6));
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Optional<ButtonType> r = dialogo.showAndWait();
        if (r.isEmpty() || r.get() != ButtonType.OK) return Optional.empty();
        String valor = matricula.getText() == null ? "" : matricula.getText().trim();
        if (curp != null && !curp.isBlank()) ColaSincronizacion.guardarMatricula(curp, valor);
        ClienteSuite.AlumnoPortal alumno = elegido[0] != null && elegido[0].matricula().equalsIgnoreCase(valor) ? elegido[0] : null;
        return Optional.of(new Eleccion(valor, alumno));
    }
}
