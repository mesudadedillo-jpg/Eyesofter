package com.eyesofter.ui;

import com.eyesofter.config.ConfiguracionUsuarios;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

/** Gestiona el acceso del personal antes de habilitar los módulos clínicos. */
public class ControladorAcceso implements ControladorDeVista {
    @FXML private TextField txtUsuario;
    @FXML private PasswordField txtContrasena;
    @FXML private Button btnAccesoInicial;
    private Stage stage;

    @FXML
    private void initialize() {
        boolean conservarAviso = ConfiguracionUsuarios.conservaCredencialesIniciales();
        btnAccesoInicial.setVisible(conservarAviso);
        btnAccesoInicial.setManaged(conservarAviso);
    }

    @Override
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    private void iniciarSesion() {
        String usuario = txtUsuario.getText().trim();
        String contrasena = txtContrasena.getText();
        if (usuario.isBlank() || contrasena.isBlank()) {
            mostrarError("Complete su nombre de usuario y contraseña.");
            return;
        }
        if (!ConfiguracionUsuarios.validarCredenciales(usuario, contrasena)) {
            mostrarError("Las credenciales no son válidas o la cuenta está desactivada.");
            return;
        }

        // Se guarda el usuario que acaba de iniciar sesión
        ConfiguracionUsuarios.setUsuarioSesionActual(usuario);

        Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
    }

    @FXML private void salirAplicacion() { ConfirmacionSalida.mostrar(stage); }

    @FXML
    private void mostrarAccesoInicial() {
        if (!ConfiguracionUsuarios.conservaCredencialesIniciales()) return;
        Alert aviso = new Alert(Alert.AlertType.INFORMATION, "Cuenta inicial: root\nContraseña inicial: root\n\nLas cuentas se guardan en el archivo local .env.", ButtonType.OK);
        aviso.setHeaderText("Acceso inicial de EyeSofter");
        aviso.showAndWait();
    }

    @FXML private void agregarUsuario() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Agregar usuario local");
        TextField nombre = new TextField(); nombre.setPromptText("Nombre de usuario");
        PasswordField contrasena = new PasswordField(); contrasena.setPromptText("Mínimo 8 caracteres");
        TextField pregunta = new TextField(); pregunta.setPromptText("Ej. ¿Cuál es tu amigo de la infancia?");
        TextField respuesta = new TextField(); respuesta.setPromptText("Respuesta de verificación");
        GridPane campos = new GridPane(); campos.setHgap(10); campos.setVgap(10);
        campos.addRow(0, new Label("Usuario"), nombre);
        campos.addRow(1, new Label("Contraseña"), contrasena);
        campos.addRow(2, new Label("Pregunta de verificación"), pregunta);
        campos.addRow(3, new Label("Respuesta"), respuesta);
        dialogo.getDialogPane().setContent(campos);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            boolean creado = ConfiguracionUsuarios.agregarUsuario(nombre.getText(), contrasena.getText(), pregunta.getText(), respuesta.getText());
            Alert aviso = new Alert(creado ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                    creado ? "Usuario local creado. Ya puede iniciar sesión." : "Completa usuario, contraseña, pregunta y respuesta; la contraseña debe tener al menos 8 caracteres.", ButtonType.OK);
            aviso.setHeaderText(creado ? "Cuenta registrada" : "No se pudo registrar la cuenta");
            aviso.showAndWait();
        });
    }

    @FXML private void recuperarContrasena() {
        TextInputDialog usuarioDialogo = new TextInputDialog();
        usuarioDialogo.setTitle("Recuperar contraseña"); usuarioDialogo.setHeaderText("Verificación de cuenta"); usuarioDialogo.setContentText("Nombre de usuario:");
        usuarioDialogo.showAndWait().ifPresent(usuario -> {
            String pregunta = ConfiguracionUsuarios.obtenerPregunta(usuario);
            if (pregunta.isBlank()) { mostrarError("Esta cuenta no tiene una pregunta de verificación configurada. Solicita ayuda a la persona administradora."); return; }
            TextInputDialog respuestaDialogo = new TextInputDialog(); respuestaDialogo.setTitle("Recuperar contraseña"); respuestaDialogo.setHeaderText(pregunta); respuestaDialogo.setContentText("Respuesta:");
            respuestaDialogo.showAndWait().ifPresent(respuesta -> {
                Dialog<ButtonType> nuevoDialogo = new Dialog<>(); nuevoDialogo.setTitle("Nueva contraseña");
                PasswordField nueva = new PasswordField(); nueva.setPromptText("Mínimo 8 caracteres"); PasswordField confirmar = new PasswordField(); confirmar.setPromptText("Confirmar contraseña");
                GridPane campos = new GridPane(); campos.setHgap(10); campos.setVgap(10); campos.addRow(0, new Label("Nueva contraseña"), nueva); campos.addRow(1, new Label("Confirmar"), confirmar);
                nuevoDialogo.getDialogPane().setContent(campos); nuevoDialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
                nuevoDialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
                    boolean correcta = nueva.getText().equals(confirmar.getText()) && ConfiguracionUsuarios.recuperarContrasena(usuario, respuesta, nueva.getText());
                    Alert aviso = new Alert(correcta ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, correcta ? "Contraseña actualizada. Ya puedes iniciar sesión." : "La respuesta no coincide o la contraseña no cumple el mínimo de 8 caracteres.", ButtonType.OK); aviso.setHeaderText(correcta ? "Recuperación completada" : "No se pudo recuperar"); aviso.showAndWait();
                });
            });
        });
    }

    private void mostrarError(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje, ButtonType.OK);
        alerta.setHeaderText("No fue posible iniciar sesión");
        alerta.showAndWait();
    }
}