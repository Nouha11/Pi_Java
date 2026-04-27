package services.gamification;

import utils.MyConnection;
import java.sql.*;

/**
 * Manages the game_content table — stores custom JSON data per game.
 * Mirrors the Symfony GameContent entity.
 *
 * JSON structure per type:
 *   PUZZLE  → {"word":"SYMFONY","hint":"A PHP framework"}
 *   MEMORY  → {"words":["Apple","Banana","Cherry","Date","Elderberry","Fig"]}
 *   TRIVIA  → {"topic":"History","questions":[{"question":"...","choices":["A","B","C","D"],"correct":2},...]}
 *   ARCADE  → {"sentences":["The quick brown fox...","Practice makes perfect."]}
 */
public class GameContentService {

    private final Connection conn = MyConnection.getInstance().getCnx();

    /** Save or update content for a game (upsert). */
    public void saveContent(int gameId, String jsonData) throws SQLException {
        String sql = "INSERT INTO game_content (game_id, data) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE data = VALUES(data)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gameId);
            ps.setString(2, jsonData);
            ps.executeUpdate();
        }
    }

    /** Load raw JSON data for a game, or null if none exists. */
    public String loadContent(int gameId) throws SQLException {
        String sql = "SELECT data FROM game_content WHERE game_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gameId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("data");
        }
        return null;
    }

    /** Delete content for a game. */
    public void deleteContent(int gameId) throws SQLException {
        String sql = "DELETE FROM game_content WHERE game_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, gameId);
            ps.executeUpdate();
        }
    }

    // ── Simple JSON builders (no external library needed) ─────────────────────

    /** Build JSON for PUZZLE: {"word":"WORD","hint":"hint text"} */
    public static String buildPuzzleJson(String word, String hint) {
        return "{\"word\":\"" + escape(word.toUpperCase()) + "\","
             + "\"hint\":\"" + escape(hint) + "\"}";
    }

    /**
     * Build JSON for MEMORY: {"words":["w1","w2","w3","w4","w5","w6"]}
     * @param wordsNewlineSeparated one word/emoji per line
     */
    public static String buildMemoryJson(String wordsNewlineSeparated) {
        String[] words = wordsNewlineSeparated.trim().split("\\r?\\n");
        StringBuilder sb = new StringBuilder("{\"words\":[");
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(words[i].trim())).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Build JSON for TRIVIA from a simple text format:
     * Each question block:
     *   Q: question text
     *   A: option1
     *   B: option2
     *   C: option3
     *   D: option4
     *   ANS: B   (letter of correct answer)
     *   ---
     */
    public static String buildTriviaJson(String topic, String questionsText) {
        StringBuilder sb = new StringBuilder("{\"topic\":\"" + escape(topic) + "\",\"questions\":[");
        String[] blocks = questionsText.trim().split("---");
        boolean first = true;
        for (String block : blocks) {
            block = block.trim();
            if (block.isEmpty()) continue;
            String[] lines = block.split("\\r?\\n");
            String question = "", a = "", b = "", c = "", d = "", ans = "0";
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("Q:"))   question = line.substring(2).trim();
                else if (line.startsWith("A:")) a = line.substring(2).trim();
                else if (line.startsWith("B:")) b = line.substring(2).trim();
                else if (line.startsWith("C:")) c = line.substring(2).trim();
                else if (line.startsWith("D:")) d = line.substring(2).trim();
                else if (line.startsWith("ANS:")) {
                    String letter = line.substring(4).trim().toUpperCase();
                    ans = switch (letter) { case "A" -> "0"; case "B" -> "1"; case "C" -> "2"; default -> "3"; };
                }
            }
            if (question.isEmpty()) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"question\":\"").append(escape(question)).append("\",")
              .append("\"choices\":[\"").append(escape(a)).append("\",\"")
              .append(escape(b)).append("\",\"").append(escape(c)).append("\",\"")
              .append(escape(d)).append("\"],")
              .append("\"correct\":").append(ans).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * Build JSON for ARCADE: {"sentences":["s1","s2","s3"]}
     * @param sentencesNewlineSeparated one sentence per line
     */
    public static String buildArcadeJson(String sentencesNewlineSeparated) {
        String[] sentences = sentencesNewlineSeparated.trim().split("\\r?\\n");
        StringBuilder sb = new StringBuilder("{\"sentences\":[");
        for (int i = 0; i < sentences.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(sentences[i].trim())).append("\"");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ── JSON parsers ──────────────────────────────────────────────────────────

    /** Extract a string field from JSON: {"key":"value"} → value */
    public static String extractString(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? null : json.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** Extract a JSON array as raw string: {"words":["a","b"]} → ["a","b"] */
    public static String extractArray(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\":[";
        int start = json.indexOf(search);
        if (start == -1) return null;
        start += search.length() - 1;
        int depth = 0, end = start;
        for (; end < json.length(); end++) {
            if (json.charAt(end) == '[') depth++;
            else if (json.charAt(end) == ']') { depth--; if (depth == 0) { end++; break; } }
        }
        return json.substring(start, end);
    }

    /** Parse a simple string array from JSON array string: ["a","b","c"] → String[] */
    public static String[] parseStringArray(String arrayJson) {
        if (arrayJson == null || arrayJson.equals("[]")) return new String[0];
        String inner = arrayJson.substring(1, arrayJson.length() - 1);
        String[] parts = inner.split("\",\"");
        String[] result = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = parts[i].replace("\"", "").replace("\\\"", "\"");
        }
        return result;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
