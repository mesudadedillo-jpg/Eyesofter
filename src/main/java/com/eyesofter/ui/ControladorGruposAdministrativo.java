package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.db.GestorGrupos;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Grupos escolares: registrar un grupo, inscribir a sus pacientes y pasarlo de año
 * (uno o todos) sin editar paciente por paciente.
 */
public class ControladorGruposAdministrativo implements VistaAdministrativa {

    @FXML private TableView<GestorGrupos.GrupoInfo> tablaGrupos;
    @FXML private TableColumn<GestorGrupos.GrupoInfo, String> colGrupo, colCiclo, colDocente, colPacientes;
    @FXML private TableView<GestorBaseDatos.PacienteInfo> tablaPacientes;
    @FXML private TableColumn<GestorBaseDatos.PacienteInfo, String> colNombre, colCurp, colTutor;
    @FXML private ToggleButton tglActivos, tglEgresados;
    @FXML private Label lblGrupo, lblDetalle, lblSinGrupo;
    @FXML private Button btnInscribir, btnPasar, btnEditar, btnEliminar, btnPasarTodos;

    private ControladorPanelAdministrativo panel;
    private boolean verActivos = true;
    private Integer seleccion;

    @FXML private void initialize() {
        ToggleGroup vista = new ToggleGroup();
        tglActivos.setToggleGroup(vista);
        tglEgresados.setToggleGroup(vista);
        // Que siempre quede uno elegido.
        vista.selectedToggleProperty().addListener((obs, antes, ahora) -> { if (ahora == null) antes.setSelected(true); });

        colGrupo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().etiqueta()));
        colCiclo.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().cicloEscolar()));
        colDocente.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().docente())));
        colPacientes.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().pacientes())));
        colNombre.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().nombre)));
        colCurp.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().curp)));
        colTutor.setCellValueFactory(c -> new SimpleStringProperty(texto(c.getValue().nombreTutor)));
        tablaGrupos.setPlaceholder(new Label("No hay grupos. Use «+ Nuevo grupo»."));
        tablaPacientes.setPlaceholder(new Label("Sin pacientes. Use «+ Inscribir pacientes»."));

        tablaGrupos.getSelectionModel().selectedItemProperty().addListener((obs, antes, g) -> {
            seleccion = g == null ? null : g.id();
            mostrarDetalle(g);
        });

        tablaPacientes.setRowFactory(tabla -> {
            TableRow<GestorBaseDatos.PacienteInfo> fila = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem ficha = new MenuItem("👁 Ver ficha clínica");
            MenuItem quitar = new MenuItem("✕ Quitar del grupo");
            ficha.setOnAction(e -> { if (!fila.isEmpty() && panel != null) panel.mostrarFicha(fila.getItem()); });
            quitar.setOnAction(e -> { if (!fila.isEmpty()) quitarPaciente(fila.getItem()); });
            menu.getItems().addAll(ficha, new SeparatorMenuItem(), quitar);
            menu.setOnShowing(e -> quitar.setDisable(!grupoActivoSeleccionado()));
            fila.contextMenuProperty().bind(javafx.beans.binding.Bindings.when(fila.emptyProperty()).then((ContextMenu) null).otherwise(menu));
            fila.setOnMouseClicked(e -> { if (!fila.isEmpty() && e.getClickCount() == 2 && panel != null) panel.mostrarFicha(fila.getItem()); });
            return fila;
        });

        cargar();
    }

    @Override public void setPanelAdministrativo(ControladorPanelAdministrativo panel) { this.panel = panel; }

    // ----------------------------------------------------------------- carga

    private void cargar() {
        List<GestorGrupos.GrupoInfo> grupos = GestorGrupos.listar(verActivos);
        tablaGrupos.setItems(FXCollections.observableArrayList(grupos));
        btnPasarTodos.setDisable(!verActivos || grupos.isEmpty());
        int sinGrupo = GestorGrupos.contarSinGrupo();
        lblSinGrupo.setText(sinGrupo > 0 ? sinGrupo + " paciente(s) sin grupo: no pasarán de año automáticamente." : "Todos los pacientes tienen grupo.");

        GestorGrupos.GrupoInfo elegido = grupos.stream().filter(g -> seleccion != null && g.id() == seleccion).findFirst()
                .orElse(grupos.isEmpty() ? null : grupos.get(0));
        if (elegido != null) tablaGrupos.getSelectionModel().select(elegido);
        else mostrarDetalle(null);
    }

    private void mostrarDetalle(GestorGrupos.GrupoInfo g) {
        boolean hay = g != null;
        boolean activo = hay && g.activo();
        btnInscribir.setDisable(!activo);
        btnPasar.setDisable(!activo);
        btnEditar.setDisable(!hay);
        btnEliminar.setDisable(!hay);
        if (!hay) {
            lblGrupo.setText("Elija un grupo");
            lblDetalle.setText("");
            tablaPacientes.setItems(FXCollections.observableArrayList());
            return;
        }
        btnPasar.setText(g.esUltimoGrado() ? "🎓 Egresar grupo" : "⇪ Pasar de año");
        lblGrupo.setText((activo ? "" : "Egresado · ") + g.etiqueta());
        StringBuilder d = new StringBuilder("Ciclo " + g.cicloEscolar());
        if (g.docente() != null && !g.docente().isBlank()) d.append(" · Docente: ").append(g.docente());
        if (g.promovidoEn() != null && g.promovidoEn().length() >= 10) d.append(" · Pasó de año el ").append(fechaCorta(g.promovidoEn()));
        lblDetalle.setText(d.toString());
        tablaPacientes.setItems(FXCollections.observableArrayList(GestorGrupos.pacientesDelGrupo(g.id())));
    }

    private boolean grupoActivoSeleccionado() {
        GestorGrupos.GrupoInfo g = tablaGrupos.getSelectionModel().getSelectedItem();
        return g != null && g.activo();
    }

    @FXML private void verActivos() { verActivos = true; seleccion = null; cargar(); }
    @FXML private void verEgresados() { verActivos = false; seleccion = null; cargar(); }

    // ----------------------------------------------------------------- altas y cambios

    @FXML private void nuevoGrupo() {
        formulario(null).ifPresent(d -> {
            try {
                seleccion = GestorGrupos.crear(d.nivel, d.grado, d.letra, d.ciclo, d.docente);
                if (!verActivos) { verActivos = true; tglActivos.setSelected(true); }
                cargar();
                if (GestorGrupos.contarSinGrupo() > 0) inscribir();
            } catch (RuntimeException e) {
                aviso(Alert.AlertType.WARNING, "No se registró el grupo", e.getMessage());
            }
        });
    }

    @FXML private void editarGrupo() {
        GestorGrupos.GrupoInfo g = tablaGrupos.getSelectionModel().getSelectedItem();
        if (g == null) return;
        formulario(g).ifPresent(d -> {
            try {
                GestorGrupos.actualizar(g.id(), d.nivel, d.grado, d.letra, d.ciclo, d.docente);
                cargar();
            } catch (RuntimeException e) {
                aviso(Alert.AlertType.WARNING, "No se guardaron los cambios", e.getMessage());
            }
        });
    }

    @FXML private void eliminarGrupo() {
        GestorGrupos.GrupoInfo g = tablaGrupos.getSelectionModel().getSelectedItem();
        if (g == null) return;
        if (!confirmar("Eliminar grupo " + g.etiqueta(),
                "Sus " + g.pacientes() + " paciente(s) NO se borran: quedan sin grupo con su grado actual.")) return;
        if (GestorGrupos.eliminar(g.id())) { seleccion = null; cargar(); }
        else aviso(Alert.AlertType.ERROR, "Error", "No se pudo eliminar el grupo.");
    }

    @FXML private void inscribir() {
        GestorGrupos.GrupoInfo g = tablaGrupos.getSelectionModel().getSelectedItem();
        if (g == null || !g.activo()) return;
        List<GestorBaseDatos.PacienteInfo> candidatos = GestorGrupos.candidatos(g.id());
        if (candidatos.isEmpty()) {
            aviso(Alert.AlertType.INFORMATION, "Sin pacientes por inscribir", "Todos los pacientes ya están en este grupo. Registre pacientes nuevos en «Pacientes».");
            return;
        }

        Map<String, BooleanProperty> marcados = new HashMap<>();
        for (GestorBaseDatos.PacienteInfo p : candidatos) marcados.put(p.curp, new SimpleBooleanProperty(false));
        FilteredList<GestorBaseDatos.PacienteInfo> filtrados = new FilteredList<>(FXCollections.observableArrayList(candidatos));
        ListView<GestorBaseDatos.PacienteInfo> lista = new ListView<>(filtrados);
        lista.setCellFactory(CheckBoxListCell.forListView(p -> marcados.get(p.curp), new javafx.util.StringConverter<>() {
            @Override public String toString(GestorBaseDatos.PacienteInfo p) {
                String actual = p.idGrupo == null ? "sin grupo" : texto(p.gradoGrupo);
                return p.nombre + "  ·  " + p.curp + "  ·  " + actual;
            }
            @Override public GestorBaseDatos.PacienteInfo fromString(String s) { return null; }
        }));
        lista.setPrefHeight(360);
        VBox.setVgrow(lista, Priority.ALWAYS);

        TextField filtro = new TextField();
        filtro.setPromptText("Filtrar por nombre, CURP o grupo");
        filtro.textProperty().addListener((obs, a, t) -> {
            String q = t == null ? "" : t.trim().toLowerCase(Locale.ROOT);
            filtrados.setPredicate(p -> q.isEmpty() || (p.nombre + " " + p.curp + " " + texto(p.gradoGrupo)).toLowerCase(Locale.ROOT).contains(q));
        });
        Button todos = new Button("Marcar los que no tienen grupo");
        todos.setOnAction(e -> filtrados.forEach(p -> { if (p.idGrupo == null) marcados.get(p.curp).set(true); }));

        Label nota = new Label("Si un paciente ya estaba en otro grupo, se cambia a este.");
        nota.setWrapText(true);
        VBox contenido = new VBox(10, filtro, todos, lista, nota);
        contenido.setPrefWidth(620);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Inscribir en " + g.etiqueta());
        dialogo.setHeaderText("Marque a los pacientes del grupo " + g.etiqueta());
        dialogo.getDialogPane().setContent(contenido);
        ButtonType aceptar = new ButtonType("Inscribir marcados", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, aceptar);
        dialogo.setResizable(true);

        dialogo.showAndWait().filter(r -> r == aceptar).ifPresent(r -> {
            List<String> curps = new ArrayList<>();
            marcados.forEach((curp, marcado) -> { if (marcado.get()) curps.add(curp); });
            if (curps.isEmpty()) return;
            try {
                int n = GestorGrupos.inscribir(g.id(), curps);
                cargar();
                aviso(Alert.AlertType.INFORMATION, "Pacientes inscritos", n + " paciente(s) quedaron en " + g.etiqueta() + ".");
            } catch (RuntimeException e) {
                aviso(Alert.AlertType.ERROR, "No se pudo inscribir", e.getMessage());
            }
        });
    }

    private void quitarPaciente(GestorBaseDatos.PacienteInfo p) {
        if (!confirmar("Quitar a " + p.nombre + " del grupo", "Conserva su grado actual, pero ya no pasará de año con el grupo.")) return;
        if (GestorGrupos.quitar(p.curp)) cargar();
    }

    // ----------------------------------------------------------------- pasar de año

    @FXML private void pasarGrupo() {
        GestorGrupos.GrupoInfo g = tablaGrupos.getSelectionModel().getSelectedItem();
        if (g == null || !g.activo()) return;
        String mensaje = g.esUltimoGrado()
                ? g.etiqueta() + " es el último grado de " + g.nivel().toLowerCase(Locale.ROOT) + ". El grupo se cerrará y sus "
                  + g.pacientes() + " paciente(s) quedarán como egresados. Su historial se conserva."
                : "El grupo y sus " + g.pacientes() + " paciente(s) pasarán a " + GestorGrupos.etiqueta(g.nivel(), g.grado() + 1, g.letra())
                  + ", ciclo " + GestorGrupos.cicloSiguiente(g.cicloEscolar()) + ".";
        if (!confirmar(g.esUltimoGrado() ? "Egresar " + g.etiqueta() : "Pasar de año " + g.etiqueta(), mensaje)) return;

        GestorGrupos.Resultado r = GestorGrupos.promover(g.id());
        if (!r.exito()) { aviso(Alert.AlertType.WARNING, "No se pasó de año", String.join("\n", r.avisos())); return; }
        cargar();
        aviso(Alert.AlertType.INFORMATION, "Listo", r.gruposEgresados() > 0
                ? r.pacientesEgresados() + " paciente(s) quedaron como egresados."
                : "El grupo pasó de año con sus " + r.pacientesMovidos() + " paciente(s).");
    }

    @FXML private void pasarTodos() {
        List<GestorGrupos.GrupoInfo> grupos = GestorGrupos.listar(true);
        if (grupos.isEmpty()) return;
        StringBuilder resumen = new StringBuilder();
        for (GestorGrupos.GrupoInfo g : grupos) {
            resumen.append(g.esUltimoGrado()
                    ? "🎓 " + g.etiqueta() + " egresa"
                    : "⇪ " + g.etiqueta() + " → " + (g.grado() + 1) + "° " + g.letra())
                    .append("  (").append(g.pacientes()).append(" pacientes)\n");
        }

        TextArea detalle = new TextArea(resumen.toString());
        detalle.setEditable(false);
        detalle.setPrefRowCount(Math.min(12, grupos.size() + 1));
        TextField confirmacion = new TextField();
        confirmacion.setPromptText("Escriba PASAR DE AÑO");
        Label nota = new Label("Úselo una vez al cerrar el ciclo escolar. Si algún grupo no puede pasar, no se cambia ninguno. "
                + "Los pacientes sin grupo no cambian.");
        nota.setWrapText(true);
        VBox contenido = new VBox(10, nota, detalle, new Label("Para confirmar escriba PASAR DE AÑO:"), confirmacion);
        contenido.setPrefWidth(520);

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Pasar de año a todos los grupos");
        dialogo.setHeaderText(grupos.size() + " grupo(s) activos");
        dialogo.getDialogPane().setContent(contenido);
        ButtonType aceptar = new ButtonType("Pasar de año", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, aceptar);
        Button botonAceptar = (Button) dialogo.getDialogPane().lookupButton(aceptar);
        botonAceptar.setDisable(true);
        confirmacion.textProperty().addListener((obs, a, t) ->
                botonAceptar.setDisable(t == null || !t.trim().equalsIgnoreCase("PASAR DE AÑO")));

        dialogo.showAndWait().filter(r -> r == aceptar).ifPresent(x -> {
            GestorGrupos.Resultado r = GestorGrupos.promoverTodos(null);
            if (!r.exito()) {
                aviso(Alert.AlertType.WARNING, "No se cambió ningún grupo", String.join("\n", r.avisos()));
                return;
            }
            cargar();
            aviso(Alert.AlertType.INFORMATION, "Ciclo cerrado",
                    r.gruposPromovidos() + " grupo(s) pasaron de año con " + r.pacientesMovidos() + " paciente(s).\n"
                    + r.gruposEgresados() + " grupo(s) egresaron (" + r.pacientesEgresados() + " paciente(s)).");
        });
    }

    // ----------------------------------------------------------------- formulario

    private static final class DatosGrupo {
        String nivel, letra, ciclo, docente;
        int grado;
    }

    private Optional<DatosGrupo> formulario(GestorGrupos.GrupoInfo actual) {
        ComboBox<String> nivel = new ComboBox<>(FXCollections.observableArrayList(GestorGrupos.NIVELES));
        nivel.setValue(actual == null ? "Primaria" : actual.nivel());
        Spinner<Integer> grado = new Spinner<>(1, 6, actual == null ? 1 : actual.grado());
        grado.setEditable(true);
        ComboBox<String> letra = new ComboBox<>(FXCollections.observableArrayList("A", "B", "C", "D", "E", "F", "G", "H"));
        letra.setEditable(true);
        letra.setValue(actual == null ? "A" : actual.letra());
        TextField ciclo = new TextField(actual == null ? GestorGrupos.cicloActual() : actual.cicloEscolar());
        ciclo.setPromptText("2026-2027");
        TextField docente = new TextField(actual == null || actual.docente() == null ? "" : actual.docente());
        docente.setPromptText("Opcional");

        GridPane campos = new GridPane();
        campos.setHgap(12);
        campos.setVgap(10);
        campos.addRow(0, new Label("Nivel *"), nivel);
        campos.addRow(1, new Label("Grado *"), grado);
        campos.addRow(2, new Label("Grupo (letra) *"), letra);
        campos.addRow(3, new Label("Ciclo escolar *"), ciclo);
        campos.addRow(4, new Label("Docente titular"), docente);
        if (actual != null && actual.activo()) {
            Label nota = new Label("Si cambia el nivel, el grado o la letra, sus pacientes se actualizan con él.");
            nota.setWrapText(true);
            campos.add(nota, 0, 5, 2, 1);
        }

        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle(actual == null ? "Nuevo grupo" : "Editar grupo " + actual.etiqueta());
        dialogo.getDialogPane().setContent(campos);
        ButtonType guardar = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, guardar);
        dialogo.getDialogPane().setMinWidth(460);

        // Validación antes de cerrar: si hay error, el diálogo sigue abierto.
        Button botonGuardar = (Button) dialogo.getDialogPane().lookupButton(guardar);
        botonGuardar.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            try { // aplica lo escrito en el spinner aunque no se haya pulsado Enter
                grado.getValueFactory().setValue(grado.getValueFactory().getConverter().fromString(grado.getEditor().getText().trim()));
            } catch (RuntimeException ignorado) { grado.getEditor().setText(String.valueOf(grado.getValue())); }
            String error = GestorGrupos.validar(nivel.getValue(), grado.getValue(), letra.getValue(), ciclo.getText());
            if (error != null) { aviso(Alert.AlertType.WARNING, "Revise los datos", error); e.consume(); }
        });

        return dialogo.showAndWait().filter(r -> r == guardar).map(r -> {
            DatosGrupo d = new DatosGrupo();
            d.nivel = nivel.getValue();
            d.grado = grado.getValue();
            d.letra = letra.getValue() == null ? "" : letra.getValue().trim().toUpperCase(Locale.ROOT);
            d.ciclo = ciclo.getText().trim();
            d.docente = docente.getText();
            return d;
        });
    }

    // ----------------------------------------------------------------- utilidades

    private static String texto(String v) { return v == null || v.isBlank() ? "—" : v; }

    private static String fechaCorta(String iso) {
        // "2027-07-10T18:00:00" → "10/07/2027"
        return iso.substring(8, 10) + "/" + iso.substring(5, 7) + "/" + iso.substring(0, 4);
    }

    private boolean confirmar(String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, mensaje, ButtonType.CANCEL, ButtonType.OK);
        alerta.setHeaderText(titulo);
        return alerta.showAndWait().filter(r -> r == ButtonType.OK).isPresent();
    }

    private void aviso(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alerta = new Alert(tipo, mensaje, ButtonType.OK);
        alerta.setHeaderText(titulo);
        alerta.showAndWait();
    }
}
