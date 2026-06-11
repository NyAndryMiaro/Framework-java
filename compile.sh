javac -cp lib/servlet-api.jar -d out src/main/java/*/*.java
cd out/
jar cvf ../MiaroFramework.jar  */*.class
cd ../