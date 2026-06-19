package frameworkServlet;

import frameworkAnnotation.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrameworkServlet extends HttpServlet {

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
                
                if (clazz.isAnnotationPresent(FrameworkAnnotation.class)) {
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
        PrintWriter out = res.getWriter();
        
        out.println("<h3>Contrôleurs détectés (possédant @FrameworkAnnotation) :</h3>");
        out.println("<ul>");
        for (Class<?> clazz : annotatedClasses) {

            out.println("<li>" + clazz.getName() + "</li>");
        }
        out.println("</ul>");
    }
}