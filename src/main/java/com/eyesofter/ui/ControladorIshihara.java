package com.eyesofter.ui;

import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.voz.ReconocedorOptotipos;
import com.eyesofter.voz.VozGuia;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Tamizaje cromático de apoyo; no sustituye la valoración diagnóstica especializada. */
public class ControladorIshihara implements ControladorDeVista {

    // Clase interna para mapear cada lámina clínicamente
    private static class Lamina {
        String rutaImagen;
        String nombreDescriptivo;
        String respuestaNormal;
        String respuestaDaltonica;

        Lamina(String rutaImagen, String nombreDescriptivo, String respuestaNormal, String respuestaDaltonica) {
            this.rutaImagen = rutaImagen;
            this.nombreDescriptivo = nombreDescriptivo;
            this.respuestaNormal = respuestaNormal;
            this.respuestaDaltonica = respuestaDaltonica;
        }
    }

    @FXML private Label lblLamina;
    @FXML private Label lblEstado;
    @FXML private Label lblMarcador;
    @FXML private Label lblSinImagen;
    @FXML private ImageView vistaLamina;
    @FXML private ProgressBar barraProgreso;
    @FXML private TextField txtRespuesta;
    @FXML private Button btnRegistrar;
    @FXML private Button btnEscuchar;
    @FXML private Label lblRetroalimentacion;
    @FXML private VBox panelRespaldo;
    @FXML private FlowPane tecladoNumerico;
    @FXML private Circle indicadorMicrofono;
    @FXML private Label lblOido;

    private static final int MAX_REINTENTOS = 2;
    private int reintentos;
    private int respuestasRegistradas;
    private int sinRespuesta;
    private boolean instruccionDada;
    private boolean finalizada;
    private int generacionCaptura;

    private Stage stage;
    private List<Lamina> laminasPrueba;
    private int indice;
    private int aciertosNormales;
    private int erroresDaltonismo;
    private boolean capturando;

    // El motor de voz se carga UNA sola vez para evitar lag
    private ReconocedorOptotipos reconocedor;

    public static void mostrarVentana(Stage stage) {
        Navegador.mostrar(stage, "/fxml/Ishihara.fxml", "EYESOFTER · Tamizaje cromático");
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
        inicializarLaminas();
        boolean asistida = SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.ASISTIDA;
        panelRespaldo.setVisible(asistida); panelRespaldo.setManaged(asistida);
        lblRetroalimentacion.setVisible(!asistida); lblRetroalimentacion.setManaged(!asistida);
        if (asistida) construirTeclado();
        iniciarMotorVoz();
        mostrarLamina();
    }

    private void inicializarLaminas() {
        laminasPrueba = new ArrayList<>();

        // Nombres descriptivos y rutas actuales del banco de láminas clínico.
        Lamina laminaControl = new Lamina("/ishihara/Ishihara_Control_12_VisionNormal.jpg", "Control: 12 visible", "12", "12");
        laminasPrueba.add(new Lamina("/ishihara/Ishihara_6_Normal_5_DeficienciaRojoVerde.jpg", "Transformación 6/5 (rojo-verde)", "6", "5"));
        laminasPrueba.add(new Lamina("/ishihara/Ishihara_42_Normal_2_DeuteranopiaProtanopia.jpg", "Transformación 42/2 (protan/deutan)", "42", "2"));
        laminasPrueba.add(new Lamina("/ishihara/Ishihara_74_Normal_21_DeficienciaRojoVerde.jpg", "Transformación 74/21 (rojo-verde)", "74", "21"));
        laminasPrueba.add(new Lamina("/ishihara/Ishihara_Oculta_Normal_SinNumero_Deficiencia_5.jpg", "Lámina oculta: sin número/5", "NADA", "5"));

        // Barajamos para evitar memorización
        Collections.shuffle(laminasPrueba);
        // Colocamos el 12 siempre al inicio
        laminasPrueba.add(0, laminaControl);
    }

    private void iniciarMotorVoz() {
        btnEscuchar.setDisable(true);
        lblEstado.setText("Cargando motor de voz, espere...");

        Thread hiloVoz = new Thread(() -> {
            try {
                ReconocedorOptotipos r = ReconocedorOptotipos.compartido();
                r.setModo(ReconocedorOptotipos.ModoPrueba.ISHIHARA);
                Platform.runLater(() -> {
                    if (finalizada) return;
                    reconocedor = r;
                    btnEscuchar.setDisable(false);
                    lblEstado.setText("Indique el número observado; si no identifica uno, registre “NADA”.");
                    if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA) guiarLaminaAutonoma();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    lblEstado.setText("Error en motor de voz. Utilice el teclado.");
                    btnEscuchar.setDisable(true); // Se apaga si falla Vosk
                });
            }
        });
        hiloVoz.setDaemon(true);
        hiloVoz.start();
    }

    private void mostrarLamina() {
        if (indice >= laminasPrueba.size()) {
            finalizar();
            return;
        }

        // El nombre clínico se conserva para trazabilidad técnica, pero no se expone
        // durante la toma para no sugerir la respuesta al paciente ni al auxiliar.
        lblLamina.setText("Lámina " + (indice + 1) + " de " + laminasPrueba.size());
        // Marcador neutral: no revela si las respuestas son correctas.
        lblMarcador.setText(respuestasRegistradas + " respuestas registradas");
        reintentos = 0;
        lblOido.setText("");
        barraProgreso.setProgress(indice / (double) laminasPrueba.size());
        lblEstado.setText("Indique el número observado; si no identifica uno, registre “NADA”.");
        lblRetroalimentacion.setText("");
        txtRespuesta.clear();
        cargarImagen();
        if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA && reconocedor != null) guiarLaminaAutonoma();
    }

    private void guiarLaminaAutonoma() {
        if (capturando || finalizada || indice >= laminasPrueba.size()) return;
        lblEstado.setText("Observa la lámina y di el número que ves.");
        if (!instruccionDada) {
            instruccionDada = true;
            VozGuia.decir("Observa la lámina. Di el número que ves. Si no ves ningún número, di nada.", this::escucharConTono);
        } else {
            escucharConTono();
        }
    }

    private void escucharConTono() {
        if (finalizada) return;
        VozGuia.tono(this::capturarPorVoz);
    }

    private void construirTeclado() {
        tecladoNumerico.getChildren().clear();
        String[] teclas = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "⌫", "NADA"};
        for (String t : teclas) {
            Button b = new Button(t);
            b.getStyleClass().add(t.equals("NADA") ? "tecla-nada" : "tecla-numero");
            b.setOnAction(e -> {
                String actual = txtRespuesta.getText() == null ? "" : txtRespuesta.getText();
                if (t.equals("⌫")) txtRespuesta.setText(actual.isEmpty() ? "" : actual.substring(0, actual.length() - 1));
                else if (t.equals("NADA")) txtRespuesta.setText("NADA");
                else txtRespuesta.setText(actual.equals("NADA") ? t : (actual + t).length() > 2 ? actual : actual + t);
                txtRespuesta.positionCaret(txtRespuesta.getText().length());
            });
            tecladoNumerico.getChildren().add(b);
        }
    }

    private void ponerMicrofono(boolean escuchando) {
        indicadorMicrofono.getStyleClass().remove("mic-escuchando");
        if (escuchando) indicadorMicrofono.getStyleClass().add("mic-escuchando");
    }

    private void cargarImagen() {
        String ruta = laminasPrueba.get(indice).rutaImagen;
        try (InputStream flujo = getClass().getResourceAsStream(ruta)) {
            if (flujo == null) {
                vistaLamina.setImage(null);
                lblSinImagen.setText("Lámina cromática no instalada\n(" + ruta + ")");
                lblSinImagen.setVisible(true);
                return;
            }
            vistaLamina.setImage(new Image(flujo));
            lblSinImagen.setVisible(false);
        } catch (Exception ex) {
            vistaLamina.setImage(null);
            lblSinImagen.setText("No fue posible cargar la lámina cromática.");
            lblSinImagen.setVisible(true);
        }
    }

    @FXML
    private void registrarRespuesta() {
        if (!capturando) {
            SesionEvaluacion.registrarEntradaManual();
            procesarRespuesta(txtRespuesta.getText());
        }
    }

    @FXML
    private void capturarPorVoz() {
        if (capturando || reconocedor == null) return;

        capturando = true;
        SesionEvaluacion.registrarEntradaVoz();
        btnEscuchar.setDisable(true);
        btnRegistrar.setDisable(true);
        lblEstado.setText("Escuchando…  di el número en voz alta.");
        ponerMicrofono(true);
        final int generacion = ++generacionCaptura;
        ReconocedorOptotipos r = reconocedor;

        Thread hilo = new Thread(() -> {
            try {
                String respuesta = r.escucharOptotipo();
                String oido = r.getUltimoTextoOido();
                Platform.runLater(() -> {
                    if (generacion != generacionCaptura || finalizada || !SesionEvaluacion.estaActiva()) return;
                    ponerMicrofono(false);
                    if (oido != null && !oido.isBlank()) lblOido.setText("Escuché: «" + oido + "»");
                    if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.ASISTIDA
                            && !ReconocedorOptotipos.TIMEOUT.equals(respuesta) && !ReconocedorOptotipos.RUIDO.equals(respuesta)
                            && !ReconocedorOptotipos.ERROR.equals(respuesta) && !ReconocedorOptotipos.CANCELADO.equals(respuesta)) {
                        // En modo asistido la voz solo rellena el campo: el operador confirma.
                        txtRespuesta.setText(respuesta);
                        lblEstado.setText("Revise la respuesta y presione Registrar (Enter).");
                        restaurarControles();
                        return;
                    }
                    procesarRespuesta(respuesta);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    if (!SesionEvaluacion.estaActiva()) return;
                    lblEstado.setText("Error al escuchar. Utilice el registro manual.");
                    restaurarControles();
                });
            }
        }, "captura-ishihara");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void procesarRespuesta(String respuesta) {
        if (finalizada) return;
        String valor = respuesta == null ? "" : respuesta.trim().toUpperCase();
        if (ReconocedorOptotipos.CANCELADO.equals(valor)) { restaurarControles(); return; }
        boolean autonoma = SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA;
        if (valor.isEmpty() || valor.equals(ReconocedorOptotipos.TIMEOUT) || valor.equals(ReconocedorOptotipos.RUIDO)
                || valor.equals(ReconocedorOptotipos.ERROR)) {
            restaurarControles();
            if (!autonoma) {
                lblEstado.setText("No se entendió. Escriba el número o use el teclado.");
                return;
            }
            reintentos++;
            if (reintentos > MAX_REINTENTOS) {
                // Sin respuesta válida tras 2 intentos: se registra y se sigue (la prueba nunca se atasca).
                sinRespuesta++;
                avanzar("Sin respuesta", false);
                return;
            }
            lblEstado.setText("No te entendí. Repite el número.");
            VozGuia.decir(reintentos == 1 ? "No te entendí. Repite el número." : "Si no ves ningún número, di nada.", this::escucharConTono);
            return;
        }
        if (valor.equals(ReconocedorOptotipos.RESPUESTA_NO_SE)) valor = ReconocedorOptotipos.RESPUESTA_NADA;
        if (!valor.equals(ReconocedorOptotipos.RESPUESTA_NADA) && !valor.matches("\\d{1,2}")) {
            lblEstado.setText("Respuesta no válida: escriba un número de 1 o 2 cifras, o NADA.");
            restaurarControles();
            return;
        }

        Lamina laminaActual = laminasPrueba.get(indice);
        boolean acierto = valor.equals(laminaActual.respuestaNormal.toUpperCase());
        if (acierto) {
            aciertosNormales++;
        } else if (valor.equals(laminaActual.respuestaDaltonica.toUpperCase())) {
            erroresDaltonismo++;
        }
        avanzar(valor.equals("NADA") ? "«nada»" : valor, true);
    }

    private void avanzar(String respuestaMostrada, boolean valida) {
        respuestasRegistradas++;
        restaurarControles();
        if (SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA) {
            // Retroalimentación neutral: no indica si la respuesta fue correcta.
            lblRetroalimentacion.setText(valida ? "✓ Respuesta registrada: " + respuestaMostrada : "Sin respuesta · continuamos");
            lblRetroalimentacion.getStyleClass().removeAll("retroalimentacion-acierto", "retroalimentacion-error");
            lblRetroalimentacion.getStyleClass().add("retroalimentacion-acierto");
            javafx.animation.PauseTransition p = new javafx.animation.PauseTransition(javafx.util.Duration.millis(600));
            p.setOnFinished(e -> { indice++; mostrarLamina(); });
            p.play();
        } else {
            indice++;
            mostrarLamina();
        }
    }

    private void restaurarControles() {
        capturando = false;
        btnEscuchar.setDisable(false);
        btnRegistrar.setDisable(false);
    }

    private void finalizar() {
        if (finalizada) return;
        finalizada = true;
        generacionCaptura++;
        if (reconocedor != null) reconocedor.cancelarEscucha();

        // La respuesta coincidente con el patrón de deficiencia se conserva
        // separada de los aciertos: es una señal de tamizaje, no un diagnóstico.
        SesionEvaluacion.registrarIshihara(aciertosNormales, laminasPrueba.size(), erroresDaltonismo);

        boolean guardada = GestorBaseDatos.registrarEvaluacionClinica(
                SesionEvaluacion.getCurpPaciente(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                SesionEvaluacion.getResultadoClinico());

        if (!guardada) {
            String pendientes = SesionEvaluacion.getResultadoClinico().getFasesPendientes();
            lblEstado.setText(pendientes.isBlank()
                    ? "No fue posible guardar la evaluación. Revise la base de datos antes de salir."
                    : "La evaluación está incompleta: faltan " + pendientes + ". No se guardó un registro parcial.");
            return;
        }

        GestorBaseDatos.obtenerCitasPaciente(SesionEvaluacion.getCurpPaciente()).stream()
                .filter(c -> "EN_EVALUACION".equalsIgnoreCase(c.estado))
                .findFirst().ifPresent(c -> GestorBaseDatos.actualizarEstadoCita(c.idCita, "COMPLETADA"));

        ControladorResultados.mostrarVentana(stage);
    }

    @FXML private void cancelarPrueba() {
        CancelacionPrueba.solicitar(stage, () -> {
            finalizada = true;
            generacionCaptura++;
            if (reconocedor != null) reconocedor.cancelarEscucha();
            VozGuia.cancelarPendientes();
        });
    }
}
