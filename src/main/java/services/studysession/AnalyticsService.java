package services.studysession;

import utils.MyConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Service providing analytics queries for the Admin and Tutor dashboards.
 *
 * All methods declare {@code throws SQLException}; every query uses
 * {@code PreparedStatement} — no SQL string concatenation.
 *
 * Requirements: 2.4, 3.2, 4.3, 5.3, 6.2, 6.5, 8.6, 16.1, 16.5, 16.6
 */
public class AnalyticsService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────────────────────────────────
    //  5.2 — Session counts over time
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the number of study sessions per ISO week for the last {@code weeks} weeks.
     *
     * @param weeks number of past weeks to include (e.g. 12)
     * @return list of {@code [weekLabel: String, count: Integer]} rows,
     *         ordered chronologically (oldest first)
     * @throws SQLException on any database error
     * Requirements: 2.4
     */
    public List<Object[]> getSessionCountByWeek(int weeks) throws SQLException {
        String sql =
            "SELECT CONCAT(YEAR(started_at), '-W', LPAD(WEEK(started_at, 3), 2, '0')) AS week_label, " +
            "       COUNT(*) AS cnt " +
            "FROM study_session " +
            "WHERE started_at >= DATE_SUB(CURDATE(), INTERVAL ? WEEK) " +
            "GROUP BY YEAR(started_at), WEEK(started_at, 3) " +
            "ORDER BY YEAR(started_at), WEEK(started_at, 3)";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, weeks);
        ResultSet rs = ps.executeQuery();

        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new Object[]{rs.getString("week_label"), rs.getInt("cnt")});
        }
        return result;
    }

    /**
     * Returns the number of study sessions per calendar month for the last
     * {@code months} months.
     *
     * @param months number of past months to include (e.g. 12)
     * @return list of {@code [monthLabel: String, count: Integer]} rows,
     *         ordered chronologically (oldest first)
     * @throws SQLException on any database error
     * Requirements: 2.4
     */
    public List<Object[]> getSessionCountByMonth(int months) throws SQLException {
        String sql =
            "SELECT DATE_FORMAT(started_at, '%Y-%m') AS month_label, " +
            "       COUNT(*) AS cnt " +
            "FROM study_session " +
            "WHERE started_at >= DATE_SUB(CURDATE(), INTERVAL ? MONTH) " +
            "GROUP BY DATE_FORMAT(started_at, '%Y-%m') " +
            "ORDER BY DATE_FORMAT(started_at, '%Y-%m')";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, months);
        ResultSet rs = ps.executeQuery();

        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new Object[]{rs.getString("month_label"), rs.getInt("cnt")});
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5.3 — Session count by difficulty (admin-wide)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the distribution of study sessions across course difficulty levels
     * (admin-wide — all courses, all students).
     *
     * Joins: {@code study_session → planning → course} on {@code planning_id} /
     * {@code course_id}, then groups by {@code difficulty}.
     *
     * @return map keyed by difficulty value (e.g. "BEGINNER") → session count
     * @throws SQLException on any database error
     * Requirements: 3.2
     */
    public Map<String, Integer> getSessionCountByDifficulty() throws SQLException {
        String sql =
            "SELECT c.difficulty, COUNT(ss.id) AS cnt " +
            "FROM study_session ss " +
            "INNER JOIN planning p  ON ss.planning_id = p.id " +
            "INNER JOIN course   c  ON p.course_id    = c.id " +
            "GROUP BY c.difficulty";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        Map<String, Integer> result = new LinkedHashMap<>();
        while (rs.next()) {
            result.put(rs.getString("difficulty"), rs.getInt("cnt"));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5.4 — Time spent by course (admin-wide)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns total minutes studied per course, optionally filtered by a date range.
     *
     * When {@code from} and/or {@code to} are {@code null} the corresponding
     * date boundary is omitted from the WHERE clause.
     *
     * @param from inclusive start date (nullable — no lower bound if null)
     * @param to   inclusive end date   (nullable — no upper bound if null)
     * @return list of {@code [courseName: String, totalMinutes: Integer]} rows,
     *         ordered by total minutes DESC
     * @throws SQLException on any database error
     * Requirements: 4.3
     */
    public List<Object[]> getTimeSpentByCourse(LocalDate from, LocalDate to) throws SQLException {
        StringBuilder sb = new StringBuilder(
            "SELECT c.course_name AS course_name, " +
            "       SUM(ss.actual_duration) AS total_minutes " +
            "FROM study_session ss " +
            "INNER JOIN planning p ON ss.planning_id = p.id " +
            "INNER JOIN course   c ON p.course_id    = c.id " +
            "WHERE ss.actual_duration IS NOT NULL "
        );

        if (from != null) {
            sb.append("AND DATE(ss.started_at) >= ? ");
        }
        if (to != null) {
            sb.append("AND DATE(ss.started_at) <= ? ");
        }
        sb.append("GROUP BY c.course_name ORDER BY total_minutes DESC");

        PreparedStatement ps = cnx.prepareStatement(sb.toString());
        int idx = 1;
        if (from != null) ps.setDate(idx++, java.sql.Date.valueOf(from));
        if (to   != null) ps.setDate(idx,   java.sql.Date.valueOf(to));

        ResultSet rs = ps.executeQuery();
        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new Object[]{rs.getString("course_name"), rs.getInt("total_minutes")});
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5.5 — Average progress by period
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the average {@code progress_percentage} from {@code student_course_progress}
     * grouped by the requested time bucket.
     *
     * <p>Supported {@code period} values:
     * <ul>
     *   <li>{@code "DAILY"}   — last N days, grouped by date</li>
     *   <li>{@code "WEEKLY"}  — last N weeks, grouped by ISO week</li>
     *   <li>{@code "MONTHLY"} — last N months, grouped by year-month</li>
     * </ul>
     *
     * @param period one of {@code "DAILY"}, {@code "WEEKLY"}, {@code "MONTHLY"}
     * @param count  number of past periods to include
     * @return list of {@code [periodLabel: String, avgProgress: Double]} rows,
     *         ordered chronologically (oldest first)
     * @throws SQLException             on any database error
     * @throws IllegalArgumentException if {@code period} is not a recognised value
     * Requirements: 5.3
     */
    public List<Object[]> getAverageProgressByPeriod(String period, int count) throws SQLException {
        String labelExpr;
        String intervalExpr;
        String groupExpr;

        switch (period.toUpperCase()) {
            case "DAILY":
                labelExpr    = "DATE(last_activity_at)";
                intervalExpr = count + " DAY";
                groupExpr    = "DATE(last_activity_at)";
                break;
            case "WEEKLY":
                labelExpr    = "CONCAT(YEAR(last_activity_at), '-W', LPAD(WEEK(last_activity_at, 3), 2, '0'))";
                intervalExpr = count + " WEEK";
                groupExpr    = "YEAR(last_activity_at), WEEK(last_activity_at, 3)";
                break;
            case "MONTHLY":
                labelExpr    = "DATE_FORMAT(last_activity_at, '%Y-%m')";
                intervalExpr = count + " MONTH";
                groupExpr    = "DATE_FORMAT(last_activity_at, '%Y-%m')";
                break;
            default:
                throw new IllegalArgumentException(
                    "Unsupported period: '" + period + "'. Use DAILY, WEEKLY, or MONTHLY.");
        }

        // intervalExpr is built from a validated switch — safe to embed directly.
        String sql =
            "SELECT " + labelExpr + " AS period_label, " +
            "       AVG(progress_percentage) AS avg_progress " +
            "FROM student_course_progress " +
            "WHERE last_activity_at >= DATE_SUB(CURDATE(), INTERVAL " + intervalExpr + ") " +
            "GROUP BY " + groupExpr + " " +
            "ORDER BY " + groupExpr;

        PreparedStatement ps = cnx.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new Object[]{rs.getString("period_label"), rs.getDouble("avg_progress")});
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5.6 — Tutor performance stats
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Aggregates per-tutor performance metrics across all tutors in the system.
     *
     * <p>For each tutor the following are computed:
     * <ul>
     *   <li>enrolled students — distinct students with an ACCEPTED enrollment in any of the tutor's courses</li>
     *   <li>average completion rate — AVG(progress_percentage) from student_course_progress for the tutor's courses</li>
     *   <li>active course count — number of courses owned by the tutor</li>
     *   <li>average session duration — AVG(actual_duration) from study_session for sessions in the tutor's courses</li>
     * </ul>
     *
     * @return list of {@link TutorPerformanceRow}, one entry per tutor
     * @throws SQLException on any database error
     * Requirements: 6.2
     */
    public List<TutorPerformanceRow> getTutorPerformanceStats() throws SQLException {
        String sql =
            "SELECT " +
            "    u.username AS tutor_name, " +
            "    COUNT(DISTINCT er.student_id)          AS enrolled_students, " +
            "    COALESCE(AVG(scp.progress_percentage), 0) AS avg_completion, " +
            "    COUNT(DISTINCT c.id)                   AS active_courses, " +
            "    COALESCE(AVG(ss.actual_duration), 0)   AS avg_session_duration " +
            "FROM user u " +
            "INNER JOIN course c ON c.created_by_id = u.id " +
            "LEFT  JOIN enrollment_requests er  ON er.course_id = c.id AND er.status = 'ACCEPTED' " +
            "LEFT  JOIN student_course_progress scp ON scp.course_id = c.id " +
            "LEFT  JOIN planning p  ON p.course_id = c.id " +
            "LEFT  JOIN study_session ss ON ss.planning_id = p.id " +
            "WHERE u.role = 'ROLE_TUTOR' " +
            "GROUP BY u.id, u.username " +
            "ORDER BY enrolled_students DESC";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();

        List<TutorPerformanceRow> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new TutorPerformanceRow(
                rs.getString("tutor_name"),
                rs.getInt("enrolled_students"),
                rs.getDouble("avg_completion"),
                rs.getInt("active_courses"),
                rs.getDouble("avg_session_duration")
            ));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5.7 — Course popularity ranking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the top {@code limit} courses ranked by the number of accepted enrollments.
     *
     * @param limit maximum number of courses to return
     * @return list of {@code [courseName: String, enrolledCount: Integer]} rows,
     *         ordered by enrollment count DESC
     * @throws SQLException on any database error
     * Requirements: 6.5
     */
    public List<Object[]> getCoursePopularityRanking(int limit) throws SQLException {
        String sql =
            "SELECT c.course_name, COUNT(er.id) AS enrolled_count " +
            "FROM course c " +
            "LEFT JOIN enrollment_requests er ON er.course_id = c.id AND er.status = 'ACCEPTED' " +
            "GROUP BY c.id, c.course_name " +
            "ORDER BY enrolled_count DESC " +
            "LIMIT ?";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, limit);
        ResultSet rs = ps.executeQuery();

        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new Object[]{rs.getString("course_name"), rs.getInt("enrolled_count")});
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  5.8 — Tutor-scoped overloads
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the number of study sessions per ISO week for the last {@code weeks} weeks,
     * scoped to courses created by {@code tutorId}.
     *
     * @param weeks   number of past weeks to include
     * @param tutorId the tutor whose courses are queried
     * @return list of {@code [weekLabel: String, count: Integer]} rows,
     *         ordered chronologically (oldest first)
     * @throws SQLException on any database error
     * Requirements: 8.6
     */
    public List<Object[]> getSessionCountByWeek(int weeks, int tutorId) throws SQLException {
        String sql =
            "SELECT CONCAT(YEAR(ss.started_at), '-W', LPAD(WEEK(ss.started_at, 3), 2, '0')) AS week_label, " +
            "       COUNT(ss.id) AS cnt " +
            "FROM study_session ss " +
            "INNER JOIN planning p ON ss.planning_id = p.id " +
            "INNER JOIN course   c ON p.course_id    = c.id " +
            "WHERE c.created_by_id = ? " +
            "  AND ss.started_at >= DATE_SUB(CURDATE(), INTERVAL ? WEEK) " +
            "GROUP BY YEAR(ss.started_at), WEEK(ss.started_at, 3) " +
            "ORDER BY YEAR(ss.started_at), WEEK(ss.started_at, 3)";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ps.setInt(2, weeks);
        ResultSet rs = ps.executeQuery();

        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new Object[]{rs.getString("week_label"), rs.getInt("cnt")});
        }
        return result;
    }

    /**
     * Returns total minutes studied per course, optionally filtered by a date range,
     * scoped to courses created by {@code tutorId}.
     *
     * @param from     inclusive start date (nullable)
     * @param to       inclusive end date   (nullable)
     * @param tutorId  the tutor whose courses are queried
     * @return list of {@code [courseName: String, totalMinutes: Integer]} rows,
     *         ordered by total minutes DESC
     * @throws SQLException on any database error
     * Requirements: 8.6
     */
    public List<Object[]> getTimeSpentByCourse(LocalDate from, LocalDate to, int tutorId) throws SQLException {
        StringBuilder sb = new StringBuilder(
            "SELECT c.course_name AS course_name, " +
            "       SUM(ss.actual_duration) AS total_minutes " +
            "FROM study_session ss " +
            "INNER JOIN planning p ON ss.planning_id = p.id " +
            "INNER JOIN course   c ON p.course_id    = c.id " +
            "WHERE c.created_by_id = ? " +
            "  AND ss.actual_duration IS NOT NULL "
        );

        if (from != null) {
            sb.append("AND DATE(ss.started_at) >= ? ");
        }
        if (to != null) {
            sb.append("AND DATE(ss.started_at) <= ? ");
        }
        sb.append("GROUP BY c.course_name ORDER BY total_minutes DESC");

        PreparedStatement ps = cnx.prepareStatement(sb.toString());
        int idx = 1;
        ps.setInt(idx++, tutorId);
        if (from != null) ps.setDate(idx++, java.sql.Date.valueOf(from));
        if (to   != null) ps.setDate(idx,   java.sql.Date.valueOf(to));

        ResultSet rs = ps.executeQuery();
        List<Object[]> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new Object[]{rs.getString("course_name"), rs.getInt("total_minutes")});
        }
        return result;
    }

    /**
     * Returns the distribution of study sessions across course difficulty levels,
     * scoped to courses created by {@code tutorId}.
     *
     * @param tutorId the tutor whose courses are queried
     * @return map keyed by difficulty value → session count
     * @throws SQLException on any database error
     * Requirements: 8.6
     */
    public Map<String, Integer> getSessionCountByDifficulty(int tutorId) throws SQLException {
        String sql =
            "SELECT c.difficulty, COUNT(ss.id) AS cnt " +
            "FROM study_session ss " +
            "INNER JOIN planning p ON ss.planning_id = p.id " +
            "INNER JOIN course   c ON p.course_id    = c.id " +
            "WHERE c.created_by_id = ? " +
            "GROUP BY c.difficulty";

        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ResultSet rs = ps.executeQuery();

        Map<String, Integer> result = new LinkedHashMap<>();
        while (rs.next()) {
            result.put(rs.getString("difficulty"), rs.getInt("cnt"));
        }
        return result;
    }
}
