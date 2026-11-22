package com.eyesofter.sincronizacion;

import com.eyesofter.config.AppConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Cliente HTTP del servidor de EYESOFTER Suite (contrato §13 de docs/API.md).
 * Comunicación REST/JSON con el token de ESTE equipo en {@code Authorization: Bearer} (C-RI-01).
 * El token se obtiene al vincular el equipo: alguien de Dirección o Enfermería inicia sesión
 * en el navegador con su cuenta del portal y lo autoriza. Sin vincular no hay acceso a datos.
 * Solo se permite HTTPS, salvo para localhost (pruebas), para cumplir C-RC-03.
 */
public final class ClienteSuite {

    /** Respuesta mínima: código HTTP y cuerpo JSON (o el mensaje de error de red). */
    public record Respuesta(int codigo, String cuerpo) {
        public boolean exito() { return codigo >= 200 && codigo < 300; }
        public JsonObject json() {
            try { return JsonParser.parseString(cuerpo).getAsJsonObject(); } catch (Exception e) { return new JsonObject(); }
        }
        public String mensajeError() {
            JsonObject j = json();
            return j.has("error") ? j.get("error").getAsString() : (cuerpo == null || cuerpo.isBlank() ? "HTTP " + codigo : cuerpo);
        }
    }

    /** Alumno del portal (para elegir la matrícula). */
    public record AlumnoPortal(String matricula, String nombre, String apellidos, String nivel, String grado, String grupo) {
        @Override public String toString() {
            return matricula + " · " + nombre + " " + apellidos + " · " + grado + "° " + grupo + " " + (nivel == null ? "" : nivel.toLowerCase());
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private ClienteSuite() { }

    /** Solicitud de vinculación: {@code solicitud} es secreta; {@code codigo} se muestra en pantalla. */
    public record Vinculacion(String solicitud, String codigo, String url, long expiraEnSegundos, int intervaloSegundos) { }

    public static boolean estaConfigurado() {
        return !AppConfig.URL_API_SUITE.isBlank() && !AppConfig.TOKEN_EQUIPO.isBlank();
    }

    /** true si el servidor dice que este equipo ya no está vinculado (401). */
    public static boolean equipoNoVinculado(Respuesta r) {
        return r.codigo() == 401;
    }

    /** Verifica que la URL sea https (o localhost para pruebas). */
    public static String validarUrl(String url) {
        if (url == null || url.isBlank()) return "Escriba la dirección del servidor.";
        try {
            URI u = URI.create(url.trim());
            boolean local = "localhost".equals(u.getHost()) || "127.0.0.1".equals(u.getHost());
            if (!"https".equalsIgnoreCase(u.getScheme()) && !(local && "http".equalsIgnoreCase(u.getScheme())))
                return "La dirección debe empezar con https:// (los datos de salud viajan cifrados).";
            if (u.getHost() == null) return "La dirección no es válida.";
            return null;
        } catch (IllegalArgumentException e) {
            return "La dirección no es válida.";
        }
    }

    private static String base() {
        String u = AppConfig.URL_API_SUITE.trim();
        return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
    }

    private static Respuesta enviar(HttpRequest.Builder b) {
        return enviar(b, true);
    }

    private static Respuesta enviar(HttpRequest.Builder b, boolean conToken) {
        try {
            b.timeout(Duration.ofSeconds(15)).header("Accept", "application/json");
            if (conToken && !AppConfig.TOKEN_EQUIPO.isBlank()) b.header("Authorization", "Bearer " + AppConfig.TOKEN_EQUIPO);
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Respuesta(r.statusCode(), r.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Respuesta(0, "Interrumpido");
        } catch (Exception e) {
            return new Respuesta(0, "Sin conexión con el servidor: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private static HttpRequest.Builder peticion(String ruta) {
        String error = validarUrl(AppConfig.URL_API_SUITE);
        if (error != null) throw new IllegalStateException(error);
        return HttpRequest.newBuilder(URI.create(base() + ruta));
    }

    /** {@code GET /salud}: prueba de conexión (no requiere vinculación). */
    public static Respuesta salud() {
        try { return enviar(peticion("/salud").GET(), false); }
        catch (IllegalStateException e) { return new Respuesta(0, e.getMessage()); }
    }

    /** {@code GET /api/escritorio/equipo}: ¿sigue vinculado este equipo? ¿quién lo autorizó? */
    public static Respuesta equipo() {
        try { return enviar(peticion("/api/escritorio/equipo").GET()); }
        catch (IllegalStateException e) { return new Respuesta(0, e.getMessage()); }
    }

    /**
     * {@code POST /api/escritorio/vinculaciones}: pide un código para vincular este equipo.
     * La URL devuelta se abre en el navegador; ahí se inicia sesión con la cuenta del portal.
     */
    public static Vinculacion iniciarVinculacion(String nombreEquipo) {
        JsonObject o = new JsonObject();
        o.addProperty("equipo", nombreEquipo == null ? "" : nombreEquipo);
        Respuesta r = enviar(peticion("/api/escritorio/vinculaciones").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(o.toString(), StandardCharsets.UTF_8)), false);
        if (!r.exito()) throw new IllegalStateException(r.codigo() == 0 ? r.cuerpo() : r.mensajeError());
        JsonObject j = r.json();
        return new Vinculacion(texto(j, "solicitud"), texto(j, "codigo"), base() + texto(j, "ruta"),
                j.has("expiraEnSegundos") ? j.get("expiraEnSegundos").getAsLong() : 600,
                j.has("intervaloSegundos") ? j.get("intervaloSegundos").getAsInt() : 3);
    }

    /**
     * {@code POST /api/escritorio/vinculaciones/estado}: 202 = aún no aprueban; 200 = aprobada
     * (trae el token, que se entrega una sola vez); 410 = venció.
     */
    public static Respuesta consultarVinculacion(String solicitud) {
        JsonObject o = new JsonObject();
        o.addProperty("solicitud", solicitud);
        try {
            return enviar(peticion("/api/escritorio/vinculaciones/estado").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(o.toString(), StandardCharsets.UTF_8)), false);
        } catch (IllegalStateException e) { return new Respuesta(0, e.getMessage()); }
    }

    /** {@code POST /api/escritorio/tamizajes} (C-RFN-02). Idempotente por folio. */
    public static Respuesta enviarTamizaje(String json) {
        try {
            return enviar(peticion("/api/escritorio/tamizajes").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
        } catch (IllegalStateException e) { return new Respuesta(0, e.getMessage()); }
    }

    /** {@code POST /api/escritorio/incidencias} (C-RFNN-03): avisa a Enfermería que la sincronización falla. */
    public static Respuesta reportarIncidencia(String mensaje, int pendientes, String desdeIso) {
        JsonObject o = new JsonObject();
        o.addProperty("mensaje", mensaje);
        o.addProperty("pendientes", pendientes);
        o.addProperty("desde", desdeIso);
        o.addProperty("equipo", AppConfig.NOMBRE_EQUIPO);
        try {
            return enviar(peticion("/api/escritorio/incidencias").header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(o.toString(), StandardCharsets.UTF_8)));
        } catch (IllegalStateException e) { return new Respuesta(0, e.getMessage()); }
    }

    /** {@code GET /api/escritorio/alumnos?q=}: busca (mín. 3 letras) alumnos del portal para tomar su matrícula. */
    public static List<AlumnoPortal> buscarAlumnos(String texto) throws IllegalStateException {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.length() < 3) throw new IllegalStateException("Escriba al menos 3 letras del nombre o de la matrícula.");
        String q = URLEncoder.encode(limpio, StandardCharsets.UTF_8);
        Respuesta r = enviar(peticion("/api/escritorio/alumnos?q=" + q).GET());
        if (!r.exito()) throw new IllegalStateException(equipoNoVinculado(r)
                ? "Este equipo no está vinculado. Vincúlelo en Configuración → Conexión con EYESOFTER Suite." : r.mensajeError());
        List<AlumnoPortal> lista = new ArrayList<>();
        JsonElement raiz = JsonParser.parseString(r.cuerpo());
        JsonArray arreglo = raiz.isJsonArray() ? raiz.getAsJsonArray() : new JsonArray();
        for (JsonElement e : arreglo) {
            JsonObject a = e.getAsJsonObject();
            lista.add(new AlumnoPortal(texto(a, "matricula"), texto(a, "nombre"), texto(a, "apellidos"),
                    texto(a, "nivel"), texto(a, "grado"), texto(a, "grupo")));
        }
        return lista;
    }

    private static String texto(JsonObject o, String campo) {
        return o.has(campo) && !o.get(campo).isJsonNull() ? o.get(campo).getAsString() : "";
    }
}
