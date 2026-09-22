@echo off
echo =======================================================
echo   RoomieVault v1.1 - Automatic Phone Installer via ADB
echo =======================================================
echo.
set ADB="C:\Users\ayush\AppData\Local\Android\Sdk\platform-tools\adb.exe"
set APK="d:\HouseHoldManagement\RoomieVault-v1.1.apk"

echo Checking for connected Android devices...
%ADB% devices
echo.

echo Waiting for device to be connected with USB Debugging enabled...
%ADB% wait-for-device
echo.
echo Device connected! Installing RoomieVault-v1.1.apk...
%ADB% install -r %APK%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo =======================================================
    echo   SUCCESS! RoomieVault v1.1 is now installed on your phone!
    echo =======================================================
    echo Launching app on your device...
    %ADB% shell monkey -p com.example.householdmanagement -c android.intent.category.LAUNCHER 1
) else (
    echo.
    echo [ERROR] Installation failed. Please ensure:
    echo 1. 'Install via USB' is enabled in Developer Options.
    echo 2. Unlock your phone screen and tap 'Allow' on any permission prompts.
)

echo.
pause
