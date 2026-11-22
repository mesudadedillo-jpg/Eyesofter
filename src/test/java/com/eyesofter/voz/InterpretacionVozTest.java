package com.eyesofter.voz;

import org.junit.jupiter.api.Test;

import static com.eyesofter.voz.ReconocedorOptotipos.ModoPrueba.DIRECCIONES;
import static com.eyesofter.voz.ReconocedorOptotipos.ModoPrueba.ISHIHARA;
import static com.eyesofter.voz.ReconocedorOptotipos.ModoPrueba.SNELLEN;
import static com.eyesofter.voz.ReconocedorOptotipos.interpretarSinModelo;
import static org.junit.jupiter.api.Assertions.*;

class InterpretacionVozTest {

    @Test
    void letrasConVariantesMexicanas() {
        assertEquals("C", interpretarSinModelo(SNELLEN, "se"));
        assertEquals("C", interpretarSinModelo(SNELLEN, "ce"));
        assertEquals("Z", interpretarSinModelo(SNELLEN, "seta"));
        assertEquals("F", interpretarSinModelo(SNELLEN, "la letra efe"));
        assertEquals("E", interpretarSinModelo(SNELLEN, "eh"));
    }

    @Test
    void variasLetrasSeguidas() {
        assertEquals("C,D,O", interpretarSinModelo(SNELLEN, "ce de o"));
    }

    @Test
    void unDesconocidoNoAnulaLaRespuesta() {
        assertEquals("T", interpretarSinModelo(SNELLEN, "[unk] te"));
    }

    @Test
    void noSeNoSeConfundeConLetras() {
        assertEquals(ReconocedorOptotipos.RESPUESTA_NO_SE, interpretarSinModelo(SNELLEN, "no se"));
        assertEquals(ReconocedorOptotipos.RESPUESTA_NO_SE, interpretarSinModelo(SNELLEN, "no lo sé"));
        assertEquals(ReconocedorOptotipos.RUIDO, interpretarSinModelo(SNELLEN, "no"));
    }

    @Test
    void numerosIshihara() {
        assertEquals("42", interpretarSinModelo(ISHIHARA, "cuarenta y dos"));
        assertEquals("74", interpretarSinModelo(ISHIHARA, "el numero setenta y cuatro"));
        assertEquals("12", interpretarSinModelo(ISHIHARA, "doce"));
        assertEquals(ReconocedorOptotipos.RESPUESTA_NADA, interpretarSinModelo(ISHIHARA, "no veo nada"));
        assertEquals(ReconocedorOptotipos.RESPUESTA_NADA, interpretarSinModelo(ISHIHARA, "no se"));
    }

    @Test
    void nivelRmsEnEscalaLogaritmica() {
        assertEquals(0, ReconocedorOptotipos.nivelRms(new byte[3200], 3200));
        byte[] fuerte = new byte[3200];
        for (int i = 0; i < fuerte.length; i += 2) { fuerte[i] = (byte) 0xFF; fuerte[i + 1] = (byte) 0x3F; }
        assertTrue(ReconocedorOptotipos.nivelRms(fuerte, fuerte.length) > 85);
    }

    @Test
    void direccionesDeLaE() {
        assertEquals("ARRIBA", interpretarSinModelo(DIRECCIONES, "arriba"));
        assertEquals("ABAJO", interpretarSinModelo(DIRECCIONES, "para abajo"));
        assertEquals("IZQUIERDA", interpretarSinModelo(DIRECCIONES, "a la izquierda"));
        assertEquals("DERECHA", interpretarSinModelo(DIRECCIONES, "hacia la derecha"));
        assertEquals("DERECHA", interpretarSinModelo(DIRECCIONES, "pa la derecha"));
        assertEquals(ReconocedorOptotipos.RESPUESTA_NO_SE, interpretarSinModelo(DIRECCIONES, "no se"));
        assertEquals(ReconocedorOptotipos.RUIDO, interpretarSinModelo(DIRECCIONES, "mesa"));
    }
}
