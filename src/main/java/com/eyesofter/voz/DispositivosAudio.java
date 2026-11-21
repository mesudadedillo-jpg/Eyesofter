package com.eyesofter.voz;

import com.eyesofter.config.AppConfig;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Descubre micrófonos disponibles y convierte la señal elegida a PCM 16 kHz
 * mono para Vosk. De este modo se aceptan dispositivos que solo exponen
 * 44.1/48 kHz, incluyendo micrófonos USB y equipos de generaciones previas.
 */
public final class DispositivosAudio {
    public static final AudioFormat FORMATO_VOSK = new AudioFormat(16000, 16, 1, true, false);
    private static final AudioFormat[] FORMATOS_CANDIDATOS = {
            FORMATO_VOSK,
            new AudioFormat(48000, 16, 1, true, false),
            new AudioFormat(44100, 16, 1, true, false),
            new AudioFormat(48000, 16, 2, true, false),
            new AudioFormat(44100, 16, 2, true, false),
            new AudioFormat(22050, 16, 1, true, false),
            new AudioFormat(8000, 16, 1, true, false)
    };

    private DispositivosAudio() { }

    /** Nombres de los dispositivos que pueden proporcionar una señal utilizable. */
    public static List<String> listarMicrofonos() {
        Set<String> nombres = new LinkedHashSet<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo().length > 0 && soportaCaptura(mixer)) {
                nombres.add(info.getName());
            }
        }
        List<String> resultado = new ArrayList<>(nombres);
        resultado.sort(Comparator.naturalOrder());
        return resultado;
    }

    public static void seleccionarMicrofono(String nombre) {
        AppConfig.DISPOSITIVO_AUDIO_PREFERIDO = nombre == null ? "" : nombre.trim();
    }

    /** Abre el micrófono elegido; si no responde, usa el siguiente compatible. */
    public static Captura abrirCaptura() throws LineUnavailableException {
        List<Mixer> candidatos = new ArrayList<>();
        String preferido = AppConfig.DISPOSITIVO_AUDIO_PREFERIDO;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo().length == 0) continue;
            if (!preferido.isBlank() && info.getName().equals(preferido)) candidatos.add(mixer);
        }
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo().length == 0 || candidatos.contains(mixer)) continue;
            candidatos.add(mixer);
        }

        LineUnavailableException ultimoError = null;
        for (Mixer mixer : candidatos) {
            for (AudioFormat formato : FORMATOS_CANDIDATOS) {
                TargetDataLine linea = null;
                try {
                    DataLine.Info lineaInfo = new DataLine.Info(TargetDataLine.class, formato);
                    if (!mixer.isLineSupported(lineaInfo)) continue;
                    linea = (TargetDataLine) mixer.getLine(lineaInfo);
                    linea.open(formato);
                    AudioInputStream origen = new AudioInputStream(linea);
                    AudioInputStream convertido = formato.matches(FORMATO_VOSK)
                            ? origen : AudioSystem.getAudioInputStream(FORMATO_VOSK, origen);
                    return new Captura(linea, convertido, mixer.getMixerInfo().getName());
                } catch (LineUnavailableException ex) {
                    ultimoError = ex;
                } catch (IllegalArgumentException ex) {
                    // El proveedor no puede convertir este formato; se prueba el siguiente.
                } finally {
                    if (linea != null && !linea.isOpen()) linea.close();
                }
            }
        }
        throw ultimoError != null ? ultimoError
                : new LineUnavailableException("No se encontró un micrófono compatible. Revise los permisos de privacidad de Windows.");
    }

    private static boolean soportaCaptura(Mixer mixer) {
        for (AudioFormat formato : FORMATOS_CANDIDATOS) {
            if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, formato))) return true;
        }
        return false;
    }

    /** Captura con salida siempre compatible con el reconocedor de voz. */
    public static final class Captura implements AutoCloseable {
        private final TargetDataLine linea;
        private final AudioInputStream flujo;
        private final String nombreDispositivo;

        private Captura(TargetDataLine linea, AudioInputStream flujo, String nombreDispositivo) {
            this.linea = linea;
            this.flujo = flujo;
            this.nombreDispositivo = nombreDispositivo;
        }

        public void iniciar() { if (!linea.isRunning()) linea.start(); }
        /**
         * Descarta el audio que quedó en el búfer de la línea (por ejemplo, la voz guía
         * o ruido grabado antes de detener). Así la respuesta empieza "limpia".
         */
        public void descartarAudioPendiente() {
            linea.flush();
            try {
                int disponibles = flujo.available();
                if (disponibles > 0) flujo.skip(disponibles);
            } catch (java.io.IOException ignored) { }
        }
        public void detener() { if (linea.isRunning()) linea.stop(); }
        public int leer(byte[] buffer) throws java.io.IOException { return flujo.read(buffer, 0, buffer.length); }
        public String getNombreDispositivo() { return nombreDispositivo; }

        @Override public void close() {
            try { flujo.close(); } catch (Exception ignored) { }
            detener();
            linea.close();
        }
    }
}
