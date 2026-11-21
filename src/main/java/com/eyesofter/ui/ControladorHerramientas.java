package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.ConvertidorAgudeza;
import com.eyesofter.formulas.TrigonometriaVisual;
import com.eyesofter.voz.VozGuia;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.Locale;

/** Herramientas clínicas complementarias al tamizaje. */
public class ControladorHerramientas implements VistaAdministrativa {
    @FXML private VBox contenido;
    private ControladorPanelAdministrativo panel;

    @Override
    public void setPanelAdministrativo(ControladorPanelAdministrativo panel) {
        this.panel = panel;
    }

    @FXML
    private void initialize() {
        Label titulo = new Label("Herramientas clínicas");
        titulo.getStyleClass().add("titulo-admin");
        Label subtitulo = new Label("Pruebas complementarias y utilidades de calibración. Los resultados de estas herramientas no se guardan en el expediente.");
        subtitulo.getStyleClass().add("subtitulo-admin");
        subtitulo.setWrapText(true);

        GridPane tarjetas = new GridPane();
        tarjetas.setHgap(16);
        tarjetas.setVgap(16);
        javafx.scene.layout.ColumnConstraints c = new javafx.scene.layout.ColumnConstraints();
        c.setPercentWidth(50);
        tarjetas.getColumnConstraints().addAll(c, c);

        tarjetas.add(tarjeta("▦  Rejilla de Amsler",
                "Detecta distorsiones o zonas faltantes en la visión central (mácula). Se realiza a 30–40 cm, un ojo a la vez; la persona marca con el ratón las zonas onduladas o borrosas.",
                "Abrir rejilla", () -> Navegador.mostrar(ventana(), "/fxml/Amsler.fxml", "EYESOFTER · Rejilla de Amsler")), 0, 0);
        tarjetas.add(tarjeta("◐  Sensibilidad al contraste",
                "Letras grandes que pierden contraste de 0.15 en 0.15 unidades log (tipo Pelli-Robson). Útil cuando la agudeza es normal pero la persona refiere ver \"borroso\" o con poca luz.",
                "Iniciar prueba", () -> Navegador.mostrar(ventana(), "/fxml/Contraste.fxml", "EYESOFTER · Sensibilidad al contraste")), 1, 0);
        tarjetas.add(tarjetaCalibracion(), 0, 1);
        tarjetas.add(tarjetaConvertidor(), 1, 1);

        contenido.getChildren().setAll(titulo, subtitulo, tarjetas);
    }

    private Stage ventana() {
        return (Stage) contenido.getScene().getWindow();
    }

    private VBox tarjeta(String titulo, String descripcion, String accion, Runnable alPulsar) {
        Label t = new Label(titulo);
        t.getStyleClass().add("subtitulo-seccion");
        Label d = new Label(descripcion);
        d.setWrapText(true);
        d.getStyleClass().add("etiqueta-subtitulo");
        Button b = new Button(accion);
        b.getStyleClass().add("boton-primario");
        b.setOnAction(e -> alPulsar.run());
        VBox caja = new VBox(12, t, d, b);
        caja.getStyleClass().addAll("tarjeta-admin", "tarjeta-herramienta");
        VBox.setVgrow(d, Priority.ALWAYS);
        return caja;
    }

    private VBox tarjetaCalibracion() {
        Label estado = new Label(textoEstadoCalibracion());
        estado.setWrapText(true);
        estado.getStyleClass().add("etiqueta-subtitulo");
        Button medir = new Button("📐 Medir pantalla con tarjeta");
        medir.getStyleClass().add("boton-primario");
        medir.setOnAction(e -> CalibradorPantalla.medirYGuardar(ventana()).ifPresent(p -> estado.setText(textoEstadoCalibracion())));
        Button probarVoz = new Button("🔊 Probar voz guía");
        probarVoz.getStyleClass().add("boton-secundario");
        probarVoz.setOnAction(e -> VozGuia.decir("Hola. Esta es la voz guía de EYESOFTER.", null));
        Label t = new Label("📏  Calibración del equipo");
        t.getStyleClass().add("subtitulo-seccion");
        Label d = new Label("El tamaño de las letras depende del PPI real del monitor. Mídalo una vez por equipo con una tarjeta bancaria o credencial de tamaño estándar.");
        d.setWrapText(true);
        d.getStyleClass().add("etiqueta-subtitulo");
        Label motor = new Label("Voz guía: " + VozGuia.motorDisponible());
        motor.getStyleClass().add("etiqueta-ayuda");
        VBox caja = new VBox(12, t, d, estado, motor, new HBox(10, medir, probarVoz));
        caja.getStyleClass().addAll("tarjeta-admin", "tarjeta-herramienta");
        return caja;
    }

    private String textoEstadoCalibracion() {
        Screen p = Screen.getPrimary();
        return String.format(Locale.US, "PPI configurado: %.1f · diagonal: %.1f in · escalado de Windows: %.0f %% · cámara: %s",
                AppConfig.PPI_ESTANDAR_MONITOR, AppConfig.MONITOR_DIAGONAL_PULGADAS, p.getOutputScaleX() * 100,
                AppConfig.DISTANCIA_FOCAL_CAMARA_PX > 0 ? "calibrada" : "sin calibrar");
    }

    private VBox tarjetaConvertidor() {
        Label t = new Label("⇄  Convertidor de agudeza visual");
        t.getStyleClass().add("subtitulo-seccion");
        ComboBox<ConvertidorAgudeza.Notacion> tipo = new ComboBox<>();
        tipo.getItems().addAll(ConvertidorAgudeza.Notacion.values());
        tipo.setValue(ConvertidorAgudeza.Notacion.SNELLEN);
        TextField valor = new TextField("20/40");
        valor.getStyleClass().add("campo-texto");
        valor.setPrefWidth(140);
        TextField distancia = new TextField(String.format(Locale.US, "%.1f", AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO));
        distancia.getStyleClass().add("campo-texto");
        distancia.setPrefWidth(80);
        Label resultado = new Label();
        resultado.setWrapText(true);
        resultado.getStyleClass().add("resultado-convertidor");
        Runnable calcular = () -> {
            try {
                ConvertidorAgudeza.Equivalencias e = ConvertidorAgudeza.convertir(valor.getText(), tipo.getValue());
                double metros = Double.parseDouble(distancia.getText().replace(',', '.'));
                double mm = ConvertidorAgudeza.alturaLetraMm(e, metros);
                double px = TrigonometriaVisual.calcularAlturaPixel(mm, AppConfig.PPI_ESTANDAR_MONITOR);
                resultado.setText(e.descripcion() + String.format(Locale.US,
                        "\nAltura de la letra a %.1f m: %.1f mm (%.0f px físicos en este monitor)", metros, mm, px));
            } catch (Exception ex) {
                resultado.setText(ex instanceof IllegalArgumentException && ex.getMessage() != null && !(ex instanceof NumberFormatException)
                        ? ex.getMessage() : "Valor no válido.");
            }
        };
        valor.setOnAction(e -> calcular.run());
        distancia.setOnAction(e -> calcular.run());
        tipo.setOnAction(e -> calcular.run());
        valor.textProperty().addListener((o, a, b) -> calcular.run());
        distancia.textProperty().addListener((o, a, b) -> calcular.run());
        calcular.run();
        tipo.setMaxWidth(Double.MAX_VALUE);
        valor.setMinWidth(110);
        distancia.setMinWidth(70);
        Label etiquetaDistancia = new Label("Distancia (m):");
        etiquetaDistancia.getStyleClass().add("etiqueta-ayuda");
        HBox fila = new HBox(10, valor, etiquetaDistancia, distancia);
        fila.setAlignment(Pos.CENTER_LEFT);
        VBox caja = new VBox(12, t, tipo, fila, resultado);
        caja.getStyleClass().addAll("tarjeta-admin", "tarjeta-herramienta");
        return caja;
    }
}
