package services.api;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

public class JDoodleService {

    private static final String API_URL = "https://api.jdoodle.com/v1/execute";
    private final HttpClient httpClient;

    private String clientId;
    private String clientSecret;

    public JDoodleService() {
        loadConfig();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    private void loadConfig() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            Properties prop = new Properties();
            if (input == null) {
                System.err.println("Sorry, unable to find config.properties for JDoodle.");
                return;
            }
            prop.load(input);
            this.clientId = prop.getProperty("JDOODLE_CLIENT_ID");
            this.clientSecret = prop.getProperty("JDOODLE_CLIENT_SECRET");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public String executeCode(String script, String language, String versionIndex) {
        if (clientId == null || clientSecret == null || clientId.isEmpty()) {
            return "Error: Missing JDoodle credentials in config.properties.";
        }

        try {
            // Escape special characters to prevent JSON breakage
            String escapedScript = script.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");

            // Build the JSON payload required by JDoodle
            String jsonBody = "{"
                    + "\"clientId\": \"" + clientId + "\","
                    + "\"clientSecret\": \"" + clientSecret + "\","
                    + "\"script\": \"" + escapedScript + "\","
                    + "\"language\": \"" + language + "\","
                    + "\"versionIndex\": \"" + versionIndex + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractOutput(response.body());
            } else {
                return "API Error (" + response.statusCode() + "): " + response.body();
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Execution failed: " + e.getMessage();
        }
    }

    // Lightweight JSON parser for JDoodle's response
    private String extractOutput(String json) {
        try {
            String outputMarker = "\"output\":\"";
            int start = json.indexOf(outputMarker);
            if (start == -1) return "Program executed, but no output could be parsed.";
            start += outputMarker.length();

            int end = start;
            while (end < json.length()) {
                if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\') {
                    break;
                }
                end++;
            }

            String rawOutput = json.substring(start, end);
            return rawOutput.replace("\\n", "\n").replace("\\\"", "\"").replace("\\t", "\t");
        } catch (Exception e) {
            return "Error parsing response from JDoodle.";
        }
    }
}