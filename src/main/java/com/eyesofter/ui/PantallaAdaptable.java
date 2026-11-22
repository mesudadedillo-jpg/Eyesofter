package com.eyesofter.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.prefs.Preferences;

/**
 * Contenedor "responsivo" para las pantallas administrativas y de formularios.
 *
 * <ul>
 *   <li><b>Escala automática</b>: la interfaz se diseñó para ~1600×940. En pantallas más
 *       chicas (laptops, Windows al 125/150 %) todo se reduce proporcionalmente en lugar de
 *       quedar cortado.</li>
 *   <li><b>Scroll</b>: si aun reducida no cabe, aparece una barra de desplazamiento.</li>
 *   <li><b>Zoom del usuario</b>: Ctrl + / Ctrl − / Ctrl 0 y Ctrl + rueda del mouse. Se
 *       recuerda entre sesiones.</li>
 * </ul>
 *
 * NO se usa en las pruebas clínicas (Snellen, Ishihara, contraste, Amsler, calibración):
 * ahí el tamaño físico de los optotipos en pantalla es parte de la medición.
 */
public final class PantallaAdaptable extends Region {

    private static final double ANCHO_DISENO = 1600;
    private static final double ALTO_DISENO = 940;
    private static final double ESCALA_AUTO_MINIMA = 0.62;
    private static final double ZOOM_MIN = 0.5;
    private static final double ZOOM_MAX = 1.6;
    private static final double PASO_ZOOM = 0.1;

    private static final Preferences PREFERENCIAS = Preferences.userRoot().node("eyesofter/interfaz");
    private static final DoubleProperty ZOOM = new SimpleDoubleProperty(leerZoomGuardado());

    private final ScrollPane scroll;
    private final Scale escala = new Scale(1, 1, 0, 0);
    private final Rectangle recorte = new Rectangle();
    private final Label aviso = new Label();
    private final InvalidationListener alCambiarZoom = obs -> requestLayout();
    private SequentialTransition animacionAviso;

    /** Envuelve una vista. {@code eraRaizDeEscena}: conserva los estilos ".root" de estilos.css. */
    public static Parent envolver(Parent contenido, boolean eraRaizDeEscena) {
        if (contenido instanceof PantallaAdaptable) return contenido;
        if (eraRaizDeEscena && !contenido.getStyleClass().contains("root")) contenido.getStyleClass().add("root");
        return new PantallaAdaptable(contenido);
    }

    private PantallaAdaptable(Parent contenido) {
        scroll = new ScrollPane(contenido);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setFocusTraversable(false);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-padding: 0; -fx-background-insets: 0;");
        scroll.getTransforms().add(escala);

        aviso.setManaged(false);
        aviso.setMouseTransparent(true);
        aviso.setOpacity(0);
        aviso.setStyle("-fx-background-color: rgba(10,25,47,0.88); -fx-text-fill: white; -fx-font-size: 15px;"
                + " -fx-font-weight: bold; -fx-padding: 9 18; -fx-background-radius: 20;");

        getChildren().addAll(scroll, aviso);
        setClip(recorte);

        ZOOM.addListener(new WeakInvalidationListener(alCambiarZoom));
        addEventFilter(KeyEvent.KEY_PRESSED, this::atajosTeclado);
        addEventFilter(ScrollEvent.SCROLL, this::ruedaConCtrl);
    }

    // ----------------------------------------------------------------- distribución

    @Override protected void layoutChildren() {
        double ancho = getWidth();
        double alto = getHeight();
        double s = escalaPara(ancho, alto);
        escala.setX(s);
        escala.setY(s);
        scroll.resizeRelocate(0, 0, ancho / s, alto / s);
        recorte.setWidth(ancho);
        recorte.setHeight(alto);
        aviso.autosize();
        aviso.relocate((ancho - aviso.getWidth()) / 2, alto - aviso.getHeight() - 36);
    }

    @Override protected double computePrefWidth(double alto) { return scroll.prefWidth(-1); }
    @Override protected double computePrefHeight(double ancho) { return scroll.prefHeight(-1); }
    @Override protected double computeMinWidth(double alto) { return 0; }
    @Override protected double computeMinHeight(double ancho) { return 0; }

    private static double escalaPara(double ancho, double alto) {
        if (ancho <= 0 || alto <= 0) return 1;
        double automatica = Math.min(1.0, Math.min(ancho / ANCHO_DISENO, alto / ALTO_DISENO));
        automatica = Math.max(ESCALA_AUTO_MINIMA, automatica);
        return Math.max(0.4, Math.min(2.0, automatica * ZOOM.get()));
    }

    // ----------------------------------------------------------------- zoom

    private void atajosTeclado(KeyEvent evento) {
        if (!evento.isShortcutDown()) return;
        KeyCode tecla = evento.getCode();
        if (tecla == KeyCode.PLUS || tecla == KeyCode.EQUALS || tecla == KeyCode.ADD) { agrandar(); mostrarAviso(); evento.consume(); }
        else if (tecla == KeyCode.MINUS || tecla == KeyCode.SUBTRACT) { reducir(); mostrarAviso(); evento.consume(); }
        else if (tecla == KeyCode.DIGIT0 || tecla == KeyCode.NUMPAD0) { restablecer(); mostrarAviso(); evento.consume(); }
    }

    private void ruedaConCtrl(ScrollEvent evento) {
        if (!evento.isShortcutDown() || evento.getDeltaY() == 0) return;
        if (evento.getDeltaY() > 0) agrandar(); else reducir();
        mostrarAviso();
        evento.consume();
    }

    private void mostrarAviso() {
        aviso.setText("Tamaño de la interfaz: " + porcentajeZoom() + " %   ·   Ctrl 0 para restablecer");
        requestLayout();
        if (animacionAviso != null) animacionAviso.stop();
        FadeTransition aparecer = new FadeTransition(Duration.millis(120), aviso);
        aparecer.setToValue(1);
        FadeTransition desaparecer = new FadeTransition(Duration.millis(400), aviso);
        desaparecer.setToValue(0);
        animacionAviso = new SequentialTransition(aparecer, new PauseTransition(Duration.millis(1300)), desaparecer);
        animacionAviso.play();
    }

    public static void agrandar() { fijarZoom(ZOOM.get() + PASO_ZOOM); }
    public static void reducir() { fijarZoom(ZOOM.get() - PASO_ZOOM); }
    public static void restablecer() { fijarZoom(1.0); }
    public static int porcentajeZoom() { return (int) Math.round(ZOOM.get() * 100); }

    private static void fijarZoom(double valor) {
        double redondeado = Math.round(Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, valor)) * 100) / 100.0;
        ZOOM.set(redondeado);
        try { PREFERENCIAS.putDouble("zoom", redondeado); } catch (Exception ignorada) { }
    }

    private static double leerZoomGuardado() {
        try {
            double valor = PREFERENCIAS.getDouble("zoom", 1.0);
            return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, valor));
        } catch (Exception e) {
            return 1.0;
        }
    }
}
