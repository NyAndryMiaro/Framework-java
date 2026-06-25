package frameworkServlet;

import frameworkAnnotation.*;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontServletController extends HttpServlet {

    private List<Class<?>> annotatedClasses = new ArrayList<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        
        try {
            String packageToScan = this.getInitParameter("package-to-scan");
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            
            String packagePath = (packageToScan != null) ? packageToScan.replace('.', '/') : "";
            java.net.URL resource = classLoader.getResource(packagePath);
            
            if (resource != null) {
                File rootDir = new File(resource.getFile());
                File globalRootDir = (packageToScan != null && !packageToScan.isEmpty()) ? rootDir.getParentFile() : rootDir;
                
                annotatedClasses.clear();
                scanDirectory(rootDir, globalRootDir);
            }
        } catch (Exception e) {
            throw new ServletException("Erreur lors du scan des annotations", e);
        }
    }

    private void scanDirectory(File currentFile, File rootDir) {
        if (!currentFile.exists()) return;

        if (currentFile.isDirectory()) {
            File[] files = currentFile.listFiles();
            if (files != null) {
                for (File file : files) {
                    scanDirectory(file, rootDir);
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

    private String getClassNameWithPackage(File classFile, File rootDir) {
        String rootPath = rootDir.getAbsolutePath();
        String classPath = classFile.getAbsolutePath();
        String relativePath = classPath.substring(rootPath.length() + 1);
        
        return relativePath.replace(".class", "").replace(File.separatorChar, '.');
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }
    
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/html;charset=UTF-8");
        String path = req.getContextPath();
        String[] urlParties = req.getRequestURL().toString().split(path);
        String url =urlParties[1];
        PrintWriter out = res.getWriter();

        //Succes
        for (Class<?> clazz : annotatedClasses) {
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                Annotation[] annotations = method.getDeclaredAnnotations();
                if(method.isAnnotationPresent(UrlMapping.class)){
                    for (Annotation annotation : annotations ) {
                        if (annotation instanceof UrlMapping) {
                            UrlMapping urlMapping = (UrlMapping) annotation;
                            if(urlMapping.url().equals(url)){
                                out.println(url + ": associe a "+ clazz.getName()+ " par la methode "+ method.getName()+"()");
                                return;
                            }
                        }
                    }
                }
            }
        }

        //Echec
        out.println(url+": url non associe");
        out.println("Les url associes sont : ");
        out.println("<ul>");
        for(Class<?> clazz : annotatedClasses){
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                Annotation[] annotations = method.getDeclaredAnnotations();
                if(method.isAnnotationPresent(UrlMapping.class)){
                    for( Annotation annotation : annotations) {
                        if(annotation instanceof UrlMapping){
                            UrlMapping urlMapping = (UrlMapping) annotation;
                            out.println("<li>");
                            out.println("url : " + urlMapping.url() + " class :" + clazz.getName() + " method : "+ method.getName()+"()");
                            out.println("</li>");
                        }
                    }
                }
            }
        }
        out.println("</ul>");
    }
}