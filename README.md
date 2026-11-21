# Eyesofter
Desktop aplication

## Conexión con EYESOFTER Suite (C-RFN-02, C-RFNN-03)

Cada tamizaje terminado se envía al servidor de la Suite para que Enfermería le dé seguimiento en el
portal y el tutor reciba el aviso en la app.

1. En el servidor (Railway) define `ESCRITORIO_API_KEY` con una clave larga y aleatoria.
2. En el escritorio: **Configuración → Conexión con EYESOFTER Suite**, escribe la dirección `https://…`
   del servidor y esa misma clave. Usa **Probar conexión** y luego **Guardar conexión**.
   (También puede darse con la variable de entorno `EYESOFTER_API_KEY`.)
3. Al registrar a un alumno captura su **matrícula del portal** o búscala con *Buscar matrícula en el portal*.

Si no hay internet, los envíos quedan en una cola local y se reintentan solos (30 s, 1 min, 2 min… hasta
30 min). Si la falla dura más de 30 minutos, Enfermería recibe una alerta. La tabla *Envíos al portal*
muestra lo enviado, lo pendiente y lo que necesita corrección (por ejemplo, una matrícula que no existe).

## Seguridad de las cuentas locales

Las contraseñas se guardan con hash PBKDF2 (nunca en texto plano), 3 intentos fallidos bloquean la cuenta
15 minutos, y la contraseña inicial `root` debe cambiarse en el primer acceso.
