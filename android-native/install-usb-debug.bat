@echo off
setlocal
cd /d "%~dp0"

echo ==============================================
echo PhantomBeats - Build + Install USB (Debug)
echo ==============================================
echo.

set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not exist "%ADB%" set "ADB=C:\Android\Sdk\platform-tools\adb.exe"

if not exist "%ADB%" (
  echo [ERROR] No se encontro adb.exe.
  echo Instala Android Platform Tools o Android Studio SDK.
  echo Ruta esperada: C:\Android\Sdk\platform-tools\adb.exe
  pause
  exit /b 1
)

echo [INFO] Usando ADB en: %ADB%
"%ADB%" start-server >nul 2>nul
echo.
echo [INFO] Dispositivos conectados:
"%ADB%" devices
echo.

set "DEVICE_STATE="
for /f "usebackq delims=" %%s in (`"%ADB%" get-state 2^>nul`) do set "DEVICE_STATE=%%s"
if /I not "%DEVICE_STATE%"=="device" (
  echo [ERROR] No hay dispositivo autorizado.
  echo Revisa en el telefono el popup: "Permitir depuracion USB".
  echo Luego vuelve a ejecutar este script.
  pause
  exit /b 1
)

echo [INFO] Configurando tunel USB hacia backend local (adb reverse tcp:3000)...
"%ADB%" reverse tcp:3000 tcp:3000 >nul 2>nul

echo [INFO] Compilando e instalando debug...
call gradlew.bat :app:installDebug
if errorlevel 1 (
  echo.
  echo [ERROR] Fallo la instalacion con Gradle.
  pause
  exit /b 1
)

echo.
echo [OK] APK debug instalada correctamente en el telefono.
echo Puedes abrir la app desde el launcher.
pause
exit /b 0
