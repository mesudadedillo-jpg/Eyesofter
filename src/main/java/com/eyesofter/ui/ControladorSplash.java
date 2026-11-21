package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.config.ConfiguracionUsuarios;
import com.eyesofter.db.GestorBaseDatos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Inicialización visual de los servicios locales antes de solicitar acceso. */
public class ControladorSplash implements ControladorDeVista {
    @FXML private ProgressBar barraProgreso;
    @FXML private Label lblEstado;
    private Stage stage;

    public static void mostrarVentana(Stage stage) {
        Navegador.mostrar(stage, "/fxml/Splash.fxml", "EYESOFTER · Inicialización");
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        AppConfig.cargar();
        TemaAplicacion.cargar();
        ConfiguracionUsuarios.cargar();
        GestorBaseDatos.inicializarBaseDatos();
        iniciarCarga();
    }

    private void iniciarCarga() {
        String[] estados = {
                "Cargando la configuración local…",
                "Verificando la base de datos clínica local…",
                "Preparando los módulos de voz y visión…",
                "Sistema listo para iniciar una sesión."
        };
        Timeline linea = new Timeline(new KeyFrame(Duration.millis(500), evento -> {
            double progreso = Math.min(1.0, barraProgreso.getProgress() + 0.25);
            barraProgreso.setProgress(progreso);
            int indice = Math.min(estados.length - 1, (int) Math.ceil(progreso * estados.length) - 1);
            lblEstado.setText(estados[Math.max(0, indice)]);
            if (progreso >= 1.0) {
                Navegador.mostrar(stage, "/fxml/Acceso.fxml", "EYESOFTER · Iniciar sesión");
            }
        }));
        linea.setCycleCount(4);
        linea.play();
    }
}
