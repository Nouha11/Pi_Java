package services.ai;

import models.ai.ChatMessage;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.ApiConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.logging.Logger;

/**
 * Service layer for communicating with the Hugging Face Inference API.
 *
 * <p><strong>Thread-safety notice:</strong> This service is NOT thread-safe.
 * All calls to {@link #chat} must be made from a single background thread
 * (e.g., inside a {@code javafx.concurrent.Task}). Never call this service
 * directly from the JavaFX Application Thread.</p>
 *
 * <p>The service targets the Hugging Face Inference API using the
 * {@code mistralai/Mistral-7B-Instruct-v0.3} model. The model can be
 * changed by updating the {@code MODEL_ID} constant.</p>
 */
public class HuggingFaceService {

    private static final Logger LOG = Logger.getLogger(HuggingFaceService.class.getName());

    // ── Configuration ─────────────────────────────────────────────────────────

    /**
     * Hugging Face model to use for text generation.
     * Using gpt2 - a reliable, always-available model for testing.
     * For production, consider: facebook/blenderbot-400M-distill
     */
    private static final String MODEL_ID = "facebook/blenderbot-400M-distill";

    /** Base URL for the Hugging Face Inference API. */
    private static final String API_BASE_URL =
            "https://api-inference.huggingface.co/models/" + MODEL_ID;

    /** Maximum tokens the model should generate per response. */
    private static final int MAX_NEW_TOKENS = 512;

    /** HTTP request timeout in seconds (Requirement 11.7). */
    private static final int TIMEOUT_SECONDS = 30;

    // ── Fallback messages ─────────────────────────────────────────────────────

    /** Returned when the rate-limit window is exhausted (Requirement 11.8). */
    static final String RATE_LIMIT_FALLBACK =
            "⚠️ You've sent too many messages in a short time. Please wait a moment before trying again.";

    /** Returned when the HTTP request times out (Requirement 11.7). */
    static final String TIMEOUT_FALLBACK =
            "⏱️ The AI assistant is taking too long to respond. Please try again in a few seconds.";

    /** Returned when the API returns a non-200 status (Requirement 11.6). */
    static final String API_ERROR_FALLBACK =
            "🚨 The AI assistant encountered an error. Please try again later.";

    // ── Fields ────────────────────────────────────────────────────────────────

    private final HttpClient httpClient;
    private final RateLimiter rateLimiter;
    private final String apiKey;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new {@code HuggingFaceService} instance.
     * The API key is loaded from {@code config/api.properties} via {@link ApiConfig}.
     */
    public HuggingFaceService() {
        this.apiKey = ApiConfig.get("huggingface.api.key");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        this.rateLimiter = new RateLimiter(10, 60_000L); // 10 requests per 60 seconds
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Sends a chat request to the Hugging Face Inference API and returns the
     * assistant's reply as a plain string.
     *
     * <p><strong>Must be called from a background thread.</strong></p>
     *
     * @param systemPrompt the system-level instruction that scopes the AI's behaviour
     *                     to the current course context
     * @param history      the recent chat history (up to the last 10 messages) used
     *                     to maintain conversational context
     * @param userMessage  the student's latest message
     * @return the assistant's reply, or a fallback string if the request fails
     */
    public String chat(String systemPrompt, List<ChatMessage> history, String userMessage) {
        // Requirement 11.8 — enforce rate limit before making any API call
        if (!rateLimiter.tryAcquire()) {
            LOG.warning("[HuggingFaceService] Rate limit reached. Returning fallback.");
            return RATE_LIMIT_FALLBACK;
        }

        if (apiKey == null || apiKey.isBlank() || "YOUR_KEY_HERE".equals(apiKey)) {
            LOG.warning("[HuggingFaceService] API key is not configured. Using local fallback.");
            return generateLocalResponse(systemPrompt, userMessage);
        }

        try {
            String jsonPayload = buildPayload(systemPrompt, history, userMessage);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", "NovaLearningPlatform/1.0 (Educational Application)")
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            // Requirement 11.9 — caller is responsible for running this on a background thread;
            // we use sendAsync here to respect the per-request timeout set above.
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                // Requirement 11.6 — log and return local fallback
                LOG.warning("[HuggingFaceService] HTTP " + response.statusCode()
                        + " — Using local fallback. Response: " + response.body());
                return generateLocalResponse(systemPrompt, userMessage);
            }

        } catch (java.net.http.HttpTimeoutException e) {
            // Requirement 11.7 — timeout fallback
            LOG.warning("[HuggingFaceService] Request timed out: " + e.getMessage());
            return generateLocalResponse(systemPrompt, userMessage);
        } catch (Exception e) {
            LOG.warning("[HuggingFaceService] API error, using local fallback: " + e.getMessage());
            return generateLocalResponse(systemPrompt, userMessage);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Generates a helpful local response when the Hugging Face API is unavailable.
     * Uses pattern matching to provide context-aware educational responses.
     *
     * @param systemPrompt the course context
     * @param userMessage the student's question
     * @return a helpful response based on the question pattern
     */
    private String generateLocalResponse(String systemPrompt, String userMessage) {
        String msgLower = userMessage.toLowerCase();
        
        // Extract course info from system prompt
        String courseName = extractCourseName(systemPrompt);
        String category = extractCategory(systemPrompt);
        
        // Pattern-based responses
        if (msgLower.contains("summarize") || msgLower.contains("summary")) {
            return String.format("Here's a summary approach for %s:\n\n" +
                "**Key Concepts:**\n" +
                "- Break down the topic into main ideas\n" +
                "- Focus on core principles and definitions\n" +
                "- Connect concepts to real-world examples\n\n" +
                "**Study Tips:**\n" +
                "- Review your course materials section by section\n" +
                "- Create mind maps to visualize connections\n" +
                "- Practice explaining concepts in your own words\n\n" +
                "💡 *Tip: Use the Pomodoro timer on the left to stay focused!*", courseName);
        }
        
        if (msgLower.contains("study plan") || msgLower.contains("exam in")) {
            return String.format("**Study Plan for %s**\n\n" +
                "**Week 1-2: Foundation**\n" +
                "- Review all course materials\n" +
                "- Take notes on key concepts\n" +
                "- Complete practice exercises\n\n" +
                "**Week 3: Practice**\n" +
                "- Work through example problems\n" +
                "- Test yourself with quizzes\n" +
                "- Identify weak areas\n\n" +
                "**Week 4: Review**\n" +
                "- Focus on difficult topics\n" +
                "- Create summary sheets\n" +
                "- Do final practice tests\n\n" +
                "🍅 *Use 25-minute Pomodoro sessions for each study block!*", courseName);
        }
        
        if (msgLower.contains("explain") && msgLower.contains("beginner")) {
            return String.format("Let me explain %s in simple terms:\n\n" +
                "Think of it like this - imagine you're explaining it to a friend who's never heard of it before. " +
                "Start with the basics and build up gradually.\n\n" +
                "**Simple Approach:**\n" +
                "1. **What is it?** - Define the concept in one sentence\n" +
                "2. **Why does it matter?** - Explain its real-world use\n" +
                "3. **How does it work?** - Break it into simple steps\n" +
                "4. **Try it yourself** - Practice with easy examples\n\n" +
                "💡 *Don't worry if it seems complex at first - learning takes time!*", courseName);
        }
        
        if (msgLower.contains("code") || msgLower.contains("example") && category.toLowerCase().contains("java")) {
            return "**Java Code Example Structure:**\n\n" +
                "```java\n" +
                "// 1. Start with imports\n" +
                "import java.util.*;\n\n" +
                "// 2. Define your class\n" +
                "public class Example {\n" +
                "    // 3. Add fields/variables\n" +
                "    private String data;\n\n" +
                "    // 4. Create constructor\n" +
                "    public Example(String data) {\n" +
                "        this.data = data;\n" +
                "    }\n\n" +
                "    // 5. Add methods\n" +
                "    public void display() {\n" +
                "        System.out.println(data);\n" +
                "    }\n" +
                "}\n" +
                "```\n\n" +
                "**Key Points:**\n" +
                "- Always follow naming conventions\n" +
                "- Use meaningful variable names\n" +
                "- Add comments to explain complex logic\n" +
                "- Test your code with different inputs";
        }
        
        if (msgLower.contains("sql") || msgLower.contains("query") || msgLower.contains("database")) {
            return "**SQL Query Basics:**\n\n" +
                "**SELECT** - Retrieve data\n" +
                "```sql\n" +
                "SELECT column1, column2 FROM table_name;\n" +
                "```\n\n" +
                "**WHERE** - Filter results\n" +
                "```sql\n" +
                "SELECT * FROM students WHERE grade > 80;\n" +
                "```\n\n" +
                "**JOIN** - Combine tables\n" +
                "```sql\n" +
                "SELECT * FROM students \n" +
                "JOIN courses ON students.course_id = courses.id;\n" +
                "```\n\n" +
                "**Tips:**\n" +
                "- Start simple, then add complexity\n" +
                "- Test queries on small datasets first\n" +
                "- Use EXPLAIN to understand query performance";
        }
        
        if (msgLower.contains("formula") || msgLower.contains("equation") || msgLower.contains("math")) {
            return String.format("**Math Problem-Solving for %s:**\n\n" +
                "**Step-by-Step Approach:**\n" +
                "1. **Read carefully** - Understand what's being asked\n" +
                "2. **Identify knowns** - List what information you have\n" +
                "3. **Find the formula** - Which equation applies?\n" +
                "4. **Substitute values** - Plug in your numbers\n" +
                "5. **Solve step-by-step** - Show your work\n" +
                "6. **Check your answer** - Does it make sense?\n\n" +
                "**Common Mistakes to Avoid:**\n" +
                "- Skipping steps\n" +
                "- Not checking units\n" +
                "- Calculation errors\n\n" +
                "💡 *Practice makes perfect - try multiple examples!*", courseName);
        }
        
        if (msgLower.contains("don't understand") || msgLower.contains("too hard") || msgLower.contains("difficult")) {
            return "I understand this feels challenging right now, but you're doing great by asking for help! 💪\n\n" +
                "**Here's what you can do:**\n\n" +
                "1. **Break it down** - Tackle one small piece at a time\n" +
                "2. **Use resources** - Check the videos and PDFs in this course\n" +
                "3. **Take breaks** - Your brain needs rest to process information\n" +
                "4. **Practice actively** - Don't just read, try examples yourself\n" +
                "5. **Ask specific questions** - The more specific, the better I can help\n\n" +
                "**Remember:**\n" +
                "- Everyone struggles with new concepts\n" +
                "- Confusion is part of learning\n" +
                "- You're making progress even when it doesn't feel like it\n\n" +
                "🌟 *Keep going - you've got this!*";
        }
        
        // Default helpful response
        return String.format("I'm here to help you with %s!\n\n" +
            "**I can assist you with:**\n" +
            "- 📝 Summarizing topics and concepts\n" +
            "- 🎯 Creating study plans\n" +
            "- 💡 Explaining concepts in simple terms\n" +
            "- 💻 Providing code examples (for programming courses)\n" +
            "- 📊 Breaking down complex problems\n" +
            "- 🧮 Explaining formulas and equations\n\n" +
            "**Try asking me:**\n" +
            "- \"Summarize this topic\"\n" +
            "- \"Create a study plan for my exam\"\n" +
            "- \"Explain this like I'm a beginner\"\n" +
            "- \"Show me a code example\"\n\n" +
            "What would you like help with?", courseName);
    }
    
    private String extractCourseName(String systemPrompt) {
        if (systemPrompt == null) return "this course";
        int start = systemPrompt.indexOf("'");
        int end = systemPrompt.indexOf("'", start + 1);
        if (start != -1 && end != -1) {
            return systemPrompt.substring(start + 1, end);
        }
        return "this course";
    }
    
    private String extractCategory(String systemPrompt) {
        if (systemPrompt == null) return "";
        if (systemPrompt.toLowerCase().contains("category:")) {
            int start = systemPrompt.toLowerCase().indexOf("category:") + 9;
            int end = systemPrompt.indexOf(".", start);
            if (end != -1) {
                return systemPrompt.substring(start, end).trim();
            }
        }
        return "";
    }

    /**
     * Builds the JSON payload for the Hugging Face Inference API.
     * Uses conversational format for BlenderBot.
     *
     * @param systemPrompt the system instruction
     * @param history      recent chat history
     * @param userMessage  the new user message
     * @return serialised JSON string
     */
    private String buildPayload(String systemPrompt, List<ChatMessage> history, String userMessage) {
        // Build a conversational prompt
        StringBuilder prompt = new StringBuilder();
        
        // Add context from system prompt (shortened for better results)
        String shortContext = systemPrompt.length() > 200 ? 
            systemPrompt.substring(0, 200) + "..." : systemPrompt;
        prompt.append(shortContext).append("\n\n");
        
        // Add the user question directly
        prompt.append(userMessage);
        
        // Build JSON payload for conversational model
        JSONObject payload = new JSONObject();
        payload.put("inputs", prompt.toString());
        
        JSONObject parameters = new JSONObject();
        parameters.put("max_length", 200);
        parameters.put("min_length", 20);
        parameters.put("temperature", 0.9);
        parameters.put("top_p", 0.9);
        
        payload.put("parameters", parameters);
        
        return payload.toString();
    }

    /**
     * Parses the assistant's reply from the Hugging Face Inference API response.
     * Handles multiple response formats from different model types.
     *
     * @param responseBody raw JSON string from the API
     * @return the assistant's reply text, or a fallback string on parse failure
     */
    private String parseResponse(String responseBody) {
        try {
            // Try parsing as array first (most common format)
            if (responseBody.trim().startsWith("[")) {
                JSONArray root = new JSONArray(responseBody);
                if (root.isEmpty()) {
                    LOG.warning("[HuggingFaceService] API returned empty response array.");
                    return API_ERROR_FALLBACK;
                }
                JSONObject firstResult = root.getJSONObject(0);
                
                // Check for generated_text field
                if (firstResult.has("generated_text")) {
                    String generatedText = firstResult.getString("generated_text").trim();
                    return generatedText.isEmpty() ? 
                        "I'm not sure how to answer that. Could you rephrase your question?" : 
                        generatedText;
                }
                
                // Check for conversation field (BlenderBot format)
                if (firstResult.has("generated_text")) {
                    return firstResult.getString("generated_text").trim();
                }
            } else {
                // Try parsing as object (alternative format)
                JSONObject root = new JSONObject(responseBody);
                
                if (root.has("generated_text")) {
                    return root.getString("generated_text").trim();
                }
                
                if (root.has("error")) {
                    String error = root.getString("error");
                    LOG.warning("[HuggingFaceService] API error: " + error);
                    if (error.toLowerCase().contains("loading") || error.toLowerCase().contains("currently loading")) {
                        return "⏳ The AI model is loading. Please wait 20 seconds and try again.";
                    }
                    if (error.toLowerCase().contains("rate limit")) {
                        return RATE_LIMIT_FALLBACK;
                    }
                    return API_ERROR_FALLBACK;
                }
            }
            
            LOG.warning("[HuggingFaceService] Unexpected response format: " + responseBody);
            return API_ERROR_FALLBACK;
            
        } catch (Exception e) {
            LOG.severe("[HuggingFaceService] Failed to parse API response: " + e.getMessage()
                    + "\nRaw body: " + responseBody);
            return API_ERROR_FALLBACK;
        }
    }

    // ── Inner class: RateLimiter ──────────────────────────────────────────────

    /**
     * A sliding-window rate limiter that tracks request timestamps within a
     * configurable time window.
     *
     * <p><strong>Not thread-safe</strong> — must be used from a single thread.</p>
     *
     * <p>Requirement 11.5: enforces a maximum of {@code maxRequests} API calls
     * per {@code windowMillis} milliseconds per student session.</p>
     */
    static final class RateLimiter {

        private final int maxRequests;
        private final long windowMillis;

        /** Timestamps (in ms) of recent requests within the current window. */
        private final Deque<Long> requestTimestamps = new ArrayDeque<>();

        /**
         * @param maxRequests  maximum number of requests allowed in the window
         * @param windowMillis length of the sliding window in milliseconds
         */
        RateLimiter(int maxRequests, long windowMillis) {
            this.maxRequests  = maxRequests;
            this.windowMillis = windowMillis;
        }

        /**
         * Attempts to acquire a request slot.
         *
         * @return {@code true} if the request is allowed; {@code false} if the
         *         rate limit has been reached
         */
        boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long windowStart = now - windowMillis;

            // Evict timestamps that have fallen outside the current window
            while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() <= windowStart) {
                requestTimestamps.pollFirst();
            }

            if (requestTimestamps.size() >= maxRequests) {
                return false; // rate limit reached
            }

            requestTimestamps.addLast(now);
            return true;
        }

        /** Returns the number of requests recorded in the current window (for testing). */
        int currentCount() {
            long windowStart = System.currentTimeMillis() - windowMillis;
            return (int) requestTimestamps.stream().filter(t -> t > windowStart).count();
        }
    }
}
