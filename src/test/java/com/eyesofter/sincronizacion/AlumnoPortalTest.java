package com.eyesofter.sincronizacion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AlumnoPortalTest {

    @Test
    void leeAlumnoCompletoConTutorYSeguimiento() {
        String json = "[{\"matricula\":\"A001\",\"nombre\":\"Ana\",\"apellidos\":\"López\",\"nivel\":\"Primaria\",\"grado\":3,\"grupo\":\"B\","
                + "\"fechaNacimiento\":\"2017-03-01\",\"usaLentes\":true,\"tutorNombre\":\"María López\",\"tutorTelefono\":\"7751110000\","
                + "\"tutorCorreo\":\"tutor@eyesofter.mx\",\"ultimoTamizaje\":\"2026-09-10\",\"ultimoSemaforo\":\"Rojo\",\"casoAbierto\":\"Abierto\"}]";
        List<ClienteSuite.AlumnoPortal> lista = ClienteSuite.alumnosDe(new ClienteSuite.Respuesta(200, json));
        ClienteSuite.AlumnoPortal a = lista.get(0);
        assertEquals("Ana López", a.nombreCompleto());
        assertEquals("3° B - Primaria", a.gradoGrupo());
        assertTrue(a.usaLentes());
        assertEquals("María López", a.tutorNombre());
        assertTrue(a.pendienteEspecialista());
        assertTrue(a.estadoSeguimiento().contains("especialista"));
    }

    @Test
    void unServidorAnteriorSinCamposNuevosSigueFuncionando() {
        String json = "[{\"matricula\":\"A002\",\"nombre\":\"Luis\",\"apellidos\":\"Ruiz\",\"nivel\":\"Primaria\",\"grado\":4,\"grupo\":\"A\"}]";
        ClienteSuite.AlumnoPortal a = ClienteSuite.alumnosDe(new ClienteSuite.Respuesta(200, json)).get(0);
        assertEquals("", a.tutorNombre());
        assertFalse(a.pendienteEspecialista());
        assertEquals("Sin tamizaje", a.estadoSeguimiento());
    }
}
