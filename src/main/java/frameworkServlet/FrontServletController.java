package frameworkServlet;

import utils.Utils.MethodMapping;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

public class FrontServletController extends HttpServlet {

    private Map<String, MethodMapping> urlMap;
    private String prefix;
    private String suffix;
    private ApplicationContext applicationContext;

    @Override
    @SuppressWarnings("unchecked")
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        urlMap = (Map<String, MethodMapping>) config.getServletContext().getAttribute("urlMap");
        if (urlMap == null) {
            throw new ServletException("urlMap non initialise : verifie AppListener et package-to-scan");
        }

        String ctxPrefix = config.getServletContext().getInitParameter("prefix");
        String ctxSuffix = config.getServletContext().getInitParameter("suffix");

        this.prefix = (ctxPrefix != null) ? ctxPrefix : "";
        this.suffix = (ctxSuffix != null) ? ctxSuffix : "";

        Object rawContext = config.getServletContext().getAttribute("applicationContext");
        this.applicationContext = (rawContext instanceof ApplicationContext) ? (ApplicationContext) rawContext : null;
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }

    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        processRequest(req, res);
    }

    protected void processRequest(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String path = req.getContextPath();
        String url = req.getRequestURI().substring(path.length());

        if (isStaticResource(req, url)) {
            req.getRequestDispatcher(url).forward(req, res);
            return;
        }

        res.setContentType("text/html;charset=UTF-8");
        String currentMethod = req.getMethod().toUpperCase();
        String lookupKey = currentMethod + ":" + url;

        MethodMapping mapping = urlMap.get(lookupKey);

        if (mapping != null) {
            invokeMapping(mapping, req, res);
        } else {
            renderNotFound(url, currentMethod, res);
        }
    }

    private boolean isStaticResource(HttpServletRequest req, String url) throws IOException {
        String realPath = req.getServletContext().getRealPath(url);
        if (realPath == null) {
            return false;
        }
        File file = new File(realPath);
        return file.isFile();
    }

    private void renderNotFound(String url, String currentMethod, HttpServletResponse res) throws IOException {
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

    private Object resolveControllerInstance(Class<?> clazz) throws Exception {
        if (applicationContext != null) {
            try {
                return applicationContext.getBean(clazz);
            } catch (NoSuchBeanDefinitionException e) {
                return clazz.getDeclaredConstructor().newInstance();
            }
        }
        return clazz.getDeclaredConstructor().newInstance();
    }

    private void invokeMapping(MethodMapping mapping, HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        try {
            Object controllerInstance = resolveControllerInstance(mapping.clazz);
            Method method = mapping.method;

            Class<?>[] paramTypes = method.getParameterTypes();
            Object[] args = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                if (paramTypes[i].equals(HttpServletRequest.class)) {
                    args[i] = req;
                } else if (paramTypes[i].equals(HttpServletResponse.class)) {
                    args[i] = res;
                } else if (paramTypes[i].equals(ApplicationContext.class)) {
                    args[i] = applicationContext;
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

            if (view.startsWith("redirect:")) {
                String target = view.substring("redirect:".length());
                res.sendRedirect(req.getContextPath() + target);
                return;
            }

            if (view.startsWith("/")) {
                if (view.endsWith(".jsp")) {
                    req.getRequestDispatcher(view).forward(req, res);
                    return;
                }
                res.getWriter().println(view);
                return;
            }

            String resolvedView = prefix + view + suffix;
            req.getRequestDispatcher(resolvedView).forward(req, res);
            return;
        }

        res.getWriter().println(result.toString());
    }
}