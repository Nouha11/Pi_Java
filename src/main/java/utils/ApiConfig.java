package utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ApiConfig {

    private static Properties props = null;

    /**
     * Returns the value for the given key from config/api.properties.
     * Loads and caches the properties file on first call.
     * Returns null and logs a warning if the key is missing or blank.
     */
    public static String get(String key) {
        if (props == null) {
            loadProperties();
        }
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            System.err.println("[API_CONFIG] Key '" + key + "' is not configured.");
            return null;
        }
        return value;
    }

    private static void loadProperties() {
        props = new Properties();
        try (InputStream is = ApiConfig.class.getClassLoader()
                .getResourceAsStream("config/api.properties")) {
            if (is != null) {
                props.load(is);
            } else {
                System.err.println("[API_CONFIG] config/api.properties not found on classpath.");
            }
        } catch (IOException e) {
            System.err.println("[API_CONFIG] Failed to load config/api.properties: " + e.getMessage());
        }
    }
}
