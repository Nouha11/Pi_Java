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
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Generates quiz hints using the OpenRouter API (OpenAI-compatible).
 * Falls back to a large pool of generic hints if the API is unavailable —
 * the user will never see an error message.
 */
public class HuggingFaceService {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL   = "mistralai/mistral-7b-instruct:free";

    // ── Fallback hint pool ────────────────────────────────────
    private static final List<String> FALLBACK_HINTS = List.of(
        "Think about what you already know about this topic and eliminate the options that seem least likely.",
        "Try to recall any related facts or concepts — sometimes the answer connects to something familiar.",
        "Read each choice carefully; one of them is clearly more specific or accurate than the others.",
        "Consider the context of the question — the wording often gives away which category the answer belongs to.",
        "Eliminate the choices that are obviously wrong first, then focus on what remains.",
        "Think about the most common or well-known fact related to this subject.",
        "If two choices seem similar, the correct one is usually the more precise or complete version.",
        "Trust your first instinct — your initial reaction is often based on knowledge you already have.",
        "Break the question down into smaller parts; each part might point you toward the right answer.",
        "Think about what you've studied or experienced that relates to this topic.",
        "The answer is often the option that is most specific rather than the most general.",
        "Consider which choice would make the most logical sense given what the question is asking.",
        "Look for keywords in the question that hint at the category or field the answer belongs to.",
        "Sometimes the longest or most detailed answer is the correct one — it tends to be more precise.",
        "Think about cause and effect — what outcome or fact would naturally follow from the question?",
        "Recall any examples or real-world cases you know that relate to this question.",
        "The correct answer often aligns with a fundamental principle or rule in this subject area.",
        "If you're unsure, go with the answer that feels most familiar from your studies.",
        "Consider the time period, location, or context implied by the question.",
        "Think about what distinguishes this concept from similar ones — that distinction is often the answer.",
        "Ask yourself: which of these choices would an expert in this field immediately recognize as correct?",
        "The answer is usually the one that is most universally accepted or well-established.",
        "Pay attention to absolute words like 'always', 'never', 'all' — they often signal a wrong answer.",
        "Think about the definition of the key term in the question — it usually points directly to the answer.",
        "Consider which answer would be taught first in an introductory course on this subject.",
        "The correct answer often has a direct, clear relationship to the question without extra assumptions.",
        "Think about any mnemonics, formulas, or rules of thumb you know for this topic.",
        "If the question asks about a process, think about the logical sequence of steps involved.",
        "Consider which answer is supported by the most evidence or is most widely agreed upon.",
        "Think about what makes this topic unique — the answer usually highlights that uniqueness.",
        "Recall any diagrams, charts, or visual representations you've seen related to this concept.",
        "The answer that is most balanced and moderate is often correct when extremes are offered.",
        "Think about the purpose or function of the thing being asked about — it often reveals the answer.",
        "Consider which choice best completes the idea or concept introduced in the question.",
        "If you've seen this type of question before, recall how similar ones were answered.",
        "Think about the relationship between the subject and the predicate of the question.",
        "The correct answer often uses precise, technical language rather than vague or casual terms.",
        "Consider what would happen if each choice were true — only one will make complete sense.",
        "Think about the most important or defining characteristic of the subject being asked about.",
        "Recall any real-world applications or examples that demonstrate this concept in action."
    );

    private static final Random RANDOM = new Random();

    private final String     apiKey;
    private final HttpClient http;

    public HuggingFaceService() {
        apiKey = loadApiKey();
        http   = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * Generates a short hint for a quiz question without revealing the answer.
     * Always waits at least 1500 ms so the loading state is always visible to the user.
     * Falls back to a random generic hint if the API call fails for any reason.
     * Blocking — always call from a background thread.
     */
    public String generateHint(String questionText, String choices) {
        long startMs = System.currentTimeMillis();

        String result = fetchHint(questionText, choices);

        // Enforce minimum perceived delay of 1500 ms
        long elapsed = System.currentTimeMillis() - startMs;
        if (elapsed < 1500) {
            try { Thread.sleep(1500 - elapsed); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        return result;
    }

    private String fetchHint(String questionText, String choices) {
        String userContent = "Give a short hint (1-2 sentences) for this multiple-choice question "
                + "WITHOUT revealing the correct answer.\n"
                + "Question: " + questionText + "\n"
                + "Choices: " + (choices == null || choices.isBlank() ? "not provided" : choices);

        JSONArray messages = new JSONArray()
                .put(new JSONObject()
                        .put("role", "system")
                        .put("content", "You are a helpful quiz assistant. Give concise hints without revealing answers."))
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", userContent));

        JSONObject body = new JSONObject()
                .put("model", MODEL)
                .put("messages", messages)
                .put("max_tokens", 120);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

        int  maxRetries = 3;
        long delayMs    = 3000;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                HttpResponse<String> response =
                        http.send(request, HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();

                if (status == 200) {
                    String hint = parseHint(response.body());
                    // If parsing failed or returned empty, fall back silently
                    return (hint == null || hint.isBlank()) ? randomFallback() : hint;
                }

                System.err.println("[HintService] HTTP " + status);

                if ((status == 429 || status == 503) && attempt < maxRetries) {
                    Thread.sleep(delayMs);
                    delayMs *= 2;
                    continue;
                }

                // Any non-retryable error → silent fallback
                return randomFallback();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return randomFallback();
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    try { Thread.sleep(delayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return randomFallback();
                    }
                    delayMs *= 2;
                    continue;
                }
                return randomFallback();
            }
        }

        return randomFallback();
    }

    // ── Private helpers ───────────────────────────────────────

    private static String parseHint(String responseBody) {
        try {
            return new JSONObject(responseBody)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String randomFallback() {
        return FALLBACK_HINTS.get(RANDOM.nextInt(FALLBACK_HINTS.size()));
    }

    private static String loadApiKey() {
        try (InputStream in = HuggingFaceService.class
                .getResourceAsStream("/config/api.properties")) {
            if (in == null) return "";
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("openrouter", "").trim();
        } catch (IOException e) {
            System.err.println("Could not load api.properties: " + e.getMessage());
            return "";
        }
    }
}
