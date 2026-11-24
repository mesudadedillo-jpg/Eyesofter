package com.eyesofter.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grupos escolares de la base local: registrar un grupo, inscribir a sus pacientes y
 * "pasar de año" (un grupo o todos) sin editar paciente por paciente.
 *
 * <p>Cada paciente inscrito guarda en {@code grado_grupo} la etiqueta del grupo con el mismo
 * formato que usa el portal ({@code "3° B - Primaria"}), así la ficha, el PDF y la jornada
 * siguen funcionando igual. En el último grado del nivel el grupo egresa: se cierra y sus
 * pacientes quedan marcados como egresados, con su historial intacto.</p>
 */
public final class GestorGrupos {
    private GestorGrupos() { }

    public static final String[] NIVELES = {"Preescolar", "Primaria", "Secundaria"};

    /** Un grupo no puede pasar de año dos veces en menos de este tiempo (evita repeticiones por error). */
    public static final int DIAS_ENTRE_PROMOCIONES = 180;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Datos de un grupo para las pantallas. */
    public record GrupoInfo(int id, String nivel, int grado, String letra, String cicloEscolar, String docente,
                            boolean activo, String promovidoEn, int pacientes) {
        public String etiqueta() { return GestorGrupos.etiqueta(nivel, grado, letra); }
        public boolean esUltimoGrado() { return grado >= gradoMaximo(nivel); }
        @Override public String toString() { return etiqueta() + " · " + cicloEscolar; }
    }

    /** Resultado de pasar de año: qué se movió y, si no se pudo, por qué (en ese caso no cambia nada). */
    public record Resultado(int gruposPromovidos, int gruposEgresados, int pacientesMovidos, int pacientesEgresados, List<String> avisos) {
        public boolean exito() { return avisos.isEmpty(); }
    }

    // =========================================================================
    // ESQUEMA
    // =========================================================================

    /** Crea la tabla de grupos y agrega a {@code paciente} las columnas id_grupo y egresado. */
    static void crearEsquema(Statement stmt) throws SQLException {
        stmt.execute("CREATE TABLE IF NOT EXISTS grupo (" +
                "id_grupo INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "nivel TEXT NOT NULL, " +
                "grado INTEGER NOT NULL, " +
                "letra TEXT NOT NULL, " +
                "ciclo_escolar TEXT NOT NULL, " +
                "docente TEXT, " +
                "activo INTEGER NOT NULL DEFAULT 1, " +
                "promovido_en TEXT)");
        // Sólo un 3° B de primaria activo; los grupos egresados quedan como historial.
        stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_grupo_activo ON grupo(nivel, grado, letra) WHERE activo = 1");
        asegurarColumna(stmt, "paciente", "id_grupo", "INTEGER REFERENCES grupo(id_grupo) ON DELETE SET NULL");
        asegurarColumna(stmt, "paciente", "egresado", "INTEGER NOT NULL DEFAULT 0");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_paciente_grupo ON paciente(id_grupo)");
    }

    private static void asegurarColumna(Statement stmt, String tabla, String columna, String definicion) throws SQLException {
        try (ResultSet columnas = stmt.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (columnas.next()) {
                if (columna.equalsIgnoreCase(columnas.getString("name"))) return;
            }
        }
        stmt.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
    }

    // =========================================================================
    // REGLAS
    // =========================================================================

    public static int gradoMaximo(String nivel) {
        String n = nivel == null ? "" : nivel.trim().toLowerCase(Locale.ROOT);
        return switch (n) {
            case "primaria" -> 6;
            default -> 3; // preescolar y secundaria
        };
    }

    /** Mismo formato que el portal: "3° B - Primaria". */
    public static String etiqueta(String nivel, int grado, String letra) {
        return grado + "° " + letra + (nivel == null || nivel.isBlank() ? "" : " - " + nivel);
    }

    /** Ciclo escolar vigente en una fecha; el ciclo nuevo empieza en agosto. */
    public static String cicloActual(LocalDate fecha) {
        int inicio = fecha.getMonthValue() >= 8 ? fecha.getYear() : fecha.getYear() - 1;
        return inicio + "-" + (inicio + 1);
    }

    public static String cicloActual() { return cicloActual(LocalDate.now()); }

    /** "2025-2026" → "2026-2027"; si el texto no tiene ese formato, el ciclo actual. */
    public static String cicloSiguiente(String ciclo) {
        if (ciclo != null && ciclo.matches("\\d{4}-\\d{4}")) {
            int inicio = Integer.parseInt(ciclo.substring(0, 4)) + 1;
            return inicio + "-" + (inicio + 1);
        }
        return cicloActual();
    }

    /** Valida los datos de un grupo. Devuelve el mensaje de error o null si están bien. */
    public static String validar(String nivel, int grado, String letra, String ciclo) {
        if (nivel == null || nivel.isBlank()) return "Elija el nivel.";
        if (grado < 1 || grado > gradoMaximo(nivel)) return "En " + nivel.toLowerCase(Locale.ROOT) + " el grado va de 1 a " + gradoMaximo(nivel) + ".";
        if (letra == null || !letra.trim().toUpperCase(Locale.ROOT).matches("[A-Z]")) return "El grupo debe ser una letra (A, B, C…).";
        if (ciclo == null || !ciclo.trim().matches("\\d{4}-\\d{4}")) return "Escriba el ciclo escolar como 2026-2027.";
        return null;
    }

    // =========================================================================
    // CONSULTAS
    // =========================================================================

    public static List<GrupoInfo> listar(boolean activos) {
        String sql = "SELECT g.*, (SELECT COUNT(*) FROM paciente p WHERE p.id_grupo = g.id_grupo AND (p.egresado = 0 OR g.activo = 0)) AS pacientes " +
                "FROM grupo g WHERE g.activo = ? ORDER BY " + ordenNivel("g.nivel") + ", g.grado, g.letra, g.ciclo_escolar DESC";
        List<GrupoInfo> lista = new ArrayList<>();
        try (Connection c = GestorBaseDatos.abrirConexion(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, activos ? 1 : 0);
            try (ResultSet rs = p.executeQuery()) {
                while (rs.next()) lista.add(leer(rs));
            }
        } catch (Exception e) {
            System.err.println("[GRUPOS]: No se pudieron listar los grupos: " + e.getMessage());
        }
        return lista;
    }

    public static GrupoInfo buscar(int idGrupo) {
        try (Connection c = GestorBaseDatos.abrirConexion()) {
            return buscar(c, idGrupo);
        } catch (Exception e) {
            return null;
        }
    }

    private static GrupoInfo buscar(Connection c, int idGrupo) throws SQLException {
        String sql = "SELECT g.*, (SELECT COUNT(*) FROM paciente p WHERE p.id_grupo = g.id_grupo AND (p.egresado = 0 OR g.activo = 0)) AS pacientes " +
                "FROM grupo g WHERE g.id_grupo = ?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, idGrupo);
            try (ResultSet rs = p.executeQuery()) {
                return rs.next() ? leer(rs) : null;
            }
        }
    }

    /** Grupo activo cuya etiqueta coincide (p. ej. "3° B - Primaria", como la manda el portal). */
    public static Integer buscarActivoPorEtiqueta(Connection c, String etiqueta) throws SQLException {
        if (etiqueta == null || etiqueta.isBlank()) return null;
        String buscada = etiqueta.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id_grupo, nivel, grado, letra FROM grupo WHERE activo = 1")) {
            while (rs.next()) {
                String e = etiqueta(rs.getString("nivel"), rs.getInt("grado"), rs.getString("letra")).toLowerCase(Locale.ROOT);
                if (e.equals(buscada)) return rs.getInt("id_grupo");
            }
        }
        return null;
    }

    /** Pacientes del grupo (en un grupo egresado, también los egresados). */
    public static List<GestorBaseDatos.PacienteInfo> pacientesDelGrupo(int idGrupo) {
        return GestorBaseDatos.consultarPacientes("WHERE p.id_grupo = ? ORDER BY p.nombre COLLATE NOCASE", idGrupo);
    }

    /** Pacientes que se pueden inscribir: no egresados y que no están en este grupo (primero los que no tienen grupo). */
    public static List<GestorBaseDatos.PacienteInfo> candidatos(int idGrupo) {
        return GestorBaseDatos.consultarPacientes(
                "WHERE COALESCE(p.egresado, 0) = 0 AND (p.id_grupo IS NULL OR p.id_grupo <> ?) " +
                "ORDER BY (p.id_grupo IS NOT NULL), p.nombre COLLATE NOCASE", idGrupo);
    }

    public static int contarSinGrupo() {
        try (Connection c = GestorBaseDatos.abrirConexion(); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM paciente WHERE id_grupo IS NULL AND COALESCE(egresado, 0) = 0")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // =========================================================================
    // ALTAS Y CAMBIOS
    // =========================================================================

    /** Registra un grupo. Devuelve su id. Lanza IllegalArgumentException con un mensaje para mostrar. */
    public static int crear(String nivel, int grado, String letra, String ciclo, String docente) {
        String error = validar(nivel, grado, letra, ciclo);
        if (error != null) throw new IllegalArgumentException(error);
        String l = letra.trim().toUpperCase(Locale.ROOT);
        try (Connection c = GestorBaseDatos.abrirConexion()) {
            if (existeActivo(c, nivel, grado, l, 0)) throw new IllegalArgumentException("Ya existe el grupo " + etiqueta(nivel, grado, l) + ".");
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO grupo (nivel, grado, letra, ciclo_escolar, docente, activo) VALUES (?, ?, ?, ?, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                p.setString(1, nivel.trim());
                p.setInt(2, grado);
                p.setString(3, l);
                p.setString(4, ciclo.trim());
                p.setString(5, docente == null || docente.isBlank() ? null : docente.trim());
                p.executeUpdate();
                try (ResultSet k = p.getGeneratedKeys()) {
                    return k.next() ? k.getInt(1) : -1;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo registrar el grupo: " + e.getMessage(), e);
        }
    }

    /** Cambia los datos del grupo; sus pacientes se actualizan con él. */
    public static void actualizar(int idGrupo, String nivel, int grado, String letra, String ciclo, String docente) {
        String error = validar(nivel, grado, letra, ciclo);
        if (error != null) throw new IllegalArgumentException(error);
        String l = letra.trim().toUpperCase(Locale.ROOT);
        try (Connection c = GestorBaseDatos.abrirConexion()) {
            GrupoInfo actual = buscar(c, idGrupo);
            if (actual == null) throw new IllegalArgumentException("El grupo ya no existe.");
            if (actual.activo() && existeActivo(c, nivel, grado, l, idGrupo))
                throw new IllegalArgumentException("Ya existe el grupo " + etiqueta(nivel, grado, l) + ".");
            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE grupo SET nivel = ?, grado = ?, letra = ?, ciclo_escolar = ?, docente = ? WHERE id_grupo = ?")) {
                    p.setString(1, nivel.trim());
                    p.setInt(2, grado);
                    p.setString(3, l);
                    p.setString(4, ciclo.trim());
                    p.setString(5, docente == null || docente.isBlank() ? null : docente.trim());
                    p.setInt(6, idGrupo);
                    p.executeUpdate();
                }
                if (actual.activo()) sincronizarPacientes(c, idGrupo, etiqueta(nivel, grado, l));
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo actualizar el grupo: " + e.getMessage(), e);
        }
    }

    /** Elimina el grupo; sus pacientes NO se borran: quedan sin grupo con su grado actual. */
    public static boolean eliminar(int idGrupo) {
        try (Connection c = GestorBaseDatos.abrirConexion()) {
            c.setAutoCommit(false);
            try (PreparedStatement q = c.prepareStatement("UPDATE paciente SET id_grupo = NULL WHERE id_grupo = ?");
                 PreparedStatement d = c.prepareStatement("DELETE FROM grupo WHERE id_grupo = ?")) {
                q.setInt(1, idGrupo);
                q.executeUpdate();
                d.setInt(1, idGrupo);
                int n = d.executeUpdate();
                c.commit();
                return n > 0;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[GRUPOS]: No se pudo eliminar el grupo: " + e.getMessage());
            return false;
        }
    }

    /** Inscribe pacientes en el grupo (si estaban en otro, se cambian). Devuelve cuántos se movieron. */
    public static int inscribir(int idGrupo, List<String> curps) {
        if (curps == null || curps.isEmpty()) return 0;
        try (Connection c = GestorBaseDatos.abrirConexion()) {
            GrupoInfo g = buscar(c, idGrupo);
            if (g == null || !g.activo()) throw new IllegalArgumentException("El grupo ya egresó o no existe.");
            c.setAutoCommit(false);
            int total = 0;
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE paciente SET id_grupo = ?, grado_grupo = ?, egresado = 0 WHERE curp = ?")) {
                for (String curp : curps) {
                    if (curp == null || curp.isBlank()) continue;
                    p.setInt(1, idGrupo);
                    p.setString(2, g.etiqueta());
                    p.setString(3, curp.trim().toUpperCase(Locale.ROOT));
                    total += p.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
            return total;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo inscribir: " + e.getMessage(), e);
        }
    }

    /** Saca al paciente de su grupo (conserva el grado que tenía). */
    public static boolean quitar(String curp) {
        try (Connection c = GestorBaseDatos.abrirConexion();
             PreparedStatement p = c.prepareStatement("UPDATE paciente SET id_grupo = NULL WHERE curp = ?")) {
            p.setString(1, curp.trim().toUpperCase(Locale.ROOT));
            return p.executeUpdate() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // PASAR DE AÑO
    // =========================================================================

    /** Pasa de año un grupo: sube un grado con sus pacientes; en el último grado, egresa. */
    public static Resultado promover(int idGrupo) { return promover(idGrupo, LocalDateTime.now()); }

    static Resultado promover(int idGrupo, LocalDateTime ahora) {
        try (Connection c = GestorBaseDatos.abrirConexion()) {
            GrupoInfo g = buscar(c, idGrupo);
            if (g == null) return fallo("El grupo ya no existe.");
            c.setAutoCommit(false);
            try {
                List<String> avisos = new ArrayList<>();
                Resultado r = promoverUno(c, g, ahora, avisos);
                if (avisos.isEmpty()) c.commit(); else c.rollback();
                return r;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return fallo("No se pudo pasar de año: " + e.getMessage());
        }
    }

    /**
     * Pasa de año TODOS los grupos activos (o los de un nivel). Va del grado más alto al más
     * bajo para que el 6° A egrese antes de que el 5° A ocupe su lugar. Es todo o nada.
     */
    public static Resultado promoverTodos(String nivel) { return promoverTodos(nivel, LocalDateTime.now()); }

    static Resultado promoverTodos(String nivel, LocalDateTime ahora) {
        List<GrupoInfo> grupos = new ArrayList<>();
        for (GrupoInfo g : listar(true)) if (nivel == null || nivel.isBlank() || nivel.equalsIgnoreCase(g.nivel())) grupos.add(g);
        if (grupos.isEmpty()) return fallo("No hay grupos activos para pasar de año.");
        grupos.sort((a, b) -> a.nivel().equalsIgnoreCase(b.nivel())
                ? (a.grado() != b.grado() ? Integer.compare(b.grado(), a.grado()) : a.letra().compareTo(b.letra()))
                : a.nivel().compareToIgnoreCase(b.nivel()));

        List<String> avisos = new ArrayList<>();
        for (GrupoInfo g : grupos) {
            String previo = avisoYaPromovido(g, ahora);
            if (previo != null) avisos.add(previo);
        }
        if (!avisos.isEmpty()) return new Resultado(0, 0, 0, 0, avisos);

        try (Connection c = GestorBaseDatos.abrirConexion()) {
            c.setAutoCommit(false);
            int promovidos = 0, egresados = 0, movidos = 0, pacEgresados = 0;
            try {
                for (GrupoInfo g : grupos) {
                    Resultado r = promoverUno(c, buscar(c, g.id()), ahora, avisos);
                    if (!avisos.isEmpty()) break;
                    promovidos += r.gruposPromovidos();
                    egresados += r.gruposEgresados();
                    movidos += r.pacientesMovidos();
                    pacEgresados += r.pacientesEgresados();
                }
                if (!avisos.isEmpty()) {
                    c.rollback();
                    return new Resultado(0, 0, 0, 0, avisos);
                }
                c.commit();
                return new Resultado(promovidos, egresados, movidos, pacEgresados, avisos);
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            return fallo("No se pudo pasar de año: " + e.getMessage());
        }
    }

    private static Resultado promoverUno(Connection c, GrupoInfo g, LocalDateTime ahora, List<String> avisos) throws SQLException {
        if (g == null) { avisos.add("El grupo ya no existe."); return new Resultado(0, 0, 0, 0, avisos); }
        if (!g.activo()) { avisos.add(g.etiqueta() + " ya egresó."); return new Resultado(0, 0, 0, 0, avisos); }
        String previo = avisoYaPromovido(g, ahora);
        if (previo != null) { avisos.add(previo); return new Resultado(0, 0, 0, 0, avisos); }

        String fecha = ahora.format(ISO);
        if (g.esUltimoGrado()) {
            try (PreparedStatement p = c.prepareStatement("UPDATE grupo SET activo = 0, promovido_en = ? WHERE id_grupo = ?")) {
                p.setString(1, fecha);
                p.setInt(2, g.id());
                p.executeUpdate();
            }
            int n;
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE paciente SET egresado = 1, grado_grupo = ? WHERE id_grupo = ? AND COALESCE(egresado, 0) = 0")) {
                p.setString(1, "Egresado · " + g.etiqueta() + " (" + g.cicloEscolar() + ")");
                p.setInt(2, g.id());
                n = p.executeUpdate();
            }
            return new Resultado(0, 1, 0, n, avisos);
        }

        int siguiente = g.grado() + 1;
        if (existeActivo(c, g.nivel(), siguiente, g.letra(), g.id())) {
            avisos.add("Ya existe el grupo " + etiqueta(g.nivel(), siguiente, g.letra())
                    + ". Páselo de año primero o use «Pasar de año a todos».");
            return new Resultado(0, 0, 0, 0, avisos);
        }
        try (PreparedStatement p = c.prepareStatement(
                "UPDATE grupo SET grado = ?, ciclo_escolar = ?, promovido_en = ? WHERE id_grupo = ?")) {
            p.setInt(1, siguiente);
            p.setString(2, cicloSiguiente(g.cicloEscolar()));
            p.setString(3, fecha);
            p.setInt(4, g.id());
            p.executeUpdate();
        }
        int n = sincronizarPacientes(c, g.id(), etiqueta(g.nivel(), siguiente, g.letra()));
        return new Resultado(1, 0, n, 0, avisos);
    }

    private static String avisoYaPromovido(GrupoInfo g, LocalDateTime ahora) {
        if (g.promovidoEn() == null || g.promovidoEn().isBlank()) return null;
        try {
            LocalDateTime antes = LocalDateTime.parse(g.promovidoEn(), ISO);
            if (antes.plusDays(DIAS_ENTRE_PROMOCIONES).isAfter(ahora)) {
                return g.etiqueta() + " ya pasó de año el " + antes.toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + ". Si fue un error, corrija el grado editando el grupo.";
            }
        } catch (Exception ignorado) {
            // Fecha ilegible: no se bloquea.
        }
        return null;
    }

    // =========================================================================
    // AUXILIARES
    // =========================================================================

    private static int sincronizarPacientes(Connection c, int idGrupo, String etiqueta) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "UPDATE paciente SET grado_grupo = ? WHERE id_grupo = ? AND COALESCE(egresado, 0) = 0")) {
            p.setString(1, etiqueta);
            p.setInt(2, idGrupo);
            return p.executeUpdate();
        }
    }

    private static boolean existeActivo(Connection c, String nivel, int grado, String letra, int excluir) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT 1 FROM grupo WHERE activo = 1 AND id_grupo <> ? AND lower(nivel) = lower(?) AND grado = ? AND upper(letra) = upper(?)")) {
            p.setInt(1, excluir);
            p.setString(2, nivel.trim());
            p.setInt(3, grado);
            p.setString(4, letra.trim());
            try (ResultSet rs = p.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String ordenNivel(String columna) {
        return "CASE lower(" + columna + ") WHEN 'preescolar' THEN 0 WHEN 'primaria' THEN 1 WHEN 'secundaria' THEN 2 ELSE 3 END";
    }

    private static GrupoInfo leer(ResultSet rs) throws SQLException {
        return new GrupoInfo(rs.getInt("id_grupo"), rs.getString("nivel"), rs.getInt("grado"), rs.getString("letra"),
                rs.getString("ciclo_escolar"), rs.getString("docente"), rs.getInt("activo") == 1,
                rs.getString("promovido_en"), rs.getInt("pacientes"));
    }

    private static Resultado fallo(String mensaje) {
        List<String> avisos = new ArrayList<>();
        avisos.add(mensaje);
        return new Resultado(0, 0, 0, 0, avisos);
    }
}
