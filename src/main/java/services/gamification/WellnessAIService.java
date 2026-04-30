package services.gamification;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Wellness AI Coach — uses HuggingFace to give personalized health advice
 * based on the student's current energy level and how they feel.
 *
 * Returns:
 *   - A short wellness tip (2-3 sentences)
 *   - A recommended mini game type: BREATHING | STRETCH | EYE | HYDRATION
 *   - A reason for the recommendation
 */
public class WellnessAIService {

    private static final String API_URL = "https://router.huggingface.co/v1/chat/completions";

    private static final String[] MODELS = {
        "Qwen/Qwen2.5-7B-Instruct-1M",
        "meta-llama/Llama-3.1-8B-Instruct",
        "mistralai/Mistral-7B-Instruct-v0.3"
    };

    public static class WellnessAdvice {
        public final String tip;           // 2-3 sentence wellness tip
        public final String gameType;      // BREATHING | STRETCH | EYE | HYDRATION
        public final String gameReason;    // why this game is recommended
        public final String urgency;       // LOW | MEDIUM | HIGH

        public WellnessAdvice(String tip, String gameType, String gameReason, String urgency) {
            this.tip        = tip;
            this.gameType   = gameType;
            this.gameReason = gameReason;
            this.urgency    = urgency;
        }
    }

    /**
     * Get personalized wellness advice based on energy level and user feeling.
     *
     * @param energyLevel  current energy 0-100
     * @param userFeeling  what the user typed ("I have a headache", "feeling tired", etc.)
     *                     can be empty — AI will base advice on energy alone
     * @return WellnessAdvice with tip + recommended game
     */
    public WellnessAdvice getAdvice(int energyLevel, String userFeeling) throws Exception {
        String apiKey = loadApiKey();
        if (apiKey == null || apiKey.isBlank())
            throw new Exception("Hugging Face API key not configured.");

        String prompt = buildPrompt(energyLevel, userFeeling);
        String sysMsg = "You are a student wellness coach. You give brief, practical health advice to students " +
                        "based on their energy level and how they feel. You always recommend one of four " +
                        "wellness exercises: BREATHING (for stress/anxiety/focus), STRETCH (for body tension/stiffness), " +
                        "EYE (for eye strain/screen fatigue), or HYDRATION (for dehydration/headache/fatigue). " +
                        "You respond ONLY in the exact JSON format requested. No extra text.";

        String responseBody = null;
        Exception lastError = null;
        for (String model : MODELS) {
            try {
                String body = buildRequest(model, sysMsg, prompt);
                responseBody = callApi(apiKey, body);
                break;
            } catch (Exception e) {
                lastError = e;
            }
        }
        if (responseBody == null) throw lastError != null ? lastError : new Exception("All models failed.");

        String content = extractContent(responseBody);
        if (content == null || content.isBlank())
            throw new Exception("No response from AI.");

        return parseAdvice(content, energyLevel, userFeeling);
    }

    // ── Prompt ────────────────────────────────────────────────────────────────

    private String buildPrompt(int energy, String feeling) {
        String energyDesc = energy >= 80 ? "high (feeling energetic)"
                          : energy >= 50 ? "moderate (slightly tired)"
                          : energy >= 20 ? "low (noticeably fatigued)"
                          :                "critically low (exhausted)";

        String feelingPart = (feeling != null && !feeling.isBlank())
            ? "The student says: \"" + feeling + "\""
            : "The student has not described how they feel.";

        return "A student is studying and needs a wellness exercise recommendation.\n\n" +
               "Current energy level: " + energy + "/100 (" + energyDesc + ")\n" +
               feelingPart + "\n\n" +
               "IMPORTANT: If energy is above 70, do NOT say the student needs a break. " +
               "Instead give a positive tip about maintaining their good energy.\n\n" +
               "EXERCISE SELECTION RULES — follow strictly:\n" +
               "- EYE: choose this if the student mentions eyes, vision, screen, blur, headache from screen, eye strain, eye pain, eye hurt, or has been studying for a long time\n" +
               "- HYDRATION: choose this if the student mentions thirst, dry mouth, headache, dehydration, dizziness, or energy is critically low\n" +
               "- STRETCH: choose this if the student mentions back pain, neck pain, shoulder pain, stiffness, body ache, sitting too long, or needs physical movement\n" +
               "- BREATHING: choose this ONLY if the student mentions stress, anxiety, panic, overwhelmed, or cannot focus mentally — NOT for physical symptoms\n\n" +
               "If the student mentions eye-related symptoms, you MUST choose EYE. Do not choose BREATHING for eye symptoms.\n\n" +
               "Respond ONLY with this exact JSON (no markdown, no extra text):\n" +
               "{\n" +
               "  \"tip\": \"[2-3 sentence personalized wellness tip]\",\n" +
               "  \"game\": \"[EYE or HYDRATION or STRETCH or BREATHING]\",\n" +
               "  \"reason\": \"[one sentence why this exercise helps right now]\",\n" +
               "  \"urgency\": \"[LOW or MEDIUM or HIGH]\"\n" +
               "}";
    }

    // ── Parser ────────────────────────────────────────────────────────────────

    private WellnessAdvice parseAdvice(String content, int energy, String userFeeling) {
        // Strip markdown code fences if present
        content = content.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        String tip      = extractJsonString(content, "tip");
        String game     = extractJsonString(content, "game");
        String reason   = extractJsonString(content, "reason");
        String urgency  = extractJsonString(content, "urgency");

        // ── Client-side keyword override — AI sometimes ignores explicit symptoms ──
        if (userFeeling != null && !userFeeling.isBlank()) {
            String f = userFeeling.toLowerCase();
            if (f.matches(".*\\b(eye|eyes|vision|screen|blur|sight|strain|eye.?hurt|eye.?pain|eye.?tired|eye.?sore).*"))
                game = "EYE";
            else if (f.matches(".*\\b(thirst|thirsty|dry|dehydrat|dizzy|dizziness|headache|head.?ache).*"))
                game = "HYDRATION";
            else if (f.matches(".*\\b(back|neck|shoulder|stiff|ache|pain|sore|sitting|posture|body).*"))
                game = "STRETCH";
            else if (f.matches(".*\\b(stress|anxious|anxiety|panic|overwhelm|focus|concentrate|breath).*"))
                game = "BREATHING";
        }

        // Fallbacks
        if (tip == null || tip.isBlank())
            tip = energy >= 80
                ? "Your energy is great! Keep up the good work. Stay hydrated and take short breaks every 25 minutes."
                : energy >= 50
                ? "Your energy is moderate. Consider a short wellness exercise to stay focused."
                : energy >= 20
                ? "Your energy is getting low. A quick exercise will help you refocus and restore energy."
                : "Your energy is critically low. Take a wellness break before continuing your studies.";

        if (game == null || !game.matches("BREATHING|STRETCH|EYE|HYDRATION"))
            game = energy <= 20 ? "BREATHING" : energy <= 50 ? "HYDRATION" : "STRETCH";

        if (reason == null || reason.isBlank())
            reason = "This exercise is recommended based on your current energy level.";

        if (urgency == null || !urgency.matches("LOW|MEDIUM|HIGH"))
            urgency = energy <= 20 ? "HIGH" : energy <= 50 ? "MEDIUM" : "LOW";

        return new WellnessAdvice(tip, game.toUpperCase().trim(), reason, urgency.toUpperCase().trim());
    }

    // ── HTTP ──────────────────────────────────────────────────────────────────

    private String buildRequest(String model, String sysMsg, String prompt) {
        String esc = prompt.replace("\\","\\\\").replace("\"","\\\"")
                           .replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
        String sys = sysMsg.replace("\\","\\\\").replace("\"","\\\"")
                           .replace("\n","\\n").replace("\r","\\r");
        return "{\"model\":\"" + model + "\"," +
               "\"messages\":[" +
                 "{\"role\":\"system\",\"content\":\"" + sys + "\"}," +
                 "{\"role\":\"user\",\"content\":\"" + esc + "\"}" +
               "]," +
               "\"max_tokens\":400," +
               "\"temperature\":0.4}";
    }

    private String callApi(String apiKey, String body) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(45_000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line).append("\n");
        }
        if (status == 401) throw new Exception("API key invalid.");
        if (status != 200) throw new Exception("API error " + status);
        return sb.toString();
    }

    private String extractContent(String responseBody) {
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
                    case 'n' -> { sb.append('\n'); idx += 2; }
                    case 't' -> { sb.append('\t'); idx += 2; }
                    case '"' -> { sb.append('"');  idx += 2; }
                    case '\\' -> { sb.append('\\'); idx += 2; }
                    case 'r' -> { sb.append('\r'); idx += 2; }
                    default  -> idx++;
                }
            } else if (c == '"') { break; }
            else { sb.append(c); idx++; }
        }
        return sb.toString().replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki == -1) return null;
        int colon = json.indexOf(":", ki + search.length());
        if (colon == -1) return null;
        int q1 = json.indexOf("\"", colon + 1);
        if (q1 == -1) return null;
        int q2 = q1 + 1;
        while (q2 < json.length()) {
            if (json.charAt(q2) == '"' && json.charAt(q2 - 1) != '\\') break;
            q2++;
        }
        return json.substring(q1 + 1, q2).replace("\\\"", "\"").replace("\\n", " ").trim();
    }

    private String loadApiKey() {
        String[] paths = {"config.properties", "config/api.properties"};
        for (String path : paths) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null) continue;
                Properties p = new Properties(); p.load(is);
                String val = p.getProperty("HUGGING_FACE_API_KEY");
                if (val != null && !val.isBlank()) return val.trim();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
