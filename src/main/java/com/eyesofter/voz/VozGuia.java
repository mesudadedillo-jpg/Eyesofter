package com.eyesofter.voz;

import com.eyesofter.config.AppConfig;
import javafx.application.Platform;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Voz guía del modo autónomo. Nunca bloquea la interfaz.
 *
 * <h3>Fluidez</h3>
 * <ul>
 *   <li><b>Caché en disco</b>: cada frase se sintetiza una sola vez y se guarda como WAV
 *       en {@code cache_voz/}. Las siguientes veces suena al instante (antes Piper se
 *       relanzaba y cargaba el modelo en cada frase: 0.7–2 s de espera).</li>
 *   <li><b>Pregeneración</b>: {@link #precalentar()} sintetiza en segundo plano las frases
 *       habituales de la prueba al abrir la aplicación.</li>
 *   <li><b>Respaldo</b>: si Piper o su modelo no están, se usa la voz de Windows (SAPI)
 *       para que la guía nunca quede en silencio.</li>
 *   <li>{@link #tono()} reproduce un aviso breve que indica "ya puedes responder".</li>
 * </ul>
 */
public final class VozGuia {

    /** ~x1.15 de velocidad: más natural que x1.3 y sigue siendo ágil. */
    private static final String ESCALA_DURACION_TTS = "0.87";
    private static final String SILENCIO_ENTRE_FRASES = "0.15";
    private static final File DIRECTORIO_CACHE = new File(System.getProperty("eyesofter.home", "."), "cache_voz");

    /** Frases que se repiten en cada evaluación: se sintetizan al iniciar la aplicación. */
    public static final List<String> FRASES_FRECUENTES = List.of(
            "Vas a ver tres letras. Cuando escuches el tono, di en voz alta la letra marcada.",
            "Mantén ambos ojos descubiertos.",
            "Tápate el ojo izquierdo con la palma de la mano. Vamos a evaluar el ojo derecho.",
            "Tápate el ojo derecho con la palma de la mano. Vamos a evaluar el ojo izquierdo.",
            "No te entendí. Repite la letra marcada.",
            "Si no la ves, di: no sé.",
            "Observa la lámina. Di el número que ves. Si no ves ningún número, di nada.",
            "No te entendí. Repite el número.",
            "Pausa. Estás demasiado cerca. Aléjate hasta la distancia indicada.",
            "Pausa. Estás demasiado lejos. Acércate hasta la distancia indicada.",
            "Pausa. Se detectaron varias personas. Solo debe permanecer la persona evaluada.",
            "Pausa. Colócate frente a la cámara.",
            "Pausa. Hay demasiado movimiento. Quédate quieto.",
            "Muy bien. Continuamos.",
            "Terminamos esta parte. Muy bien."
    );

    private static final ThreadPoolExecutor COLA_DE_VOZ = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(), r -> {
        Thread hilo = new Thread(r, "voz-guia-autonoma");
        hilo.setDaemon(true);
        return hilo;
    });

    private static long version = 0;
    private static Process procesoActivo;
    private static Clip clipActivo;
    private static Thread hiloActivo;
    private static volatile boolean silenciada;

    private VozGuia() { }

    /** Silencia la guía (por ejemplo, en un entorno donde el operador lee las instrucciones). */
    public static void setSilenciada(boolean valor) { silenciada = valor; }
    public static boolean isSilenciada() { return silenciada; }

    public static synchronized void decir(String texto, Runnable alTerminar) {
        if (texto == null || texto.isBlank() || silenciada) {
            if (alTerminar != null) Platform.runLater(alTerminar);
            return;
        }
        final long versionSolicitud = version;
        COLA_DE_VOZ.execute(() -> {
            try {
                synchronized (VozGuia.class) {
                    if (versionSolicitud != version) return;
                    hiloActivo = Thread.currentThread();
                }
                File audio = obtenerAudio(texto);
                if (audio != null && versionSolicitud == version) reproducir(audio, versionSolicitud);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("[VOZ]: No se pudo reproducir la guía: " + e.getMessage());
            } finally {
                synchronized (VozGuia.class) {
                    procesoActivo = null;
                    clipActivo = null;
                    hiloActivo = null;
                }
                if (alTerminar != null && versionSolicitud == version) Platform.runLater(alTerminar);
            }
        });
    }

    /** Tono breve (880 Hz, 110 ms) que indica que el micrófono está escuchando. */
    public static synchronized void tono(Runnable alTerminar) {
        final long versionSolicitud = version;
        COLA_DE_VOZ.execute(() -> {
            try {
                if (versionSolicitud == version) reproducirTono();
            } catch (Exception e) {
                System.err.println("[VOZ]: tono no disponible: " + e.getMessage());
            } finally {
                if (alTerminar != null && versionSolicitud == version) Platform.runLater(alTerminar);
            }
        });
    }

    /** Sintetiza en segundo plano las frases frecuentes para que la primera prueba no espere. */
    public static void precalentar() {
        Thread hilo = new Thread(() -> {
            for (String frase : FRASES_FRECUENTES) {
                try { obtenerAudio(frase); } catch (Exception ignored) { }
            }
        }, "voz-precalentado");
        hilo.setDaemon(true);
        hilo.setPriority(Thread.MIN_PRIORITY);
        hilo.start();
    }

    /** Detiene la locución y descarta instrucciones que ya no pertenecen a la prueba activa. */
    public static synchronized void cancelarPendientes() {
        version++;
        COLA_DE_VOZ.getQueue().clear();
        if (clipActivo != null) { clipActivo.stop(); clipActivo.close(); }
        if (procesoActivo != null && procesoActivo.isAlive()) procesoActivo.destroyForcibly();
        if (hiloActivo != null) hiloActivo.interrupt();
    }

    /** Motor que se usará: útil para mostrar en Ajustes / Verificación de periféricos. */
    public static String motorDisponible() {
        if (piperDisponible()) return "Piper (voz neuronal local)";
        if (esWindows()) return "Voz de Windows (respaldo)";
        return "Sin voz disponible";
    }

    // ------------------------------------------------------------------

    private static boolean piperDisponible() {
        return new File(AppConfig.RUTA_PIPER_EXE).isFile() && new File(AppConfig.RUTA_MODELO_VOZ_TTS).isFile();
    }

    private static boolean esWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private static File obtenerAudio(String texto) throws Exception {
        boolean piper = piperDisponible();
        String motor = piper ? "piper|" + new File(AppConfig.RUTA_MODELO_VOZ_TTS).getName() + "|" + ESCALA_DURACION_TTS : "sapi";
        File cache = new File(DIRECTORIO_CACHE, hash(motor + "|" + texto) + ".wav");
        if (cache.isFile() && cache.length() > 44) return cache;
        if (!DIRECTORIO_CACHE.isDirectory() && !DIRECTORIO_CACHE.mkdirs()) return null;

        File temporal = File.createTempFile("eyesofter-voz-", ".wav", DIRECTORIO_CACHE);
        try {
            boolean ok = piper ? sintetizarPiper(texto, temporal) : esWindows() && sintetizarSapi(texto, temporal);
            if (!ok || temporal.length() <= 44) return null;
            Files.move(temporal.toPath(), cache.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return cache;
        } finally {
            if (temporal.exists() && !temporal.delete()) temporal.deleteOnExit();
        }
    }

    private static boolean sintetizarPiper(String texto, File salida) throws Exception {
        Process proceso = new ProcessBuilder(
                new File(AppConfig.RUTA_PIPER_EXE).getAbsolutePath(),
                "-m", new File(AppConfig.RUTA_MODELO_VOZ_TTS).getAbsolutePath(),
                "-f", salida.getAbsolutePath(),
                "--length_scale", ESCALA_DURACION_TTS,
                "--sentence_silence", SILENCIO_ENTRE_FRASES
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        synchronized (VozGuia.class) { procesoActivo = proceso; }
        try (OutputStream entrada = proceso.getOutputStream()) {
            entrada.write(texto.getBytes(StandardCharsets.UTF_8));
        }
        if (!proceso.waitFor(20, TimeUnit.SECONDS)) { proceso.destroyForcibly(); return false; }
        return proceso.exitValue() == 0;
    }

    /** Voz de Windows mediante System.Speech (incluida en Windows 10/11). */
    private static boolean sintetizarSapi(String texto, File salida) throws Exception {
        String seguro = texto.replace("'", "''");
        String ruta = salida.getAbsolutePath().replace("'", "''");
        String script = "Add-Type -AssemblyName System.Speech; "
                + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                + "$v = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -like 'es-*' } | Select-Object -First 1; "
                + "if ($v) { $s.SelectVoice($v.VoiceInfo.Name) }; "
                + "$s.Rate = 1; $s.SetOutputToWaveFile('" + ruta + "'); $s.Speak('" + seguro + "'); $s.Dispose()";
        Process proceso = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        synchronized (VozGuia.class) { procesoActivo = proceso; }
        if (!proceso.waitFor(20, TimeUnit.SECONDS)) { proceso.destroyForcibly(); return false; }
        return proceso.exitValue() == 0;
    }

    private static void reproducir(File archivo, long versionSolicitud) throws Exception {
        try (AudioInputStream audio = AudioSystem.getAudioInputStream(archivo)) {
            reproducirFlujo(audio, versionSolicitud);
        }
    }

    private static void reproducirFlujo(AudioInputStream audio, long versionSolicitud) throws Exception {
        Clip clip = AudioSystem.getClip();
        CountDownLatch fin = new CountDownLatch(1);
        clip.addLineListener(e -> { if (e.getType() == LineEvent.Type.STOP || e.getType() == LineEvent.Type.CLOSE) fin.countDown(); });
        clip.open(audio);
        synchronized (VozGuia.class) {
            if (versionSolicitud != version) { clip.close(); return; }
            clipActivo = clip;
        }
        clip.start();
        // Espera exacta al final del audio (más un margen por si el evento no llega).
        fin.await(clip.getMicrosecondLength() / 1000 + 500, TimeUnit.MILLISECONDS);
        clip.close();
    }

    private static void reproducirTono() throws Exception {
        float frecuenciaMuestreo = 22050f;
        int muestras = (int) (frecuenciaMuestreo * 0.11);
        byte[] datos = new byte[muestras * 2];
        for (int i = 0; i < muestras; i++) {
            double t = i / frecuenciaMuestreo;
            double envolvente = Math.min(1.0, Math.min(i, muestras - i) / (frecuenciaMuestreo * 0.01));
            short valor = (short) (Math.sin(2 * Math.PI * 880 * t) * 9000 * envolvente);
            datos[2 * i] = (byte) (valor & 0xFF);
            datos[2 * i + 1] = (byte) ((valor >> 8) & 0xFF);
        }
        AudioFormat formato = new AudioFormat(frecuenciaMuestreo, 16, 1, true, false);
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(datos), formato, muestras)) {
            reproducirFlujo(audio, version);
        }
    }

    private static String hash(String texto) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(texto.getBytes(StandardCharsets.UTF_8))).substring(0, 20);
    }
}
