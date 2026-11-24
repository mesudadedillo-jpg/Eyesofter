package com.eyesofter.ui;

import com.eyesofter.sincronizacion.ClienteSuite;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Jornada de tamizaje en la escuela: se elige un grupo del portal y se pasa a los alumnos
 * uno por uno. Cada alumno muestra si ya se evaluó en este ciclo o si sigue pendiente de
 * atención con el especialista (para recordarlo al tutor). El grupo elegido se recuerda
 * mientras la aplicación siga abierta, así al terminar una prueba se sigue con el siguiente.
 */
public final class DialogoJornadaGrupo {
    private DialogoJornadaGrupo() { }

    /** Grupo de la jornada en curso (se conserva entre pacientes). */
    private static ClienteSuite.GrupoPortal grupoActual;
    /** Matrículas evaluadas en ESTE equipo durante la jornada (aunque el portal aún no las reciba). */
    private static final java.util.Set<String> evaluadosHoy = new java.util.HashSet<>();

    /** Lo llama el registro al iniciar la prueba, para marcar al alumno en la lista. */
    public static void marcarEvaluado(String matricula) {
        if (matricula != null && !matricula.isBlank()) evaluadosHoy.add(matricula.trim().toUpperCase(Locale.ROOT));
    }

    public static boolean hayJornadaEnCurso() { return grupoActual != null; }

    public static Optional<ClienteSuite.AlumnoPortal> elegir(Window propietario) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        if (propietario != null) dialogo.initOwner(propietario);
        dialogo.setTitle("Jornada por grupo");
        dialogo.setHeaderText("Elija el grupo y luego al alumno que va a pasar");

        ComboBox<ClienteSuite.GrupoPortal> grupos = new ComboBox<>();
        grupos.setPromptText("Cargando grupos del portal…");
        grupos.setMaxWidth(Double.MAX_VALUE);
        grupos.setAccessibleText("Grupo de la escuela");
        TextField filtro = new TextField();
        filtro.setPromptText("Filtrar por nombre o matrícula");
        HBox.setHgrow(filtro, Priority.ALWAYS);
        javafx.scene.control.CheckBox soloPendientes = new javafx.scene.control.CheckBox("Sólo los que faltan");
        soloPendientes.setSelected(true);

        ListView<ClienteSuite.AlumnoPortal> alumnos = new ListView<>();
        alumnos.setPrefSize(560, 320);
        alumnos.setPlaceholder(new Label("Elija un grupo."));
        alumnos.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(ClienteSuite.AlumnoPortal a, boolean vacio) {
                super.updateItem(a, vacio);
                if (vacio || a == null) { setText(null); setStyle(""); return; }
                boolean hoy = evaluadosHoy.contains(a.matricula().toUpperCase(Locale.ROOT));
                String marca = hoy ? "✔ Evaluado hoy" : a.estadoSeguimiento();
                setText(a.apellidos() + " " + a.nombre() + "  ·  " + a.matricula() + "\n    " + marca
                        + (a.usaLentes() ? "  ·  usa lentes" : ""));
                setStyle(hoy ? "-fx-opacity: 0.6;" : a.pendienteEspecialista() ? "-fx-text-fill: #b45309;" : "");
            }
        });
        Label estado = new Label();
        estado.setWrapText(true);
        Label avance = new Label();

        List<ClienteSuite.AlumnoPortal>[] cargados = new List[]{List.of()};
        Runnable aplicarFiltro = () -> {
            String f = filtro.getText() == null ? "" : filtro.getText().trim().toLowerCase(Locale.ROOT);
            alumnos.getItems().setAll(cargados[0].stream()
                    .filter(a -> !soloPendientes.isSelected() || !evaluadosHoy.contains(a.matricula().toUpperCase(Locale.ROOT)))
                    .filter(a -> f.isEmpty() || (a.nombreCompleto() + " " + a.matricula()).toLowerCase(Locale.ROOT).contains(f))
                    .toList());
            long hechos = cargados[0].stream().filter(a -> evaluadosHoy.contains(a.matricula().toUpperCase(Locale.ROOT))).count();
            long pendientesEsp = cargados[0].stream().filter(ClienteSuite.AlumnoPortal::pendienteEspecialista).count();
            avance.setText(cargados[0].isEmpty() ? "" : hechos + " de " + cargados[0].size() + " evaluados hoy en este equipo"
                    + (pendientesEsp > 0 ? " · " + pendientesEsp + " con caso pendiente de especialista" : ""));
        };
        filtro.textProperty().addListener((o, a, b) -> aplicarFiltro.run());
        soloPendientes.selectedProperty().addListener((o, a, b) -> aplicarFiltro.run());

        grupos.valueProperty().addListener((o, a, g) -> {
            if (g == null) return;
            grupoActual = g;
            estado.setText("Cargando la lista de " + g.grado() + "° " + g.grupo() + "…");
            enSegundoPlano(() -> ClienteSuite.alumnosDelGrupo(g), lista -> {
                cargados[0] = lista;
                aplicarFiltro.run();
                estado.setText(lista.isEmpty() ? "El grupo no tiene alumnos registrados en el portal." : "");
            }, estado);
        });

        if (!ClienteSuite.estaConfigurado()) {
            estado.setText("Vincule este equipo con el portal (Configuración → Conexión con EYESOFTER Suite) para usar las listas de grupo.");
            grupos.setDisable(true);
        } else {
            enSegundoPlano(ClienteSuite::grupos, lista -> {
                grupos.getItems().setAll(lista);
                grupos.setPromptText(lista.isEmpty() ? "La escuela no tiene grupos en el portal" : "Elija el grupo");
                if (grupoActual != null) lista.stream().filter(g -> mismoGrupo(g, grupoActual)).findFirst().ifPresent(grupos::setValue);
            }, estado);
        }

        Button actualizar = new Button("Actualizar");
        actualizar.setOnAction(e -> { ClienteSuite.GrupoPortal g = grupos.getValue(); grupos.setValue(null); grupos.setValue(g); });

        VBox contenido = new VBox(10, new Label("Grupo"), new HBox(8, grupos, actualizar),
                new HBox(8, filtro, soloPendientes), alumnos, avance, estado);
        HBox.setHgrow(grupos, Priority.ALWAYS);
        contenido.setPadding(new Insets(6));
        dialogo.getDialogPane().setContent(contenido);
        ButtonType usar = new ButtonType("Evaluar a este alumno", ButtonBar.ButtonData.OK_DONE);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, usar);
        dialogo.getDialogPane().lookupButton(usar).disableProperty().bind(alumnos.getSelectionModel().selectedItemProperty().isNull());
        alumnos.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && alumnos.getSelectionModel().getSelectedItem() != null) {
                ((Button) dialogo.getDialogPane().lookupButton(usar)).fire();
            }
        });

        Optional<ButtonType> r = dialogo.showAndWait();
        if (r.isEmpty() || r.get() != usar) return Optional.empty();
        return Optional.ofNullable(alumnos.getSelectionModel().getSelectedItem());
    }

    private static boolean mismoGrupo(ClienteSuite.GrupoPortal a, ClienteSuite.GrupoPortal b) {
        return a.grado() == b.grado() && a.nivel().equalsIgnoreCase(b.nivel()) && a.grupo().equalsIgnoreCase(b.grupo());
    }

    private static <T> void enSegundoPlano(Supplier<T> tarea, java.util.function.Consumer<T> alTerminar, Label estado) {
        Thread t = new Thread(() -> {
            try {
                T valor = tarea.get();
                Platform.runLater(() -> alTerminar.accept(valor));
            } catch (Exception ex) {
                Platform.runLater(() -> estado.setText(ex.getMessage() == null ? "No se pudo consultar el portal. Revise la conexión a internet." : ex.getMessage()));
            }
        }, "jornada-grupo");
        t.setDaemon(true);
        t.start();
    }
}
