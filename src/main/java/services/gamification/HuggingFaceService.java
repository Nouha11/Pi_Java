package services.gamification;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates trivia questions using Hugging Face API (Novita router).
 * Mirrors the Symfony HuggingFaceService.php logic exactly.
 *
 * API: https://router.huggingface.co/novita/v3/openai/chat/completions
 * Model: qwen/qwen2.5-7b-instruct
 *
 * Config key: HUGGING_FACE_API_KEY in config.properties
 */
public class HuggingFaceService {

    // Correct HuggingFace Router endpoint (OpenAI-compatible)
    private static final String API_URL = "https://router.huggingface.co/v1/chat/completions";

    // Models with their providers (format: "model-id:provider")
    // Try in order until one works
    private static final String[] MODELS = {
        "Qwen/Qwen2.5-7B-Instruct-1M",
        "meta-llama/Llama-3.1-8B-Instruct",
        "mistralai/Mistral-7B-Instruct-v0.3"
    };

    private static String getApiUrl(String model) {
        return API_URL;
    }

    /**
     * Takes a list of rough/misspelled word inputs from the admin,
     * corrects them, and returns the best matching Unicode emoji for each.
     *
     * Example: ["binana", "dag", "appel"] → ["🍌", "🐕", "🍎"]
     *
     * @param rawWords list of admin-typed words (may be misspelled)
     * @return list of emoji strings in the same order
     * @throws Exception if API call fails
     */
    public List<String> resolveMemoryEmojis(List<String> rawWords) throws Exception {
        String apiKey = loadApiKey();
        if (apiKey == null || apiKey.isBlank())
            throw new Exception("Hugging Face API key is not configured.");

        String wordList = String.join(", ", rawWords);
        String prompt = "You are an emoji resolver. For each word in the list below, " +
            "correct any spelling mistakes, understand what the word means, " +
            "and return the single best matching Unicode emoji for it.\n\n" +
            "Words: " + wordList + "\n\n" +
            "Rules:\n" +
            "- Return ONLY a comma-separated list of emojis, one per word, in the same order.\n" +
            "- No explanations, no text, no numbers — just emojis separated by commas.\n" +
            "- If a word is unclear, pick the closest reasonable emoji.\n\n" +
            "Example input: apple, dag, binana, kar\n" +
            "Example output: 🍎, 🐕, 🍌, 🚗\n\n" +
            "Now resolve: " + wordList + "\n" +
            "Output:";

        String requestBody = buildRequestBody(prompt, MODELS[0]);
        String responseBody = null;
        Exception lastError = null;

        for (String model : MODELS) {
            try {
                requestBody = buildRequestBody(prompt, model);
                responseBody = callApi(apiKey, getApiUrl(model), requestBody);
                break;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (responseBody == null) throw lastError != null ? lastError : new Exception("All models failed.");

        String content = extractContent(responseBody);
        if (content == null || content.isBlank())
            throw new Exception("No response from AI.");

        // Parse comma-separated emojis from response
        // Clean up any extra text — take only the first line that has emojis
        String[] lines = content.trim().split("\\n");
        String emojiLine = "";
        for (String line : lines) {
            line = line.trim();
            // Pick the line that looks like emoji output (contains emoji chars or commas)
            if (!line.isEmpty() && (line.contains(",") || line.codePointCount(0, line.length()) <= rawWords.size() * 3)) {
                emojiLine = line;
                break;
            }
        }
        if (emojiLine.isEmpty()) emojiLine = lines[0].trim();

        // Split by comma and clean each entry
        String[] parts = emojiLine.split(",");
        List<String> emojis = new ArrayList<>();
        for (int i = 0; i < rawWords.size(); i++) {
            if (i < parts.length) {
                String emoji = parts[i].trim();
                // Remove any surrounding text, keep only the first emoji character(s)
                emoji = extractFirstEmoji(emoji);
                emojis.add(emoji.isEmpty() ? "❓" : emoji);
            } else {
                emojis.add("❓");
            }
        }
        return emojis;
    }

    /** Extract the first emoji sequence from a string. */
    private String extractFirstEmoji(String s) {
        if (s == null || s.isEmpty()) return "";
        // Walk codepoints and collect the first emoji cluster
        StringBuilder sb = new StringBuilder();
        int[] cps = s.codePoints().toArray();
        boolean started = false;
        for (int cp : cps) {
            boolean isEmoji = (cp >= 0x1F300 && cp <= 0x1FAFF) // misc symbols, emoticons
                           || (cp >= 0x2600  && cp <= 0x27BF)  // misc symbols
                           || (cp >= 0x1F000 && cp <= 0x1F02F) // mahjong
                           || (cp >= 0x1F0A0 && cp <= 0x1F0FF) // playing cards
                           || (cp >= 0x1F100 && cp <= 0x1F1FF) // enclosed alphanumeric
                           || (cp >= 0x1F200 && cp <= 0x1F2FF) // enclosed CJK
                           || (cp == 0xFE0F)                   // variation selector
                           || (cp == 0x200D);                  // ZWJ
            if (isEmoji) { sb.appendCodePoint(cp); started = true; }
            else if (started) break; // stop after first emoji cluster
        }
        return sb.toString();
    }

    /**
     * Generate trivia questions.
     *
     * @param topic      topic for the questions
     * @param count      number of questions (1-10)
     * @param difficulty EASY | MEDIUM | HARD
     * @return list of TriviaQuestion objects
     * @throws Exception if API call fails or key is missing
     */
    public List<TriviaQuestion> generateTriviaQuestions(String topic, int count, String difficulty)
            throws Exception {

        String apiKey = loadApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new Exception("Hugging Face API key is not configured. " +
                    "Add HUGGING_FACE_API_KEY to config.properties.");
        }

        String prompt = buildPrompt(topic, count, difficulty);
        String requestBody = buildRequestBody(prompt, MODELS[0]);
        String responseBody = null;
        Exception lastError = null;

        // Try each model in order until one works
        for (String model : MODELS) {
            try {
                requestBody = buildRequestBody(prompt, model);
                responseBody = callApi(apiKey, getApiUrl(model), requestBody);
                break; // success
            } catch (Exception e) {
                lastError = e;
                System.err.println("[HuggingFace] Model " + model + " failed: " + e.getMessage() + " — trying next...");
            }
        }
        if (responseBody == null) throw lastError != null ? lastError : new Exception("All models failed.");
        String generatedText = extractContent(responseBody);

        if (generatedText == null || generatedText.isBlank()) {
            throw new Exception("No content generated by AI. Please try again.");
        }

        List<TriviaQuestion> questions = parseGeneratedQuestions(generatedText, count);
        if (questions.isEmpty()) {
            throw new Exception("Failed to parse generated questions. Please try again.");
        }
        return questions;
    }

    /** Test API connectivity — returns true if the key works. */
    public boolean testConnection() {
        String apiKey = loadApiKey();
        if (apiKey == null || apiKey.isBlank()) return false;
        try {
            String body = "{\"model\":\"" + MODELS[0] + "\",\"messages\":[{\"role\":\"user\",\"content\":\"Say hello\"}],\"max_tokens\":10}";
            String response = callApi(apiKey, getApiUrl(MODELS[0]), body);
            return response.contains("choices");
        } catch (Exception e) {
            System.err.println("[HuggingFace] Connection test failed: " + e.getMessage());
            return false;
        }
    }

    // ── Key loader — tries config.properties (root) then config/api.properties ─

    private String loadApiKey() {
        // Try paths in order: root config.properties, then config/api.properties
        String[] paths = {"config.properties", "config/api.properties"};
        for (String path : paths) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null) continue;
                Properties p = new Properties();
                p.load(is);
                String val = p.getProperty("HUGGING_FACE_API_KEY");
                if (val != null && !val.isBlank()) return val.trim();
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Prompt builder (mirrors PHP buildPrompt) ──────────────────────────────

    private String buildPrompt(String topic, int count, String difficulty) {
        String diffInstructions = switch (difficulty.toUpperCase()) {
            case "EASY" -> "Make the questions suitable for beginners. Use simple language and straightforward concepts.";
            case "HARD" -> "Make the questions challenging and detailed. Include advanced concepts and require deeper knowledge.";
            default     -> "Make the questions moderately challenging with clear but not overly simple concepts.";
        };

        return "Generate exactly " + count + " multiple choice trivia questions about \"" + topic + "\".\n\n" +
               "Difficulty Level: " + difficulty + "\n" +
               diffInstructions + "\n\n" +
               "For each question, provide:\n" +
               "1. The question text\n" +
               "2. Four answer choices (A, B, C, D)\n" +
               "3. The correct answer (indicate which letter is correct)\n\n" +
               "Format each question exactly like this:\n\n" +
               "Q1: [Question text here]\n" +
               "A) [First choice]\n" +
               "B) [Second choice]\n" +
               "C) [Third choice]\n" +
               "D) [Fourth choice]\n" +
               "Correct: [A/B/C/D]\n\n" +
               "Make the questions educational, clear, and appropriate for students. " +
               "Ensure one answer is clearly correct and the others are plausible but incorrect.\n\n" +
               "Begin generating the questions now:";
    }

    // ── HTTP call ─────────────────────────────────────────────────────────────

    private String buildRequestBody(String prompt, String model) {
        // OpenAI-compatible format for HuggingFace Router
        String escaped = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");

        return "{" +
               "\"model\":\"" + model + "\"," +
               "\"messages\":[{\"role\":\"user\",\"content\":\"" + escaped + "\"}]," +
               "\"max_tokens\":2000," +
               "\"temperature\":0.7" +
               "}";
    }

    private String callApi(String apiKey, String apiUrl, String requestBody) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(60_000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.getBytes(StandardCharsets.UTF_8));
        }

        int status = conn.getResponseCode();
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }

        String body = sb.toString();

        if (status == 401) throw new Exception("Hugging Face API key is invalid or expired.");
        if (status != 200) {
            String msg = extractJsonString(body, "error");
            throw new Exception("API error " + status + ": " + (msg != null ? msg : body));
        }
        return body;
    }

    private String extractContent(String responseBody) {
        // OpenAI-compatible response: choices[0].message.content
        String search = "\"content\":\"";
        int idx = responseBody.indexOf(search);
        if (idx == -1) return null;
        idx += search.length();
        StringBuilder sb = new StringBuilder();
        while (idx < responseBody.length()) {
            char c = responseBody.charAt(idx);
            if (c == '\\' && idx + 1 < responseBody.length()) {
                char next = responseBody.charAt(idx + 1);
                switch (next) {
                    case 'n'  -> { sb.append('\n'); idx += 2; }
                    case 't'  -> { sb.append('\t'); idx += 2; }
                    case '"'  -> { sb.append('"');  idx += 2; }
                    case '\\' -> { sb.append('\\'); idx += 2; }
                    case 'r'  -> { sb.append('\r'); idx += 2; }
                    case 'u'  -> {
                        // Unicode escape sequence (4 hex digits)
                        if (idx + 5 < responseBody.length()) {
                            try {
                                int cp = Integer.parseInt(responseBody.substring(idx + 2, idx + 6), 16);
                                sb.appendCodePoint(cp); idx += 6;
                            } catch (NumberFormatException e) { idx++; }
                        } else { idx++; }
                    }
                    default -> {
                        // Stray backslash (e.g. \to, \star) — skip the backslash, keep the char
                        idx++;
                    }
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c); idx++;
            }
        }
        // Post-process: clean up any remaining escape artifacts
        return sb.toString()
                 .replace("\\\"", "\"")   // leftover escaped quotes
                 .replace("\\'", "'")      // escaped apostrophes
                 .trim();
    }

    // ── Parser (mirrors PHP parseGeneratedQuestions) ──────────────────────────

    private List<TriviaQuestion> parseGeneratedQuestions(String text, int expectedCount) {
        List<TriviaQuestion> questions = new ArrayList<>();

        // Split by Q1:, Q2:, etc.
        Pattern p = Pattern.compile("Q\\d+:\\s*(.+?)(?=Q\\d+:|$)", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            TriviaQuestion q = parseQuestionBlock(m.group(1));
            if (q != null) questions.add(q);
        }

        // Fallback: try without Q prefix
        if (questions.isEmpty()) {
            questions = parseAlternativeFormat(text);
        }

        return questions.subList(0, Math.min(questions.size(), expectedCount));
    }

    private TriviaQuestion parseQuestionBlock(String block) {
        String[] lines = block.split("\\r?\\n");
        String questionText = null;
        List<String> choices = new ArrayList<>();
        int correctAnswer = -1;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            // Choice line: A) text
            Matcher choiceMatcher = Pattern.compile("^([A-D])\\)\\s*(.+)$", Pattern.CASE_INSENSITIVE).matcher(line);
            if (choiceMatcher.matches()) {
                choices.add(choiceMatcher.group(2).trim());
                continue;
            }

            // Correct answer line
            Matcher correctMatcher = Pattern.compile("^Correct:\\s*([A-D])", Pattern.CASE_INSENSITIVE).matcher(line);
            if (correctMatcher.find()) {
                correctAnswer = correctMatcher.group(1).toUpperCase().charAt(0) - 'A';
                continue;
            }

            // Question text (first non-choice, non-correct line)
            if (questionText == null) {
                questionText = line;
            }
        }

        if (questionText == null || choices.size() != 4 || correctAnswer < 0) return null;
        return new TriviaQuestion(questionText, choices, correctAnswer);
    }

    private List<TriviaQuestion> parseAlternativeFormat(String text) {
        // Try numbered questions without Q prefix
        List<TriviaQuestion> questions = new ArrayList<>();
        Pattern p = Pattern.compile("\\d+\\.\\s*(.+?)(?=\\d+\\.|$)", Pattern.DOTALL);
        Matcher m = p.matcher(text);
        while (m.find()) {
            TriviaQuestion q = parseQuestionBlock(m.group(1));
            if (q != null) questions.add(q);
        }
        return questions;
    }

    // ── Simple JSON string extractor ──────────────────────────────────────────

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end);
    }

    // ── Data class ────────────────────────────────────────────────────────────

    public static class TriviaQuestion {
        public final String question;
        public final List<String> choices;
        public final int correct; // 0-based index

        public TriviaQuestion(String question, List<String> choices, int correct) {
            this.question = question;
            this.choices  = choices;
            this.correct  = correct;
        }

        /**
         * Convert to the format used by GameContentService.buildTriviaJson:
         * Q: question
         * A: choice0
         * B: choice1
         * C: choice2
         * D: choice3
         * ANS: A/B/C/D
         */
        public String toFormFormat() {
            String[] letters = {"A", "B", "C", "D"};
            StringBuilder sb = new StringBuilder();
            sb.append("Q: ").append(question).append("\n");
            for (int i = 0; i < choices.size(); i++) {
                sb.append(letters[i]).append(": ").append(choices.get(i)).append("\n");
            }
            sb.append("ANS: ").append(letters[correct]);
            return sb.toString();
        }
    }
}
