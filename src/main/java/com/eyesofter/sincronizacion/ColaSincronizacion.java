package com.eyesofter.sincronizacion;

import com.eyesofter.clinica.ResultadoClinico;
import com.eyesofter.config.AppConfig;
import com.eyesofter.db.GestorBaseDatos;
import com.google.gson.JsonObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Envío de los tamizajes al servidor de EYESOFTER Suite con cola persistente.
 *
 * <ul>
 *   <li><b>C-RFN-02</b>: cada evaluación terminada se encola y se envía con
 *       {@code POST /api/escritorio/tamizajes}.</li>
 *   <li><b>C-RFNN-03</b>: si falla la red o el servidor, se reintenta sola con espera
 *       creciente (30 s, 1 min, 2 min… hasta 30 min). La cola vive en SQLite, así que
 *       sobrevive a cerrar la aplicación. Si la falla dura más de 30 minutos se avisa a
 *       Enfermería con {@code POST /api/escritorio/incidencias} (como máximo una vez por hora).</li>
 *   <li><b>C-RC-11</b>: el folio se genera una sola vez y el servidor es idempotente
 *       por folio, así que un reintento nunca duplica ni altera el tamizaje.</li>
 *   <li>Los errores de datos (400, o 422 por matrícula desconocida) no se reintentan solos:
 *       se muestran al operador para corregir la matrícula y reenviar.</li>
 * </ul>
 */
public final class ColaSincronizacion {

    public enum Estado { PENDIENTE, ENVIADO, REQUIERE_ATENCION }

    /** Elemento de la cola para mostrar en pantalla. */
    public record Envio(String folio, String curp, String nombre, Estado estado, int intentos,
                        String ultimoError, String creadoEn, String casoFolio) { }

    private static final long ESPERA_BASE_MS = 30_000;
    private static final long ESPERA_MAXIMA_MS = 30 * 60_000L;
    private static final long UMBRAL_INCIDENCIA_MS = 30 * 60_000L;
    private static final long INTERVALO_INCIDENCIA_MS = 60 * 60_000L;

    private static ScheduledExecutorService programador;
    private static volatile long ultimaIncidenciaMs;
    private static volatile String ultimoResultado = "Sin envíos todavía.";

    private ColaSincronizacion() { }

    // ------------------------------------------------------------------
    // Esquema
    // ------------------------------------------------------------------

    private static volatile boolean tablasListas;

    private static void asegurarTablas() { if (!tablasListas) crearTablas(); }

    public static void crearTablas() {
        tablasListas = true;
        try (Connection c = GestorBaseDatos.abrirConexion(); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS envio_tamizaje (" +
                    "folio TEXT PRIMARY KEY, curp_paciente TEXT NOT NULL, fecha TEXT NOT NULL, " +
                    "semaforo TEXT NOT NULL, agudeza_od TEXT, agudeza_oi TEXT, observaciones TEXT, " +
                    "estado TEXT NOT NULL DEFAULT 'PENDIENTE', intentos INTEGER NOT NULL DEFAULT 0, " +
                    "proximo_intento INTEGER NOT NULL DEFAULT 0, ultimo_error TEXT, caso_folio TEXT, " +
                    "creado_en TEXT NOT NULL, enviado_en TEXT)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_envio_estado ON envio_tamizaje(estado, proximo_intento)");
            // Matrícula del portal (la API identifica al alumno por matrícula, el escritorio por CURP).
            s.execute("CREATE TABLE IF NOT EXISTS paciente_matricula (curp TEXT PRIMARY KEY, matricula TEXT NOT NULL)");
        } catch (SQLException e) {
            System.err.println("[SYNC] No se pudo crear la cola de envío: " + e.getMessage());
        }
    }

    public static String obtenerMatricula(String curp) {
        if (curp == null) return "";
        asegurarTablas();
        try (Connection c = GestorBaseDatos.abrirConexion();
             PreparedStatement p = c.prepareStatement("SELECT matricula FROM paciente_matricula WHERE curp = ?")) {
            p.setString(1, curp);
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getString(1) : ""; }
        } catch (SQLException e) {
            return "";
        }
    }

    /** Guarda la matrícula y reactiva los envíos de ese alumno que estaban detenidos por ella. */
    public static void guardarMatricula(String curp, String matricula) {
        if (curp == null || curp.isBlank()) return;
        asegurarTablas();
        String limpia = matricula == null ? "" : matricula.trim().toUpperCase(Locale.ROOT);
        try (Connection c = GestorBaseDatos.abrirConexion()) {
            if (limpia.isEmpty()) {
                try (PreparedStatement p = c.prepareStatement("DELETE FROM paciente_matricula WHERE curp = ?")) { p.setString(1, curp); p.executeUpdate(); }
                return;
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO paciente_matricula(curp, matricula) VALUES(?, ?) " +
                    "ON CONFLICT(curp) DO UPDATE SET matricula = excluded.matricula")) {
                p.setString(1, curp); p.setString(2, limpia); p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("UPDATE envio_tamizaje SET estado = 'PENDIENTE', proximo_intento = 0 " +
                    "WHERE curp_paciente = ? AND estado = 'REQUIERE_ATENCION'")) { p.setString(1, curp); p.executeUpdate(); }
        } catch (SQLException e) {
            System.err.println("[SYNC] No se pudo guardar la matrícula: " + e.getMessage());
        }
        despertar();
    }

    // ------------------------------------------------------------------
    // Encolar
    // ------------------------------------------------------------------

    /** Semáforo del portal a partir de la prioridad clínica del escritorio. */
    public static String semaforo(ResultadoClinico r) {
        return switch (r.getPrioridadSeguimiento()) {
            case SIN_BANDERAS -> "VERDE";
            case NO_CONCLUYENTE, PROGRAMADA, PREFERENTE -> "AMARILLO";
            case PRIORITARIA -> "ROJO";
        };
    }

    /** "20/NN" exacto o null si el ojo no alcanzó 20/200 (el dato se explica en observaciones). */
    static String agudeza(ResultadoClinico.ResultadoOjo ojo) {
        if (ojo == null || ojo.getSnellen() == null) return null;
        return ojo.getSnellen().matches("20/\\d{1,3}") ? ojo.getSnellen() : null;
    }

    static String observaciones(ResultadoClinico r) {
        StringBuilder sb = new StringBuilder();
        if (r.getAmbosOjos() != null) sb.append("Ambos ojos ").append(r.getAmbosOjos().resumen()).append(". ");
        if (r.getOjoDerecho() != null) sb.append("OD ").append(r.getOjoDerecho().resumen()).append(". ");
        if (r.getOjoIzquierdo() != null) sb.append("OI ").append(r.getOjoIzquierdo().resumen()).append(". ");
        sb.append("Visión de color ").append(r.getIshiharaFormateado()).append(". ");
        String alertas = r.getAlertasComoTexto();
        if (!alertas.isBlank()) sb.append(alertas);
        String texto = sb.toString().trim();
        return texto.length() > 500 ? texto.substring(0, 497) + "…" : texto;
    }

    /** Encola el tamizaje terminado y dispara un envío inmediato. Devuelve el folio. */
    public static String encolar(String curp, ResultadoClinico r) {
        asegurarTablas();
        String folio = "ESC-" + LocalDate.now().getYear() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        try (Connection c = GestorBaseDatos.abrirConexion();
             PreparedStatement p = c.prepareStatement("INSERT INTO envio_tamizaje(folio, curp_paciente, fecha, semaforo, agudeza_od, " +
                     "agudeza_oi, observaciones, creado_en) VALUES(?,?,?,?,?,?,?,?)")) {
            p.setString(1, folio);
            p.setString(2, curp);
            p.setString(3, LocalDate.now().toString());
            p.setString(4, semaforo(r));
            p.setString(5, agudeza(r.getOjoDerecho()));
            p.setString(6, agudeza(r.getOjoIzquierdo()));
            p.setString(7, observaciones(r));
            p.setString(8, Instant.now().toString());
            p.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SYNC] No se pudo encolar el tamizaje: " + e.getMessage());
            return null;
        }
        despertar();
        return folio;
    }

    // ------------------------------------------------------------------
    // Proceso en segundo plano
    // ------------------------------------------------------------------

    public static synchronized void iniciar() {
        crearTablas();
        if (programador != null) return;
        programador = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sincronizacion-suite");
            t.setDaemon(true);
            return t;
        });
        programador.scheduleWithFixedDelay(ColaSincronizacion::procesarSeguro, 5, 30, TimeUnit.SECONDS);
    }

    /** Pide un intento inmediato (después de encolar, guardar matrícula o cambiar la configuración). */
    public static void despertar() {
        ScheduledExecutorService p = programador;
        if (p != null) p.execute(ColaSincronizacion::procesarSeguro);
    }

    /** Reintenta ya todo lo pendiente, incluidos los que requerían atención. */
    public static void reintentarTodo() {
        try (Connection c = GestorBaseDatos.abrirConexion(); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE envio_tamizaje SET proximo_intento = 0, estado = 'PENDIENTE' WHERE estado <> 'ENVIADO'");
        } catch (SQLException ignored) { }
        despertar();
    }

    public static String getUltimoResultado() { return ultimoResultado; }

    private static void procesarSeguro() {
        try { procesar(); } catch (Throwable t) { System.err.println("[SYNC] " + t); }
    }

    private static synchronized void procesar() {
        if (!ClienteSuite.estaConfigurado()) { ultimoResultado = "Conexión con el portal sin configurar."; return; }
        long ahora = System.currentTimeMillis();
        List<String[]> pendientes = new ArrayList<>();
        try (Connection c = GestorBaseDatos.abrirConexion();
             PreparedStatement p = c.prepareStatement("SELECT folio, curp_paciente, fecha, semaforo, agudeza_od, agudeza_oi, observaciones, intentos " +
                     "FROM envio_tamizaje WHERE estado = 'PENDIENTE' AND proximo_intento <= ? ORDER BY creado_en LIMIT 20")) {
            p.setLong(1, ahora);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) pendientes.add(new String[]{r.getString(1), r.getString(2), r.getString(3), r.getString(4),
                        r.getString(5), r.getString(6), r.getString(7), String.valueOf(r.getInt(8))});
            }
        } catch (SQLException e) {
            return;
        }
        for (String[] f : pendientes) enviarUno(f);
        revisarIncidencia();
    }

    private static void enviarUno(String[] f) {
        String folio = f[0], curp = f[1];
        int intentos = Integer.parseInt(f[7]) + 1;
        String matricula = obtenerMatricula(curp);
        if (matricula.isBlank()) {
            marcar(folio, Estado.REQUIERE_ATENCION, intentos, 0, "Falta la matrícula del portal para este alumno.", null);
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("folio", folio);
        o.addProperty("matricula", matricula);
        o.addProperty("fecha", f[2]);
        o.addProperty("semaforo", f[3]);
        if (f[4] != null) o.addProperty("agudezaOjoDerecho", f[4]);
        if (f[5] != null) o.addProperty("agudezaOjoIzquierdo", f[5]);
        if (f[6] != null && !f[6].isBlank()) o.addProperty("observaciones", f[6]);

        ClienteSuite.Respuesta r = ClienteSuite.enviarTamizaje(o.toString());
        if (r.exito()) {
            String caso = r.json().has("casoFolio") && !r.json().get("casoFolio").isJsonNull() ? r.json().get("casoFolio").getAsString() : null;
            marcar(folio, Estado.ENVIADO, intentos, 0, null, caso);
            ultimoResultado = "Último envío correcto: " + folio + (caso != null ? " · caso " + caso : "");
        } else if (r.codigo() == 400 || r.codigo() == 422) {
            String motivo = r.codigo() == 422 ? "El portal no tiene un alumno con la matrícula " + matricula + ". Corríjala o dé de alta al alumno en el portal."
                    : "El portal rechazó los datos: " + r.mensajeError();
            marcar(folio, Estado.REQUIERE_ATENCION, intentos, 0, motivo, null);
            ultimoResultado = motivo;
        } else {
            long espera = Math.min(ESPERA_MAXIMA_MS, ESPERA_BASE_MS << Math.min(10, intentos - 1));
            String motivo = r.codigo() == 401 ? "La clave del escritorio no es válida."
                    : r.codigo() == 503 ? "El servidor no tiene habilitada la recepción del escritorio."
                    : r.codigo() == 0 ? r.cuerpo() : "El servidor respondió " + r.codigo() + ".";
            marcar(folio, Estado.PENDIENTE, intentos, System.currentTimeMillis() + espera, motivo, null);
            ultimoResultado = motivo + " Se reintentará solo.";
        }
    }

    private static void marcar(String folio, Estado estado, int intentos, long proximo, String error, String caso) {
        try (Connection c = GestorBaseDatos.abrirConexion();
             PreparedStatement p = c.prepareStatement("UPDATE envio_tamizaje SET estado = ?, intentos = ?, proximo_intento = ?, " +
                     "ultimo_error = ?, caso_folio = COALESCE(?, caso_folio), enviado_en = CASE WHEN ? = 'ENVIADO' THEN ? ELSE enviado_en END WHERE folio = ?")) {
            p.setString(1, estado.name());
            p.setInt(2, intentos);
            p.setLong(3, proximo);
            p.setString(4, error);
            p.setString(5, caso);
            p.setString(6, estado.name());
            p.setString(7, Instant.now().toString());
            p.setString(8, folio);
            p.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[SYNC] No se pudo actualizar " + folio + ": " + e.getMessage());
        }
    }

    /** Si hay envíos pendientes desde hace más de 30 min, avisa a Enfermería (máximo 1 vez por hora). */
    private static void revisarIncidencia() {
        long ahora = System.currentTimeMillis();
        if (ahora - ultimaIncidenciaMs < INTERVALO_INCIDENCIA_MS) return;
        try (Connection c = GestorBaseDatos.abrirConexion(); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT COUNT(*), MIN(creado_en) FROM envio_tamizaje WHERE estado = 'PENDIENTE' AND intentos > 0")) {
            if (!r.next() || r.getInt(1) == 0) return;
            int pendientes = r.getInt(1);
            String desde = r.getString(2);
            if (ahora - Instant.parse(desde).toEpochMilli() < UMBRAL_INCIDENCIA_MS) return;
            ClienteSuite.Respuesta resp = ClienteSuite.reportarIncidencia(
                    "El equipo de escritorio no ha podido enviar " + pendientes + " tamizaje(s).", pendientes, desde);
            if (resp.exito()) ultimaIncidenciaMs = ahora;
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------------
    // Consulta para la interfaz
    // ------------------------------------------------------------------

    public static int contar(Estado estado) {
        asegurarTablas();
        try (Connection c = GestorBaseDatos.abrirConexion();
             PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM envio_tamizaje WHERE estado = ?")) {
            p.setString(1, estado.name());
            try (ResultSet r = p.executeQuery()) { return r.next() ? r.getInt(1) : 0; }
        } catch (SQLException e) {
            return 0;
        }
    }

    public static List<Envio> listar(int limite) {
        asegurarTablas();
        List<Envio> lista = new ArrayList<>();
        try (Connection c = GestorBaseDatos.abrirConexion();
             PreparedStatement p = c.prepareStatement("SELECT e.folio, e.curp_paciente, COALESCE(p.nombre, ''), e.estado, e.intentos, " +
                     "e.ultimo_error, e.creado_en, e.caso_folio FROM envio_tamizaje e LEFT JOIN paciente p ON p.curp = e.curp_paciente " +
                     "ORDER BY CASE e.estado WHEN 'REQUIERE_ATENCION' THEN 0 WHEN 'PENDIENTE' THEN 1 ELSE 2 END, e.creado_en DESC LIMIT ?")) {
            p.setInt(1, limite);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) lista.add(new Envio(r.getString(1), r.getString(2), r.getString(3), Estado.valueOf(r.getString(4)),
                        r.getInt(5), r.getString(6), r.getString(7), r.getString(8)));
            }
        } catch (SQLException e) {
            System.err.println("[SYNC] " + e.getMessage());
        }
        return lista;
    }

    /** Estado del envío de un folio (para la pantalla de resultados). */
    public static Envio buscar(String folio) {
        for (Envio e : listar(500)) if (e.folio().equals(folio)) return e;
        return null;
    }

    /** Solo para pruebas: detiene el proceso en segundo plano. */
    static synchronized void detener() {
        if (programador != null) { programador.shutdownNow(); programador = null; }
    }

    static String nombreEquipo() { return AppConfig.NOMBRE_EQUIPO; }
}
