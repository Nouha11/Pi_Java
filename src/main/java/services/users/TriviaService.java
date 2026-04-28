package services.users;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class TriviaService {

    private static final String API_URL =
        "https://opentdb.com/api.php?amount=1&type=multiple&difficulty=easy&category=9";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    // ── Inner model ───────────────────────────────────────────────────────────

    public static class TriviaQuestion {
        public final String question;
        public final String correctAnswer;
        public final List<String> allAnswers; // shuffled

        public TriviaQuestion(String question, String correct, List<String> incorrect) {
            this.question      = decodeHtml(question);
            this.correctAnswer = decodeHtml(correct);
            List<String> answers = new ArrayList<>();
            answers.add(this.correctAnswer);
            for (String s : incorrect) answers.add(decodeHtml(s));
            Collections.shuffle(answers);
            this.allAnswers = Collections.unmodifiableList(answers);
        }
    }

    // ── Fetch ─────────────────────────────────────────────────────────────────

    /**
     * Fetches one trivia question. Returns null on failure.
     */
    public TriviaQuestion fetchQuestion() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;

            return parseJson(resp.body());
        } catch (Exception e) {
            System.err.println("[TriviaService] Failed to fetch: " + e.getMessage());
            return null;
        }
    }

    // ── Minimal JSON parser (no external library needed) ──────────────────────

    private TriviaQuestion parseJson(String json) {
        try {
            // Extract "question"
            String question = extractField(json, "question");
            // Extract "correct_answer"
            String correct  = extractField(json, "correct_answer");
            // Extract "incorrect_answers" array
            List<String> incorrect = extractArray(json, "incorrect_answers");

            if (question == null || correct == null || incorrect.isEmpty()) return null;
            return new TriviaQuestion(question, correct, incorrect);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractField(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    private List<String> extractArray(String json, String key) {
        List<String> list = new ArrayList<>();
        String search = "\"" + key + "\":[";
        int start = json.indexOf(search);
        if (start < 0) return list;
        start += search.length();
        int end = json.indexOf("]", start);
        if (end < 0) return list;
        String arr = json.substring(start, end);
        // Split by "," but respect quoted strings
        for (String part : arr.split(",")) {
            String val = part.trim().replaceAll("^\"|\"$", "");
            if (!val.isEmpty()) list.add(val);
        }
        return list;
    }

    // ── HTML entity decoder ───────────────────────────────────────────────────

    private static String decodeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&amp;",   "&")
            .replace("&lt;",    "<")
            .replace("&gt;",    ">")
            .replace("&quot;",  "\"")
            .replace("&#039;",  "'")
            .replace("&ldquo;", "\u201C")
            .replace("&rdquo;", "\u201D")
            .replace("&lsquo;", "\u2018")
            .replace("&rsquo;", "\u2019")
            .replace("&ndash;", "\u2013")
            .replace("&mdash;", "\u2014")
            .replace("&eacute;","e")
            .replace("&egrave;","e")
            .replace("&agrave;","a")
            .replace("&aacute;","a")
            .replace("&uuml;",  "u")
            .replace("&ouml;",  "o")
            .replace("&auml;",  "a")
            .replace("&ntilde;","n");
    }
}
