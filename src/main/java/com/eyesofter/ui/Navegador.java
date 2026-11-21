package com.eyesofter.ui;

import javafx.animation.FadeTransition;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;

/** Navegación centralizada para conservar la sesión y evitar abrir ventanas duplicadas. */
public final class Navegador {
    private record Ruta(String recurso, String titulo) { }
    private static final Deque<Ruta> historial = new ArrayDeque<>();
    private static final Deque<Ruta> futuro = new ArrayDeque<>();
    private static Ruta actual;
    private static boolean regresando;
    private Navegador() { }

    public static void mostrar(Stage stage, String recursoFxml, String titulo) {
        if (actual != null && !regresando && !actual.recurso.equals(recursoFxml)) { historial.push(actual); futuro.clear(); }
        URL recurso = Navegador.class.getResource(recursoFxml);
        if (recurso == null) {
            throw new IllegalStateException("No se encontró la vista: " + recursoFxml);
        }
        try {
            FXMLLoader loader = new FXMLLoader(recurso);
            Parent root = loader.load();
            Object controller = loader.getController();

            if (controller instanceof ControladorDeVista controladorDeVista) {
                controladorDeVista.setStage(stage);
            }

            stage.setTitle(titulo);
            actual = new Ruta(recursoFxml, titulo);
            root = envolverConRegreso(root, recursoFxml, stage);

            // --- Lógica SPA para evitar el parpadeo (Flickering) ---
            if (stage.getScene() == null) {
                // Es el primer arranque de la aplicación: creamos la Scene
                stage.setScene(new Scene(root));
                TemaAplicacion.aplicar(stage.getScene());
                ModoKiosco.aplicar(stage);
                instalarAtajo(stage);
                stage.show();
            } else {
                // Ya hay una ventana activa: solo cambiamos el nodo raíz
                root.setOpacity(0.0); // Ocultamos el nuevo FXML temporalmente
                stage.getScene().setRoot(root); // Intercambio en caliente
                TemaAplicacion.aplicar(stage.getScene());

                // Animación de transición suave (Fade-in)
                FadeTransition fadeIn = new FadeTransition(Duration.millis(300), root);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            }

        } catch (IOException e) {
            throw new IllegalStateException("No fue posible abrir " + recursoFxml, e);
        }
    }

    /** Regresa una sola pantalla sin borrar datos persistidos ni resultados ya registrados. */
    public static void volver(Stage stage) {
        if (historial.isEmpty()) return;
        Ruta destino = historial.pop();
        if (actual != null) futuro.push(actual);
        regresando = true;
        try { mostrar(stage, destino.recurso, destino.titulo); }
        finally { regresando = false; }
    }

    public static void avanzar(Stage stage) {
        if (futuro.isEmpty()) return;
        Ruta destino = futuro.pop();
        if (actual != null) historial.push(actual);
        regresando = true;
        try { mostrar(stage, destino.recurso, destino.titulo); }
        finally { regresando = false; }
    }

    /** Regreso seguro al panel administrativo sin pasar por la pantalla de acceso. */
    public static void volverAlPanelAdministrativo(Stage stage) {
        historial.clear(); futuro.clear(); regresando = false;
        mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
        historial.clear(); futuro.clear();
    }

    private static Parent envolverConRegreso(Parent root, String recurso, Stage stage) {
        StackPane contenedor = new StackPane(root);
        if (recurso.endsWith("Splash.fxml") || recurso.endsWith("Acceso.fxml") || recurso.endsWith("PanelAdministrativo.fxml")) return contenedor;
        Button volver = new Button("←");
        Button avanzar = new Button("→");
        Button salir = new Button("Salir");
        volver.getStyleClass().add("boton-regreso-global"); avanzar.getStyleClass().add("boton-regreso-global"); salir.getStyleClass().add("boton-salir");
        // Siempre visible y accionable: si no hay historial, vuelve al panel seguro.
        volver.setDisable(false);
        volver.setOnAction(evento -> { if (historial.isEmpty()) volverAlPanelAdministrativo(stage); else volver(stage); });
        avanzar.setOnAction(evento -> avanzar(stage));
        salir.setOnAction(evento -> ConfirmacionSalida.mostrar(stage));
        javafx.scene.layout.HBox navegacion = new javafx.scene.layout.HBox(6, volver, avanzar, salir);
        // StackPane redimensionaba esta HBox hasta ocupar toda la escena. Aunque
        // fuese transparente, su área recibía los clics antes que la vista actual.
        // Se limita al tamaño de sus botones para que el resto de la pantalla siga
        // recibiendo eventos del mouse.
        navegacion.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        navegacion.setPickOnBounds(false);
        contenedor.getChildren().add(navegacion);
        StackPane.setAlignment(navegacion, javafx.geometry.Pos.TOP_LEFT);
        StackPane.setMargin(navegacion, new javafx.geometry.Insets(12));
        avanzar.setDisable(false);
        return contenedor;
    }

    private static void instalarAtajo(Stage stage) {
        stage.getScene().setOnKeyPressed(evento -> { if (evento.getCode() == KeyCode.ESCAPE) volver(stage); });
    }
}
