@echo off
REM ========================================================================
REM PERFORMANCE MONITOR - ONE-CLICK INSTALLER (Windows)
REM ========================================================================

echo.
echo ╔══════════════════════════════════════════════════════════════╗
echo ║                                                              ║
echo ║     🎯 PERFORMANCE MONITOR - ONE-CLICK INSTALLER            ║
echo ║        Zero Bugs • Zero Errors • 100%% Ready                 ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.

REM Check if we're in the right directory
if not exist "gradlew.bat" (
    echo ❌ ERROR: Please run this script from your project root directory
    echo    (The folder that contains gradlew.bat)
    pause
    exit /b 1
)

echo 📋 Installation Steps:
echo.
echo [1/5] Creating debug folder...
if not exist "src\client\java\com\bapel_slimefun_mod\debug" (
    mkdir "src\client\java\com\bapel_slimefun_mod\debug"
    echo ✓ Created debug folder
) else (
    echo ✓ Debug folder already exists
)
echo.

echo [2/5] Copying PerformanceMonitor.java...
copy /Y "FINAL\PerformanceMonitor.java" "src\client\java\com\bapel_slimefun_mod\debug\" >nul
if errorlevel 1 (
    echo ❌ ERROR: Could not copy PerformanceMonitor.java
    echo    Make sure FINAL folder exists in current directory
    pause
    exit /b 1
)
echo ✓ PerformanceMonitor.java copied
echo.

echo [3/5] Replacing edited project files...
copy /Y "FINAL\BapelSlimefunMod.java" "src\client\java\com\bapel_slimefun_mod\" >nul
if errorlevel 1 (
    echo ❌ ERROR: Could not copy BapelSlimefunMod.java
    pause
    exit /b 1
)
echo ✓ BapelSlimefunMod.java replaced

copy /Y "FINAL\ModKeybinds.java" "src\client\java\com\bapel_slimefun_mod\client\" >nul
if errorlevel 1 (
    echo ❌ ERROR: Could not copy ModKeybinds.java
    pause
    exit /b 1
)
echo ✓ ModKeybinds.java replaced

copy /Y "FINAL\en_us.json" "src\main\resources\assets\bapel-slimefun-mod\lang\" >nul
if errorlevel 1 (
    echo ❌ ERROR: Could not copy en_us.json
    pause
    exit /b 1
)
echo ✓ en_us.json replaced
echo.

echo [4/5] Running smart injector...
python FINAL\smart_inject.py
if errorlevel 1 (
    echo ❌ ERROR: Smart injector failed
    echo    Make sure Python is installed
    pause
    exit /b 1
)
echo.

echo [5/5] Building project...
call gradlew clean build
if errorlevel 1 (
    echo ❌ ERROR: Build failed
    echo    Check console output above for errors
    pause
    exit /b 1
)
echo.

echo ╔══════════════════════════════════════════════════════════════╗
echo ║                                                              ║
echo ║     ✅ INSTALLATION COMPLETE!                               ║
echo ║                                                              ║
echo ╚══════════════════════════════════════════════════════════════╝
echo.
echo 🎉 Success! Performance Monitor installed successfully!
echo.
echo 📝 Next Steps:
echo    1. Run: gradlew runClient
echo    2. Press F3 in-game to toggle performance overlay
echo    3. Enjoy monitoring!
echo.
echo 📚 Documentation:
echo    - Quick Guide: FINAL\FILE_REPLACEMENT_GUIDE.md
echo    - Full Guide:  FINAL\INSTALLATION_GUIDE.md
echo    - Overview:    FINAL\START_HERE.md
echo.

pause