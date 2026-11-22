package com.eyesofter.ui;

import com.eyesofter.voz.DispositivosAudio;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Controlador de la Bienvenida Interactiva para el Alumno (Pantalla 6)
 */
public class ControladorBienvenidaPaciente {

    @FXML private Label lblNombreAlumno;
    @FXML private Label lblInstruccion;
    @FXML private Label lblEstadoMic;
    @FXML private Button btnContinuarManual;

    private String nombrePaciente;
    private Stage stagePrincipal;
    private boolean escuchando = true;

    // Variable para controlar la animación y poder detenerla luego
    private ScaleTransition animacionLatido;

    public static void mostrarVentana(Stage stage, String nombreA) {
        try {
            FXMLLoader loader = new FXMLLoader(ControladorBienvenidaPaciente.class.getResource("/fxml/Bienvenida.fxml"));
            Parent root = loader.load();

            ControladorBienvenidaPaciente controlador = loader.getController();
            controlador.inicializarDatos(stage, nombreA);

            stage.setTitle("EYESOFTER · Instrucciones para la persona paciente");
            stage.setScene(new Scene(root));

            ModoKiosco.aplicar(stage);

            // NUEVO EFECTO: Transición de entrada (Fade-In) para toda la ventana
            FadeTransition fade = new FadeTransition(Duration.millis(1200), root);
            fade.setFromValue(0.0);
            fade.setToValue(1.0);
            fade.play();

            stage.centerOnScreen();
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void inicializarDatos(Stage stage, String nombreA) {
        this.stagePrincipal = stage;
        this.nombrePaciente = nombreA;

        // Texto dinámico mucho más amigable
        lblNombreAlumno.setText("Hola, " + nombrePaciente);
        lblEstadoMic.setText("Preparando el reconocimiento de voz…");
        btnContinuarManual.setVisible(true);

        // SE QUITÓ LA TILDE A "SI" para que Piper lo pronuncie fluidamente
        String textoBienvenida = "Hola " + nombrePaciente + ". Realizaremos un breve tamizaje visual. Cuando estés listo, di SI para comenzar.";

        reproducirVozMecanica(textoBienvenida, () -> {
            iniciarEscuchaVoz();
        });
    }

    /**
     * Utiliza Piper TTS y ejecuta una acción posterior (Runnable) al terminar de hablar.
     */
    private void reproducirVozMecanica(String texto, Runnable accionSiguiente) {
        Task<Void> tareaVoz = new Task<>() {
            @Override
            protected Void call() throws Exception {
                try {
                    File piperExe = new File("modelo_tts/piper.exe");
                    File modeloVoz = new File("modelo_tts/es_MX-claude-high.onnx");
                    File salidaAudio = new File("modelo_tts/salida_temporal.wav");

                    ProcessBuilder pb = new ProcessBuilder(
                            piperExe.getAbsolutePath(),
                            "-m", modeloVoz.getAbsolutePath(),
                            "-f", salidaAudio.getAbsolutePath()
                    );
                    Process proceso = pb.start();

                    OutputStream os = proceso.getOutputStream();
                    os.write(texto.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();

                    proceso.waitFor();

                    if (salidaAudio.exists()) {
                        AudioInputStream audioStream = AudioSystem.getAudioInputStream(salidaAudio);
                        Clip clip = AudioSystem.getClip();
                        clip.open(audioStream);
                        clip.start();

                        long duracionMilisegundos = clip.getMicrosecondLength() / 1000;
                        Thread.sleep(duracionMilisegundos + 200);

                        clip.close();
                        audioStream.close();
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ [ERROR TTS]: Fallo en la reproducción.");
                    e.printStackTrace();
                }
                return null;
            }
        };

        tareaVoz.setOnSucceeded(e -> {
            if (accionSiguiente != null) {
                accionSiguiente.run();
            }
        });

        new Thread(tareaVoz).start();
    }

    private void iniciarEscuchaVoz() {
        // Configuramos la UI en el hilo principal de JavaFX antes de arrancar el Hilo de Vosk
        Platform.runLater(() -> {
            lblEstadoMic.setText("Micrófono activo. Diga “sí” para continuar.");

            // NUEVO EFECTO: Iniciar el latido del micrófono
            animacionLatido = new ScaleTransition(Duration.millis(600), lblEstadoMic);
            animacionLatido.setByX(0.06); // Crece un poco a los lados
            animacionLatido.setByY(0.06); // Crece un poco hacia arriba y abajo
            animacionLatido.setCycleCount(ScaleTransition.INDEFINITE); // Repetir infinito
            animacionLatido.setAutoReverse(true); // Efecto latido (va y viene)
            animacionLatido.play();
        });

        Task<Void> tareaReconocimiento = new Task<>() {
            @Override
            protected Void call() throws Exception {
                File carpetaModelo = new File(com.eyesofter.config.AppConfig.RUTA_MODELO_VOSK);
                String rutaModeloAbsoluta = carpetaModelo.getAbsolutePath();

                try (Model modelo = new Model(rutaModeloAbsoluta);
                     Recognizer reconocedor = new Recognizer(modelo, 16000.0f, "[\"sí\", \"si\", \"[unk]\"]");
                     DispositivosAudio.Captura microfono = DispositivosAudio.abrirCaptura()) {

                    microfono.iniciar();
                    Platform.runLater(() -> lblEstadoMic.setText("Micrófono activo: " + microfono.getNombreDispositivo()));

                    byte[] buffer = new byte[4096];
                    int bytesLeidos;

                    while (escuchando && !isCancelled()) {
                        bytesLeidos = microfono.leer(buffer);
                        if (bytesLeidos > 0) {
                            if (reconocedor.acceptWaveForm(buffer, bytesLeidos)) {
                                String resultado = reconocedor.getResult();

                                if (resultado.contains("\"sí\"") || resultado.contains("\"si\"")) {
                                    escuchando = false;
                                    Platform.runLater(() -> avanzarACalibracion());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        if (animacionLatido != null) animacionLatido.stop(); // Detener si hay error
                        lblEstadoMic.setText("No fue posible iniciar el reconocimiento de voz. Puede continuar de forma manual.");
                        lblEstadoMic.getStyleClass().add("estado-error");
                    });
                    e.printStackTrace();
                }
                return null;
            }
        };
        new Thread(tareaReconocimiento).start();
    }

    private void avanzarACalibracion() {
        // NUEVO EFECTO: Detener el latido y restaurar el tamaño original del botón
        if (animacionLatido != null) {
            animacionLatido.stop();
            lblEstadoMic.setScaleX(1.0);
            lblEstadoMic.setScaleY(1.0);
        }

        lblEstadoMic.setText("Respuesta confirmada. Iniciando calibración…");

        // Aplicamos el CSS de éxito brillante que hicimos anteriormente
        lblEstadoMic.getStyleClass().add("estado-exito");

        reproducirVozMecanica("Respuesta confirmada. Iniciaremos la calibración de posición.",
                () -> Platform.runLater(() -> ControladorCalibracionPosicion.mostrarVentana(stagePrincipal)));
    }

    /** Respaldo para sesión supervisada cuando el reconocimiento de voz no está disponible. */
    @FXML
    private void iniciarEvaluacionManual() {
        if (animacionLatido != null) animacionLatido.stop();
        escuchando = false;
        ControladorCalibracionPosicion.mostrarVentana(stagePrincipal);
    }
}
