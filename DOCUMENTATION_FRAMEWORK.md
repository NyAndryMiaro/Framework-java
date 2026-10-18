# MiaroFramework — Documentation technique

Framework MVC Java maison, inspiré de Spring MVC, bâti sprint par sprint sur l'API Servlet (Jakarta) et la réflexion Java.

---

## 1. Vue d'ensemble

### 1.1 Les fichiers du framework

| Fichier | Rôle |
|---|---|
| `frameworkAnnotation/Controller.java` | Annotation de **classe** : marque une classe comme contrôleur |
| `frameworkAnnotation/UrlMapping.java` | Annotation de **méthode** : associe une URL (`url`) et un verbe HTTP (`method`, `GET` par défaut) |
| `frameworkAnnotation/WebApi.java` | Annotation de **méthode** : le retour est sérialisé en JSON au lieu d'être interprété comme une vue |
| `utils/Utils.java` | Scan du classpath, détection des contrôleurs, construction de la table de routage |
| `utils/FileMap.java` | Objet représentant un fichier téléversé (nom, extension, octets) |
| `listener/AppListener.java` | Point d'entrée au démarrage : scan + création de la table d'URL + conteneur Spring |
| `frameworkServlet/FrontServletController.java` | Servlet unique qui reçoit **toutes** les requêtes, trouve la bonne méthode, prépare ses arguments, l'exécute et traite son résultat |

### 1.2 Les deux grandes phases

```
 PHASE 1 — DÉMARRAGE (une seule fois)            PHASE 2 — CHAQUE REQUÊTE
 ─────────────────────────────────────           ─────────────────────────────────────────
 Tomcat charge l'application                     Navigateur ──► FrontServletController
        │                                                │
        ▼                                                ▼
 AppListener.contextInitialized()                processRequest()
   ├─ Utils.scanDirectory()  → classes @Controller        ├─ fichier statique ?  → forwardToStatic()
   ├─ Utils.buildUrlMap()    → table "VERBE:url"           ├─ sinon cherche "VERBE:url" dans urlMap
   │      stockée dans ServletContext["urlMap"]            │      ├─ trouvé  → invokeMapping()
   └─ Conteneur Spring       → ServletContext              │      └─ absent  → renderNotFound() (404)
      ["applicationContext"]                               ▼
                                                  invokeMapping()
                                                    ├─ resolveControllerInstance()
                                                    ├─ construction des arguments (binding)
                                                    ├─ method.invoke()
                                                    └─ handleResult() : JSON / redirection / vue / texte
```

### 1.3 Configuration (`web.xml` de l'application utilisatrice)

Les paramètres sont des **`<context-param>`** (lus via `ServletContext.getInitParameter`), pas des `<init-param>` du servlet.

| Paramètre | Lu par | Rôle |
|---|---|---|
| `package-to-scan` | `AppListener` | Package contenant les contrôleurs (ex. `controller`) ; également donné à Spring |
| `prefix` | `FrontServletController.init` | Préfixe de résolution des vues (ex. `/`) |
| `suffix` | `FrontServletController.init` | Suffixe de résolution des vues (ex. `.jsp`) |

Le servlet est mappé sur `url-pattern /` : **toutes** les requêtes passent par lui.

```xml
<context-param><param-name>package-to-scan</param-name><param-value>controller</param-value></context-param>
<context-param><param-name>prefix</param-name><param-value>/</param-value></context-param>
<context-param><param-name>suffix</param-name><param-value>.jsp</param-value></context-param>

<servlet>
    <servlet-name>FrontController</servlet-name>
    <servlet-class>frameworkServlet.FrontServletController</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>FrontController</servlet-name>
    <url-pattern>/</url-pattern>
</servlet-mapping>
```

---

## 2. Les sprints : ce qui est fait et par quelles méthodes

### Sprint 0 — Récupérer l'URL demandée

**Objectif :** récupérer l'URL tapée dans la barre d'adresse.

**Méthode :** `FrontServletController.processRequest()`

```java
String path = req.getContextPath();                       // "/ApplicationTestFramework"
String url  = req.getRequestURI().substring(path.length()); // "/form"
```

On retire le *context path* pour ne garder que le chemin relatif à l'application. `doGet()` et `doPost()` délèguent tous deux à `processRequest()`.

---

### Sprint 1 — Trouver les classes annotées `@Controller`

**Objectif :** repérer toutes les classes marquées `@Controller`.

| Méthode | Rôle |
|---|---|
| `Utils.scanDirectory(currentFile, rootDir, annotatedClasses)` | Parcourt **récursivement** un dossier. Pour chaque `.class`, charge la classe (`Class.forName`) et la garde si `isAnnotationPresent(Controller.class)`. Si le chargement échoue, un message est affiché et le scan continue. |
| `Utils.getClassNameWithPackage(classFile, rootDir)` | Transforme un chemin de fichier en nom de classe qualifié (`controller/TestController.class` → `controller.TestController`). |

L'annotation `Controller` est déclarée avec `@Target(TYPE)` et `@Retention(RUNTIME)` : sans `RUNTIME`, elle ne serait pas visible par la réflexion.

---

### Sprint 2 — Associer une URL à une méthode `@UrlMapping`

**Objectif :** pour chaque URL, retrouver la méthode annotée correspondante.

**Méthode :** `Utils.buildUrlMap(annotatedClasses)`

Pour chaque classe contrôleur, elle examine `getDeclaredMethods()`, lit `@UrlMapping` et remplit une `Map<String, MethodMapping>`. `MethodMapping` regroupe `clazz`, `method` et `httpMethod`.

Si deux méthodes déclarent la même clé, une `RuntimeException` est levée **au démarrage** (« Mapping deja existant »).

---

### Sprint 3 — Attribut `method` (GET / POST)

**Objectif :** une même URL peut mener à deux méthodes selon le verbe HTTP.

- `UrlMapping` expose `String method() default "GET"`.
- La clé de la table est `VERBE:url` (ex. `POST:/save`), construite à deux endroits :
  - `Utils.buildUrlMap()` à l'enregistrement ;
  - `FrontServletController.processRequest()` à la requête (`lookupKey = currentMethod + ":" + url`).

Si aucune entrée ne correspond (mauvaise URL **ou** mauvais verbe), `renderNotFound()` renvoie un 404 listant toutes les routes connues.

---

### Sprint 3 bis — Exécuter la méthode associée

**Objectif :** appeler réellement la méthode du contrôleur.

**Méthode :** `FrontServletController.invokeMapping(mapping, req, res)`

1. `resolveControllerInstance()` obtient l'objet contrôleur.
2. Les arguments sont préparés (voir sprints 5 bis et 7).
3. `method.setAccessible(true)` puis `method.invoke(controllerInstance, args)`.
4. Le résultat est passé à `handleResult()`.

Toute exception est enveloppée dans une `ServletException` (« Erreur lors de l'invocation de la methode … »).

---

### Sprint 4 — Scanner au démarrage avec un `ContextListener`

**Objectif :** ne pas rescanner à chaque requête.

**Méthode :** `AppListener.contextInitialized(sce)` (classe annotée `@WebListener`)

Déroulé :

1. Lit `package-to-scan` dans le contexte.
2. Détermine le dossier du package (`classLoader.getResource(...)`) et la racine du classpath (`globalRootDir`).
3. Appelle `Utils.scanDirectory()` puis `Utils.buildUrlMap()`.
4. Stocke le résultat : `context.setAttribute("urlMap", urlMap)`.

Côté servlet, `FrontServletController.init()` relit cette table avec `getAttribute("urlMap")`. Si elle est absente, une `ServletException` explicite est levée au démarrage du servlet.

---

### Sprint 5 — Gestion des vues

**Objectif :** une méthode peut retourner le nom d'une page.

**Méthodes :** `handleResult()`, `viewExists()`, `forwardToStatic()`

Pour un résultat de type `String` (méthode **non** annotée `@WebApi`), dans cet ordre :

| Valeur retournée | Comportement |
|---|---|
| `"redirect:/xxx"` | `res.sendRedirect(contextPath + "/xxx")` |
| `"/chemin/page.html"` (commence par `/`) | Si le fichier existe → `forwardToStatic()` ; sinon la chaîne est affichée en texte |
| `"nom"` (relatif) | Résolu en `prefix + nom + suffix` (ex. `/nom.jsp`). Si le fichier existe → forward ; sinon la chaîne est affichée en texte |
| Autre type d'objet | `result.toString()` affiché |
| `null` | Rien n'est écrit |

**Fichiers statiques :** `processRequest()` appelle d'abord `isStaticResource()`. Si le fichier demandé existe physiquement (ex. `/test.html`), il est servi directement, sans passer par la table d'URL.

**`forwardToStatic()`** — le servlet étant mappé sur `/`, un simple `getRequestDispatcher(...).forward(...)` vers un `.html` reviendrait dans le framework (boucle infinie → `StackOverflowError`). La méthode utilise donc le dispatcher nommé `"default"` de Tomcat, avec une requête enveloppée (`HttpServletRequestWrapper`) dont `getRequestURI()`, `getServletPath()` et `getPathInfo()` sont réécrits pour pointer sur le fichier voulu.

---

### Sprint 5 bis — Conteneur Spring et injection

**Objectif :** disposer d'un `ApplicationContext` Spring dans les contrôleurs.

| Méthode | Rôle |
|---|---|
| `AppListener.contextInitialized()` | Si `package-to-scan` est défini : crée un `AnnotationConfigApplicationContext`, appelle `scan(package)` puis `refresh()`, et le stocke dans `ServletContext["applicationContext"]` |
| `AppListener.contextDestroyed()` | Ferme proprement le conteneur à l'arrêt de l'application |
| `FrontServletController.init()` | Récupère le contexte ; il reste `null` s'il n'existe pas |
| `FrontServletController.resolveControllerInstance(clazz)` | `applicationContext.getBean(clazz)` ; en cas de `NoSuchBeanDefinitionException` (ou sans contexte), instanciation directe avec le constructeur vide |
| `FrontServletController.invokeMapping()` | Si un paramètre de la méthode est de type `ApplicationContext`, le contexte lui est injecté (`null` s'il n'existe pas) |

> Le sprint demandait de passer `null` quand le contexte n'existe pas. Pour les **arguments**, c'est le cas. Pour l'**instance du contrôleur**, l'implémentation est plus tolérante : elle retombe sur une instanciation directe.

Les paramètres de type `HttpServletRequest` et `HttpServletResponse` sont aussi injectés par `invokeMapping()`.

---

### Sprint 6 — Web API (JSON)

**Objectif :** une méthode peut renvoyer du JSON plutôt qu'une vue.

- Annotation : `@WebApi` (sur la méthode, à côté de `@UrlMapping`).
- Traitement : `handleResult()` teste `method.isAnnotationPresent(WebApi.class)` **avant** tout le reste :

```java
res.setContentType("application/json;charset=UTF-8");
String json = new ObjectMapper().writeValueAsString(result);
res.getWriter().print(json);
```

Conséquence : une méthode `@WebApi` qui retourne une `String` produit du JSON (`"texte"`) et non un nom de vue. Jackson (`jackson-databind`) sérialise POJO, `List`, `Map` et types simples ; les POJO doivent exposer des **getters publics**.

---

### Sprint 7 — Binding automatique des paramètres de requête

**Objectif :** remplir automatiquement les arguments des méthodes à partir des données du formulaire, en faisant correspondre **les noms**.

**Prérequis de compilation :** l'option `javac -parameters`, indispensable pour que `Parameter.getName()` renvoie `id` et non `arg0` (présente dans `compile.sh`, `compile.bat` et `deploy.bat`).

#### Ordre de décision dans `invokeMapping()` pour chaque argument

```
1. HttpServletRequest       → la requête
2. HttpServletResponse      → la réponse
3. ApplicationContext       → le contexte Spring
4. List<FileMap>            → bindFileList()          (sprint 8)
5. FileMap                  → bindFile()              (sprint 8)
6. type simple              → convertValue(req.getParameter(nom))
7. List<...>                → bindList()
8. autre classe (objet)     → bindObject()
```

L'ordre compte : `List<FileMap>` doit passer avant le cas général `List`, sinon `bindList` traiterait `FileMap` comme un objet ordinaire.

#### Méthodes de binding

| Méthode | Rôle |
|---|---|
| `isSimpleType(type)` | Vrai pour `String`, `int/Integer`, `long/Long`, `double/Double`, `float/Float`, `boolean/Boolean` |
| `convertValue(raw, type)` | Convertit une `String` vers le type cible. Renvoie `null` si la valeur est absente ou non convertible (`"abc"` → `int`) |
| `bindObject(type, prefix, req, received, depth)` | Instancie et remplit un objet (voir ci-dessous) |
| `bindList(genericType, name, req, received, depth)` | Construit une `List` (voir ci-dessous) |
| `resolveValue(type, genericType, name, req, received, depth)` | Aiguillage pour un champ ou un paramètre de constructeur : simple, liste ou objet |
| `hasParametersWithPrefix(req, prefix)` | Vrai si au moins un paramètre de requête commence par `prefix` |
| `printReceivedParams(map)` | Affiche dans la console Tomcat tout ce qui a été reçu |

#### `bindObject` — deux stratégies de construction

1. **Constructeur sans argument présent** : l'objet est créé vide, puis chaque champ non `static` et non `final` est rempli par réflexion (`Field.set`). Un champ sans donnée garde sa valeur par défaut.
2. **Pas de constructeur vide** : on choisit le constructeur ayant le **plus de paramètres** ; chaque paramètre est résolu par son **nom** (grâce à `-parameters`). Une valeur absente pour un type primitif devient `0` / `false`.

Si une classe a les deux, c'est le constructeur vide qui est utilisé.

#### Objets imbriqués (récursion par préfixe)

`bindObject` s'appelle lui-même avec un préfixe. Pour un champ `adresse` de type `Adresse`, il cherche `adresse.ville`, `adresse.rue`, etc. Un objet imbriqué dont aucun champ n'est présent dans le formulaire reste `null`. La profondeur est limitée par `MAX_DEPTH = 5` (protection contre les références circulaires).

#### Listes

`bindList` lit le type des éléments via les génériques (`List<Etudiant>` → `Etudiant`), puis :

- **Éléments simples** : plusieurs champs de même nom (`notes=12&notes=15`) ;
- **Sinon** : boucle sur `nom[0]`, `nom[1]`, … et s'arrête au premier indice absent. Chaque élément objet est construit par `bindObject` avec le préfixe `nom[i].`.

#### Conventions de nommage dans le formulaire

| Cas | `name` du champ HTML | Paramètre Java |
|---|---|---|
| Valeur simple | `id`, `nom` | `int id, String nom` |
| Objet | `id`, `nom` | `Etudiant etudiant` |
| Objet imbriqué | `adresse.ville` | champ `Adresse adresse` dans l'objet |
| Liste d'objets | `etudiants[0].nom`, `etudiants[1].nom` | `List<Etudiant> etudiants` |
| Liste de simples | `notes` (répété) ou `notes[0]`, `notes[1]` | `List<Integer> notes` |

Le préfixe d'un objet **racine** est vide : ses champs sont cherchés directement par leur nom, **pas** par le nom du paramètre Java. Pour une liste, en revanche, c'est le nom du paramètre qui sert de préfixe.

---

### Sprint 8 — Téléversement de fichiers (upload)

**Objectif :** récupérer un fichier envoyé par un `<input type="file">`.

#### `FileMap`

| Champ | Contenu | Exemple |
|---|---|---|
| `nom` | Nom du fichier sans extension | `photo` |
| `extension` | Extension sans le point | `jpeg` |
| `bytes` | Contenu complet du fichier | `byte[]` |

Le constructeur `FileMap(String fileName, byte[] bytes)` découpe le nom au **dernier point**. Sans point, l'extension est vide.

#### Mise en place

- `FrontServletController` est annoté **`@MultipartConfig`** : sans cela, Tomcat ne lit pas les formulaires `multipart/form-data`. Aucune configuration n'est donc requise dans le `web.xml` de l'application. Valeurs par défaut : pas de limite de taille.
- `FileMap` est un type reconnu par `invokeMapping()` (étapes 4 et 5 de l'ordre ci-dessus).

| Méthode | Rôle |
|---|---|
| `bindFile(name, req)` | `req.getPart(name)` → lit les octets → `new FileMap(nomSoumis, octets)`. Renvoie `null` si le champ est vide ou en cas d'erreur |
| `bindFileList(name, req)` | Parcourt `req.getParts()`, garde ceux dont le nom vaut `name` et dont la taille est > 0 → `List<FileMap>` |

Le `name` de l'input HTML doit être **identique** au nom du paramètre Java, et le formulaire doit déclarer `enctype="multipart/form-data"`.

---

## 3. Index des méthodes par classe

### `FrontServletController`

| Méthode | Sprint(s) | Rôle |
|---|---|---|
| `init(config)` | 4, 5, 5 bis | Charge `urlMap`, `prefix`, `suffix`, `applicationContext` |
| `doGet` / `doPost` | 0 | Délèguent à `processRequest` |
| `processRequest` | 0, 3, 5 | Extrait l'URL, sert les fichiers statiques, cherche la route, appelle `invokeMapping` ou `renderNotFound` |
| `isStaticResource` | 5 | Le fichier demandé existe-t-il sur le disque ? |
| `forwardToStatic` | 5 | Sert un fichier via le servlet par défaut, sans boucle |
| `renderNotFound` | 3 | 404 listant toutes les routes enregistrées |
| `resolveControllerInstance` | 5 bis | Bean Spring ou instanciation directe |
| `invokeMapping` | 3 bis, 5 bis, 7, 8 | Prépare les arguments, appelle la méthode, déclenche `handleResult` |
| `resolveValue` | 7 | Aiguille un champ vers simple / liste / objet |
| `bindObject` | 7 | Objet, objet imbriqué, constructeur vide ou rempli |
| `bindList` | 7 | Listes de simples ou d'objets |
| `hasParametersWithPrefix` | 7 | Détecte la présence de paramètres par préfixe |
| `convertValue` | 7 | `String` → type simple |
| `isSimpleType` | 7 | Reconnaît les types simples |
| `printReceivedParams` | 7 | Journal console des valeurs reçues |
| `handleResult` | 5, 6 | JSON, redirection, vue, texte |
| `viewExists` | 5 | La vue existe-t-elle ? |
| `bindFile` / `bindFileList` | 8 | Upload d'un ou plusieurs fichiers |

### `Utils`

| Méthode | Sprint | Rôle |
|---|---|---|
| `scanDirectory` | 1 | Parcours récursif et détection des `@Controller` |
| `getClassNameWithPackage` | 1 | Chemin de fichier → nom de classe |
| `buildUrlMap` | 2, 3 | Table `VERBE:url` → `MethodMapping` |

### `AppListener`

| Méthode | Sprint | Rôle |
|---|---|---|
| `contextInitialized` | 4, 5 bis | Scan, table d'URL, conteneur Spring |
| `contextDestroyed` | 5 bis | Fermeture du conteneur Spring |

---

## 4. Construction et déploiement

### 4.1 Le jar du framework

`compile.sh` / `compile.bat` :

1. Compile avec `javac -parameters -cp "lib/*" -d out ...`.
2. Extrait le contenu de chaque jar de `lib/` dans `out/` (jar « fat »), supprime les signatures (`META-INF/*.SF`, `*.DSA`, `*.RSA`) et `module-info.class`.
3. Produit `MiaroFramework.jar`.

Dépendances nécessaires dans `lib/` : l'API Servlet, les jars Spring (`spring-context`, `spring-core`, `spring-beans`, `spring-aop`, `spring-expression`, `spring-jcl`) et Jackson (`jackson-databind`, `jackson-core`, `jackson-annotations`). Elles sont embarquées dans le jar ; l'application n'a donc rien d'autre à copier.

> Attention : si `servlet-api.jar` est présent dans `lib/`, il se retrouve aussi dans le jar fat. Tomcat fournit déjà cette API ; mieux vaut l'exclure de l'extraction pour éviter des conflits de classes.

### 4.2 L'application de test

`deploy.sh` / `deploy.bat` :

1. Copient `MiaroFramework.jar` dans `WEB-INF/lib`.
2. Compilent l'application avec `-parameters`.
3. Copient les pages web, placent `web.xml` dans `WEB-INF`.
4. Génèrent le `.war` et le copient dans le dossier `webapps` de Tomcat.

---

## 5. Exemple complet

```java
@Controller
public class TestController {

    @UrlMapping(url = "/form")
    public String form() { return "/form.html"; }                 // vue statique

    @UrlMapping(url = "/save", method = "POST")
    public String save(int id, String nom) {                      // paramètres simples
        return "id=" + id + ", nom=" + nom;
    }

    @UrlMapping(url = "/save2", method = "POST")
    public String save2(Etudiant etudiant) {                      // objet (+ imbriqué)
        return etudiant.getNom();
    }

    @UrlMapping(url = "/save4", method = "POST")
    public String save4(List<Etudiant> etudiants) {               // liste d'objets
        return etudiants.size() + " etudiant(s)";
    }

    @UrlMapping(url = "/api/hello")
    @WebApi
    public Map<String, Object> hello() {                          // JSON
        return Map.of("message", "Hello");
    }

    @UrlMapping(url = "/save5", method = "POST")
    public String upload(FileMap fichier) {                       // upload
        return fichier.getNom() + "." + fichier.getExtension();
    }
}
```

---

## 6. Limites connues et points d'attention

1. **Les vues `.jsp` passent par `forwardToStatic()`.** Cette méthode utilise le servlet par défaut de Tomcat, qui sert un fichier **tel quel** : un `.jsp` risque d'être affiché en code source au lieu d'être exécuté. Pour les JSP, un `req.getRequestDispatcher(vue).forward(req, res)` classique convient et ne boucle pas, car `*.jsp` est mappé sur le `JspServlet`, plus spécifique que `/`. À corriger avant d'utiliser réellement `prefix` / `suffix` avec des JSP.
2. **Un fichier statique prime sur une route.** Si `/test` existe à la fois comme fichier et comme `@UrlMapping`, c'est le fichier qui est servi.
3. **Pas de 405.** Un mauvais verbe HTTP donne le même 404 qu'une URL inconnue.
4. **Paramètre primitif absent.** Pour `save(int id, ...)`, si `id` n'est pas envoyé, `convertValue` renvoie `null`, et `method.invoke` échoue sur un primitif. Utiliser `Integer` évite le problème. (Dans `bindObject`, le cas est géré : `0` / `false`.)
5. **Échecs de binding silencieux.** Une conversion impossible donne `null` sans erreur, et `bindObject` n'affiche qu'un message console.
6. **Types non gérés.** `Date`, `enum`, `BigDecimal`, tableaux et `Set` ne sont pas convertis (les champs correspondants restent à leur valeur par défaut).
7. **`FileMap` limité.** Il fonctionne comme argument direct ou en `List<FileMap>`, mais pas comme champ d'un objet, ni dans une liste d'objets contenant des fichiers. Il n'a pas non plus de constructeur vide.
8. **Fichiers en mémoire.** `FileMap` charge tout le contenu dans un `byte[]` ; inadapté aux très gros fichiers. `@MultipartConfig` est utilisé sans limites de taille.
9. **Doublons de routes.** Deux méthodes avec la même clé `VERBE:url` empêchent le démarrage de l'application.
10. **Index d'une liste.** `nom[0]`, `nom[2]` sans `nom[1]` : la lecture s'arrête avant l'indice manquant.
