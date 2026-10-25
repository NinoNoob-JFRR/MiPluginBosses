package com.tunombre.bossplugin.model;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Efecto individual de una habilidad de jefe.
 */
public record AbilityEffect(String type, Map<String, Object> params) {

    public String type() {
        return type == null ? "" : type.toUpperCase(Locale.ROOT);
    }

    public double d(String key, double def) {
        Object value = params.get(key);
        return value instanceof Number n ? n.doubleValue() : def;
    }

    public int i(String key, int def) {
        Object value = params.get(key);
        return value instanceof Number n ? n.intValue() : def;
    }

    public String s(String key, String def) {
        Object value = params.get(key);
        return value == null ? def : String.valueOf(value);
    }

    public boolean b(String key, boolean def) {
        Object value = params.get(key);
        return value instanceof Boolean bool ? bool : def;
    }

    @SuppressWarnings("unchecked")
    public List<String> list(String key) {
        Object value = params.get(key);
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
