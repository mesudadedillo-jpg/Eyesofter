package com.eyesofter.voz;

import com.eyesofter.config.AppConfig;
import javafx.application.Platform;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.AudioInputStream;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Instrucciones breves y fluidas por voz para el modo autónomo. Nunca bloquea la interfaz. */
public final class VozGuia {

    /* Parámetros de ritmo y aceleración para Piper TTS */
    private static final String ESCALA_DURACION_TTS = "0.77"; // Equivalente a ~x1.3 de velocidad (1.0 / 1.3)
    private static final String SILENCIO_ENTRE_FRASES = "0.1"; // 100 ms de pausa entre oraciones

    /* Cola única de ejecución para síntesis */
    private static final ThreadPoolExecutor COLA_DE_VOZ = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), r -> {
        Thread hilo = new Thread(r, "voz-guia-autonoma");
        hilo.setDaemon(true);
        return hilo;
    });

    private static long version = 0;
    private static Process procesoActivo;
    private static Clip clipActivo;
    private static Thread hiloActivo;

    private VozGuia() { }

    public static synchronized void decir(String texto, Runnable alTerminar) {
        if (texto == null || texto.isBlank()) {
            if (alTerminar != null) Platform.runLater(alTerminar);
            return;
        }
        final long versionSolicitud = version;
        COLA_DE_VOZ.execute(() -> {
            File salida = null;
            try {
                synchronized (VozGuia.class) {
                    if (versionSolicitud != version) return;
                    hiloActivo = Thread.currentThread();
                }
                File ejecutable = new File(AppConfig.RUTA_PIPER_EXE);
                File modelo = new File(AppConfig.RUTA_MODELO_VOZ_TTS);
                salida = Files.createTempFile("eyesofter-guia-", ".wav").toFile();

                if (ejecutable.isFile() && modelo.isFile()) {
                    // Lanzar Piper con aceleración x1.3 y reducción de silencios
                    Process proceso = new ProcessBuilder(
                            ejecutable.getAbsolutePath(),
                            "-m", modelo.getAbsolutePath(),
                            "-f", salida.getAbsolutePath(),
                            "--length_scale", ESCALA_DURACION_TTS,
                            "--sentence_silence", SILENCIO_ENTRE_FRASES
                    ).start();

                    synchronized (VozGuia.class) { procesoActivo = proceso; }
                    try (OutputStream entrada = proceso.getOutputStream()) {
                        entrada.write(texto.getBytes(StandardCharsets.UTF_8));
                    }

                    if (proceso.waitFor() != 0) throw new IllegalStateException("Piper terminó con error.");

                    if (salida.isFile()) {
                        try (AudioInputStream audio = AudioSystem.getAudioInputStream(salida)) {
                            Clip clip = AudioSystem.getClip();
                            clip.open(audio);
                            synchronized (VozGuia.class) { clipActivo = clip; }
                            if (versionSolicitud != version) return;
                            clip.start();

                            // Espera reducida a solo 20 ms tras terminar el clip para transiciones rápidas
                            Thread.sleep((clip.getMicrosecondLength() / 1000) + 20);
                            clip.close();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("[VOZ]: No se pudo reproducir la guía: " + e.getMessage());
            } finally {
                if (salida != null && salida.exists() && !salida.delete()) salida.deleteOnExit();
                synchronized (VozGuia.class) {
                    procesoActivo = null;
                    clipActivo = null;
                    hiloActivo = null;
                }
                if (alTerminar != null && versionSolicitud == version) {
                    Platform.runLater(alTerminar);
                }
            }
        });
    }

    /** Detiene la locución y descarta instrucciones que ya no pertenecen a la prueba activa. */
    public static synchronized void cancelarPendientes() {
        version++;
        COLA_DE_VOZ.getQueue().clear();
        if (clipActivo != null) { clipActivo.stop(); clipActivo.close(); }
        if (procesoActivo != null && procesoActivo.isAlive()) procesoActivo.destroyForcibly();
        if (hiloActivo != null) hiloActivo.interrupt();
    }
}