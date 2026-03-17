package org.example.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import com.google.gson.Gson;

/**
 * Utility class for JSON operations and document handling
 */
public class JsonUtils {
    
    private static final Gson gson = new Gson();
    
    /**
     * Converts Map to JSON string with sorted keys for consistent serialization
     */
    public static String toSortedJson(Map<String, Object> map) {
        Map<String, Object> sortedMap = new TreeMap<>(map);
        return gson.toJson(sortedMap);
    }
    
    /**
     * Converts JSON string to Map
     */
    public static Map<String, Object> fromJson(String json) {
        return gson.fromJson(json, Map.class);
    }
    
    /**
     * Creates a copy of the document without the specified field
     */
    public static Map<String, Object> copyWithoutField(Map<String, Object> document, String fieldToRemove) {
        Map<String, Object> copy = new HashMap<>(document);
        copy.remove(fieldToRemove);
        return copy;
    }
    
    /**
     * Sorts document fields using TreeMap for consistent ordering
     */
    public static Map<String, Object> sortDocument(Map<String, Object> document) {
        return new TreeMap<>(document);
    }
    
    /**
     * Parses JSON string and extracts specific fields
     */
    public static String extractJsonField(String json, String fieldName) {
        Map<String, Object> parsed = fromJson(json);
        Object value = parsed.get(fieldName);
        return value != null ? value.toString() : null;
    }
}