package com.eyesofter.ui;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Refresco automático que solo corre mientras la vista está en pantalla.
 *
 * Antes cada visita a Inicio, Pacientes o Reportes creaba un Timeline infinito que nunca
 * se detenía: tras navegar un rato había decenas consultando la base cada 2–4 s y la
 * aplicación se volvía lenta. Ahora se detiene al salir de la vista y, si la vista
 * vuelve desde la caché del historial, se refresca al instante y sigue.
 */
public final class ActualizacionPeriodica {
    private ActualizacionPeriodica() { }

    public static Timeline mientrasVisible(Node nodo, Duration cada, Runnable accion) {
        Timeline linea = new Timeline(new KeyFrame(cada, e -> accion.run()));
        linea.setCycleCount(Animation.INDEFINITE);
        boolean[] yaMostrada = { nodo.getScene() != null };
        nodo.sceneProperty().addListener((obs, anterior, escena) -> {
            if (escena == null) {
                linea.stop();
            } else if (linea.getStatus() != Animation.Status.RUNNING) {
                // Si regresa desde la caché, refresca de inmediato en vez de esperar el ciclo.
                if (yaMostrada[0]) accion.run();
                yaMostrada[0] = true;
                linea.play();
            }
        });
        if (yaMostrada[0]) linea.play();
        return linea;
    }
}
