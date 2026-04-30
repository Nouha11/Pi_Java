package services.quiz;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

/**
 * Calls the Gemini 2.0 Flash REST API to generate a hint for a quiz question.
 * The API key is read from resources/config/api.properties (key: gemini_api).
 */
public class GeminiService {

    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    private final String apiKey;
    private final HttpClient http;

    public GeminiService() {
        apiKey = loadApiKey();
        http   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Generates a concise hint for the given question without revealing the answer.
     * Retries up to 3 times with exponential backoff on 429 rate-limit responses.
     * Blocking — call from a background thread.
     */
    public String generateHint(String questionText, String choices) {
        String prompt = "You are a helpful quiz assistant. "
                + "Give a short, useful hint (1-2 sentences) for the following multiple-choice question "
                + "WITHOUT revealing the correct answer directly.\n\n"
                + "Question: " + questionText + "\n"
                + "Choices: " + choices + "\n\n"
                + "Hint:";

        JSONObject textPart    = new JSONObject().put("text", prompt);
        JSONArray  parts       = new JSONArray().put(textPart);
        JSONObject content     = new JSONObject().put("parts", parts);
        JSONArray  contents    = new JSONArray().put(content);
        JSONObject requestBody = new JSONObject().put("contents", contents);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(Duration.ofSeconds(15))
                .build();

        int     maxRetries = 3;
        long    delayMs    = 2000; // start at 2 s, doubles each retry

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JSONObject json = new JSONObject(response.body());
                    return json.getJSONArray("candidates")
                               .getJSONObject(0)
                               .getJSONObject("content")
                               .getJSONArray("parts")
                               .getJSONObject(0)
                               .getString("text")
                               .trim();
                }

                if (response.statusCode() == 429) {
                    if (attempt < maxRetries) {
                        Thread.sleep(delayMs);
                        delayMs *= 2; // exponential backoff
                        continue;
                    }
                    return "Hint unavailable — API rate limit reached. Please wait a moment and try again.";
                }

                return "Hint unavailable (API error " + response.statusCode() + ").";

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Hint unavailable (interrupted).";
            } catch (IOException e) {
                if (attempt < maxRetries) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "Hint unavailable (interrupted).";
                    }
                    delayMs *= 2;
                    continue;
                }
                return "Hint unavailable (network error).";
            } catch (Exception e) {
                return "Hint unavailable (unexpected error).";
            }
        }

        return "Hint unavailable.";
    }

    // ── Private helpers ───────────────────────────────────────

    private static String loadApiKey() {
        try (InputStream in = GeminiService.class
                .getResourceAsStream("/config/api.properties")) {
            if (in == null) return "";
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("gemini_api", "");
        } catch (IOException e) {
            System.err.println("Could not load api.properties: " + e.getMessage());
            return "";
        }
    }
}
