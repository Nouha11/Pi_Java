package services.ai;

import models.ai.ChatMessage;
import utils.MyConnection;
import utils.UserSession;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service layer for persisting and retrieving AI Study Assistant chat messages.
 *
 * <p>All operations validate that the {@code studentId} parameter matches the
 * currently authenticated user from {@link UserSession}. Any mismatch is treated
 * as a security violation: a warning is logged and an {@link IllegalStateException}
 * is thrown.
 *
 * <p>Uses {@code MyConnection.getInstance().getCnx()} and {@link PreparedStatement}
 * following the same pattern as {@code TutorAnalyticsDashboardController}.
 *
 * Requirements: 9.1, 9.2, 9.3, 14.3, 14.4
 */
public class ChatHistoryService {

    // ── Security validation ───────────────────────────────────────────────────

    /**
     * Validates that the given {@code studentId} matches the currently authenticated
     * user. Logs a security warning and throws {@link IllegalStateException} on mismatch.
     *
     * @param studentId the student ID to validate
     * @throws IllegalStateException if {@code studentId} does not match the session user
     * Requirements: 14.3, 14.4
     */
    private void validateSessionUser(int studentId) {
        int sessionUserId = UserSession.getInstance().getUserId();
        if (studentId != sessionUserId) {
            System.err.println("[SECURITY WARNING] ChatHistoryService: studentId=" + studentId
                    + " does not match authenticated userId=" + sessionUserId
                    + ". Operation rejected.");
            throw new IllegalStateException(
                    "Security violation: studentId does not match the authenticated user.");
        }
    }

    // ── save ─────────────────────────────────────────────────────────────────

    /**
     * Persists a {@link ChatMessage} to the {@code ai_chat_messages} table.
     *
     * <p>Validates that {@code msg.getStudentId()} matches the session user before
     * writing. Logs a security warning and throws {@link IllegalStateException} on
     * mismatch.
     *
     * @param msg the message to persist; must have a valid {@code studentId} and
     *            {@code courseId}
     * @throws IllegalStateException if the message's {@code studentId} does not match
     *                               the authenticated user
     * Requirements: 9.1, 14.3, 14.4
     */
    public void save(ChatMessage msg) {
        validateSessionUser(msg.getStudentId());

        String sql = "INSERT INTO ai_chat_messages "
                + "(student_id, course_id, role, message_text, is_favorite, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";

        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, msg.getStudentId());
                ps.setInt(2, msg.getCourseId());
                ps.setString(3, msg.getRole());
                ps.setString(4, msg.getMessageText());
                ps.setBoolean(5, msg.isFavorite());
                ps.setTimestamp(6, Timestamp.valueOf(
                        msg.getCreatedAt() != null ? msg.getCreatedAt() : LocalDateTime.now()));
                ps.executeUpdate();

                // Propagate the generated primary key back to the model
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        msg.setId(generatedKeys.getInt(1));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ChatHistoryService] save() failed: " + e.getMessage());
            throw new RuntimeException("Failed to save chat message.", e);
        }
    }

    // ── findLast50 ────────────────────────────────────────────────────────────

    /**
     * Retrieves the last 50 {@link ChatMessage}s for the given student and course,
     * ordered by {@code created_at} ascending (oldest first).
     *
     * <p>Validates that {@code studentId} matches the session user before querying.
     *
     * @param studentId the student whose history to retrieve
     * @param courseId  the course to scope the history to
     * @return an ordered list of up to 50 messages, oldest first
     * @throws IllegalStateException if {@code studentId} does not match the authenticated user
     * Requirements: 9.2, 14.3, 14.4
     */
    public List<ChatMessage> findLast50(int studentId, int courseId) {
        validateSessionUser(studentId);

        // Subquery selects the 50 most-recent rows; outer query re-orders them ASC
        String sql = "SELECT id, student_id, course_id, role, message_text, is_favorite, created_at "
                + "FROM ("
                + "  SELECT id, student_id, course_id, role, message_text, is_favorite, created_at "
                + "  FROM ai_chat_messages "
                + "  WHERE student_id = ? AND course_id = ? "
                + "  ORDER BY created_at DESC "
                + "  LIMIT 50"
                + ") AS last50 "
                + "ORDER BY created_at ASC";

        List<ChatMessage> messages = new ArrayList<>();

        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, courseId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        messages.add(mapRow(rs));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[ChatHistoryService] findLast50() failed: " + e.getMessage());
            throw new RuntimeException("Failed to retrieve chat history.", e);
        }

        return messages;
    }

    // ── clearHistory ──────────────────────────────────────────────────────────

    /**
     * Deletes all {@link ChatMessage}s for the given student and course.
     *
     * <p>Validates that {@code studentId} matches the session user before deleting.
     *
     * @param studentId the student whose history to clear
     * @param courseId  the course to scope the deletion to
     * @throws IllegalStateException if {@code studentId} does not match the authenticated user
     * Requirements: 9.3, 14.3, 14.4
     */
    public void clearHistory(int studentId, int courseId) {
        validateSessionUser(studentId);

        String sql = "DELETE FROM ai_chat_messages WHERE student_id = ? AND course_id = ?";

        try {
            Connection cnx = MyConnection.getInstance().getCnx();
            try (PreparedStatement ps = cnx.prepareStatement(sql)) {
                ps.setInt(1, studentId);
                ps.setInt(2, courseId);
                int deleted = ps.executeUpdate();
                System.out.println("[ChatHistoryService] clearHistory(): deleted " + deleted
                        + " messages for studentId=" + studentId + ", courseId=" + courseId);
            }
        } catch (SQLException e) {
            System.err.println("[ChatHistoryService] clearHistory() failed: " + e.getMessage());
            throw new RuntimeException("Failed to clear chat history.", e);
        }
    }

    // ── setFavorite ───────────────────────────────────────────────────────────

    /**
     * Updates the {@code is_favorite} flag for a specific message.
     *
     * <p>Validates ownership by first performing a SELECT to confirm that the message
     * with {@code messageId} belongs to {@code studentId}. If the message is not found
     * or does not belong to the student, the update is rejected.
     *
     * <p>Also validates that {@code studentId} matches the session user.
     *
     * @param messageId the primary key of the message to update
     * @param favorite  {@code true} to mark as favorite, {@code false} to unmark
     * @param studentId the student who owns the message (used for ownership check)
     * @throws IllegalStateException    if {@code studentId} does not match the authenticated user
     * @throws IllegalArgumentException if the message does not exist or is not owned by the student
     * Requirements: 14.3, 14.4
     */
    public void setFavorite(int messageId, boolean favorite, int studentId) {
        validateSessionUser(studentId);

        // Ownership check: verify the message belongs to this student
        String selectSql = "SELECT id FROM ai_chat_messages WHERE id = ? AND student_id = ?";
        try {
            Connection cnx = MyConnection.getInstance().getCnx();

            try (PreparedStatement selectPs = cnx.prepareStatement(selectSql)) {
                selectPs.setInt(1, messageId);
                selectPs.setInt(2, studentId);
                try (ResultSet rs = selectPs.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("[SECURITY WARNING] ChatHistoryService: setFavorite() "
                                + "rejected — messageId=" + messageId
                                + " not owned by studentId=" + studentId);
                        throw new IllegalArgumentException(
                                "Message not found or not owned by the current user.");
                    }
                }
            }

            // Ownership confirmed — perform the update
            String updateSql = "UPDATE ai_chat_messages SET is_favorite = ? WHERE id = ?";
            try (PreparedStatement updatePs = cnx.prepareStatement(updateSql)) {
                updatePs.setBoolean(1, favorite);
                updatePs.setInt(2, messageId);
                updatePs.executeUpdate();
            }

        } catch (SQLException e) {
            System.err.println("[ChatHistoryService] setFavorite() failed: " + e.getMessage());
            throw new RuntimeException("Failed to update favorite status.", e);
        }
    }

    // ── Row mapping ───────────────────────────────────────────────────────────

    /**
     * Maps the current row of a {@link ResultSet} to a {@link ChatMessage}.
     *
     * @param rs an open {@link ResultSet} positioned on the row to map
     * @return a fully populated {@link ChatMessage}
     * @throws SQLException if any column cannot be read
     */
    private ChatMessage mapRow(ResultSet rs) throws SQLException {
        int           id          = rs.getInt("id");
        int           studentId   = rs.getInt("student_id");
        int           courseId    = rs.getInt("course_id");
        String        role        = rs.getString("role");
        String        messageText = rs.getString("message_text");
        boolean       isFavorite  = rs.getBoolean("is_favorite");
        Timestamp     ts          = rs.getTimestamp("created_at");
        LocalDateTime createdAt   = ts != null ? ts.toLocalDateTime() : LocalDateTime.now();

        return new ChatMessage(id, studentId, courseId, role, messageText, isFavorite, createdAt);
    }
}
