package services.library;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Calls the Grok API (xAI) to explain selected text from a PDF.
 * Uses the /v1/chat/completions endpoint — same format as OpenAI.
 * Get your API key from: https://console.x.ai/
 */
public class GrokService {

    private static final String API_URL = "https://api.x.ai/v1/chat/completions";
    private static final String MODEL   = "grok-3-mini";

    // Replace with your real key from https://console.x.ai/
    private static final String API_KEY = "xai-n7V1pFuVPMP7OFzTfSktXmOV1jKGSzdevkQWCQBsO20GgNXPdk3Rn2vXC2G2xWJVJpnwav4ZRLbaKqh0";

    /**
     * Sends a message to Grok and streams the response token by token.
     * Runs on a background thread — calls onToken on each chunk, onDone when finished.
     *
     * @param selectedText  the text the student selected from the PDF
     * @param userMessage   optional follow-up question (null = just explain the selection)
     * @param onToken       called for each streamed token (update UI incrementally)
     * @param onDone        called when streaming is complete
     * @param onError       called if something goes wrong
     */
    public void explainAsync(String selectedText, String userMessage,
                             Consumer<String> onToken,
                             Runnable onDone,
                             Consumer<String> onError) {
        new Thread(() -> {
            try {
                // Build the prompt
                String systemPrompt = "You are a helpful reading assistant embedded in a digital library app. " +
                        "When a student selects text from a book, explain it clearly and concisely. " +
                        "Use simple language, give examples when helpful, and keep responses focused.";

                String userPrompt = userMessage != null && !userMessage.isBlank()
                        ? userMessage
                        : "Please explain this passage from the book:\n\n\"" + selectedText + "\"";

                // Build JSON body
                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("stream", true);
                body.put("messages", new JSONArray()
                        .put(new JSONObject().put("role", "system").put("content", systemPrompt))
                        .put(new JSONObject().put("role", "user").put("content",
                                selectedText != null && !selectedText.isBlank()
                                        ? "Context (selected text): \"" + selectedText + "\"\n\n" + userPrompt
                                        : userPrompt))
                );

                // Make the HTTP request
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
                    onError.accept("API error " + conn.getResponseCode() + ": " + err);
                    return;
                }

                // Read the SSE stream line by line
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
                                        String token = delta.getString("content");
                                        onToken.accept(token);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
                onDone.run();

            } catch (Exception e) {
                onError.accept("Connection error: " + e.getMessage());
            }
        }).start();
    }
}
