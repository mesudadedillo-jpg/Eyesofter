package com.eyesofter.ui;

import com.eyesofter.voz.VozGuia;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;

/** Instrucciones visibles y habladas antes de presentar estímulos clínicos. */
public final class ControladorFaseEvaluacion implements ControladorDeVista {
    @FXML private Label lblPrueba;
    @FXML private Label lblOjo;
    @FXML private Label lblInstruccion;
    @FXML private Label lblCuentaRegresiva;
    private Stage stage;
    private Timeline cuentaRegresiva;
    private boolean iniciada;
    private static final int SEGUNDOS_INICIO_AUTOMATICO = 5;

    public static void mostrar(Stage stage, SesionEvaluacion.FaseEvaluacion fase) {
        SesionEvaluacion.prepararFase(fase);
        Navegador.mostrar(stage, "/fxml/FaseEvaluacion.fxml", "EYESOFTER - Preparación de evaluación");
    }

    @Override public void setStage(Stage stage) {
        this.stage = stage;
        SesionEvaluacion.FaseEvaluacion fase = SesionEvaluacion.getFaseActual();
        lblPrueba.setText(fase.prueba());
        lblOjo.setText(fase.ojo().etiqueta());
        String instruccion = instruccion(fase);
        lblInstruccion.setText(instruccion);
        boolean autonoma = SesionEvaluacion.getModoEvaluacion() == SesionEvaluacion.ModoEvaluacion.AUTONOMA;
        // En modo autónomo la persona evaluada no tiene que tocar nada: al terminar
        // la instrucción hablada empieza una cuenta regresiva visible.
        VozGuia.decir(instruccion, autonoma ? this::iniciarCuentaRegresiva : null);
    }

    private void iniciarCuentaRegresiva() {
        if (iniciada || lblCuentaRegresiva == null) return;
        final int[] restante = {SEGUNDOS_INICIO_AUTOMATICO};
        lblCuentaRegresiva.setText("Comenzamos en " + restante[0] + "…");
        cuentaRegresiva = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            restante[0]--;
            if (restante[0] <= 0) { cuentaRegresiva.stop(); comenzar(); }
            else lblCuentaRegresiva.setText("Comenzamos en " + restante[0] + "…");
        }));
        cuentaRegresiva.setCycleCount(SEGUNDOS_INICIO_AUTOMATICO);
        cuentaRegresiva.play();
    }

    @FXML private void comenzar() {
        if (iniciada) return;
        iniciada = true;
        if (cuentaRegresiva != null) cuentaRegresiva.stop();
        if (SesionEvaluacion.getFaseActual().esAgudeza()) ControladorSnellen.mostrarVentana(stage);
        else ControladorIshihara.mostrarVentana(stage);
    }

    @FXML private void cancelarPrueba() {
        if (cuentaRegresiva != null) cuentaRegresiva.pause();
        CancelacionPrueba.solicitar(stage);
        if (cuentaRegresiva != null && !iniciada && SesionEvaluacion.estaActiva()) cuentaRegresiva.play();
    }

    private String instruccion(SesionEvaluacion.FaseEvaluacion fase) {
        return switch (fase) {
            case AGUDEZA_AMBOS -> "Evaluaremos la visión de lejos con ambos ojos abiertos. Di lo que ves en la pantalla.";
            case AGUDEZA_DERECHO -> "Ahora evaluaremos el ojo derecho. Cubre el ojo izquierdo sin presionarlo y di lo que ves.";
            case AGUDEZA_IZQUIERDO -> "Ahora evaluaremos el ojo izquierdo. Cubre el ojo derecho sin presionarlo y di lo que ves.";
            case COLOR_AMBOS -> "Evaluaremos la visión de colores con ambos ojos abiertos. Di el número que ves; si no ves uno, di nada.";
        };
    }
}
