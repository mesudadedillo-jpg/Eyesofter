package com.eyesofter.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ConfiguracionUsuariosTest {

    @BeforeAll
    static void directorioTemporal() throws Exception {
        Path dir = Files.createTempDirectory("eyesofter-usuarios");
        System.setProperty("eyesofter.home", dir.toString());
    }

    @Test
    void hashNoGuardaTextoPlanoYVerifica() {
        String h = ConfiguracionUsuarios.hash("Secreto123");
        assertTrue(h.startsWith("pbkdf2$"));
        assertFalse(h.contains("Secreto123"));
        assertTrue(ConfiguracionUsuarios.coincide(h, "Secreto123"));
        assertFalse(ConfiguracionUsuarios.coincide(h, "secreto123"));
        assertNotEquals(h, ConfiguracionUsuarios.hash("Secreto123"), "cada hash lleva sal distinta");
    }

    @Test
    void reglasDeContrasena() {
        assertFalse(ConfiguracionUsuarios.contrasenaValida("root"));
        assertFalse(ConfiguracionUsuarios.contrasenaValida("corta1"));
        assertFalse(ConfiguracionUsuarios.contrasenaValida("sinnumeros"));
        assertTrue(ConfiguracionUsuarios.contrasenaValida("Enfermeria2026"));
    }

    @Test
    void bloqueoTrasTresIntentos() {
        ConfiguracionUsuarios.reiniciarCredencialesFabrica();
        assertTrue(ConfiguracionUsuarios.agregarUsuario("prueba", "Clave2026x", "¿Color?", "azul"));
        for (int i = 0; i < 3; i++) assertFalse(ConfiguracionUsuarios.validarCredenciales("prueba", "mala"));
        assertTrue(ConfiguracionUsuarios.minutosBloqueo("prueba") > 0);
        assertFalse(ConfiguracionUsuarios.validarCredenciales("prueba", "Clave2026x"), "bloqueada aunque la clave sea correcta");
    }

    @Test
    void cambioDeContrasenaInicial() {
        ConfiguracionUsuarios.reiniciarCredencialesFabrica();
        assertTrue(ConfiguracionUsuarios.conservaCredencialesIniciales());
        assertFalse(ConfiguracionUsuarios.cambiarContrasena("root", "root", "root"));
        assertTrue(ConfiguracionUsuarios.cambiarContrasena("root", "root", "NuevaClave1"));
        assertFalse(ConfiguracionUsuarios.conservaCredencialesIniciales());
        assertTrue(ConfiguracionUsuarios.validarCredenciales("root", "NuevaClave1"));
    }
}
