package frameworkServlet;

import frameworkAnnotation.UrlMapping;
import utils.Utils;
import utils.Utils.MethodMapping;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class FrontServletController extends HttpServlet {

    private Map<String, MethodMapping> urlMap;

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

                List<Class<?>> annotatedClasses = new ArrayList<>();
                Utils.scanDirectory(rootDir, globalRootDir, annotatedClasses);
                urlMap = Utils.buildUrlMap(annotatedClasses);
            }
        } catch (Exception e) {
            throw new ServletException("Erreur lors du scan des annotations", e);
        }
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
        String url = urlParties[1];
        PrintWriter out = res.getWriter();

        MethodMapping mapping = urlMap.get(url);

        if (mapping != null) {
            out.println(url + ": associe a " + mapping.clazz.getName() + " par la methode " + mapping.method.getName() + "()");
        } else {
            out.println(url + ": url non associe");
            out.println("Les url associes sont : ");
            out.println("<ul>");
            for (Map.Entry<String, MethodMapping> entry : urlMap.entrySet()) {
                out.println("<li>");
                out.println("url : " + entry.getKey() + " class : " + entry.getValue().clazz.getName() + " method : " + entry.getValue().method.getName() + "()");
                out.println("</li>");
            }
            out.println("</ul>");
        }
    }
}