@echo off

if not exist out mkdir out

dir /s /b src\main\java\*.java > sources.txt

javac -cp "lib/*" -d out @sources.txt

if errorlevel 1 (
    del sources.txt
    echo Erreur de compilation.
    pause
    exit /b
)

del sources.txt

cd out
jar cvf ..\MiaroFramework.jar .
cd ..

echo.
echo JAR cree avec succes.
pause