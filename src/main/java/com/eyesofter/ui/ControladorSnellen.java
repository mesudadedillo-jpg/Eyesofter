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
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;


/** Tamizaje Snellen dimensionado para la pantalla y distancia de cada sesión. */
public class ControladorSnellen implements ControladorDeVista {
    @FXML private Label lblLinea;
    @FXML private HBox contenedorOptotipos;
    @FXML private Label lblEstado;
    @FXML private ProgressBar barraProgreso;
    @FXML private TextField txtRespuestaManual;
    @FXML private Button btnRegistrar;
    @FXML private Button btnEscuchar;
    @FXML private VBox panelRespaldo;
    @FXML private Label lblAlerta;
    @FXML private HBox controlesAlerta;

    private Stage stage;
    private MotorSnellen motor;
    private MotorSnellen.Fila filaActual;
    private DetectorDistancia detectorDistancia;
    private Timeline vigilanciaDistancia;
    private Timeline esperaReanudacion;
    private boolean capturando;
    private boolean pausada;
    private ResultadoClinico.Ojo ojoActual = ResultadoClinico.Ojo.AMBOS;
    private long instantePresentacionNs;
    private final List<Label> etiquetasOptotipos = new ArrayList<>();
    private int indiceAutonomo;
    private int aciertosAutonomos;
    private boolean avisoDistanciaDado;
    private double escalaAlerta = 1.0;
    private DetectorDistancia.CondicionPrueba motivoPausa;
    private DetectorDistancia.CondicionPrueba condicionInestable;
    private int lecturasInestables;
    private static final int LECTURAS_PARA_PAUSA = 3;
    private volatile ReconocedorOptotipos capturaActual;
    private volatile ReconocedorOptotipos reconocedorAutonomo;
    private boolean motorVozAutonomoListo;

    public static void mostrarVentana(Stage stage) {
        Navegador.mostrar(stage, "/fxml/Snellen.fxml", "EYESOFTER · Tamizaje de agudeza visual");
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        detectorDistancia = SesionEvaluacion.getDetectorDistancia();
        motor = new MotorSnellen(null, detectorDistancia);
        motor.configurarEntornoPrueba(SesionEvaluacion.getDistanciaMetros(), SesionEvaluacion.getPpi(), AppConfig.FUENTE_OPTOTIPO_DEFECTO);
        motor.reiniciarInteractivo();
        ojoActual = SesionEvaluacion.getFaseActual().ojo();
        if (ojoActual == ResultadoClinico.Ojo.AMBOS && detectorDistancia != null) detectorDistancia.iniciarRegistroFiabilidad();
        boolean asistida = SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.ASISTIDA;
        panelRespaldo.setVisible(asistida);
        panelRespaldo.setManaged(asistida);
        if (!asistida) iniciarMotorVozAutonomo();
        mostrarSiguienteLinea();
        iniciarVigilanciaDistancia();
    }

    private void mostrarSiguienteLinea() {
        if (!motor.haySiguienteFila()) {
            finalizarSnellen();
            return;
        }
        filaActual = motor.generarSiguienteFila();
        int numeroLinea = motor.getLetrasEvaluadasTotales() / 3 + 1;
        lblLinea.setText(ojoActual.abreviatura() + " · Línea " + numeroLinea + " de " + MotorSnellen.getNumeroNiveles()
                + " · " + MotorSnellen.formatoSnellen(filaActual.vDecimal));
        prepararOptotipos();
        barraProgreso.setProgress((numeroLinea - 1) / (double) MotorSnellen.getNumeroNiveles());
        lblEstado.setText("Solo una persona debe permanecer frente a la cámara. " + (ojoActual == ResultadoClinico.Ojo.DERECHO
                ? "Cubra el ojo izquierdo. " : ojoActual == ResultadoClinico.Ojo.IZQUIERDO
                ? "Cubra el ojo derecho. " : "Mantenga ambos ojos descubiertos. ")
                + (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA
                ? "Escuchando… responda las letras de izquierda a derecha." : "Indique las letras de izquierda a derecha."));
        txtRespuestaManual.clear();
        instantePresentacionNs = System.nanoTime();
        if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA) {
            if (motorVozAutonomoListo) iniciarGuiaAutonoma();
            else lblEstado.setText("Preparando el reconocimiento de voz…");
        }
    }

    private void prepararOptotipos() {
        contenedorOptotipos.getChildren().clear(); etiquetasOptotipos.clear();
        for (String letra : filaActual.letras) {
            Label etiqueta = new Label(letra);
            etiqueta.getStyleClass().add("optotipo-individual");
            etiqueta.setStyle(String.format("-fx-font-family: '%s'; -fx-font-size: %.1fpx;", AppConfig.FUENTE_OPTOTIPO_DEFECTO, filaActual.tamanioPx));
            etiquetasOptotipos.add(etiqueta); contenedorOptotipos.getChildren().add(etiqueta);
        }
    }

    private void iniciarGuiaAutonoma() {
        indiceAutonomo = 0; aciertosAutonomos = 0;
        VozGuia.decir("Mira cada letra subrayada y dilo en voz alta.", this::presentarLetraAutonoma);
    }

    /** Mantiene el modelo y el micrófono abiertos durante toda la prueba para
     * que la primera palabra no se pierda al inicializar Vosk en cada letra. */
    private void iniciarMotorVozAutonomo() {
        Thread hilo = new Thread(() -> {
            try {
                ReconocedorOptotipos motorVoz = new ReconocedorOptotipos(AppConfig.RUTA_MODELO_VOSK);
                motorVoz.setModo(ReconocedorOptotipos.ModoPrueba.SNELLEN);
                Platform.runLater(() -> {
                    if (!SesionEvaluacion.estaActiva()) { motorVoz.liberar(); return; }
                    reconocedorAutonomo = motorVoz;
                    motorVozAutonomoListo = true;
                    iniciarGuiaAutonoma();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (SesionEvaluacion.estaActiva()) lblEstado.setText("No se pudo iniciar el reconocimiento de voz. Cancele la prueba y revise el micrófono.");
                });
            }
        }, "inicio-voz-autonoma");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void presentarLetraAutonoma() {
        if (pausada) return;
        if (indiceAutonomo >= filaActual.letras.length) { registrarLineaAutonoma(); return; }
        for (int i = 0; i < etiquetasOptotipos.size(); i++) etiquetasOptotipos.get(i).getStyleClass().removeAll("optotipo-activo", "optotipo-acierto", "optotipo-error");
        etiquetasOptotipos.get(indiceAutonomo).getStyleClass().add("optotipo-activo");
        lblEstado.setText("Mira la letra subrayada y dilo en voz alta.");
        VozGuia.decir("Di la letra subrayada.", () -> capturarLetraAutonoma(indiceAutonomo));
    }

    private void capturarLetraAutonoma(int indiceEsperado) {
        if (capturando || pausada || indiceEsperado != indiceAutonomo) return;
        capturando = true; lblEstado.setText("Escuchando la letra subrayada…");
        Thread hilo = new Thread(() -> {
            try {
                ReconocedorOptotipos reconocedor = reconocedorAutonomo;
                boolean temporal = reconocedor == null;
                if (temporal) reconocedor = new ReconocedorOptotipos(AppConfig.RUTA_MODELO_VOSK);
                capturaActual = reconocedor;
                reconocedor.setModo(ReconocedorOptotipos.ModoPrueba.SNELLEN);
                String respuesta = reconocedor.escucharOptotipo();
                if (temporal) reconocedor.liberar();
                Platform.runLater(() -> { if (SesionEvaluacion.estaActiva()) procesarLetraAutonoma(respuesta, indiceEsperado); });
            } catch (Exception ex) { Platform.runLater(() -> { if (SesionEvaluacion.estaActiva()) procesarLetraAutonoma("", indiceEsperado); }); }
            finally { capturaActual = null; }
        }, "captura-letra-autonoma");
        hilo.setDaemon(true); hilo.start();
    }

    private void procesarLetraAutonoma(String respuesta, int indiceEsperado) {
        capturando = false;
        if (pausada || indiceEsperado != indiceAutonomo) return;
        String letra = respuesta == null ? "" : respuesta.toUpperCase().replaceAll("[^A-Z]", "");
        if (letra.isBlank() || letra.equals("TIMEOUT") || letra.equals("RUIDO")) {
            lblEstado.setText("No te entendí. Repite la letra subrayada.");
            VozGuia.decir("No te entendí. Repite la letra subrayada.", () -> capturarLetraAutonoma(indiceEsperado));
            return;
        }
        Label etiqueta = etiquetasOptotipos.get(indiceEsperado);
        boolean acierto = filaActual.letras[indiceEsperado].equals(String.valueOf(letra.charAt(0)));
        etiqueta.getStyleClass().remove("optotipo-activo"); etiqueta.getStyleClass().add(acierto ? "optotipo-acierto" : "optotipo-error");
        if (acierto) { aciertosAutonomos++; lblEstado.setText("✓ Muy bien."); VozGuia.decir("Muy bien.", this::avanzarLetraAutonoma); }
        else { lblEstado.setText("✗ Esa no es. Continuemos."); VozGuia.decir("Continuemos con la siguiente letra.", this::avanzarLetraAutonoma); }
    }

    private void avanzarLetraAutonoma() { indiceAutonomo++; presentarLetraAutonoma(); }
    private void registrarLineaAutonoma() {
        motor.registrarResultadoFila(filaActual, aciertosAutonomos);
        if (instantePresentacionNs > 0) SesionEvaluacion.registrarTiempoRespuesta(Math.max(0, (System.nanoTime() - instantePresentacionNs) / 1_000_000));
        mostrarSiguienteLinea();
    }

    @FXML private void registrarRespuestaManual() {
        if (!capturando && !pausada) {
            SesionEvaluacion.registrarEntradaManual();
            procesarRespuesta(txtRespuestaManual.getText());
        }
    }

    @FXML private void capturarPorVoz() {
        if (capturando || pausada) return;
        capturando = true;
        SesionEvaluacion.registrarEntradaVoz();
        btnEscuchar.setDisable(true);
        btnRegistrar.setDisable(true);
        lblEstado.setText("Escuchando… responda las tres letras en orden.");
        Thread hilo = new Thread(() -> {
            StringBuilder respuesta = new StringBuilder();
            try {
                ReconocedorOptotipos reconocedor = new ReconocedorOptotipos(AppConfig.RUTA_MODELO_VOSK);
                capturaActual = reconocedor;
                reconocedor.setModo(ReconocedorOptotipos.ModoPrueba.SNELLEN);
                for (int indice = 0; indice < 3; indice++) respuesta.append(reconocedor.escucharOptotipo());
                reconocedor.liberar();
                Platform.runLater(() -> { if (SesionEvaluacion.estaActiva()) procesarRespuesta(respuesta.toString()); });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblEstado.setText("No fue posible escuchar la respuesta. Intente de nuevo.");
                    restaurarControles();
                });
            } finally { capturaActual = null; }
        }, "captura-snellen");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void procesarRespuesta(String respuesta) {
        if (pausada) return;
        String normalizada = respuesta == null ? "" : respuesta.toUpperCase().replaceAll("[^A-Z]", "");
        if (normalizada.length() != 3) {
            lblEstado.setText("No se detectaron tres letras claras. Se repite la misma línea.");
            restaurarControles();
            if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA) reintentarAutomaticamente();
            return;
        }
        int aciertosFila = 0;
        for (int indice = 0; indice < 3; indice++) {
            if (filaActual.letras[indice].equals(String.valueOf(normalizada.charAt(indice)))) aciertosFila++;
        }
        motor.registrarResultadoFila(filaActual, aciertosFila);
        if (instantePresentacionNs > 0) {
            SesionEvaluacion.registrarTiempoRespuesta(Math.max(0, (System.nanoTime() - instantePresentacionNs) / 1_000_000));
        }
        restaurarControles();
        mostrarSiguienteLinea();
    }

    private void reintentarAutomaticamente() {
        PauseTransition espera = new PauseTransition(Duration.millis(900));
        espera.setOnFinished(evento -> capturarPorVoz());
        espera.play();
    }

    private void iniciarVigilanciaDistancia() {
        if (detectorDistancia == null) return;
        vigilanciaDistancia = new Timeline(new KeyFrame(Duration.millis(800), evento -> {
            if (!capturando && !pausada) {
                DetectorDistancia.CondicionPrueba condicion = detectorDistancia.evaluarCondicionPrueba();
                if (condicion == DetectorDistancia.CondicionPrueba.CORRECTA
                        || condicion == DetectorDistancia.CondicionPrueba.RUIDO_VISUAL) {
                    lecturasInestables = 0; condicionInestable = null;
                } else if (condicion == DetectorDistancia.CondicionPrueba.MULTIPLES_PERSONAS) {
                    pausarPorCondicion(condicion, true);
                } else if (condicion == condicionInestable) {
                    if (++lecturasInestables >= LECTURAS_PARA_PAUSA) pausarPorCondicion(condicion, true);
                } else {
                    condicionInestable = condicion; lecturasInestables = 1;
                }
            }
        }));
        vigilanciaDistancia.setCycleCount(Timeline.INDEFINITE);
        vigilanciaDistancia.play();
    }

    private void pausarPorCondicion(DetectorDistancia.CondicionPrueba condicion, boolean automatica) {
        if (pausada) return;
        pausada = true;
        motivoPausa = condicion;
        restaurarControles();
        mostrarAlerta(condicion, automatica);
        esperaReanudacion = new Timeline(new KeyFrame(Duration.seconds(1), evento -> {
            if (detectorDistancia != null && detectorDistancia.evaluarCondicionPrueba() == DetectorDistancia.CondicionPrueba.CORRECTA) {
                esperaReanudacion.stop(); pausada = false; avisoDistanciaDado = false;
                ocultarAlerta();
                lblEstado.setText("Condiciones estables recuperadas. Continúe exactamente donde se pausó.");
                VozGuia.decir("Condiciones correctas. Continuamos donde se pausó.", null);
                if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA) presentarLetraAutonoma();
            }
        }));
        esperaReanudacion.setCycleCount(Timeline.INDEFINITE); esperaReanudacion.play();
    }

    private void mostrarAlerta(DetectorDistancia.CondicionPrueba condicion, boolean automatica) {
        String encabezado = automatica ? "Pausa automática: " : "Prueba pausada: ";
        String mensaje;
        String voz;
        switch (condicion) {
            case CORRECTA -> { mensaje = "La pausa fue solicitada por el operador."; voz = "La prueba está pausada."; }
            case DEMASIADO_CERCA -> { mensaje = "Está demasiado cerca del monitor. Aléjese hasta la distancia indicada."; voz = "Pausa. Estás demasiado cerca. Aléjate hasta la distancia indicada."; }
            case DEMASIADO_LEJOS -> { mensaje = "Está demasiado lejos del monitor. Acérquese hasta la distancia indicada."; voz = "Pausa. Estás demasiado lejos. Acércate hasta la distancia indicada."; }
            case MULTIPLES_PERSONAS -> { mensaje = "Se detectaron varias personas. Solo la persona evaluada debe permanecer frente a la cámara."; voz = "Pausa. Se detectaron varias personas. Solo debe permanecer la persona evaluada."; }
            case RUIDO_VISUAL -> { mensaje = "Se detectó demasiado movimiento o ruido visual. Permanezca quieto y estabilice la imagen."; voz = "Pausa. Hay demasiado movimiento visual. Quédate quieto para estabilizar la imagen."; }
            case SIN_ROSTRO -> { mensaje = "No se detecta a la persona evaluada. Colóquese frente a la cámara."; voz = "Pausa. Colócate frente a la cámara."; }
            default -> { mensaje = "La cámara no puede verificar las condiciones de la prueba. Revise la cámara e intente de nuevo."; voz = "Pausa. No puedo verificar la cámara."; }
        }
        lblAlerta.setText(encabezado + mensaje + " La misma línea permanecerá guardada.");
        lblAlerta.setVisible(true); lblAlerta.setManaged(true);
        controlesAlerta.setVisible(true); controlesAlerta.setManaged(true);
        lblEstado.setText("La evaluación está pausada hasta que la condición se corrija.");
        if (!avisoDistanciaDado) { avisoDistanciaDado = true; VozGuia.decir(voz, null); }
    }

    private void ocultarAlerta() {
        lblAlerta.setVisible(false); lblAlerta.setManaged(false);
        controlesAlerta.setVisible(false); controlesAlerta.setManaged(false);
    }

    /** Pausa elegida por el operador: no descarta fila, letra ni respuestas previas. */
    @FXML private void pausarPrueba() {
        if (!pausada) pausarPorCondicion(DetectorDistancia.CondicionPrueba.CORRECTA, false);
    }

    @FXML private void reanudarPrueba() {
        if (!pausada) return;
        DetectorDistancia.CondicionPrueba condicion = detectorDistancia == null
                ? DetectorDistancia.CondicionPrueba.CORRECTA : detectorDistancia.evaluarCondicionPrueba();
        if (condicion != DetectorDistancia.CondicionPrueba.CORRECTA) {
            mostrarAlerta(condicion, true);
            return;
        }
        if (esperaReanudacion != null) esperaReanudacion.stop();
        pausada = false; avisoDistanciaDado = false; ocultarAlerta();
        lblEstado.setText("Prueba reanudada exactamente donde se pausó.");
        if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA) presentarLetraAutonoma();
    }

    @FXML private void reducirAlerta() { ajustarTamanoAlerta(-0.15); }
    @FXML private void ampliarAlerta() { ajustarTamanoAlerta(0.15); }
    private void ajustarTamanoAlerta(double cambio) {
        escalaAlerta = Math.max(0.75, Math.min(1.75, escalaAlerta + cambio));
        lblAlerta.setStyle(String.format("-fx-font-size: %.0fpx;", 16 * escalaAlerta));
    }

    private void finalizarSnellen() {
        if (reconocedorAutonomo != null) { reconocedorAutonomo.liberar(); reconocedorAutonomo = null; }
        SesionEvaluacion.registrarSnellen(ojoActual, ResultadoClinico.ResultadoOjo.desdeMotor(motor));
        if (vigilanciaDistancia != null) vigilanciaDistancia.stop();
        if (esperaReanudacion != null) esperaReanudacion.stop();
        if (ojoActual == ResultadoClinico.Ojo.AMBOS) {
            ControladorFaseEvaluacion.mostrar(stage, SesionEvaluacion.FaseEvaluacion.AGUDEZA_DERECHO);
        } else if (ojoActual == ResultadoClinico.Ojo.DERECHO) {
            ControladorFaseEvaluacion.mostrar(stage, SesionEvaluacion.FaseEvaluacion.AGUDEZA_IZQUIERDO);
        } else {
            registrarMetricasFiabilidad();
            SesionEvaluacion.liberarDetectorDistancia();
            ControladorFaseEvaluacion.mostrar(stage, SesionEvaluacion.FaseEvaluacion.COLOR_AMBOS);
        }
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
        btnEscuchar.setDisable(false);
        btnRegistrar.setDisable(false);
    }

    @FXML private void cancelarPrueba() {
        CancelacionPrueba.solicitar(stage, () -> {
            if (capturaActual != null) capturaActual.liberar();
            if (reconocedorAutonomo != null) reconocedorAutonomo.liberar();
        });
    }
}
