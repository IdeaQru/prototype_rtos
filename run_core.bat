@echo off
chcp 65001 >nul
call "%~dp0gradlew.bat" runCore -q
