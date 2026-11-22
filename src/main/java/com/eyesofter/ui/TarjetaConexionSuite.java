package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.sincronizacion.ClienteSuite;
import com.eyesofter.sincronizacion.ColaSincronizacion;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Tarjeta de Configuración: vinculación con el servidor de EYESOFTER Suite y cola de envíos
 * (C-RFN-02, C-RFNN-03). Instalar el escritorio y entrar con la cuenta local NO da acceso a
 * los datos de la escuela: el equipo debe vincularse en el navegador con una cuenta del
 * portal de Dirección o Enfermería. El operador ve qué tamizajes se enviaron, cuáles
 * esperan reintento y cuáles necesitan una corrección (matrícula).
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
        TextField equipo = new TextField(AppConfig.NOMBRE_EQUIPO);
        equipo.setAccessibleText("Nombre de este equipo");
        GridPane campos = new GridPane();
        campos.setHgap(12);
        campos.setVgap(10);
        campos.addRow(0, new Label("Dirección del servidor"), url);
        campos.addRow(1, new Label("Nombre de este equipo"), equipo);
        GridPane.setHgrow(url, Priority.ALWAYS);
        GridPane.setHgrow(equipo, Priority.ALWAYS);

        // Estado de la vinculación: sin vincular, este equipo NO puede ver ni buscar datos de la escuela.
        Label vinculo = new Label();
        vinculo.setWrapText(true);
        vinculo.getStyleClass().add("etiqueta-kpi");
        Label codigo = new Label();
        codigo.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 26px; -fx-font-weight: bold;");
        codigo.setVisible(false);
        codigo.setManaged(false);
        Runnable pintarVinculo = () -> vinculo.setText(AppConfig.TOKEN_EQUIPO.isBlank()
                ? "🔒 Equipo sin vincular. No tiene acceso a datos de la escuela hasta que Dirección o Enfermería lo autorice con su cuenta del portal."
                : "✓ Equipo vinculado" + (AppConfig.VINCULADO_POR.isBlank() ? "" : " por " + AppConfig.VINCULADO_POR) + ".");
        pintarVinculo.run();

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
        Button vincular = new Button("Vincular este equipo");
        vincular.getStyleClass().add("boton-primario");
        Button desvincular = new Button("Desvincular");
        desvincular.getStyleClass().add("boton-secundario");
        desvincular.setDisable(AppConfig.TOKEN_EQUIPO.isBlank());
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

        vincular.setOnAction(e -> {
            String error = ClienteSuite.validarUrl(url.getText());
            if (error != null) { estado.setText("⚠ " + error); return; }
            String nuevaUrl = url.getText().trim();
            if (!nuevaUrl.equals(AppConfig.URL_API_SUITE)) {
                // El token pertenece al servidor que lo emitió: nunca se manda a otro.
                AppConfig.TOKEN_EQUIPO = "";
                AppConfig.VINCULADO_POR = "";
                desvincular.setDisable(true);
                pintarVinculo.run();
            }
            AppConfig.URL_API_SUITE = nuevaUrl;
            AppConfig.NOMBRE_EQUIPO = equipo.getText().isBlank() ? "Equipo de escritorio" : equipo.getText().trim();
            AppConfig.guardar();
            vincular.setDisable(true);
            estado.setText("Pidiendo un código al servidor…");
            Thread t = new Thread(() -> esperarVinculacion(estado, codigo, vincular, desvincular, pintarVinculo, refrescar), "vincular-equipo");
            t.setDaemon(true);
            t.start();
        });
        desvincular.setOnAction(e -> {
            AppConfig.TOKEN_EQUIPO = "";
            AppConfig.VINCULADO_POR = "";
            AppConfig.guardar();
            desvincular.setDisable(true);
            pintarVinculo.run();
            estado.setText("Equipo desvinculado. Los tamizajes se guardan aquí hasta que se vuelva a vincular. "
                    + "Para retirar el acceso desde el servidor, cambie la contraseña de la cuenta que lo autorizó.");
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
                        String base = "✓ Servidor en línea" + (j.has("version") ? " (versión " + j.get("version").getAsString() + ")" : "");
                        if (AppConfig.TOKEN_EQUIPO.isBlank()) { estado.setText(base + ". Falta vincular este equipo."); return; }
                        Thread v = new Thread(() -> {
                            ClienteSuite.Respuesta eq = ClienteSuite.equipo();
                            Platform.runLater(() -> estado.setText(base + (eq.exito()
                                    ? ". Vinculación vigente (autorizada por " + eq.json().get("usuario").getAsString() + ")."
                                    : ClienteSuite.equipoNoVinculado(eq) ? ". ⚠ La vinculación ya no es válida: vuelva a vincular el equipo." : ". ⚠ " + eq.mensajeError())));
                        }, "probar-equipo");
                        v.setDaemon(true);
                        v.start();
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

        HBox acciones = new HBox(10, probar, desvincular, vincular);
        acciones.setAlignment(Pos.CENTER_RIGHT);
        HBox accionesCola = new HBox(10, reintentar, matricula);
        accionesCola.setAlignment(Pos.CENTER_LEFT);
        Label tituloCola = new Label("Envíos al portal");
        tituloCola.getStyleClass().add("etiqueta-kpi");

        VBox tarjeta = new VBox(12, titulo, descripcion, new Separator(), vinculo, campos, acciones, codigo, estado, tituloCola, tabla, accionesCola);
        tarjeta.getStyleClass().add("tarjeta-admin");
        refrescar.run();
        return tarjeta;
    }

    /**
     * Pide un código, abre el navegador en la página de autorización y espera a que alguien
     * de Dirección o Enfermería inicie sesión y apruebe. Corre en un hilo aparte.
     */
    private static void esperarVinculacion(Label estado, Label codigo, Button vincular, Button desvincular, Runnable pintarVinculo, Runnable refrescar) {
        try {
            ClienteSuite.Vinculacion v = ClienteSuite.iniciarVinculacion(AppConfig.NOMBRE_EQUIPO);
            boolean abierto = abrirNavegador(v.url());
            Platform.runLater(() -> {
                codigo.setText(v.codigo());
                codigo.setVisible(true);
                codigo.setManaged(true);
                estado.setText((abierto ? "Se abrió el navegador. " : "Abra en el navegador: " + v.url() + "\n")
                        + "Inicie sesión con una cuenta de Dirección o Enfermería del portal y confirme que ve este mismo código. Esperando autorización…");
            });
            long limite = System.currentTimeMillis() + v.expiraEnSegundos() * 1000;
            while (System.currentTimeMillis() < limite) {
                Thread.sleep(Math.max(2, v.intervaloSegundos()) * 1000L);
                ClienteSuite.Respuesta r = ClienteSuite.consultarVinculacion(v.solicitud());
                if (r.codigo() == 202 || r.codigo() == 0) continue; // pendiente, o sin red por un momento
                if (r.exito() && r.json().has("token")) {
                    var j = r.json();
                    AppConfig.TOKEN_EQUIPO = j.get("token").getAsString();
                    AppConfig.VINCULADO_POR = j.has("usuario") ? j.get("usuario").getAsString() : "";
                    AppConfig.guardar();
                    ColaSincronizacion.reintentarTodo();
                    Platform.runLater(() -> {
                        pintarVinculo.run();
                        estado.setText("✓ Equipo vinculado. Enviando lo pendiente…");
                        desvincular.setDisable(false);
                        refrescar.run();
                    });
                    return;
                }
                String error = r.mensajeError();
                Platform.runLater(() -> estado.setText("⚠ " + error));
                return;
            }
            Platform.runLater(() -> estado.setText("⚠ El código venció sin que nadie lo autorizara. Pulse «Vincular este equipo» otra vez."));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            Platform.runLater(() -> estado.setText("⚠ No se pudo iniciar la vinculación: " + msg));
        } finally {
            Platform.runLater(() -> {
                vincular.setDisable(false);
                codigo.setVisible(false);
                codigo.setManaged(false);
            });
        }
    }

    static boolean abrirNavegador(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return true;
            }
        } catch (Exception ignorada) { }
        try {
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
                return true;
            }
        } catch (Exception ignorada) { }
        return false;
    }

    private static TableColumn<ColaSincronizacion.Envio, String> columna(String titulo,
            java.util.function.Function<ColaSincronizacion.Envio, String> valor, double ancho) {
        TableColumn<ColaSincronizacion.Envio, String> c = new TableColumn<>(titulo);
        c.setCellValueFactory(d -> new SimpleStringProperty(valor.apply(d.getValue())));
        c.setPrefWidth(ancho);
        return c;
    }
}
