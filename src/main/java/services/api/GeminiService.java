package services.api;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Properties;

public class GeminiService {

    // 🔥 Securely loads the key from the hidden file instead of hardcoding it
    private static final String API_KEY = loadApiKey();
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

    private final HttpClient httpClient;

    public GeminiService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Reaches into the src/main/resources/ folder to read the config.properties file.
     */
    private static String loadApiKey() {
        try (InputStream input = GeminiService.class.getClassLoader().getResourceAsStream("config.properties")) {
            Properties prop = new Properties();
            if (input == null) {
                System.err.println("🚨 ERROR: config.properties file not found in src/main/resources/");
                return "MISSING_KEY";
            }
            prop.load(input);
            return prop.getProperty("GEMINI_API_KEY");
        } catch (Exception ex) {
            System.err.println("🚨 ERROR: Could not read config.properties");
            return "ERROR";
        }
    }

    /**
     * Fixes grammar, spelling, and professional tone before publishing.
     */
    public String enhancePost(String rawText) {
        String prompt = "You are a technical editor for an engineering student forum. " +
                "Fix all spelling and grammar mistakes in the following text. " +
                "Improve the formatting for readability but keep the original meaning. " +
                "CRITICAL RULE: Return ONLY the corrected text. Do not include introductory phrases like 'Here is the text'.\n\n" +
                "Text to enhance:\n" + rawText;

        return callGeminiAPI(prompt);
    }

    /**
     * Generates a quick 2-sentence summary for a very long discussion.
     */
    public String summarizePost(String longText) {
        String prompt = "Provide a maximum 2-sentence summary of the following forum discussion. " +
                "Keep it highly technical and concise. " +
                "CRITICAL RULE: Return ONLY the summary.\n\n" +
                "Discussion:\n" + longText;

        return callGeminiAPI(prompt);
    }

    /**
     * Core HTTP method to communicate with Google's servers.
     */
    private String callGeminiAPI(String prompt) {
        try {
            // 1. Sanitize the user's text so it doesn't break our JSON payload
            String safePrompt = prompt.replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "");

            // 2. Build the exact JSON structure Gemini expects
            String jsonPayload = "{"
                    + "\"contents\": [{"
                    + "  \"parts\":[{\"text\": \"" + safePrompt + "\"}]"
                    + "}]"
                    + "}";

            // 3. Create the HTTP Request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // 4. Send the Request synchronously
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return extractTextFromJson(response.body());
            } else {
                System.err.println("🚨 Gemini API Error: " + response.statusCode() + " - " + response.body());
                return null;
            }

        } catch (Exception e) {
            System.err.println("🚨 Network Error while contacting Gemini: " + e.getMessage());
            return null;
        }
    }

    /**
     * A lightweight string parser to extract the AI's response without needing external JSON libraries like Jackson/Gson.
     */
    private String extractTextFromJson(String jsonResponse) {
        try {
            // Find the "text": " marker
            String target = "\"text\": \"";
            int startIndex = jsonResponse.indexOf(target);
            if (startIndex == -1) return "Error generating response.";

            startIndex += target.length();

            // Find the closing quote for the text block
            // We look for "\n" or just the quote depending on how Gemini formats it
            int endIndex = jsonResponse.indexOf("\"", startIndex);

            // Handle edge cases where the text itself contains escaped quotes
            while (jsonResponse.charAt(endIndex - 1) == '\\') {
                endIndex = jsonResponse.indexOf("\"", endIndex + 1);
            }

            String extractedText = jsonResponse.substring(startIndex, endIndex);

            // Un-escape standard JSON formatting characters
            return extractedText.replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\*", "*"); // Sometimes Gemini escapes asterisks
        } catch (Exception e) {
            System.err.println("Failed to parse Gemini response: " + e.getMessage());
            return "Error parsing AI response.";
        }
    }
}