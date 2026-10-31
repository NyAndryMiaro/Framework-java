#!/bin/bash

rm -rf out
mkdir -p out

javac -parameters -cp "lib/*" -d out src/main/java/*/*.java || exit 1
echo "Compilation terminee"

jar cvf MiaroFramework.jar -C out .
echo "MiaroFramework.jar cree"
