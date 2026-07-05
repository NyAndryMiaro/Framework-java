package frameworkServlet;

import utils.Utils;
import utils.Utils.MethodMapping;

import java.io.*;
import java.lang.reflect.Method;
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

        String currentMethod = req.getMethod().toUpperCase();
        String lookupKey = currentMethod + ":" + url;

        MethodMapping mapping = urlMap.get(lookupKey);

        if (mapping != null) {
            invokeMapping(mapping, req, res);
        } else {
            PrintWriter out = res.getWriter();
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.println(url + " (" + currentMethod + "): url non associe");
            out.println("<br/>Les url associes sont : ");
            out.println("<ul>");

            for (Map.Entry<String, MethodMapping> entry : urlMap.entrySet()) {
                out.println("<li>");
                out.println("Cle (Methode:URL) : <strong>" + entry.getKey() + "</strong> ➔ class : " + entry.getValue().clazz.getName() + " ➔ method : " + entry.getValue().method.getName() + "()");
                out.println("</li>");
            }
            out.println("</ul>");
        }
    }

    private void invokeMapping(MethodMapping mapping, HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            Object controllerInstance = mapping.clazz.getDeclaredConstructor().newInstance();
            Method method = mapping.method;

            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i].equals(HttpServletRequest.class)) {
                    args[i] = req;
                } else if (paramTypes[i].equals(HttpServletResponse.class)) {
                    args[i] = res;
                } else {
                    args[i] = null;
                }
            }

            method.setAccessible(true);
            Object result = method.invoke(controllerInstance, args);
            handleResult(result, req, res);

        } catch (Exception e) {
            throw new ServletException("Erreur lors de l'invocation de la methode " + mapping.method.getName(), e);
        }
    }

    private void handleResult(Object result, HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if (result == null) {
            return;
        }

        if (result instanceof String) {
            String view = (String) result;
            if (view.endsWith(".jsp")) {
                req.getRequestDispatcher(view).forward(req, res);
                return;
            }
            res.getWriter().println(view);
            return;
        }

        res.getWriter().println(result.toString());
    }
}