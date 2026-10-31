@echo off

if exist out rmdir /S /Q out
mkdir out

dir /s /b src\main\java\*.java > sources.txt

javac -parameters -cp "lib/*" -d out @sources.txt

if errorlevel 1 (
    del sources.txt
    echo Erreur de compilation.
    pause
    exit /b 1
)

del sources.txt

jar cvf MiaroFramework.jar -C out .

echo.
echo JAR cree avec succes.
pause