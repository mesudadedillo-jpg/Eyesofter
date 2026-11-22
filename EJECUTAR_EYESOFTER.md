# Ejecutar EyeSofter

## Aplicación lista

Abra `INICIAR_EYESOFTER.bat`. Este iniciador abre `release\EYESOFTER\EYESOFTER.exe`, la distribución vigente de la aplicación, sin Maven ni Java instalados en el equipo final.

Para crear o actualizar esa distribución desde el código, ejecute desde PowerShell:

```powershell
.\EMPAQUETAR_EYESOFTER.ps1
```

La carpeta completa `release\EYESOFTER` contiene el ejecutable, Java y los modelos de voz y cámara; no se deben separar sus archivos. **No** lleva la base de datos clínica, la agenda, la configuración ni `.env`: cada instalación empieza vacía y, para enviar tamizajes al portal, se vincula desde *Configuración → Conexión con EYESOFTER Suite → Vincular este equipo* con una cuenta de Dirección o Enfermería.

Para usarla en otro equipo Windows, copie la carpeta completa `dist\EYESOFTER` y ejecute `EYESOFTER.exe`. No requiere instalar Java ni Maven.

El acceso local inicial es `root` / `root` y se pide cambiar la contraseña en el primer ingreso. Esa cuenta es solo de este equipo: no da acceso a los datos del portal.

## Maven para desarrollo

Maven solo hace falta si se modifica el código fuente. Descargue el archivo binario ZIP desde `https://maven.apache.org/download.cgi`, extráigalo en una carpeta permanente, por ejemplo `C:\Herramientas\apache-maven`, y agregue `C:\Herramientas\apache-maven\bin` a la variable de entorno `Path` de Windows.

Abra una terminal nueva y compruebe la instalación con:

```powershell
mvn -version
```

Desde la carpeta del proyecto, ejecute el modo de desarrollo con:

```powershell
mvn javafx:run
```
