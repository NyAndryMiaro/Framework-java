package utils;

import frameworkAnnotation.Controller;
import frameworkAnnotation.UrlMapping;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

public static class MethodMapping {
    public final Class<?> clazz;
    public final Method method;
    public final String httpMethod;

    public MethodMapping(Class<?> clazz, Method method, String httpMethod) {
        this.clazz = clazz;
        this.method = method;
        this.httpMethod = httpMethod;
    }
}

    public static String getClassNameWithPackage(File classFile, File rootDir) {
        String rootPath = rootDir.getAbsolutePath();
        String classPath = classFile.getAbsolutePath();
        String relativePath = classPath.substring(rootPath.length() + 1);
        return relativePath.replace(".class", "").replace(File.separatorChar, '.');
    }

    public static void scanDirectory(File currentFile, File rootDir, List<Class<?>> annotatedClasses) {
        if (!currentFile.exists()) return;

        if (currentFile.isDirectory()) {
            File[] files = currentFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    scanDirectory(file, rootDir, annotatedClasses);
                }
            }
        } else if (currentFile.isFile() && currentFile.getName().endsWith(".class")) {
            String className = getClassNameWithPackage(currentFile, rootDir);
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isAnnotationPresent(Controller.class)) {
                    annotatedClasses.add(clazz);
                }
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                System.out.println("[Framework] Impossible de charger la classe: " + className);
            }
        }
    }

public static Map<String, MethodMapping> buildUrlMap(List<Class<?>> annotatedClasses) {
    Map<String, MethodMapping> urlMap = new HashMap<>();
    for (Class<?> clazz : annotatedClasses) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(UrlMapping.class)) {
                UrlMapping annotation = method.getAnnotation(UrlMapping.class);
                String url = annotation.url();
                String httpMethod = annotation.method().toUpperCase();
                String uniqueKey = httpMethod + ":" + url;

                if (urlMap.containsKey(uniqueKey)) {
                    throw new RuntimeException("Mapping deja existant pour : " + uniqueKey);
                }

                urlMap.put(uniqueKey, new MethodMapping(clazz, method, httpMethod));
            }
        }
    }
    return urlMap;
}
}