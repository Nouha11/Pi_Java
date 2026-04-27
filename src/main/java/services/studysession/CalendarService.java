package services.studysession;

import models.studysession.CalendarEvent;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for loading, saving, and validating CalendarEvents.
 *
 * <p>All write methods execute within a transaction (setAutoCommit(false) /
 * commit() / rollback()) to ensure atomicity. All methods declare
 * {@code throws SQLException}; exceptions are never swallowed silently.
 *
 * <p>Database connections are obtained via {@link utils.MyConnection}.
 *
 * Requirements: 10.1, 10.3, 11.2, 11.3, 11.4, 12.1, 12.2, 12.3, 12.4,
 *               13.1, 13.2, 13.3, 16.2, 16.4, 16.5, 16.6
 */
public class CalendarService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────────────────────────────────
    //  6.2 — getEventsForPeriod
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all CalendarEvents visible to the given user for the specified
     * date range, applying role-based scoping and optional filter predicates.
     *
     * <p>Role scoping rules:
     * <ul>
     *   <li>ROLE_STUDENT — own study sessions + planning records for enrolled courses</li>
     *   <li>ROLE_TUTOR   — all sessions in the tutor's courses + the tutor's own plannings</li>
     *   <li>ROLE_ADMIN   — all study sessions and planning records</li>
     * </ul>
     *
     * @param userId the logged-in user's id
     * @param role   one of {@code "ROLE_STUDENT"}, {@code "ROLE_TUTOR"}, {@code "ROLE_ADMIN"}
     * @param from   inclusive start of the period (must not be null)
     * @param to     inclusive end of the period (must not be null)
     * @param filter optional additional filter predicates; may be null (= no extra filtering)
     * @return combined list of CalendarEvents ordered by start time ascending
     * @throws SQLException on any database error
     * Requirements: 10.1, 10.3, 13.1, 13.2, 13.3
     */
    public List<CalendarEvent> getEventsForPeriod(int userId, String role,
                                                   LocalDate from, LocalDate to,
                                                   CalendarFilter filter) throws SQLException {
        List<CalendarEvent> events = new ArrayList<>();
        CalendarFilter f = (filter != null) ? filter : new CalendarFilter();

        events.addAll(fetchSessionEvents(userId, role, from, to, f));
        events.addAll(fetchPlanningEvents(userId, role, from, to, f));

        // Sort by start time ascending
        events.sort((a, b) -> a.getStart().compareTo(b.getStart()));
        return events;
    }

    // ── Session events ────────────────────────────────────────────────────────

    /**
     * Fetches study_session records as CalendarEvents, applying role scoping and filters.
     */
    private List<CalendarEvent> fetchSessionEvents(int userId, String role,
                                                    LocalDate from, LocalDate to,
                                                    CalendarFilter f) throws SQLException {
        StringBuilder sb = new StringBuilder(
            "SELECT ss.id, ss.user_id, ss.started_at, ss.ended_at, " +
            "       c.course_name AS course_name_cache, ss.mood AS notes, " +
            "       c.difficulty, " +
            "       COALESCE(p.course_id, 0) AS course_id " +
            "FROM study_session ss " +
            "LEFT JOIN planning p ON ss.planning_id = p.id " +
            "LEFT JOIN course   c ON p.course_id    = c.id " +
            "WHERE ss.started_at >= ? AND ss.started_at < ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(Timestamp.valueOf(from.atStartOfDay()));
        params.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));

        // Role scoping
        switch (role) {
            case "ROLE_STUDENT":
                sb.append("AND ss.user_id = ? ");
                params.add(userId);
                break;
            case "ROLE_TUTOR":
                // All sessions in courses created by this tutor
                sb.append("AND c.created_by_id = ? ");
                params.add(userId);
                break;
            case "ROLE_ADMIN":
                // No additional restriction
                break;
            default:
                // Fallback: own sessions only
                sb.append("AND ss.user_id = ? ");
                params.add(userId);
        }

        // Optional filter predicates
        if (f.getCourseId() != null) {
            sb.append("AND p.course_id = ? ");
            params.add(f.getCourseId());
        }
        if (f.getDifficulty() != null && !f.getDifficulty().isEmpty()) {
            sb.append("AND c.difficulty = ? ");
            params.add(f.getDifficulty());
        }
        if (f.getFrom() != null) {
            sb.append("AND DATE(ss.started_at) >= ? ");
            params.add(Date.valueOf(f.getFrom()));
        }
        if (f.getTo() != null) {
            sb.append("AND DATE(ss.started_at) <= ? ");
            params.add(Date.valueOf(f.getTo()));
        }
        if (f.getStudentId() != null) {
            sb.append("AND ss.user_id = ? ");
            params.add(f.getStudentId());
        }
        if (f.getTutorId() != null) {
            sb.append("AND c.created_by_id = ? ");
            params.add(f.getTutorId());
        }

        sb.append("ORDER BY ss.started_at ASC");

        PreparedStatement ps = cnx.prepareStatement(sb.toString());
        for (int i = 0; i < params.size(); i++) {
            setParam(ps, i + 1, params.get(i));
        }

        ResultSet rs = ps.executeQuery();
        List<CalendarEvent> list = new ArrayList<>();
        while (rs.next()) {
            Timestamp startTs = rs.getTimestamp("started_at");
            Timestamp endTs   = rs.getTimestamp("ended_at");
            LocalDateTime start = startTs != null ? startTs.toLocalDateTime() : null;
            LocalDateTime end   = endTs   != null ? endTs.toLocalDateTime()   : null;

            list.add(new CalendarEvent(
                rs.getInt("id"),
                "SESSION",
                rs.getString("course_name_cache"),
                start,
                end,
                rs.getString("difficulty"),
                null,                          // sessions have no planning status
                rs.getInt("user_id"),
                rs.getInt("course_id"),
                rs.getString("notes")
            ));
        }
        return list;
    }

    // ── Planning events ───────────────────────────────────────────────────────

    /**
     * Fetches planning records as CalendarEvents, applying role scoping and filters.
     */
    private List<CalendarEvent> fetchPlanningEvents(int userId, String role,
                                                     LocalDate from, LocalDate to,
                                                     CalendarFilter f) throws SQLException {
        StringBuilder sb = new StringBuilder(
            "SELECT p.id, p.course_id, c.course_name AS course_name_cache, p.title, " +
            "       p.scheduled_date, p.scheduled_time, p.planned_duration, " +
            "       p.status, c.difficulty " +
            "FROM planning p " +
            "LEFT JOIN course c ON p.course_id = c.id " +
            "WHERE p.scheduled_date >= ? AND p.scheduled_date <= ? "
        );

        List<Object> params = new ArrayList<>();
        params.add(Date.valueOf(from));
        params.add(Date.valueOf(to));

        // Role scoping
        switch (role) {
            case "ROLE_STUDENT":
                // Only plannings for courses the student is enrolled in (ACCEPTED)
                sb.append("AND p.course_id IN (" +
                          "  SELECT er.course_id FROM enrollment_requests er " +
                          "  WHERE er.student_id = ? AND er.status = 'ACCEPTED'" +
                          ") ");
                params.add(userId);
                break;
            case "ROLE_TUTOR":
                // Plannings for courses created by this tutor
                sb.append("AND c.created_by_id = ? ");
                params.add(userId);
                break;
            case "ROLE_ADMIN":
                // No restriction
                break;
            default:
                sb.append("AND p.course_id IN (" +
                          "  SELECT er.course_id FROM enrollment_requests er " +
                          "  WHERE er.student_id = ? AND er.status = 'ACCEPTED'" +
                          ") ");
                params.add(userId);
        }

        // Optional filter predicates
        if (f.getCourseId() != null) {
            sb.append("AND p.course_id = ? ");
            params.add(f.getCourseId());
        }
        if (f.getDifficulty() != null && !f.getDifficulty().isEmpty()) {
            sb.append("AND c.difficulty = ? ");
            params.add(f.getDifficulty());
        }
        if (f.getStatus() != null && !f.getStatus().isEmpty()) {
            sb.append("AND p.status = ? ");
            params.add(f.getStatus());
        }
        if (f.getFrom() != null) {
            sb.append("AND p.scheduled_date >= ? ");
            params.add(Date.valueOf(f.getFrom()));
        }
        if (f.getTo() != null) {
            sb.append("AND p.scheduled_date <= ? ");
            params.add(Date.valueOf(f.getTo()));
        }
        if (f.getTutorId() != null) {
            sb.append("AND c.created_by_id = ? ");
            params.add(f.getTutorId());
        }

        sb.append("ORDER BY p.scheduled_date ASC, p.scheduled_time ASC");

        PreparedStatement ps = cnx.prepareStatement(sb.toString());
        for (int i = 0; i < params.size(); i++) {
            setParam(ps, i + 1, params.get(i));
        }

        ResultSet rs = ps.executeQuery();
        List<CalendarEvent> list = new ArrayList<>();
        while (rs.next()) {
            java.sql.Date d = rs.getDate("scheduled_date");
            Time t          = rs.getTime("scheduled_time");
            int durationMin = rs.getInt("planned_duration");

            LocalDateTime start = null;
            LocalDateTime end   = null;
            if (d != null && t != null) {
                start = d.toLocalDate().atTime(t.toLocalTime());
                end   = start.plusMinutes(durationMin);
            }

            list.add(new CalendarEvent(
                rs.getInt("id"),
                "PLANNING",
                rs.getString("title"),
                start,
                end,
                rs.getString("difficulty"),
                rs.getString("status"),
                0,                             // planning records have no single userId
                rs.getInt("course_id"),
                null
            ));
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  6.3 — OverlapGuard
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Checks whether the proposed time range overlaps with any existing event
     * for the given user, excluding the event currently being moved/resized.
     *
     * <p>Queries both {@code study_session} and {@code planning} tables.
     * Two intervals [A,B] and [C,D] overlap when A < D AND C < B.
     *
     * @param userId      the user whose schedule is checked
     * @param newStart    proposed start of the new/moved event
     * @param newEnd      proposed end of the new/moved event
     * @param excludeId   id of the event being moved (excluded from the check)
     * @param excludeType {@code "SESSION"} or {@code "PLANNING"} — type of the excluded event
     * @throws OverlapException if an overlapping event is found
     * @throws SQLException     on any database error
     * Requirements: 12.1, 12.2
     */
    private void checkOverlap(int userId, LocalDateTime newStart, LocalDateTime newEnd,
                               int excludeId, String excludeType)
            throws OverlapException, SQLException {

        // Check study_session overlaps
        String sessionSql =
            "SELECT ss.id, c.course_name AS title, ss.started_at, ss.ended_at " +
            "FROM study_session ss " +
            "LEFT JOIN planning p ON ss.planning_id = p.id " +
            "LEFT JOIN course   c ON p.course_id    = c.id " +
            "WHERE ss.user_id = ? " +
            "  AND ss.started_at < ? " +
            "  AND ss.ended_at   > ? " +
            (excludeType.equals("SESSION") ? "  AND ss.id != ? " : "") +
            "LIMIT 1";

        PreparedStatement ps1 = cnx.prepareStatement(sessionSql);
        ps1.setInt(1, userId);
        ps1.setTimestamp(2, Timestamp.valueOf(newEnd));
        ps1.setTimestamp(3, Timestamp.valueOf(newStart));
        if (excludeType.equals("SESSION")) {
            ps1.setInt(4, excludeId);
        }
        ResultSet rs1 = ps1.executeQuery();
        if (rs1.next()) {
            String title = rs1.getString("title");
            Timestamp cs = rs1.getTimestamp("started_at");
            Timestamp ce = rs1.getTimestamp("ended_at");
            throw new OverlapException(
                title,
                cs != null ? cs.toLocalDateTime() : newStart,
                ce != null ? ce.toLocalDateTime() : newEnd
            );
        }

        // Check planning overlaps (for the same user via enrollment or ownership)
        // We check plannings where the user is the student enrolled in the course
        // or where the planning belongs to a course the user created (tutor).
        // For simplicity and correctness we check plannings linked to the user
        // via enrollment_requests (student) or course.created_by_id (tutor/admin).
        String planningSql =
            "SELECT p.id, p.title, " +
            "       TIMESTAMP(p.scheduled_date, p.scheduled_time) AS p_start, " +
            "       TIMESTAMPADD(MINUTE, p.planned_duration, TIMESTAMP(p.scheduled_date, p.scheduled_time)) AS p_end " +
            "FROM planning p " +
            "LEFT JOIN course c ON p.course_id = c.id " +
            "WHERE ( " +
            "    p.course_id IN (SELECT er.course_id FROM enrollment_requests er WHERE er.student_id = ? AND er.status = 'ACCEPTED') " +
            "    OR c.created_by_id = ? " +
            ") " +
            "  AND TIMESTAMP(p.scheduled_date, p.scheduled_time) < ? " +
            "  AND TIMESTAMPADD(MINUTE, p.planned_duration, TIMESTAMP(p.scheduled_date, p.scheduled_time)) > ? " +
            (excludeType.equals("PLANNING") ? "  AND p.id != ? " : "") +
            "LIMIT 1";

        PreparedStatement ps2 = cnx.prepareStatement(planningSql);
        ps2.setInt(1, userId);
        ps2.setInt(2, userId);
        ps2.setTimestamp(3, Timestamp.valueOf(newEnd));
        ps2.setTimestamp(4, Timestamp.valueOf(newStart));
        if (excludeType.equals("PLANNING")) {
            ps2.setInt(5, excludeId);
        }
        ResultSet rs2 = ps2.executeQuery();
        if (rs2.next()) {
            String title = rs2.getString("title");
            Timestamp cs = rs2.getTimestamp("p_start");
            Timestamp ce = rs2.getTimestamp("p_end");
            throw new OverlapException(
                title,
                cs != null ? cs.toLocalDateTime() : newStart,
                ce != null ? ce.toLocalDateTime() : newEnd
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  6.4 — rescheduleEvent
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reschedules a CalendarEvent to a new start time, shifting the end time
     * by the same delta to preserve the original duration.
     *
     * <p>Calls {@link #checkOverlap} before persisting; propagates
     * {@link OverlapException} to the caller on conflict.
     *
     * @param eventId  the id of the event to reschedule
     * @param type     {@code "SESSION"} or {@code "PLANNING"}
     * @param newStart the new start time
     * @throws OverlapException if the new time slot conflicts with an existing event
     * @throws SQLException     on any database error
     * Requirements: 11.3, 12.2, 16.4
     */
    public void rescheduleEvent(int eventId, String type, LocalDateTime newStart)
            throws OverlapException, SQLException {

        // Fetch current event to compute delta and userId
        CalendarEvent current = getEventDetail(eventId, type);
        if (current == null) {
            throw new SQLException("Event not found: id=" + eventId + ", type=" + type);
        }

        long deltaMinutes = ChronoUnit.MINUTES.between(current.getStart(), newStart);
        LocalDateTime newEnd = current.getEnd().plusMinutes(deltaMinutes);

        // Determine userId for overlap check
        int userId = resolveUserId(eventId, type);

        checkOverlap(userId, newStart, newEnd, eventId, type);

        boolean autoCommitBefore = cnx.getAutoCommit();
        cnx.setAutoCommit(false);
        try {
            if ("SESSION".equals(type)) {
                PreparedStatement ps = cnx.prepareStatement(
                    "UPDATE study_session SET started_at = ?, ended_at = ? WHERE id = ?");
                ps.setTimestamp(1, Timestamp.valueOf(newStart));
                ps.setTimestamp(2, Timestamp.valueOf(newEnd));
                ps.setInt(3, eventId);
                ps.executeUpdate();
            } else if ("PLANNING".equals(type)) {
                PreparedStatement ps = cnx.prepareStatement(
                    "UPDATE planning SET scheduled_date = ?, scheduled_time = ? WHERE id = ?");
                ps.setDate(1, Date.valueOf(newStart.toLocalDate()));
                ps.setTime(2, Time.valueOf(newStart.toLocalTime()));
                ps.setInt(3, eventId);
                ps.executeUpdate();
            } else {
                throw new IllegalArgumentException("Unknown event type: " + type);
            }
            cnx.commit();
        } catch (SQLException e) {
            cnx.rollback();
            throw e;
        } finally {
            cnx.setAutoCommit(autoCommitBefore);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  6.5 — resizeEvent
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resizes a CalendarEvent by setting a new end time.
     *
     * <p>Validates that the resulting duration is between 5 and 480 minutes
     * (inclusive). Calls {@link #checkOverlap} for the extended range before
     * persisting.
     *
     * @param eventId the id of the event to resize
     * @param type    {@code "SESSION"} or {@code "PLANNING"}
     * @param newEnd  the new end time
     * @throws IllegalArgumentException if the new duration is outside [5, 480] minutes
     * @throws OverlapException         if the resized event conflicts with an existing event
     * @throws SQLException             on any database error
     * Requirements: 11.4, 12.3, 16.4
     */
    public void resizeEvent(int eventId, String type, LocalDateTime newEnd)
            throws OverlapException, SQLException {

        CalendarEvent current = getEventDetail(eventId, type);
        if (current == null) {
            throw new SQLException("Event not found: id=" + eventId + ", type=" + type);
        }

        long newDurationMinutes = ChronoUnit.MINUTES.between(current.getStart(), newEnd);
        if (newDurationMinutes < 5) {
            throw new IllegalArgumentException(
                "Event duration must be at least 5 minutes (requested: " + newDurationMinutes + " min).");
        }
        if (newDurationMinutes > 480) {
            throw new IllegalArgumentException(
                "Event duration must not exceed 480 minutes / 8 hours (requested: " + newDurationMinutes + " min).");
        }

        int userId = resolveUserId(eventId, type);
        checkOverlap(userId, current.getStart(), newEnd, eventId, type);

        boolean autoCommitBefore = cnx.getAutoCommit();
        cnx.setAutoCommit(false);
        try {
            if ("SESSION".equals(type)) {
                PreparedStatement ps = cnx.prepareStatement(
                    "UPDATE study_session " +
                    "SET ended_at = ?, actual_duration = ? " +
                    "WHERE id = ?");
                ps.setTimestamp(1, Timestamp.valueOf(newEnd));
                ps.setInt(2, (int) newDurationMinutes);
                ps.setInt(3, eventId);
                ps.executeUpdate();
            } else if ("PLANNING".equals(type)) {
                PreparedStatement ps = cnx.prepareStatement(
                    "UPDATE planning SET planned_duration = ? WHERE id = ?");
                ps.setInt(1, (int) newDurationMinutes);
                ps.setInt(2, eventId);
                ps.executeUpdate();
            } else {
                throw new IllegalArgumentException("Unknown event type: " + type);
            }
            cnx.commit();
        } catch (SQLException e) {
            cnx.rollback();
            throw e;
        } finally {
            cnx.setAutoCommit(autoCommitBefore);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  6.6 — deleteEvent and getEventDetail
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Deletes a CalendarEvent from the database.
     *
     * <p>Wrapped in a transaction; rolls back on failure.
     *
     * @param eventId the id of the event to delete
     * @param type    {@code "SESSION"} or {@code "PLANNING"}
     * @throws SQLException on any database error
     * Requirements: 12.4, 16.2
     */
    public void deleteEvent(int eventId, String type) throws SQLException {
        String table;
        if ("SESSION".equals(type)) {
            table = "study_session";
        } else if ("PLANNING".equals(type)) {
            table = "planning";
        } else {
            throw new IllegalArgumentException("Unknown event type: " + type);
        }

        boolean autoCommitBefore = cnx.getAutoCommit();
        cnx.setAutoCommit(false);
        try {
            PreparedStatement ps = cnx.prepareStatement(
                "DELETE FROM " + table + " WHERE id = ?");
            ps.setInt(1, eventId);
            ps.executeUpdate();
            cnx.commit();
        } catch (SQLException e) {
            cnx.rollback();
            throw e;
        } finally {
            cnx.setAutoCommit(autoCommitBefore);
        }
    }

    /**
     * Retrieves the full details of a CalendarEvent by id and type.
     *
     * <p>For SESSION events all study_session fields are mapped; for PLANNING
     * events all planning fields are mapped. Returns {@code null} if no record
     * is found.
     *
     * @param eventId the id of the event
     * @param type    {@code "SESSION"} or {@code "PLANNING"}
     * @return a fully populated {@link CalendarEvent}, or {@code null} if not found
     * @throws SQLException on any database error
     * Requirements: 11.2, 16.2
     */
    public CalendarEvent getEventDetail(int eventId, String type) throws SQLException {
        if ("SESSION".equals(type)) {
            return getSessionDetail(eventId);
        } else if ("PLANNING".equals(type)) {
            return getPlanningDetail(eventId);
        } else {
            throw new IllegalArgumentException("Unknown event type: " + type);
        }
    }

    // ── Detail helpers ────────────────────────────────────────────────────────

    private CalendarEvent getSessionDetail(int eventId) throws SQLException {
        String sql =
            "SELECT ss.id, ss.user_id, ss.started_at, ss.ended_at, " +
            "       c.course_name AS course_name_cache, ss.mood AS notes, " +
            "       c.difficulty, " +
            "       COALESCE(p.course_id, 0) AS course_id " +
            "FROM study_session ss " +
            "LEFT JOIN planning p ON ss.planning_id = p.id " +
            "LEFT JOIN course   c ON p.course_id    = c.id " +
            "WHERE ss.id = ?";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, eventId);
        ResultSet rs = ps.executeQuery();

        if (!rs.next()) return null;

        Timestamp startTs = rs.getTimestamp("started_at");
        Timestamp endTs   = rs.getTimestamp("ended_at");

        return new CalendarEvent(
            rs.getInt("id"),
            "SESSION",
            rs.getString("course_name_cache"),
            startTs != null ? startTs.toLocalDateTime() : null,
            endTs   != null ? endTs.toLocalDateTime()   : null,
            rs.getString("difficulty"),
            null,
            rs.getInt("user_id"),
            rs.getInt("course_id"),
            rs.getString("notes")
        );
    }

    private CalendarEvent getPlanningDetail(int eventId) throws SQLException {
        String sql =
            "SELECT p.id, p.course_id, p.title, " +
            "       p.scheduled_date, p.scheduled_time, p.planned_duration, " +
            "       p.status, c.difficulty " +
            "FROM planning p " +
            "LEFT JOIN course c ON p.course_id = c.id " +
            "WHERE p.id = ?";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, eventId);
        ResultSet rs = ps.executeQuery();

        if (!rs.next()) return null;

        java.sql.Date d = rs.getDate("scheduled_date");
        Time t          = rs.getTime("scheduled_time");
        int durationMin = rs.getInt("planned_duration");

        LocalDateTime start = null;
        LocalDateTime end   = null;
        if (d != null && t != null) {
            start = d.toLocalDate().atTime(t.toLocalTime());
            end   = start.plusMinutes(durationMin);
        }

        return new CalendarEvent(
            rs.getInt("id"),
            "PLANNING",
            rs.getString("title"),
            start,
            end,
            rs.getString("difficulty"),
            rs.getString("status"),
            0,
            rs.getInt("course_id"),
            null
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolves the userId associated with an event for overlap checking.
     * For SESSION events this is the session's user_id.
     * For PLANNING events we use the course creator (tutor) as the owner.
     */
    private int resolveUserId(int eventId, String type) throws SQLException {
        if ("SESSION".equals(type)) {
            PreparedStatement ps = cnx.prepareStatement(
                "SELECT user_id FROM study_session WHERE id = ?");
            ps.setInt(1, eventId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("user_id");
            throw new SQLException("study_session not found: id=" + eventId);
        } else {
            PreparedStatement ps = cnx.prepareStatement(
                "SELECT c.created_by_id FROM planning p " +
                "LEFT JOIN course c ON p.course_id = c.id " +
                "WHERE p.id = ?");
            ps.setInt(1, eventId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("created_by_id");
            throw new SQLException("planning not found: id=" + eventId);
        }
    }

    /**
     * Sets a PreparedStatement parameter based on the runtime type of the value.
     */
    private void setParam(PreparedStatement ps, int idx, Object value) throws SQLException {
        if (value instanceof Integer) {
            ps.setInt(idx, (Integer) value);
        } else if (value instanceof String) {
            ps.setString(idx, (String) value);
        } else if (value instanceof Timestamp) {
            ps.setTimestamp(idx, (Timestamp) value);
        } else if (value instanceof Date) {
            ps.setDate(idx, (Date) value);
        } else if (value instanceof java.util.Date) {
            ps.setTimestamp(idx, new Timestamp(((java.util.Date) value).getTime()));
        } else {
            ps.setObject(idx, value);
        }
    }
}
