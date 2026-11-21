package com.eyesofter.camara;

import com.eyesofter.formulas.CalibracionFocal;
import com.eyesofter.config.AppConfig;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Core;
import org.opencv.core.Point;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Módulo de Visión Artificial.
 * Mide la distancia en tiempo real entre el paciente y el monitor
 * aplicando el modelo matemático Pinhole Camera y Semejanza de Triángulos.
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
    private CascadeClassifier detectorOjos;

    private double distanciaFocalCalibrada = 600.0;
    private boolean focalCalibrada;
    private double distanciaObjetivoCm = 100.0;

    private final Mat ultimoFrame = new Mat();
    private double ultimaDistanciaCm = -1;
    private boolean ultimoRostroDetectado = false;
    private int ultimoNumeroRostros;
    private CondicionPrueba ultimaCondicion = CondicionPrueba.CAMARA_SIN_IMAGEN;
    private Mat grisAnterior;
    private static final double UMBRAL_RUIDO_VISUAL = 42.0;
    private double sumaDistanciaPupilarMm;
    private int muestrasDistanciaPupilar;

    private long ultimoInstanteMuestraMs = -1;
    private long duracionMuestreadaMs;
    private long duracionEnRangoMs;
    private boolean ultimoEstadoEnRango;
    private int muestrasFiabilidad;

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
        this(rutaModeloHaar, AppConfig.RUTA_MODELO_OJOS_OPENCV, AppConfig.INDICE_CAMARA_PREFERIDA);
    }

    public DetectorDistancia(String rutaModeloHaar, String rutaModeloOjos) {
        this(rutaModeloHaar, rutaModeloOjos, AppConfig.INDICE_CAMARA_PREFERIDA);
    }

    public DetectorDistancia(String rutaModeloHaar, String rutaModeloOjos, int indiceCamara) {
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

            File archivoOjos = new File(rutaModeloOjos == null ? "" : rutaModeloOjos);
            if (archivoOjos.exists()) {
                CascadeClassifier candidato = new CascadeClassifier(archivoOjos.getAbsolutePath());
                if (!candidato.empty()) detectorOjos = candidato;
            }

            if (AppConfig.DISTANCIA_FOCAL_CAMARA_PX > 0) {
                setDistanciaFocalCalibrada(AppConfig.DISTANCIA_FOCAL_CAMARA_PX);
            }

            this.camara = new VideoCapture(Math.max(0, indiceCamara));
            if (!camara.isOpened()) {
                throw new RuntimeException("CRÍTICO: No se pudo encender la cámara web índice " + indiceCamara);
            }
        } catch (Throwable t) {
            mostrarErrorProfundo(t, "Error al inicializar el constructor de DetectorDistancia");
            throw t;
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

    public void setDistanciaFocalCalibrada(double focal) {
        if (focal > 0) {
            this.distanciaFocalCalibrada = focal;
            this.focalCalibrada = true;
        }
    }

    public synchronized void iniciarRegistroFiabilidad() {
        ultimoInstanteMuestraMs = -1;
        duracionMuestreadaMs = 0;
        duracionEnRangoMs = 0;
        ultimoEstadoEnRango = false;
        muestrasFiabilidad = 0;
        sumaDistanciaPupilarMm = 0;
        muestrasDistanciaPupilar = 0;
    }

    public boolean validarPosicionPaciente() {
        return evaluarCondicionPrueba() == CondicionPrueba.CORRECTA;
    }

    /**
     * Evalúa la posición del paciente con el rostro principal.
     *
     * Mejoras de exactitud:
     *  - El fotograma se reduce a ~480 px de ancho y se ecualiza el histograma:
     *    la detección es 3-4 veces más rápida y tolera mejor la iluminación.
     *  - La distancia se filtra con la mediana de las últimas lecturas y luego
     *    un suavizado exponencial: desaparecen los saltos de ±10 cm entre cuadros.
     *  - Histéresis: para salir de rango hay que superar la tolerancia + 3 cm,
     *    así la prueba no se pausa por oscilaciones en el borde.
     *  - Se detecta una segunda persona si su rostro mide al menos el 55 % del principal.
     */
    public synchronized CondicionPrueba evaluarCondicionPrueba() {
        Mat frame = new Mat();
        Mat gris = new Mat();
        Mat reducido = new Mat();
        MatOfRect rostros = new MatOfRect();
        try {
            if (camara == null || !camara.read(frame) || frame.empty()) {
                registrarMuestraFiabilidad(false);
                ultimaCondicion = CondicionPrueba.CAMARA_SIN_IMAGEN;
                return ultimaCondicion;
            }
            frame.copyTo(ultimoFrame);
            Imgproc.cvtColor(frame, gris, Imgproc.COLOR_BGR2GRAY);

            double escala = gris.cols() > ANCHO_PROCESAMIENTO ? ANCHO_PROCESAMIENTO / (double) gris.cols() : 1.0;
            if (escala < 1.0) Imgproc.resize(gris, reducido, new org.opencv.core.Size(), escala, escala, Imgproc.INTER_AREA);
            else gris.copyTo(reducido);
            Imgproc.equalizeHist(reducido, reducido);

            int minimo = Math.max(30, (int) Math.round(60 * escala));
            detectorRostros.detectMultiScale(reducido, rostros, 1.1, 5, 0,
                    new org.opencv.core.Size(minimo, minimo), new org.opencv.core.Size());

            Rect[] arrayRostros = rostros.toArray();
            ultimoNumeroRostros = arrayRostros.length;
            boolean ruidoVisual = hayRuidoVisual(reducido);

            if (arrayRostros.length == 0) {
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

            Rect rostroReducido = seleccionarRostroPrincipal(arrayRostros);
            if (hayOtraPersona(arrayRostros, rostroReducido)) {
                registrarMuestraFiabilidad(false);
                ultimaCondicion = CondicionPrueba.MULTIPLES_PERSONAS;
                return ultimaCondicion;
            }

            // Coordenadas de vuelta a la resolución original.
            ultimoRostro = new Rect((int) Math.round(rostroReducido.x / escala), (int) Math.round(rostroReducido.y / escala),
                    (int) Math.round(rostroReducido.width / escala), (int) Math.round(rostroReducido.height / escala));
            Rect rostro = new Rect((int) Math.round(rostroReducido.x / escala), (int) Math.round(rostroReducido.y / escala),
                    (int) Math.round(rostroReducido.width / escala), (int) Math.round(rostroReducido.height / escala));
            ultimoAnchoRostroPx = rostro.width;
            agregarAnchoParaCalibracion(rostro.width);

            double distanciaInstantanea = distanciaDesdeAnchoRostro(rostro.width);
            double distanciaActualCm = filtrarDistancia(distanciaInstantanea);
            this.ultimaDistanciaCm = distanciaActualCm;

            double tolerancia = AppConfig.TOLERANCIA_DISTANCIA_CM;
            double diferencia = Math.abs(distanciaActualCm - distanciaObjetivoCm);
            boolean enRango = ultimoEstadoEnRangoDistancia ? diferencia <= tolerancia + HISTERESIS_CM : diferencia <= tolerancia;
            ultimoEstadoEnRangoDistancia = enRango;
            if (enRango) registrarMuestraPupilar(gris, rostro, distanciaActualCm);

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
            rostros.release();
        }
    }

    /** Ancho de procesamiento: suficiente para rostros a 4 m con cámaras 720p/1080p. */
    private static final double ANCHO_PROCESAMIENTO = 480.0;
    private static final double HISTERESIS_CM = 3.0;
    private static final int VENTANA_MEDIANA = 5;
    private static final double ALFA_SUAVIZADO = 0.35;
    /** Fracción del ancho del rostro Haar que equivale a la distancia interpupilar (modelo original). */
    private static final double FRACCION_IPD_EN_ROSTRO = 0.45;

    private final java.util.ArrayDeque<Double> historialDistancias = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Integer> anchosRecientes = new java.util.ArrayDeque<>();
    private double distanciaSuavizada = -1;
    private int cuadrosSinRostro;
    private boolean ultimoEstadoEnRangoDistancia;
    private int ultimoAnchoRostroPx;
    private Rect ultimoRostro;

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
                Imgproc.rectangle(copia, ultimoRostro.tl(), ultimoRostro.br(), color, Math.max(2, copia.cols() / 220));
            }
            double escala = 360.0 / copia.cols();
            Imgproc.resize(copia, reducida, new org.opencv.core.Size(), escala, escala, Imgproc.INTER_AREA);
            Core.flip(reducida, reducida, 1);
            org.opencv.imgcodecs.Imgcodecs.imencode(".jpg", reducida, salida);
            return salida.toArray();
        } finally {
            copia.release();
            reducida.release();
            salida.release();
        }
    }

    /** Modelo pinhole: d = IPD · f / (0.45 · ancho_rostro). */
    private double distanciaDesdeAnchoRostro(double anchoPx) {
        return CalibracionFocal.calcularDistanciaZ(CalibracionFocal.IPD_PROMEDIO_INFANTIL_MM, distanciaFocalCalibrada,
                anchoPx * FRACCION_IPD_EN_ROSTRO);
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

    private boolean hayOtraPersona(Rect[] rostros, Rect principal) {
        double areaPrincipal = principal.area();
        for (Rect r : rostros) {
            if (r == principal) continue;
            boolean solapa = (r.x < principal.x + principal.width && principal.x < r.x + r.width
                    && r.y < principal.y + principal.height && principal.y < r.y + r.height);
            if (!solapa && r.area() >= areaPrincipal * 0.55 * 0.55) return true;
        }
        return false;
    }

    private void agregarAnchoParaCalibracion(int ancho) {
        anchosRecientes.addLast(ancho);
        while (anchosRecientes.size() > 15) anchosRecientes.removeFirst();
    }

    /**
     * Calibra la cámara con la persona colocada a una distancia medida con cinta.
     * Usa la mediana de los últimos anchos de rostro para obtener la focal, de modo
     * que la distancia estimada coincida con la real para esta cámara.
     *
     * @return la focal calculada en píxeles, o -1 si no hay suficientes lecturas.
     */
    public synchronized double calibrarConDistanciaReal(double distanciaRealCm) {
        if (anchosRecientes.size() < 5 || distanciaRealCm <= 0) return -1;
        int[] anchos = anchosRecientes.stream().mapToInt(Integer::intValue).sorted().toArray();
        double anchoMediana = anchos[anchos.length / 2];
        double focal = CalibracionFocal.calcularDistanciaFocal(anchoMediana * FRACCION_IPD_EN_ROSTRO, distanciaRealCm,
                CalibracionFocal.IPD_PROMEDIO_INFANTIL_MM);
        setDistanciaFocalCalibrada(focal);
        historialDistancias.clear();
        distanciaSuavizada = -1;
        return focal;
    }

    /** Lecturas disponibles para calibrar (se necesitan al menos 5). */
    public synchronized int getLecturasParaCalibrar() { return anchosRecientes.size(); }

    public boolean isFocalCalibrada() { return focalCalibrada; }

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

    private Rect seleccionarRostroPrincipal(Rect[] rostros) {
        Rect principal = rostros[0];
        for (Rect rostro : rostros) {
            if (rostro.width * rostro.height > principal.width * principal.height) {
                principal = rostro;
            }
        }
        return principal;
    }

    private void registrarMuestraPupilar(Mat gris, Rect rostro, double distanciaCm) {
        if (detectorOjos == null || !focalCalibrada) return;
        int x = Math.max(0, rostro.x);
        int y = Math.max(0, rostro.y + (int) (rostro.height * 0.10));
        int ancho = Math.min(gris.cols() - x, rostro.width);
        int alto = Math.min(gris.rows() - y, (int) (rostro.height * 0.52));
        if (ancho <= 0 || alto <= 0) return;

        Mat regionOjos = new Mat(gris, new Rect(x, y, ancho, alto));
        MatOfRect ojosDetectados = new MatOfRect();
        try {
            detectorOjos.detectMultiScale(regionOjos, ojosDetectados, 1.1, 3, 0,
                    new org.opencv.core.Size(20, 20), new org.opencv.core.Size());
            Rect[] ojos = ojosDetectados.toArray();
            if (ojos.length < 2) return;
            Rect[] par = seleccionarParOjos(ojos);
            if (par == null) return;

            Point izquierdo = new Point(x + par[0].x + par[0].width / 2.0, y + par[0].y + par[0].height / 2.0);
            Point derecho = new Point(x + par[1].x + par[1].width / 2.0, y + par[1].y + par[1].height / 2.0);
            double separacionPx = CalibracionFocal.calcularMagnitudPupilar(izquierdo.x, izquierdo.y, derecho.x, derecho.y);
            double dpMm = separacionPx * (distanciaCm * 10.0) / distanciaFocalCalibrada;

            if (dpMm >= 45.0 && dpMm <= 80.0) {
                sumaDistanciaPupilarMm += dpMm;
                muestrasDistanciaPupilar++;
            }
        } finally {
            regionOjos.release();
            ojosDetectados.release();
        }
    }

    private Rect[] seleccionarParOjos(Rect[] ojos) {
        Rect izquierdo = null;
        Rect derecho = null;
        double mayorSeparacion = -1;
        for (int i = 0; i < ojos.length; i++) {
            for (int j = i + 1; j < ojos.length; j++) {
                double c1 = ojos[i].x + ojos[i].width / 2.0;
                double c2 = ojos[j].x + ojos[j].width / 2.0;
                double separacion = Math.abs(c2 - c1);
                if (separacion > mayorSeparacion) {
                    mayorSeparacion = separacion;
                    if (c1 < c2) { izquierdo = ojos[i]; derecho = ojos[j]; }
                    else { izquierdo = ojos[j]; derecho = ojos[i]; }
                }
            }
        }
        return izquierdo == null ? null : new Rect[]{izquierdo, derecho};
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

    public synchronized double getDistanciaPupilarPromedioMm() {
        return muestrasDistanciaPupilar == 0 ? Double.NaN : sumaDistanciaPupilarMm / muestrasDistanciaPupilar;
    }

    public synchronized boolean isDistanciaPupilarCalibrada() {
        return focalCalibrada && muestrasDistanciaPupilar >= 3;
    }

    public Mat getUltimoFrame() {
        return ultimoFrame;
    }

    public int getUltimoAnchoRostroPx() { return ultimoAnchoRostroPx; }

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