package utils;

import java.util.HashMap;
import java.util.Map;

public class MySession {

    private Map<String, Object> data;

    public MySession() {
        this.data = new HashMap<>();
    }

    public MySession(Map<String, Object> data) {
        this.data = (data != null) ? data : new HashMap<>();
    }

    /** Insère ou met à jour une valeur */
    public void set(String key, Object value) {
        data.put(key, value);
    }

    /** Récupère une valeur */
    public Object get(String key) {
        return data.get(key);
    }

    /** Supprime une clé */
    public void remove(String key) {
        data.remove(key);
    }

    /** Vide toute la session */
    public void clear() {
        data.clear();
    }

    /** Vérifie si une clé existe */
    public boolean contains(String key) {
        return data.containsKey(key);
    }

    /** Retourne le Map interne (lecture seule recommandée) */
    public Map<String, Object> getData() {
        return data;
    }

    /** Remplace tout le contenu */
    public void setData(Map<String, Object> data) {
        this.data = (data != null) ? data : new HashMap<>();
    }
}