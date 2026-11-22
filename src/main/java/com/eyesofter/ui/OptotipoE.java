package com.eyesofter.ui;

import com.eyesofter.medicion_snellen.MotorSnellen;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * E direccional ("E de Snellen" o "tumbling E") dibujada con rectángulos sobre una
 * rejilla de 5 × 5: trazos y espacios miden exactamente 1/5 del lado, como exige
 * la norma (ISO 8596 / ETDRS). Se dibuja con figuras y no con una tipografía porque
 * la "E" de las fuentes comunes no es cuadrada ni tiene trazos de 1/5.
 *
 * <p>Las patitas apuntan en la dirección indicada; la figura base (patitas hacia la
 * derecha) se gira en múltiplos de 90°, sin deformarse.</p>
 */
public final class OptotipoE {

    private OptotipoE() { }

    /**
     * @param lado      alto = ancho del optotipo, en píxeles lógicos de JavaFX
     * @param direccion una de las constantes {@code MotorSnellen.E_*}
     */
    public static Pane crear(double lado, String direccion) {
        double t = lado / 5.0;
        Rectangle lomo = rect(0, 0, t, lado);
        Rectangle arriba = rect(0, 0, lado, t);
        Rectangle medio = rect(0, 2 * t, lado, t);
        Rectangle abajo = rect(0, 4 * t, lado, t);
        Group figura = new Group(lomo, arriba, medio, abajo);
        figura.setRotate(rotacion(direccion));

        Pane contenedor = new Pane(figura);
        contenedor.setMinSize(lado, lado);
        contenedor.setPrefSize(lado, lado);
        contenedor.setMaxSize(lado, lado);
        return contenedor;
    }

    /** Grados (sentido horario, eje Y hacia abajo) que llevan la E base a la dirección pedida. */
    static double rotacion(String direccion) {
        if (direccion == null) return 0;
        return switch (direccion) {
            case MotorSnellen.E_ABAJO -> 90;
            case MotorSnellen.E_IZQUIERDA -> 180;
            case MotorSnellen.E_ARRIBA -> 270;
            default -> 0;
        };
    }

    private static Rectangle rect(double x, double y, double ancho, double alto) {
        Rectangle r = new Rectangle(x, y, ancho, alto);
        r.setFill(Color.BLACK);
        r.getStyleClass().add("optotipo-e-trazo");
        return r;
    }
}
