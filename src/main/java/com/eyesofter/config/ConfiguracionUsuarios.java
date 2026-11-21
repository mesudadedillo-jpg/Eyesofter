package com.eyesofter.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Gestiona las credenciales locales de acceso de EYESOFTER
 * almacenadas en un archivo de configuración fuera de la base de datos clínica.
 *
 * <p>Seguridad (C-RC-05, C-RFNN-01):</p>
 * <ul>
 *   <li>Contraseñas y respuestas de recuperación se guardan con PBKDF2-HMAC-SHA256
 *       (210 000 iteraciones, sal aleatoria de 16 bytes), nunca en texto plano. Las
 *       que estaban en texto plano se convierten solas al primer inicio de sesión.</li>
 *   <li>3 intentos fallidos seguidos bloquean la cuenta 15 minutos (igual que el portal).</li>
 * </ul>
 */
public final class ConfiguracionUsuarios {

    private static final String ARCHIVO_ENTORNO = ".env";
    private static final String PREFIJO_USUARIO = "EYESOFTER_USER_";
    private static final String PREFIJO_PREGUNTA = "EYESOFTER_QUESTION_";
    private static final String PREFIJO_RESPUESTA = "EYESOFTER_ANSWER_";
    private static final Properties usuarios = new Properties();
    private static boolean cargada;

    // Almacena el usuario de la sesión actual en memoria
    private static String usuarioSesionActual = "root";

    private static final String PREFIJO_HASH = "pbkdf2$";
    private static final int ITERACIONES = 210_000;
    public static final int INTENTOS_MAXIMOS = 3;
    public static final long BLOQUEO_MS = 15 * 60 * 1000L;
    private static final Map<String, Integer> fallos = new HashMap<>();
    private static final Map<String, Long> bloqueadoHasta = new HashMap<>();

    private ConfiguracionUsuarios() { }

    /** Hash PBKDF2 con sal: "pbkdf2$iteraciones$sal$hash" (Base64). */
    static String hash(String secreto) {
        byte[] sal = new byte[16];
        new SecureRandom().nextBytes(sal);
        return PREFIJO_HASH + ITERACIONES + "$" + Base64.getEncoder().encodeToString(sal) + "$"
                + Base64.getEncoder().encodeToString(derivar(secreto, sal, ITERACIONES));
    }

    private static byte[] derivar(String secreto, byte[] sal, int iteraciones) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secreto.toCharArray(), sal, iteraciones, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 no disponible", e);
        }
    }

    /** Compara en tiempo constante. Acepta valores antiguos en texto plano (se migran al validar). */
    static boolean coincide(String almacenado, String candidato) {
        if (almacenado == null || candidato == null) return false;
        if (almacenado.startsWith(PREFIJO_HASH)) {
            String[] partes = almacenado.split("\\$");
            if (partes.length != 4) return false;
            try {
                int it = Integer.parseInt(partes[1]);
                byte[] sal = Base64.getDecoder().decode(partes[2]);
                byte[] esperado = Base64.getDecoder().decode(partes[3]);
                return MessageDigest.isEqual(esperado, derivar(candidato, sal, it));
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return MessageDigest.isEqual(almacenado.getBytes(StandardCharsets.UTF_8), candidato.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean esHash(String valor) { return valor != null && valor.startsWith(PREFIJO_HASH); }

    /** Minutos que faltan para desbloquear la cuenta (0 si no está bloqueada). */
    public static synchronized long minutosBloqueo(String nombreUsuario) {
        Long hasta = bloqueadoHasta.get(normalizarUsuario(nombreUsuario));
        if (hasta == null) return 0;
        long resta = hasta - System.currentTimeMillis();
        if (resta <= 0) { bloqueadoHasta.remove(normalizarUsuario(nombreUsuario)); return 0; }
        return Math.max(1, (resta + 59_999) / 60_000);
    }

    private static void registrarFallo(String usuarioNorm) {
        int n = fallos.merge(usuarioNorm, 1, Integer::sum);
        if (n >= INTENTOS_MAXIMOS) {
            bloqueadoHasta.put(usuarioNorm, System.currentTimeMillis() + BLOQUEO_MS);
            fallos.remove(usuarioNorm);
        }
    }

    /** Convierte a hash cualquier contraseña o respuesta que siga en texto plano. */
    private static void migrarTextoPlano() {
        boolean cambio = false;
        for (String clave : usuarios.stringPropertyNames()) {
            if ((clave.startsWith(PREFIJO_USUARIO) || clave.startsWith(PREFIJO_RESPUESTA)) && !esHash(usuarios.getProperty(clave))
                    && !usuarios.getProperty(clave).isEmpty()) {
                usuarios.setProperty(clave, hash(usuarios.getProperty(clave)));
                cambio = true;
            }
        }
        if (cambio) guardar();
    }

    public static synchronized void cargar() {
        if (cargada) return;

        File archivo = new File(directorioAplicacion(), ARCHIVO_ENTORNO);
        if (archivo.exists()) {
            try (FileInputStream entrada = new FileInputStream(archivo)) {
                usuarios.load(entrada);
            } catch (IOException e) {
                System.err.println("[CONFIG ERROR]: No se pudo leer el archivo de usuarios: " + e.getMessage());
            }
        }

        // Si no existe ningún usuario registrado, crear el usuario por defecto root / root
        if (usuarios.stringPropertyNames().stream().noneMatch(clave -> clave.startsWith(PREFIJO_USUARIO))) {
            usuarios.setProperty(PREFIJO_USUARIO + "root", hash("root"));
            usuarios.setProperty(PREFIJO_PREGUNTA + "root", "¿Cuál es la clave maestra inicial?");
            usuarios.setProperty(PREFIJO_RESPUESTA + "root", hash(normalizarRespuesta("root")));
            guardar();
        }
        migrarTextoPlano();

        cargada = true;
    }

    public static synchronized boolean validarCredenciales(String nombreUsuario, String contrasena) {
        cargar();
        if (nombreUsuario == null || contrasena == null) return false;

        String usuarioNorm = normalizarUsuario(nombreUsuario);
        if (minutosBloqueo(usuarioNorm) > 0) return false;
        String passAlmacenada = usuarios.getProperty(PREFIJO_USUARIO + usuarioNorm);

        if (coincide(passAlmacenada, contrasena)) {
            fallos.remove(usuarioNorm);
            if (!esHash(passAlmacenada)) { usuarios.setProperty(PREFIJO_USUARIO + usuarioNorm, hash(contrasena)); guardar(); }
            usuarioSesionActual = usuarioNorm;
            return true;
        }
        registrarFallo(usuarioNorm);
        return false;
    }

    /** Obtiene el usuario que tiene la sesión iniciada actualmente. */
    public static String getUsuarioSesionActual() {
        return (usuarioSesionActual == null || usuarioSesionActual.isBlank()) ? "root" : usuarioSesionActual;
    }

    /** Permite establecer manualmente el usuario en sesión desde el Login. */
    public static void setUsuarioSesionActual(String usuario) {
        usuarioSesionActual = normalizarUsuario(usuario);
    }

    /** Indica si existe únicamente la cuenta inicial sin modificaciones. */
    public static synchronized boolean conservaCredencialesIniciales() {
        cargar();
        long totalUsuarios = usuarios.stringPropertyNames().stream()
                .filter(clave -> clave.startsWith(PREFIJO_USUARIO))
                .count();

        String passRoot = usuarios.getProperty(PREFIJO_USUARIO + "root");
        return totalUsuarios == 1 && coincide(passRoot, "root");
    }

    /** Cuenta local disponible para la pantalla de configuración en instalaciones de un solo operador. */
    public static synchronized String obtenerUsuarioPrincipal() {
        cargar();
        return usuarios.stringPropertyNames().stream()
                .filter(clave -> clave.startsWith(PREFIJO_USUARIO))
                .map(clave -> clave.substring(PREFIJO_USUARIO.length()))
                .sorted()
                .findFirst()
                .orElse("root");
    }

    /** Devuelve la lista completa de usuarios registrados. */
    public static synchronized List<String> listarUsuarios() {
        cargar();
        List<String> lista = new ArrayList<>();
        for (String clave : usuarios.stringPropertyNames()) {
            if (clave.startsWith(PREFIJO_USUARIO)) {
                lista.add(clave.substring(PREFIJO_USUARIO.length()));
            }
        }
        return lista;
    }

    public static synchronized boolean agregarUsuario(String nombreUsuario, String contrasena) {
        return agregarUsuario(nombreUsuario, contrasena, "¿Pregunta de seguridad por defecto?", "eyesofter");
    }

    public static synchronized boolean agregarUsuario(String nombreUsuario, String contrasena, String pregunta, String respuesta) {
        cargar();
        String usuarioNorm = normalizarUsuario(nombreUsuario);
        if (usuarioNorm.isEmpty() || !contrasenaValida(contrasena)) return false;
        if (respuesta == null || respuesta.isBlank()) return false;

        String claveUsuario = PREFIJO_USUARIO + usuarioNorm;
        if (usuarios.containsKey(claveUsuario)) return false; // Usuario duplicado

        usuarios.setProperty(claveUsuario, hash(contrasena));
        usuarios.setProperty(PREFIJO_PREGUNTA + usuarioNorm, pregunta != null ? pregunta.trim() : "");
        usuarios.setProperty(PREFIJO_RESPUESTA + usuarioNorm, hash(normalizarRespuesta(respuesta)));
        guardar();
        return true;
    }

    /** Elimina un usuario del archivo de credenciales si no es el único. */
    public static synchronized boolean eliminarUsuario(String nombreUsuario) {
        cargar();
        String usuarioNorm = normalizarUsuario(nombreUsuario);
        String claveUsuario = PREFIJO_USUARIO + usuarioNorm;

        if (!usuarios.containsKey(claveUsuario)) return false;

        long totalUsuarios = usuarios.stringPropertyNames().stream()
                .filter(c -> c.startsWith(PREFIJO_USUARIO))
                .count();

        // Prohibir la eliminación si solo queda un usuario en el sistema
        if (totalUsuarios <= 1) return false;

        usuarios.remove(claveUsuario);
        usuarios.remove(PREFIJO_PREGUNTA + usuarioNorm);
        usuarios.remove(PREFIJO_RESPUESTA + usuarioNorm);
        guardar();

        // Si se eliminó el usuario en sesión actual, reasignar a uno disponible
        if (usuarioNorm.equalsIgnoreCase(usuarioSesionActual)) {
            usuarioSesionActual = obtenerUsuarioPrincipal();
        }

        return true;
    }

    public static synchronized String obtenerPregunta(String nombreUsuario) {
        cargar();
        String usuarioNorm = normalizarUsuario(nombreUsuario);
        return usuarios.getProperty(PREFIJO_PREGUNTA + usuarioNorm, "¿Cuál es tu pregunta de seguridad?").trim();
    }

    public static synchronized boolean configurarVerificacion(String nombreUsuario, String contrasenaActual, String pregunta, String respuesta) {
        cargar();
        String usuarioNorm = normalizarUsuario(nombreUsuario);
        String passAlmacenada = usuarios.getProperty(PREFIJO_USUARIO + usuarioNorm);

        if (!coincide(passAlmacenada, contrasenaActual)) return false;
        if (pregunta == null || pregunta.isBlank() || respuesta == null || respuesta.isBlank()) return false;

        usuarios.setProperty(PREFIJO_PREGUNTA + usuarioNorm, pregunta.trim());
        usuarios.setProperty(PREFIJO_RESPUESTA + usuarioNorm, hash(normalizarRespuesta(respuesta)));
        guardar();
        return true;
    }

    public static synchronized boolean recuperarContrasena(String nombreUsuario, String respuesta, String nuevaContrasena) {
        cargar();
        String usuarioNorm = normalizarUsuario(nombreUsuario);
        String claveUsuario = PREFIJO_USUARIO + usuarioNorm;

        if (!usuarios.containsKey(claveUsuario)) return false;
        if (!contrasenaValida(nuevaContrasena)) return false;
        if (minutosBloqueo(usuarioNorm) > 0) return false;

        String esperada = usuarios.getProperty(PREFIJO_RESPUESTA + usuarioNorm, "");
        if (esperada.isBlank() || !coincide(esperada, normalizarRespuesta(respuesta))) { registrarFallo(usuarioNorm); return false; }

        fallos.remove(usuarioNorm);
        usuarios.setProperty(claveUsuario, hash(nuevaContrasena));
        guardar();
        return true;
    }

    public static synchronized boolean cambiarContrasena(String nombreUsuario, String contrasenaActual, String nuevaContrasena) {
        cargar();
        String usuarioNorm = normalizarUsuario(nombreUsuario);
        String passAlmacenada = usuarios.getProperty(PREFIJO_USUARIO + usuarioNorm);

        if (!coincide(passAlmacenada, contrasenaActual)) return false;
        if (!contrasenaValida(nuevaContrasena) || nuevaContrasena.equals(contrasenaActual)) return false;

        usuarios.setProperty(PREFIJO_USUARIO + usuarioNorm, hash(nuevaContrasena));
        guardar();
        return true;
    }

    /** Renombra una cuenta local migrando sus credenciales, preguntas y respuestas. */
    public static synchronized boolean cambiarNombreUsuario(String usuarioActual, String usuarioNuevo, String contrasenaActual) {
        cargar();
        String actual = normalizarUsuario(usuarioActual);
        String nuevo = normalizarUsuario(usuarioNuevo);

        String claveActual = PREFIJO_USUARIO + actual;
        String claveNueva = PREFIJO_USUARIO + nuevo;

        String passAlmacenada = usuarios.getProperty(claveActual);
        if (!coincide(passAlmacenada, contrasenaActual)) return false;
        if (nuevo.isEmpty() || usuarios.containsKey(claveNueva)) return false;

        // Migrar contraseña
        usuarios.setProperty(claveNueva, passAlmacenada);
        usuarios.remove(claveActual);

        // Migrar pregunta
        String pregunta = usuarios.getProperty(PREFIJO_PREGUNTA + actual);
        if (pregunta != null) {
            usuarios.setProperty(PREFIJO_PREGUNTA + nuevo, pregunta);
            usuarios.remove(PREFIJO_PREGUNTA + actual);
        }

        // Migrar respuesta
        String respuesta = usuarios.getProperty(PREFIJO_RESPUESTA + actual);
        if (respuesta != null) {
            usuarios.setProperty(PREFIJO_RESPUESTA + nuevo, respuesta);
            usuarios.remove(PREFIJO_RESPUESTA + actual);
        }

        guardar();
        if (actual.equalsIgnoreCase(usuarioSesionActual)) {
            usuarioSesionActual = nuevo;
        }
        return true;
    }

    /** Restablece la configuración de fábrica a root / root. */
    public static synchronized void reiniciarCredencialesFabrica() {
        usuarios.clear();
        usuarios.setProperty(PREFIJO_USUARIO + "root", hash("root"));
        usuarios.setProperty(PREFIJO_PREGUNTA + "root", "¿Cuál es la clave maestra inicial?");
        usuarios.setProperty(PREFIJO_RESPUESTA + "root", hash(normalizarRespuesta("root")));
        usuarioSesionActual = "root";
        guardar();
    }

    /** Mínimo 8 caracteres, con al menos una letra y un número; "root" nunca es válida. */
    public static boolean contrasenaValida(String contrasena) {
        return contrasena != null && contrasena.length() >= 8 && contrasena.matches(".*[A-Za-zÁÉÍÓÚáéíóúÑñ].*")
                && contrasena.matches(".*\\d.*") && !contrasena.equalsIgnoreCase("root");
    }

    private static String normalizarUsuario(String nombreUsuario) {
        return nombreUsuario == null ? "" : nombreUsuario.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizarRespuesta(String respuesta) {
        return respuesta == null ? "" : respuesta.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static void guardar() {
        File dir = directorioAplicacion();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try (FileOutputStream salida = new FileOutputStream(new File(dir, ARCHIVO_ENTORNO))) {
            usuarios.store(salida, "Credenciales locales de EYESOFTER");
        } catch (IOException e) {
            System.err.println("[CONFIG ERROR]: No se pudo guardar el archivo de usuarios: " + e.getMessage());
            throw new IllegalStateException("No se pudo guardar la configuración local de usuarios.", e);
        }
    }

    private static File directorioAplicacion() {
        return new File(System.getProperty("eyesofter.home", "."));
    }
}