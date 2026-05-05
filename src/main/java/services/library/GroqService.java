package services.library;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI assistant service using Groq API with Llama 3.3 70B Versatile.
 * Falls back to local text analysis if the API is unavailable or disabled.
 *
 * API: https://api.groq.com/openai/v1/chat/completions
 * Model: llama-3.3-70b-versatile
 * Get your key: https://console.groq.com/keys
 */
public class GroqService {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL   = "llama-3.3-70b-versatile";

    // Replace with your Groq API key from https://console.groq.com/keys
    private static final String API_KEY = "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";

    // Set to false to force local fallback mode (useful for offline/demo)
    private static final boolean API_ENABLED = true;

    /**
     * Sends a message to Groq and streams the response token by token.
     * Falls back to local analysis if API is disabled or fails.
     *
     * @param selectedText  text selected from the PDF (context)
     * @param userMessage   user's question or null for auto-explain
     * @param onToken       called for each streamed token
     * @param onDone        called when complete
     * @param onError       called on error (won't be called if fallback succeeds)
     */
    public void explainAsync(String selectedText, String userMessage,
                             Consumer<String> onToken,
                             Runnable onDone,
                             Consumer<String> onError) {
        new Thread(() -> {
            if (!API_ENABLED || API_KEY.startsWith("gsk_YOUR")) {
                // Use local fallback immediately
                String result = localAnalysis(selectedText, userMessage);
                onToken.accept(result);
                onDone.run();
                return;
            }

            try {
                callGroqApi(selectedText, userMessage, onToken, onDone);
            } catch (Exception e) {
                // API failed — fall back to local analysis
                System.err.println("Groq API failed, using local fallback: " + e.getMessage());
                String result = localAnalysis(selectedText, userMessage);
                onToken.accept(result);
                onDone.run();
            }
        }).start();
    }

    // ── GROQ API CALL ─────────────────────────────────────────────────────────

    private void callGroqApi(String selectedText, String userMessage,
                             Consumer<String> onToken, Runnable onDone) throws Exception {

        String systemPrompt = "You are a helpful reading assistant embedded in a digital library app. " +
                "When a student selects text from a book, explain it clearly and concisely. " +
                "Use simple language, give examples when helpful, and keep responses focused. " +
                "If the text is in French, respond in French.";

        String userPrompt = (userMessage != null && !userMessage.isBlank())
                ? userMessage
                : "Please explain this passage from the book:\n\n\"" + selectedText + "\"";

        String fullContent = (selectedText != null && !selectedText.isBlank()
                && userMessage != null && !userMessage.isBlank())
                ? "Context (selected text): \"" + selectedText + "\"\n\nQuestion: " + userPrompt
                : userPrompt;

        JSONObject body = new JSONObject();
        body.put("model", MODEL);
        body.put("stream", true);
        body.put("max_tokens", 1024);
        body.put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                .put(new JSONObject().put("role", "user").put("content", fullContent))
        );

        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() != 200) {
            String err = new String(conn.getErrorStream().readAllBytes());
            throw new RuntimeException("API error " + conn.getResponseCode() + ": " + err);
        }

        // Read SSE stream
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) break;
                    try {
                        JSONObject chunk = new JSONObject(data);
                        JSONArray choices = chunk.optJSONArray("choices");
                        if (choices != null && !choices.isEmpty()) {
                            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                            if (delta != null && delta.has("content")) {
                                onToken.accept(delta.getString("content"));
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        onDone.run();
    }

    // ── LOCAL FALLBACK ────────────────────────────────────────────────────────

    /**
     * Local text analysis when API is unavailable.
     * Provides word count, language detection, and keyword extraction.
     */
    private String localAnalysis(String selectedText, String userMessage) {
        if (selectedText == null || selectedText.isBlank()) {
            return "Please select some text from the PDF to analyze.";
        }

        StringBuilder result = new StringBuilder();
        result.append("📊 Local Analysis (API unavailable)\n\n");

        // Word count
        String[] words = selectedText.trim().split("\\s+");
        result.append("Words: ").append(words.length).append("\n");
        result.append("Characters: ").append(selectedText.length()).append("\n\n");

        // Language detection (simple heuristic)
        String lang = detectLanguage(selectedText);
        result.append("Detected language: ").append(lang).append("\n\n");

        // Keyword extraction (most frequent non-stop words)
        List<String> keywords = extractKeywords(selectedText);
        if (!keywords.isEmpty()) {
            result.append("Key terms: ").append(String.join(", ", keywords)).append("\n\n");
        }

        // Summary hint
        result.append("Selected text preview:\n\"");
        result.append(selectedText.length() > 200
                ? selectedText.substring(0, 200) + "..."
                : selectedText);
        result.append("\"\n\n");
        result.append("To get AI explanations, add your Groq API key in GroqService.java.");

        return result.toString();
    }

    private String detectLanguage(String text) {
        String lower = text.toLowerCase();
        long frenchMarkers = Arrays.stream(new String[]{"le ", "la ", "les ", "de ", "du ", "des ",
                "un ", "une ", "et ", "est ", "que ", "qui ", "dans ", "pour ", "avec "})
                .filter(lower::contains).count();
        long englishMarkers = Arrays.stream(new String[]{"the ", "is ", "are ", "and ", "of ",
                "to ", "in ", "that ", "it ", "for ", "with ", "this "})
                .filter(lower::contains).count();
        if (frenchMarkers > englishMarkers) return "French";
        if (englishMarkers > frenchMarkers) return "English";
        return "Unknown";
    }

    private List<String> extractKeywords(String text) {
        Set<String> stopWords = new HashSet<>(Arrays.asList(
                "le", "la", "les", "de", "du", "des", "un", "une", "et", "est", "que", "qui",
                "dans", "pour", "avec", "the", "is", "are", "and", "of", "to", "in", "that",
                "it", "for", "with", "this", "a", "an", "on", "at", "by", "as", "be", "was"
        ));

        Map<String, Integer> freq = new HashMap<>();
        Pattern p = Pattern.compile("[a-zA-ZÀ-ÿ]{4,}");
        Matcher m = p.matcher(text.toLowerCase());
        while (m.find()) {
            String word = m.group();
            if (!stopWords.contains(word)) {
                freq.merge(word, 1, Integer::sum);
            }
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .toList();
    }
}
