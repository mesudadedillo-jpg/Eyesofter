package com.eyesofter.db;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gestor de persistencia desacoplado para la agenda de citas en formato JSON plano.
 * Almacena las citas en 'citas_agenda.json' fuera de la base de datos clínica SQLite.
 */
public final class GestorAgendaJSON {

    private static final String NOMBRE_ARCHIVO = "citas_agenda.json";
    private static final List<GestorBaseDatos.CitaInfo> memoriaCitas = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicInteger contadorIds = new AtomicInteger(1);
    private static boolean inicializado = false;

    private GestorAgendaJSON() { }

    private static File obtenerArchivoJSON() {
        return new File(System.getProperty("eyesofter.home", "."), NOMBRE_ARCHIVO);
    }

    public static synchronized void inicializar() {
        if (inicializado) return;
        cargarDesdeDisco();
        inicializado = true;
    }

    // =========================================================================
    // OPERACIONES CRUD EN JSON
    // =========================================================================

    public static synchronized boolean agendarCita(String curpPaciente, LocalDate fecha, String hora, String tipo, String notas) {
        inicializar();
        if (curpPaciente == null || fecha == null || hora == null || tipo == null || hora.isBlank()) return false;

        String fechaStr = fecha.toString();
        String horaLimpia = hora.trim();

        // Validar conflicto de horario (no dos citas el mismo día a la misma hora activas)
        boolean horarioOcupado = memoriaCitas.stream().anyMatch(c ->
                fechaStr.equals(c.fecha) &&
                        horaLimpia.equalsIgnoreCase(c.hora) &&
                        !"CANCELADA".equalsIgnoreCase(c.estado)
        );
        if (horarioOcupado) return false;

        // Obtener nombre del paciente para enriquecer la cita
        GestorBaseDatos.PacienteInfo paciente = GestorBaseDatos.buscarPacientePorCurp(curpPaciente);
        String nombrePaciente = paciente != null ? paciente.nombre : "Paciente " + curpPaciente;

        GestorBaseDatos.CitaInfo nueva = new GestorBaseDatos.CitaInfo();
        nueva.idCita = contadorIds.getAndIncrement();
        nueva.curpPaciente = curpPaciente.trim().toUpperCase();
        nueva.nombrePaciente = nombrePaciente;
        nueva.fecha = fechaStr;
        nueva.hora = horaLimpia;
        nueva.tipo = tipo.trim();
        nueva.notas = notas == null ? "" : notas.trim();
        nueva.estado = "EN_ESPERA";

        memoriaCitas.add(nueva);
        guardarEnDisco();
        return true;
    }

    public static synchronized boolean actualizarEstadoCita(int idCita, String nuevoEstado) {
        inicializar();
        if (idCita <= 0 || nuevoEstado == null) return false;

        for (GestorBaseDatos.CitaInfo c : memoriaCitas) {
            if (c.idCita == idCita) {
                c.estado = nuevoEstado.trim().toUpperCase();
                guardarEnDisco();
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean actualizarCita(int idCita, LocalDate fecha, String hora, String tipo, String notas, String estado) {
        inicializar();
        for (GestorBaseDatos.CitaInfo c : memoriaCitas) {
            if (c.idCita == idCita) {
                if (fecha != null) c.fecha = fecha.toString();
                if (hora != null && !hora.isBlank()) c.hora = hora.trim();
                if (tipo != null && !tipo.isBlank()) c.tipo = tipo.trim();
                if (notas != null) c.notas = notas.trim();
                if (estado != null && !estado.isBlank()) c.estado = estado.trim();
                guardarEnDisco();
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean eliminarCita(int idCita) {
        inicializar();
        boolean removido = memoriaCitas.removeIf(c -> c.idCita == idCita);
        if (removido) {
            guardarEnDisco();
        }
        return removido;
    }

    // =========================================================================
    // CONSULTAS
    // =========================================================================

    public static synchronized List<GestorBaseDatos.CitaInfo> obtenerCitas(LocalDate fecha) {
        inicializar();
        if (fecha == null) return Collections.emptyList();
        String fStr = fecha.toString();
        List<GestorBaseDatos.CitaInfo> resultado = new ArrayList<>();
        for (GestorBaseDatos.CitaInfo c : memoriaCitas) {
            if (fStr.equals(c.fecha)) resultado.add(clonar(c));
        }
        resultado.sort(Comparator.comparing(c -> c.hora));
        return resultado;
    }

    public static synchronized List<GestorBaseDatos.CitaInfo> obtenerCitasProximas(LocalDate desdeFecha) {
        inicializar();
        if (desdeFecha == null) desdeFecha = LocalDate.now();
        final LocalDate limite = desdeFecha;
        List<GestorBaseDatos.CitaInfo> resultado = new ArrayList<>();

        for (GestorBaseDatos.CitaInfo c : memoriaCitas) {
            try {
                LocalDate fCita = LocalDate.parse(c.fecha);
                if (!fCita.isBefore(limite)) {
                    resultado.add(clonar(c));
                }
            } catch (Exception ignored) { }
        }
        resultado.sort(Comparator.comparing((GestorBaseDatos.CitaInfo c) -> c.fecha).thenComparing(c -> c.hora));
        return resultado;
    }

    public static synchronized List<GestorBaseDatos.CitaInfo> obtenerCitasPaciente(String curpPaciente) {
        inicializar();
        if (curpPaciente == null || curpPaciente.isBlank()) return Collections.emptyList();
        String curpNorm = curpPaciente.trim().toUpperCase();
        List<GestorBaseDatos.CitaInfo> resultado = new ArrayList<>();
        for (GestorBaseDatos.CitaInfo c : memoriaCitas) {
            if (curpNorm.equalsIgnoreCase(c.curpPaciente)) {
                resultado.add(clonar(c));
            }
        }
        resultado.sort(Comparator.comparing((GestorBaseDatos.CitaInfo c) -> c.fecha).reversed().thenComparing(c -> c.hora));
        return resultado;
    }

    public static synchronized List<GestorBaseDatos.CitaInfo> obtenerTodasLasCitas() {
        inicializar();
        List<GestorBaseDatos.CitaInfo> resultado = new ArrayList<>();
        for (GestorBaseDatos.CitaInfo c : memoriaCitas) {
            resultado.add(clonar(c));
        }
        resultado.sort(Comparator.comparing((GestorBaseDatos.CitaInfo c) -> c.fecha).reversed().thenComparing(c -> c.hora));
        return resultado;
    }

    // =========================================================================
    // PERSISTENCIA Y PARSEO JSON NATIVO
    // =========================================================================

    private static synchronized void guardarEnDisco() {
        File archivo = obtenerArchivoJSON();
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");

        for (int i = 0; i < memoriaCitas.size(); i++) {
            GestorBaseDatos.CitaInfo c = memoriaCitas.get(i);
            sb.append("  {\n");
            sb.append("    \"idCita\": ").append(c.idCita).append(",\n");
            sb.append("    \"curpPaciente\": \"").append(escapar(c.curpPaciente)).append("\",\n");
            sb.append("    \"nombrePaciente\": \"").append(escapar(c.nombrePaciente)).append("\",\n");
            sb.append("    \"fecha\": \"").append(escapar(c.fecha)).append("\",\n");
            sb.append("    \"hora\": \"").append(escapar(c.hora)).append("\",\n");
            sb.append("    \"tipo\": \"").append(escapar(c.tipo)).append("\",\n");
            sb.append("    \"notas\": \"").append(escapar(c.notas)).append("\",\n");
            sb.append("    \"estado\": \"").append(escapar(c.estado)).append("\"\n");
            sb.append("  }").append(i < memoriaCitas.size() - 1 ? "," : "").append("\n");
        }
        sb.append("]\n");

        try {
            if (archivo.getParentFile() != null && !archivo.getParentFile().exists()) {
                archivo.getParentFile().mkdirs();
            }
            Files.writeString(archivo.toPath(), sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("[JSON ERROR]: No se pudo escribir en " + archivo.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private static synchronized void cargarDesdeDisco() {
        memoriaCitas.clear();
        File archivo = obtenerArchivoJSON();
        if (!archivo.exists()) {
            // Si no existe, sembramos datos iniciales de Tulancingo
            sembrarCitasIniciales();
            return;
        }

        try {
            String contenido = Files.readString(archivo.toPath(), StandardCharsets.UTF_8).trim();
            if (contenido.isEmpty() || !contenido.startsWith("[")) {
                sembrarCitasIniciales();
                return;
            }

            // Parser simple nativo de objetos JSON
            int maxId = 0;
            String[] bloques = contenido.split("\\},\\s*\\{");
            for (String bloque : bloques) {
                GestorBaseDatos.CitaInfo c = new GestorBaseDatos.CitaInfo();
                c.idCita = extraerEntero(bloque, "idCita");
                c.curpPaciente = extraerCadena(bloque, "curpPaciente");
                c.nombrePaciente = extraerCadena(bloque, "nombrePaciente");
                c.fecha = extraerCadena(bloque, "fecha");
                c.hora = extraerCadena(bloque, "hora");
                c.tipo = extraerCadena(bloque, "tipo");
                c.notas = extraerCadena(bloque, "notas");
                c.estado = extraerCadena(bloque, "estado");

                if (c.idCita > maxId) maxId = c.idCita;
                if (c.curpPaciente != null && !c.curpPaciente.isBlank()) {
                    memoriaCitas.add(c);
                }
            }
            contadorIds.set(maxId + 1);

        } catch (Exception e) {
            System.err.println("[JSON ERROR]: Fallo al leer " + archivo.getAbsolutePath() + ". Regenerando...");
            sembrarCitasIniciales();
        }
    }

    private static void sembrarCitasIniciales() {
        memoriaCitas.clear();
        LocalDate hoy = LocalDate.now();

        // 12 citas realistas de Tulancingo distribuidas entre hoy y fechas próximas
        String[][] datos = {
                {"HERM180312HDFNLB01", "Mateo Hernández García", hoy.toString(), "09:30", "Tamizaje Escolar Anual", "Revisión preventiva general.", "COMPLETADA"},
                {"GARM180524MDFRLN02", "Sofía García Morales", hoy.toString(), "10:15", "Tamizaje Escolar Anual", "Se acerca mucho a los cuadernos.", "COMPLETADA"},
                {"LOPR170709HDFPNS03", "Santiago López Ruiz", hoy.plusDays(1).toString(), "09:00", "Tamizaje Escolar Anual", "Revisión periódica de agudeza y color.", "EN_ESPERA"},
                {"MARV170915MDFTSA04", "Valentina Martínez Vargas", hoy.plusDays(1).toString(), "11:20", "Tamizaje Escolar Anual", "Reporta dolor de cabeza vespertino.", "EN_ESPERA"},
                {"SANR160128HDFSRG05", "Emiliano Sánchez Romero", hoy.plusDays(2).toString(), "08:45", "Tamizaje Escolar Anual", "Control rutinario.", "EN_ESPERA"},
                {"RODC160410MDFRLT06", "Camila Rodríguez Castillo", hoy.plusDays(2).toString(), "10:00", "Tamizaje Escolar Anual", "Revisión de ciclo escolar.", "EN_ESPERA"},
                {"PACL150822HDFCPR07", "Leonardo Pacheco López", hoy.plusDays(3).toString(), "09:10", "Seguimiento Clínico", "Bajo rendimiento en pizarrón.", "EN_ESPERA"},
                {"DOMR151130MDFMTN08", "Regina Domínguez Téllez", hoy.plusDays(3).toString(), "11:40", "Tamizaje Escolar Anual", "Revisión anual rutinaria.", "EN_ESPERA"},
                {"CASM140605HDFSLS09", "Sebastián Castillo Mendoza", hoy.plusDays(4).toString(), "08:30", "Seguimiento Clínico", "Sospecha de miopía.", "EN_ESPERA"},
                {"VARM141018MDFGNS10", "Ximena Vargas Mendoza", hoy.plusDays(4).toString(), "10:50", "Tamizaje Escolar Anual", "Egreso de primaria.", "EN_ESPERA"}
        };

        int id = 1;
        for (String[] d : datos) {
            GestorBaseDatos.CitaInfo c = new GestorBaseDatos.CitaInfo();
            c.idCita = id++;
            c.curpPaciente = d[0];
            c.nombrePaciente = d[1];
            c.fecha = d[2];
            c.hora = d[3];
            c.tipo = d[4];
            c.notas = d[5];
            c.estado = d[6];
            memoriaCitas.add(c);
        }
        contadorIds.set(id);
        guardarEnDisco();
    }

    private static GestorBaseDatos.CitaInfo clonar(GestorBaseDatos.CitaInfo orig) {
        GestorBaseDatos.CitaInfo c = new GestorBaseDatos.CitaInfo();
        c.idCita = orig.idCita;
        c.curpPaciente = orig.curpPaciente;
        c.nombrePaciente = orig.nombrePaciente;
        c.fecha = orig.fecha;
        c.hora = orig.hora;
        c.tipo = orig.tipo;
        c.notas = orig.notas;
        c.estado = orig.estado;
        return c;
    }

    private static String escapar(String valor) {
        if (valor == null) return "";
        return valor.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }

    private static String extraerCadena(String json, String campo) {
        String patron = "\"" + campo + "\"\\s*:\\s*\"";
        int idx = json.indexOf(campo);
        if (idx == -1) return "";
        int inicio = json.indexOf("\"", idx + campo.length() + 1);
        if (inicio == -1) return "";
        int fin = json.indexOf("\"", inicio + 1);
        if (fin == -1) return "";
        return json.substring(inicio + 1, fin).trim();
    }

    private static int extraerEntero(String json, String campo) {
        try {
            int idx = json.indexOf("\"" + campo + "\"");
            if (idx == -1) return 0;
            int dosPuntos = json.indexOf(":", idx);
            int coma = json.indexOf(",", dosPuntos);
            if (coma == -1) coma = json.indexOf("}", dosPuntos);
            return Integer.parseInt(json.substring(dosPuntos + 1, coma).replaceAll("[^0-9]", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }
}