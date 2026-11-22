package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * "Descarga la app móvil": muestra el QR de la página de descarga del portal (/App) para que la
 * familia lo escanee con su teléfono, y un botón para abrirla en el navegador.
 *
 * El QR y el instalador vienen del portal, así que cambiar dónde está el APK no requiere tocar
 * este equipo. Sin internet se muestra la dirección para escribirla a mano.
 */
public final class DialogoAppMovil {
    private DialogoAppMovil() { }

    public static String urlPagina() {
        String base = AppConfig.URL_PORTAL == null ? "" : AppConfig.URL_PORTAL.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/App";
    }

    public static void mostrar(Window propietario) {
        String pagina = urlPagina();

        Dialog<ButtonType> dialogo = new Dialog<>();
        if (propietario != null) dialogo.initOwner(propietario);
        dialogo.setTitle("Descarga la app móvil");
        dialogo.setHeaderText("App EYESOFTER para el teléfono");

        Label explicacion = new Label("Con la app, la familia recibe los resultados del tamizaje, los avisos de la escuela "
                + "y el pase médico con QR. Escanee este código con la cámara del teléfono:");
        explicacion.setWrapText(true);
        explicacion.setMaxWidth(380);

        Label cargando = new Label("Cargando código QR…");
        cargando.setWrapText(true);
        cargando.setMaxWidth(260);
        ImageView qr = new ImageView();
        qr.setFitWidth(260);
        qr.setFitHeight(260);
        qr.setPreserveRatio(true);
        qr.setAccessibleText("Código QR para abrir la página de descarga de la app en el teléfono");
        Image imagen = new Image(pagina + "/Qr", 260, 260, true, false, true);
        imagen.progressProperty().addListener((o, a, b) -> {
            if (b.doubleValue() >= 1.0 && !imagen.isError()) cargando.setVisible(false);
        });
        imagen.errorProperty().addListener((o, a, b) -> {
            if (b) cargando.setText("No se pudo cargar el código QR (¿sin internet?). En el teléfono, abra la dirección de abajo.");
        });
        qr.setImage(imagen);
        StackPane marco = new StackPane(cargando, qr);
        marco.setMinSize(270, 270);
        marco.setStyle("-fx-background-color: white; -fx-border-color: #d0d7e2; -fx-border-radius: 6; -fx-background-radius: 6;");

        Label direccion = new Label(pagina);
        direccion.setWrapText(true);
        direccion.setMaxWidth(380);
        direccion.setStyle("-fx-font-weight: bold;");

        Label nota = new Label("Por ahora la app es para Android. En iPhone, la familia entra al portal desde Safari con la misma cuenta. "
                + "Descárguela solo desde esa página: la escuela nunca la envía por WhatsApp.");
        nota.setWrapText(true);
        nota.setMaxWidth(380);
        nota.getStyleClass().add("etiqueta-subtitulo");

        Label estado = new Label();
        estado.setWrapText(true);
        estado.setMaxWidth(380);
        Button abrir = new Button("Abrir la página en este equipo");
        abrir.getStyleClass().add("boton-secundario");
        abrir.setOnAction(e -> {
            if (!TarjetaConexionSuite.abrirNavegador(pagina)) estado.setText("No se pudo abrir el navegador. Escriba la dirección de arriba.");
        });

        VBox contenido = new VBox(12, explicacion, marco, direccion, abrir, estado, nota);
        contenido.setAlignment(Pos.CENTER);
        contenido.setPadding(new Insets(8));
        dialogo.getDialogPane().setContent(contenido);
        dialogo.getDialogPane().getButtonTypes().add(new ButtonType("Cerrar", ButtonBar.ButtonData.CANCEL_CLOSE));
        dialogo.showAndWait();
    }
}
