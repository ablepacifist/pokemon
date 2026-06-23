@echo off
rem Delegates to alchemyServer's Gradle wrapper (shares the same jar)
set "POKEMON_DIR=%~dp0"
call "%POKEMON_DIR%..\..\..\alchemyServer\gradlew.bat" %*
