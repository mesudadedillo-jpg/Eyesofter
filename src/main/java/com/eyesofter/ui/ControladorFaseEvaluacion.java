package com.eyesofter.ui;

import com.eyesofter.voz.VozGuia;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/** Instrucciones visibles y habladas antes de presentar estímulos clínicos. */
public final class ControladorFaseEvaluacion implements ControladorDeVista {
    @FXML private Label lblPrueba;
    @FXML private Label lblOjo;
    @FXML private Label lblInstruccion;
    private Stage stage;

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
        VozGuia.decir(instruccion, null);
    }

    @FXML private void comenzar() {
        if (SesionEvaluacion.getFaseActual().esAgudeza()) ControladorSnellen.mostrarVentana(stage);
        else ControladorIshihara.mostrarVentana(stage);
    }

    @FXML private void cancelarPrueba() { CancelacionPrueba.solicitar(stage); }

    private String instruccion(SesionEvaluacion.FaseEvaluacion fase) {
        return switch (fase) {
            case AGUDEZA_AMBOS -> "Evaluaremos la visión de lejos con ambos ojos abiertos. Di lo que ves en la pantalla.";
            case AGUDEZA_DERECHO -> "Ahora evaluaremos el ojo derecho. Cubre el ojo izquierdo sin presionarlo y di lo que ves.";
            case AGUDEZA_IZQUIERDO -> "Ahora evaluaremos el ojo izquierdo. Cubre el ojo derecho sin presionarlo y di lo que ves.";
            case COLOR_AMBOS -> "Evaluaremos la visión de colores con ambos ojos abiertos. Di el número que ves; si no ves uno, di nada.";
        };
    }
}
