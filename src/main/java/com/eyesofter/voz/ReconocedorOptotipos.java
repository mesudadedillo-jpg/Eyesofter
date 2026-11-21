package com.eyesofter.voz;


import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.CalculadoraWER;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.LineUnavailableException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconocimiento de voz local (Vosk) para letras Snellen y números Ishihara.
 *
 * <h3>Fluidez</h3>
 * <ul>
 *   <li>El modelo se carga una vez y se comparte entre pantallas ({@link #compartido()}).</li>
 *   <li>Al empezar a escuchar se vacía el búfer del micrófono: ya no se "oye" la
 *       voz guía ni ruido de hace segundos.</li>
 *   <li>Aceptación temprana: si el resultado parcial ya es una letra válida y se
 *       mantiene estable 350 ms, se responde sin esperar el silencio final de Vosk
 *       (ahorra 0.5–1 s por letra).</li>
 *   <li>{@link #cancelarEscucha()} corta la escucha en curso (pausa o cancelación).</li>
 * </ul>
 *
 * <h3>Precisión</h3>
 * <ul>
 *   <li>Gramática cerrada: Vosk solo puede elegir entre las palabras esperadas.</li>
 *   <li>Coincidencia exacta antes que distancia de Levenshtein; los empates se
 *       consideran ambiguos y se pide repetir.</li>
 *   <li>Variantes del español de México: "se" (C), "seta" (Z), "ef" (F)…</li>
 *   <li>Un "[unk]" aislado ya no anula una respuesta válida: solo se descarta
 *       cuando no hay ninguna palabra útil o el ruido supera el umbral WER.</li>
 * </ul>
 */
public class ReconocedorOptotipos {

    /** DIRECCIONES: E direccional ("arriba", "abajo", "izquierda", "derecha"). */
    public enum ModoPrueba { SNELLEN, ISHIHARA, DIRECCIONES }

    /** El paciente indica que no distingue ningún número (Ishihara). */
    public static final String RESPUESTA_NADA = "NADA";
    /** El paciente dice que no sabe / no ve la letra: se registra como no leída. */
    public static final String RESPUESTA_NO_SE = "NOSE";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String RUIDO = "RUIDO";
    public static final String ERROR = "ERROR";
    public static final String CANCELADO = "CANCELADO";

    private static final long ESTABILIDAD_PARCIAL_MS = 350;
    private static final int MAX_ALTERNATIVAS = 5;

    private static ReconocedorOptotipos compartido;

    private final Model model;
    private Recognizer recognizer;
    private DispositivosAudio.Captura microphone;
    private final int tiempoEsperaSegundos;
    private ModoPrueba modoActual;
    private volatile boolean cancelado;
    private volatile String ultimoTextoOido = "";

    /** Letra → formas habladas. La primera forma es la "oficial". */
    private static final Map<String, String[]> FORMAS_LETRAS = new LinkedHashMap<>();
    static {
        FORMAS_LETRAS.put("C", new String[]{"ce", "se", "ser"});
        FORMAS_LETRAS.put("D", new String[]{"de", "dé"});
        FORMAS_LETRAS.put("E", new String[]{"e", "eh"});
        FORMAS_LETRAS.put("F", new String[]{"efe", "ef", "fe"});
        FORMAS_LETRAS.put("L", new String[]{"ele", "ela"});
        FORMAS_LETRAS.put("O", new String[]{"o", "oh"});
        FORMAS_LETRAS.put("P", new String[]{"pe", "pé"});
        FORMAS_LETRAS.put("T", new String[]{"te", "té"});
        FORMAS_LETRAS.put("Z", new String[]{"zeta", "seta", "ceta", "zeda"});
    }

    /** Direcciones de la E: el código coincide con MotorSnellen.E_*. */
    private static final Map<String, String[]> FORMAS_DIRECCIONES = new LinkedHashMap<>();
    static {
        FORMAS_DIRECCIONES.put("ARRIBA", new String[]{"arriba", "riba", "para arriba", "pa arriba", "hacia arriba"});
        FORMAS_DIRECCIONES.put("ABAJO", new String[]{"abajo", "bajo", "para abajo", "pa abajo", "hacia abajo"});
        FORMAS_DIRECCIONES.put("IZQUIERDA", new String[]{"izquierda", "a la izquierda", "quierda"});
        FORMAS_DIRECCIONES.put("DERECHA", new String[]{"derecha", "a la derecha", "derecho"});
    }

    private static final Map<String, String[]> FORMAS_NUMEROS = new LinkedHashMap<>();
    static {
        FORMAS_NUMEROS.put("2", new String[]{"dos"});
        FORMAS_NUMEROS.put("3", new String[]{"tres"});
        FORMAS_NUMEROS.put("5", new String[]{"cinco"});
        FORMAS_NUMEROS.put("6", new String[]{"seis"});
        FORMAS_NUMEROS.put("8", new String[]{"ocho"});
        FORMAS_NUMEROS.put("12", new String[]{"doce"});
        FORMAS_NUMEROS.put("15", new String[]{"quince"});
        FORMAS_NUMEROS.put("21", new String[]{"veintiuno", "veinte y uno", "veinte uno"});
        FORMAS_NUMEROS.put("29", new String[]{"veintinueve"});
        FORMAS_NUMEROS.put("42", new String[]{"cuarenta y dos", "cuarenta dos"});
        FORMAS_NUMEROS.put("45", new String[]{"cuarenta y cinco", "cuarenta cinco"});
        FORMAS_NUMEROS.put("74", new String[]{"setenta y cuatro", "setenta cuatro"});
    }

    private static final Set<String> MULETILLAS = new HashSet<>(Arrays.asList(
            "este", "la", "letra", "el", "numero", "número", "es", "una", "un", "minuscula", "mayuscula",
            "creo", "que", "veo", "yo", "pues", "mmm", "ah"
    ));

    /** Palabras de la gramática que nunca deben convertirse en letra por parecido. */
    private static final Set<String> NO_SON_LETRAS = new HashSet<>(Arrays.asList("no", "lo", "veo", "paso", "siguiente", "hay"));

    private static final String[] FRASES_NO_SE = {"no se", "no sé", "no lo se", "no lo sé", "no la veo", "no veo", "paso", "siguiente"};
    private static final String[] FRASES_NADA = {"nada", "ninguno", "ningun numero", "ningún número", "no hay", "no veo nada"};

    private static final String VOCABULARIO_SNELLEN = construirGramatica(FORMAS_LETRAS,
            "no", "se", "lo", "la", "veo", "paso", "siguiente", "este", "letra", "es", "una", "creo", "que");
    private static final String VOCABULARIO_DIRECCIONES = construirGramatica(FORMAS_DIRECCIONES,
            "no", "se", "lo", "la", "veo", "paso", "siguiente", "para", "pa", "hacia", "a", "creo", "que");
    private static final String VOCABULARIO_ISHIHARA = construirGramatica(FORMAS_NUMEROS,
            "nada", "ninguno", "no", "se", "hay", "veo", "numero", "es", "el", "este", "creo", "que", "y");

    private static String construirGramatica(Map<String, String[]> formas, String... extras) {
        Set<String> palabras = new java.util.LinkedHashSet<>();
        for (String[] variantes : formas.values()) palabras.addAll(Arrays.asList(variantes));
        palabras.addAll(Arrays.asList(extras));
        StringBuilder json = new StringBuilder("[");
        for (String p : palabras) json.append('"').append(p).append("\", ");
        json.append("\"[unk]\"]");
        return json.toString();
    }

    public ReconocedorOptotipos(String pathModelo) throws RuntimeException {
        try {
            this.model = new Model(pathModelo);
            this.tiempoEsperaSegundos = AppConfig.estaCargado() ? Math.max(2, AppConfig.TIEMPO_ESPERA_VOZ_SEGUNDOS) : 5;
            microphone = DispositivosAudio.abrirCaptura();
            System.out.println("[AUDIO]: Micrófono seleccionado: " + microphone.getNombreDispositivo());
            setModo(ModoPrueba.SNELLEN);
        } catch (IOException | LineUnavailableException e) {
            throw new RuntimeException("CRÍTICO: Fallo al inicializar el hardware acústico local.", e);
        }
    }

    /**
     * Instancia compartida: el modelo Vosk (≈ 50 MB) se carga una sola vez por ejecución.
     * Si el micrófono se desconectó, se vuelve a crear.
     */
    public static synchronized ReconocedorOptotipos compartido() {
        if (compartido == null) compartido = new ReconocedorOptotipos(AppConfig.RUTA_MODELO_VOSK);
        return compartido;
    }

    /** Carga el modelo en segundo plano para que la primera prueba no espere. */
    public static void precargarEnSegundoPlano() {
        Thread hilo = new Thread(() -> {
            try { compartido(); } catch (Throwable t) { System.err.println("[VOZ]: precarga no disponible: " + t.getMessage()); }
        }, "precarga-vosk");
        hilo.setDaemon(true);
        hilo.setPriority(Thread.MIN_PRIORITY);
        hilo.start();
    }

    public static synchronized void liberarCompartido() {
        if (compartido != null) { compartido.liberarInterno(); compartido = null; }
    }

    public synchronized void setModo(ModoPrueba nuevoModo) {
        if (nuevoModo == modoActual && recognizer != null) return;
        this.modoActual = nuevoModo;
        String vocabulario = switch (nuevoModo) {
            case SNELLEN -> VOCABULARIO_SNELLEN;
            case DIRECCIONES -> VOCABULARIO_DIRECCIONES;
            default -> VOCABULARIO_ISHIHARA;
        };
        try {
            if (this.recognizer != null) this.recognizer.close();
            this.recognizer = new Recognizer(this.model, 16000, vocabulario);
            this.recognizer.setMaxAlternatives(MAX_ALTERNATIVAS);
        } catch (Exception e) {
            System.err.println("⚠️ [VOZ]: Error al cambiar el vocabulario del reconocedor: " + e.getMessage());
        }
    }

    public ModoPrueba getModo() { return modoActual; }

    /** Texto bruto de la última respuesta (para mostrar "Escuché: …"). */
    public String getUltimoTextoOido() { return ultimoTextoOido; }

    /** Interrumpe la escucha en curso (devuelve {@link #CANCELADO}). */
    public void cancelarEscucha() { cancelado = true; }

    /** Nivel de audio instantáneo (0-100 aprox.) para el medidor de ruido de "Verificación de Periféricos". */
    public int leerNivelMicrofono(byte[] bufferReutilizable) {
        if (microphone == null) return 0;
        microphone.iniciar();
        int leidos;
        try {
            leidos = microphone.leer(bufferReutilizable);
        } catch (IOException e) {
            return 0;
        }
        return nivelRms(bufferReutilizable, leidos);
    }

    static int nivelRms(byte[] buffer, int leidos) {
        double suma = 0;
        int muestras = 0;
        for (int i = 0; i + 1 < leidos; i += 2) {
            short muestra = (short) ((buffer[i + 1] << 8) | (buffer[i] & 0xFF));
            suma += (double) muestra * muestra;
            muestras++;
        }
        if (muestras == 0) return 0;
        double rms = Math.sqrt(suma / muestras) / 32768.0;
        // Escala logarítmica: -60 dBFS → 0, 0 dBFS → 100.
        double db = 20 * Math.log10(Math.max(rms, 1e-6));
        return (int) Math.max(0, Math.min(100, (db + 60) * 100 / 60));
    }

    /** Escucha una sola respuesta (letra, número, NADA, NOSE, TIMEOUT, RUIDO, CANCELADO o ERROR). */
    public synchronized String escucharOptotipo() {
        cancelado = false;
        ultimoTextoOido = "";
        recognizer.reset();
        microphone.descartarAudioPendiente();
        microphone.iniciar();
        long inicio = System.currentTimeMillis();
        long limite = tiempoEsperaSegundos * 1000L;
        byte[] buffer = new byte[3200]; // 100 ms a 16 kHz mono 16 bits
        String candidatoParcial = null;
        long instanteCandidato = 0;

        try {
            while (true) {
                if (cancelado) return CANCELADO;
                long ahora = System.currentTimeMillis();
                if (ahora - inicio > limite) {
                    String finalTexto = procesarAudio(recognizer.getFinalResult());
                    return RUIDO.equals(finalTexto) ? TIMEOUT : finalTexto;
                }

                int bytesRead = microphone.leer(buffer);
                if (bytesRead <= 0) continue;

                if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                    String resultado = procesarAudio(recognizer.getResult());
                    if (!RUIDO.equals(resultado)) return resultado;
                    // Enunciado vacío o con solo ruido: se sigue escuchando hasta el límite.
                    candidatoParcial = null;
                    continue;
                }

                String parcial = extraerParcial(recognizer.getPartialResult());
                if (parcial.isEmpty()) continue;
                String interpretado = interpretar(parcial);
                if (RUIDO.equals(interpretado) || esPrefijoIncompleto(modoActual, parcial)) {
                    candidatoParcial = null;
                    continue;
                }
                if (!interpretado.equals(candidatoParcial)) {
                    candidatoParcial = interpretado;
                    instanteCandidato = ahora;
                } else if (ahora - instanteCandidato >= ESTABILIDAD_PARCIAL_MS) {
                    ultimoTextoOido = parcial;
                    return interpretado;
                }
            }
        } catch (IOException e) {
            return ERROR;
        } finally {
            microphone.detener();
        }
    }

    /**
     * Escucha hasta {@code cantidad} letras dichas en una o varias frases
     * ("ce, de, o" o una por una). Devuelve las letras reconocidas en orden;
     * las no entendidas se marcan con '?'.
     */
    public List<String> escucharSecuencia(int cantidad) {
        List<String> letras = new ArrayList<>();
        int fallos = 0;
        while (letras.size() < cantidad && !cancelado && fallos < cantidad + 1) {
            String r = escucharOptotipo();
            if (CANCELADO.equals(r) || ERROR.equals(r)) break;
            if (TIMEOUT.equals(r) || RUIDO.equals(r)) { fallos++; if (TIMEOUT.equals(r)) break; continue; }
            if (r.contains(",")) letras.addAll(Arrays.asList(r.split(",")));
            else letras.add(r);
        }
        while (letras.size() > cantidad) letras.remove(letras.size() - 1);
        return letras;
    }

    private static String extraerParcial(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            return o.has("partial") ? o.get("partial").getAsString().trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Un parcial que todavía puede continuar no se acepta: "no" (→ "no sé"),
     * y en Ishihara "cuarenta…", "setenta…", "veinte…".
     */
    private static boolean esPrefijoIncompleto(ModoPrueba modo, String texto) {
        String t = texto.trim();
        if (t.equals("no") || t.endsWith(" no") || t.endsWith("no lo")) return true;
        return modo == ModoPrueba.ISHIHARA
                && (t.endsWith("cuarenta") || t.endsWith("setenta") || t.endsWith("veinte") || t.endsWith(" y"));
    }

    private String procesarAudio(String jsonStr) {
        List<String> hipotesis = new ArrayList<>();
        try {
            JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();
            if (jsonObject.has("alternatives") && jsonObject.get("alternatives").isJsonArray()) {
                JsonArray alternativas = jsonObject.getAsJsonArray("alternatives");
                for (int i = 0; i < alternativas.size(); i++) {
                    JsonObject alt = alternativas.get(i).getAsJsonObject();
                    if (alt.has("text")) hipotesis.add(alt.get("text").getAsString().trim());
                }
            } else if (jsonObject.has("text")) {
                hipotesis.add(jsonObject.get("text").getAsString().trim());
            }
        } catch (Exception e) {
            return RUIDO;
        }
        hipotesis.removeIf(String::isEmpty);
        if (hipotesis.isEmpty()) return RUIDO;

        String principal = hipotesis.get(0);
        // WER sobre la hipótesis principal: proporción de palabras desconocidas.
        String[] palabras = principal.split("\\s+");
        int desconocidas = 0;
        for (String p : palabras) if (p.equals("[unk]")) desconocidas++;
        double wer = CalculadoraWER.calcularWER(0, 0, desconocidas, Math.max(1, palabras.length));
        if (desconocidas == palabras.length || CalculadoraWER.esAmbienteInestable(wer) && desconocidas > 1) {
            System.out.printf("⚠️ [VOZ]: Ruido ambiental (WER %.2f)%n", wer);
            ultimoTextoOido = principal;
            return RUIDO;
        }

        for (String h : hipotesis) {
            String r = interpretar(h);
            if (!RUIDO.equals(r)) { ultimoTextoOido = h; return r; }
        }
        ultimoTextoOido = principal;
        return RUIDO;
    }

    /** Interpreta un texto según el modo actual. */
    String interpretar(String texto) {
        return interpretarSinModelo(modoActual, texto);
    }

    private static String sinMuletillas(String frase) {
        StringBuilder r = new StringBuilder();
        for (String p : frase.split(" ")) if (!MULETILLAS.contains(p)) r.append(p).append(' ');
        return r.toString().trim();
    }

    /**
     * Convierte una o varias letras habladas. Si se dicen varias ("ce de o"),
     * se devuelven separadas por comas ("C,D,O").
     */
    private static String mapearLetras(String texto) {
        if (texto.isEmpty()) return RUIDO;
        List<String> letras = new ArrayList<>();
        for (String palabra : texto.split(" ")) {
            if (NO_SON_LETRAS.contains(palabra)) continue;
            String letra = mejorCoincidencia(palabra, FORMAS_LETRAS, 1);
            if (letra != null) letras.add(letra);
        }
        if (letras.isEmpty()) return RUIDO;
        return String.join(",", letras);
    }

    /** Una o varias direcciones habladas ("arriba", "para abajo") → "ARRIBA" o "ARRIBA,ABAJO". */
    private static String mapearDirecciones(String texto) {
        String limpio = (" " + texto + " ").replace(" para ", " ").replace(" pa ", " ").replace(" hacia ", " ")
                .replace(" a la ", " ").replace(" creo que ", " ").trim();
        if (limpio.isEmpty()) return RUIDO;
        List<String> direcciones = new ArrayList<>();
        for (String palabra : limpio.split(" ")) {
            if (NO_SON_LETRAS.contains(palabra)) continue;
            String d = mejorCoincidencia(palabra, FORMAS_DIRECCIONES, 2);
            if (d != null) direcciones.add(d);
        }
        return direcciones.isEmpty() ? RUIDO : String.join(",", direcciones);
    }

    private static String mapearNumero(String texto) {
        if (texto.isEmpty()) return RUIDO;
        String exacto = coincidenciaExacta(texto, FORMAS_NUMEROS);
        if (exacto != null) return exacto;
        String aproximado = mejorCoincidencia(texto, FORMAS_NUMEROS, 2);
        return aproximado == null ? RUIDO : aproximado;
    }

    private static String coincidenciaExacta(String texto, Map<String, String[]> formas) {
        for (Map.Entry<String, String[]> e : formas.entrySet())
            for (String f : e.getValue()) if (f.equals(texto)) return e.getKey();
        return null;
    }

    /**
     * Coincidencia exacta primero; si no hay, la forma más cercana (Levenshtein) dentro
     * del umbral. Un empate entre valores distintos se considera ambiguo (null).
     */
    private static String mejorCoincidencia(String texto, Map<String, String[]> formas, int umbral) {
        String exacto = coincidenciaExacta(texto, formas);
        if (exacto != null) return exacto;
        int menor = Integer.MAX_VALUE;
        String mejor = null;
        boolean empate = false;
        for (Map.Entry<String, String[]> e : formas.entrySet()) {
            for (String f : e.getValue()) {
                int d = levenshtein(texto, f);
                if (d < menor) { menor = d; mejor = e.getKey(); empate = false; }
                else if (d == menor && !e.getKey().equals(mejor)) empate = true;
            }
        }
        if (empate || mejor == null || menor > umbral || texto.length() <= menor) return null;
        return mejor;
    }

    static int levenshtein(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) dp[i][j] = j;
                else if (j == 0) dp[i][j] = i;
                else {
                    int costo = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                    dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + costo);
                }
            }
        }
        return dp[s1.length()][s2.length()];
    }

    /**
     * Antes cerraba el modelo en cada pantalla. Ahora, si es la instancia
     * compartida, solo detiene la escucha (el modelo sigue cargado para la siguiente prueba).
     */
    public void liberar() {
        cancelarEscucha();
        if (this == compartido) return;
        liberarInterno();
    }

    private void liberarInterno() {
        cancelado = true;
        synchronized (this) {
            try {
                if (microphone != null) microphone.close();
                if (recognizer != null) recognizer.close();
                if (model != null) model.close();
            } catch (Exception e) {
                System.err.println("[VOZ]: Error al liberar recursos de audio.");
            }
        }
    }

    /** Solo para pruebas: intérprete sin modelo ni micrófono. */
    static String interpretarSinModelo(ModoPrueba modo, String texto) {
        String limpio = texto.toLowerCase().replace("[unk]", " ").replaceAll("\\s+", " ").trim();
        if (limpio.isEmpty()) return RUIDO;
        for (String frase : FRASES_NO_SE) if (limpio.contains(frase)) return modo == ModoPrueba.ISHIHARA ? RESPUESTA_NADA : RESPUESTA_NO_SE;
        if (modo == ModoPrueba.ISHIHARA) {
            for (String frase : FRASES_NADA) if (limpio.contains(frase)) return RESPUESTA_NADA;
            return mapearNumero(sinMuletillas(limpio));
        }
        if (modo == ModoPrueba.DIRECCIONES) return mapearDirecciones(limpio);
        return mapearLetras(sinMuletillas(limpio));
    }
}
