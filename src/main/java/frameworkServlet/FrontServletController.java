package frameworkServlet;

import utils.Utils.MethodMapping;

import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.Part;
import utils.FileMap;

@MultipartConfig
public class FrontServletController extends HttpServlet {

    private static final int MAX_DEPTH = 5;

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
            out.println("Cle (Methode:URL) : <strong>" + entry.getKey() + "</strong> ➔ class : "
                    + entry.getValue().clazz.getName() + " ➔ method : " + entry.getValue().method.getName() + "()");
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
                }
                else if (List.class.isAssignableFrom(paramTypes[i])
                && parameters[i].getParameterizedType() instanceof ParameterizedType
                && ((ParameterizedType) parameters[i].getParameterizedType()).getActualTypeArguments()[0].equals(FileMap.class)) {
                args[i] = bindFileList(parameters[i].getName(), req);
                }

                  else if (paramTypes[i].equals(FileMap.class)) {
                    args[i] = bindFile(parameters[i].getName(), req);
                }
                else if (isSimpleType(paramTypes[i])) {
                    String paramName = parameters[i].getName();
                    Object convertedValue = convertValue(req.getParameter(paramName), paramTypes[i]);
                    args[i] = convertedValue;
                    receivedParams.put(paramName, convertedValue);
                } else if (List.class.isAssignableFrom(paramTypes[i])) {
                    args[i] = bindList(parameters[i].getParameterizedType(), parameters[i].getName(), req, receivedParams, 0);
                } else {
                    args[i] = bindObject(paramTypes[i], "", req, receivedParams, 0);
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

    // ------------------------------------------------------------------
    // Binding : valeurs simples, objets (imbriques) et listes
    // ------------------------------------------------------------------

    /** Resout une valeur selon son type : simple, liste ou objet. */
    private Object resolveValue(Class<?> type, Type genericType, String name,
                                HttpServletRequest req, Map<String, Object> received, int depth) {
        if (isSimpleType(type)) {
            Object value = convertValue(req.getParameter(name), type);
            received.put(name, value);
            return value;
        }
        if (List.class.isAssignableFrom(type)) {
            return bindList(genericType, name, req, received, depth + 1);
        }
        return bindObject(type, name + ".", req, received, depth + 1);
    }

    /**
     * Cree un objet et le remplit.
     * prefix = "" pour l'objet racine, "adresse." pour un champ imbrique,
     * "etudiants[0]." pour un element de liste.
     */
    private Object bindObject(Class<?> type, String prefix, HttpServletRequest req,
                              Map<String, Object> received, int depth) {
        if (depth > MAX_DEPTH) {
            return null;
        }
        // Objet imbrique sans aucun champ dans le formulaire : on laisse null
        if (!prefix.isEmpty() && !hasParametersWithPrefix(req, prefix)) {
            return null;
        }

        try {
            Constructor<?> noArg = null;
            try {
                noArg = type.getDeclaredConstructor();
            } catch (NoSuchMethodException ignored) {
                // pas de constructeur vide : on utilisera le constructeur le plus complet
            }

            // Cas 1 : constructeur vide -> on remplit les champs
            if (noArg != null) {
                noArg.setAccessible(true);
                Object instance = noArg.newInstance();

                for (Field field : type.getDeclaredFields()) {
                    int mod = field.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) {
                        continue;
                    }
                    Object value = resolveValue(field.getType(), field.getGenericType(),
                            prefix + field.getName(), req, received, depth);
                    if (value == null) {
                        continue; // on garde la valeur par defaut du champ
                    }
                    field.setAccessible(true);
                    field.set(instance, value);
                }
                return instance;
            }

            // Cas 2 : pas de constructeur vide -> constructeur avec le plus de parametres
            Constructor<?> chosen = null;
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                if (chosen == null || c.getParameterCount() > chosen.getParameterCount()) {
                    chosen = c;
                }
            }
            if (chosen == null) {
                return null;
            }

            Parameter[] ctorParams = chosen.getParameters();
            Object[] ctorArgs = new Object[ctorParams.length];
            for (int i = 0; i < ctorParams.length; i++) {
                Object value = resolveValue(ctorParams[i].getType(), ctorParams[i].getParameterizedType(),
                        prefix + ctorParams[i].getName(), req, received, depth);
                if (value == null && ctorParams[i].getType().isPrimitive()) {
                    value = Array.get(Array.newInstance(ctorParams[i].getType(), 1), 0); // 0, false...
                }
                ctorArgs[i] = value;
            }
            chosen.setAccessible(true);
            return chosen.newInstance(ctorArgs);

        } catch (Exception e) {
            System.out.println("[Framework] Echec du binding de " + type.getName() + " : " + e);
            return null;
        }
    }

    /**
     * Remplit une liste.
     * Formats supportes : etudiants[0].nom, etudiants[1].nom ...  /  notes[0], notes[1] ...
     * ou, pour une liste de valeurs simples, plusieurs champs du meme nom (notes=1&notes=2).
     */
    private List<Object> bindList(Type genericType, String name, HttpServletRequest req,
                                  Map<String, Object> received, int depth) {
        List<Object> list = new ArrayList<>();
        if (depth > MAX_DEPTH) {
            return list;
        }

        // Type des elements via les generiques : List<Etudiant> -> Etudiant
        Class<?> elementType = String.class;
        if (genericType instanceof ParameterizedType) {
            Type arg = ((ParameterizedType) genericType).getActualTypeArguments()[0];
            if (arg instanceof Class) {
                elementType = (Class<?>) arg;
            }
        }

        // Liste de valeurs simples envoyees sous le meme nom
        if (isSimpleType(elementType)) {
            String[] values = req.getParameterValues(name);
            if (values != null) {
                for (String v : values) {
                    Object converted = convertValue(v, elementType);
                    if (converted != null) {
                        list.add(converted);
                    }
                }
                received.put(name, list);
                return list;
            }
        }

        // Liste indexee : name[0], name[1]...
        for (int i = 0; ; i++) {
            String elementName = name + "[" + i + "]";
            if (!hasParametersWithPrefix(req, elementName)) {
                break;
            }
            if (isSimpleType(elementType)) {
                Object converted = convertValue(req.getParameter(elementName), elementType);
                list.add(converted);
                received.put(elementName, converted);
            } else {
                Object element = bindObject(elementType, elementName + ".", req, received, depth + 1);
                if (element != null) {
                    list.add(element);
                }
            }
        }
        return list;
    }

    private boolean hasParametersWithPrefix(HttpServletRequest req, String prefix) {
        for (String key : req.getParameterMap().keySet()) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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

    private boolean isSimpleType(Class<?> type) {
        return type.equals(String.class)
                || type.equals(int.class) || type.equals(Integer.class)
                || type.equals(long.class) || type.equals(Long.class)
                || type.equals(double.class) || type.equals(Double.class)
                || type.equals(float.class) || type.equals(Float.class)
                || type.equals(boolean.class) || type.equals(Boolean.class);
    }

    private void printReceivedParams(Map<String, Object> receivedParams) {
        System.out.println("[Framework] Parametres recus :");
        for (Map.Entry<String, Object> entry : receivedParams.entrySet()) {
            System.out.println("  - " + entry.getKey() + " = " + entry.getValue());
        }
    }

    // ------------------------------------------------------------------
    // Gestion du resultat (JSON / vues)
    // ------------------------------------------------------------------

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
    private FileMap bindFile(String name, HttpServletRequest req) {
    try {
        Part part = req.getPart(name);
        if (part == null || part.getSize() == 0) {
            return null;
        }
        return new FileMap(part.getSubmittedFileName(), part.getInputStream().readAllBytes());
    } catch (Exception e) {
        return null;
    }
}
private List<FileMap> bindFileList(String name, HttpServletRequest req) {
    List<FileMap> files = new ArrayList<>();
    try {
        for (Part part : req.getParts()) {
            if (name.equals(part.getName()) && part.getSize() > 0) {
                files.add(new FileMap(part.getSubmittedFileName(), part.getInputStream().readAllBytes()));
            }
        }
    } catch (Exception e) {
        // on renvoie la liste telle quelle (vide ou partielle)
    }
    return files;
}
}