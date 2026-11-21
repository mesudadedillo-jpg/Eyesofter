package com.eyesofter.ui;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/** Alerta clínica reutilizable cuando se interrumpe una condición de la prueba. */
public class ControladorAlertaInterrupcion implements ControladorDeVista {
    public enum Motivo { DISTANCIA, RUIDO }
    private static Motivo proximoMotivo = Motivo.DISTANCIA;
    @FXML private Label lblTitulo;
    @FXML private Label lblMensaje;
    @FXML private Label lblIndicacion;
    private Stage stage;
    private Motivo motivo = Motivo.DISTANCIA;

    public static void mostrarVentana(Stage stage, Motivo motivo) {
        proximoMotivo = motivo;
        Navegador.mostrar(stage, "/fxml/AlertaInterrupcion.fxml", "EYESOFTER · Pausa técnica");
    }

    @Override public void setStage(Stage stage) {
        this.stage = stage;
        mostrarMotivo(proximoMotivo);
    }

    public void mostrarMotivo(Motivo motivo) {
        this.motivo = motivo;
        boolean distancia = motivo == Motivo.DISTANCIA;
        lblTitulo.setText(distancia ? "Pausa por posición fuera de rango" : "Pausa por interferencia acústica");
        lblMensaje.setText(distancia
                ? "La distancia estimada se encuentra fuera del rango configurado para esta evaluación."
                : "El nivel de ruido ambiental puede comprometer la interpretación de la respuesta verbal.");
        lblIndicacion.setText(distancia
                ? "Recupere la posición indicada y reanude la prueba cuando el estado sea estable."
                : "Reduzca el ruido ambiental y confirme que la respuesta pueda escucharse con claridad.");
    }

    @FXML private void reintentar() {
        if (motivo == Motivo.DISTANCIA) ControladorCalibracionPosicion.mostrarVentana(stage);
        else ControladorSnellen.mostrarVentana(stage);
    }

    @FXML private void finalizarSesion() {
        Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
    }
}
