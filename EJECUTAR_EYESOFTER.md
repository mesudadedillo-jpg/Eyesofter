# Ejecutar EyeSofter

## Aplicación lista

Abra `INICIAR_EYESOFTER.bat`. Este iniciador abre `release\EYESOFTER\EYESOFTER.exe`, la distribución vigente de la aplicación, sin Maven ni Java instalados en el equipo final.

Para crear o actualizar esa distribución desde el código, ejecute desde PowerShell:

```powershell
.\EMPAQUETAR_EYESOFTER.ps1
```

La carpeta completa `release\EYESOFTER` contiene el ejecutable, Java, los modelos de voz y cámara, la base de datos y el archivo local `.env`; no se deben separar sus archivos.

Para usarla en otro equipo Windows, copie la carpeta completa `dist\EYESOFTER` y ejecute `EYESOFTER.exe`. No requiere instalar Java ni Maven.

Las credenciales locales se encuentran en `dist\EYESOFTER\app\.env`. El acceso inicial es `root` / `root`.

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
