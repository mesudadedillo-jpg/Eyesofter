-- ==========================================================
-- Backup autogenerado de la base de datos EYESOFTER
-- Compatible para importar en MySQL Workbench
-- ==========================================================

SET FOREIGN_KEY_CHECKS = 0;

-- Estructura de tabla: tutor
DROP TABLE IF EXISTS `tutor`;
CREATE TABLE tutor (curp TEXT PRIMARY KEY, nombre TEXT NOT NULL, telefono TEXT, correo TEXT);

-- Datos de la tabla: tutor
INSERT INTO `tutor` VALUES ('HERL820315HDFRNR01', 'Luis Alberto Hernández Ramos', '7751124589', 'l.hernandez.tul@gmail.com');
INSERT INTO `tutor` VALUES ('GARM850720MDFNZM02', 'María Elena García Morales', '7751249876', 'elena.garcia_tul@outlook.com');
INSERT INTO `tutor` VALUES ('LOPH801105HDFRZR03', 'Hugo López Pérez', '7751436521', 'hugo.lopez.p@gmail.com');
INSERT INTO `tutor` VALUES ('MART840418MDFLND04', 'Teresa Martínez Soto', '7757531204', 'teresa_mtz84@hotmail.com');
INSERT INTO `tutor` VALUES ('SANI860925HDFRRN05', 'Ignacio Sánchez Ríos', '7757558912', 'ignacio.sanchez.r@gmail.com');
INSERT INTO `tutor` VALUES ('RODR830112MDFLZS06', 'Rosa Delia Rodríguez Vargas', '7751023344', 'rosa.rodriguez.v@gmail.com');
INSERT INTO `tutor` VALUES ('PAGJ810630HDFRTR07', 'Jorge Antonio Pacheco Cruz', '7751307788', 'jorge_pacheco_cruz@yahoo.com');
INSERT INTO `tutor` VALUES ('DOMM870814MDFLNR08', 'Mónica Domínguez Téllez', '7751684490', 'monica.dominguez.t@gmail.com');
INSERT INTO `tutor` VALUES ('CAST850203HDFRLL09', 'Carlos Castillo Islas', '7757542211', 'carlos.castillo.islas@gmail.com');
INSERT INTO `tutor` VALUES ('VARG861219MDFRMD10', 'Gabriela Vargas Mendoza', '7751190055', 'gaby.vargas.m@gmail.com');


-- Estructura de tabla: paciente
DROP TABLE IF EXISTS `paciente`;
CREATE TABLE paciente (curp TEXT PRIMARY KEY, nombre TEXT NOT NULL, fecha_nacimiento TEXT, grado_grupo TEXT, curp_tutor TEXT, FOREIGN KEY(curp_tutor) REFERENCES tutor(curp) ON DELETE SET NULL ON UPDATE CASCADE);

-- Datos de la tabla: paciente
INSERT INTO `paciente` VALUES ('HERM180312HDFNLB01', 'Mateo Hernández García', '2018-03-12', '1° Primaria · A', 'HERL820315HDFRNR01');
INSERT INTO `paciente` VALUES ('GARM180524MDFRLN02', 'Sofía García Morales', '2018-05-24', '1° Primaria · A', 'GARM850720MDFNZM02');
INSERT INTO `paciente` VALUES ('LOPR170709HDFPNS03', 'Santiago López Ruiz', '2017-07-09', '2° Primaria · B', 'LOPH801105HDFRZR03');
INSERT INTO `paciente` VALUES ('MARV170915MDFTSA04', 'Valentina Martínez Vargas', '2017-09-15', '2° Primaria · B', 'MART840418MDFLND04');
INSERT INTO `paciente` VALUES ('SANR160128HDFSRG05', 'Emiliano Sánchez Romero', '2016-01-28', '3° Primaria · A', 'SANI860925HDFRRN05');
INSERT INTO `paciente` VALUES ('RODC160410MDFRLT06', 'Camila Rodríguez Castillo', '2016-04-10', '3° Primaria · A', 'RODR830112MDFLZS06');
INSERT INTO `paciente` VALUES ('PACL150822HDFCPR07', 'Leonardo Pacheco López', '2015-08-22', '4° Primaria · B', 'PAGJ810630HDFRTR07');
INSERT INTO `paciente` VALUES ('DOMR151130MDFMTN08', 'Regina Domínguez Téllez', '2015-11-30', '4° Primaria · B', 'DOMM870814MDFLNR08');
INSERT INTO `paciente` VALUES ('CASM140605HDFSLS09', 'Sebastián Castillo Mendoza', '2014-06-05', '5° Primaria · A', 'CAST850203HDFRLL09');
INSERT INTO `paciente` VALUES ('VARM141018MDFGNS10', 'Ximena Vargas Mendoza', '2014-10-18', '6° Primaria · A', 'VARG861219MDFRMD10');


-- Estructura de tabla: evaluacion
DROP TABLE IF EXISTS `evaluacion`;
CREATE TABLE evaluacion (id_evaluacion INTEGER PRIMARY KEY AUTO_INCREMENT, curp_paciente TEXT, fecha_hora TEXT, agudeza_izq TEXT, agudeza_der TEXT, resultado_ishihara TEXT, tasa_ruido_wer REAL, estado_recomendacion TEXT, snellen_izq TEXT, snellen_der TEXT, snellen_ao TEXT, logmar_izq REAL, logmar_der REAL, logmar_ao REAL, dioptrias_izq REAL, dioptrias_der REAL, dioptrias_ao REAL, ishihara_aciertos INTEGER, ishihara_total INTEGER, ishihara_respuestas_deficiencia INTEGER, cumplimiento_distancia_pct REAL, muestras_distancia INTEGER, tiempo_respuesta_ms INTEGER, distancia_pupilar_mm REAL, dp_calibrada INTEGER, metodo_entrada TEXT, distancia_objetivo_m REAL, tolerancia_distancia_cm REAL, alertas_clinicas TEXT, FOREIGN KEY(curp_paciente) REFERENCES paciente(curp) ON DELETE CASCADE ON UPDATE CASCADE);

-- Datos de la tabla: evaluacion
INSERT INTO `evaluacion` VALUES (1, 'HERM180312HDFNLB01', '2026-08-10 09:30:15', '20/20', '20/20', '5/5', 0.0, 'Resultado dentro de parámetros esperados', '20/20', '20/20', '20/20', 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 5, 5, 0, 98.2, 140, 4200, 53.5, 1, 'Voz', 1.0, 5.0, 'Sin observaciones.');
INSERT INTO `evaluacion` VALUES (2, 'GARM180524MDFRLN02', '2026-08-10 10:15:22', '20/25', '20/40', '5/5', 0.01, 'Se recomienda valoración visual profesional', '20/25', '20/40', '20/25', 0.1, 0.3, 0.1, 0.25, 0.75, 0.25, 5, 5, 0, 96.5, 135, 5100, 52.0, 1, 'Teclado', 1.0, 5.0, 'Asimetría de agudeza visual detectada entre ambos ojos.');
INSERT INTO `evaluacion` VALUES (3, 'LOPR170709HDFPNS03', '2026-08-11 09:00:40', '20/20', '20/20', '2/5', 0.0, 'Se identificaron hallazgos en prueba cromática. Amerita valoración profesional.', '20/20', '20/20', '20/20', 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2, 5, 3, 97.8, 150, 4800, 54.0, 1, 'Voz', 1.0, 5.0, 'Sospecha de deficiencia en percepción de color rojo-verde.');
INSERT INTO `evaluacion` VALUES (4, 'MARV170915MDFTSA04', '2026-08-11 11:20:05', '20/40', '20/50', '5/5', 0.02, 'Se recomienda valoración visual profesional', '20/40', '20/50', '20/40', 0.3, 0.4, 0.3, 0.75, 1.0, 0.75, 5, 5, 0, 95.1, 160, 6200, 53.0, 1, 'Voz', 1.0, 5.0, 'Agudeza visual reducida en ambos ojos.');
INSERT INTO `evaluacion` VALUES (5, 'SANR160128HDFSRG05', '2026-08-12 08:45:10', '20/20', '20/20', '5/5', 0.0, 'Resultado dentro de parámetros esperados', '20/20', '20/20', '20/20', 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 5, 5, 0, 99.1, 130, 3900, 55.0, 1, 'Voz', 1.0, 5.0, 'Sin observaciones.');
INSERT INTO `evaluacion` VALUES (6, 'RODC160410MDFRLT06', '2026-08-12 10:00:33', '20/30', '20/25', '5/5', 0.0, 'Resultado dentro de parámetros esperados con seguimiento preventivo', '20/30', '20/25', '20/25', 0.18, 0.1, 0.1, 0.5, 0.25, 0.25, 5, 5, 0, 94.7, 142, 5400, 54.5, 1, 'Voz', 1.0, 5.0, 'Sin banderas críticas.');
INSERT INTO `evaluacion` VALUES (7, 'PACL150822HDFCPR07', '2026-08-15 09:10:50', '20/20', '20/60', '4/5', 0.03, 'Se recomienda valoración visual profesional urgente', '20/20', '20/60', '20/25', 0.0, 0.48, 0.1, 0.0, 1.25, 0.25, 4, 5, 0, 93.4, 155, 6800, 56.0, 1, 'Teclado', 1.0, 5.0, 'Diferencia significativa mayor a 2 líneas de Snellen.');
INSERT INTO `evaluacion` VALUES (8, 'DOMR151130MDFMTN08', '2026-08-15 11:40:12', '20/20', '20/20', '5/5', 0.0, 'Resultado dentro de parámetros esperados', '20/20', '20/20', '20/20', 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 5, 5, 0, 98.9, 128, 4100, 55.5, 1, 'Voz', 1.0, 5.0, 'Sin observaciones.');
INSERT INTO `evaluacion` VALUES (9, 'CASM140605HDFSLS09', '2026-08-16 08:30:00', '20/50', '20/40', '5/5', 0.0, 'Se recomienda valoración visual profesional', '20/50', '20/40', '20/40', 0.4, 0.3, 0.3, 1.0, 0.75, 0.75, 5, 5, 0, 96.2, 145, 5600, 57.0, 1, 'Voz', 1.0, 5.0, 'El alumno refiere dificultad para enfocar texto a distancia.');
INSERT INTO `evaluacion` VALUES (10, 'VARM141018MDFGNS10', '2026-08-16 10:50:45', '20/20', '20/20', '5/5', 0.0, 'Resultado dentro de parámetros esperados', '20/20', '20/20', '20/20', 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 5, 5, 0, 99.4, 132, 3850, 58.0, 1, 'Voz', 1.0, 5.0, 'Sin observaciones.');


-- Estructura de tabla: cita
DROP TABLE IF EXISTS `cita`;
CREATE TABLE cita (id_cita INTEGER PRIMARY KEY AUTO_INCREMENT, curp_paciente TEXT NOT NULL, fecha TEXT NOT NULL, hora TEXT NOT NULL, tipo TEXT NOT NULL, notas TEXT, estado TEXT NOT NULL DEFAULT 'EN_ESPERA', creada_en TEXT NOT NULL, FOREIGN KEY(curp_paciente) REFERENCES paciente(curp) ON DELETE CASCADE ON UPDATE CASCADE);

-- Datos de la tabla: cita


-- Estructura de tabla: reporte_pdf
DROP TABLE IF EXISTS `reporte_pdf`;
CREATE TABLE reporte_pdf (id_reporte INTEGER PRIMARY KEY AUTO_INCREMENT, id_evaluacion INTEGER NOT NULL, ruta TEXT NOT NULL, generado_en TEXT NOT NULL, FOREIGN KEY(id_evaluacion) REFERENCES evaluacion(id_evaluacion) ON DELETE CASCADE);

-- Datos de la tabla: reporte_pdf
INSERT INTO `reporte_pdf` VALUES (1, 1, 'reportes/2026/08/Reporte_HERM180312_Mateo_Hernandez.pdf', '2026-08-10 09:32:00');
INSERT INTO `reporte_pdf` VALUES (2, 2, 'reportes/2026/08/Reporte_GARM180524_Sofia_Garcia.pdf', '2026-08-10 10:17:30');
INSERT INTO `reporte_pdf` VALUES (3, 3, 'reportes/2026/08/Reporte_LOPR170709_Santiago_Lopez.pdf', '2026-08-11 09:02:15');
INSERT INTO `reporte_pdf` VALUES (4, 7, 'reportes/2026/08/Reporte_PACL150822_Leonardo_Pacheco.pdf', '2026-08-15 09:12:40');
INSERT INTO `reporte_pdf` VALUES (5, 9, 'reportes/2026/08/Reporte_CASM140605_Sebastian_Castillo.pdf', '2026-08-16 08:32:10');


-- Estructura de tabla: cita (Exportada desde citas_agenda.json)
DROP TABLE IF EXISTS `cita`;
CREATE TABLE `cita` (`id_cita` INTEGER PRIMARY KEY AUTO_INCREMENT, `curp_paciente` TEXT NOT NULL, `fecha` TEXT NOT NULL, `hora` TEXT NOT NULL, `tipo` TEXT NOT NULL, `notas` TEXT, `estado` TEXT NOT NULL DEFAULT 'EN_ESPERA', `creada_en` TEXT NOT NULL);

-- Datos de la tabla: cita
INSERT INTO `cita` VALUES (9, 'CASM140605HDFSLS09', '2026-08-21', '08:30', 'Seguimiento Clínico', 'Sospecha de miopía.', 'EN_ESPERA', '2026-08-21T08:30:00');
INSERT INTO `cita` VALUES (10, 'VARM141018MDFGNS10', '2026-08-21', '10:50', 'Tamizaje Escolar Anual', 'Egreso de primaria.', 'EN_ESPERA', '2026-08-21T10:50:00');
INSERT INTO `cita` VALUES (7, 'PACL150822HDFCPR07', '2026-08-20', '09:10', 'Seguimiento Clínico', 'Bajo rendimiento en pizarrón.', 'EN_ESPERA', '2026-08-20T09:10:00');
INSERT INTO `cita` VALUES (8, 'DOMR151130MDFMTN08', '2026-08-20', '11:40', 'Tamizaje Escolar Anual', 'Revisión anual rutinaria.', 'EN_ESPERA', '2026-08-20T11:40:00');
INSERT INTO `cita` VALUES (5, 'SANR160128HDFSRG05', '2026-08-19', '08:45', 'Tamizaje Escolar Anual', 'Control rutinario.', 'EN_ESPERA', '2026-08-19T08:45:00');
INSERT INTO `cita` VALUES (6, 'RODC160410MDFRLT06', '2026-08-19', '10:00', 'Tamizaje Escolar Anual', 'Revisión de ciclo escolar.', 'EN_ESPERA', '2026-08-19T10:00:00');
INSERT INTO `cita` VALUES (3, 'LOPR170709HDFPNS03', '2026-08-18', '09:00', 'Tamizaje Escolar Anual', 'Revisión periódica de agudeza y color.', 'EN_ESPERA', '2026-08-18T09:00:00');
INSERT INTO `cita` VALUES (4, 'MARV170915MDFTSA04', '2026-08-18', '11:20', 'Tamizaje Escolar Anual', 'Reporta dolor de cabeza vespertino.', 'EN_ESPERA', '2026-08-18T11:20:00');
INSERT INTO `cita` VALUES (1, 'HERM180312HDFNLB01', '2026-08-17', '09:30', 'Tamizaje Escolar Anual', 'Revisión preventiva general.', 'COMPLETADA', '2026-08-17T09:30:00');
INSERT INTO `cita` VALUES (2, 'GARM180524MDFRLN02', '2026-08-17', '10:15', 'Tamizaje Escolar Anual', 'Se acerca mucho a los cuadernos.', 'COMPLETADA', '2026-08-17T10:15:00');


SET FOREIGN_KEY_CHECKS = 1;
