#!/bin/bash

# 1. Créer le dossier out s'il n'existe pas
mkdir -p out

# 2. Compilation
javac -cp "lib/*" -d out src/main/java/*/*.java

# 3. Création du JAR en incluant correctement l'arborescence des packages
cd out
jar cvf ../MiaroFramework.jar .
cd ..