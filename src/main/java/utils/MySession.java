package utils;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;

public class MySession {

    private Map<String, Object> data;
    private HttpSession httpSession;

    public MySession() {
        this.data = new HashMap<>();
    }

    public MySession(Map<String, Object> data) {
        this.data = (data != null) ? data : new HashMap<>();
    }

    public MySession(Map<String, Object> data, HttpSession httpSession) {
        this.data = (data != null) ? data : new HashMap<>();
        this.httpSession = httpSession;
    }

    public void set(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }

    public void remove(String key) {
        data.remove(key);
    }

    public void clear() {
        data.clear();
    }

    public void invalidate() {
        data.clear();
        if (httpSession != null) {
            try {
                httpSession.invalidate();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = (data != null) ? data : new HashMap<>();
    }
}