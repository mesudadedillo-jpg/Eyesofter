# Ejecutar EyeSofter

## Primera vez en un equipo (o después de clonar de GitHub)

1. Doble clic en **`INSTALAR_REQUISITOS.bat`**. Pide permiso de administrador y deja todo listo:
   - **Java 21** (Microsoft OpenJDK) si no hay un JDK 17+ con `jpackage`.
   - **Apache Maven** dentro de la carpeta `herramientas\` del proyecto (no toca el sistema).
   - **Modelo de voz Vosk** en español → `modelo_vosk\vosk-model-small-es-0.42`.
   - **Piper** (voz guía neuronal, opcional) → `modelo_tts\`. Si no se puede descargar, se usa la voz de Windows.
   - Descarga las librerías del `pom.xml`, compila y genera `release\EYESOFTER\EYESOFTER.exe`.
2. Doble clic en **`INICIAR_EYESOFTER.bat`**.

`INICIAR_EYESOFTER.bat` también detecta si falta algo y ofrece instalarlo. Si el código cambió, vuelve a
compilar solo; si la compilación falla, muestra las últimas líneas del error y la ruta del registro
(`tmp\empaquetado.log`).

Para recompilar a mano desde PowerShell:

```powershell
.\EMPAQUETAR_EYESOFTER.ps1
```

La carpeta completa `release\EYESOFTER` contiene el ejecutable, Java y los modelos de voz y cámara; no se deben separar sus archivos. **No** lleva la base de datos clínica, la agenda, la configuración ni `.env`: cada instalación empieza vacía y, para enviar tamizajes al portal, se vincula desde *Configuración → Conexión con EYESOFTER Suite → Vincular este equipo* con una cuenta de Dirección o Enfermería.

Para usarla en otro equipo Windows sin compilar, copie la carpeta completa `release\EYESOFTER` y ejecute `EYESOFTER.exe`. No requiere instalar Java ni Maven.

El acceso local inicial es `root` / `root` y se pide cambiar la contraseña en el primer ingreso. Esa cuenta es solo de este equipo: no da acceso a los datos del portal.

## Modo desarrollo

Con los requisitos instalados, desde la carpeta del proyecto:

```powershell
herramientas\apache-maven-3.9.9\bin\mvn.cmd javafx:run
```

(o solo `mvn javafx:run` si Maven está en el `Path`). En este modo los modelos se leen de `modelo_vosk\`, `modelo_tts\` y `modelo_opencv\` en la raíz del proyecto.
