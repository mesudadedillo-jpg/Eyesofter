package com.eyesofter.db;

import com.eyesofter.clinica.ResultadoClinico;
import com.eyesofter.config.AppConfig;

import java.io.File;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestor de Base de Datos local (SQLite) para EYESOFTER.
 * Soporta operaciones completas de CRUD (Crear, Buscar, Editar, Eliminar)
 * con control de transacciones e integridad referencial.
 */
public class GestorBaseDatos {

    /** Ubicación única del archivo SQLite local. */
    public static String urlBaseDatos() {
        String configurada = AppConfig.URL_BASE_DATOS;
        if (configurada != null && !configurada.isBlank() && !"jdbc:sqlite:eyesofter_local.db".equals(configurada.trim())) {
            return configurada.trim();
        }
        return "jdbc:sqlite:" + new File(System.getProperty("eyesofter.home", "."), "eyesofter_local.db").getPath();
    }

    public static Connection abrirConexion() throws SQLException {
        Connection conexion = DriverManager.getConnection(urlBaseDatos());
        try (Statement stmt = conexion.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
        return conexion;
    }

    // =========================================================================
    // INICIALIZACIÓN Y ESTRUCTURA DE TABLAS
    // =========================================================================

    public static void inicializarBaseDatos() {
        File directorio = new File(System.getProperty("eyesofter.home", "."));
        if (!directorio.exists() && !directorio.mkdirs()) {
            System.err.println("[DB ERROR CRÍTICO]: No se pudo crear el directorio de datos locales.");
            return;
        }

        // Inicializar también la agenda JSON
        GestorAgendaJSON.inicializar();

        try (Connection conn = abrirConexion();
             Statement stmt = conn.createStatement()) {

            // Tabla Tutor
            stmt.execute("CREATE TABLE IF NOT EXISTS tutor (" +
                    "curp TEXT PRIMARY KEY, " +
                    "nombre TEXT NOT NULL, " +
                    "telefono TEXT, " +
                    "correo TEXT)");

            // Tabla Paciente / Alumno
            stmt.execute("CREATE TABLE IF NOT EXISTS paciente (" +
                    "curp TEXT PRIMARY KEY, " +
                    "nombre TEXT NOT NULL, " +
                    "fecha_nacimiento TEXT, " +
                    "grado_grupo TEXT, " +
                    "curp_tutor TEXT, " +
                    "FOREIGN KEY(curp_tutor) REFERENCES tutor(curp) ON DELETE SET NULL ON UPDATE CASCADE)");

            // Grupos escolares (pasar de año a todo un grupo sin editar paciente por paciente).
            GestorGrupos.crearEsquema(stmt);

            // Tabla Evaluacion Clínica
            stmt.execute("CREATE TABLE IF NOT EXISTS evaluacion (" +
                    "id_evaluacion INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "curp_paciente TEXT, " +
                    "fecha_hora TEXT, " +
                    "agudeza_izq TEXT, " +
                    "agudeza_der TEXT, " +
                    "resultado_ishihara TEXT, " +
                    "tasa_ruido_wer REAL, " +
                    "estado_recomendacion TEXT, " +
                    "snellen_izq TEXT, " +
                    "snellen_der TEXT, " +
                    "snellen_ao TEXT, " +
                    "logmar_izq REAL, " +
                    "logmar_der REAL, " +
                    "logmar_ao REAL, " +
                    "dioptrias_izq REAL, " +
                    "dioptrias_der REAL, " +
                    "dioptrias_ao REAL, " +
                    "ishihara_aciertos INTEGER, " +
                    "ishihara_total INTEGER, " +
                    "ishihara_respuestas_deficiencia INTEGER, " +
                    "cumplimiento_distancia_pct REAL, " +
                    "muestras_distancia INTEGER, " +
                    "tiempo_respuesta_ms INTEGER, " +
                    "distancia_pupilar_mm REAL, " +
                    "dp_calibrada INTEGER, " +
                    "metodo_entrada TEXT, " +
                    "distancia_objetivo_m REAL, " +
                    "tolerancia_distancia_cm REAL, " +
                    "alertas_clinicas TEXT, " +
                    "FOREIGN KEY(curp_paciente) REFERENCES paciente(curp) ON DELETE CASCADE ON UPDATE CASCADE)");

            // Migración no destructiva para las bases creadas por versiones anteriores.
            asegurarColumna(stmt, "evaluacion", "snellen_izq", "TEXT");
            asegurarColumna(stmt, "evaluacion", "snellen_der", "TEXT");
            asegurarColumna(stmt, "evaluacion", "snellen_ao", "TEXT");
            asegurarColumna(stmt, "evaluacion", "logmar_izq", "REAL");
            asegurarColumna(stmt, "evaluacion", "logmar_der", "REAL");
            asegurarColumna(stmt, "evaluacion", "logmar_ao", "REAL");
            asegurarColumna(stmt, "evaluacion", "dioptrias_izq", "REAL");
            asegurarColumna(stmt, "evaluacion", "dioptrias_der", "REAL");
            asegurarColumna(stmt, "evaluacion", "dioptrias_ao", "REAL");
            asegurarColumna(stmt, "evaluacion", "ishihara_aciertos", "INTEGER");
            asegurarColumna(stmt, "evaluacion", "ishihara_total", "INTEGER");
            asegurarColumna(stmt, "evaluacion", "ishihara_respuestas_deficiencia", "INTEGER");
            asegurarColumna(stmt, "evaluacion", "cumplimiento_distancia_pct", "REAL");
            asegurarColumna(stmt, "evaluacion", "muestras_distancia", "INTEGER");
            asegurarColumna(stmt, "evaluacion", "tiempo_respuesta_ms", "INTEGER");
            asegurarColumna(stmt, "evaluacion", "distancia_pupilar_mm", "REAL");
            asegurarColumna(stmt, "evaluacion", "dp_calibrada", "INTEGER");
            asegurarColumna(stmt, "evaluacion", "metodo_entrada", "TEXT");
            asegurarColumna(stmt, "evaluacion", "distancia_objetivo_m", "REAL");
            asegurarColumna(stmt, "evaluacion", "tolerancia_distancia_cm", "REAL");
            asegurarColumna(stmt, "evaluacion", "alertas_clinicas", "TEXT");
            asegurarColumna(stmt, "evaluacion", "edad_anios", "REAL");
            asegurarColumna(stmt, "evaluacion", "tipo_optotipo", "TEXT");
            asegurarColumna(stmt, "evaluacion", "limite_pantalla_logmar", "REAL");
            asegurarColumna(stmt, "evaluacion", "distancia_real_m", "REAL");
            asegurarColumna(stmt, "evaluacion", "condiciones_prueba", "TEXT");

            // Tabla Citas
            stmt.execute("CREATE TABLE IF NOT EXISTS cita (" +
                    "id_cita INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "curp_paciente TEXT NOT NULL, fecha TEXT NOT NULL, hora TEXT NOT NULL, " +
                    "tipo TEXT NOT NULL, notas TEXT, estado TEXT NOT NULL DEFAULT 'EN_ESPERA', " +
                    "creada_en TEXT NOT NULL, " +
                    "FOREIGN KEY(curp_paciente) REFERENCES paciente(curp) ON DELETE CASCADE ON UPDATE CASCADE)");

            // Tabla Reportes PDF
            stmt.execute("CREATE TABLE IF NOT EXISTS reporte_pdf (" +
                    "id_reporte INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "id_evaluacion INTEGER NOT NULL, " +
                    "ruta TEXT NOT NULL, " +
                    "generado_en TEXT NOT NULL, " +
                    "FOREIGN KEY(id_evaluacion) REFERENCES evaluacion(id_evaluacion) ON DELETE CASCADE)");

            // Índices de rendimiento
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_cita_horario ON cita(fecha, hora)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_evaluacion_paciente_fecha ON evaluacion(curp_paciente, id_evaluacion DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cita_paciente_fecha ON cita(curp_paciente, fecha DESC)");

        } catch (Exception e) {
            System.err.println("[DB ERROR CRÍTICO]: Fallo al inicializar las tablas locales.");
            e.printStackTrace();
        }
    }

    private static void asegurarColumna(Statement stmt, String tabla, String columna, String tipo) throws Exception {
        try (ResultSet columnas = stmt.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (columnas.next()) {
                if (columna.equalsIgnoreCase(columnas.getString("name"))) return;
            }
        }
        stmt.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + tipo);
    }

    // =========================================================================
    // MÓDULO 1: PACIENTES (CRUD)
    // =========================================================================

    public static boolean registrarPaciente(String curp, String nombre, String fechaNacimiento, String gradoGrupo, String curpTutor) {
        if (curp == null || curp.isBlank() || nombre == null || nombre.isBlank()) return false;
        // Si el paciente ya está en un grupo, su grado lo manda el grupo (no se pisa con lo capturado).
        String sql = "INSERT INTO paciente (curp, nombre, fecha_nacimiento, grado_grupo, curp_tutor) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(curp) DO UPDATE SET nombre=excluded.nombre, fecha_nacimiento=excluded.fecha_nacimiento, " +
                "grado_grupo=CASE WHEN paciente.id_grupo IS NULL THEN excluded.grado_grupo ELSE paciente.grado_grupo END, " +
                "curp_tutor=excluded.curp_tutor";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            String curpNorm = curp.trim().toUpperCase();
            pstmt.setString(1, curpNorm);
            pstmt.setString(2, nombre.trim());
            pstmt.setString(3, fechaNacimiento != null ? fechaNacimiento.trim() : "");
            pstmt.setString(4, gradoGrupo != null ? gradoGrupo.trim() : "");
            pstmt.setString(5, curpTutor != null && !curpTutor.isBlank() ? curpTutor.trim().toUpperCase() : null);
            boolean guardado = pstmt.executeUpdate() > 0;
            if (guardado) vincularGrupoPorEtiqueta(conn, curpNorm, gradoGrupo);
            return guardado;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo registrar el paciente: " + e.getMessage());
            return false;
        }
    }

    /**
     * Inscribe automáticamente al paciente sin grupo en el grupo local cuya etiqueta coincide con
     * su grado (p. ej. "3° B - Primaria", tal como llega del portal en la jornada por grupo).
     */
    private static void vincularGrupoPorEtiqueta(Connection conn, String curp, String gradoGrupo) {
        try {
            Integer idGrupo = GestorGrupos.buscarActivoPorEtiqueta(conn, gradoGrupo);
            if (idGrupo == null) return;
            try (PreparedStatement p = conn.prepareStatement(
                    "UPDATE paciente SET id_grupo = ? WHERE curp = ? AND id_grupo IS NULL AND COALESCE(egresado, 0) = 0")) {
                p.setInt(1, idGrupo);
                p.setString(2, curp);
                p.executeUpdate();
            }
        } catch (Exception e) {
            System.err.println("[DB]: No se pudo vincular el paciente a su grupo: " + e.getMessage());
        }
    }

    public static boolean actualizarPaciente(String curpOriginal, String nuevoNombre, String nuevaFechaNac, String nuevoGradoGrupo, String nuevoCurpTutor) {
        if (curpOriginal == null || curpOriginal.isBlank() || nuevoNombre == null || nuevoNombre.isBlank()) return false;
        // Con grupo, el grado lo manda el grupo; sin grupo se guarda lo capturado.
        String sql = "UPDATE paciente SET nombre = ?, fecha_nacimiento = ?, " +
                "grado_grupo = CASE WHEN id_grupo IS NULL THEN ? ELSE grado_grupo END, curp_tutor = ? WHERE curp = ?";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nuevoNombre.trim());
            pstmt.setString(2, nuevaFechaNac != null ? nuevaFechaNac.trim() : "");
            pstmt.setString(3, nuevoGradoGrupo != null ? nuevoGradoGrupo.trim() : "");
            pstmt.setString(4, nuevoCurpTutor != null && !nuevoCurpTutor.isBlank() ? nuevoCurpTutor.trim().toUpperCase() : null);
            pstmt.setString(5, curpOriginal.trim().toUpperCase());
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo actualizar el paciente: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina un paciente y realiza la limpieza en cascada de sus citas, evaluaciones y PDFs.
     */
    /**
     * Elimina un paciente y limpia en orden estricto todos sus registros asociados
     * (PDFs, Evaluaciones, Citas SQL residuales y Citas JSON) dentro de una transacción.
     */
    public static boolean eliminarPaciente(String curp) {
        if (curp == null || curp.isBlank()) return false;
        String curpNorm = curp.trim().toUpperCase();

        // 1. Eliminar citas de la agenda JSON
        try {
            for (CitaInfo cita : GestorAgendaJSON.obtenerCitasPaciente(curpNorm)) {
                GestorAgendaJSON.eliminarCita(cita.idCita);
            }
        } catch (Exception e) {
            System.err.println("[AGENDA JSON]: Advertencia al limpiar citas: " + e.getMessage());
        }

        // 2. Limpieza en cascada en SQLite
        String sqlBorrarPdfs = "DELETE FROM reporte_pdf WHERE id_evaluacion IN (SELECT id_evaluacion FROM evaluacion WHERE curp_paciente = ?)";
        String sqlBorrarEvaluaciones = "DELETE FROM evaluacion WHERE curp_paciente = ?";
        String sqlBorrarCitasSql = "DELETE FROM cita WHERE curp_paciente = ?";
        String sqlBorrarPaciente = "DELETE FROM paciente WHERE curp = ?";

        try (Connection conn = abrirConexion()) {
            conn.setAutoCommit(false); // Iniciar transacción

            try {
                // A. Borrar reportes PDF asociados a las evaluaciones del paciente
                try (PreparedStatement pPdfs = conn.prepareStatement(sqlBorrarPdfs)) {
                    pPdfs.setString(1, curpNorm);
                    pPdfs.executeUpdate();
                }

                // B. Borrar las evaluaciones clínicas del paciente
                try (PreparedStatement pEval = conn.prepareStatement(sqlBorrarEvaluaciones)) {
                    pEval.setString(1, curpNorm);
                    pEval.executeUpdate();
                }

                // C. Borrar posibles citas residuales en la tabla SQLite si existiera
                try (PreparedStatement pCitas = conn.prepareStatement(sqlBorrarCitasSql)) {
                    pCitas.setString(1, curpNorm);
                    pCitas.executeUpdate();
                } catch (SQLException ignored) {
                    // Si la tabla cita no existe o ya fue retirada, continúa sin error
                }

                // D. Finalmente borrar al paciente
                int filasAfectadas;
                try (PreparedStatement pPac = conn.prepareStatement(sqlBorrarPaciente)) {
                    pPac.setString(1, curpNorm);
                    filasAfectadas = pPac.executeUpdate();
                }

                conn.commit(); // Confirmar cambios
                return filasAfectadas > 0;

            } catch (SQLException ex) {
                conn.rollback(); // Revertir si algo falla
                System.err.println("[DB ERROR]: Falló la transacción al eliminar paciente: " + ex.getMessage());
                return false;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo eliminar el paciente: " + e.getMessage());
            return false;
        }
    }

    public static PacienteInfo buscarPacientePorCurp(String curp) {
        if (curp == null || curp.trim().isEmpty()) return null;
        String sql = COLUMNAS_PACIENTE + "WHERE p.curp = ?";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, curp.trim().toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return mapearPaciente(rs);
            }
        } catch (Exception e) {
            System.err.println("[DB ERROR]: Fallo al buscar paciente por CURP: " + e.getMessage());
        }
        return null;
    }

    private static final String COLUMNAS_PACIENTE = "SELECT p.curp, p.nombre, p.fecha_nacimiento, p.grado_grupo, p.curp_tutor, " +
            "p.id_grupo, p.egresado, t.nombre AS nombre_tutor, t.telefono AS telefono_tutor, t.correo AS correo_tutor " +
            "FROM paciente p LEFT JOIN tutor t ON p.curp_tutor = t.curp ";

    /** Consulta de pacientes con un WHERE/ORDER BY propio (lo usan los grupos). Parámetros enteros o texto. */
    static List<PacienteInfo> consultarPacientes(String condicion, Object... parametros) {
        List<PacienteInfo> lista = new ArrayList<>();
        try (Connection conn = abrirConexion();
             PreparedStatement p = conn.prepareStatement(COLUMNAS_PACIENTE + condicion)) {
            for (int i = 0; i < parametros.length; i++) p.setObject(i + 1, parametros[i]);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) lista.add(mapearPaciente(rs));
            }
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudieron consultar los pacientes: " + e.getMessage());
        }
        return lista;
    }

    public static List<PacienteInfo> obtenerPacientes(String textoBusqueda) {
        return obtenerPacientes(textoBusqueda, "", "", "", "", "", "");
    }

    public static List<PacienteInfo> obtenerPacientes(String general, String curp, String nombre, String grupo, String tutor, String telefono, String correo) {
        List<PacienteInfo> pacientes = new ArrayList<>();
        String filtro = general == null ? "" : general.trim();
        String sql = COLUMNAS_PACIENTE +
                "WHERE (? = '' OR p.curp LIKE ? OR p.nombre LIKE ? OR p.grado_grupo LIKE ? OR t.nombre LIKE ? OR t.telefono LIKE ? OR t.correo LIKE ?) " +
                "AND (? = '' OR p.curp LIKE ?) AND (? = '' OR p.nombre LIKE ?) AND (? = '' OR p.grado_grupo LIKE ?) " +
                "AND (? = '' OR t.nombre LIKE ?) AND (? = '' OR t.telefono LIKE ?) AND (? = '' OR t.correo LIKE ?) " +
                "ORDER BY p.nombre COLLATE NOCASE";
        try (Connection conn = abrirConexion();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            String patron = "%" + filtro + "%";
            statement.setString(1, filtro);
            statement.setString(2, patron);
            statement.setString(3, patron);
            statement.setString(4, patron);
            statement.setString(5, patron);
            statement.setString(6, patron);
            statement.setString(7, patron);

            String[] filtros = {curp, nombre, grupo, tutor, telefono, correo};
            int i = 8;
            for (String valor : filtros) {
                String v = valor == null ? "" : valor.trim();
                statement.setString(i++, v);
                statement.setString(i++, "%" + v + "%");
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) pacientes.add(mapearPaciente(rs));
            }
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo listar pacientes: " + e.getMessage());
        }
        return pacientes;
    }

    public static boolean registrarPacienteRapido(String curp, String nombre, String grado, String grupo,
                                                  String tutor, String telefono, String correo) {
        if (curp == null || curp.isBlank() || nombre == null || nombre.isBlank()) return false;
        curp = curp.trim().toUpperCase();
        String curpTutor = tutor == null || tutor.isBlank() ? null : "T-" + curp.substring(Math.max(0, curp.length() - 4));
        try {
            if (curpTutor != null) registrarTutor(curpTutor, tutor.trim(), telefono == null ? "" : telefono.trim(), correo == null ? "" : correo.trim());
            return registrarPaciente(curp, nombre.trim(), "", unirGradoGrupo(grado, grupo), curpTutor);
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // MÓDULO 2: TUTORES / RESPONSABLES (CRUD)
    // =========================================================================

    public static boolean registrarTutor(String curp, String nombre, String telefono, String correo) {
        if (curp == null || curp.isBlank() || nombre == null || nombre.isBlank()) return false;
        String sql = "INSERT INTO tutor (curp, nombre, telefono, correo) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(curp) DO UPDATE SET nombre=excluded.nombre, telefono=excluded.telefono, correo=excluded.correo";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, curp.trim().toUpperCase());
            pstmt.setString(2, nombre.trim());
            pstmt.setString(3, telefono != null ? telefono.trim() : "");
            pstmt.setString(4, correo != null ? correo.trim() : "");
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo registrar el tutor: " + e.getMessage());
            return false;
        }
    }

    public static boolean actualizarTutor(String curp, String nombre, String telefono, String correo) {
        if (curp == null || curp.isBlank() || nombre == null || nombre.isBlank()) return false;
        String sql = "UPDATE tutor SET nombre = ?, telefono = ?, correo = ? WHERE curp = ?";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nombre.trim());
            pstmt.setString(2, telefono != null ? telefono.trim() : "");
            pstmt.setString(3, correo != null ? correo.trim() : "");
            pstmt.setString(4, curp.trim().toUpperCase());
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo actualizar el tutor: " + e.getMessage());
            return false;
        }
    }

    public static boolean eliminarTutor(String curp) {
        if (curp == null || curp.isBlank()) return false;
        String sql = "DELETE FROM tutor WHERE curp = ?";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, curp.trim().toUpperCase());
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo eliminar el tutor: " + e.getMessage());
            return false;
        }
    }

    public static List<PacienteInfo> obtenerTutores() {
        List<PacienteInfo> tutores = new ArrayList<>();
        String sql = "SELECT curp, nombre, telefono, correo FROM tutor ORDER BY nombre COLLATE NOCASE";
        try (Connection conn = abrirConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                PacienteInfo tutor = new PacienteInfo();
                tutor.curp = rs.getString("curp");
                tutor.nombreTutor = rs.getString("nombre");
                tutor.telefonoTutor = rs.getString("telefono");
                tutor.correoTutor = rs.getString("correo");
                tutores.add(tutor);
            }
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudieron consultar los responsables: " + e.getMessage());
        }
        return tutores;
    }

    // =========================================================================
    // MÓDULO 3: CITAS Y AGENDA (Delegación a GestorAgendaJSON)
    // =========================================================================

    public static boolean agendarCita(String curpPaciente, LocalDate fecha, String hora, String tipo, String notas) {
        return GestorAgendaJSON.agendarCita(curpPaciente, fecha, hora, tipo, notas);
    }

    public static boolean actualizarCita(int idCita, LocalDate fecha, String hora, String tipo, String notas, String estado) {
        return GestorAgendaJSON.actualizarCita(idCita, fecha, hora, tipo, notas, estado);
    }

    public static boolean actualizarEstadoCita(int idCita, String estado) {
        return GestorAgendaJSON.actualizarEstadoCita(idCita, estado);
    }

    public static boolean eliminarCita(int idCita) {
        return GestorAgendaJSON.eliminarCita(idCita);
    }

    public static List<CitaInfo> obtenerCitas(LocalDate fecha) {
        return GestorAgendaJSON.obtenerCitas(fecha);
    }

    public static List<CitaInfo> obtenerCitasProximas(LocalDate desdeFecha) {
        return GestorAgendaJSON.obtenerCitasProximas(desdeFecha);
    }

    public static List<CitaInfo> obtenerCitasPaciente(String curpPaciente) {
        return GestorAgendaJSON.obtenerCitasPaciente(curpPaciente);
    }

    public static List<CitaInfo> obtenerTodasLasCitas() {
        return GestorAgendaJSON.obtenerTodasLasCitas();
    }

    // =========================================================================
    // MÓDULO 4: EVALUACIONES CLÍNICAS (CRUD)
    // =========================================================================

    /** Registra la evaluación clínica estructurada (Sobrecarga principal). */
    public static boolean registrarEvaluacionClinica(String curpPaciente, String fechaHora, ResultadoClinico resultado) {
        if (resultado == null || !resultado.estaCompleto() || curpPaciente == null || curpPaciente.isBlank()) return false;
        ResultadoClinico.ResultadoOjo oi = resultado.getOjoIzquierdo();
        ResultadoClinico.ResultadoOjo od = resultado.getOjoDerecho();
        ResultadoClinico.ResultadoOjo ao = resultado.getAmbosOjos();

        String sql = "INSERT INTO evaluacion (" +
                "curp_paciente, fecha_hora, agudeza_izq, agudeza_der, resultado_ishihara, tasa_ruido_wer, estado_recomendacion, " +
                "snellen_izq, snellen_der, snellen_ao, logmar_izq, logmar_der, logmar_ao, dioptrias_izq, dioptrias_der, dioptrias_ao, " +
                "ishihara_aciertos, ishihara_total, ishihara_respuestas_deficiencia, cumplimiento_distancia_pct, " +
                "muestras_distancia, tiempo_respuesta_ms, distancia_pupilar_mm, dp_calibrada, metodo_entrada, distancia_objetivo_m, tolerancia_distancia_cm, alertas_clinicas, " +
                "edad_anios, tipo_optotipo, limite_pantalla_logmar, distancia_real_m, condiciones_prueba" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, curpPaciente.trim().toUpperCase());
            pstmt.setString(2, fechaHora);
            pstmt.setString(3, oi == null ? null : oi.getSnellen());
            pstmt.setString(4, od == null ? null : od.getSnellen());
            pstmt.setString(5, resultado.getIshiharaFormateado());
            pstmt.setNull(6, Types.REAL);
            pstmt.setString(7, resultado.getRecomendacion());
            pstmt.setString(8, oi == null ? null : oi.getSnellen());
            pstmt.setString(9, od == null ? null : od.getSnellen());
            pstmt.setString(10, ao == null ? null : ao.getSnellen());
            setNullableDouble(pstmt, 11, oi == null ? null : oi.getLogMar());
            setNullableDouble(pstmt, 12, od == null ? null : od.getLogMar());
            setNullableDouble(pstmt, 13, ao == null ? null : ao.getLogMar());
            setNullableDouble(pstmt, 14, oi == null ? null : oi.getDioptriasEgger());
            setNullableDouble(pstmt, 15, od == null ? null : od.getDioptriasEgger());
            setNullableDouble(pstmt, 16, ao == null ? null : ao.getDioptriasEgger());
            pstmt.setInt(17, resultado.getIshiharaAciertos());
            pstmt.setInt(18, resultado.getIshiharaTotal());
            pstmt.setInt(19, resultado.getIshiharaRespuestasDeficiencia());
            setNullableDouble(pstmt, 20, resultado.getCumplimientoDistanciaPct());
            pstmt.setInt(21, resultado.getMuestrasDistancia());
            if (resultado.getTiempoRespuestaPromedioMs() < 0) pstmt.setNull(22, Types.INTEGER);
            else pstmt.setLong(22, resultado.getTiempoRespuestaPromedioMs());
            setNullableDouble(pstmt, 23, resultado.getDistanciaPupilarMm());
            pstmt.setInt(24, resultado.isDistanciaPupilarCalibrada() ? 1 : 0);
            pstmt.setString(25, resultado.getMetodoEntrada());
            setNullableDouble(pstmt, 26, resultado.getDistanciaObjetivoMetros());
            setNullableDouble(pstmt, 27, resultado.getToleranciaDistanciaCm());
            pstmt.setString(28, resultado.getAlertasComoTexto());
            com.eyesofter.clinica.CondicionesPrueba c = resultado.getCondiciones();
            setNullableDouble(pstmt, 29, resultado.getEdadAnios());
            pstmt.setString(30, c == null ? null : c.tipoOptotipo);
            setNullableDouble(pstmt, 31, c == null ? null : c.logMarLimitePantalla);
            setNullableDouble(pstmt, 32, c == null ? null : c.distanciaRealPromedioM);
            pstmt.setString(33, c == null ? null : c.resumen());
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo registrar la evaluación clínica estructurada: " + e.getMessage());
            return false;
        }
    }

    /** Sobrecarga compatible con versiones anteriores del evaluador. */
    public static boolean registrarEvaluacion(String curpPaciente, String fechaHora, String agudezaIzq,
                                              String agudezaDer, String resultadoIshihara, double wer, String recomendacion) {
        String sql = "INSERT INTO evaluacion (curp_paciente, fecha_hora, agudeza_izq, agudeza_der, resultado_ishihara, tasa_ruido_wer, estado_recomendacion) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, curpPaciente);
            pstmt.setString(2, fechaHora);
            pstmt.setString(3, agudezaIzq);
            pstmt.setString(4, agudezaDer);
            pstmt.setString(5, resultadoIshihara);
            pstmt.setDouble(6, wer);
            pstmt.setString(7, recomendacion);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo registrar la evaluación clínica: " + e.getMessage());
            return false;
        }
    }

    public static boolean actualizarRecomendacionEvaluacion(int idEvaluacion, String nuevaRecomendacion, String nuevasAlertas) {
        if (idEvaluacion <= 0) return false;
        String sql = "UPDATE evaluacion SET estado_recomendacion = ?, alertas_clinicas = ? WHERE id_evaluacion = ?";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, nuevaRecomendacion);
            pstmt.setString(2, nuevasAlertas);
            pstmt.setInt(3, idEvaluacion);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo actualizar la evaluación: " + e.getMessage());
            return false;
        }
    }

    public static boolean eliminarEvaluacion(int idEvaluacion) {
        if (idEvaluacion <= 0) return false;
        String sql = "DELETE FROM evaluacion WHERE id_evaluacion = ?";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idEvaluacion);
            return pstmt.executeUpdate() > 0;
        } catch (Exception e) {
            System.err.println("[DB ERROR]: No se pudo eliminar la evaluación: " + e.getMessage());
            return false;
        }
    }

    public static List<EvaluacionInfo> obtenerHistorialEvaluaciones() {
        List<EvaluacionInfo> lista = new ArrayList<>();
        String sql = "SELECT e.*, p.nombre AS nombre_paciente FROM evaluacion e " +
                "LEFT JOIN paciente p ON e.curp_paciente = p.curp ORDER BY e.id_evaluacion DESC";
        try (Connection conn = abrirConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                EvaluacionInfo info = new EvaluacionInfo();
                info.idEvaluacion = rs.getInt("id_evaluacion");
                info.curpPaciente = rs.getString("curp_paciente");
                info.nombrePaciente = rs.getString("nombre_paciente");
                info.fechaHora = rs.getString("fecha_hora");
                info.agudezaIzq = rs.getString("agudeza_izq");
                info.agudezaDer = rs.getString("agudeza_der");
                info.resultadoIshihara = rs.getString("resultado_ishihara");
                info.tasaRuidoWer = rs.getDouble("tasa_ruido_wer");
                info.recomendacion = rs.getString("estado_recomendacion");
                llenarCamposClinicos(rs, info);
                lista.add(info);
            }
        } catch (Exception e) {
            System.err.println("[DB ERROR]: Fallo al obtener el historial de evaluaciones: " + e.getMessage());
        }
        return lista;
    }

    public static List<EvaluacionInfo> obtenerEvaluacionesPaciente(String curpPaciente) {
        List<EvaluacionInfo> evaluaciones = new ArrayList<>();
        for (EvaluacionInfo evaluacion : obtenerHistorialEvaluaciones()) {
            if (curpPaciente != null && curpPaciente.equalsIgnoreCase(evaluacion.curpPaciente)) {
                evaluaciones.add(evaluacion);
            }
        }
        return evaluaciones;
    }

    public static EvaluacionInfo obtenerUltimaEvaluacion(String curpPaciente) {
        String sql = "SELECT * FROM evaluacion WHERE curp_paciente = ? ORDER BY id_evaluacion DESC LIMIT 1";
        try (Connection conn = abrirConexion();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, curpPaciente.trim().toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    EvaluacionInfo info = new EvaluacionInfo();
                    info.idEvaluacion = rs.getInt("id_evaluacion");
                    info.curpPaciente = rs.getString("curp_paciente");
                    info.fechaHora = rs.getString("fecha_hora");
                    info.agudezaIzq = rs.getString("agudeza_izq");
                    info.agudezaDer = rs.getString("agudeza_der");
                    info.resultadoIshihara = rs.getString("resultado_ishihara");
                    info.tasaRuidoWer = rs.getDouble("tasa_ruido_wer");
                    info.recomendacion = rs.getString("estado_recomendacion");
                    llenarCamposClinicos(rs, info);
                    return info;
                }
            }
        } catch (Exception e) {
            System.err.println("[DB ERROR]: Fallo al obtener la última evaluación: " + e.getMessage());
        }
        return null;
    }

    // =========================================================================
    // MÓDULO 5: REPORTES PDF
    // =========================================================================

    public static void registrarReportePdf(int idEvaluacion, String ruta) {
        if (idEvaluacion <= 0 || ruta == null || ruta.isBlank()) return;
        String sql = "INSERT INTO reporte_pdf (id_evaluacion, ruta, generado_en) VALUES (?, ?, ?)";
        try (Connection c = abrirConexion();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, idEvaluacion);
            p.setString(2, ruta);
            p.setString(3, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            p.executeUpdate();
        } catch (Exception e) {
            System.err.println("[DB] No se pudo registrar el PDF: " + e.getMessage());
        }
    }

    // =========================================================================
    // AUXILIARES, DIAGNÓSTICO Y DTOs
    // =========================================================================

    public static void mostrarTodosLosDatos() {
        System.out.println("\n=========================================");
        System.out.println("   EXTRACCIÓN DE DATOS - EYESOFTER DB");
        System.out.println("=========================================");

        try (Connection conn = abrirConexion();
             Statement stmt = conn.createStatement()) {

            // 1. Mostrar Tutores
            System.out.println("\n--- TABLA: TUTOR ---");
            ResultSet rsTutor = stmt.executeQuery("SELECT * FROM tutor");
            while (rsTutor.next()) {
                System.out.printf("CURP: %s | Nombre: %s | Tel: %s | Correo: %s%n",
                        rsTutor.getString("curp"),
                        rsTutor.getString("nombre"),
                        rsTutor.getString("telefono"),
                        rsTutor.getString("correo"));
            }

            // 2. Mostrar Pacientes
            System.out.println("\n--- TABLA: PACIENTE ---");
            ResultSet rsPaciente = stmt.executeQuery("SELECT * FROM paciente");
            while (rsPaciente.next()) {
                System.out.printf("CURP: %s | Nombre: %s | Nacimiento: %s | Grado: %s | CURP Tutor: %s%n",
                        rsPaciente.getString("curp"),
                        rsPaciente.getString("nombre"),
                        rsPaciente.getString("fecha_nacimiento"),
                        rsPaciente.getString("grado_grupo"),
                        rsPaciente.getString("curp_tutor"));
            }

            // 3. Mostrar Evaluaciones
            System.out.println("\n--- TABLA: EVALUACION ---");
            ResultSet rsEval = stmt.executeQuery("SELECT * FROM evaluacion");
            while (rsEval.next()) {
                System.out.printf("ID: %d | Paciente: %s | Fecha: %s | Agudeza (Izq/Der): %s / %s | Ishihara: %s | WER: %.2f | Rec: %s%n",
                        rsEval.getInt("id_evaluacion"),
                        rsEval.getString("curp_paciente"),
                        rsEval.getString("fecha_hora"),
                        rsEval.getString("agudeza_izq"),
                        rsEval.getString("agudeza_der"),
                        rsEval.getString("resultado_ishihara"),
                        rsEval.getDouble("tasa_ruido_wer"),
                        rsEval.getString("estado_recomendacion"));
            }

            System.out.println("\n=========================================\n");

        } catch (Exception e) {
            System.err.println("[DB ERROR]: Fallo al consultar los datos de la base de datos.");
            e.printStackTrace();
        }
    }

    public static int contarPacientes() { return contarRegistros("paciente"); }
    public static int contarEvaluaciones() { return contarRegistros("evaluacion"); }
    public static String siguienteFolioPaciente() { return String.format("P-%04d", contarPacientes() + 1); }

    private static int contarRegistros(String tabla) {
        try (Connection conn = abrirConexion();
             Statement consulta = conn.createStatement();
             ResultSet resultado = consulta.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return resultado.next() ? resultado.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String unirGradoGrupo(String grado, String grupo) {
        String g = grado == null ? "" : grado.trim();
        String grupoLimpio = grupo == null ? "" : grupo.trim();
        return grupoLimpio.isBlank() ? g : g + " · " + grupoLimpio;
    }

    private static void setNullableDouble(PreparedStatement pstmt, int indice, Double valor) throws Exception {
        if (valor == null || !Double.isFinite(valor)) pstmt.setNull(indice, Types.REAL);
        else pstmt.setDouble(indice, valor);
    }

    private static Double getNullableDouble(ResultSet rs, String columna) throws Exception {
        double valor = rs.getDouble(columna);
        return rs.wasNull() ? null : valor;
    }

    private static PacienteInfo mapearPaciente(ResultSet rs) throws SQLException {
        PacienteInfo info = new PacienteInfo();
        info.curp = rs.getString("curp");
        info.nombre = rs.getString("nombre");
        info.fechaNacimiento = rs.getString("fecha_nacimiento");
        info.gradoGrupo = rs.getString("grado_grupo");
        info.curpTutor = rs.getString("curp_tutor");
        info.nombreTutor = rs.getString("nombre_tutor");
        info.telefonoTutor = rs.getString("telefono_tutor");
        info.correoTutor = rs.getString("correo_tutor");
        int idGrupo = rs.getInt("id_grupo");
        info.idGrupo = rs.wasNull() ? null : idGrupo;
        info.egresado = rs.getInt("egresado") == 1;
        return info;
    }

    private static CitaInfo leerCita(ResultSet rs) throws Exception {
        CitaInfo cita = new CitaInfo();
        cita.idCita = rs.getInt("id_cita");
        cita.curpPaciente = rs.getString("curp_paciente");
        cita.nombrePaciente = rs.getString("nombre_paciente");
        cita.fecha = rs.getString("fecha");
        cita.hora = rs.getString("hora");
        cita.tipo = rs.getString("tipo");
        cita.notas = rs.getString("notas");
        cita.estado = rs.getString("estado");
        return cita;
    }

    private static void llenarCamposClinicos(ResultSet rs, EvaluacionInfo info) throws Exception {
        info.snellenIzq = rs.getString("snellen_izq");
        info.snellenDer = rs.getString("snellen_der");
        info.snellenAo = rs.getString("snellen_ao");
        info.logmarIzq = getNullableDouble(rs, "logmar_izq");
        info.logmarDer = getNullableDouble(rs, "logmar_der");
        info.logmarAo = getNullableDouble(rs, "logmar_ao");
        info.dioptriasIzq = getNullableDouble(rs, "dioptrias_izq");
        info.dioptriasDer = getNullableDouble(rs, "dioptrias_der");
        info.dioptriasAo = getNullableDouble(rs, "dioptrias_ao");
        info.ishiharaAciertos = rs.getInt("ishihara_aciertos");
        info.ishiharaTotal = rs.getInt("ishihara_total");
        info.ishiharaRespuestasDeficiencia = rs.getInt("ishihara_respuestas_deficiencia");
        info.cumplimientoDistanciaPct = getNullableDouble(rs, "cumplimiento_distancia_pct");
        info.muestrasDistancia = rs.getInt("muestras_distancia");
        long respuestaMs = rs.getLong("tiempo_respuesta_ms");
        info.tiempoRespuestaMs = rs.wasNull() ? null : respuestaMs;
        info.distanciaPupilarMm = getNullableDouble(rs, "distancia_pupilar_mm");
        info.dpCalibrada = rs.getInt("dp_calibrada") == 1;
        info.metodoEntrada = rs.getString("metodo_entrada");
        info.distanciaObjetivoMetros = getNullableDouble(rs, "distancia_objetivo_m");
        info.toleranciaDistanciaCm = getNullableDouble(rs, "tolerancia_distancia_cm");
        info.alertasClinicas = rs.getString("alertas_clinicas");
        info.edadAnios = getNullableDouble(rs, "edad_anios");
        info.tipoOptotipo = rs.getString("tipo_optotipo");
        info.limitePantallaLogmar = getNullableDouble(rs, "limite_pantalla_logmar");
        info.distanciaRealMetros = getNullableDouble(rs, "distancia_real_m");
        info.condicionesPrueba = rs.getString("condiciones_prueba");
    }

    // =========================================================================
    // DTOs PARA LA INTERFAZ DE USUARIO (JAVAFX)
    // =========================================================================

    public static class PacienteInfo {
        public String curp, nombre, fechaNacimiento, gradoGrupo, curpTutor;
        public String nombreTutor, telefonoTutor, correoTutor;
        /** Grupo escolar local (null = sin grupo). */
        public Integer idGrupo;
        /** Terminó el último grado de su nivel (su grupo egresó). */
        public boolean egresado;
    }

    public static class EvaluacionInfo {
        public int idEvaluacion;
        public String curpPaciente, nombrePaciente, fechaHora, agudezaIzq, agudezaDer, resultadoIshihara, recomendacion;
        public double tasaRuidoWer;
        public String snellenIzq, snellenDer, snellenAo, alertasClinicas, metodoEntrada;
        public Double logmarIzq, logmarDer, logmarAo, dioptriasIzq, dioptriasDer, dioptriasAo, distanciaObjetivoMetros, toleranciaDistanciaCm;
        public int ishiharaAciertos, ishiharaTotal, ishiharaRespuestasDeficiencia, muestrasDistancia;
        public Double cumplimientoDistanciaPct, distanciaPupilarMm;
        public Long tiempoRespuestaMs;
        public boolean dpCalibrada;
        public Double edadAnios, limitePantallaLogmar, distanciaRealMetros;
        public String tipoOptotipo, condicionesPrueba;
    }

    public static class CitaInfo {
        public int idCita;
        public String curpPaciente, nombrePaciente, fecha, hora, tipo, notas, estado;
    }
}