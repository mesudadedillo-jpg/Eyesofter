package com.eyesofter.db;

import com.eyesofter.config.AppConfig;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;

public class mostrardb {

    // Extraemos la URL exactamente igual que lo hace tu GestorBaseDatos
    private static String obtenerUrlCorrecta() {
        String configurada = AppConfig.URL_BASE_DATOS;
        if (configurada != null && !configurada.isBlank() && !"jdbc:sqlite:eyesofter_local.db".equals(configurada.trim())) {
            return configurada.trim();
        }
        return "jdbc:sqlite:" + new File(System.getProperty("eyesofter.home", "."), "eyesofter_local.db").getPath();
    }

    public static void main(String[] args) {
        System.out.println("Iniciando consulta a la base de datos local de EYESOFTER...");

        // 1. Mostramos los datos en consola
        GestorBaseDatos.inicializarBaseDatos();

        // 2. Ejecutamos la exportación autónoma
        System.out.println("\nGenerando copia de seguridad en SQL para Workbench...");
        exportarBaseDatosASql("eyesofter_export.sql");

        System.out.println("Proceso finalizado.");
    }

    /**
     * Método autónomo que lee las tablas y datos y genera un archivo .sql
     */
    public static void exportarBaseDatosASql(String archivoDestino) {
        String url = obtenerUrlCorrecta();
        System.out.println("Conectando para exportar usando la URL: " + url);

        // Al usar FileWriter con 'false', reemplaza el archivo si ya existe.
        try (Connection conn = DriverManager.getConnection(url);
             PrintWriter writer = new PrintWriter(new FileWriter(archivoDestino, false))) {

            // Cabecera del archivo SQL
            writer.println("-- ==========================================================");
            writer.println("-- Backup autogenerado de la base de datos EYESOFTER");
            writer.println("-- Compatible para importar en MySQL Workbench");
            writer.println("-- ==========================================================\n");

            // Desactivar llaves foráneas temporalmente (ayuda a evitar errores al importar en MySQL)
            writer.println("SET FOREIGN_KEY_CHECKS = 0;\n");

            Statement stmt = conn.createStatement();

            // Consultamos sqlite_master para obtener las tablas de SQLite
            ResultSet rsTablas = stmt.executeQuery("SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'");

            while (rsTablas.next()) {
                String nombreTabla = rsTablas.getString("name");
                String sqlCreacion = rsTablas.getString("sql");

                if (sqlCreacion == null) continue;

                // Adaptamos la sintaxis: SQLite usa AUTOINCREMENT, MySQL usa AUTO_INCREMENT
                sqlCreacion = sqlCreacion.replaceAll("(?i)AUTOINCREMENT", "AUTO_INCREMENT");

                // Escribimos la estructura de la tabla asegurando que se borre si ya existe
                writer.println("-- Estructura de tabla: " + nombreTabla);
                writer.println("DROP TABLE IF EXISTS `" + nombreTabla + "`;");
                writer.println(sqlCreacion + ";\n");

                // Extraemos y escribimos los datos de esta tabla
                Statement stmtDatos = conn.createStatement();
                ResultSet rsDatos = stmtDatos.executeQuery("SELECT * FROM `" + nombreTabla + "`");
                ResultSetMetaData metaDatos = rsDatos.getMetaData();
                int numColumnas = metaDatos.getColumnCount();

                writer.println("-- Datos de la tabla: " + nombreTabla);
                while (rsDatos.next()) {
                    StringBuilder insert = new StringBuilder("INSERT INTO `" + nombreTabla + "` VALUES (");
                    for (int i = 1; i <= numColumnas; i++) {
                        Object valor = rsDatos.getObject(i);
                        if (valor == null) {
                            insert.append("NULL");
                        } else if (valor instanceof String) {
                            // Escapamos comillas simples para evitar errores en SQL
                            String valorStr = ((String) valor).replace("'", "''");
                            insert.append("'").append(valorStr).append("'");
                        } else {
                            insert.append(valor.toString());
                        }
                        if (i < numColumnas) insert.append(", ");
                    }
                    insert.append(");");
                    writer.println(insert.toString());
                }
                writer.println("\n");
                rsDatos.close();
                stmtDatos.close();
            }
            rsTablas.close();
            stmt.close();

            // Volvemos a activar las llaves foráneas
            writer.println("SET FOREIGN_KEY_CHECKS = 1;");

            System.out.println("¡Éxito! Archivo SQL generado en la raíz de tu proyecto como: " + archivoDestino);

        } catch (Exception e) {
            System.err.println("Error al intentar exportar la base de datos a SQL: " + e.getMessage());
            e.printStackTrace();
        }
    }
}