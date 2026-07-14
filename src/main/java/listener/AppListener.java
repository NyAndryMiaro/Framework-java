package listener;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
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

            if (packageToScan != null && !packageToScan.isEmpty()) {
                AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
                applicationContext.scan(packageToScan);
                applicationContext.refresh();
                context.setAttribute("applicationContext", applicationContext);
            }

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'initialisation du framework", e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        Object appContext = context.getAttribute("applicationContext");
        if (appContext instanceof AnnotationConfigApplicationContext) {
            ((AnnotationConfigApplicationContext) appContext).close();
        }
    }
}