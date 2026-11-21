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
        long bloqueo = ConfiguracionUsuarios.minutosBloqueo(usuario);
        if (bloqueo > 0) {
            mostrarError("Cuenta bloqueada por " + ConfiguracionUsuarios.INTENTOS_MAXIMOS
                    + " intentos fallidos. Intente de nuevo en " + bloqueo + " minuto(s).");
            return;
        }
        boolean inicial = ConfiguracionUsuarios.conservaCredencialesIniciales();
        if (!ConfiguracionUsuarios.validarCredenciales(usuario, contrasena)) {
            long ahora = ConfiguracionUsuarios.minutosBloqueo(usuario);
            mostrarError(ahora > 0
                    ? "Demasiados intentos fallidos. La cuenta quedó bloqueada " + ahora + " minutos."
                    : "Usuario o contraseña incorrectos.");
            txtContrasena.clear();
            return;
        }
        // Primer uso: la contraseña de fábrica (root) debe cambiarse antes de entrar.
        if (inicial && !exigirCambioContrasenaInicial(usuario, contrasena)) return;

        // Se guarda el usuario que acaba de iniciar sesión
        ConfiguracionUsuarios.setUsuarioSesionActual(usuario);

        Navegador.mostrar(stage, "/fxml/PanelAdministrativo.fxml", "EYESOFTER · Panel de control");
    }

    private boolean exigirCambioContrasenaInicial(String usuario, String actual) {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Cambie la contraseña inicial");
        dialogo.setHeaderText("Por seguridad, la contraseña de fábrica debe cambiarse en el primer acceso.");
        PasswordField nueva = new PasswordField(); nueva.setPromptText("Mínimo 8 caracteres, letras y números");
        PasswordField confirmar = new PasswordField(); confirmar.setPromptText("Repita la contraseña");
        Label regla = new Label("Use al menos 8 caracteres con letras y números.");
        regla.setWrapText(true);
        GridPane campos = new GridPane(); campos.setHgap(10); campos.setVgap(10);
        campos.addRow(0, new Label("Nueva contraseña"), nueva);
        campos.addRow(1, new Label("Confirmar"), confirmar);
        campos.add(regla, 0, 2, 2, 1);
        dialogo.getDialogPane().setContent(campos);
        dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        while (true) {
            var r = dialogo.showAndWait();
            if (r.isEmpty() || r.get() != ButtonType.OK) return false;
            if (!nueva.getText().equals(confirmar.getText())) { regla.setText("Las contraseñas no coinciden."); continue; }
            if (ConfiguracionUsuarios.cambiarContrasena(usuario, actual, nueva.getText())) return true;
            regla.setText("La contraseña debe tener al menos 8 caracteres, con letras y números, y no puede ser «root».");
        }
    }

    @FXML private void salirAplicacion() { ConfirmacionSalida.mostrar(stage); }

    @FXML
    private void mostrarAccesoInicial() {
        if (!ConfiguracionUsuarios.conservaCredencialesIniciales()) return;
        Alert aviso = new Alert(Alert.AlertType.INFORMATION, "Cuenta inicial: root\nContraseña inicial: root\n\nAl entrar por primera vez se le pedirá cambiarla. Las contraseñas se guardan cifradas (hash) en el equipo.", ButtonType.OK);
        aviso.setHeaderText("Acceso inicial de EyeSofter");
        aviso.showAndWait();
    }

    /**
     * Crear cuentas exige autenticarse con una cuenta existente: antes cualquiera podía
     * crearse una cuenta desde esta pantalla y entrar a los expedientes.
     */
    @FXML private void agregarUsuario() {
        Dialog<ButtonType> autorizacion = new Dialog<>();
        autorizacion.setTitle("Autorizar nueva cuenta");
        autorizacion.setHeaderText("Una persona con cuenta debe autorizar el alta.");
        TextField usuarioAut = new TextField(); usuarioAut.setPromptText("Usuario existente");
        PasswordField claveAut = new PasswordField(); claveAut.setPromptText("Contraseña");
        GridPane datosAut = new GridPane(); datosAut.setHgap(10); datosAut.setVgap(10);
        datosAut.addRow(0, new Label("Usuario"), usuarioAut);
        datosAut.addRow(1, new Label("Contraseña"), claveAut);
        autorizacion.getDialogPane().setContent(datosAut);
        autorizacion.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        var respuestaAut = autorizacion.showAndWait();
        if (respuestaAut.isEmpty() || respuestaAut.get() != ButtonType.OK) return;
        if (ConfiguracionUsuarios.conservaCredencialesIniciales()
                || !ConfiguracionUsuarios.validarCredenciales(usuarioAut.getText(), claveAut.getText())) {
            mostrarError("No se autorizó: credenciales incorrectas, o primero debe cambiarse la contraseña inicial iniciando sesión.");
            return;
        }
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
                    creado ? "Usuario local creado. Ya puede iniciar sesión." : "Completa usuario, contraseña, pregunta y respuesta. La contraseña debe tener al menos 8 caracteres con letras y números, y el usuario no debe existir.", ButtonType.OK);
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