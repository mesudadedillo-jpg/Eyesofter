package com.eyesofter.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.config.ConfiguracionUsuarios;

import java.io.IOException;

/** Contenedor persistente para los módulos administrativos de EYESOFTER. */
public class ControladorPanelAdministrativo implements ControladorDeVista {
    @FXML private StackPane contenido;
    @FXML private Button btnInicio, btnPacientes, btnHistorial, btnReportes, btnHerramientas, btnConfiguracion;

    // Componentes para actualizar dinámicamente el usuario en la esquina superior derecha
    @FXML private Label lblNombreUsuarioHeader;
    @FXML private Label lblInicialesHeader;

    private Stage stage;

    @FXML private void initialize() {
        cargar("DashboardAdministrativo", btnInicio);

        // Sincronizar el usuario activo al cargar el panel por primera vez
        String usuarioActual = ConfiguracionUsuarios.obtenerUsuarioPrincipal();
        actualizarInformacionUsuario(usuarioActual);
    }

    @Override public void setStage(Stage stage) { this.stage = stage; }
    @FXML private void inicio() { cargar("DashboardAdministrativo", btnInicio); }
    @FXML private void pacientes() { cargar("PacientesAdministrativo", btnPacientes); }
    @FXML public void historial() { cargar("HistorialAdministrativo", btnHistorial); }
    @FXML private void reportes() { cargar("ReportesAdministrativo", btnReportes); }
    @FXML private void herramientas() { cargar("HerramientasAdministrativo", btnHerramientas); }
    @FXML private void configuracion() { cargar("ConfiguracionAdministrativo", btnConfiguracion); }
    public void mostrarPacientes() { cargar("PacientesAdministrativo", btnPacientes); }

    // --- VOCABULARIO CLÍNICO ACTUALIZADO ---
    public void iniciarTamizaje() { ControladorRegistro.mostrarVentana(stage); }

    public void iniciarTamizajePaciente(GestorBaseDatos.PacienteInfo paciente, SesionEvaluacion.ModoEvaluacion modo) {
        if (paciente == null) return;
        SesionEvaluacion.iniciar(paciente.curp, paciente.nombre, paciente.gradoGrupo);

        // Aplicamos el modo elegido (Asistida o Autónoma)
        SesionEvaluacion.configurarModo(modo);

        ControladorConfiguracion.mostrarVentana(stage, paciente.curp, paciente.nombre, paciente.gradoGrupo);
    }

    public void abrirConfiguracionTecnica() { ControladorConfiguracion.mostrarVentana(stage, "", "", ""); }

    public void mostrarFicha(GestorBaseDatos.PacienteInfo paciente) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/administrativo/FichaPaciente.fxml"));
            Node pagina = loader.load();
            ControladorFichaPaciente controlador = loader.getController();
            controlador.setPanelAdministrativo(this);
            controlador.setPaciente(paciente);
            contenido.getChildren().setAll(pagina);
            seleccionarMenu(btnPacientes);
        } catch (IOException e) {
            throw new IllegalStateException("No fue posible cargar la ficha del paciente", e);
        }
    }

    @FXML private void cerrarSesion() {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Estás seguro de que deseas cerrar sesión?", ButtonType.CANCEL, ButtonType.OK);
        confirmacion.setHeaderText("Cerrar sesión");
        confirmacion.showAndWait().filter(respuesta -> respuesta == ButtonType.OK)
                .ifPresent(respuesta -> Navegador.mostrar(stage, "/fxml/Acceso.fxml", "EYESOFTER · Iniciar sesión"));
    }

    @FXML private void salirAplicacion() { ConfirmacionSalida.mostrar(stage); }

    private void cargar(String vista, Button botonSeleccionado) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/administrativo/" + vista + ".fxml"));
            Node pagina = loader.load();
            Object controlador = loader.getController();
            if (controlador instanceof VistaAdministrativa vistaAdministrativa) {
                vistaAdministrativa.setPanelAdministrativo(this);
            }
            contenido.getChildren().setAll(pagina);
            seleccionarMenu(botonSeleccionado);
        } catch (Exception e) {
            e.printStackTrace();
            String detalle = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            Alert alerta = new Alert(Alert.AlertType.ERROR, "No fue posible abrir esta sección.\n" + (detalle == null ? "Sin detalle disponible." : detalle), ButtonType.OK);
            alerta.setHeaderText("Error al abrir " + vista);
            alerta.showAndWait();
        }
    }

    public void actualizarInformacionUsuario(String nuevoUsuario) {
        if (nuevoUsuario == null || nuevoUsuario.trim().isEmpty()) return;

        if (lblNombreUsuarioHeader != null) {
            lblNombreUsuarioHeader.setText(nuevoUsuario);
        }

        if (lblInicialesHeader != null && nuevoUsuario.length() >= 2) {
            lblInicialesHeader.setText(nuevoUsuario.substring(0, 2).toUpperCase());
        }
    }

    private void seleccionarMenu(Button botonSeleccionado) {
        for (Button boton : new Button[] {btnInicio, btnPacientes, btnHistorial, btnReportes, btnHerramientas, btnConfiguracion}) {
            boton.getStyleClass().remove("boton-menu-activo");
        }
        botonSeleccionado.getStyleClass().add("boton-menu-activo");
    }
}