package com.eyesofter.ui;

import com.eyesofter.config.AppConfig;
import com.eyesofter.config.ConfiguracionUsuarios;
import com.eyesofter.voz.DispositivosAudio;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;

import java.util.List;

/** Ajustes de la cuenta y los periféricos persistidos en el equipo local. */
public class ControladorConfiguracionAdministrativo implements VistaAdministrativa {
    @FXML private Label lblUsuario, lblEntorno, lblGuardado, lblTemaResumen;
    @FXML private ComboBox<String> cmbMicrofono, cmbDistancia, cmbCamara;
    @FXML private ToggleButton tgTema;
    private ControladorPanelAdministrativo panel;
    private String usuarioActivo;

    @FXML private javafx.scene.layout.VBox contenedorTarjetas;

    @FXML private void initialize() {
        // Conexión con el portal: va después de la tarjeta de cuenta.
        if (contenedorTarjetas != null) contenedorTarjetas.getChildren().add(Math.min(2, contenedorTarjetas.getChildren().size()), TarjetaConexionSuite.crear());
        usuarioActivo = ConfiguracionUsuarios.obtenerUsuarioPrincipal();
        lblUsuario.setText(usuarioActivo);
        // Windows puede tardar (o bloquearse) al consultar un controlador de audio.
        // La pantalla debe abrirse y seguir siendo utilizable aunque ese controlador falle.
        cmbMicrofono.setItems(FXCollections.observableArrayList("Micrófono predeterminado"));
        cmbMicrofono.setValue("Micrófono predeterminado");
        cmbDistancia.setItems(FXCollections.observableArrayList("0.8 metros", "1.0 metros", "1.5 metros", "2.0 metros"));
        cmbDistancia.setValue(String.format("%.1f metros", AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO));
        if (!cmbDistancia.getItems().contains(cmbDistancia.getValue())) cmbDistancia.setValue("1.0 metros");
        cmbCamara.setItems(FXCollections.observableArrayList("Cámara web integrada", "Cámara externa (predeterminada)"));
        cmbCamara.setValue("Cámara web integrada");
        tgTema.setSelected(TemaAplicacion.esOscuro());
        actualizarTemaTexto();
        actualizarResumen();
        cargarMicrofonosEnSegundoPlano();
    }

    private void cargarMicrofonosEnSegundoPlano() {
        Task<List<String>> tarea = new Task<>() {
            @Override protected List<String> call() { return DispositivosAudio.listarMicrofonos(); }
        };
        tarea.setOnSucceeded(evento -> {
            List<String> microfonos = tarea.getValue();
            if (microfonos == null || microfonos.isEmpty()) return;
            cmbMicrofono.setItems(FXCollections.observableArrayList(microfonos));
            String preferido = AppConfig.DISPOSITIVO_AUDIO_PREFERIDO;
            cmbMicrofono.setValue(preferido == null || preferido.isBlank() || !microfonos.contains(preferido)
                    ? microfonos.get(0) : preferido);
        });
        tarea.setOnFailed(evento -> lblGuardado.setText("No se pudo detectar el micrófono; se usará el predeterminado."));
        Thread hilo = new Thread(tarea, "deteccion-microfonos-configuracion");
        hilo.setDaemon(true);
        hilo.start();
    }

    @Override public void setPanelAdministrativo(ControladorPanelAdministrativo panel) { this.panel = panel; }

    @FXML private void guardarOperativa() {
        String microfono = cmbMicrofono.getValue();
        DispositivosAudio.seleccionarMicrofono("Micrófono predeterminado".equals(microfono) ? "" : microfono);
        try {
            AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO = Double.parseDouble(cmbDistancia.getValue().replace(" metros", ""));
            AppConfig.guardar();
            actualizarResumen();
            lblGuardado.setText("Ajustes guardados en este equipo.");
        } catch (Exception e) {
            lblGuardado.setText("No se pudieron guardar los ajustes.");
        }
    }

    @FXML private void abrirEntorno() { panel.abrirConfiguracionTecnica(); }

    @FXML private void cambiarUsuario() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Cambiar usuario");
        TextField nuevo = new TextField(); nuevo.setPromptText("Ingresa el nuevo usuario");
        PasswordField contrasena = new PasswordField(); contrasena.setPromptText("Contraseña actual");
        GridPane contenido = new GridPane(); contenido.setHgap(10); contenido.setVgap(10);
        contenido.addRow(0, new Label("Usuario actual"), new Label(usuarioActivo));
        contenido.addRow(1, new Label("Nuevo usuario"), nuevo);
        contenido.addRow(2, new Label("Contraseña actual"), contrasena);
        dialogo.getDialogPane().setContent(contenido); dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            boolean correcto = ConfiguracionUsuarios.cambiarNombreUsuario(usuarioActivo, nuevo.getText(), contrasena.getText());

            if (correcto) {
                usuarioActivo = nuevo.getText().trim().toLowerCase(java.util.Locale.ROOT);
                lblUsuario.setText(usuarioActivo);
                if (panel != null) panel.actualizarInformacionUsuario(usuarioActivo);
            }

            aviso(correcto ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                    correcto ? "Usuario actualizado" : "Validación requerida",
                    correcto ? "Vuelve a iniciar sesión con el nuevo usuario." : "Verifica la contraseña y que el usuario no esté ocupado.");
        });
    }

    @FXML private void cambiarContrasena() {
        Dialog<ButtonType> dialogo = new Dialog<>();
        dialogo.setTitle("Cambiar contraseña");
        PasswordField actual = new PasswordField(); actual.setPromptText("Contraseña actual");
        PasswordField nueva = new PasswordField(); nueva.setPromptText("Nueva contraseña (mínimo 8 caracteres)");
        PasswordField confirmar = new PasswordField(); confirmar.setPromptText("Confirma la nueva contraseña");
        GridPane contenido = new GridPane(); contenido.setHgap(10); contenido.setVgap(10);
        contenido.addRow(0, new Label("Actual"), actual); contenido.addRow(1, new Label("Nueva"), nueva); contenido.addRow(2, new Label("Confirmar"), confirmar);
        dialogo.getDialogPane().setContent(contenido); dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            boolean correcta = nueva.getText().equals(confirmar.getText()) && ConfiguracionUsuarios.cambiarContrasena(usuarioActivo, actual.getText(), nueva.getText());
            aviso(correcta ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR,
                    correcta ? "Configuración guardada" : "Validación requerida",
                    correcta ? "Contraseña actualizada." : "La contraseña actual no coincide o la nueva tiene menos de 8 caracteres.");
        });
    }

    @FXML private void crearCuenta() {
        Dialog<ButtonType> dialogo = new Dialog<>(); dialogo.setTitle("Crear nueva cuenta local");
        TextField usuario = new TextField(); usuario.setPromptText("Nombre de usuario"); PasswordField clave = new PasswordField(); clave.setPromptText("Mínimo 8 caracteres"); TextField pregunta = new TextField(); pregunta.setPromptText("Pregunta de verificación"); TextField respuesta = new TextField(); respuesta.setPromptText("Respuesta");
        GridPane campos = new GridPane(); campos.setHgap(10); campos.setVgap(10); campos.addRow(0, new Label("Usuario"), usuario); campos.addRow(1, new Label("Contraseña"), clave); campos.addRow(2, new Label("Pregunta"), pregunta); campos.addRow(3, new Label("Respuesta"), respuesta); dialogo.getDialogPane().setContent(campos); dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> { boolean creada = ConfiguracionUsuarios.agregarUsuario(usuario.getText(), clave.getText(), pregunta.getText(), respuesta.getText()); aviso(creada ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, creada ? "Cuenta creada" : "No se pudo crear", creada ? "La nueva cuenta puede iniciar sesión." : "Completa todos los campos y usa una contraseña de al menos 8 caracteres."); });
    }

    @FXML private void verContrasena() {
        Dialog<ButtonType> dialogo = new Dialog<>(); dialogo.setTitle("Ver contraseña actual");
        PasswordField actual = new PasswordField(); actual.setPromptText("Confirma tu contraseña actual");
        dialogo.getDialogPane().setContent(actual); dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            if (ConfiguracionUsuarios.validarCredenciales(usuarioActivo, actual.getText())) {
                aviso(Alert.AlertType.INFORMATION, "Contraseña actual", "Por seguridad, la contraseña no se muestra. Puedes cambiarla desde esta pantalla.");
            } else aviso(Alert.AlertType.ERROR, "Verificación fallida", "La contraseña actual no coincide.");
        });
    }

    @FXML private void configurarVerificacion() {
        Dialog<ButtonType> dialogo = new Dialog<>(); dialogo.setTitle("Pregunta de verificación");
        PasswordField actual = new PasswordField(); actual.setPromptText("Contraseña actual"); TextField pregunta = new TextField(); pregunta.setPromptText("¿Cuál es tu amigo de la infancia?"); TextField respuesta = new TextField(); respuesta.setPromptText("Respuesta");
        GridPane contenido = new GridPane(); contenido.setHgap(10); contenido.setVgap(10); contenido.addRow(0, new Label("Contraseña actual"), actual); contenido.addRow(1, new Label("Pregunta"), pregunta); contenido.addRow(2, new Label("Respuesta"), respuesta); dialogo.getDialogPane().setContent(contenido); dialogo.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialogo.showAndWait().filter(r -> r == ButtonType.OK).ifPresent(r -> {
            boolean guardada = ConfiguracionUsuarios.configurarVerificacion(usuarioActivo, actual.getText(), pregunta.getText(), respuesta.getText());
            aviso(guardada ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR, "Pregunta de verificación", guardada ? "Pregunta guardada." : "Verifica la contraseña y completa ambos campos.");
        });
    }

    @FXML private void alternarTema() {
        TemaAplicacion.aplicar(tgTema.getScene(), tgTema.isSelected());
        actualizarTemaTexto();
    }

    private void actualizarResumen() {
        lblEntorno.setText(String.format("Distancia: %.1f m · PPI: %.0f · Focal: %s", AppConfig.DISTANCIA_OBJETIVO_METROS_DEFECTO,
                AppConfig.PPI_ESTANDAR_MONITOR, AppConfig.DISTANCIA_FOCAL_CAMARA_PX > 0 ? String.format("%.0f px", AppConfig.DISTANCIA_FOCAL_CAMARA_PX) : "sin calibrar"));
    }

    private void actualizarTemaTexto() {
        boolean oscuro = tgTema != null && tgTema.isSelected();
        if (tgTema != null) tgTema.setText(oscuro ? "Cambiar a modo claro" : "Activar modo oscuro");
        if (lblTemaResumen != null) lblTemaResumen.setText(oscuro ? "Modo oscuro activo · texto y controles adaptados · guardado automáticamente" : "Modo claro activo · se guarda automáticamente");
    }

    private void aviso(Alert.AlertType tipo, String titulo, String mensaje) { Alert alerta = new Alert(tipo, mensaje, ButtonType.OK); alerta.setHeaderText(titulo); preparar(alerta); alerta.showAndWait(); }

    private void preparar(Dialog<?> dialogo) {
        dialogo.setResizable(true);
        dialogo.getDialogPane().setMinWidth(560);
        dialogo.getDialogPane().setPrefHeight(320);
        Button cancelar = (Button) dialogo.getDialogPane().lookupButton(ButtonType.CANCEL);
        Button aceptar = (Button) dialogo.getDialogPane().lookupButton(ButtonType.OK);
        if (cancelar != null) cancelar.setText("Cancelar");
        if (aceptar != null) aceptar.setText("Guardar");
    }
}