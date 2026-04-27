package services.ai;

import utils.MyConnection;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service layer for aggregating AI Study Assistant usage analytics.
 *
 * <p>Provides aggregated metrics (total message count, unique student count,
 * top keywords) from the {@code ai_chat_messages} table. Individual student
 * message text is never returned — only aggregated statistics are exposed.
 *
 * <p>Keyword extraction is performed in Java by fetching only {@code message_text}
 * from {@code USER} role messages, splitting on whitespace, lowercasing, and
 * filtering a predefined stop-word list.
 *
 * <p>Uses {@code MyConnection.getInstance().getCnx()} and {@link PreparedStatement}
 * following the same pattern as {@link ChatHistoryService}.
 *
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */
public class AiChatAnalyticsService {

    private static final Logger LOGGER = Logger.getLogger(AiChatAnalyticsService.class.getName());

    // ── Stop-word list ────────────────────────────────────────────────────────

    /** Words to exclude from keyword frequency analysis. */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "shall", "can", "need", "dare", "ought",
            "used", "to", "of", "in", "on", "at", "by", "for", "with", "about",
            "against", "between", "into", "through", "during", "before", "after",
            "above", "below", "from", "up", "down", "out", "off", "over", "under",
            "again", "further", "then", "once", "and", "but", "or", "nor", "so",
            "yet", "both", "either", "neither", "not", "only", "own", "same",
            "than", "too", "very", "just",
            "i", "me", "my", "myself", "we", "our", "ours", "ourselves",
            "you", "your", "yours", "yourself", "yourselves",
            "he", "him", "his", "himself", "she", "her", "hers", "herself",
            "it", "its", "itself", "they", "them", "their", "theirs", "themselves",
            "what", "which", "who", "whom", "this", "that", "these", "those",
            "how", "when", "where", "why",
            "all", "each", "every", "few", "more", "most", "other", "some", "such",
            "no", "nor", "not", "only", "same", "so", "than", "too", "very"
    ));

    // ── getCourseStats ────────────────────────────────────────────────────────

    /**
     * Returns aggregated statistics for a single course.
     *
     * <p>The returned map contains:
     * <ul>
     *   <li>{@code "totalMessages"} — {@code int}: total number of messages in the course</li>
     *   <li>{@code "uniqueStudents"} — {@code int}: number of distinct students who sent messages</li>
     *   <li>{@code "topKeywords"} — {@code List<String[]>}: top-10 keywords as {@code [keyword, count]} pairs</li>
     * </ul>
     *
     * <p>Individual message text is never included in the result.
     *
     * @param courseId the course to aggregate statistics for
     * @return a map of aggregated statistics; never {@code null}
     * Requirements: 12.1, 12.2
     */
    public Map<String, Object> getCourseStats(int courseId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Total message count for the course
        int totalMessages = 0;
        String countSql = "SELECT COUNT(*) AS total FROM ai_chat_messages WHERE course_id = ?";
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(countSql)) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalMessages = rs.getInt("total");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[AiChatAnalyticsService] getCourseStats() count failed: " + e.getMessage(), e);
        }

        // Unique student count for the course
        int uniqueStudents = 0;
        String studentSql = "SELECT COUNT(DISTINCT student_id) AS unique_students "
                + "FROM ai_chat_messages WHERE course_id = ?";
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(studentSql)) {
                ps.setInt(1, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        uniqueStudents = rs.getInt("unique_students");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[AiChatAnalyticsService] getCourseStats() uniqueStudents failed: " + e.getMessage(), e);
        }

        // Top-10 keywords extracted from USER messages
        List<String[]> topKeywords = getTopTopics(courseId, 10);

        stats.put("totalMessages", totalMessages);
        stats.put("uniqueStudents", uniqueStudents);
        stats.put("topKeywords", topKeywords);

        return stats;
    }

    // ── getAllCoursesStats ────────────────────────────────────────────────────

    /**
     * Returns aggregated statistics across all courses (for Admin use).
     *
     * <p>The returned map contains:
     * <ul>
     *   <li>{@code "totalMessages"} — {@code int}: total messages across all courses</li>
     *   <li>{@code "uniqueStudents"} — {@code int}: distinct students across all courses</li>
     *   <li>{@code "topKeywords"} — {@code List<String[]>}: top-10 keywords across all courses</li>
     *   <li>{@code "courseBreakdown"} — {@code List<Map<String,Object>>}: per-course stats,
     *       each map containing {@code courseId}, {@code courseName}, {@code totalMessages},
     *       {@code uniqueStudents}</li>
     * </ul>
     *
     * @return a map of aggregated statistics for all courses; never {@code null}
     * Requirements: 12.3
     */
    public Map<String, Object> getAllCoursesStats() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Total message count across all courses
        int totalMessages = 0;
        String countSql = "SELECT COUNT(*) AS total FROM ai_chat_messages";
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(countSql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        totalMessages = rs.getInt("total");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[AiChatAnalyticsService] getAllCoursesStats() count failed: " + e.getMessage(), e);
        }

        // Unique student count across all courses
        int uniqueStudents = 0;
        String studentSql = "SELECT COUNT(DISTINCT student_id) AS unique_students FROM ai_chat_messages";
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(studentSql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        uniqueStudents = rs.getInt("unique_students");
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[AiChatAnalyticsService] getAllCoursesStats() uniqueStudents failed: " + e.getMessage(), e);
        }

        // Top-10 keywords across all courses (courseId = -1 means all courses)
        List<String[]> topKeywords = getTopTopics(-1, 10);

        // Per-course breakdown
        List<Map<String, Object>> courseBreakdown = new ArrayList<>();
        String breakdownSql =
                "SELECT c.id AS course_id, c.course_name, "
                + "COUNT(m.id) AS total_messages, "
                + "COUNT(DISTINCT m.student_id) AS unique_students "
                + "FROM course c "
                + "LEFT JOIN ai_chat_messages m ON m.course_id = c.id "
                + "GROUP BY c.id, c.course_name "
                + "ORDER BY total_messages DESC";
        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(breakdownSql)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> courseEntry = new LinkedHashMap<>();
                        courseEntry.put("courseId", rs.getInt("course_id"));
                        courseEntry.put("courseName", rs.getString("course_name"));
                        courseEntry.put("totalMessages", rs.getInt("total_messages"));
                        courseEntry.put("uniqueStudents", rs.getInt("unique_students"));
                        courseBreakdown.add(courseEntry);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[AiChatAnalyticsService] getAllCoursesStats() breakdown failed: " + e.getMessage(), e);
        }

        stats.put("totalMessages", totalMessages);
        stats.put("uniqueStudents", uniqueStudents);
        stats.put("topKeywords", topKeywords);
        stats.put("courseBreakdown", courseBreakdown);

        return stats;
    }

    // ── getTopTopics ──────────────────────────────────────────────────────────

    /**
     * Returns the top {@code limit} keywords ranked by frequency from USER messages.
     *
     * <p>Keywords are extracted by fetching {@code message_text} from {@code USER} role
     * messages, splitting on whitespace, lowercasing, and filtering stop-words. Only
     * tokens with 2 or more characters are considered.
     *
     * <p>If {@code courseId} is {@code -1}, aggregates across all courses.
     *
     * @param courseId the course to scope the analysis to; use {@code -1} for all courses
     * @param limit    the maximum number of keyword entries to return
     * @return a list of {@code [keyword, count_as_string]} pairs ordered by frequency
     *         descending; never {@code null}
     * Requirements: 12.1, 12.4
     */
    public List<String[]> getTopTopics(int courseId, int limit) {
        // Fetch message_text from USER role messages only
        List<String> messageTexts = new ArrayList<>();

        String sql;
        if (courseId == -1) {
            sql = "SELECT message_text FROM ai_chat_messages WHERE role = 'USER'";
        } else {
            sql = "SELECT message_text FROM ai_chat_messages WHERE role = 'USER' AND course_id = ?";
        }

        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                if (courseId != -1) {
                    ps.setInt(1, courseId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String text = rs.getString("message_text");
                        if (text != null && !text.isEmpty()) {
                            messageTexts.add(text);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "[AiChatAnalyticsService] getTopTopics() query failed: " + e.getMessage(), e);
            return Collections.emptyList();
        }

        // Extract and count keywords in Java
        Map<String, Integer> frequencyMap = new LinkedHashMap<>();
        for (String text : messageTexts) {
            String[] tokens = text.toLowerCase().split("\\s+");
            for (String token : tokens) {
                // Strip leading/trailing punctuation
                String word = token.replaceAll("^[^a-z0-9]+|[^a-z0-9]+$", "");
                // Skip short tokens and stop-words
                if (word.length() < 2 || STOP_WORDS.contains(word)) {
                    continue;
                }
                frequencyMap.merge(word, 1, Integer::sum);
            }
        }

        // Sort by frequency descending, then alphabetically for ties
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(frequencyMap.entrySet());
        entries.sort((a, b) -> {
            int cmp = b.getValue().compareTo(a.getValue());
            return cmp != 0 ? cmp : a.getKey().compareTo(b.getKey());
        });

        // Build result list capped at limit
        List<String[]> result = new ArrayList<>();
        int count = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (count >= limit) break;
            result.add(new String[]{entry.getKey(), String.valueOf(entry.getValue())});
            count++;
        }

        return result;
    }
}
