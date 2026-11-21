package com.eyesofter.ui;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Visor interno robusto: conserva el documento abierto durante toda la navegación. */
public final class VisorPdfWindow {
    private VisorPdfWindow() { }

    public static void mostrar(Stage owner, File archivo) {
        if (archivo == null || !archivo.isFile()) return;
        final PDDocument documento;
        try { documento = PDDocument.load(archivo); }
        catch (Exception ex) { mostrarError(owner, "No se pudo abrir el PDF", ex.getMessage()); return; }

        Stage ventana = new Stage();
        ventana.initOwner(owner); ventana.initModality(Modality.WINDOW_MODAL); ventana.setResizable(true);
        ventana.setTitle("◈ Vista previa · " + archivo.getName());
        ventana.setMinWidth(900); ventana.setMinHeight(700); ventana.setWidth(1100); ventana.setHeight(820);

        ImageView imagen = new ImageView(); imagen.setPreserveRatio(true); imagen.setSmooth(true);
        ScrollPane desplazamiento = new ScrollPane(imagen); desplazamiento.setFitToWidth(true); desplazamiento.setFitToHeight(true);
        Label estado = new Label();
        Button anterior = new Button("← Anterior"), siguiente = new Button("Siguiente →");
        Button zoomMenos = new Button("− Zoom"), zoomMas = new Button("＋ Zoom"), salir = new Button("✕ Salir");
        boolean yaDescargado = archivo.toPath().toAbsolutePath().startsWith(new File("expedientes_pdf").toPath().toAbsolutePath());
        Button accion = new Button(yaDescargado ? "⌫ Eliminar" : "⇩ Descargar");
        final int[] pagina = {0}; final double[] dpi = {130};
        PDFRenderer renderizador = new PDFRenderer(documento);

        Runnable pintar = () -> {
            try {
                BufferedImage imagenPagina = renderizador.renderImageWithDPI(pagina[0], (float) dpi[0]);
                File temporal = Files.createTempFile("eyesofter-pdf-pagina-", ".png").toFile(); temporal.deleteOnExit();
                ImageIO.write(imagenPagina, "png", temporal); imagen.setImage(new Image(temporal.toURI().toString(), true));
                estado.setText("Página " + (pagina[0] + 1) + " de " + documento.getNumberOfPages() + " · " + archivo.getName());
                anterior.setDisable(pagina[0] == 0); siguiente.setDisable(pagina[0] >= documento.getNumberOfPages() - 1);
            } catch (Exception ex) { estado.setText("No se pudo renderizar la página: " + ex.getMessage()); }
        };
        anterior.setOnAction(e -> { if (pagina[0] > 0) { pagina[0]--; pintar.run(); } });
        siguiente.setOnAction(e -> { if (pagina[0] < documento.getNumberOfPages() - 1) { pagina[0]++; pintar.run(); } });
        zoomMenos.setOnAction(e -> { dpi[0] = Math.max(70, dpi[0] - 20); pintar.run(); });
        zoomMas.setOnAction(e -> { dpi[0] = Math.min(260, dpi[0] + 20); pintar.run(); });
        salir.setOnAction(e -> ventana.close());
        accion.setOnAction(e -> {
            try {
                if (yaDescargado) {
                    Files.deleteIfExists(archivo.toPath()); ventana.close();
                } else {
                    File carpeta = new File("expedientes_pdf"); carpeta.mkdirs();
                    Files.copy(archivo.toPath(), new File(carpeta, archivo.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    accion.setText("✓ Descargado"); accion.setDisable(true);
                }
            } catch (Exception ex) { mostrarError(ventana, "No se pudo completar la acción", ex.getMessage()); }
        });
        if (yaDescargado) {
            Button mover = new Button("⇱ Mover");
            mover.setOnAction(e -> {
                DirectoryChooser selector = new DirectoryChooser(); selector.setTitle("Mover expediente a otra carpeta");
                File destino = selector.showDialog(ventana);
                if (destino != null) try { Files.move(archivo.toPath(), new File(destino, archivo.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING); ventana.close(); } catch (Exception ex) { mostrarError(ventana, "No se pudo mover", ex.getMessage()); }
            });
            HBox barra = new HBox(8, anterior, siguiente, zoomMenos, zoomMas, accion, mover, estado, salir);
            mostrarVentana(ventana, barra, desplazamiento, documento, pintar);
        } else {
            HBox barra = new HBox(8, anterior, siguiente, zoomMenos, zoomMas, accion, estado, salir);
            mostrarVentana(ventana, barra, desplazamiento, documento, pintar);
        }
    }

    private static void mostrarVentana(Stage ventana, HBox barra, ScrollPane contenido, PDDocument documento, Runnable pintar) {
        barra.setPadding(new Insets(10)); barra.setFillHeight(true);
        BorderPane raiz = new BorderPane(contenido); raiz.setTop(barra); raiz.getStyleClass().add("ventana-emergente");
        ventana.setScene(new Scene(raiz)); TemaAplicacion.aplicar(ventana.getScene());
        ventana.setOnHidden(e -> { try { documento.close(); } catch (Exception ignored) { } });
        ventana.show(); pintar.run();
    }

    private static void mostrarError(Stage owner, String titulo, String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje == null ? "Error desconocido" : mensaje, ButtonType.OK);
        alerta.setHeaderText(titulo); if (owner != null) alerta.initOwner(owner); alerta.showAndWait();
    }
}
