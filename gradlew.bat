@echo off
setlocal enabledelayedexpansion

:: 1. Cek jika gradle ada di PATH
where gradle >nul 2>nul
if %ERRORLEVEL% equ 0 (
    gradle --console=plain %*
    exit /b %ERRORLEVEL%
)

:: 2. Jalankan langsung gradle.bat resmi di folder bin dari cache wrapper
for /f "delims=" %%G in ('dir /b /s "%USERPROFILE%\.gradle\wrapper\dists\gradle.bat" 2^>nul') do (
    echo "%%G" | findstr /i "\\bin\\gradle\.bat" >nul
    if !ERRORLEVEL! equ 0 (
        "%%G" --console=plain %*
        exit /b !ERRORLEVEL%
    )
)

echo [ERROR] gradle.bat resmi tidak ditemukan di folder bin.
exit /b 1
