package com.eyesofter.camara;

/**
 * Controlador que gestiona el hardware de video y la lógica de validación de postura.
 */
public class ControladorCamara {

    public static void main(String[] args) {
        // Apuntamos al archivo XML que acabamos de descargar
        String rutaHaar = "modelo_opencv/haarcascade_frontalface_default.xml";

        System.out.println("==================================================");
        System.out.println("   INICIANDO CALIBRACIÓN DE CÁMARA (EYESOFTER)    ");
        System.out.println("==================================================");

        try {
            DetectorDistancia detector = new DetectorDistancia(rutaHaar);

            // Establecemos la distancia esperada para la prueba clínica (100 cm = 1 metro)
            detector.setDistanciaObjetivo(100.0);

            System.out.println("✅ Cámara encendida correctamente.");
            System.out.println("👉 Por favor, colócate frente al monitor...");
            System.out.println("Calculando distancia en tiempo real...\n");

            // Bucle de prueba de 15 segundos para que te muevas y veas cómo reacciona
            long tiempoInicio = System.currentTimeMillis();
            while ((System.currentTimeMillis() - tiempoInicio) < 15000) {

                boolean posicionCorrecta = detector.validarPosicionPaciente();

                if (posicionCorrecta) {
                    System.out.println("🟢 POSICIÓN CORRECTA. Distancia focal óptima. (El examen avanzaría)");
                } else {
                    System.out.println("🔴 FUERA DE RANGO. (El examen se pausaría para reacomodar al alumno)");
                }

                // Pausamos medio segundo entre cada foto para no saturar el procesador
                try { Thread.sleep(500); } catch (InterruptedException e) {}
            }

            // Es crítico apagar la cámara al terminar para liberar la memoria
            detector.apagarCamara();
            System.out.println("\n==================================================");
            System.out.println("          PRUEBA DE VISIÓN FINALIZADA             ");
            System.out.println("==================================================");

        } catch (Exception e) {
            System.err.println("❌ ERROR FATAL: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
