package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.TrigonometriaVisual;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Locale;

/**
 * Rejilla de Amsler: 20 × 20 cuadros de 5 mm reales (1° a ~29 cm) con punto de fijación.
 * La persona marca con clic (o arrastrando) las zonas que ve onduladas, borrosas o
 * faltantes. Se guardan marcas separadas para cada ojo.
 */
public class ControladorAmsler implements ControladorDeVista {
    @FXML private BorderPane raiz;

    private static final int CUADROS = 20;
    private static final double LADO_CUADRO_MM = 5.0;
    /** Distancia a la que un cuadro de 5 mm subtiende 1°. */
    private static final double DISTANCIA_ESTANDAR_CM = LADO_CUADRO_MM / 10.0 / Math.tan(Math.toRadians(1.0));
    private double distanciaCm = DISTANCIA_ESTANDAR_CM;

    private final boolean[][] marcasDerecho = new boolean[CUADROS][CUADROS];
    private final boolean[][] marcasIzquierdo = new boolean[CUADROS][CUADROS];
    private boolean[][] marcasActuales = marcasDerecho;
    private Canvas lienzo;
    private double ladoPx;
    private Label lblResumen;
    private Boolean valorArrastre;

    @Override
    public void setStage(Stage stage) {
        double escala = Screen.getPrimary().getOutputScaleX() > 0 ? Screen.getPrimary().getOutputScaleX() : 1.0;
        ladoPx = TrigonometriaVisual.calcularAlturaPixel(LADO_CUADRO_MM, AppConfig.PPI_ESTANDAR_MONITOR) / escala;
        double maximo = Math.min(Screen.getPrimary().getVisualBounds().getHeight() - 260, 760);
        boolean reducida = ladoPx * CUADROS > maximo;
        double ladoIdeal = ladoPx;
        if (reducida) ladoPx = maximo / CUADROS;
        // Cada cuadro debe medir 1°: 5 mm a 28.6 cm. Antes se pedía "30-40 cm": a 40 cm la
        // rejilla cubre 14° en vez de 20° y deja fuera parte de la zona que se quiere explorar.
        // Si la rejilla se redujo, la distancia se acorta en la misma proporción.
        distanciaCm = DISTANCIA_ESTANDAR_CM * ladoPx / ladoIdeal;

        lienzo = new Canvas(ladoPx * CUADROS + 2, ladoPx * CUADROS + 2);
        lienzo.setOnMousePressed(e -> {
            int[] c = celda(e.getX(), e.getY());
            if (c == null) return;
            valorArrastre = !marcasActuales[c[1]][c[0]];
            marcar(c, valorArrastre);
        });
        lienzo.setOnMouseDragged(e -> { int[] c = celda(e.getX(), e.getY()); if (c != null && valorArrastre != null) marcar(c, valorArrastre); });
        lienzo.setOnMouseReleased(e -> valorArrastre = null);

        Label titulo = new Label("Rejilla de Amsler");
        titulo.getStyleClass().add("etiqueta-titulo");
        Label instrucciones = new Label(String.format(Locale.US, "1. Siéntese a %.0f cm de la pantalla (mídalo con una regla; use lentes de lectura si los usa).\n", distanciaCm)
                + "2. Tape un ojo y mire fijamente el punto central, sin mover la vista.\n"
                + "3. ¿Alguna línea se ve ondulada, borrosa, o falta algún cuadro? Haga clic o arrastre sobre esas zonas.");
        instrucciones.setWrapText(true);
        instrucciones.getStyleClass().add("etiqueta-instruccion");
        instrucciones.setMaxWidth(460);

        ToggleGroup grupo = new ToggleGroup();
        ToggleButton od = new ToggleButton("Ojo derecho");
        ToggleButton oi = new ToggleButton("Ojo izquierdo");
        od.setToggleGroup(grupo); oi.setToggleGroup(grupo); od.setSelected(true);
        od.getStyleClass().add("boton-alternar"); oi.getStyleClass().add("boton-alternar");
        grupo.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) { (a == null ? od : (ToggleButton) a).setSelected(true); return; }
            marcasActuales = b == od ? marcasDerecho : marcasIzquierdo;
            dibujar();
        });
        Button limpiar = new Button("Limpiar marcas");
        limpiar.getStyleClass().add("boton-secundario");
        limpiar.setOnAction(e -> { for (boolean[] fila : marcasActuales) java.util.Arrays.fill(fila, false); dibujar(); });

        lblResumen = new Label();
        lblResumen.setWrapText(true);
        lblResumen.getStyleClass().add("resultado-convertidor");
        lblResumen.setMaxWidth(460);

        Label nota = new Label(reducida
                ? String.format(Locale.US, "La pantalla es pequeña para el tamaño estándar: la rejilla se redujo, por eso la distancia es %.0f cm en lugar de 29 cm.", distanciaCm)
                : String.format(Locale.US, "Cuadros de %.0f mm reales (PPI %.0f). Si el tamaño no coincide con una regla, calibre la pantalla en Herramientas.",
                LADO_CUADRO_MM, AppConfig.PPI_ESTANDAR_MONITOR));
        nota.setWrapText(true);
        nota.getStyleClass().add("etiqueta-ayuda");
        nota.setMaxWidth(460);

        Region espacio = new Region();
        VBox.setVgrow(espacio, Priority.ALWAYS);
        VBox lateral = new VBox(16, titulo, instrucciones, new HBox(8, od, oi), limpiar, lblResumen, espacio, nota);
        lateral.setPadding(new Insets(60, 24, 24, 24));
        lateral.setPrefWidth(500);

        StackPane centro = new StackPane(lienzo);
        centro.setAlignment(Pos.CENTER);
        centro.setPadding(new Insets(24));
        raiz.setLeft(lateral);
        raiz.setCenter(centro);
        dibujar();
    }

    private int[] celda(double x, double y) {
        int cx = (int) Math.floor((x - 1) / ladoPx);
        int cy = (int) Math.floor((y - 1) / ladoPx);
        if (cx < 0 || cy < 0 || cx >= CUADROS || cy >= CUADROS) return null;
        return new int[]{cx, cy};
    }

    private void marcar(int[] c, boolean valor) {
        if (marcasActuales[c[1]][c[0]] == valor) return;
        marcasActuales[c[1]][c[0]] = valor;
        dibujar();
    }

    private void dibujar() {
        GraphicsContext g = lienzo.getGraphicsContext2D();
        double total = ladoPx * CUADROS;
        g.setFill(Color.WHITE);
        g.fillRect(0, 0, lienzo.getWidth(), lienzo.getHeight());
        g.setFill(Color.rgb(230, 80, 70, 0.45));
        for (int y = 0; y < CUADROS; y++)
            for (int x = 0; x < CUADROS; x++)
                if (marcasActuales[y][x]) g.fillRect(1 + x * ladoPx, 1 + y * ladoPx, ladoPx, ladoPx);
        g.setStroke(Color.BLACK);
        g.setLineWidth(1);
        for (int i = 0; i <= CUADROS; i++) {
            double p = 1 + i * ladoPx;
            g.strokeLine(p, 1, p, 1 + total);
            g.strokeLine(1, p, 1 + total, p);
        }
        double r = Math.max(4, ladoPx * 0.28);
        g.setFill(Color.BLACK);
        g.fillOval(1 + total / 2 - r, 1 + total / 2 - r, 2 * r, 2 * r);
        actualizarResumen();
    }

    private void actualizarResumen() {
        int od = contar(marcasDerecho), oi = contar(marcasIzquierdo);
        String texto = String.format(Locale.US, "Zonas marcadas · OD: %d cuadro(s) %s · OI: %d cuadro(s) %s",
                od, cuadrante(marcasDerecho), oi, cuadrante(marcasIzquierdo));
        if (od + oi > 0) texto += "\nCualquier distorsión o zona faltante amerita valoración por oftalmología.";
        lblResumen.setText(texto);
    }

    private static int contar(boolean[][] m) {
        int n = 0;
        for (boolean[] f : m) for (boolean b : f) if (b) n++;
        return n;
    }

    /** Cuadrantes afectados (desde la perspectiva de la persona). */
    private static String cuadrante(boolean[][] m) {
        boolean[] q = new boolean[4];
        for (int y = 0; y < CUADROS; y++)
            for (int x = 0; x < CUADROS; x++)
                if (m[y][x]) q[(y < CUADROS / 2 ? 0 : 2) + (x < CUADROS / 2 ? 0 : 1)] = true;
        String[] nombres = {"sup. izq.", "sup. der.", "inf. izq.", "inf. der."};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) if (q[i]) sb.append(sb.length() == 0 ? "(" : ", ").append(nombres[i]);
        return sb.length() == 0 ? "" : sb.append(")").toString();
    }
}
