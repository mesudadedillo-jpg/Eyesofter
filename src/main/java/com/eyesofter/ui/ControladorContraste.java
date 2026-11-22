package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.FuentesOptotipo;
import com.eyesofter.formulas.SensibilidadContraste;
import com.eyesofter.formulas.TrigonometriaVisual;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Prueba de sensibilidad al contraste tipo Pelli-Robson, guiada por el operador.
 * Tripletes de letras grandes (2.8° a la distancia configurada) cuyo contraste baja
 * 0.15 log por triplete. El operador marca las letras no leídas; termina cuando un
 * triplete tiene 2 o más errores. Puntuación letra por letra: 0.05 log por letra.
 */
public class ControladorContraste implements ControladorDeVista {
    @FXML private BorderPane raiz;

    private static final String[] LETRAS = {"C", "D", "H", "K", "N", "O", "R", "S", "V", "Z"};
    private final SensibilidadContraste prueba = new SensibilidadContraste();
    private final List<Label> etiquetas = new ArrayList<>();
    private boolean[] leidas = new boolean[3];
    private String[] tripleteActual;
    private HBox filaLetras;
    private Label lblTriplete;
    private Label lblEstado;
    private Label lblResultado;
    private Button btnRegistrar;
    private double tamanioFuente;
    private double tamanioIdeal;

    /** Tres letras (~0.75 em c/u) + dos espacios (0.6 em) deben caber en el 85 % del ancho. */
    private static double limitar(double ideal, double ancho) {
        double maximo = Math.min(Screen.getPrimary().getVisualBounds().getHeight() * 0.38, ancho * 0.85 / 3.5);
        return Math.min(ideal, maximo);
    }
    private boolean terminada;
    private double distanciaContrasteM = 1.0;

    @Override
    public void setStage(Stage stage) {
        double escala = Screen.getPrimary().getOutputScaleY() > 0 ? Screen.getPrimary().getOutputScaleY() : 1.0;
        // Pelli-Robson se valida a 1 m con letras de 2.8°. Antes se usaba la distancia de la
        // prueba de agudeza (hasta 4 m) y, si las letras no cabían, se achicaban: letras más
        // pequeñas son más difíciles y la sensibilidad salía menor. Ahora el tamaño angular se
        // conserva: si a 1 m no caben, se indica una distancia más corta.
        double distanciaMm = 1000.0;
        double alturaMm = 2 * distanciaMm * Math.tan(Math.toRadians(SensibilidadContraste.GRADOS_LETRA / 2.0));
        tamanioFuente = TrigonometriaVisual.calcularTamanioFuenteLogico(alturaMm, AppConfig.PPI_ESTANDAR_MONITOR,
                FuentesOptotipo.factorDeCorreccion(AppConfig.FUENTE_OPTOTIPO_DEFECTO), escala);
        tamanioIdeal = tamanioFuente;
        double anchoDisponible = stage.getWidth() > 0 ? stage.getWidth() : Screen.getPrimary().getVisualBounds().getWidth();
        tamanioFuente = limitar(tamanioIdeal, anchoDisponible);
        boolean reducida = tamanioFuente < tamanioIdeal;
        distanciaContrasteM = (reducida ? distanciaMm * tamanioFuente / tamanioIdeal : distanciaMm) / 1000.0;

        Label titulo = new Label("Sensibilidad al contraste");
        titulo.getStyleClass().add("etiqueta-titulo");
        lblTriplete = new Label();
        lblTriplete.getStyleClass().add("etiqueta-subtitulo-fuerte");
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        Button reiniciar = new Button("↺ Reiniciar");
        reiniciar.getStyleClass().add("boton-secundario");
        reiniciar.setOnAction(e -> reiniciar());
        HBox barra = new HBox(16, titulo, lblTriplete, espacio, reiniciar);
        barra.setAlignment(Pos.CENTER_LEFT);
        barra.setPadding(new Insets(14, 32, 8, 160));

        filaLetras = new HBox(Math.max(20, tamanioFuente * 0.6));
        filaLetras.setAlignment(Pos.CENTER);

        lblEstado = new Label();
        lblEstado.setWrapText(true);
        lblEstado.getStyleClass().add("estado-voz");
        Label ayuda = new Label(String.format(Locale.US,
                "Siente a la persona a %.2f m de la pantalla%s · luz ambiente uniforme, sin reflejos. Clic en una letra (o 1·2·3) = no leída · Enter registra.",
                distanciaContrasteM, reducida ? " (las letras de 2.8° no caben a 1 m en esta pantalla)" : ""));
        ayuda.getStyleClass().add("etiqueta-ayuda");
        ayuda.setWrapText(true);
        btnRegistrar = new Button("Registrar triplete  ⏎");
        btnRegistrar.getStyleClass().add("boton-primario");
        btnRegistrar.setOnAction(e -> registrar());
        lblResultado = new Label();
        lblResultado.setWrapText(true);
        lblResultado.getStyleClass().add("resultado-convertidor");
        VBox inferior = new VBox(10, lblEstado, new HBox(12, btnRegistrar), ayuda, lblResultado);
        inferior.setPadding(new Insets(12, 32, 28, 32));

        raiz.setTop(barra);
        raiz.setCenter(filaLetras);
        raiz.setBottom(inferior);
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            KeyCode c = e.getCode();
            if (c == KeyCode.DIGIT1 || c == KeyCode.NUMPAD1) { alternar(0); e.consume(); }
            else if (c == KeyCode.DIGIT2 || c == KeyCode.NUMPAD2) { alternar(1); e.consume(); }
            else if (c == KeyCode.DIGIT3 || c == KeyCode.NUMPAD3) { alternar(2); e.consume(); }
            else if (c == KeyCode.ENTER) { registrar(); e.consume(); }
        });
        raiz.widthProperty().addListener((o, a, b) -> {
            if (b.doubleValue() <= 0) return;
            tamanioFuente = limitar(tamanioIdeal, b.doubleValue());
            filaLetras.setSpacing(Math.max(20, tamanioFuente * 0.6));
            for (Label l : etiquetas) l.setStyle(l.getStyle().replaceAll("-fx-font-size: [0-9.]+px", String.format(Locale.US, "-fx-font-size: %.1fpx", tamanioFuente)));
        });
        reiniciar();
    }

    private void reiniciar() {
        prueba.reiniciar();
        terminada = false;
        btnRegistrar.setDisable(false);
        lblResultado.setText("");
        mostrarTriplete();
    }

    private void mostrarTriplete() {
        int indice = prueba.getTripletesEvaluados();
        double logCs = SensibilidadContraste.logCsDeTriplete(indice);
        SensibilidadContraste.Gris gris = SensibilidadContraste.grisParaLogCs(logCs);
        List<String> bolsa = new ArrayList<>(List.of(LETRAS));
        Collections.shuffle(bolsa);
        tripleteActual = bolsa.subList(0, 3).toArray(new String[0]);
        leidas = new boolean[]{true, true, true};
        etiquetas.clear();
        filaLetras.getChildren().clear();
        String color = String.format("#%02x%02x%02x", gris.valor8bits(), gris.valor8bits(), gris.valor8bits());
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            Label l = new Label(tripleteActual[i]);
            l.setStyle(String.format(Locale.US, "-fx-font-family: '%s'; -fx-font-weight: 900; -fx-font-size: %.1fpx; -fx-text-fill: %s;",
                    AppConfig.FUENTE_OPTOTIPO_DEFECTO, tamanioFuente, color));
            l.getStyleClass().add("letra-contraste");
            l.setOnMouseClicked(e -> alternar(idx));
            l.setCursor(javafx.scene.Cursor.HAND);
            etiquetas.add(l);
            filaLetras.getChildren().add(l);
        }
        lblTriplete.setText(String.format(Locale.US, "Triplete %d de %d · log CS dibujado %.2f · contraste real %.2f %%",
                indice + 1, SensibilidadContraste.tripletesRepresentables(), SensibilidadContraste.logCsRealDeTriplete(indice),
                gris.contrasteReal() * 100));
        lblEstado.setText("Pida a la persona que lea las tres letras. Marque las que no leyó.");
        actualizarMarcas();
        raiz.requestFocus();
    }

    private void alternar(int i) {
        if (terminada || i >= leidas.length) return;
        leidas[i] = !leidas[i];
        actualizarMarcas();
    }

    private void actualizarMarcas() {
        // Las letras no cambian al marcarlas: un subrayado rojo bajo una letra de bajo contraste
        // era una pista visible para la persona evaluada. El estado se ve solo en el botón.
        int n = 0;
        StringBuilder noLeidas = new StringBuilder();
        for (int i = 0; i < leidas.length; i++) {
            if (leidas[i]) n++;
            else noLeidas.append(noLeidas.length() == 0 ? "" : ", ").append(i + 1);
        }
        btnRegistrar.setText("Registrar triplete (" + n + "/3)" + (noLeidas.length() > 0 ? " · no leídas: " + noLeidas : "") + "  ⏎");
    }

    private void registrar() {
        if (terminada) return;
        int n = 0;
        for (boolean b : leidas) if (b) n++;
        prueba.registrarTriplete(n);
        if (prueba.terminada()) {
            terminada = true;
            btnRegistrar.setDisable(true);
            filaLetras.getChildren().clear();
            double logCs = prueba.getLogCs();
            lblEstado.setText("Prueba terminada.");
            lblResultado.setText(String.format(Locale.US, "Sensibilidad al contraste: log CS %.2f  ·  %s\n"
                            + "(Referencia orientativa para adultos: ≥ 1.65 normal; 1.50–1.64 limítrofe; < 1.50 reducida. No es un diagnóstico.)",
                    logCs, SensibilidadContraste.interpretar(logCs)));
        } else {
            mostrarTriplete();
        }
    }
}
