package com.eyesofter.ui;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import com.eyesofter.db.GestorBaseDatos;
import com.eyesofter.config.ConfiguracionUsuarios;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Contenedor persistente para los módulos administrativos de EYESOFTER.
 *
 * <h3>Regreso inteligente</h3>
 * Cada sección visitada queda en un historial <b>con la vista en caché</b>: al regresar
 * (←, Alt+←, Esc, Retroceso o el botón "atrás" del mouse) se recupera tal como se dejó —
 * filtros, búsqueda escrita, fila seleccionada y posición del scroll — sin volver a
 * cargarla. Los datos se refrescan solos (ver {@link ActualizacionPeriodica}).
 * Elegir una sección en el menú lateral o F5 sí la carga de nuevo.
 * Al volver al panel desde otra pantalla se abre la última sección usada.
 */
public class ControladorPanelAdministrativo implements ControladorDeVista {
    @FXML private BorderPane raizPanel;
    @FXML private StackPane contenido;
    @FXML private ScrollPane scrollContenido;
    @FXML private Button btnInicio, btnPacientes, btnHistorial, btnReportes, btnHerramientas, btnConfiguracion;
    @FXML private Button btnAtras, btnAdelante, btnRecargar;

    // Componentes para actualizar dinámicamente el usuario en la esquina superior derecha
    @FXML private Label lblNombreUsuarioHeader;
    @FXML private Label lblInicialesHeader;

    private static final String VISTA_INICIAL = "DashboardAdministrativo";
    private static final int MAX_HISTORIAL = 20;
    /** Última sección usada: al regresar al panel desde otra pantalla se abre esa. */
    private static String ultimaSeccion = VISTA_INICIAL;

    /** Una página del historial: su vista en caché, cómo recrearla y dónde iba el scroll. */
    private static final class Pagina {
        final String nombre;
        final Button boton;
        final Supplier<Node> fabrica;
        Node nodo;
        double scrollV;
        double scrollH;

        Pagina(String nombre, Button boton, Supplier<Node> fabrica, Node nodo) {
            this.nombre = nombre; this.boton = boton; this.fabrica = fabrica; this.nodo = nodo;
        }
    }

    private final Deque<Pagina> atras = new ArrayDeque<>();
    private final Deque<Pagina> adelante = new ArrayDeque<>();
    private Pagina actual;
    private Stage stage;

    @FXML private void initialize() {
        cargar(ultimaSeccion, botonDe(ultimaSeccion));

        // Sincronizar el usuario activo al cargar el panel por primera vez
        String usuarioActual = ConfiguracionUsuarios.obtenerUsuarioPrincipal();
        actualizarInformacionUsuario(usuarioActual);

        instalarAtajos();
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
        SesionEvaluacion.iniciar(paciente.curp, paciente.nombre, paciente.gradoGrupo, paciente.fechaNacimiento);

        // Aplicamos el modo elegido (Asistida o Autónoma)
        SesionEvaluacion.configurarModo(modo);

        ControladorConfiguracion.mostrarVentana(stage, paciente.curp, paciente.nombre, paciente.gradoGrupo);
    }

    public void abrirConfiguracionTecnica() { ControladorConfiguracion.mostrarVentana(stage, "", "", ""); }

    public void mostrarFicha(GestorBaseDatos.PacienteInfo paciente) {
        if (paciente == null) return;
        Supplier<Node> fabrica = () -> crearFicha(paciente);
        Node pagina = fabrica.get();
        if (pagina != null) navegarA(new Pagina("Ficha", btnPacientes, fabrica, pagina), true);
    }

    private Node crearFicha(GestorBaseDatos.PacienteInfo paciente) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/administrativo/FichaPaciente.fxml"));
            Node pagina = loader.load();
            ControladorFichaPaciente controlador = loader.getController();
            controlador.setPanelAdministrativo(this);
            controlador.setPaciente(paciente);
            return pagina;
        } catch (Exception e) {
            mostrarError("la ficha del paciente", e);
            return null;
        }
    }

    @FXML private void cerrarSesion() {
        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION,
                "¿Estás seguro de que deseas cerrar sesión?", ButtonType.CANCEL, ButtonType.OK);
        confirmacion.setHeaderText("Cerrar sesión");
        confirmacion.showAndWait().filter(respuesta -> respuesta == ButtonType.OK)
                .ifPresent(respuesta -> {
                    ultimaSeccion = VISTA_INICIAL;
                    Navegador.mostrar(stage, "/fxml/Acceso.fxml", "EYESOFTER · Iniciar sesión");
                });
    }

    @FXML private void salirAplicacion() { ConfirmacionSalida.mostrar(stage); }

    // ----------------------------------------------------------------- historial con caché

    /** Abre una sección del menú (siempre con datos frescos). */
    private void cargar(String vista, Button botonSeleccionado) {
        Supplier<Node> fabrica = () -> crearSeccion(vista);
        Node pagina = fabrica.get();
        if (pagina == null) return;
        // Volver a pulsar la misma sección la recarga en su lugar, sin llenar el historial.
        boolean mismaSeccion = actual != null && actual.nombre.equals(vista);
        navegarA(new Pagina(vista, botonSeleccionado, fabrica, pagina), !mismaSeccion);
    }

    private Node crearSeccion(String vista) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/administrativo/" + vista + ".fxml"));
            Node pagina = loader.load();
            Object controlador = loader.getController();
            if (controlador instanceof VistaAdministrativa vistaAdministrativa) {
                vistaAdministrativa.setPanelAdministrativo(this);
            }
            return pagina;
        } catch (Exception e) {
            mostrarError(vista, e);
            return null;
        }
    }

    private void navegarA(Pagina nueva, boolean registrarEnHistorial) {
        if (actual != null) {
            guardarScroll(actual);
            if (registrarEnHistorial) {
                atras.push(actual);
                while (atras.size() > MAX_HISTORIAL) atras.removeLast();
                adelante.clear();
            }
        }
        mostrar(nueva, false);
    }

    @FXML private void atras() { volver(); }
    @FXML private void adelante() { avanzar(); }

    /** Regresa a la página anterior desde la caché. Devuelve false si no hay a dónde regresar. */
    public boolean volver() {
        if (atras.isEmpty()) return false;
        guardarScroll(actual);
        adelante.push(actual);
        mostrar(atras.pop(), true);
        return true;
    }

    public boolean avanzar() {
        if (adelante.isEmpty()) return false;
        guardarScroll(actual);
        atras.push(actual);
        mostrar(adelante.pop(), true);
        return true;
    }

    /** F5: vuelve a crear la página actual con datos frescos (conserva el historial). */
    @FXML public void recargar() {
        if (actual == null) return;
        Node nuevo = actual.fabrica.get();
        if (nuevo == null) return;
        guardarScroll(actual);
        actual.nodo = nuevo;
        mostrar(actual, true);
    }

    private void mostrar(Pagina pagina, boolean restaurarScroll) {
        actual = pagina;
        contenido.getChildren().setAll(pagina.nodo);
        seleccionarMenu(pagina.boton);
        InteraccionTablas.instalarEn(pagina.nodo);
        if (!"Ficha".equals(pagina.nombre)) ultimaSeccion = pagina.nombre;
        double v = restaurarScroll ? pagina.scrollV : 0;
        double h = restaurarScroll ? pagina.scrollH : 0;
        scrollContenido.setVvalue(v);
        scrollContenido.setHvalue(h);
        // Tras el layout la altura del contenido ya es la real: se vuelve a aplicar.
        Platform.runLater(() -> {
            InteraccionTablas.instalarEn(pagina.nodo);
            if (actual == pagina) { scrollContenido.setVvalue(v); scrollContenido.setHvalue(h); }
        });
        actualizarBotonesNavegacion();
    }

    private void guardarScroll(Pagina pagina) {
        if (pagina == null) return;
        pagina.scrollV = scrollContenido.getVvalue();
        pagina.scrollH = scrollContenido.getHvalue();
    }

    private void actualizarBotonesNavegacion() {
        if (btnAtras != null) btnAtras.setDisable(atras.isEmpty());
        if (btnAdelante != null) btnAdelante.setDisable(adelante.isEmpty());
    }

    // ----------------------------------------------------------------- mouse y teclado

    private void instalarAtajos() {
        if (raizPanel == null) return;

        // Botones laterales del mouse (atrás / adelante).
        raizPanel.addEventFilter(MouseEvent.MOUSE_PRESSED, evento -> {
            if (evento.getButton() == MouseButton.BACK) { volver(); evento.consume(); }
            else if (evento.getButton() == MouseButton.FORWARD) { avanzar(); evento.consume(); }
        });

        raizPanel.addEventHandler(KeyEvent.KEY_PRESSED, evento -> {
            KeyCode tecla = evento.getCode();
            boolean escribiendo = escribiendoTexto(evento);
            if (evento.isAltDown() && tecla == KeyCode.LEFT) { volver(); evento.consume(); }
            else if (evento.isAltDown() && tecla == KeyCode.RIGHT) { avanzar(); evento.consume(); }
            else if (tecla == KeyCode.F5) { recargar(); evento.consume(); }
            else if (tecla == KeyCode.BACK_SPACE && !escribiendo) { if (volver()) evento.consume(); }
            // Esc nunca saca del panel (antes regresaba a la pantalla de acceso).
            else if (tecla == KeyCode.ESCAPE && !escribiendo) { volver(); evento.consume(); }
        });

        // Clic derecho en una zona libre de la sección: navegación y tamaño de la interfaz.
        // (Las tablas y campos de texto tienen su propio menú y no llegan aquí.)
        ContextMenu menu = new ContextMenu();
        MenuItem mAtras = new MenuItem("←  Atrás");
        MenuItem mAdelante = new MenuItem("→  Adelante");
        MenuItem mRecargar = new MenuItem("⟳  Actualizar sección (F5)");
        MenuItem mInicio = new MenuItem("▦  Ir a Inicio");
        MenuItem mAgrandar = new MenuItem("Agrandar interfaz (Ctrl +)");
        MenuItem mReducir = new MenuItem("Reducir interfaz (Ctrl −)");
        MenuItem mNormal = new MenuItem("Tamaño normal (Ctrl 0)");
        mAtras.setOnAction(e -> volver());
        mAdelante.setOnAction(e -> avanzar());
        mRecargar.setOnAction(e -> recargar());
        mInicio.setOnAction(e -> inicio());
        mAgrandar.setOnAction(e -> PantallaAdaptable.agrandar());
        mReducir.setOnAction(e -> PantallaAdaptable.reducir());
        mNormal.setOnAction(e -> PantallaAdaptable.restablecer());
        menu.getItems().addAll(mAtras, mAdelante, mRecargar, mInicio, new SeparatorMenuItem(), mAgrandar, mReducir, mNormal);
        menu.setOnShowing(e -> {
            mAtras.setDisable(atras.isEmpty());
            mAdelante.setDisable(adelante.isEmpty());
            mNormal.setText("Tamaño normal (Ctrl 0) · ahora " + PantallaAdaptable.porcentajeZoom() + " %");
        });
        scrollContenido.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, evento -> {
            menu.show(scrollContenido, evento.getScreenX(), evento.getScreenY());
            evento.consume();
        });
        scrollContenido.addEventFilter(MouseEvent.MOUSE_PRESSED, evento -> {
            if (evento.getButton() == MouseButton.PRIMARY && menu.isShowing()) menu.hide();
        });
    }

    /** True si el foco está en un campo donde Retroceso/Esc tienen otro significado. */
    private boolean escribiendoTexto(KeyEvent evento) {
        Object objetivo = evento.getTarget();
        if (objetivo instanceof TextInputControl) return true;
        if (objetivo instanceof ComboBoxBase<?> combo && (combo.isEditable() || combo.isShowing())) return true;
        Node foco = raizPanel.getScene() == null ? null : raizPanel.getScene().getFocusOwner();
        return foco instanceof TextInputControl;
    }

    // ----------------------------------------------------------------- utilidades

    private Button botonDe(String vista) {
        return switch (vista) {
            case "PacientesAdministrativo" -> btnPacientes;
            case "HistorialAdministrativo" -> btnHistorial;
            case "ReportesAdministrativo" -> btnReportes;
            case "HerramientasAdministrativo" -> btnHerramientas;
            case "ConfiguracionAdministrativo" -> btnConfiguracion;
            default -> btnInicio;
        };
    }

    private void mostrarError(String vista, Exception e) {
        e.printStackTrace();
        String detalle = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
        Alert alerta = new Alert(Alert.AlertType.ERROR, "No fue posible abrir esta sección.\n" + (detalle == null ? "Sin detalle disponible." : detalle), ButtonType.OK);
        alerta.setHeaderText("Error al abrir " + vista);
        alerta.showAndWait();
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
        if (botonSeleccionado != null) botonSeleccionado.getStyleClass().add("boton-menu-activo");
    }
}
