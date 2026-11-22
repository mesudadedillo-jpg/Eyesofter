package com.eyesofter.ui;

import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.config.AppConfig;
import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.formulas.TrigonometriaVisual;
import com.eyesofter.voz.ReconocedorOptotipos;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Controlador de Pantalla Interactiva para el Examen Visual EYESOFTER.
 * Gestiona la calibración previa, la pantalla completa con optotipos dinámicos y el guardado en BD.
 */
public class ControladorPantallaTest {

    private Stage stagePrincipal;
    private String curpPaciente;
    private String nombrePaciente;
    private double distanciaMetros;

    private DetectorDistancia detectorDistancia;
    private ReconocedorOptotipos reconocedorVoz;

    private Label lblInstruccion;
    private Label lblOptotipo;
    private Label lblEstadoCamara;
    private Button btnIniciar;
    private Slider sliderDistancia; // NUEVO: Control de distancia

    private boolean camaraValidada = false;

    public ControladorPantallaTest(Stage stage, String curpPaciente, String nombrePaciente, double distanciaMetros) {
        this.stagePrincipal = stage;
        this.curpPaciente = curpPaciente;
        this.nombrePaciente = nombrePaciente;
        this.distanciaMetros = distanciaMetros;

        try {
            this.reconocedorVoz = new ReconocedorOptotipos("modelo_vosk/vosk-model-small-es-0.42");
            this.detectorDistancia = new DetectorDistancia(AppConfig.RUTA_MODELO_OPENCV);
            // La distancia objetivo se actualizará dinámicamente con el Slider
            this.detectorDistancia.setDistanciaObjetivo(this.distanciaMetros * 100.0);
        } catch (Exception e) {
            System.err.println("[ERROR HARDWARE]: No se pudo inicializar cámara o voz.");
            e.printStackTrace();
        }
    }

    /**
     * FASE 1: Pantalla de Preparación con validación de distancia en tiempo real.
     */
    public void mostrarPantallaPreparacion() {
        stagePrincipal.setTitle("EYESOFTER - Calibración y Preparación");

        // MODIFICACIÓN 1: Fondo general y Tarjeta centrada (Dashboard Clínico)
        StackPane fondoPantalla = new StackPane();
        fondoPantalla.setStyle("-fx-background-color: #F4F7F6;");

        VBox tarjeta = new VBox(25); // Mayor espaciado para mejor legibilidad
        tarjeta.setAlignment(Pos.CENTER);
        tarjeta.setPadding(new Insets(50));
        tarjeta.setMaxWidth(800);
        tarjeta.setMaxHeight(VBox.USE_PREF_SIZE);
        tarjeta.getStyleClass().add("contenedor-principal");

        Label lblTitulo = new Label("Preparación de Evaluación");
        lblTitulo.getStyleClass().add("etiqueta-titulo");

        Label lblSubtitulo = new Label("Paciente: " + nombrePaciente);
        lblSubtitulo.setStyle("-fx-font-size: 24px; -fx-text-fill: #555555; -fx-font-weight: bold;");

        lblInstruccion = new Label("Colócate frente al monitor a la distancia indicada.");
        lblInstruccion.getStyleClass().add("etiqueta-instruccion");

        // MODIFICACIÓN 2: Slider de distancia de 0.5m a 4m
        Label lblDistancia = new Label("Ajustar Distancia Focal (Metros):");
        lblDistancia.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        sliderDistancia = new Slider(0.5, 4.0, distanciaMetros);
        sliderDistancia.setMajorTickUnit(0.5);
        sliderDistancia.setMinorTickCount(0);
        sliderDistancia.setSnapToTicks(true); // Fuerza a que salte de 0.5 en 0.5
        sliderDistancia.setShowTickMarks(true);
        sliderDistancia.setShowTickLabels(true);
        sliderDistancia.setStyle("-fx-padding: 10px 0; -fx-pref-width: 400px;");

        // Evento para actualizar la cámara en tiempo real si el operador mueve el slider
        sliderDistancia.valueProperty().addListener((obs, oldVal, newVal) -> {
            this.distanciaMetros = newVal.doubleValue();
            if (detectorDistancia != null) {
                detectorDistancia.setDistanciaObjetivo(this.distanciaMetros * 100.0);
            }
        });

        lblEstadoCamara = new Label("🔴 Inicializando cámara...");
        lblEstadoCamara.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-padding: 20px;");

        btnIniciar = new Button("Estoy Listo (Iniciar Examen)");
        btnIniciar.getStyleClass().add("boton-primario"); // Aplicamos el estilo de tu CSS
        btnIniciar.setDisable(true);

        btnIniciar.setOnAction(e -> {
            if (camaraValidada) {
                iniciarExamenPantallaCompleta();
            }
        });

        tarjeta.getChildren().addAll(lblTitulo, lblSubtitulo, lblInstruccion, lblDistancia, sliderDistancia, lblEstadoCamara, btnIniciar);
        fondoPantalla.getChildren().add(tarjeta);

        Scene scene = new Scene(fondoPantalla);
        // Cargar CSS
        try {
            scene.getStylesheets().add(ControladorPantallaTest.class.getResource("/css/estilos.css").toExternalForm());
        } catch (Exception e) {
            System.err.println("⚠️ CSS no encontrado.");
        }

        stagePrincipal.setScene(scene);

        // MODIFICACIÓN 3: Configuración de Kiosco (Evitar minimizar, maximizado fijo)
        ModoKiosco.aplicar(stagePrincipal);

        stagePrincipal.show();

        iniciarBucleValidacionPostura();
    }

    private void iniciarBucleValidacionPostura() {
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(0.8), event -> {
            if (detectorDistancia != null) {
                boolean posicionCorrecta = detectorDistancia.validarPosicionPaciente();
                Platform.runLater(() -> {
                    if (posicionCorrecta) {
                        lblEstadoCamara.setText("🟢 Posición correcta. Distancia focal óptima.");
                        lblEstadoCamara.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #27ae60;");
                        btnIniciar.setDisable(false);
                        camaraValidada = true;
                    } else {
                        lblEstadoCamara.setText("🔴 Fuera de rango. Ajuste su posición a " + distanciaMetros + "m.");
                        lblEstadoCamara.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #c0392b;");
                        btnIniciar.setDisable(true);
                        camaraValidada = false;
                    }
                });
            }
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    /**
     * FASE 2: Pantalla Completa con Optotipos Dinámicos y Reconocimiento de Voz.
     */
    private void iniciarExamenPantallaCompleta() {
        // MODIFICACIÓN 4: Pantalla completa estricta (oculta la barra de tareas y bordes)
        ModoKiosco.aplicar(stagePrincipal);

        VBox rootPantalla = new VBox(40);
        rootPantalla.setAlignment(Pos.CENTER);
        rootPantalla.setStyle("-fx-background-color: #FFFFFF;"); // Blanco puro para alto contraste

        Label lblIndicacionTest = new Label("Menciona en voz alta la letra que visualizas:");
        lblIndicacionTest.setStyle("-fx-font-size: 28px; -fx-text-fill: #7f8c8d;");

        lblOptotipo = new Label("E");
        lblOptotipo.setStyle("-fx-font-size: 150px; -fx-font-weight: bold; -fx-text-fill: black;");

        rootPantalla.getChildren().addAll(lblIndicacionTest, lblOptotipo);
        stagePrincipal.setScene(new Scene(rootPantalla));

        new Thread(() -> {
            try {
                reconocedorVoz.setModo(ReconocedorOptotipos.ModoPrueba.SNELLEN);
                String[] letras = {"C", "D", "E", "F", "L", "O", "P", "T", "Z"};
                Random random = new Random();
                int aciertos = 0;
                int totalPruebas = 3;

                // Screen.getDpi() devuelve el DPI LÓGICO (en Windows casi siempre 96), no el físico:
                // se usa el PPI calibrado y se corrige el escalado de la pantalla.
                Screen pantallaPrincipal = Screen.getPrimary();
                double escala = pantallaPrincipal.getOutputScaleY() > 0 ? pantallaPrincipal.getOutputScaleY() : 1.0;
                double dpiDinamico = AppConfig.PPI_ESTANDAR_MONITOR / escala;

                for (int i = 0; i < totalPruebas; i++) {
                    String letraActual = letras[random.nextInt(letras.length)];
                    double vDecimal = (i == 0) ? 0.5 : (i == 1) ? 0.8 : 1.0;

                    double mar = TrigonometriaVisual.calcularMAR(vDecimal);
                    double dMm = this.distanciaMetros * 1000.0; // Usa la distancia configurada en el Slider
                    double hMm = TrigonometriaVisual.calcularAlturaMM(dMm, mar);

                    // Cálculo de píxeles usando el DPI detectado automáticamente
                    double tamanioPx = TrigonometriaVisual.calcularAlturaPixel(hMm, dpiDinamico);

                    Platform.runLater(() -> {
                        lblOptotipo.setText(letraActual);
                        lblOptotipo.setStyle(String.format("-fx-font-size: %.1fpx; -fx-font-family: 'Arial'; -fx-font-weight: bold; -fx-text-fill: black;", tamanioPx));
                    });

                    String respuesta = reconocedorVoz.escucharOptotipo();
                    if (respuesta.equals(letraActual)) {
                        aciertos++;
                    }
                    Thread.sleep(1000);
                }

                finalizarYGuardarResultados(aciertos, totalPruebas);

            } catch (Exception ex) {
                System.err.println("[ERROR EN EXAMEN]: " + ex.getMessage());
            }
        }).start();
    }

    /**
     * FASE 3: Guardado en la Base de Datos Local SQLite y Cierre.
     */
    private void finalizarYGuardarResultados(int aciertos, int total) {
        String resultadoFinal = aciertos + " / " + total + " Aciertos";
        String fechaHora = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String recomendacion = (aciertos >= 2) ? "Agudeza visual dentro de parámetros normales." : "Revisión oftalmológica recomendada.";

        GestorBaseDatos.registrarEvaluacion(
                curpPaciente,
                fechaHora,
                resultadoFinal,
                resultadoFinal,
                "No Aplica (Snellen)",
                0.05,
                recomendacion
        );

        Platform.runLater(() -> {
            if (detectorDistancia != null) {
                detectorDistancia.apagarCamara();
            }

            // MODIFICACIÓN 6: Pantalla de finalización con el estilo de la tarjeta
            StackPane fondoFin = new StackPane();
            fondoFin.setStyle("-fx-background-color: #F4F7F6;");

            VBox rootFin = new VBox(20);
            rootFin.setAlignment(Pos.CENTER);
            rootFin.setPadding(new Insets(40));
            rootFin.setMaxWidth(600);
            rootFin.setMaxHeight(VBox.USE_PREF_SIZE);
            rootFin.getStyleClass().add("contenedor-principal");

            Label lblFin = new Label("¡Prueba Finalizada!");
            lblFin.getStyleClass().add("etiqueta-titulo");
            lblFin.setStyle("-fx-text-fill: #27ae60;"); // Forzar color verde

            Label lblResumen = new Label("Resultado guardado en la base de datos local.\n\n" + recomendacion);
            lblResumen.setStyle("-fx-font-size: 20px; -fx-text-alignment: center; -fx-wrap-text: true;");

            Button btnCerrar = new Button("Finalizar y Salir");
            btnCerrar.getStyleClass().add("boton-primario");
            btnCerrar.setOnAction(ev -> stagePrincipal.close());

            rootFin.getChildren().addAll(lblFin, lblResumen, btnCerrar);
            fondoFin.getChildren().add(rootFin);

            Scene escenaFin = new Scene(fondoFin);
            try { escenaFin.getStylesheets().add(ControladorPantallaTest.class.getResource("/css/estilos.css").toExternalForm()); } catch (Exception e) {}

            stagePrincipal.setScene(escenaFin);
            ModoKiosco.aplicar(stagePrincipal);
        });
    }
}
