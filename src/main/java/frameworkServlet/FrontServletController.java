package frameworkServlet;

import utils.Utils.MethodMapping;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Map;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import frameworkAnnotation.WebApi;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;

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
    forwardToStatic(req, res, url);
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

private void forwardToStatic(HttpServletRequest req, HttpServletResponse res, String path) throws ServletException, IOException {
    final String targetPath = path;
    HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(req) {
        @Override
        public String getRequestURI() {
            return req.getContextPath() + targetPath;
        }

        @Override
        public String getServletPath() {
            return targetPath;
        }

        @Override
        public String getPathInfo() {
            return null;
        }
    };
    req.getServletContext().getNamedDispatcher("default").forward(wrappedRequest, res);
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
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[paramTypes.length];
        Map<String, Object> receivedParams = new LinkedHashMap<>();

        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i].equals(HttpServletRequest.class)) {
                args[i] = req;
            } else if (paramTypes[i].equals(HttpServletResponse.class)) {
                args[i] = res;
            } else if (paramTypes[i].equals(ApplicationContext.class)) {
                args[i] = applicationContext;
            } else {
                String paramName = parameters[i].getName();
                String rawValue = req.getParameter(paramName);
                Object convertedValue = convertValue(rawValue, paramTypes[i]);
                args[i] = convertedValue;
                receivedParams.put(paramName, convertedValue);
            }
        }

        method.setAccessible(true);
        Object result = method.invoke(controllerInstance, args);

        if (!receivedParams.isEmpty()) {
            printReceivedParams(receivedParams);
        }

        handleResult(result, method, req, res);

    } catch (Exception e) {
        throw new ServletException("Erreur lors de l'invocation de la methode " + mapping.method.getName(), e);
    }
}
private Object convertValue(String rawValue, Class<?> targetType) {
    if (rawValue == null) {
        return null;
    }
    try {
        if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
            return Integer.parseInt(rawValue);
        }
        if (targetType.equals(long.class) || targetType.equals(Long.class)) {
            return Long.parseLong(rawValue);
        }
        if (targetType.equals(double.class) || targetType.equals(Double.class)) {
            return Double.parseDouble(rawValue);
        }
        if (targetType.equals(float.class) || targetType.equals(Float.class)) {
            return Float.parseFloat(rawValue);
        }
        if (targetType.equals(boolean.class) || targetType.equals(Boolean.class)) {
            return Boolean.parseBoolean(rawValue);
        }
        if (targetType.equals(String.class)) {
            return rawValue;
        }
    } catch (NumberFormatException e) {
        return null;
    }
    return rawValue;
}
private void printReceivedParams(Map<String, Object> receivedParams) {
    System.out.println("[Framework] Parametres recus :");
    for (Map.Entry<String, Object> entry : receivedParams.entrySet()) {
        System.out.println("  - " + entry.getKey() + " = " + entry.getValue());
    }
}

private void handleResult(Object result, Method method, HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
    if (result == null) {
        return;
    }

    if (method.isAnnotationPresent(WebApi.class)) {
        res.setContentType("application/json;charset=UTF-8");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(result);
        res.getWriter().print(json);
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
    if (viewExists(req, view)) {
        forwardToStatic(req, res, view);
        return;
    }
    res.getWriter().println(view);
    return;
}

String resolvedView = prefix + view + suffix;
if (viewExists(req, resolvedView)) {
    forwardToStatic(req, res, resolvedView);
    return;
}

        res.getWriter().println(view);
        return;
    }

    res.getWriter().println(result.toString());
}

private boolean viewExists(HttpServletRequest req, String viewPath) {
    String realPath = req.getServletContext().getRealPath(viewPath);
    if (realPath == null) {
        return false;
    }
    File file = new File(realPath);
    return file.isFile();
}
}