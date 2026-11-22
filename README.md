# Eyesofter
Desktop aplication

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

## Datos clínicos: nunca en el repositorio ni en el instalador

`eyesofter_local.db`, `citas_agenda.json`, `eyesofter_export.sql`, `eyesofter_config.properties` y `.env` están en
`.gitignore` y `EMPAQUETAR_EYESOFTER.ps1` ya **no** los copia a `release\EYESOFTER`. Cada instalación empieza vacía.
