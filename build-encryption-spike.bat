@echo off
echo ==========================================
echo  TAUT Project — Build Encryption Spike
echo ==========================================
echo.

set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
echo JAVA_HOME: %JAVA_HOME%
echo.

cd /d "%~dp0"
call gradlew.bat build --no-daemon

echo.
if %ERRORLEVEL% equ 0 (
    echo [OK] Encryption spike build BERHASIL!
) else (
    echo [FAIL] Build gagal. Cek output di atas.
)
pause
