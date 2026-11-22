package com.eyesofter.config;
import java.io.File;

import java.io.FileInputStream;

import java.io.FileOutputStream;

import java.io.IOException;

import java.util.Properties;



/**

 * Configuración global y centralizada de rutas y parámetros para EYESOFTER.

 *

 * CAMBIO IMPORTANTE respecto a la versión original: los campos ya NO son

 * "final". Siguen siendo estáticos (por lo que todo el código existente que

 * hace referencia a AppConfig.PPI_ESTANDAR_MONITOR, AppConfig.RUTA_MODELO_VOSK,

 * etc. sigue compilando exactamente igual), pero ahora se pueden modificar en

 * tiempo de ejecución desde la pantalla de "Ajustes Generales" y persisten

 * entre ejecuciones gracias a cargar()/guardar().

 */

public class AppConfig {



// Apuntamos al modelo small exacto que tienes en tu carpeta modelo_vosk

    public static String RUTA_MODELO_VOSK = "modelo_vosk/vosk-model-small-es-0.42";

    public static String RUTA_MODELO_OPENCV = "modelo_opencv/haarcascade_frontalface_default.xml";

    /** Clasificador de ojos para la estimación de DP; se distribuye con la app. */
    public static String RUTA_MODELO_OJOS_OPENCV = "modelo_opencv/haarcascade_eye_tree_eyeglasses.xml";

    /**
     * Distancia focal de la cámara en píxeles, obtenida mediante calibración.
     * Cero significa "no calibrada" y evita reportar una DP numérica.
     */
    public static double DISTANCIA_FOCAL_CAMARA_PX = 0.0;

    /**
     * Detector de rostro con puntos de los ojos (YuNet, licencia MIT, ~230 KB).
     * Si falta, se usa el método de respaldo (ancho del rostro con Haar).
     */
    public static String RUTA_MODELO_YUNET = "modelo_opencv/face_detection_yunet_2023mar.onnx";

    /**
     * Focal de la cámara dividida entre el ancho del fotograma (adimensional), así no
     * depende de la resolución con que se abra la cámara. 0 = sin calibrar.
     * Reemplaza a DISTANCIA_FOCAL_CAMARA_PX, que mezclaba la cámara con el tamaño
     * de la cara de quien calibró.
     */
    public static double FOCAL_CAMARA_NORMALIZADA = 0.0;
    /** Método con que se calibró la focal ("OJOS" o "ROSTRO"): solo vale para el mismo método. */
    public static String METODO_FOCAL_CAMARA = "";

    /** Cómo se obtuvo el PPI: TARJETA, DIAGONAL o MANUAL. */
    public static String METODO_PPI = "DIAGONAL";
    /** Identificador de la pantalla donde se midió el PPI (resolución, posición y escalado). */
    public static String PANTALLA_CALIBRADA = "";



// Rutas del sintetizador de voz (Piper), usadas por SintetizadorVoz

    public static String RUTA_PIPER_EXE = "modelo_tts/piper.exe";

    public static String RUTA_MODELO_VOZ_TTS = "modelo_tts/es_MX-claude-high.onnx";



// Parámetros clínicos y de hardware por defecto

    public static double PPI_ESTANDAR_MONITOR = 96.0;

    public static int TIEMPO_ESPERA_VOZ_SEGUNDOS = 5;

    public static double TOLERANCIA_DISTANCIA_CM = 15.0;

    public static double DISTANCIA_OBJETIVO_METROS_DEFECTO = 1.0;



// Nuevos parámetros (factor 2 y 3 del tamaño de optotipos)

    public static double MONITOR_DIAGONAL_PULGADAS = 15.6;

    public static String FUENTE_OPTOTIPO_DEFECTO = "Arial";

    public static String DISPOSITIVO_AUDIO_PREFERIDO = "";
    /** Índice de la cámara elegida en la comprobación de periféricos. */
    public static int INDICE_CAMARA_PREFERIDA = 0;
    public static boolean TEMA_OSCURO = false;

    // Conexión con EYESOFTER Suite (servidor de la app móvil y del portal web)
    /** Dirección base del servidor de EYESOFTER Suite. */
    public static String URL_API_SUITE = "https://eyesofter-api.up.railway.app";
    /**
     * Token de ESTE equipo. Se obtiene al vincularlo: alguien de Dirección o Enfermería inicia
     * sesión en el navegador con su cuenta del portal y lo autoriza. Vacío = equipo sin vincular,
     * sin acceso a ningún dato de la escuela. Ya no existe una clave compartida.
     */
    public static String TOKEN_EQUIPO = "";
    /** Quién autorizó este equipo (correo de la cuenta del portal), para mostrarlo en Configuración. */
    public static String VINCULADO_POR = "";
    /** Nombre del equipo que aparece en las alertas de sincronización. */
    public static String NOMBRE_EQUIPO = "Enfermería PC1";



// Configuración de la Base de Datos Local

    public static String URL_BASE_DATOS = "jdbc:sqlite:eyesofter_local.db";



    private static final File DIRECTORIO_APLICACION = new File(System.getProperty("eyesofter.home", "."));
    private static final String ARCHIVO_PROPIEDADES = "eyesofter_config.properties";

    private static boolean cargado = false;



    private AppConfig() {}



    /** Carga los valores desde eyesofter_config.properties si existe (llamar una vez al iniciar la app). */

    public static synchronized void cargar() {

        File archivo = new File(DIRECTORIO_APLICACION, ARCHIVO_PROPIEDADES);

        if (!archivo.exists()) {

            cargado = true;

            return;

        }

        Properties p = new Properties();

        try (FileInputStream in = new FileInputStream(archivo)) {

            p.load(in);

            RUTA_MODELO_VOSK = p.getProperty("rutaModeloVosk", RUTA_MODELO_VOSK);

            RUTA_MODELO_OPENCV = p.getProperty("rutaModeloOpenCV", RUTA_MODELO_OPENCV);

            RUTA_MODELO_OJOS_OPENCV = p.getProperty("rutaModeloOjosOpenCV", RUTA_MODELO_OJOS_OPENCV);

            DISTANCIA_FOCAL_CAMARA_PX = parseDouble(p.getProperty("distanciaFocalCamaraPx"), DISTANCIA_FOCAL_CAMARA_PX);
            RUTA_MODELO_YUNET = p.getProperty("rutaModeloYunet", RUTA_MODELO_YUNET);
            FOCAL_CAMARA_NORMALIZADA = parseDouble(p.getProperty("focalCamaraNormalizada"), FOCAL_CAMARA_NORMALIZADA);
            METODO_FOCAL_CAMARA = p.getProperty("metodoFocalCamara", METODO_FOCAL_CAMARA);
            METODO_PPI = p.getProperty("metodoPpi", METODO_PPI);
            PANTALLA_CALIBRADA = p.getProperty("pantallaCalibrada", PANTALLA_CALIBRADA);

            RUTA_PIPER_EXE = p.getProperty("rutaPiperExe", RUTA_PIPER_EXE);

            RUTA_MODELO_VOZ_TTS = p.getProperty("rutaModeloVozTts", RUTA_MODELO_VOZ_TTS);

            PPI_ESTANDAR_MONITOR = parseDouble(p.getProperty("ppiEstandar"), PPI_ESTANDAR_MONITOR);

            TIEMPO_ESPERA_VOZ_SEGUNDOS = (int) parseDouble(p.getProperty("tiempoEsperaVoz"), TIEMPO_ESPERA_VOZ_SEGUNDOS);

            TOLERANCIA_DISTANCIA_CM = parseDouble(p.getProperty("toleranciaDistanciaCm"), TOLERANCIA_DISTANCIA_CM);

            DISTANCIA_OBJETIVO_METROS_DEFECTO = parseDouble(p.getProperty("distanciaObjetivoMetros"), DISTANCIA_OBJETIVO_METROS_DEFECTO);

            MONITOR_DIAGONAL_PULGADAS = parseDouble(p.getProperty("monitorDiagonalPulgadas"), MONITOR_DIAGONAL_PULGADAS);

            FUENTE_OPTOTIPO_DEFECTO = p.getProperty("fuenteOptotipoDefecto", FUENTE_OPTOTIPO_DEFECTO);

            DISPOSITIVO_AUDIO_PREFERIDO = p.getProperty("dispositivoAudioPreferido", DISPOSITIVO_AUDIO_PREFERIDO);
            INDICE_CAMARA_PREFERIDA = (int) parseDouble(p.getProperty("indiceCamaraPreferida"), INDICE_CAMARA_PREFERIDA);
            TEMA_OSCURO = Boolean.parseBoolean(p.getProperty("temaOscuro", String.valueOf(TEMA_OSCURO)));

            URL_BASE_DATOS = p.getProperty("urlBaseDatos", URL_BASE_DATOS);
            URL_API_SUITE = p.getProperty("urlApiSuite", URL_API_SUITE);
            // "claveApiEscritorio" (la antigua clave compartida) se ignora a propósito y se borra al guardar.
            TOKEN_EQUIPO = p.getProperty("tokenEquipo", TOKEN_EQUIPO);
            VINCULADO_POR = p.getProperty("vinculadoPor", VINCULADO_POR);
            NOMBRE_EQUIPO = p.getProperty("nombreEquipo", NOMBRE_EQUIPO);

        } catch (IOException e) {

            System.err.println("[CONFIG]: No se pudo leer " + ARCHIVO_PROPIEDADES + ", se usan valores por defecto.");

        }

        cargado = true;

    }



    /** Guarda los valores actuales en eyesofter_config.properties (llamar desde la pantalla de Ajustes). */

    public static synchronized void guardar() {

        Properties p = new Properties();

        p.setProperty("rutaModeloVosk", RUTA_MODELO_VOSK);

        p.setProperty("rutaModeloOpenCV", RUTA_MODELO_OPENCV);

        p.setProperty("rutaModeloOjosOpenCV", RUTA_MODELO_OJOS_OPENCV);

        p.setProperty("distanciaFocalCamaraPx", String.valueOf(DISTANCIA_FOCAL_CAMARA_PX));
        p.setProperty("rutaModeloYunet", RUTA_MODELO_YUNET);
        p.setProperty("focalCamaraNormalizada", String.valueOf(FOCAL_CAMARA_NORMALIZADA));
        p.setProperty("metodoFocalCamara", METODO_FOCAL_CAMARA);
        p.setProperty("metodoPpi", METODO_PPI);
        p.setProperty("pantallaCalibrada", PANTALLA_CALIBRADA);

        p.setProperty("rutaPiperExe", RUTA_PIPER_EXE);

        p.setProperty("rutaModeloVozTts", RUTA_MODELO_VOZ_TTS);

        p.setProperty("ppiEstandar", String.valueOf(PPI_ESTANDAR_MONITOR));

        p.setProperty("tiempoEsperaVoz", String.valueOf(TIEMPO_ESPERA_VOZ_SEGUNDOS));

        p.setProperty("toleranciaDistanciaCm", String.valueOf(TOLERANCIA_DISTANCIA_CM));

        p.setProperty("distanciaObjetivoMetros", String.valueOf(DISTANCIA_OBJETIVO_METROS_DEFECTO));

        p.setProperty("monitorDiagonalPulgadas", String.valueOf(MONITOR_DIAGONAL_PULGADAS));

        p.setProperty("fuenteOptotipoDefecto", FUENTE_OPTOTIPO_DEFECTO);

        p.setProperty("dispositivoAudioPreferido", DISPOSITIVO_AUDIO_PREFERIDO);
        p.setProperty("indiceCamaraPreferida", String.valueOf(INDICE_CAMARA_PREFERIDA));
        p.setProperty("temaOscuro", String.valueOf(TEMA_OSCURO));

        p.setProperty("urlBaseDatos", URL_BASE_DATOS);
        p.setProperty("urlApiSuite", URL_API_SUITE);
        p.setProperty("tokenEquipo", TOKEN_EQUIPO);
        p.setProperty("vinculadoPor", VINCULADO_POR);
        p.setProperty("nombreEquipo", NOMBRE_EQUIPO);

        try (FileOutputStream out = new FileOutputStream(new File(DIRECTORIO_APLICACION, ARCHIVO_PROPIEDADES))) {

            p.store(out, "Configuracion EYESOFTER (generado automaticamente)");

        } catch (IOException e) {

            System.err.println("[CONFIG ERROR]: No se pudo guardar " + ARCHIVO_PROPIEDADES);

        }

    }



    public static boolean estaCargado() {

        return cargado;

    }



    private static double parseDouble(String valor, double porDefecto) {

        if (valor == null) return porDefecto;

        try {

            return Double.parseDouble(valor);

        } catch (NumberFormatException e) {

            return porDefecto;

        }

    }

}
