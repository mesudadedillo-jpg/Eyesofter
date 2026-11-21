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
     * Evalúa la posición del paciente ignorando rostros falsos secundarios
     * y enfocándose siempre en el rostro principal.
     */
    public synchronized CondicionPrueba evaluarCondicionPrueba() {
        Mat frame = new Mat();
        try {
            if (camara.read(frame)) {
                frame.copyTo(ultimoFrame);

                MatOfRect rostros = new MatOfRect();
                Mat gris = new Mat();
                Imgproc.cvtColor(frame, gris, Imgproc.COLOR_BGR2GRAY);

                // Detección filtrada para mayor estabilidad
                detectorRostros.detectMultiScale(
                        gris,
                        rostros,
                        1.15,
                        5,
                        0,
                        new org.opencv.core.Size(60, 60),
                        new org.opencv.core.Size()
                );

                Rect[] arrayRostros = rostros.toArray();
                ultimoNumeroRostros = arrayRostros.length;
                boolean ruidoVisual = hayRuidoVisual(gris);

                if (arrayRostros.length > 0) {
                    ultimoRostroDetectado = true;

                    // Seleccionamos siempre el rostro más grande (el paciente), sin bloquear por falsos positivos
                    Rect rostro = seleccionarRostroPrincipal(arrayRostros);
                    double anchoPixeles = rostro.width;

                    double pActualEstimado = anchoPixeles * 0.45;

                    double distanciaActualCm = CalibracionFocal.calcularDistanciaZ(
                            CalibracionFocal.IPD_PROMEDIO_INFANTIL_MM,
                            distanciaFocalCalibrada,
                            pActualEstimado
                    );
                    this.ultimaDistanciaCm = distanciaActualCm;

                    System.out.printf("📷 [VISIÓN]: Distancia detectada: %.2f cm (Objetivo: %.0f cm)\n", distanciaActualCm, distanciaObjetivoCm);

                    boolean enRango = Math.abs(distanciaActualCm - distanciaObjetivoCm) <= AppConfig.TOLERANCIA_DISTANCIA_CM;
                    if (enRango) registrarMuestraPupilar(gris, rostro, distanciaActualCm);

                    boolean correcta = enRango && !ruidoVisual;
                    registrarMuestraFiabilidad(correcta);

                    gris.release();
                    rostros.release();

                    if (!enRango) {
                        ultimaCondicion = distanciaActualCm < distanciaObjetivoCm
                                ? CondicionPrueba.DEMASIADO_CERCA
                                : CondicionPrueba.DEMASIADO_LEJOS;
                    } else {
                        ultimaCondicion = ruidoVisual ? CondicionPrueba.RUIDO_VISUAL : CondicionPrueba.CORRECTA;
                    }
                    return ultimaCondicion;
                } else {
                    ultimoRostroDetectado = false;
                    this.ultimaDistanciaCm = -1;
                    System.out.println("⚠️ [VISIÓN]: No se detecta ningún rostro frente a la pantalla.");
                    registrarMuestraFiabilidad(false);
                    gris.release();
                    rostros.release();
                    ultimaCondicion = CondicionPrueba.SIN_ROSTRO;
                    return ultimaCondicion;
                }
            }
            registrarMuestraFiabilidad(false);
            ultimaCondicion = CondicionPrueba.CAMARA_SIN_IMAGEN;
            return ultimaCondicion;
        } finally {
            frame.release();
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

    public double getUltimaDistanciaCm() {
        return ultimaDistanciaCm;
    }

    public boolean isUltimoRostroDetectado() {
        return ultimoRostroDetectado;
    }

    public int getUltimoNumeroRostros() { return ultimoNumeroRostros; }

    public CondicionPrueba getUltimaCondicion() { return ultimaCondicion; }

    public boolean puedeCapturarFotograma() {
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
        if (camara != null && camara.isOpened()) {
            camara.release();
        }
        if (grisAnterior != null) {
            grisAnterior.release();
            grisAnterior = null;
        }
    }
}