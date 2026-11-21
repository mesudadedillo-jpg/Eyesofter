package com.eyesofter.voz;


import com.eyesofter.config.AppConfig;
import com.eyesofter.formulas.CalculadoraWER;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Módulo acústico EYESOFTER multi-modal.
 * Soporta cambio dinámico de diccionarios (Snellen / Ishihara) en tiempo de ejecución.
 *
 * MEJORAS DE PRECISIÓN (respecto a la versión anterior):
 *  1) Se activa recognizer.setMaxAlternatives(...) y se evalúan TODAS las
 *     hipótesis que entrega Vosk (no solo la mejor) contra el diccionario de
 *     letras/números válidos; se toma la que tenga menor distancia de edición.
 *  2) Si dos letras/números distintos empatan en la distancia mínima (p. ej.
 *     "de" vs "te", que a menudo se confunden), YA NO se adivina: se responde
 *     "RUIDO" para que el controlador pida repetir. Esto reduce falsos
 *     positivos (aciertos/errores registrados incorrectamente) aunque cueste
 *     una repetición extra ocasional.
 *  3) Se agregan variantes fonéticas adicionales reales (no solo la forma
 *     "de diccionario") para mejorar el recall.
 *  4) El tiempo de espera ahora se toma de AppConfig.TIEMPO_ESPERA_VOZ_SEGUNDOS
 *     en vez de un valor fijo, para que sea ajustable desde "Ajustes".
 */
public class ReconocedorOptotipos {

    // Nuestro interruptor principal
    public enum ModoPrueba {
        SNELLEN, ISHIHARA
    }

    /** Sentinela devuelto cuando el paciente indica que no distingue ningún número (Ishihara). */
    public static final String RESPUESTA_NADA = "NADA";

    private Model model;
    private Recognizer recognizer;
    private DispositivosAudio.Captura microphone;
    private int tiempoEsperaSegundos;
    private ModoPrueba modoActual;
    private static final int MAX_ALTERNATIVAS = 5;

    // Diccionarios herméticos en formato JSON para Vosk
    private static final String VOCABULARIO_SNELLEN = "[\"ce\", \"de\", \"e\", \"efe\", \"ef\", \"ele\", \"el\", \"o\", \"pe\", \"te\", \"zeta\", \"seta\", " +
            "\"este\", \"eh\", \"la\", \"letra\", \"es\", \"una\", \"minuscula\", \"mayuscula\", \"creo\", \"que\", \"[unk]\"]";

    private static final String VOCABULARIO_ISHIHARA = "[\"dos\", \"cinco\", \"seis\", \"doce\", \"veintiuno\", \"cuarenta\", \"cuarenta dos\", \"cuarenta y dos\", \"setenta cuatro\", " +
            "\"nada\", \"no se\", \"no sé\", \"ninguno\", \"ningun numero\", " +
            "\"este\", \"eh\", \"el\", \"numero\", \"es\", \"creo\", \"que\", \"y\", \"[unk]\"]";

    // Teoría de conjuntos: Ampliamos las muletillas para incluir contextos de números
    private static final Set<String> MULETILLAS = new HashSet<>(Arrays.asList(
            "este", "eh", "la", "letra", "el", "numero", "es", "una", "un", "minuscula", "mayuscula", "creo", "que", "y"
    ));

    // Frases que en Ishihara equivalen a "no veo ningún número" (respuesta clínicamente válida)
    private static final Set<String> FRASES_NADA = new HashSet<>(Arrays.asList(
            "nada", "no se", "no sé", "ninguno", "ningun numero", "ningún número"
    ));

    public ReconocedorOptotipos(String pathModelo) throws RuntimeException {
        try {
            this.model = new Model(pathModelo);
            this.tiempoEsperaSegundos = AppConfig.estaCargado() ? AppConfig.TIEMPO_ESPERA_VOZ_SEGUNDOS : 5;
            validarMicrofono();
            // Por defecto, arranca en modo Snellen
            setModo(ModoPrueba.SNELLEN);
        } catch (IOException | LineUnavailableException e) {
            throw new RuntimeException("CRÍTICO: Fallo al inicializar el hardware acústico local.", e);
        }
    }

    /**
     * EL INTERRUPTOR: Cambia la red neuronal en vivo sin recargar el modelo.
     */
    public void setModo(ModoPrueba nuevoModo) {
        this.modoActual = nuevoModo;
        String vocabularioSeleccionado = (nuevoModo == ModoPrueba.SNELLEN) ? VOCABULARIO_SNELLEN : VOCABULARIO_ISHIHARA;

        // Destruye el grafo anterior y crea uno nuevo al instante
        try {
            if (this.recognizer != null) {
                this.recognizer.close();
            }
            this.recognizer = new Recognizer(this.model, 16000, vocabularioSeleccionado);
            this.recognizer.setMaxAlternatives(MAX_ALTERNATIVAS);
            System.out.println("⚙️ [SISTEMA]: Motor acústico cambiado a modo -> " + nuevoModo.name());
        } catch (Exception e) {
            System.err.println("⚠️ [CRÍTICO]: Error al cambiar el vocabulario del reconocedor.");
        }
    }

    private void validarMicrofono() throws LineUnavailableException {
        microphone = DispositivosAudio.abrirCaptura();
        System.out.println("[AUDIO]: Micrófono seleccionado: " + microphone.getNombreDispositivo());
    }

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
        long suma = 0;
        for (int i = 0; i + 1 < leidos; i += 2) {
            short muestra = (short) ((bufferReutilizable[i + 1] << 8) | (bufferReutilizable[i] & 0xFF));
            suma += Math.abs(muestra);
        }
        int muestras = Math.max(1, leidos / 2);
        double promedio = suma / (double) muestras;
        int nivel = (int) Math.min(100, (promedio / 32768.0) * 100 * 6); // *6 para que sea visualmente sensible
        return nivel;
    }

    public synchronized String escucharOptotipo() {
        recognizer.reset();
        microphone.iniciar();
        long tiempoInicio = System.currentTimeMillis();
        int bytesRead;
        byte[] buffer = new byte[4096];

        try {
            while (true) {
                if ((System.currentTimeMillis() - tiempoInicio) > (tiempoEsperaSegundos * 1000L)) {
                    String resultadoFinal = procesarAudio(recognizer.getFinalResult());
                    return resultadoFinal.equals("RUIDO") ? "TIMEOUT" : resultadoFinal;
                }

                bytesRead = microphone.leer(buffer);
                if (bytesRead > 0) {
                    if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                        return procesarAudio(recognizer.getResult());
                    }
                }
            }
        } catch (IOException e) {
            return "ERROR";
        } finally {
            // Evita que la siguiente pregunta empiece leyendo audio acumulado.
            microphone.detener();
        }
    }

    private String procesarAudio(String jsonStr) {
        JsonObject jsonObject = JsonParser.parseString(jsonStr).getAsJsonObject();

        // Reunimos TODAS las hipótesis que Vosk nos ofrece (texto principal + alternativas)
        java.util.List<String> hipotesis = new java.util.ArrayList<>();
        if (jsonObject.has("alternatives") && jsonObject.get("alternatives").isJsonArray()) {
            JsonArray alternativas = jsonObject.getAsJsonArray("alternatives");
            for (int i = 0; i < alternativas.size(); i++) {
                JsonObject alt = alternativas.get(i).getAsJsonObject();
                if (alt.has("text")) {
                    hipotesis.add(alt.get("text").getAsString().trim());
                }
            }
        } else if (jsonObject.has("text")) {
            hipotesis.add(jsonObject.get("text").getAsString().trim());
        }

        hipotesis.removeIf(String::isEmpty);
        if (hipotesis.isEmpty()) {
            return "RUIDO";
        }

        // Evaluamos la tasa WER solo sobre la hipótesis principal (la de mayor confianza)
        String textoPrincipal = hipotesis.get(0);
        int insercionesDesconocidas = contarOcurrencias(textoPrincipal, "[unk]");
        double tasaWER = CalculadoraWER.calcularWERPorInserciones(insercionesDesconocidas);

        if (CalculadoraWER.esAmbienteInestable(tasaWER)) {
            System.out.printf("⚠️ [ALERTA WER]: Saturación ambiental inestable detectada (WER: %.2f)\n", tasaWER);
            return "RUIDO";
        }

        // Enrutamiento matemático dependiendo del interruptor, probando cada hipótesis
        for (String hipotesisTexto : hipotesis) {
            String textoLimpio = limpiarMuletillas(hipotesisTexto);
            if (textoLimpio.isEmpty()) continue;

            String resultado = (modoActual == ModoPrueba.SNELLEN)
                    ? mapearFonemaSnellen(textoLimpio)
                    : mapearFonemaIshihara(textoLimpio);

            if (!resultado.equals("RUIDO")) {
                return resultado; // primera hipótesis que produce un match limpio y no ambiguo
            }
        }
        return "RUIDO";
    }

    private int contarOcurrencias(String texto, String subcadena) {
        int contador = 0;
        int indice = 0;
        while ((indice = texto.indexOf(subcadena, indice)) != -1) {
            contador++;
            indice += subcadena.length();
        }
        return contador;
    }

    private String limpiarMuletillas(String frase) {
        String[] palabras = frase.toLowerCase().split(" ");
        StringBuilder resultado = new StringBuilder();
        for (String palabra : palabras) {
            if (!MULETILLAS.contains(palabra) && !palabra.equals("[unk]")) {
                resultado.append(palabra).append(" ");
            }
        }
        return resultado.toString().trim();
    }

    /**
     * Resolución de Levenshtein para Letras.
     * IMPORTANTE: si dos letras distintas empatan en la menor distancia, se
     * considera AMBIGUO y se devuelve "RUIDO" en vez de arriesgar un acierto
     * o error mal registrado.
     */
    private String mapearFonemaSnellen(String fonemaLimpio) {
        if (fonemaLimpio.isEmpty()) return "RUIDO";
        String palabraPrincipal = fonemaLimpio.split(" ")[0];
        // NOTA: "el" (articulo) queda excluido a propósito: limpiarMuletillas() ya
        // lo filtra como palabra de relleno, así que nunca llegaría hasta aquí y
        // solo generaría un falso "empate" con "ele".
        String[] fonemasValidos = {"ce", "de", "e", "efe", "ef", "ele", "o", "pe", "te", "zeta", "seta"};
        String[] letrasReales = {"C", "D", "E", "F", "F", "L", "O", "P", "T", "Z", "Z"};

        return mejorMatchSinAmbiguedad(palabraPrincipal, fonemasValidos, letrasReales, 1);
    }

    /**
     * Resolución de Levenshtein para Números (Evalúa la frase completa).
     * Antes de comparar contra los números, se revisa si la respuesta
     * corresponde a "nada / no sé" (respuesta clínicamente válida en Ishihara).
     */
    private String mapearFonemaIshihara(String fonemaLimpio) {
        if (fonemaLimpio.isEmpty()) return "RUIDO";
        if (FRASES_NADA.contains(fonemaLimpio)) {
            return RESPUESTA_NADA;
        }

        String[] fonemasValidos = {"dos", "cinco", "seis", "doce", "veintiuno", "cuarenta y dos", "cuarenta dos", "setenta cuatro"};
        String[] numerosReales = {"2", "5", "6", "12", "21", "42", "42", "74"};

        return mejorMatchSinAmbiguedad(fonemaLimpio, fonemasValidos, numerosReales, 2);
    }

    /**
     * Busca la palabra de referencia más cercana (Levenshtein) dentro del umbral dado.
     * Si hay empate entre dos valores REALES distintos, se considera ambiguo -> "RUIDO".
     */
    private String mejorMatchSinAmbiguedad(String texto, String[] fonemasValidos, String[] valoresReales, int umbral) {
        int menorDistancia = Integer.MAX_VALUE;
        String mejorValor = null;
        boolean empateAmbiguo = false;

        for (int i = 0; i < fonemasValidos.length; i++) {
            int distancia = calcularDistanciaLevenshtein(texto, fonemasValidos[i]);
            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                mejorValor = valoresReales[i];
                empateAmbiguo = false;
            } else if (distancia == menorDistancia && !valoresReales[i].equals(mejorValor)) {
                empateAmbiguo = true;
            }
        }

        if (empateAmbiguo || mejorValor == null || menorDistancia > umbral) {
            return "RUIDO";
        }
        return mejorValor;
    }

    private int calcularDistanciaLevenshtein(String s1, String s2) {
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

    /** Libera el micrófono (llamar al cerrar la app / terminar la sesión). */
    public void liberar() {
        try {
            if (microphone != null) {
                microphone.close();
            }
            if (recognizer != null) recognizer.close();
            if (model != null) model.close();
        } catch (Exception e) {
            System.err.println("[VOZ]: Error al liberar recursos de audio.");
        }
    }
}
