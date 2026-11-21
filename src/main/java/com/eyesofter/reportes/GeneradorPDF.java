package com.eyesofter.reportes;

import com.eyesofter.db.GestorBaseDatos.EvaluacionInfo;
import com.eyesofter.db.GestorBaseDatos.PacienteInfo;
import com.eyesofter.formulas.TrigonometriaVisual;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Genera un informe estructurado para revisión optométrica del tamizaje. */
public final class GeneradorPDF {
    private static final Color TEAL = new Color(0x16, 0x4E, 0x59);
    private static final Color TEAL_SUAVE = new Color(0xE7, 0xF2, 0xF3);
    private static final Color GRIS = new Color(0x5C, 0x7A, 0x82);
    private static final Color ROJO = new Color(0xA6, 0x2C, 0x2B);
    private static final Color VERDE = new Color(0x16, 0x7A, 0x45);
    private static final Color AMARILLO = new Color(0xA6, 0x6D, 0x00);
    private static final String CARPETA_SALIDA = "expedientes_pdf";
    private static final float MARGEN = 46;

    private GeneradorPDF() { }

    public static File generarReporte(PacienteInfo paciente, EvaluacionInfo evaluacion) throws IOException {
        File carpeta = new File(CARPETA_SALIDA);
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            throw new IOException("No se pudo crear la carpeta de expedientes.");
        }

        String marcaTiempo = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        File salida = new File(carpeta, "expediente_" + safe(paciente == null ? null : paciente.curp) + "_" + marcaTiempo + ".pdf");
        return generarReporteEn(paciente, evaluacion, salida);
    }

    /** Genera el expediente en cualquier destino, incluyendo archivos temporales para vista previa. */
    public static File generarReporteEn(PacienteInfo paciente, EvaluacionInfo evaluacion, File salida) throws IOException {

        try (PDDocument documento = new PDDocument()) {
            Pagina pagina1 = nuevaPagina(documento, "PARTE 1 · Resumen claro para paciente o tutor", 1, 3);
            escribirBloqueDatosPaciente(pagina1, paciente, evaluacion);
            escribirResumenPaciente(pagina1, evaluacion);
            escribirPie(pagina1, 1);
            pagina1.cerrar();

            Pagina pagina2 = nuevaPagina(documento, "PARTE 2 · Reporte técnico para el especialista", 2, 3);
            escribirFiabilidad(pagina2, evaluacion);
            escribirResultados(pagina2, evaluacion);
            escribirInterpretacionOrientativa(pagina2, evaluacion);
            escribirAlertas(pagina2, evaluacion);
            escribirPie(pagina2, 2);
            pagina2.cerrar();

            Pagina pagina3 = nuevaPagina(documento, "PARTE 2 · Método, límites y referencias", 3, 3);
            escribirNotasClinicas(pagina3);
            escribirPie(pagina3, 3);
            pagina3.cerrar();
            documento.save(salida);
        }
        return salida;
    }

    private static Pagina nuevaPagina(PDDocument documento, String subtitulo, int numero, int total) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        documento.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(documento, page);
        Pagina p = new Pagina(page, cs);
        p.color(TEAL);
        p.texto("EYESOFTER", MARGEN, p.y, PDType1Font.HELVETICA_BOLD, 22);
        p.y -= 17;
        p.color(GRIS);
        p.texto(subtitulo, MARGEN, p.y, PDType1Font.HELVETICA, 10.5f);
        p.texto("Página " + numero + " de " + total, p.ancho - MARGEN - 52, p.y, PDType1Font.HELVETICA, 9);
        p.y -= 13;
        p.linea(MARGEN, p.y, p.ancho - MARGEN, p.y, TEAL, 1.1f);
        p.y -= 22;
        return p;
    }

    private static void escribirBloqueDatosPaciente(Pagina p, PacienteInfo paciente, EvaluacionInfo e) throws IOException {
        seccion(p, "Identificación de la persona paciente");
        filas(p, new String[][]{
                {"Nombre", valor(paciente == null ? null : paciente.nombre)},
                {"CURP", valor(paciente == null ? null : paciente.curp)},
                {"Fecha de nacimiento", valor(paciente == null ? null : paciente.fechaNacimiento)},
                {"Grado / grupo", valor(paciente == null ? null : paciente.gradoGrupo)},
                {"Fecha y hora de prueba", valor(e == null ? null : e.fechaHora)}
        });
        p.y -= 10;
    }

    /** Primera página sin jerga: comunica prioridad, motivo y siguiente paso. */
    private static void escribirResumenPaciente(Pagina p, EvaluacionInfo e) throws IOException {
        Nivel nivel = nivelPaciente(e);
        seccion(p, "Estado general de la visión");
        tarjetaEstado(p, nivel, mensajeGeneral(e, nivel));
        p.y -= 12;

        seccion(p, "Lo que observamos en esta revisión");
        textoEnvuelto(p, "Visión de lejos", mensajeAgudezaPaciente(e), 11, Color.BLACK);
        textoEnvuelto(p, "Visión de colores", mensajeColorPaciente(e), 11, Color.BLACK);
        p.y -= 5;

        seccion(p, "Siguiente paso recomendado");
        tarjetaEstado(p, nivel == Nivel.VERDE ? Nivel.VERDE : Nivel.AMARILLO, recomendacionPaciente(e, nivel));
        p.y -= 10;
        textoEnvuelto(p, "Importante", "Este reporte es un tamizaje: detecta señales que conviene revisar, pero no entrega una receta ni confirma una enfermedad. Llévelo a la consulta si se recomienda valoración.", 9.8f, GRIS);
    }

    private static void tarjetaEstado(Pagina p, Nivel nivel, String texto) throws IOException {
        Color color = switch (nivel) { case VERDE -> VERDE; case AMARILLO -> AMARILLO; case ROJO -> ROJO; };
        // La tarjeta no puede tener una altura fija: los textos de orientación
        // varían según el resultado y antes podían invadir el siguiente bloque.
        final float interlineado = 12;
        List<String> lineas = envolverPorAncho(texto, p.ancho - MARGEN * 2 - 24,
                PDType1Font.HELVETICA_BOLD, 9.2f);
        float alto = 38 + lineas.size() * interlineado;
        float superior = p.y + 10;

        p.rectangulo(MARGEN, superior - alto, p.ancho - MARGEN * 2, alto, color);
        p.color(Color.WHITE);
        p.texto(nivel.etiqueta, MARGEN + 12, superior - 17, PDType1Font.HELVETICA_BOLD, 12);
        float yTexto = superior - 34;
        for (String linea : lineas) {
            p.texto(linea, MARGEN + 12, yTexto, PDType1Font.HELVETICA_BOLD, 9.2f);
            yTexto -= interlineado;
        }
        p.y = superior - alto - 10;
    }

    private static void escribirFiabilidad(Pagina p, EvaluacionInfo e) throws IOException {
        seccion(p, "A. Métricas de fiabilidad");
        filas(p, new String[][]{
                {"Distancia objetivo y tolerancia", distanciaValidada(e)},
                {"Cumplimiento de distancia", porcentaje(e == null ? null : e.cumplimientoDistanciaPct)
                        + "  (" + entero(e == null ? 0 : e.muestrasDistancia) + " muestras)"},
                {"Tiempo de respuesta promedio", segundos(e == null ? null : e.tiempoRespuestaMs)},
                {"Método de entrada", valor(e == null ? null : e.metodoEntrada)},
                {"Edad al evaluar", e == null || e.edadAnios == null ? "N/D" : String.format(Locale.US, "%.1f años", e.edadAnios)},
                {"Optotipo", valor(e == null ? null : e.tipoOptotipo)},
                {"Límite medible de la pantalla", e == null || e.limitePantallaLogmar == null ? "N/D"
                        : String.format(Locale.US, "LogMAR %.1f (%s)", e.limitePantallaLogmar, TrigonometriaVisual.snellenDesdeLogMAR(e.limitePantallaLogmar))},
                {"Control acústico histórico (WER)", e == null ? "N/D" : String.format(Locale.US, "%.2f", e.tasaRuidoWer)}
        });
        if (e != null && e.condicionesPrueba != null && !e.condicionesPrueba.isBlank()) {
            textoEnvuelto(p, "Condiciones del equipo", e.condicionesPrueba, 9.4f, GRIS);
        }
        p.y -= 10;
    }

    private static void escribirResultados(Pagina p, EvaluacionInfo e) throws IOException {
        seccion(p, "B. Resultados clínicos estandarizados");
        cabeceraTabla(p, new String[]{"Métrica", "OD", "OI", "AO"}, new float[]{145, 120, 120, 95});
        filaTabla(p, new String[]{"Agudeza Snellen", snellenOD(e), snellenOI(e), snellenAO(e)}, new float[]{145, 120, 120, 95});
        filaTabla(p, new String[]{"LogMAR equivalente", decimal(e == null ? null : e.logmarDer), decimal(e == null ? null : e.logmarIzq), decimal(e == null ? null : e.logmarAo)}, new float[]{145, 120, 120, 95});
        filaTabla(p, new String[]{"Ishihara binocular", ishihara(e) + " concord.", "Patrón RG: " + entero(e == null ? 0 : e.ishiharaRespuestasDeficiencia), "Clasificación: " + tipoColor(e)}, new float[]{145, 120, 120, 95});
        filaTabla(p, new String[]{"Distancia pupilar (DP)", dp(e), e != null && e.dpCalibrada ? "Cámara calibrada" : "No reportable"}, new float[]{190, 145, 145});
        p.y -= 6;
    }

    private static void escribirInterpretacionOrientativa(Pagina p, EvaluacionInfo e) throws IOException {
        seccion(p, "C. Interpretación orientativa de tamizaje");
        textoEnvuelto(p, "Síntesis para revisión", sintesisProfesional(e), 10.2f, Color.BLACK);
        textoEnvuelto(p, "Orientación comunicada a la persona usuaria", valor(e == null ? null : e.recomendacion), 9.8f, TEAL);
        textoEnvuelto(p, "Alcance clínico", "La agudeza y LogMAR describen el desempeño en esta cartilla digital; no calculan graduación, dioptrías, cilindro, eje, acomodación ni salud ocular. Una refracción y exploración profesional son necesarias para esos fines.", 9.8f, GRIS);
        p.y -= 12;
    }

    private static void escribirAlertas(Pagina p, EvaluacionInfo e) throws IOException {
        seccion(p, "Alertas clínicas y calidad del resultado");
        String alertas = valor(e == null ? null : e.alertasClinicas);
        if ("N/D".equals(alertas)) alertas = "No hay datos estructurados de alertas para este expediente.";
        List<String> entradas = separarAlertas(alertas);
        for (String alerta : entradas) {
            p.color(ROJO);
            p.rectangulo(MARGEN, p.y - 3, 5, 27, ROJO);
            p.color(ROJO);
            p.texto("ALERTA / NOTA", MARGEN + 13, p.y + 12, PDType1Font.HELVETICA_BOLD, 9);
            p.color(Color.BLACK);
            p.y -= 2;
            List<String> lineas = envolver(alerta, 88);
            for (String linea : lineas) {
                p.texto(linea, MARGEN + 13, p.y, PDType1Font.HELVETICA, 9.6f);
                p.y -= 12;
            }
            p.y -= 10;
        }
    }

    private static void escribirNotasClinicas(Pagina p) throws IOException {
        seccion(p, "Método, calidad y límites del registro");
        String[] notas = {
                "- La conversión a LogMAR se calcula como log10(denominador Snellen / 20).",
                "- El banco digital actual contiene 5 láminas Ishihara de apoyo. Puede señalar respuestas compatibles con una alteración rojo-verde, pero no permite determinar con validez clínica Protan/Deutan ni severidad; para ello se requiere una edición estandarizada y pruebas confirmatorias.",
                "- La condición de corrección óptica habitual no se registra en este formato; las cifras no deben interpretarse como agudeza visual con mejor corrección.",
                "- La distancia se reporta como objetivo configurado, tolerancia, porcentaje de muestras en rango y, con cámara, la distancia real promedio estimada; no es una certificación metrológica.",
                "- Con cámara, la distancia se estima con la separación de los ojos y la distancia interpupilar (DP) del paciente: medida con cinta, capturada o estimada por su edad (variación natural +/- 6 %).",
                "- La DP solo se informa cuando se midió en la sesión con cinta y la cámara calibrada; la estimada por edad no es una medición.",
                "- Las filas cuyo optotipo mide menos de 5 píxeles físicos no se muestran; si la persona leyó la fila más pequeña posible, su agudeza real puede ser mejor.",
                "- Criterios pediátricos de referencia (AAPOS/AAP 2016): 3 años 20/50, 4 años 20/40, 5 años o más 20/32, o 2 líneas de diferencia entre ojos.",
                "- Referencias: ETDRS/LogMAR (Bailey y Lovie; escala por letra 0.02 LogMAR); AAO EyeWiki, Visual Acuity Testing in Adults; OMS, Vision and eye screening implementation handbook, 2024; Birch, Efficiency of the Ishihara test for identifying red-green colour deficiency, 1997."
        };
        for (String nota : notas) {
            for (String linea : envolver(nota, 92)) {
                p.color(Color.BLACK);
                p.texto(linea, MARGEN, p.y, PDType1Font.HELVETICA, 9.6f);
                p.y -= 13;
            }
        }
    }

    private static void seccion(Pagina p, String titulo) throws IOException {
        p.color(TEAL_SUAVE);
        p.rectangulo(MARGEN, p.y - 5, p.ancho - MARGEN * 2, 22, TEAL_SUAVE);
        p.color(TEAL);
        p.texto(titulo, MARGEN + 9, p.y + 2, PDType1Font.HELVETICA_BOLD, 12);
        p.y -= 29;
    }

    private static void filas(Pagina p, String[][] datos) throws IOException {
        for (String[] fila : datos) {
            p.color(TEAL);
            p.texto(fila[0] + ":", MARGEN, p.y, PDType1Font.HELVETICA_BOLD, 9.7f);
            p.color(Color.BLACK);
            p.texto(cortar(fila[1], 58), MARGEN + 175, p.y, PDType1Font.HELVETICA, 9.7f);
            p.y -= 15;
        }
    }

    private static void cabeceraTabla(Pagina p, String[] celdas, float[] anchos) throws IOException {
        float x = MARGEN;
        for (int i = 0; i < celdas.length; i++) {
            p.rectangulo(x, p.y - 5, anchos[i], 20, TEAL);
            p.color(Color.WHITE);
            p.texto(celdas[i], x + 6, p.y + 1, PDType1Font.HELVETICA_BOLD, 9.5f);
            x += anchos[i];
        }
        p.y -= 20;
    }

    private static void filaTabla(Pagina p, String[] celdas, float[] anchos) throws IOException {
        float x = MARGEN;
        for (int i = 0; i < celdas.length; i++) {
            p.rectangulo(x, p.y - 4, anchos[i], 19, Color.WHITE);
            p.linea(x, p.y - 4, x + anchos[i], p.y - 4, new Color(0xCF, 0xDE, 0xE0), 0.4f);
            p.color(Color.BLACK);
            p.texto(cortar(celdas[i], i == 0 ? 28 : 21), x + 6, p.y + 1, PDType1Font.HELVETICA, 8.8f);
            x += anchos[i];
        }
        p.y -= 19;
    }

    private static void textoEnvuelto(Pagina p, String etiqueta, String contenido, float tamano, Color color) throws IOException {
        p.color(TEAL);
        p.texto(etiqueta + ":", MARGEN, p.y, PDType1Font.HELVETICA_BOLD, 10);
        p.y -= 14;
        p.color(color);
        for (String linea : envolver(contenido, 94)) {
            p.texto(linea, MARGEN, p.y, PDType1Font.HELVETICA, tamano);
            p.y -= 13;
        }
        p.y -= 5;
    }

    private static void escribirPie(Pagina p, int pagina) throws IOException {
        float yPie = 35;
        p.linea(MARGEN, yPie + 10, p.ancho - MARGEN, yPie + 10, new Color(0xCF, 0xDE, 0xE0), 0.5f);
        p.color(GRIS);
        p.texto("Tamizaje automatizado - no sustituye una valoración optométrica u oftalmológica completa.", MARGEN, yPie, PDType1Font.HELVETICA, 7.6f);
    }

    private static String snellenOD(EvaluacionInfo e) { return valor(e != null && e.snellenDer != null ? e.snellenDer : e == null ? null : e.agudezaDer); }
    private static String snellenOI(EvaluacionInfo e) { return valor(e != null && e.snellenIzq != null ? e.snellenIzq : e == null ? null : e.agudezaIzq); }
    private static String snellenAO(EvaluacionInfo e) { return valor(e == null ? null : e.snellenAo); }
    private static String ishihara(EvaluacionInfo e) {
        if (e == null) return "N/D";
        return e.ishiharaTotal > 0 ? e.ishiharaAciertos + "/" + e.ishiharaTotal : valor(e.resultadoIshihara);
    }
    private static String dp(EvaluacionInfo e) {
        return e != null && e.dpCalibrada && e.distanciaPupilarMm != null
                ? String.format(Locale.US, "%.1f mm", e.distanciaPupilarMm) : "N/D";
    }
    private static String distanciaValidada(EvaluacionInfo e) {
        if (e == null || e.distanciaObjetivoMetros == null || e.toleranciaDistanciaCm == null) return "N/D (expediente previo)";
        return String.format(Locale.US, "%.1f m (tolerancia configurada +/- %.0f cm)", e.distanciaObjetivoMetros, e.toleranciaDistanciaCm);
    }
    private static String tipoColor(EvaluacionInfo e) {
        if (e == null || e.ishiharaTotal <= 0) return "N/D";
        return e.ishiharaRespuestasDeficiencia >= 2 ? "Tamiz positivo; subtipo no determinable" : "Sin señal automática";
    }
    private static String severidadColor(EvaluacionInfo e) {
        if (e == null || e.ishiharaTotal <= 0) return "N/D";
        return "No clasificable con 5 láminas";
    }
    private static String dioptrias(Double valor) { return valor == null ? "N/D" : String.format(Locale.US, "%.2f D", valor); }
    private enum Nivel {
        VERDE("VERDE - sin banderas automáticas"),
        AMARILLO("AMARILLO - revisión recomendada"),
        ROJO("ROJO - atención preferente");
        final String etiqueta;
        Nivel(String etiqueta) { this.etiqueta = etiqueta; }
    }

    private static Nivel nivelPaciente(EvaluacionInfo e) {
        if (e == null) return Nivel.AMARILLO;
        double od = e.logmarDer == null ? Double.NEGATIVE_INFINITY : e.logmarDer;
        double oi = e.logmarIzq == null ? Double.NEGATIVE_INFINITY : e.logmarIzq;
        boolean asimetria = e.logmarDer != null && e.logmarIzq != null && Math.abs(od - oi) >= 0.20;
        if (Math.max(od, oi) >= 0.50 || asimetria) return Nivel.ROJO;
        if (Math.max(od, oi) > 0.20 || e.ishiharaRespuestasDeficiencia >= 2 ||
                (e.cumplimientoDistanciaPct != null && e.cumplimientoDistanciaPct < 90)) return Nivel.AMARILLO;
        return Nivel.VERDE;
    }

    private static String mensajeGeneral(EvaluacionInfo e, Nivel nivel) {
        return switch (nivel) {
            case VERDE -> "No se detectaron banderas automáticas en este tamizaje.";
            case AMARILLO -> "Se detectó una señal que conviene revisar en una consulta de rutina.";
            case ROJO -> "Se detectó una diferencia o reducción visual que requiere una valoración profesional preferente.";
        };
    }

    private static String mensajeAgudezaPaciente(EvaluacionInfo e) {
        if (e == null || (e.logmarDer == null && e.logmarIzq == null)) return "No hay datos suficientes de visión de lejos.";
        StringBuilder resumen = new StringBuilder();
        agregarExplicacionOjo(resumen, "derecho", snellenOD(e), e.logmarDer);
        agregarExplicacionOjo(resumen, "izquierdo", snellenOI(e), e.logmarIzq);
        double od = e.logmarDer == null ? Double.NEGATIVE_INFINITY : e.logmarDer;
        double oi = e.logmarIzq == null ? Double.NEGATIVE_INFINITY : e.logmarIzq;
        if (e.logmarDer != null && e.logmarIzq != null && Math.abs(od - oi) >= 0.20)
            resumen.append(" Se observó una diferencia relevante entre ambos ojos; es importante revisarla.");
        if (Math.max(od, oi) > 0.20)
            resumen.append(od > oi ? " El ojo derecho presentó mayor dificultad para distinguir letras lejanas." : " El ojo izquierdo presentó mayor dificultad para distinguir letras lejanas.");
        if (resumen.isEmpty()) return "La visión de lejos se mantuvo dentro de las banderas automáticas de este tamizaje.";
        return resumen.toString().trim();
    }

    private static void agregarExplicacionOjo(StringBuilder texto, String ojo, String snellen, Double logmar) {
        if (snellen == null || "N/D".equals(snellen)) return;
        Integer denominador = denominadorSnellen(snellen);
        if (denominador == null || denominador <= 20 || (logmar != null && logmar <= 0.0)) return;
        double metrosPrueba = 6.1;
        double metrosEquivalentes = denominador * metrosPrueba / 20.0;
        texto.append("En el ojo ").append(ojo).append(" se registró ").append(snellen)
                .append(": presenta dificultad para ver detalles lejanos. A unos ")
                .append(String.format(Locale.US, "%.1f", metrosPrueba)).append(" metros ve aproximadamente lo que una persona con visión estándar distingue a ")
                .append(String.format(Locale.US, "%.1f", metrosEquivalentes)).append(" metros.");
    }

    private static Integer denominadorSnellen(String snellen) {
        try {
            String limpio = snellen.replace(" ", "");
            if (!limpio.matches("20/[0-9]+")) return null;
            return Integer.parseInt(limpio.substring(3));
        } catch (Exception ex) { return null; }
    }

    private static String mensajeColorPaciente(EvaluacionInfo e) {
        if (e == null || e.ishiharaTotal <= 0) return "No hay datos suficientes de visión de colores.";
        if (e.ishiharaRespuestasDeficiencia >= 2)
            return "Hubo respuestas que pueden indicar dificultad para distinguir algunos tonos rojo-verde. Requiere confirmación profesional.";
        return "No se detectaron respuestas compatibles con una dificultad rojo-verde en este tamizaje.";
    }

    private static String recomendacionPaciente(EvaluacionInfo e, Nivel nivel) {
        return switch (nivel) {
            case VERDE -> "Mantenga revisiones visuales periódicas. Consulte antes si aparecen molestias o cambios en la visión.";
            case AMARILLO -> "Agende una consulta con optometría u oftalmología y lleve este reporte para una evaluación completa.";
            case ROJO -> "Agende una valoración con optometría u oftalmología en las próximas semanas y lleve este reporte. Si la pérdida de visión fue súbita o hay dolor, ojo rojo o trauma, busque atención médica inmediata.";
        };
    }
    private static String sintesisProfesional(EvaluacionInfo e) {
        if (e == null) return "No hay una evaluación disponible para interpretar.";
        List<String> hallazgos = new ArrayList<>();
        hallazgos.add("Agudeza registrada: OD " + snellenOD(e) + "; OI " + snellenOI(e) + "; AO " + snellenAO(e) + ".");
        if (e.ishiharaTotal > 0) {
            hallazgos.add("Tamiz cromático: " + e.ishiharaAciertos + "/" + e.ishiharaTotal + " respuestas concordantes; "
                    + e.ishiharaRespuestasDeficiencia + " patrón(es) no concordante(s).");
        } else {
            hallazgos.add("Tamiz cromático: " + valor(e.resultadoIshihara) + ".");
        }
        if (e.alertasClinicas != null && !e.alertasClinicas.isBlank() && !e.alertasClinicas.startsWith("Sin banderas")) {
            hallazgos.add("Existen banderas automáticas que requieren correlación con antecedentes, refracción y exploración ocular.");
        } else {
            hallazgos.add("No se almacenaron banderas automáticas de referencia inmediata.");
        }
        return String.join(" ", hallazgos);
    }
    private static String decimal(Double valor) { return valor == null ? "N/D" : String.format(Locale.US, "%.2f", valor); }
    private static String porcentaje(Double valor) { return valor == null ? "N/D" : String.format(Locale.US, "%.0f%%", valor); }
    private static String segundos(Long valor) { return valor == null ? "N/D" : String.format(Locale.US, "%.2f s", valor / 1000.0); }
    private static String entero(int valor) { return valor <= 0 ? "N/D" : String.valueOf(valor); }
    private static String valor(String texto) { return texto == null || texto.trim().isEmpty() ? "N/D" : texto.trim(); }
    private static String safe(String texto) { return texto == null ? "SIN_CURP" : texto.replaceAll("[^A-Za-z0-9]", ""); }
    private static String cortar(String texto, int maximo) { return texto.length() <= maximo ? texto : texto.substring(0, Math.max(0, maximo - 3)) + "..."; }

    private static List<String> separarAlertas(String alertas) {
        List<String> resultado = new ArrayList<>();
        for (String parte : alertas.split("\\s*\\|\\s*")) if (!parte.trim().isEmpty()) resultado.add(parte.trim());
        return resultado.isEmpty() ? List.of("Sin alertas registradas.") : resultado;
    }

    private static List<String> envolver(String texto, int maximoCaracteres) {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : valor(texto).split("\\s+")) {
            if (actual.length() > 0 && actual.length() + 1 + palabra.length() > maximoCaracteres) {
                lineas.add(actual.toString());
                actual.setLength(0);
            }
            if (actual.length() > 0) actual.append(' ');
            actual.append(palabra);
        }
        if (actual.length() > 0) lineas.add(actual.toString());
        return lineas;
    }

    /** Ajusta cada renglón al ancho real de la fuente, no a una cantidad aproximada de caracteres. */
    private static List<String> envolverPorAncho(String texto, float anchoMaximo, PDFont fuente, float tamano) throws IOException {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : valor(texto).split("\\s+")) {
            String candidata = actual.length() == 0 ? palabra : actual + " " + palabra;
            if (fuente.getStringWidth(candidata) / 1000f * tamano <= anchoMaximo) {
                actual.setLength(0);
                actual.append(candidata);
            } else {
                if (actual.length() > 0) lineas.add(actual.toString());
                actual.setLength(0);
                actual.append(palabra);
            }
        }
        if (actual.length() > 0) lineas.add(actual.toString());
        return lineas.isEmpty() ? List.of("N/D") : lineas;
    }

    private static final class Pagina {
        final PDPage page;
        final PDPageContentStream cs;
        final float ancho;
        float y;

        Pagina(PDPage page, PDPageContentStream cs) {
            this.page = page;
            this.cs = cs;
            this.ancho = page.getMediaBox().getWidth();
            this.y = page.getMediaBox().getHeight() - MARGEN;
        }

        void color(Color color) throws IOException { cs.setNonStrokingColor(color); }
        void rectangulo(float x, float y, float ancho, float alto, Color color) throws IOException {
            cs.setNonStrokingColor(color); cs.addRect(x, y, ancho, alto); cs.fill();
        }
        void linea(float x1, float y1, float x2, float y2, Color color, float grosor) throws IOException {
            cs.setStrokingColor(color); cs.setLineWidth(grosor); cs.moveTo(x1, y1); cs.lineTo(x2, y2); cs.stroke();
        }
        void texto(String texto, float x, float y, PDFont fuente, float tamano) throws IOException {
            cs.beginText(); cs.setFont(fuente, tamano); cs.newLineAtOffset(x, y); cs.showText(codificable(fuente, sanitizar(texto))); cs.endText();
        }
        void cerrar() throws IOException { cs.close(); }
        /** Las fuentes estándar del PDF solo tienen WinAnsi: un "≥" o "⚠" haría fallar todo el informe. */
        private String codificable(PDFont fuente, String texto) {
            StringBuilder sb = new StringBuilder(texto.length());
            for (int i = 0; i < texto.length(); ) {
                int cp = texto.codePointAt(i);
                String c = new String(Character.toChars(cp));
                try { fuente.encode(c); sb.append(c); } catch (Exception ex) { sb.append('?'); }
                i += Character.charCount(cp);
            }
            return sb.toString();
        }

        private String sanitizar(String texto) {
            return valor(texto).replace('–', '-').replace('—', '-').replace('“', '"').replace('”', '"')
                    .replace("≥", ">=").replace("≤", "<=").replace("→", "->").replace("⚠", "!").replace("✓", "OK").replace("✗", "X");
        }
    }
}
