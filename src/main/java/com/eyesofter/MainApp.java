package com.eyesofter;

import com.eyesofter.ui.ControladorSplash;
import com.eyesofter.ui.TemaAplicacion;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Comparator;

public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        configurarDirectorioPersistencia();
        instalarEstiloVentanasEmergentes();
        // Las frases de la voz guía se sintetizan en segundo plano y quedan en caché.
        com.eyesofter.config.AppConfig.cargar();
        com.eyesofter.formulas.FuentesOptotipo.activarMedicionEnPantalla();
        com.eyesofter.voz.VozGuia.precalentar();

        try {
            Image icono = new Image(getClass().getResourceAsStream("/img/eye-icono.png"));
            primaryStage.getIcons().add(icono);
        } catch (Exception e) {
            System.err.println("[UI] No se pudo cargar el logo principal.");
        }

        cargarFuentes();

        // --- MODO KIOSCO: 100% Full Screen, Indecorado e Inminimizable ---
        primaryStage.initStyle(javafx.stage.StageStyle.UNDECORATED); // Elimina la barra de Windows (minimizar, maximizar, cerrar)
        primaryStage.setAlwaysOnTop(true); // Auto-impuesto: Siempre por encima de otras aplicaciones
        primaryStage.setFullScreen(true); // Ocupa el 100% de la pantalla
        primaryStage.setFullScreenExitHint(""); // Oculta el mensaje molesto de "Presione ESC para salir"
        primaryStage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH); // Esc no saca de pantalla completa

        // Llamamos a tu ControladorSplash para que use esta ventana configurada
        ControladorSplash.mostrarVentana(primaryStage);
    }

    /** Montserrat, la letra de títulos y botones de la app móvil y el portal (licencia OFL). */
    private static void cargarFuentes() {
        for (String f : new String[]{"medium", "semibold", "bold", "extrabold"}) {
            try (var in = MainApp.class.getResourceAsStream("/fonts/montserrat_" + f + ".ttf")) {
                if (in != null) javafx.scene.text.Font.loadFont(in, 14);
            } catch (Exception e) {
                System.err.println("[UI] No se pudo cargar Montserrat " + f + ": " + e.getMessage());
            }
        }
    }

    private void configurarDirectorioPersistencia() {
        File estable = new File(System.getProperty("user.home"), ".eyesofter");
        if (!estable.exists()) estable.mkdirs();
        File anterior = new File(".env"); File destino = new File(estable, ".env");
        try { if (anterior.isFile() && !destino.isFile()) Files.copy(anterior.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING); } catch (Exception ignored) { }
        migrarBaseAnterior(estable);
        System.setProperty("eyesofter.home", estable.getAbsolutePath());
    }

    private void migrarBaseAnterior(File estable) {
        File destino = new File(estable, "eyesofter_local.db");
        File[] candidatos = {
                destino,
                new File("eyesofter_local.db"),
                new File("release/EYESOFTER/app/eyesofter_local.db"),
                new File("dist/EYESOFTER/app/eyesofter_local.db")
        };
        File mejor = Arrays.stream(candidatos)
                .filter(File::isFile)
                .filter(this::baseTieneDatos)
                .max(Comparator.comparingLong(this::pesoBaseDatos))
                .orElse(null);
        if (mejor == null || mejor.equals(destino)) return;
        try {
            if (destino.isFile()) {
                Files.copy(destino.toPath(), new File(estable, "eyesofter_local.db.antes-de-migracion.bak").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(mejor.toPath(), destino.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[DB] Base clínica unificada desde: " + mejor.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[DB] No se pudo unificar la base de datos: " + e.getMessage());
        }
    }

    private long pesoBaseDatos(File archivo) {
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + archivo.getAbsolutePath());
             var s = c.createStatement();
             var r = s.executeQuery("SELECT (SELECT count(*) FROM paciente) * 1000000 + (SELECT count(*) FROM evaluacion) * 1000 + (SELECT count(*) FROM cita)")) {
            return (r.next() ? r.getLong(1) : 0) * 100000000L + archivo.length();
        } catch (Exception e) {
            return archivo.length();
        }
    }

    private boolean baseTieneDatos(File archivo) {
        try (var c = DriverManager.getConnection("jdbc:sqlite:" + archivo.getAbsolutePath()); var s = c.createStatement(); var r = s.executeQuery("SELECT (SELECT count(*) FROM paciente) + (SELECT count(*) FROM evaluacion)")) { return r.next() && r.getInt(1) > 0; }
        catch (Exception e) { return false; }
    }

    private void instalarEstiloVentanasEmergentes() {
        Window.getWindows().addListener((javafx.collections.ListChangeListener<Window>) cambio -> {
            while (cambio.next()) if (cambio.wasAdded()) for (Window ventana : cambio.getAddedSubList()) aplicarEstilo(ventana);
        });
    }

    private void aplicarEstilo(Window ventana) {
        Platform.runLater(() -> {
            // [NUEVO] Aplicar el ícono a todas las ventanas secundarias
            if (ventana instanceof Stage stage) {
                try {
                    if (stage.getIcons().isEmpty()) {
                        stage.getIcons().add(new Image(getClass().getResourceAsStream("/img/eye-icono.png")));
                    }
                } catch (Exception ignored) {}
            }

            if (ventana.getScene() == null) return;
            String css = MainApp.class.getResource("/css/estilos.css").toExternalForm();
            if (!ventana.getScene().getStylesheets().contains(css)) ventana.getScene().getStylesheets().add(css);
            if (ventana.getScene().getRoot() != null) {
                var clases = ventana.getScene().getRoot().getStyleClass();
                if (!clases.contains("ventana-emergente")) clases.add("ventana-emergente");
                if (TemaAplicacion.esOscuro() && !clases.contains("tema-oscuro")) clases.add("tema-oscuro");
                DialogPane panel = ventana.getScene().getRoot() instanceof DialogPane
                        ? (DialogPane) ventana.getScene().getRoot() : (DialogPane) ventana.getScene().getRoot().lookup(".dialog-pane");
                if (panel != null) prepararDialogo(ventana, panel);
            }
        });
    }

    private void prepararDialogo(Window ventana, DialogPane panel) {
        ventana.setOnShown((WindowEvent e) -> ajustarDialogo(ventana, panel));
        Platform.runLater(() -> ajustarDialogo(ventana, panel));
    }

    private void ajustarDialogo(Window ventana, DialogPane panel) {
        if (!(ventana instanceof Stage stage)) return;
        double contenido = panel.getContent() == null ? 180 : panel.getContent().prefHeight(-1);
        double alto = Math.max(460, Math.min(900, contenido + 220));
        double ancho = Math.max(720, Math.min(1100, panel.getContent() == null ? 720 : panel.getContent().prefWidth(-1) + 260));
        stage.setMinWidth(680); stage.setMinHeight(420);
        if (stage.getWidth() < ancho) stage.setWidth(ancho);
        if (stage.getHeight() < alto) stage.setHeight(alto);
        stage.setResizable(true);
    }

    @Override
    public void stop() {
        com.eyesofter.voz.ReconocedorOptotipos.liberarCompartido();
    }

    public static void main(String[] args) {
        launch(args);
    }
}