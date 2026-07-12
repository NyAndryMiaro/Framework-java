package listener;

import frameworkAnnotation.Controller;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import utils.Utils;
import utils.Utils.MethodMapping;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebListener
public class AppListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        String packageToScan = context.getInitParameter("package-to-scan");

        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

            // Racine reelle du classpath (ex: WEB-INF/classes), peu importe
            // le nombre de segments dans packageToScan
            java.net.URL rootResource = classLoader.getResource("");
            if (rootResource == null) {
                throw new RuntimeException("Impossible de localiser la racine du classpath");
            }
            File globalRootDir = new File(rootResource.getFile());

            String packagePath = (packageToScan != null) ? packageToScan.replace('.', '/') : "";
            java.net.URL resource = classLoader.getResource(packagePath);
            File rootDir = (resource != null) ? new File(resource.getFile()) : globalRootDir;

            List<Class<?>> annotatedClasses = new ArrayList<>();
            Utils.scanDirectory(rootDir, globalRootDir, annotatedClasses);

            Map<String, MethodMapping> urlMap = Utils.buildUrlMap(annotatedClasses);
            context.setAttribute("urlMap", urlMap);

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du scan des controllers", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
    }
}