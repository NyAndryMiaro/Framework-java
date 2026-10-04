@echo off

if not exist out mkdir out

dir /s /b src\main\java\*.java > sources.txt

javac -parameters -cp "lib/*" -d out @sources.txt

if errorlevel 1 (
    del sources.txt
    echo Erreur de compilation.
    pause
    exit /b
)

del sources.txt

cd out

for %%J in (..\lib\*.jar) do (
    jar xf "%%J"
)

if exist META-INF\*.SF del /q META-INF\*.SF
if exist META-INF\*.DSA del /q META-INF\*.DSA
if exist META-INF\*.RSA del /q META-INF\*.RSA
if exist module-info.class del /q module-info.class

jar cvf ..\MiaroFramework.jar .
cd ..

echo.
echo JAR cree avec succes.
pause