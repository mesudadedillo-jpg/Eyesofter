package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.sincronizacion.ClienteSuite;
import com.eyesofter.sincronizacion.ColaSincronizacion;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tarjeta de Configuración: conexión con el servidor de EYESOFTER Suite y cola de envíos
 * (C-RFN-02, C-RFNN-03). El operador ve qué tamizajes se enviaron, cuáles esperan
 * reintento y cuáles necesitan una corrección (matrícula).
 */
final class TarjetaConexionSuite {
    private TarjetaConexionSuite() { }

    static VBox crear() {
        Label titulo = new Label("Conexión con EYESOFTER Suite");
        titulo.getStyleClass().add("subtitulo-seccion");
        Label descripcion = new Label("Cada tamizaje terminado se envía al portal para que Enfermería dé seguimiento y el tutor reciba el aviso. Si no hay internet, se guarda y se reenvía solo.");
        descripcion.setWrapText(true);
        descripcion.getStyleClass().add("etiqueta-subtitulo");

        TextField url = new TextField(AppConfig.URL_API_SUITE);
        url.setPromptText("https://servidor-de-eyesofter.up.railway.app");
        url.setAccessibleText("Dirección del servidor");
        PasswordField clave = new PasswordField();
        clave.setText(AppConfig.CLAVE_API_ESCRITORIO);
        clave.setPromptText("Clave del escritorio (la entrega Dirección)");
        clave.setAccessibleText("Clave del escritorio");
        TextField equipo = new TextField(AppConfig.NOMBRE_EQUIPO);
        equipo.setAccessibleText("Nombre de este equipo");
        GridPane campos = new GridPane();
        campos.setHgap(12);
        campos.setVgap(10);
        campos.addRow(0, new Label("Dirección del servidor"), url);
        campos.addRow(1, new Label("Clave del escritorio"), clave);
        campos.addRow(2, new Label("Nombre de este equipo"), equipo);
        GridPane.setHgrow(url, Priority.ALWAYS);
        GridPane.setHgrow(clave, Priority.ALWAYS);
        GridPane.setHgrow(equipo, Priority.ALWAYS);

        Label estado = new Label(ColaSincronizacion.getUltimoResultado());
        estado.setWrapText(true);
        estado.getStyleClass().add("etiqueta-subtitulo");

        TableView<ColaSincronizacion.Envio> tabla = new TableView<>();
        tabla.setPrefHeight(220);
        tabla.setPlaceholder(new Label("Aún no hay tamizajes enviados desde este equipo."));
        tabla.getColumns().add(columna("Folio", e -> e.folio(), 150));
        tabla.getColumns().add(columna("Alumno", e -> e.nombre().isBlank() ? e.curp() : e.nombre(), 220));
        tabla.getColumns().add(columna("Estado", e -> switch (e.estado()) {
            case ENVIADO -> "✓ Enviado" + (e.casoFolio() != null ? " · " + e.casoFolio() : "");
            case PENDIENTE -> "⟳ Pendiente (" + e.intentos() + " intento(s))";
            case REQUIERE_ATENCION -> "⚠ Requiere atención";
        }, 220));
        tabla.getColumns().add(columna("Detalle", e -> e.ultimoError() == null ? "" : e.ultimoError(), 380));
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        Button probar = new Button("Probar conexión");
        probar.getStyleClass().add("boton-secundario");
        Button guardar = new Button("Guardar conexión");
        guardar.getStyleClass().add("boton-primario");
        Button reintentar = new Button("Reintentar ahora");
        reintentar.getStyleClass().add("boton-secundario");
        Button matricula = new Button("Corregir matrícula del seleccionado");
        matricula.getStyleClass().add("boton-secundario");
        matricula.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull());

        Runnable refrescar = () -> {
            Thread t = new Thread(() -> {
                var lista = ColaSincronizacion.listar(100);
                int pend = ColaSincronizacion.contar(ColaSincronizacion.Estado.PENDIENTE);
                int aten = ColaSincronizacion.contar(ColaSincronizacion.Estado.REQUIERE_ATENCION);
                int env = ColaSincronizacion.contar(ColaSincronizacion.Estado.ENVIADO);
                Platform.runLater(() -> {
                    tabla.getItems().setAll(lista);
                    estado.setText(String.format("Enviados: %d · Pendientes: %d · Requieren atención: %d.  %s", env, pend, aten, ColaSincronizacion.getUltimoResultado()));
                });
            }, "cola-ui");
            t.setDaemon(true);
            t.start();
        };

        guardar.setOnAction(e -> {
            String error = ClienteSuite.validarUrl(url.getText());
            if (error != null) { estado.setText("⚠ " + error); return; }
            AppConfig.URL_API_SUITE = url.getText().trim();
            AppConfig.CLAVE_API_ESCRITORIO = clave.getText().trim();
            AppConfig.NOMBRE_EQUIPO = equipo.getText().isBlank() ? "Equipo de escritorio" : equipo.getText().trim();
            AppConfig.guardar();
            estado.setText("✓ Conexión guardada. Enviando lo pendiente…");
            ColaSincronizacion.reintentarTodo();
            refrescar.run();
        });
        probar.setOnAction(e -> {
            String error = ClienteSuite.validarUrl(url.getText());
            if (error != null) { estado.setText("⚠ " + error); return; }
            String anterior = AppConfig.URL_API_SUITE;
            AppConfig.URL_API_SUITE = url.getText().trim();
            estado.setText("Probando…");
            Thread t = new Thread(() -> {
                ClienteSuite.Respuesta r = ClienteSuite.salud();
                AppConfig.URL_API_SUITE = anterior;
                Platform.runLater(() -> {
                    if (r.exito()) {
                        var j = r.json();
                        boolean escritorio = j.has("escritorio") && j.get("escritorio").getAsBoolean();
                        estado.setText("✓ Servidor en línea" + (j.has("version") ? " (versión " + j.get("version").getAsString() + ")" : "")
                                + (escritorio ? ". Recepción del escritorio habilitada." : ". ⚠ El servidor no tiene configurada la clave del escritorio (ESCRITORIO_API_KEY)."));
                    } else {
                        estado.setText("⚠ No se pudo conectar: " + r.mensajeError());
                    }
                });
            }, "probar-suite");
            t.setDaemon(true);
            t.start();
        });
        reintentar.setOnAction(e -> { ColaSincronizacion.reintentarTodo(); estado.setText("Reintentando…"); refrescar.run(); });
        matricula.setOnAction(e -> {
            var sel = tabla.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            DialogoMatricula.pedir(tabla.getScene().getWindow(), sel.curp(), sel.nombre()).ifPresent(m -> refrescar.run());
        });

        HBox acciones = new HBox(10, probar, guardar);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        HBox accionesCola = new HBox(10, reintentar, matricula);
        accionesCola.setAlignment(Pos.CENTER_LEFT);
        Label tituloCola = new Label("Envíos al portal");
        tituloCola.getStyleClass().add("etiqueta-kpi");

        VBox tarjeta = new VBox(12, titulo, descripcion, new Separator(), campos, acciones, estado, tituloCola, tabla, accionesCola);
        tarjeta.getStyleClass().add("tarjeta-admin");
        refrescar.run();
        return tarjeta;
    }

    private static TableColumn<ColaSincronizacion.Envio, String> columna(String titulo,
            java.util.function.Function<ColaSincronizacion.Envio, String> valor, double ancho) {
        TableColumn<ColaSincronizacion.Envio, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleStringProperty(valor.apply(d.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }
}
