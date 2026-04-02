@echo off
setlocal
cd /d "%~dp0"

echo ==============================================
echo PhantomBeats - Build + Install USB (Release)
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

echo [INFO] Compilando e instalando release (backend Render)...
call gradlew.bat --stop >nul 2>nul
call gradlew.bat --no-daemon --console=plain :app:installRelease
if errorlevel 1 (
  echo.
  echo [WARN] Fallo inicial de Gradle. Intentando autoreparacion de cache Kotlin/KAPT...
  call gradlew.bat --stop >nul 2>nul

  if exist ".gradle" (
    echo [INFO] Limpiando cache local: .gradle
    rmdir /s /q ".gradle"
  )
  if exist "app\build" (
    echo [INFO] Limpiando build local: app\build
    rmdir /s /q "app\build"
  )
  if exist "%USERPROFILE%\.gradle\kotlin" (
    echo [INFO] Limpiando cache global Kotlin: %USERPROFILE%\.gradle\kotlin
    rmdir /s /q "%USERPROFILE%\.gradle\kotlin"
  )
  if exist "%USERPROFILE%\.gradle\daemon" (
    echo [INFO] Limpiando daemons viejos: %USERPROFILE%\.gradle\daemon
    rmdir /s /q "%USERPROFILE%\.gradle\daemon"
  )

  echo [INFO] Reintentando installRelease sin incremental...
  call gradlew.bat --no-daemon --console=plain --rerun-tasks -Dkotlin.incremental=false -Pkotlin.incremental=false -Pkapt.incremental.apt=false :app:installRelease
  if errorlevel 1 (
    echo.
    echo [ERROR] Fallo la instalacion release con Gradle incluso tras autoreparacion.
    echo Sugerencia: cerrar Android Studio y volver a ejecutar este script.
    pause
    exit /b 1
  )
)

echo.
echo [INFO] Paquetes instalados de PhantomBeats:
"%ADB%" shell pm list packages | findstr /i "com.phantombeats"
echo.
echo [OK] APK release instalada correctamente.
echo Esta es la app final para uso diario (Render).
pause
exit /b 0
