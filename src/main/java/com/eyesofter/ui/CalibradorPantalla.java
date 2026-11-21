package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.TrigonometriaVisual;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Mide el PPI real del monitor con un objeto de tamaño estándar: una tarjeta
 * bancaria o credencial (ISO/IEC 7810 ID-1, 85.60 × 53.98 mm; la INE también lo es).
 *
 * <p>Es la forma más exacta de dimensionar optotipos: el PPI calculado desde la
 * diagonal depende de que el operador conozca las pulgadas exactas y del escalado
 * de Windows; aquí se mide directamente sobre la pantalla.</p>
 */
public final class CalibradorPantalla {
    public static final double ANCHO_TARJETA_MM = 85.60;
    public static final double ALTO_TARJETA_MM = 53.98;

    private CalibradorPantalla() { }

    /** Abre el asistente. @return PPI físico medido, o vacío si se canceló. */
    public static OptionalDouble medir(Window propietario) {
        Screen pantalla = Screen.getPrimary();
        double escala = pantalla.getOutputScaleX() > 0 ? pantalla.getOutputScaleX() : 1.0;
        double ppiInicial = AppConfig.PPI_ESTANDAR_MONITOR > 0 ? AppConfig.PPI_ESTANDAR_MONITOR : 96;
        double anchoInicialLogico = ANCHO_TARJETA_MM / TrigonometriaVisual.MM_POR_PULGADA * ppiInicial / escala;

        Stage ventana = new Stage();
        if (propietario != null) ventana.initOwner(propietario);
        ventana.initModality(Modality.APPLICATION_MODAL);
        ventana.setTitle("Medir pantalla con una tarjeta");
        ventana.setAlwaysOnTop(true);

        Rectangle tarjeta = new Rectangle();
        tarjeta.getStyleClass().add("tarjeta-calibracion");
        tarjeta.setArcWidth(18);
        tarjeta.setArcHeight(18);

        Slider control = new Slider(150, 900, anchoInicialLogico);
        control.setBlockIncrement(0.5);
        HBox.setHgrow(control, Priority.ALWAYS);

        Label lectura = new Label();
        lectura.getStyleClass().add("subtitulo-seccion");

        Runnable actualizar = () -> {
            double ancho = control.getValue();
            tarjeta.setWidth(ancho);
            tarjeta.setHeight(ancho * ALTO_TARJETA_MM / ANCHO_TARJETA_MM);
            double ppi = TrigonometriaVisual.calcularPPIPorObjeto(ancho * escala, ANCHO_TARJETA_MM);
            lectura.setText(String.format(Locale.US, "PPI medido: %.1f   ·   escalado de Windows: %.0f %%", ppi, escala * 100));
        };
        control.valueProperty().addListener((o, a, b) -> actualizar.run());
        actualizar.run();

        Button menos = new Button("−");
        Button mas = new Button("+");
        menos.setOnAction(e -> control.setValue(control.getValue() - 0.5));
        mas.setOnAction(e -> control.setValue(control.getValue() + 0.5));

        final double[] resultado = {Double.NaN};
        Button aceptar = new Button("Guardar medición");
        aceptar.getStyleClass().add("boton-primario");
        aceptar.setDefaultButton(true);
        aceptar.setOnAction(e -> {
            resultado[0] = TrigonometriaVisual.calcularPPIPorObjeto(control.getValue() * escala, ANCHO_TARJETA_MM);
            ventana.close();
        });
        Button cancelar = new Button("Cancelar");
        cancelar.getStyleClass().add("boton-secundario");
        cancelar.setCancelButton(true);
        cancelar.setOnAction(e -> ventana.close());

        Label titulo = new Label("Calibrar el tamaño real de la pantalla");
        titulo.getStyleClass().add("etiqueta-titulo");
        Label ayuda = new Label("1. Apoye una tarjeta bancaria o credencial (tamaño estándar) sobre la pantalla, en la esquina del recuadro.\n"
                + "2. Mueva el control (o use las flechas ← →) hasta que el recuadro tenga EXACTAMENTE el ancho de la tarjeta.\n"
                + "3. Guarde. Las letras de la prueba quedarán con su tamaño físico correcto.");
        ayuda.setWrapText(true);
        ayuda.getStyleClass().add("etiqueta-instruccion");

        StackPane zona = new StackPane(tarjeta);
        zona.setAlignment(Pos.TOP_LEFT);
        zona.setMinHeight(560 * ALTO_TARJETA_MM / ANCHO_TARJETA_MM + 40);
        zona.setPadding(new Insets(10));

        HBox controles = new HBox(10, menos, control, mas);
        controles.setAlignment(Pos.CENTER_LEFT);
        Region espacio = new Region();
        HBox.setHgrow(espacio, Priority.ALWAYS);
        HBox acciones = new HBox(10, lectura, espacio, cancelar, aceptar);
        acciones.setAlignment(Pos.CENTER_LEFT);

        VBox raiz = new VBox(16, titulo, ayuda, zona, controles, acciones);
        raiz.setPadding(new Insets(28));
        raiz.getStyleClass().add("ventana-emergente");
        Scene escena = new Scene(raiz, 1000, 760);
        escena.getStylesheets().add(CalibradorPantalla.class.getResource("/css/estilos.css").toExternalForm());
        escena.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.LEFT) control.setValue(control.getValue() - (e.isShiftDown() ? 5 : 0.5));
            if (e.getCode() == KeyCode.RIGHT) control.setValue(control.getValue() + (e.isShiftDown() ? 5 : 0.5));
        });
        ventana.setScene(escena);
        ventana.showAndWait();
        return Double.isNaN(resultado[0]) ? OptionalDouble.empty() : OptionalDouble.of(resultado[0]);
    }

    /** Mide y guarda en la configuración. @return el PPI guardado o vacío. */
    public static OptionalDouble medirYGuardar(Window propietario) {
        OptionalDouble ppi = medir(propietario);
        if (ppi.isPresent()) {
            AppConfig.PPI_ESTANDAR_MONITOR = Math.round(ppi.getAsDouble() * 10) / 10.0;
            Screen p = Screen.getPrimary();
            double anchoFisico = p.getBounds().getWidth() * p.getOutputScaleX();
            double altoFisico = p.getBounds().getHeight() * p.getOutputScaleY();
            AppConfig.MONITOR_DIAGONAL_PULGADAS = Math.round(Math.hypot(anchoFisico, altoFisico) / ppi.getAsDouble() * 10) / 10.0;
            AppConfig.guardar();
        }
        return ppi;
    }
}
