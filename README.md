# Eyesofter
Desktop aplication

## Cómo ejecutarlo

1. `INSTALAR_REQUISITOS.bat` (se abre como administrador: instala Java 21, Maven y los modelos de voz, y compila).
2. `INICIAR_EYESOFTER.bat`.

Detalles en [EJECUTAR_EYESOFTER.md](EJECUTAR_EYESOFTER.md).

## Conexión con EYESOFTER Suite (C-RFN-02, C-RFNN-03)

Cada tamizaje terminado se envía al servidor de la Suite para que Enfermería le dé seguimiento en el
portal y el tutor reciba el aviso en la app.

Instalar el escritorio y entrar con la cuenta local (`root`) **no da acceso a ningún dato de la escuela**.
Cada computadora debe **vincularse** con la cuenta del portal de alguien de Dirección o Enfermería:

1. En el escritorio: **Configuración → Conexión con EYESOFTER Suite → Vincular este equipo**.
2. Se abre el navegador con un código (p. ej. `K7QM-3RTA`) que también aparece en el escritorio.
   Inicia sesión con tu **cuenta del portal** (Dirección o Enfermería), confirma que el código coincide y autoriza.
3. El escritorio queda vinculado y envía lo pendiente. Al registrar a un alumno captura su **matrícula del portal**
   o búscala con *Buscar matrícula en el portal* (mínimo 3 letras; ya no se puede descargar la lista completa).

Para retirar el acceso a un equipo: pulsa **Desvincular** en ese equipo, o cambia la contraseña / desactiva en el
portal la cuenta que lo autorizó (eso lo desvincula al instante desde el servidor).

Si no hay internet, los envíos quedan en una cola local y se reintentan solos (30 s, 1 min, 2 min… hasta
30 min). Si la falla dura más de 30 minutos, Enfermería recibe una alerta. La tabla *Envíos al portal*
muestra lo enviado, lo pendiente y lo que necesita corrección (por ejemplo, una matrícula que no existe).

## Seguridad de las cuentas locales

Las contraseñas se guardan con hash PBKDF2 (nunca en texto plano), 3 intentos fallidos bloquean la cuenta
15 minutos, y la contraseña inicial `root` debe cambiarse en el primer acceso.

## Exactitud clínica: equipo, distancia y edad

El resultado depende del equipo y de la persona; la aplicación se ajusta a ambos y
guarda en cada expediente (base local y PDF) las condiciones en que se midió.

**Pantalla**
- El tamaño real se mide una vez por equipo con una tarjeta bancaria o credencial
  (Configuración → *Medir con tarjeta*). La medición queda ligada a esa pantalla: si
  la prueba se muestra en otra, se avisa.
- Una fila cuyo optotipo mediría menos de 5 píxeles físicos no se muestra (ya no
  tendría su forma). Si la persona lee la fila más pequeña posible, el resultado se
  marca "en el límite de la pantalla"; si la pantalla no alcanza la línea de
  referencia para la edad, el tamizaje sale **no concluyente** y la pantalla de
  configuración indica a qué distancia hay que alejarse.
- Si la fila 20/200 no cabe en la ventana (distancias largas en laptops), la prueba
  empieza en la primera fila que cabe y da por leídas las de arriba (criterio ETDRS).
- La altura de las letras se mide sobre la tipografía que realmente se dibuja (si
  Arial no está instalada, la sustituta tiene otra altura).

**Distancia (cámara)**
- Se ubican los ojos con YuNet (`modelo_opencv/face_detection_yunet_2023mar.onnx`,
  licencia MIT, OpenCV 4.9; corre en cualquier CPU) y se usa la distancia entre
  pupilas (DP) del paciente: medida con cinta en la sesión, capturada por la clínica
  o estimada por la edad. Antes se suponía la misma DP para todos y a los niños se
  les pedía acercarse de más (su agudeza salía mejor de lo real).
- Calibración: con la persona a una distancia medida con cinta, *Calibrar a esta
  distancia*. Sin cinta: 4 hojas tamaño carta a lo largo miden 1.12 m. Con la cámara
  ya calibrada, la misma acción mide la DP real del paciente.
- La tolerancia de distancia es la configurada, pero nunca más del 10 % de la
  distancia objetivo.

**Edad**
- Se toma de la fecha de nacimiento o, si falta, de la CURP.
- Menores de 6 años: se sugiere la **E direccional** (4 por fila, se pasa con 3/4,
  puntuación por fila completa para controlar el azar). Se responde señalando
  (flechas del teclado en modo asistido) o diciendo arriba/abajo/izquierda/derecha.
- Línea aprobatoria por edad (AAPOS/AAP 2016, ajustable en `ResultadoClinico`):
  3 años 20/50, 4 años 20/40, 5–17 años 20/32; adultos, 20/40. Menores de 3 años:
  aviso de que los optotipos no son confiables (fotocribado o valoración pediátrica).

**Otras condiciones que se registran**: si usa lentes y si los traía, brillo al
máximo sin reflejos y sin "Luz nocturna", optotipo usado y método de captura (voz
o manual; en menores de 8 años se recomienda el modo asistido).

**Contraste y Amsler**: el contraste se puntúa con el gris que realmente se dibuja
(con 8 bits los niveles más finos se repetían) y siempre con letras de 2.8°; la
rejilla de Amsler indica la distancia exacta (29 cm, o menos si la pantalla obliga a
reducirla).
## Datos clínicos: nunca en el repositorio ni en el instalador

`eyesofter_local.db`, `citas_agenda.json`, `eyesofter_export.sql`, `eyesofter_config.properties` y `.env` están en
`.gitignore` y `EMPAQUETAR_EYESOFTER.ps1` ya **no** los copia a `release\EYESOFTER`. Cada instalación empieza vacía.
