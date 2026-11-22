# Seguridad — EYESOFTER (sistema de escritorio)

| Área | Medida |
|---|---|
| Cuentas locales | Contraseñas con hash PBKDF2; bloqueo de 15 min tras 3 intentos; la contraseña inicial `root` se cambia en el primer acceso |
| Conexión al portal | Sin clave compartida: el equipo se **vincula** en el navegador con una cuenta de Dirección o Enfermería de su escuela (con verificación en dos pasos). Solo HTTPS |
| Datos | El instalador no lleva base de datos, configuración ni `.env`: cada instalación empieza vacía |

## Reportar una vulnerabilidad

Si encuentras una falla de seguridad, **no abras un issue público**. Usa
*Security → Report a vulnerability* de este repositorio (reporte privado de GitHub) o el formulario
*Reportar una falla* del portal. Responderemos lo antes posible.

## Datos y secretos

- **Todos los datos de este repositorio son ficticios** (nombres, CURP, teléfonos y correos de ejemplo).
  Ningún dato real de alumnos o familias se guarda en el código.
- **No hay secretos en el repositorio.** Llaves de cifrado, secretos de sesión, contraseñas y conexiones
  a la base viven solo en las variables de entorno de Railway.
- Las cuentas demo (contraseña `Eyesofter123!`) existen solo en desarrollo. En producción el portal
  desactiva al arrancar cualquier cuenta demo que conserve esa contraseña.

