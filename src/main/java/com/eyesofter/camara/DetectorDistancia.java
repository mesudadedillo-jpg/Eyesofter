package com.eyesofter.camara;

import com.eyesofter.formulas.CalibracionFocal;
import com.eyesofter.config.AppConfig;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.core.Size;
import org.opencv.videoio.Videoio;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Módulo de Visión Artificial.
 * Mide la distancia en tiempo real entre el paciente y el monitor
 * aplicando el modelo matemático Pinhole Camera y Semejanza de Triángulos.
 *
 * <h3>Cómo se mide</h3>
 * d = DP · f / p, donde p es la separación de los ojos en la imagen, f la focal de
 * la cámara y DP la distancia interpupilar de ESA persona.
 * <ul>
 *   <li>Los ojos se ubican con YuNet (OpenCV 4.9, modelo abierto de 230 KB que corre
 *       en cualquier CPU). Si falta el modelo se usa el ancho del rostro (Haar).</li>
 *   <li>La DP sale de la medición con cinta de la sesión, de la capturada por el
 *       operador o de la tabla por edad ({@link com.eyesofter.formulas.Antropometria}).
 *       Antes se suponía 55 mm para todos: un adulto parecía estar más cerca y un
 *       niño pequeño más lejos de lo real.</li>
 *   <li>La focal se guarda dividida entre el ancho del fotograma y se calibra con la
 *       DP de quien está frente a la cámara, así sirve para cualquier paciente.</li>
 *   <li>Las lecturas con la cabeza girada se descartan (los ojos se ven más juntos).</li>
 * </ul>
 */
public class DetectorDistancia {

    public enum CondicionPrueba {
        CORRECTA,
        SIN_ROSTRO,
        MULTIPLES_PERSONAS,
        DEMASIADO_CERCA,
        DEMASIADO_LEJOS,
        RUIDO_VISUAL,
        CAMARA_SIN_IMAGEN
    }

    /** Método con que se ubica a la persona. Una calibración solo vale para su mismo método. */
    public enum Metodo { OJOS, ROSTRO }

    private static Throwable errorFatalOpenCV = null;

    static {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (Throwable t) {
            errorFatalOpenCV = t;
        }
    }

    private VideoCapture camara;
    private CascadeClassifier detectorRostros;
    private FaceDetectorYN detectorYunet;
    private Metodo metodo = Metodo.ROSTRO;

    private double focalNormalizada = CalibracionFocal.FOCAL_NORMALIZADA_POR_DEFECTO;
    private boolean focalCalibrada;
    private double distanciaObjetivoCm = 100.0;
    private double dpPacienteMm = com.eyesofter.formulas.Antropometria.DP_ADULTO_MM;

    private final Mat ultimoFrame = new Mat();
    private double ultimaDistanciaCm = -1;
    private boolean ultimoRostroDetectado = false;
    private int ultimoNumeroRostros;
    private CondicionPrueba ultimaCondicion = CondicionPrueba.CAMARA_SIN_IMAGEN;
    private Mat grisAnterior;
    private static final double UMBRAL_RUIDO_VISUAL = 42.0;

    private long ultimoInstanteMuestraMs = -1;
    private long duracionMuestreadaMs;
    private long duracionEnRangoMs;
    private boolean ultimoEstadoEnRango;
    private int muestrasFiabilidad;
    private double sumaDistanciaCm;
    private double sumaCuadradosDistanciaCm;
    private int muestrasDistancia;

    private static void mostrarErrorProfundo(Throwable ex, String contexto) {
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alerta = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
            alerta.setTitle("Diagnóstico de Cámara EYESOFTER");
            alerta.setHeaderText("Fallo crítico: " + contexto);
            alerta.setContentText(ex.getMessage());

            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            ex.printStackTrace(pw);

            javafx.scene.control.TextArea areaTexto = new javafx.scene.control.TextArea(sw.toString());
            areaTexto.setEditable(false);
            areaTexto.setWrapText(true);
            areaTexto.setMaxWidth(Double.MAX_VALUE);
            areaTexto.setMaxHeight(Double.MAX_VALUE);

            javafx.scene.layout.Priority alto = javafx.scene.layout.Priority.ALWAYS;
            javafx.scene.layout.GridPane.setVgrow(areaTexto, alto);
            javafx.scene.layout.GridPane.setHgrow(areaTexto, alto);

            javafx.scene.layout.GridPane contenidoExpandible = new javafx.scene.layout.GridPane();
            contenidoExpandible.setMaxWidth(Double.MAX_VALUE);
            contenidoExpandible.add(areaTexto, 0, 0);

            alerta.getDialogPane().setExpandableContent(contenidoExpandible);
            alerta.getDialogPane().setExpanded(true);
            alerta.showAndWait();
        });
    }

    public DetectorDistancia(String rutaModeloHaar) {
        this(rutaModeloHaar, AppConfig.RUTA_MODELO_YUNET, AppConfig.INDICE_CAMARA_PREFERIDA);
    }

    public DetectorDistancia(String rutaModeloHaar, String rutaModeloYunet) {
        this(rutaModeloHaar, rutaModeloYunet, AppConfig.INDICE_CAMARA_PREFERIDA);
    }

    public DetectorDistancia(String rutaModeloHaar, String rutaModeloYunet, int indiceCamara) {
        if (errorFatalOpenCV != null) {
            mostrarErrorProfundo(errorFatalOpenCV, "La librería OpenCV no se pudo cargar en memoria.");
            throw new RuntimeException("Fallo estático de OpenCV");
        }

        try {
            File archivoModelo = new File(rutaModeloHaar);
            if (!archivoModelo.exists()) {
                throw new RuntimeException("CRÍTICO: No se encontró el XML de OpenCV en: " + archivoModelo.getAbsolutePath());
            }

            this.detectorRostros = new CascadeClassifier(archivoModelo.getAbsolutePath());
            if (this.detectorRostros.empty()) {
                throw new RuntimeException("CRÍTICO: El modelo Haar Cascade se cargó vacío o está corrupto.");
            }

            this.detectorYunet = cargarYunet(rutaModeloYunet);
            this.metodo = detectorYunet != null ? Metodo.OJOS : Metodo.ROSTRO;
            cargarCalibracionGuardada();

            this.camara = new VideoCapture(Math.max(0, indiceCamara));
            if (!camara.isOpened()) {
                throw new RuntimeException("CRÍTICO: No se pudo encender la cámara web índice " + indiceCamara);
            }
            // Más resolución = ojos mejor ubicados a 2-4 m. Si la cámara no la admite, se queda con la suya.
            camara.set(Videoio.CAP_PROP_FRAME_WIDTH, 1280);
            camara.set(Videoio.CAP_PROP_FRAME_HEIGHT, 720);
        } catch (Throwable t) {
            mostrarErrorProfundo(t, "Error al inicializar el constructor de DetectorDistancia");
            throw t;
        }
    }

    private static FaceDetectorYN cargarYunet(String ruta) {
        if (ruta == null || ruta.isBlank() || !new File(ruta).exists()) {
            System.err.println("[CÁMARA]: no se encontró el modelo YuNet; se usa el ancho del rostro (menos exacto).");
            return null;
        }
        try {
            return FaceDetectorYN.create(new File(ruta).getAbsolutePath(), "", new Size(320, 320), 0.75f, 0.3f, 50);
        } catch (Throwable t) {
            System.err.println("[CÁMARA]: no se pudo cargar YuNet (" + t.getMessage() + "); se usa el ancho del rostro.");
            return null;
        }
    }

    /** La calibración guardada solo se usa si se hizo con el mismo método (ojos o rostro). */
    private void cargarCalibracionGuardada() {
        if (AppConfig.FOCAL_CAMARA_NORMALIZADA > 0 && metodo.name().equals(AppConfig.METODO_FOCAL_CAMARA)) {
            focalNormalizada = AppConfig.FOCAL_CAMARA_NORMALIZADA;
            focalCalibrada = true;
        }
    }

    public static List<Integer> listarCamarasDisponibles() {
        if (errorFatalOpenCV != null) {
            mostrarErrorProfundo(errorFatalOpenCV, "Imposible listar cámaras. OpenCV falló al iniciar.");
            return new ArrayList<>();
        }

        List<Integer> indices = new ArrayList<>();
        for (int indice = 0; indice < 4; indice++) {
            try {
                VideoCapture captura = new VideoCapture(indice);
                if (captura.isOpened()) {
                    indices.add(indice);
                    captura.release();
                }
            } catch (Throwable t) {
                mostrarErrorProfundo(t, "Error al sondear la cámara índice " + indice);
            }
        }
        return indices;
    }

    public void setDistanciaObjetivo(double centimetros) {
        this.distanciaObjetivoCm = centimetros;
    }

    public double getDistanciaObjetivo() {
        return distanciaObjetivoCm;
    }

    /** DP de la persona evaluada (medida, capturada o estimada por edad). */
    public synchronized void setDpPacienteMm(double mm) {
        if (com.eyesofter.formulas.Antropometria.esDpPlausible(mm)) {
            dpPacienteMm = mm;
            historialDistancias.clear();
            distanciaSuavizada = -1;
        }
    }

    public synchronized double getDpPacienteMm() { return dpPacienteMm; }

    public Metodo getMetodo() { return metodo; }

    public synchronized void iniciarRegistroFiabilidad() {
        ultimoInstanteMuestraMs = -1;
        duracionMuestreadaMs = 0;
        duracionEnRangoMs = 0;
        ultimoEstadoEnRango = false;
        muestrasFiabilidad = 0;
        sumaDistanciaCm = 0;
        sumaCuadradosDistanciaCm = 0;
        muestrasDistancia = 0;
    }

    public boolean validarPosicionPaciente() {
        return evaluarCondicionPrueba() == CondicionPrueba.CORRECTA;
    }

    /** Un rostro detectado en coordenadas del fotograma original. */
    private record Rostro(Rect caja, double separacionOjosPx, boolean girado, Point ojoA, Point ojoB) { }

    /**
     * Evalúa la posición del paciente con el rostro principal.
     *
     *  - La distancia se filtra con la mediana de las últimas lecturas y luego
     *    un suavizado exponencial: desaparecen los saltos de ±10 cm entre cuadros.
     *  - Histéresis: para salir de rango hay que superar la tolerancia + 3 cm,
     *    así la prueba no se pausa por oscilaciones en el borde.
     *  - Se detecta una segunda persona si su rostro mide al menos el 55 % del principal.
     *  - Con la cabeza girada se conserva la última distancia válida.
     */
    public synchronized CondicionPrueba evaluarCondicionPrueba() {
        Mat frame = new Mat();
        Mat gris = new Mat();
        Mat reducido = new Mat();
        try {
            if (camara == null || !camara.read(frame) || frame.empty()) {
                registrarMuestraFiabilidad(false);
                ultimaCondicion = CondicionPrueba.CAMARA_SIN_IMAGEN;
                return ultimaCondicion;
            }
            frame.copyTo(ultimoFrame);
            anchoFotogramaPx = frame.cols();
            Imgproc.cvtColor(frame, gris, Imgproc.COLOR_BGR2GRAY);
            double escalaRuido = gris.cols() > ANCHO_RUIDO ? ANCHO_RUIDO / (double) gris.cols() : 1.0;
            Imgproc.resize(gris, reducido, new Size(), escalaRuido, escalaRuido, Imgproc.INTER_AREA);
            boolean ruidoVisual = hayRuidoVisual(reducido);

            List<Rostro> rostros = metodo == Metodo.OJOS ? detectarConYunet(frame) : detectarConHaar(gris);
            ultimoNumeroRostros = rostros.size();

            if (rostros.isEmpty()) {
                ultimoRostroDetectado = false;
                ultimoRostro = null;
                cuadrosSinRostro++;
                // Un parpadeo de la detección no borra la distancia: se conserva 2 cuadros.
                if (cuadrosSinRostro > 2) { ultimaDistanciaCm = -1; historialDistancias.clear(); distanciaSuavizada = -1; }
                registrarMuestraFiabilidad(false);
                ultimaCondicion = cuadrosSinRostro > 2 ? CondicionPrueba.SIN_ROSTRO : ultimaCondicion;
                if (ultimaCondicion == null) ultimaCondicion = CondicionPrueba.SIN_ROSTRO;
                return ultimaCondicion;
            }
            cuadrosSinRostro = 0;
            ultimoRostroDetectado = true;

            Rostro principal = seleccionarRostroPrincipal(rostros);
            ultimoRostro = principal;
            if (hayOtraPersona(rostros, principal)) {
                registrarMuestraFiabilidad(false);
                ultimaCondicion = CondicionPrueba.MULTIPLES_PERSONAS;
                return ultimaCondicion;
            }

            double distanciaActualCm;
            if (principal.girado() && distanciaSuavizada > 0) {
                distanciaActualCm = distanciaSuavizada;
            } else {
                agregarSeparacionParaCalibracion(principal.separacionOjosPx());
                double instantaneaCm = CalibracionFocal.distanciaMm(dpPacienteMm, focalNormalizada, anchoFotogramaPx,
                        principal.separacionOjosPx()) / 10.0;
                distanciaActualCm = filtrarDistancia(instantaneaCm);
                if (distanciaActualCm > 0) registrarDistancia(distanciaActualCm);
            }
            this.ultimaDistanciaCm = distanciaActualCm;

            double tolerancia = AppConfig.TOLERANCIA_DISTANCIA_CM;
            double diferencia = Math.abs(distanciaActualCm - distanciaObjetivoCm);
            boolean enRango = ultimoEstadoEnRangoDistancia ? diferencia <= tolerancia + HISTERESIS_CM : diferencia <= tolerancia;
            ultimoEstadoEnRangoDistancia = enRango;

            registrarMuestraFiabilidad(enRango && !ruidoVisual);

            if (!enRango) {
                ultimaCondicion = distanciaActualCm < distanciaObjetivoCm ? CondicionPrueba.DEMASIADO_CERCA : CondicionPrueba.DEMASIADO_LEJOS;
            } else {
                ultimaCondicion = ruidoVisual ? CondicionPrueba.RUIDO_VISUAL : CondicionPrueba.CORRECTA;
            }
            return ultimaCondicion;
        } finally {
            frame.release();
            gris.release();
            reducido.release();
        }
    }

    /**
     * YuNet devuelve por rostro: caja (x, y, ancho, alto), ojo derecho, ojo izquierdo,
     * nariz, comisuras de la boca y la confianza. Se procesa a ≤ 960 px de ancho: a 4 m
     * con una cámara 720p la cara mide ~40 px, dentro del rango del modelo (10-300 px).
     */
    private List<Rostro> detectarConYunet(Mat frame) {
        List<Rostro> lista = new ArrayList<>();
        Mat entrada = new Mat();
        Mat caras = new Mat();
        try {
            double escala = frame.cols() > ANCHO_YUNET ? ANCHO_YUNET / (double) frame.cols() : 1.0;
            if (escala < 1.0) Imgproc.resize(frame, entrada, new Size(), escala, escala, Imgproc.INTER_AREA);
            else frame.copyTo(entrada);
            detectorYunet.setInputSize(new Size(entrada.cols(), entrada.rows()));
            detectorYunet.detect(entrada, caras);
            for (int i = 0; i < caras.rows(); i++) {
                double[] v = new double[15];
                for (int j = 0; j < 15; j++) v[j] = caras.get(i, j)[0] / (j < 14 ? escala : 1.0);
                Rect caja = new Rect((int) Math.round(v[0]), (int) Math.round(v[1]), (int) Math.round(v[2]), (int) Math.round(v[3]));
                Point ojoA = new Point(v[4], v[5]);
                Point ojoB = new Point(v[6], v[7]);
                double separacion = CalibracionFocal.calcularMagnitudPupilar(ojoA.x, ojoA.y, ojoB.x, ojoB.y);
                boolean girado = CalibracionFocal.rostroGirado(ojoA.x, ojoB.x, v[8], separacion);
                lista.add(new Rostro(caja, separacion, girado, ojoA, ojoB));
            }
        } finally {
            entrada.release();
            caras.release();
        }
        return lista;
    }

    /** Respaldo sin YuNet: la separación de los ojos se aproxima con el 45 % del ancho del rostro. */
    private List<Rostro> detectarConHaar(Mat gris) {
        List<Rostro> lista = new ArrayList<>();
        Mat reducido = new Mat();
        MatOfRect rostros = new MatOfRect();
        try {
            double escala = gris.cols() > ANCHO_PROCESAMIENTO ? ANCHO_PROCESAMIENTO / (double) gris.cols() : 1.0;
            if (escala < 1.0) Imgproc.resize(gris, reducido, new Size(), escala, escala, Imgproc.INTER_AREA);
            else gris.copyTo(reducido);
            Imgproc.equalizeHist(reducido, reducido);
            int minimo = Math.max(30, (int) Math.round(60 * escala));
            detectorRostros.detectMultiScale(reducido, rostros, 1.1, 5, 0, new Size(minimo, minimo), new Size());
            for (Rect r : rostros.toArray()) {
                Rect caja = new Rect((int) Math.round(r.x / escala), (int) Math.round(r.y / escala),
                        (int) Math.round(r.width / escala), (int) Math.round(r.height / escala));
                lista.add(new Rostro(caja, caja.width * FRACCION_IPD_EN_ROSTRO, false, null, null));
            }
        } finally {
            reducido.release();
            rostros.release();
        }
        return lista;
    }

    /** Ancho de procesamiento Haar: suficiente para rostros a 4 m con cámaras 720p/1080p. */
    private static final double ANCHO_PROCESAMIENTO = 480.0;
    private static final double ANCHO_YUNET = 960.0;
    private static final double ANCHO_RUIDO = 480.0;
    private static final double HISTERESIS_CM = 3.0;
    private static final int VENTANA_MEDIANA = 5;
    private static final double ALFA_SUAVIZADO = 0.35;
    /** Fracción del ancho del rostro Haar que equivale a la distancia interpupilar (modelo original). */
    private static final double FRACCION_IPD_EN_ROSTRO = 0.45;

    private final java.util.ArrayDeque<Double> historialDistancias = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Double> separacionesRecientes = new java.util.ArrayDeque<>();
    private double distanciaSuavizada = -1;
    private int cuadrosSinRostro;
    private boolean ultimoEstadoEnRangoDistancia;
    private Rostro ultimoRostro;
    private int anchoFotogramaPx = 640;

    /**
     * Vista previa del último fotograma (JPEG, ~360 px de ancho, en espejo) con el
     * recuadro del rostro detectado: verde si está en rango, ámbar si no.
     */
    public synchronized byte[] vistaPreviaJpg() {
        if (ultimoFrame.empty()) return null;
        Mat copia = ultimoFrame.clone();
        Mat reducida = new Mat();
        org.opencv.core.MatOfByte salida = new org.opencv.core.MatOfByte();
        try {
            if (ultimoRostro != null) {
                org.opencv.core.Scalar color = ultimaCondicion == CondicionPrueba.CORRECTA
                        ? new org.opencv.core.Scalar(107, 174, 47) : new org.opencv.core.Scalar(60, 162, 230);
                int grosor = Math.max(2, copia.cols() / 220);
                Imgproc.rectangle(copia, ultimoRostro.caja().tl(), ultimoRostro.caja().br(), color, grosor);
                if (ultimoRostro.ojoA() != null) {
                    Imgproc.circle(copia, ultimoRostro.ojoA(), grosor * 2, color, -1);
                    Imgproc.circle(copia, ultimoRostro.ojoB(), grosor * 2, color, -1);
                }
            }
            double escala = 360.0 / copia.cols();
            Imgproc.resize(copia, reducida, new Size(), escala, escala, Imgproc.INTER_AREA);
            Core.flip(reducida, reducida, 1);
            org.opencv.imgcodecs.Imgcodecs.imencode(".jpg", reducida, salida);
            return salida.toArray();
        } finally {
            copia.release();
            reducida.release();
            salida.release();
        }
    }

    private double filtrarDistancia(double instantanea) {
        if (instantanea <= 0) return distanciaSuavizada;
        historialDistancias.addLast(instantanea);
        while (historialDistancias.size() > VENTANA_MEDIANA) historialDistancias.removeFirst();
        double[] valores = historialDistancias.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double mediana = valores[valores.length / 2];
        distanciaSuavizada = distanciaSuavizada < 0 ? mediana : distanciaSuavizada + ALFA_SUAVIZADO * (mediana - distanciaSuavizada);
        return distanciaSuavizada;
    }

    private boolean hayOtraPersona(List<Rostro> rostros, Rostro principal) {
        Rect p = principal.caja();
        double areaPrincipal = p.area();
        for (Rostro otro : rostros) {
            if (otro == principal) continue;
            Rect r = otro.caja();
            boolean solapa = (r.x < p.x + p.width && p.x < r.x + r.width
                    && r.y < p.y + p.height && p.y < r.y + r.height);
            if (!solapa && r.area() >= areaPrincipal * 0.55 * 0.55) return true;
        }
        return false;
    }

    private void agregarSeparacionParaCalibracion(double separacionPx) {
        separacionesRecientes.addLast(separacionPx);
        while (separacionesRecientes.size() > 15) separacionesRecientes.removeFirst();
    }

    private double medianaSeparacion() {
        double[] v = separacionesRecientes.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return v.length == 0 ? -1 : v[v.length / 2];
    }

    /**
     * Calibra la cámara con la persona colocada a una distancia medida con cinta.
     * Usa la DP de esa persona (medida o estimada por su edad), de modo que la
     * focal describa a la cámara y sirva después para pacientes de cualquier edad.
     *
     * @return la focal normalizada, o -1 si no hay suficientes lecturas.
     */
    public synchronized double calibrarConDistanciaReal(double distanciaRealCm, double dpPersonaMm) {
        if (separacionesRecientes.size() < 5 || distanciaRealCm <= 0) return -1;
        double focal = CalibracionFocal.focalNormalizada(medianaSeparacion(), distanciaRealCm * 10.0, dpPersonaMm, anchoFotogramaPx);
        if (focal <= 0) return -1;
        focalNormalizada = focal;
        focalCalibrada = true;
        historialDistancias.clear();
        distanciaSuavizada = -1;
        return focal;
    }

    /**
     * Con la cámara ya calibrada y la persona a una distancia medida con cinta, calcula
     * su DP real. A partir de ahí la distancia de esta sesión no depende de la tabla por edad.
     *
     * @return DP en mm, o -1 si no es posible.
     */
    public synchronized double medirDpConDistanciaReal(double distanciaRealCm) {
        if (!focalCalibrada || separacionesRecientes.size() < 5 || distanciaRealCm <= 0) return -1;
        return CalibracionFocal.dpMedidaMm(medianaSeparacion(), distanciaRealCm * 10.0, focalNormalizada, anchoFotogramaPx);
    }

    /** Lecturas disponibles para calibrar (se necesitan al menos 5). */
    public synchronized int getLecturasParaCalibrar() { return separacionesRecientes.size(); }

    public boolean isFocalCalibrada() { return focalCalibrada; }

    public double getFocalNormalizada() { return focalNormalizada; }

    // ------------------------------------------------------------------
    // Evaluación en segundo plano: la detección (30-80 ms) nunca bloquea la interfaz.
    // ------------------------------------------------------------------
    private final java.util.concurrent.ExecutorService ejecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "vision-distancia");
        t.setDaemon(true);
        return t;
    });
    private final java.util.concurrent.atomic.AtomicBoolean evaluando = new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Evalúa la condición en un hilo de visión y entrega el resultado en el hilo de JavaFX.
     * Si ya hay una evaluación en curso, la solicitud se descarta (no se acumulan).
     */
    public void evaluarEnSegundoPlano(java.util.function.Consumer<CondicionPrueba> alTerminar) {
        if (!evaluando.compareAndSet(false, true)) return;
        try {
            ejecutor.execute(() -> {
                CondicionPrueba condicion;
                try { condicion = evaluarCondicionPrueba(); }
                catch (Throwable t) { condicion = CondicionPrueba.CAMARA_SIN_IMAGEN; }
                finally { evaluando.set(false); }
                CondicionPrueba resultado = condicion;
                if (alTerminar != null) javafx.application.Platform.runLater(() -> alTerminar.accept(resultado));
            });
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            evaluando.set(false);
        }
    }

    private boolean hayRuidoVisual(Mat gris) {
        boolean ruido = false;
        if (grisAnterior != null && !grisAnterior.empty()
                && grisAnterior.rows() == gris.rows() && grisAnterior.cols() == gris.cols()) {
            Mat diferencia = new Mat();
            try {
                Core.absdiff(gris, grisAnterior, diferencia);
                ruido = Core.mean(diferencia).val[0] > UMBRAL_RUIDO_VISUAL;
            } finally { diferencia.release(); }
        }
        if (grisAnterior != null) grisAnterior.release();
        grisAnterior = gris.clone();
        return ruido;
    }

    private Rostro seleccionarRostroPrincipal(List<Rostro> rostros) {
        Rostro principal = rostros.get(0);
        for (Rostro r : rostros) if (r.caja().area() > principal.caja().area()) principal = r;
        return principal;
    }

    private void registrarDistancia(double cm) {
        sumaDistanciaCm += cm;
        sumaCuadradosDistanciaCm += cm * cm;
        muestrasDistancia++;
    }

    private synchronized void registrarMuestraFiabilidad(boolean enRango) {
        long ahora = System.currentTimeMillis();
        if (ultimoInstanteMuestraMs > 0) {
            long intervalo = Math.min(2_000, Math.max(0, ahora - ultimoInstanteMuestraMs));
            duracionMuestreadaMs += intervalo;
            if (ultimoEstadoEnRango) duracionEnRangoMs += intervalo;
        }
        ultimoInstanteMuestraMs = ahora;
        ultimoEstadoEnRango = enRango;
        muestrasFiabilidad++;
    }

    public synchronized int getMuestrasFiabilidad() { return muestrasFiabilidad; }

    public synchronized double getCumplimientoDistanciaPorcentaje() {
        if (muestrasFiabilidad == 0) return Double.NaN;
        if (duracionMuestreadaMs == 0) return ultimoEstadoEnRango ? 100.0 : 0.0;
        return 100.0 * duracionEnRangoMs / duracionMuestreadaMs;
    }

    /** Distancia real promedio durante la prueba (cm), NaN sin muestras. */
    public synchronized double getDistanciaPromedioCm() {
        return muestrasDistancia == 0 ? Double.NaN : sumaDistanciaCm / muestrasDistancia;
    }

    /** Desviación estándar de la distancia durante la prueba (cm). */
    public synchronized double getDesviacionDistanciaCm() {
        if (muestrasDistancia < 2) return Double.NaN;
        double media = sumaDistanciaCm / muestrasDistancia;
        return Math.sqrt(Math.max(0, sumaCuadradosDistanciaCm / muestrasDistancia - media * media));
    }

    public Mat getUltimoFrame() {
        return ultimoFrame;
    }

    public double getUltimaDistanciaCm() {
        return ultimaDistanciaCm;
    }

    public boolean isUltimoRostroDetectado() {
        return ultimoRostroDetectado;
    }

    public int getUltimoNumeroRostros() { return ultimoNumeroRostros; }

    public CondicionPrueba getUltimaCondicion() { return ultimaCondicion; }

    public synchronized boolean puedeCapturarFotograma() {
        if (camara == null || !camara.isOpened()) {
            return false;
        }

        Mat frame = new Mat();
        try {
            return camara.read(frame) && !frame.empty();
        } finally {
            frame.release();
        }
    }

    public void apagarCamara() {
        ejecutor.shutdownNow();
        synchronized (this) { apagarCamaraInterno(); }
    }

    private void apagarCamaraInterno() {
        if (camara != null && camara.isOpened()) {
            camara.release();
        }
        if (grisAnterior != null) {
            grisAnterior.release();
            grisAnterior = null;
        }
    }
}
