package com.eyesofter.ui;

import com.eyesofter.camara.DetectorDistancia;
import com.eyesofter.clinica.ResultadoClinico;
import com.eyesofter.config.AppConfig;
import com.eyesofter.medicion_snellen.MotorSnellen;
import com.eyesofter.voz.ReconocedorOptotipos;
import com.eyesofter.voz.VozGuia;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tamizaje de agudeza visual (Snellen con escala LogMAR).
 *
 * <h3>Modo autónomo</h3>
 * Una instrucción hablada al inicio de la fase; después, por cada letra: se marca,
 * suena un tono y se escucha. Sin frases entre letras (fluidez). Si no se entiende,
 * se pide repetir como máximo 2 veces y luego se registra como no leída, por lo que
 * la prueba nunca se queda atascada.
 *
 * <h3>Modo asistido (operador)</h3>
 * Las tres letras aparecen como botones: un clic (o teclas 1-2-3) marca la letra como
 * no leída. Enter registra la fila, A = todas leídas, N = ninguna, V = captura por voz
 * (rellena las marcas para que el operador confirme), Retroceso = deshacer fila.
 *
 * <h3>Cámara</h3>
 * La distancia se evalúa en un hilo aparte cada 700 ms (la interfaz ya no se congela)
 * y se muestra en vivo en la barra superior.
 */
public class ControladorSnellen implements ControladorDeVista {
    @FXML private StackPane raiz;
    @FXML private Label lblOjo;
    @FXML private Label lblLinea;
    @FXML private Label lblDistancia;
    @FXML private Label lblInstruccion;
    @FXML private HBox contenedorOptotipos;
    @FXML private Label lblEstado;
    @FXML private Label lblOido;
    @FXML private Circle indicadorMicrofono;
    @FXML private ProgressBar barraProgreso;
    @FXML private TextField txtRespuestaManual;
    @FXML private Button btnRegistrar;
    @FXML private Button btnEscuchar;
    @FXML private Button btnDeshacer;
    @FXML private HBox panelMarcas;
    @FXML private VBox panelRespaldo;
    @FXML private Label lblAlerta;
    @FXML private HBox controlesAlerta;

    private static final int MAX_REINTENTOS_LETRA = 2;
    private static final int LECTURAS_PARA_PAUSA = 3;
    private static final int LECTURAS_PARA_REANUDAR = 2;
    /** La introducción completa solo se dice en la primera fase de la sesión. */
    private static boolean introduccionDada;

    private Stage stage;
    private MotorSnellen motor;
    private MotorSnellen.Fila filaActual;
    private DetectorDistancia detectorDistancia;
    private Timeline vigilanciaDistancia;
    private Timeline esperaReanudacion;
    private boolean capturando;
    private boolean pausada;
    private boolean finalizada;
    private boolean asistida;
    private ResultadoClinico.Ojo ojoActual = ResultadoClinico.Ojo.AMBOS;
    private long instantePresentacionNs;
    private final List<Label> etiquetasOptotipos = new ArrayList<>();
    private final List<Button> botonesMarca = new ArrayList<>();
    private boolean[] marcasLeidas = new boolean[MotorSnellen.LETRAS_POR_FILA];

    private int indiceAutonomo;
    private int aciertosAutonomos;
    private int reintentosLetra;
    private boolean avisoDistanciaDado;
    private double escalaAlerta = 1.0;
    private DetectorDistancia.CondicionPrueba condicionInestable;
    private int lecturasInestables;
    private int lecturasCorrectasEnPausa;
    private volatile ReconocedorOptotipos reconocedor;
    private int generacionCaptura;

    public static void mostrarVentana(Stage stage) {
        Navegador.mostrar(stage, "/fxml/Snellen.fxml", "EYESOFTER · Tamizaje de agudeza visual");
    }

    /** Reinicia la introducción hablada al empezar un nuevo paciente. */
    public static void reiniciarIntroduccion() { introduccionDada = false; }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        detectorDistancia = SesionEvaluacion.getDetectorDistancia();
        motor = new MotorSnellen(null, detectorDistancia);
        motor.configurarEntornoPrueba(SesionEvaluacion.getDistanciaMetros(), SesionEvaluacion.getPpi(), AppConfig.FUENTE_OPTOTIPO_DEFECTO);
        motor.setEscalaSalida(escalaPantalla());
        motor.reiniciarInteractivo();
        ojoActual = SesionEvaluacion.getFaseActual().ojo();
        if (ojoActual == ResultadoClinico.Ojo.AMBOS) {
            if (detectorDistancia != null) detectorDistancia.iniciarRegistroFiabilidad();
        }
        asistida = SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.ASISTIDA;
        panelRespaldo.setVisible(asistida);
        panelRespaldo.setManaged(asistida);
        lblOjo.setText(ojoActual.abreviatura());
        lblOjo.getStyleClass().add("insignia-" + ojoActual.name().toLowerCase(Locale.ROOT));
        lblInstruccion.setText(instruccionOjo());
        if (asistida) instalarAtajos();
        if (detectorDistancia == null) lblDistancia.setText("Cámara no activa");
        prepararVoz();
        mostrarSiguienteLinea();
        iniciarVigilanciaDistancia();
    }

    /** Escalado de Windows de la pantalla donde está la ventana (1.0, 1.25, 1.5…). */
    private double escalaPantalla() {
        Screen pantalla = Screen.getPrimary();
        if (stage != null && !Double.isNaN(stage.getX()) && stage.getWidth() > 0) {
            pantalla = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight())
                    .stream().findFirst().orElse(pantalla);
        }
        return pantalla.getOutputScaleY();
    }

    private String instruccionOjo() {
        return switch (ojoActual) {
            case DERECHO -> "👁  Ojo DERECHO · Tape el ojo izquierdo con la palma, sin presionar.";
            case IZQUIERDO -> "👁  Ojo IZQUIERDO · Tape el ojo derecho con la palma, sin presionar.";
            default -> "👁  Ambos ojos descubiertos.";
        };
    }

    private String instruccionOjoHablada() {
        return switch (ojoActual) {
            case DERECHO -> "Tápate el ojo izquierdo con la palma de la mano. Vamos a evaluar el ojo derecho.";
            case IZQUIERDO -> "Tápate el ojo derecho con la palma de la mano. Vamos a evaluar el ojo izquierdo.";
            default -> "Mantén ambos ojos descubiertos.";
        };
    }

    // ------------------------------------------------------------------
    // Voz
    // ------------------------------------------------------------------

    private void prepararVoz() {
        if (asistida) btnEscuchar.setDisable(true);
        else ponerEstado("Preparando el reconocimiento de voz…", EstadoMicrofono.ESPERA);
        Thread hilo = new Thread(() -> {
            try {
                ReconocedorOptotipos r = ReconocedorOptotipos.compartido();
                r.setModo(ReconocedorOptotipos.ModoPrueba.SNELLEN);
                Platform.runLater(() -> {
                    if (finalizada || !SesionEvaluacion.estaActiva()) return;
                    reconocedor = r;
                    if (asistida) btnEscuchar.setDisable(false);
                    else iniciarGuiaAutonoma();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!SesionEvaluacion.estaActiva()) return;
                    if (asistida) {
                        btnEscuchar.setDisable(true);
                        btnEscuchar.setText("🎤 Voz no disponible");
                    } else {
                        ponerEstado("No se pudo iniciar el micrófono. Cancele y revise el micrófono, o use el modo asistido.", EstadoMicrofono.ERROR);
                    }
                });
            }
        }, "inicio-voz-snellen");
        hilo.setDaemon(true);
        hilo.start();
    }

    private enum EstadoMicrofono { ESPERA, ESCUCHANDO, PROCESANDO, ERROR }

    private void ponerEstado(String texto, EstadoMicrofono estado) {
        lblEstado.setText(texto);
        indicadorMicrofono.getStyleClass().removeAll("mic-escuchando", "mic-procesando", "mic-error");
        switch (estado) {
            case ESCUCHANDO -> indicadorMicrofono.getStyleClass().add("mic-escuchando");
            case PROCESANDO -> indicadorMicrofono.getStyleClass().add("mic-procesando");
            case ERROR -> indicadorMicrofono.getStyleClass().add("mic-error");
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // Filas
    // ------------------------------------------------------------------

    private void mostrarSiguienteLinea() {
        if (!motor.haySiguienteFila()) {
            finalizarSnellen();
            return;
        }
        filaActual = motor.generarSiguienteFila();
        int numero = motor.getFilasEvaluadas() + 1;
        lblLinea.setText(String.format(Locale.US, "Fila %d · %s · LogMAR %.1f", numero,
                MotorSnellen.formatoSnellen(filaActual.vDecimal), filaActual.logMar));
        // Progreso según lo pequeño de la fila (la prueba termina antes si la persona deja de leer).
        barraProgreso.setProgress((filaActual.nivel + 1) / (double) MotorSnellen.getNumeroNiveles());
        prepararOptotipos();
        txtRespuestaManual.clear();
        lblOido.setText("");
        btnDeshacer.setDisable(motor.getFilasEvaluadas() == 0);
        instantePresentacionNs = System.nanoTime();
        if (asistida) {
            ponerEstado("Pida a la persona que lea las letras de izquierda a derecha.", EstadoMicrofono.ESPERA);
            raiz.requestFocus();
        } else if (reconocedor != null) {
            indiceAutonomo = 0;
            aciertosAutonomos = 0;
            presentarLetraAutonoma();
        }
    }

    private void prepararOptotipos() {
        contenedorOptotipos.getChildren().clear();
        etiquetasOptotipos.clear();
        panelMarcas.getChildren().clear();
        botonesMarca.clear();
        marcasLeidas = new boolean[filaActual.letras.length];
        java.util.Arrays.fill(marcasLeidas, true);
        for (int i = 0; i < filaActual.letras.length; i++) {
            String letra = filaActual.letras[i];
            Label etiqueta = new Label(letra);
            etiqueta.getStyleClass().add("optotipo-individual");
            etiqueta.setStyle(String.format(Locale.US, "-fx-font-family: '%s'; -fx-font-size: %.2fpx;", AppConfig.FUENTE_OPTOTIPO_DEFECTO, filaActual.tamanioPx));
            etiquetasOptotipos.add(etiqueta);
            contenedorOptotipos.getChildren().add(etiqueta);
            // Espaciado entre letras = ancho de una letra (criterio de apiñamiento ETDRS).
            contenedorOptotipos.setSpacing(Math.max(12, filaActual.tamanioPx * 0.72));

            if (asistida) {
                final int indice = i;
                etiqueta.setOnMouseClicked(e -> alternarMarca(indice));
                etiqueta.setCursor(javafx.scene.Cursor.HAND);
                Button boton = new Button();
                boton.getStyleClass().add("boton-marca");
                boton.setOnAction(e -> alternarMarca(indice));
                botonesMarca.add(boton);
                panelMarcas.getChildren().add(boton);
            }
        }
        actualizarMarcas();
    }

    // ------------------------------------------------------------------
    // Modo autónomo
    // ------------------------------------------------------------------

    /**
     * La primera fase de la sesión explica la dinámica una vez; en las siguientes la
     * pantalla de preparación ya dio la indicación del ojo, así que se empieza directo.
     */
    private void iniciarGuiaAutonoma() {
        if (filaActual == null || finalizada) return;
        Runnable empezar = () -> { indiceAutonomo = 0; aciertosAutonomos = 0; presentarLetraAutonoma(); };
        if (introduccionDada) { empezar.run(); return; }
        introduccionDada = true;
        ponerEstado("Escuche la instrucción…", EstadoMicrofono.ESPERA);
        VozGuia.decir("Vas a ver tres letras. Cuando escuches el tono, di en voz alta la letra marcada.",
                () -> VozGuia.decir("Si no la ves, di: no sé.", empezar));
    }

    private void presentarLetraAutonoma() {
        if (pausada || finalizada || reconocedor == null) return;
        if (indiceAutonomo >= filaActual.letras.length) { registrarLineaAutonoma(); return; }
        reintentosLetra = 0;
        resaltarLetraActiva();
        escucharLetraAutonoma();
    }

    private void resaltarLetraActiva() {
        for (int i = 0; i < etiquetasOptotipos.size(); i++) {
            Label l = etiquetasOptotipos.get(i);
            l.getStyleClass().remove("optotipo-activo");
            if (i == indiceAutonomo) l.getStyleClass().add("optotipo-activo");
        }
    }

    private void escucharLetraAutonoma() {
        if (pausada || finalizada) return;
        final int indiceEsperado = indiceAutonomo;
        final int generacion = ++generacionCaptura;
        ponerEstado("Di la letra marcada", EstadoMicrofono.ESPERA);
        VozGuia.tono(() -> {
            if (pausada || finalizada || generacion != generacionCaptura) return;
            capturando = true;
            ponerEstado("Escuchando…  di la letra marcada", EstadoMicrofono.ESCUCHANDO);
            ReconocedorOptotipos r = reconocedor;
            Thread hilo = new Thread(() -> {
                String respuesta;
                try { respuesta = r.escucharOptotipo(); }
                catch (Exception ex) { respuesta = ReconocedorOptotipos.ERROR; }
                String oido = r.getUltimoTextoOido();
                String resultado = respuesta;
                Platform.runLater(() -> {
                    if (generacion != generacionCaptura) return;
                    capturando = false;
                    if (SesionEvaluacion.estaActiva()) procesarLetraAutonoma(resultado, oido, indiceEsperado);
                });
            }, "captura-letra-autonoma");
            hilo.setDaemon(true);
            hilo.start();
        });
    }

    private void procesarLetraAutonoma(String respuesta, String oido, int indiceEsperado) {
        if (pausada || finalizada || indiceEsperado != indiceAutonomo) return;
        if (ReconocedorOptotipos.CANCELADO.equals(respuesta)) return;
        boolean noEntendido = respuesta == null || respuesta.isBlank()
                || ReconocedorOptotipos.TIMEOUT.equals(respuesta)
                || ReconocedorOptotipos.RUIDO.equals(respuesta)
                || ReconocedorOptotipos.ERROR.equals(respuesta);
        if (noEntendido) {
            reintentosLetra++;
            if (reintentosLetra > MAX_REINTENTOS_LETRA) {
                lblOido.setText("Sin respuesta");
                registrarLetraAutonoma(false);
                return;
            }
            String pedido = reintentosLetra == 1 ? "No te entendí. Repite la letra marcada." : "Si no la ves, di: no sé.";
            ponerEstado(pedido, EstadoMicrofono.ERROR);
            VozGuia.decir(pedido, this::escucharLetraAutonoma);
            return;
        }
        SesionEvaluacion.registrarEntradaVoz();
        if (ReconocedorOptotipos.RESPUESTA_NO_SE.equals(respuesta)) {
            lblOido.setText("Escuché: «no sé»");
            registrarLetraAutonoma(false);
            return;
        }
        // Puede llegar más de una letra ("C,D,O") si la persona leyó la fila seguida.
        String[] letras = respuesta.split(",");
        lblOido.setText("Escuché: " + (oido == null || oido.isBlank() ? String.join(" ", letras) : "«" + oido + "»"));
        for (String letra : letras) {
            if (indiceAutonomo >= filaActual.letras.length) break;
            boolean acierto = filaActual.letras[indiceAutonomo].equals(letra.trim());
            marcarLetraRegistrada(indiceAutonomo, acierto);
            if (acierto) aciertosAutonomos++;
            indiceAutonomo++;
        }
        continuarDespuesDeLetra();
    }

    private void registrarLetraAutonoma(boolean acierto) {
        marcarLetraRegistrada(indiceAutonomo, acierto);
        if (acierto) aciertosAutonomos++;
        indiceAutonomo++;
        continuarDespuesDeLetra();
    }

    /** Marca neutral: la persona evaluada no recibe pistas de acierto o error. */
    private void marcarLetraRegistrada(int indice, boolean acierto) {
        Label etiqueta = etiquetasOptotipos.get(indice);
        etiqueta.getStyleClass().remove("optotipo-activo");
        etiqueta.getStyleClass().add("optotipo-registrado");
    }

    private void continuarDespuesDeLetra() {
        PauseTransition pausa = new PauseTransition(Duration.millis(220));
        pausa.setOnFinished(e -> presentarLetraAutonoma());
        pausa.play();
    }

    private void registrarLineaAutonoma() {
        registrarFila(aciertosAutonomos);
    }

    // ------------------------------------------------------------------
    // Modo asistido
    // ------------------------------------------------------------------

    private void instalarAtajos() {
        raiz.setFocusTraversable(true);
        raiz.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (pausada || finalizada) return;
            boolean enTexto = e.getTarget() instanceof TextField;
            KeyCode c = e.getCode();
            if (!enTexto && (c == KeyCode.DIGIT1 || c == KeyCode.NUMPAD1)) { alternarMarca(0); e.consume(); }
            else if (!enTexto && (c == KeyCode.DIGIT2 || c == KeyCode.NUMPAD2)) { alternarMarca(1); e.consume(); }
            else if (!enTexto && (c == KeyCode.DIGIT3 || c == KeyCode.NUMPAD3)) { alternarMarca(2); e.consume(); }
            else if (!enTexto && c == KeyCode.A) { marcarTodasCorrectas(); e.consume(); }
            else if (!enTexto && c == KeyCode.N) { marcarNinguna(); e.consume(); }
            else if (!enTexto && c == KeyCode.V) { capturarPorVoz(); e.consume(); }
            else if (!enTexto && c == KeyCode.BACK_SPACE) { deshacerFila(); e.consume(); }
            else if (!enTexto && c == KeyCode.ENTER) { registrarRespuestaManual(); e.consume(); }
        });
    }

    private void alternarMarca(int indice) {
        if (!asistida || capturando || pausada || indice >= marcasLeidas.length) return;
        marcasLeidas[indice] = !marcasLeidas[indice];
        actualizarMarcas();
    }

    @FXML private void marcarTodasCorrectas() { if (asistida && !capturando) { java.util.Arrays.fill(marcasLeidas, true); actualizarMarcas(); } }
    @FXML private void marcarNinguna() { if (asistida && !capturando) { java.util.Arrays.fill(marcasLeidas, false); actualizarMarcas(); } }

    private void actualizarMarcas() {
        if (!asistida) return;
        // Las letras siguen en negro (el estímulo no cambia y la persona no ve pistas):
        // el estado solo se muestra en los botones del operador.
        for (int i = 0; i < etiquetasOptotipos.size(); i++) {
            if (i < botonesMarca.size()) {
                Button b = botonesMarca.get(i);
                b.setText((i + 1) + "  " + filaActual.letras[i] + "  " + (marcasLeidas[i] ? "✓" : "✗"));
                b.getStyleClass().removeAll("marca-leida", "marca-no-leida");
                b.getStyleClass().add(marcasLeidas[i] ? "marca-leida" : "marca-no-leida");
            }
        }
        int leidas = 0;
        for (boolean m : marcasLeidas) if (m) leidas++;
        btnRegistrar.setText("Registrar fila (" + leidas + "/" + marcasLeidas.length + ")  ⏎");
    }

    /**
     * Registra la fila. Si el operador escribió letras, se comparan posición por posición;
     * si no, se usan las marcas (✓/✗) de cada letra.
     */
    @FXML private void registrarRespuestaManual() {
        if (capturando || pausada || finalizada || filaActual == null) return;
        String texto = txtRespuestaManual.getText() == null ? "" : txtRespuestaManual.getText();
        String normalizada = texto.toUpperCase(Locale.ROOT).replaceAll("[^A-Z?]", "");
        SesionEvaluacion.registrarEntradaManual();
        if (!normalizada.isEmpty()) {
            if (normalizada.length() != filaActual.letras.length) {
                ponerEstado("Escriba exactamente " + filaActual.letras.length + " letras (use ? para una letra no leída) o borre el texto y use las marcas.", EstadoMicrofono.ERROR);
                return;
            }
            for (int i = 0; i < filaActual.letras.length; i++)
                marcasLeidas[i] = filaActual.letras[i].equals(String.valueOf(normalizada.charAt(i)));
            actualizarMarcas();
        }
        int aciertos = 0;
        for (boolean m : marcasLeidas) if (m) aciertos++;
        registrarFila(aciertos);
    }

    /** Captura por voz en modo asistido: rellena las marcas para que el operador confirme. */
    @FXML private void capturarPorVoz() {
        if (capturando || pausada || reconocedor == null || filaActual == null) return;
        capturando = true;
        SesionEvaluacion.registrarEntradaVoz();
        btnEscuchar.setDisable(true);
        btnRegistrar.setDisable(true);
        ponerEstado("Escuchando… la persona debe leer las " + filaActual.letras.length + " letras.", EstadoMicrofono.ESCUCHANDO);
        final int generacion = ++generacionCaptura;
        final int cantidad = filaActual.letras.length;
        ReconocedorOptotipos r = reconocedor;
        Thread hilo = new Thread(() -> {
            List<String> letras;
            try { letras = r.escucharSecuencia(cantidad); }
            catch (Exception ex) { letras = List.of(); }
            List<String> resultado = letras;
            Platform.runLater(() -> {
                if (generacion != generacionCaptura || !SesionEvaluacion.estaActiva()) return;
                restaurarControles();
                if (resultado.isEmpty()) {
                    ponerEstado("No se entendió la respuesta. Repita la captura o marque las letras manualmente.", EstadoMicrofono.ERROR);
                    return;
                }
                for (int i = 0; i < cantidad; i++) {
                    String dicha = i < resultado.size() ? resultado.get(i) : "";
                    marcasLeidas[i] = filaActual.letras[i].equals(dicha);
                }
                actualizarMarcas();
                lblOido.setText("Escuché: " + String.join(" ", resultado));
                ponerEstado("Revise las marcas y presione Enter para registrar.", EstadoMicrofono.ESPERA);
            });
        }, "captura-snellen");
        hilo.setDaemon(true);
        hilo.start();
    }

    @FXML private void deshacerFila() {
        if (!asistida || capturando || pausada) return;
        if (motor.deshacerUltimaFila()) {
            ponerEstado("Se deshizo la última fila registrada.", EstadoMicrofono.ESPERA);
            mostrarSiguienteLinea();
        }
    }

    // ------------------------------------------------------------------
    // Registro común
    // ------------------------------------------------------------------

    private void registrarFila(int aciertos) {
        motor.registrarResultadoFila(filaActual, aciertos);
        if (instantePresentacionNs > 0) {
            SesionEvaluacion.registrarTiempoRespuesta(Math.max(0, (System.nanoTime() - instantePresentacionNs) / 1_000_000));
        }
        restaurarControles();
        mostrarSiguienteLinea();
    }

    // ------------------------------------------------------------------
    // Vigilancia de la cámara (en segundo plano)
    // ------------------------------------------------------------------

    private void iniciarVigilanciaDistancia() {
        if (detectorDistancia == null) return;
        vigilanciaDistancia = new Timeline(new KeyFrame(Duration.millis(700),
                e -> detectorDistancia.evaluarEnSegundoPlano(this::procesarCondicion)));
        vigilanciaDistancia.setCycleCount(Timeline.INDEFINITE);
        vigilanciaDistancia.play();
    }

    private void procesarCondicion(DetectorDistancia.CondicionPrueba condicion) {
        if (finalizada) return;
        actualizarChipDistancia(condicion);
        if (pausada) {
            if (condicion == DetectorDistancia.CondicionPrueba.CORRECTA) {
                if (++lecturasCorrectasEnPausa >= LECTURAS_PARA_REANUDAR && motivoPausaAutomatica) reanudarInterno(true);
            } else {
                lecturasCorrectasEnPausa = 0;
            }
            return;
        }
        if (condicion == DetectorDistancia.CondicionPrueba.CORRECTA || condicion == DetectorDistancia.CondicionPrueba.RUIDO_VISUAL) {
            lecturasInestables = 0;
            condicionInestable = null;
        } else if (condicion == condicionInestable) {
            int requeridas = condicion == DetectorDistancia.CondicionPrueba.MULTIPLES_PERSONAS ? 2 : LECTURAS_PARA_PAUSA;
            if (++lecturasInestables >= requeridas) pausarPorCondicion(condicion, true);
        } else {
            condicionInestable = condicion;
            lecturasInestables = 1;
        }
    }

    private void actualizarChipDistancia(DetectorDistancia.CondicionPrueba condicion) {
        double cm = detectorDistancia.getUltimaDistanciaCm();
        lblDistancia.getStyleClass().removeAll("chip-ok", "chip-aviso");
        if (cm > 0) {
            boolean ok = condicion == DetectorDistancia.CondicionPrueba.CORRECTA || condicion == DetectorDistancia.CondicionPrueba.RUIDO_VISUAL;
            lblDistancia.setText(String.format(Locale.US, "📏 %.2f m %s", cm / 100.0, ok ? "✓" : "⚠"));
            lblDistancia.getStyleClass().add(ok ? "chip-ok" : "chip-aviso");
        } else {
            lblDistancia.setText("📏 sin rostro");
            lblDistancia.getStyleClass().add("chip-aviso");
        }
    }

    private boolean motivoPausaAutomatica;

    private void pausarPorCondicion(DetectorDistancia.CondicionPrueba condicion, boolean automatica) {
        if (pausada) return;
        pausada = true;
        motivoPausaAutomatica = automatica;
        lecturasCorrectasEnPausa = 0;
        generacionCaptura++;
        if (reconocedor != null && capturando) reconocedor.cancelarEscucha();
        VozGuia.cancelarPendientes();
        restaurarControles();
        mostrarAlerta(condicion, automatica);
    }

    private void mostrarAlerta(DetectorDistancia.CondicionPrueba condicion, boolean automatica) {
        String encabezado = automatica ? "Pausa automática · " : "Prueba en pausa · ";
        String mensaje;
        String voz;
        switch (condicion) {
            case CORRECTA -> { mensaje = "Pausa solicitada por el operador."; voz = null; }
            case DEMASIADO_CERCA -> { mensaje = "Está demasiado cerca del monitor. Aléjese hasta la distancia indicada."; voz = "Pausa. Estás demasiado cerca. Aléjate hasta la distancia indicada."; }
            case DEMASIADO_LEJOS -> { mensaje = "Está demasiado lejos del monitor. Acérquese hasta la distancia indicada."; voz = "Pausa. Estás demasiado lejos. Acércate hasta la distancia indicada."; }
            case MULTIPLES_PERSONAS -> { mensaje = "Se detectaron varias personas. Solo la persona evaluada debe permanecer frente a la cámara."; voz = "Pausa. Se detectaron varias personas. Solo debe permanecer la persona evaluada."; }
            case RUIDO_VISUAL -> { mensaje = "Hay demasiado movimiento. Permanezca quieto."; voz = "Pausa. Hay demasiado movimiento. Quédate quieto."; }
            case SIN_ROSTRO -> { mensaje = "No se detecta a la persona evaluada. Colóquese frente a la cámara."; voz = "Pausa. Colócate frente a la cámara."; }
            default -> { mensaje = "La cámara no puede verificar las condiciones. Revise la cámara."; voz = null; }
        }
        lblAlerta.setText(encabezado + mensaje + (automatica ? "\nLa prueba continuará sola cuando se corrija." : "") + "\nLa fila actual se conserva.");
        lblAlerta.setVisible(true); lblAlerta.setManaged(true);
        controlesAlerta.setVisible(true); controlesAlerta.setManaged(true);
        ponerEstado("Evaluación en pausa.", EstadoMicrofono.ESPERA);
        if (!avisoDistanciaDado && voz != null) { avisoDistanciaDado = true; VozGuia.decir(voz, null); }
    }

    private void ocultarAlerta() {
        lblAlerta.setVisible(false); lblAlerta.setManaged(false);
        controlesAlerta.setVisible(false); controlesAlerta.setManaged(false);
    }

    @FXML private void pausarPrueba() {
        if (!pausada) pausarPorCondicion(DetectorDistancia.CondicionPrueba.CORRECTA, false);
    }

    @FXML private void reanudarPrueba() {
        if (!pausada) return;
        DetectorDistancia.CondicionPrueba condicion = detectorDistancia == null
                ? DetectorDistancia.CondicionPrueba.CORRECTA : detectorDistancia.getUltimaCondicion();
        boolean condicionMala = condicion != DetectorDistancia.CondicionPrueba.CORRECTA
                && condicion != DetectorDistancia.CondicionPrueba.RUIDO_VISUAL;
        if (motivoPausaAutomatica && condicionMala) {
            mostrarAlerta(condicion, true);
            return;
        }
        reanudarInterno(false);
    }

    private void reanudarInterno(boolean automatica) {
        pausada = false;
        avisoDistanciaDado = false;
        lecturasInestables = 0;
        condicionInestable = null;
        ocultarAlerta();
        ponerEstado("Prueba reanudada donde se pausó.", EstadoMicrofono.ESPERA);
        if (!asistida) {
            Runnable seguir = this::presentarLetraAutonoma;
            if (automatica) VozGuia.decir("Muy bien. Continuamos.", seguir);
            else seguir.run();
        } else {
            raiz.requestFocus();
        }
    }

    @FXML private void reducirAlerta() { ajustarTamanoAlerta(-0.15); }
    @FXML private void ampliarAlerta() { ajustarTamanoAlerta(0.15); }
    private void ajustarTamanoAlerta(double cambio) {
        escalaAlerta = Math.max(0.75, Math.min(1.75, escalaAlerta + cambio));
        lblAlerta.setStyle(String.format(Locale.US, "-fx-font-size: %.0fpx;", 18 * escalaAlerta));
    }

    // ------------------------------------------------------------------
    // Cierre
    // ------------------------------------------------------------------

    private void finalizarSnellen() {
        if (finalizada) return;
        finalizada = true;
        detenerVigilancia();
        SesionEvaluacion.registrarSnellen(ojoActual, ResultadoClinico.ResultadoOjo.desdeMotor(motor));
        Runnable siguiente = () -> {
            if (ojoActual == ResultadoClinico.Ojo.AMBOS) {
                ControladorFaseEvaluacion.mostrar(stage, SesionEvaluacion.FaseEvaluacion.AGUDEZA_DERECHO);
            } else if (ojoActual == ResultadoClinico.Ojo.DERECHO) {
                ControladorFaseEvaluacion.mostrar(stage, SesionEvaluacion.FaseEvaluacion.AGUDEZA_IZQUIERDO);
            } else {
                registrarMetricasFiabilidad();
                SesionEvaluacion.liberarDetectorDistancia();
                ControladorFaseEvaluacion.mostrar(stage, SesionEvaluacion.FaseEvaluacion.COLOR_AMBOS);
            }
        };
        if (!asistida) {
            ponerEstado("Terminamos esta parte.", EstadoMicrofono.ESPERA);
            VozGuia.decir("Terminamos esta parte. Muy bien.", siguiente);
        } else {
            siguiente.run();
        }
    }

    private void detenerVigilancia() {
        if (vigilanciaDistancia != null) vigilanciaDistancia.stop();
        if (esperaReanudacion != null) esperaReanudacion.stop();
    }

    private void registrarMetricasFiabilidad() {
        long promedio = SesionEvaluacion.getPromedioRespuestaMs();
        if (detectorDistancia == null) {
            SesionEvaluacion.registrarFiabilidad(Double.NaN, 0, promedio);
            SesionEvaluacion.registrarDistanciaPupilar(Double.NaN, false);
            return;
        }
        SesionEvaluacion.registrarFiabilidad(detectorDistancia.getCumplimientoDistanciaPorcentaje(),
                detectorDistancia.getMuestrasFiabilidad(), promedio);
        SesionEvaluacion.registrarCondicionesDistancia(detectorDistancia.getDistanciaObjetivo() / 100.0,
                AppConfig.TOLERANCIA_DISTANCIA_CM);
        SesionEvaluacion.registrarDistanciaPupilar(detectorDistancia.getDistanciaPupilarPromedioMm(),
                detectorDistancia.isDistanciaPupilarCalibrada());
    }

    private void restaurarControles() {
        capturando = false;
        if (btnEscuchar != null) btnEscuchar.setDisable(reconocedor == null);
        if (btnRegistrar != null) btnRegistrar.setDisable(false);
    }

    @FXML private void cancelarPrueba() {
        // Se pausa mientras el operador decide, para que el micrófono no siga registrando.
        if (!pausada) pausarPorCondicion(DetectorDistancia.CondicionPrueba.CORRECTA, false);
        CancelacionPrueba.solicitar(stage, () -> {
            finalizada = true;
            detenerVigilancia();
            generacionCaptura++;
            if (reconocedor != null) reconocedor.cancelarEscucha();
        });
    }
}
