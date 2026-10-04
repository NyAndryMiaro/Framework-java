#!/bin/bash

# 1. Créer le dossier out s'il n'existe pas
mkdir -p out

# 2. Compilation
javac -cp "lib/*" -d out src/main/java/*/*.java

# 3. Extraction des dépendances dans out/ pour créer un jar "fat"
#    (chaque .jar de lib/ est décompressé dans out, en évitant d'écraser
#     les métadonnées de signature qui provoqueraient des erreurs au runtime)
cd out
for jar in ../lib/*.jar; do
    jar xf "$jar"
done

# Nettoyage des métadonnées de signature et modules qui posent problème
# une fois les jars fusionnés ensemble
rm -rf META-INF/*.SF META-INF/*.DSA META-INF/*.RSA module-info.class

# 4. Création du JAR final en incluant le code compilé + toutes les dépendances
jar cvf ../MiaroFramework.jar .
cd ..