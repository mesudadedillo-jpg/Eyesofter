package com.eyesofter.ui;

import javafx.scene.Node;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Comportamiento común para todas las tablas de la aplicación:
 * <ul>
 *   <li><b>Clic derecho</b>: copiar celda, fila o tabla completa (se pega directo en Excel)
 *       y ajustar columnas al ancho. Si la tabla ya tenía su menú (completar cita, ver
 *       ficha…) estas opciones se agregan al final, no lo reemplazan.</li>
 *   <li><b>Ctrl + C</b>: copia las filas seleccionadas.</li>
 *   <li><b>Texto cortado</b> ("Mateo Hern…"): al pasar el mouse se muestra completo.</li>
 * </ul>
 */
public final class InteraccionTablas {
    private static final String MARCA_TABLA = "eyesofter.tabla.instalada";
    private static final String MARCA_MENU = "eyesofter.menu.generico";
    private static final String CELDA_ACTUAL = "eyesofter.celda.actual";

    private InteraccionTablas() { }

    /** Instala el comportamiento en todas las tablas que haya dentro de {@code raiz}. */
    public static void instalarEn(Node raiz) {
        if (raiz == null) return;
        for (Node nodo : raiz.lookupAll(".table-view")) {
            if (nodo instanceof TableView<?> tabla) instalar(tabla);
        }
    }

    public static void instalar(TableView<?> tabla) {
        if (tabla.getProperties().containsKey(MARCA_TABLA)) return;
        tabla.getProperties().put(MARCA_TABLA, Boolean.TRUE);

        tabla.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, evento -> prepararMenu(tabla, evento));
        tabla.addEventHandler(KeyEvent.KEY_PRESSED, evento -> {
            if (evento.isShortcutDown() && evento.getCode() == KeyCode.C) {
                copiarSeleccion(tabla);
                evento.consume();
            }
        });

        Tooltip ayuda = new Tooltip();
        ayuda.setShowDelay(Duration.millis(450));
        ayuda.setWrapText(true);
        ayuda.setMaxWidth(460);
        TableCell<?, ?>[] ultima = new TableCell<?, ?>[1];
        tabla.addEventFilter(MouseEvent.MOUSE_MOVED, evento -> {
            TableCell<?, ?> celda = celdaDe(evento.getPickResult().getIntersectedNode());
            if (celda == ultima[0]) return;
            if (ultima[0] != null && ultima[0].getTooltip() == ayuda) ultima[0].setTooltip(null);
            ultima[0] = celda;
            if (celda != null && celda.getTooltip() == null && textoCortado(celda)) {
                ayuda.setText(celda.getText());
                celda.setTooltip(ayuda);
            }
        });
    }

    // ----------------------------------------------------------------- menú contextual

    private static void prepararMenu(TableView<?> tabla, ContextMenuEvent evento) {
        Node objetivo = evento.getPickResult().getIntersectedNode();
        TableCell<?, ?> celda = celdaDe(objetivo);
        tabla.getProperties().put(CELDA_ACTUAL, celda);

        TableRow<?> fila = filaDe(objetivo);
        ContextMenu menu = fila != null && !fila.isEmpty() ? fila.getContextMenu() : null;
        if (menu == null) {
            menu = tabla.getContextMenu();
            if (menu == null) {
                menu = new ContextMenu();
                tabla.setContextMenu(menu);
            }
        }
        if (menu.getProperties().containsKey(MARCA_MENU)) return;
        menu.getProperties().put(MARCA_MENU, Boolean.TRUE);

        MenuItem copiarCelda = new MenuItem("Copiar celda");
        copiarCelda.setOnAction(e -> {
            TableCell<?, ?> c = (TableCell<?, ?>) tabla.getProperties().get(CELDA_ACTUAL);
            if (c != null && !c.isEmpty()) copiar(textoCelda(c.getTableColumn(), c.getIndex()));
        });
        MenuItem copiarFila = new MenuItem("Copiar fila");
        copiarFila.setOnAction(e -> {
            TableCell<?, ?> c = (TableCell<?, ?>) tabla.getProperties().get(CELDA_ACTUAL);
            int indice = c != null && !c.isEmpty() ? c.getIndex() : tabla.getSelectionModel().getSelectedIndex();
            if (indice >= 0) copiar(textoFila(tabla, indice));
        });
        MenuItem copiarTabla = new MenuItem("Copiar tabla completa (para Excel)");
        copiarTabla.setOnAction(e -> copiarTodo(tabla));
        CheckMenuItem ajustar = new CheckMenuItem("Ajustar columnas al ancho");
        ajustar.setSelected(tabla.getColumnResizePolicy() == TableView.CONSTRAINED_RESIZE_POLICY);
        ajustar.setOnAction(e -> tabla.setColumnResizePolicy(ajustar.isSelected()
                ? TableView.CONSTRAINED_RESIZE_POLICY : TableView.UNCONSTRAINED_RESIZE_POLICY));

        if (!menu.getItems().isEmpty()) menu.getItems().add(new SeparatorMenuItem());
        menu.getItems().addAll(copiarCelda, copiarFila, copiarTabla, new SeparatorMenuItem(), ajustar);
        ContextMenu menuFinal = menu;
        menu.setOnShowing(e -> {
            TableCell<?, ?> c = (TableCell<?, ?>) tabla.getProperties().get(CELDA_ACTUAL);
            copiarCelda.setDisable(c == null || c.isEmpty());
            copiarTabla.setDisable(tabla.getItems().isEmpty());
            ajustar.setSelected(tabla.getColumnResizePolicy() == TableView.CONSTRAINED_RESIZE_POLICY);
            menuFinal.getProperties().put("abierto", true);
        });
    }

    // ----------------------------------------------------------------- copiar

    private static void copiarSeleccion(TableView<?> tabla) {
        List<Integer> indices = new ArrayList<>(tabla.getSelectionModel().getSelectedIndices());
        if (indices.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i : indices) sb.append(textoFila(tabla, i)).append('\n');
        copiar(sb.toString().stripTrailing());
    }

    private static void copiarTodo(TableView<?> tabla) {
        StringBuilder sb = new StringBuilder();
        List<String> encabezados = new ArrayList<>();
        for (TableColumn<?, ?> columna : tabla.getVisibleLeafColumns()) encabezados.add(limpiar(columna.getText()));
        sb.append(String.join("\t", encabezados)).append('\n');
        for (int i = 0; i < tabla.getItems().size(); i++) sb.append(textoFila(tabla, i)).append('\n');
        copiar(sb.toString().stripTrailing());
    }

    private static String textoFila(TableView<?> tabla, int indice) {
        List<String> valores = new ArrayList<>();
        for (TableColumn<?, ?> columna : tabla.getVisibleLeafColumns()) valores.add(textoCelda(columna, indice));
        return String.join("\t", valores);
    }

    private static String textoCelda(TableColumn<?, ?> columna, int indice) {
        if (columna == null || indice < 0) return "";
        Object valor = columna.getCellData(indice);
        return limpiar(valor == null ? "" : valor.toString());
    }

    private static String limpiar(String texto) {
        return texto == null ? "" : texto.replace('\t', ' ').replace("\r", "").replace('\n', ' ').trim();
    }

    private static void copiar(String texto) {
        ClipboardContent contenido = new ClipboardContent();
        contenido.putString(texto);
        Clipboard.getSystemClipboard().setContent(contenido);
    }

    // ----------------------------------------------------------------- utilidades

    private static boolean textoCortado(TableCell<?, ?> celda) {
        if (celda.isEmpty() || celda.getText() == null || celda.getText().isBlank()) return false;
        if (celda.getText().contains("\n")) return true;
        Text medida = new Text(celda.getText());
        medida.setFont(celda.getFont());
        double disponible = celda.getWidth() - celda.snappedLeftInset() - celda.snappedRightInset();
        return medida.getLayoutBounds().getWidth() > disponible - 1;
    }

    private static TableCell<?, ?> celdaDe(Node nodo) {
        while (nodo != null && !(nodo instanceof TableView)) {
            if (nodo instanceof TableCell<?, ?> celda) return celda;
            nodo = nodo.getParent();
        }
        return null;
    }

    private static TableRow<?> filaDe(Node nodo) {
        while (nodo != null && !(nodo instanceof TableView)) {
            if (nodo instanceof TableRow<?> fila) return fila;
            nodo = nodo.getParent();
        }
        return null;
    }
}
