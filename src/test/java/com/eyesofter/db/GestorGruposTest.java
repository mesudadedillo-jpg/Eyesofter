package com.eyesofter.db;

import com.eyesofter.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Grupos escolares: inscribir y pasar de año sin editar paciente por paciente (base SQLite temporal). */
class GestorGruposTest {

    private static final LocalDateTime JULIO_2027 = LocalDateTime.of(2027, 7, 10, 12, 0);

    private String urlOriginal;
    private File base;

    @BeforeEach
    void baseTemporal() throws Exception {
        urlOriginal = AppConfig.URL_BASE_DATOS;
        base = Files.createTempFile("eyesofter-grupos", ".db").toFile();
        AppConfig.URL_BASE_DATOS = "jdbc:sqlite:" + base.getAbsolutePath();
        GestorBaseDatos.inicializarBaseDatos();
    }

    @AfterEach
    void limpiar() {
        AppConfig.URL_BASE_DATOS = urlOriginal;
        if (base != null) base.delete();
    }

    private static void paciente(String curp, String grado) {
        assertTrue(GestorBaseDatos.registrarPaciente(curp, "Paciente " + curp, "", grado, null));
    }

    private static GestorBaseDatos.PacienteInfo leer(String curp) {
        return GestorBaseDatos.buscarPacientePorCurp(curp);
    }

    @Test
    void cicloEscolarEmpiezaEnAgosto() {
        assertEquals("2026-2027", GestorGrupos.cicloActual(LocalDate.of(2026, 8, 1)));
        assertEquals("2026-2027", GestorGrupos.cicloActual(LocalDate.of(2027, 7, 31)));
        assertEquals("2027-2028", GestorGrupos.cicloSiguiente("2026-2027"));
    }

    @Test
    void validaGradoSegunNivel() {
        assertNull(GestorGrupos.validar("Primaria", 6, "A", "2026-2027"));
        assertNotNull(GestorGrupos.validar("Secundaria", 4, "A", "2026-2027"));
        assertNotNull(GestorGrupos.validar("Primaria", 2, "AB", "2026-2027"));
    }

    @Test
    void inscribirPoneLaEtiquetaDelGrupo() {
        paciente("AAAA000000HHGRRR01", "texto libre");
        int id = GestorGrupos.crear("Primaria", 3, "b", "2026-2027", "Mtra. Ruiz");
        assertEquals(1, GestorGrupos.inscribir(id, List.of("AAAA000000HHGRRR01")));
        GestorBaseDatos.PacienteInfo p = leer("AAAA000000HHGRRR01");
        assertEquals(id, p.idGrupo);
        assertEquals("3° B - Primaria", p.gradoGrupo);
    }

    @Test
    void noPermiteDosGruposActivosIguales() {
        GestorGrupos.crear("Primaria", 1, "A", "2026-2027", null);
        assertThrows(IllegalArgumentException.class, () -> GestorGrupos.crear("Primaria", 1, "A", "2026-2027", null));
    }

    @Test
    void elRegistroConLaEtiquetaDelPortalSeInscribeSolo() {
        int id = GestorGrupos.crear("Secundaria", 2, "C", "2026-2027", null);
        paciente("BBBB000000HHGRRR02", "2° C - Secundaria");
        assertEquals(id, leer("BBBB000000HHGRRR02").idGrupo);
    }

    @Test
    void pasarDeAnioMueveAlGrupoYASusPacientes() {
        int id = GestorGrupos.crear("Primaria", 4, "A", "2026-2027", null);
        paciente("CCCC000000HHGRRR03", "");
        paciente("DDDD000000HHGRRR04", "");
        GestorGrupos.inscribir(id, List.of("CCCC000000HHGRRR03", "DDDD000000HHGRRR04"));

        GestorGrupos.Resultado r = GestorGrupos.promover(id, JULIO_2027);

        assertTrue(r.exito(), String.join(" ", r.avisos()));
        assertEquals(2, r.pacientesMovidos());
        assertEquals("5° A - Primaria", leer("CCCC000000HHGRRR03").gradoGrupo);
        assertEquals("2027-2028", GestorGrupos.buscar(id).cicloEscolar());
        // Un segundo intento seguido se rechaza.
        assertFalse(GestorGrupos.promover(id, JULIO_2027.plusMinutes(5)).exito());
        assertEquals(5, GestorGrupos.buscar(id).grado());
    }

    @Test
    void ultimoGradoEgresa() {
        int id = GestorGrupos.crear("Secundaria", 3, "A", "2026-2027", null);
        paciente("EEEE000000HHGRRR05", "");
        GestorGrupos.inscribir(id, List.of("EEEE000000HHGRRR05"));

        GestorGrupos.Resultado r = GestorGrupos.promover(id, JULIO_2027);

        assertTrue(r.exito());
        assertEquals(1, r.gruposEgresados());
        assertFalse(GestorGrupos.buscar(id).activo());
        assertTrue(leer("EEEE000000HHGRRR05").egresado);
        // Ya se puede abrir otro 3° A.
        assertDoesNotThrow(() -> { GestorGrupos.crear("Secundaria", 3, "A", "2027-2028", null); });
    }

    @Test
    void todosDeArribaHaciaAbajoYTodoONada() {
        int sexto = GestorGrupos.crear("Primaria", 6, "A", "2026-2027", null);
        int quinto = GestorGrupos.crear("Primaria", 5, "A", "2026-2027", null);
        int primero = GestorGrupos.crear("Primaria", 1, "A", "2026-2027", null);
        paciente("FFFF000000HHGRRR06", "");
        GestorGrupos.inscribir(quinto, List.of("FFFF000000HHGRRR06"));

        GestorGrupos.Resultado r = GestorGrupos.promoverTodos(null, JULIO_2027);

        assertTrue(r.exito(), String.join(" ", r.avisos()));
        assertEquals(2, r.gruposPromovidos());
        assertEquals(1, r.gruposEgresados());
        assertFalse(GestorGrupos.buscar(sexto).activo());
        assertEquals(6, GestorGrupos.buscar(quinto).grado());
        assertEquals(2, GestorGrupos.buscar(primero).grado());
        assertEquals("6° A - Primaria", leer("FFFF000000HHGRRR06").gradoGrupo);

        // Repetirlo el mismo día no cambia nada.
        assertFalse(GestorGrupos.promoverTodos(null, JULIO_2027.plusHours(1)).exito());
        assertEquals(2, GestorGrupos.buscar(primero).grado());
    }

    @Test
    void eliminarGrupoConservaAlPaciente() {
        int id = GestorGrupos.crear("Primaria", 2, "D", "2026-2027", null);
        paciente("GGGG000000HHGRRR07", "");
        GestorGrupos.inscribir(id, List.of("GGGG000000HHGRRR07"));
        assertTrue(GestorGrupos.eliminar(id));
        GestorBaseDatos.PacienteInfo p = leer("GGGG000000HHGRRR07");
        assertNotNull(p);
        assertNull(p.idGrupo);
        assertEquals("2° D - Primaria", p.gradoGrupo);
    }
}
