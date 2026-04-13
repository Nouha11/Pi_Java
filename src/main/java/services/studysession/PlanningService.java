package services.studysession;

import models.studysession.Planning;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class PlanningService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    public String validate(Planning p, boolean forUpdate) {
        if (p.getTitle() == null || p.getTitle().trim().isEmpty())
            return "Title is required.";
        if (p.getTitle().trim().length() < 3)
            return "Title must be at least 3 characters.";
        if (p.getTitle().trim().length() > 255)
            return "Title cannot exceed 255 characters.";

        if (p.getCourseId() <= 0)
            return "A course must be selected.";

        if (p.getScheduledDate() == null)
            return "Scheduled date is required.";
        if (!forUpdate && p.getScheduledDate().isBefore(LocalDate.now()))
            return "Scheduled date cannot be in the past.";

        if (p.getScheduledTime() == null)
            return "Scheduled time is required.";

        if (p.getPlannedDuration() <= 0)
            return "Planned duration must be a positive number (in minutes).";

        if (p.getStatus() == null || p.getStatus().trim().isEmpty())
            return "Status is required.";
        if (!List.of(Planning.STATUS_SCHEDULED, Planning.STATUS_COMPLETED,
                Planning.STATUS_MISSED, Planning.STATUS_CANCELLED).contains(p.getStatus()))
            return "Invalid status value.";

        // Uniqueness: same course + same date + same title
        String uniqueErr = checkUniqueness(p.getCourseId(), p.getTitle().trim(),
                p.getScheduledDate(), forUpdate ? p.getId() : -1);
        if (uniqueErr != null) return uniqueErr;

        return null;
    }

    private String checkUniqueness(int courseId, String title, LocalDate date, int excludeId) {
        try {
            String sql = excludeId == -1
                    ? "SELECT COUNT(*) FROM planning WHERE course_id=? AND title=? AND scheduled_date=?"
                    : "SELECT COUNT(*) FROM planning WHERE course_id=? AND title=? AND scheduled_date=? AND id!=?";
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setInt(1, courseId);
            ps.setString(2, title);
            ps.setDate(3, Date.valueOf(date));
            if (excludeId != -1) ps.setInt(4, excludeId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0)
                return "A planning session with the same title already exists for this course on that date.";
        } catch (SQLException e) {
            System.err.println("Uniqueness check failed: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────

    public void create(Planning p) throws SQLException {
        String sql = "INSERT INTO planning (course_id, title, scheduled_date, scheduled_time, " +
                "planned_duration, status, reminder, created_at) VALUES (?,?,?,?,?,?,?,?)";
        PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setInt(1, p.getCourseId());
        ps.setString(2, p.getTitle().trim());
        ps.setDate(3, Date.valueOf(p.getScheduledDate()));
        ps.setTime(4, Time.valueOf(p.getScheduledTime()));
        ps.setInt(5, p.getPlannedDuration());
        ps.setString(6, p.getStatus());
        ps.setBoolean(7, p.isReminder());
        ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) p.setId(keys.getInt(1));
    }

    public void update(Planning p) throws SQLException {
        String sql = "UPDATE planning SET course_id=?, title=?, scheduled_date=?, scheduled_time=?, " +
                "planned_duration=?, status=?, reminder=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, p.getCourseId());
        ps.setString(2, p.getTitle().trim());
        ps.setDate(3, Date.valueOf(p.getScheduledDate()));
        ps.setTime(4, Time.valueOf(p.getScheduledTime()));
        ps.setInt(5, p.getPlannedDuration());
        ps.setString(6, p.getStatus());
        ps.setBoolean(7, p.isReminder());
        ps.setInt(8, p.getId());
        ps.executeUpdate();
    }

    public void delete(int id) throws SQLException {
        // Check dependent study sessions
        PreparedStatement checkPs = cnx.prepareStatement(
                "SELECT COUNT(*) FROM study_session WHERE planning_id=?");
        checkPs.setInt(1, id);
        ResultSet rs = checkPs.executeQuery();
        if (rs.next() && rs.getInt(1) > 0)
            throw new SQLException("Cannot delete planning: it has existing study sessions.");

        PreparedStatement ps = cnx.prepareStatement("DELETE FROM planning WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    public void updateStatus(int id, String newStatus) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "UPDATE planning SET status=? WHERE id=?");
        ps.setString(1, newStatus);
        ps.setInt(2, id);
        ps.executeUpdate();
    }

    /** Auto-mark past SCHEDULED sessions as MISSED */
    public void autoMarkMissed() throws SQLException {
        String sql = "UPDATE planning SET status='MISSED' " +
                "WHERE status='SCHEDULED' AND " +
                "TIMESTAMP(scheduled_date, scheduled_time) < NOW()";
        cnx.createStatement().executeUpdate(sql);
    }

    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    public List<Planning> findAll() throws SQLException {
        return mapResultSet(cnx.createStatement().executeQuery(
                "SELECT p.*, c.course_name FROM planning p " +
                        "LEFT JOIN course c ON p.course_id=c.id " +
                        "ORDER BY p.scheduled_date DESC, p.scheduled_time DESC"));
    }

    public List<Planning> findByFilters(String status, LocalDate dateFrom, LocalDate dateTo,
                                        Integer courseId, String search) throws SQLException {
        StringBuilder sb = new StringBuilder(
                "SELECT p.*, c.course_name FROM planning p " +
                        "LEFT JOIN course c ON p.course_id=c.id WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            sb.append(" AND p.status=?"); params.add(status);
        }
        if (dateFrom != null) {
            sb.append(" AND p.scheduled_date>=?"); params.add(Date.valueOf(dateFrom));
        }
        if (dateTo != null) {
            sb.append(" AND p.scheduled_date<=?"); params.add(Date.valueOf(dateTo));
        }
        if (courseId != null) {
            sb.append(" AND p.course_id=?"); params.add(courseId);
        }
        if (search != null && !search.isEmpty()) {
            sb.append(" AND p.title LIKE ?"); params.add("%" + search + "%");
        }
        sb.append(" ORDER BY p.scheduled_date DESC, p.scheduled_time DESC");

        PreparedStatement ps = cnx.prepareStatement(sb.toString());
        for (int i = 0; i < params.size(); i++) ps.setObject(i + 1, params.get(i));
        return mapResultSet(ps.executeQuery());
    }

    public Planning findById(int id) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "SELECT p.*, c.course_name FROM planning p " +
                        "LEFT JOIN course c ON p.course_id=c.id WHERE p.id=?");
        ps.setInt(1, id);
        List<Planning> list = mapResultSet(ps.executeQuery());
        return list.isEmpty() ? null : list.get(0);
    }

    public List<Planning> findByCourse(int courseId) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "SELECT p.*, c.course_name FROM planning p " +
                        "LEFT JOIN course c ON p.course_id=c.id WHERE p.course_id=? " +
                        "ORDER BY p.scheduled_date DESC");
        ps.setInt(1, courseId);
        return mapResultSet(ps.executeQuery());
    }

    /** Count by status for stats */
    public List<Object[]> countByStatus() throws SQLException {
        List<Object[]> stats = new ArrayList<>();
        ResultSet rs = cnx.createStatement().executeQuery(
                "SELECT status, COUNT(*) as cnt FROM planning GROUP BY status");
        while (rs.next())
            stats.add(new Object[]{rs.getString("status"), rs.getInt("cnt")});
        return stats;
    }

    // ─────────────────────────────────────────────
    //  MAPPING
    // ─────────────────────────────────────────────

    private List<Planning> mapResultSet(ResultSet rs) throws SQLException {
        List<Planning> list = new ArrayList<>();
        while (rs.next()) {
            Planning p = new Planning();
            p.setId(rs.getInt("id"));
            p.setCourseId(rs.getInt("course_id"));
            p.setCourseNameCache(rs.getString("course_name"));
            p.setTitle(rs.getString("title"));
            Date d = rs.getDate("scheduled_date");
            p.setScheduledDate(d != null ? d.toLocalDate() : null);
            Time t = rs.getTime("scheduled_time");
            p.setScheduledTime(t != null ? t.toLocalTime() : null);
            p.setPlannedDuration(rs.getInt("planned_duration"));
            p.setStatus(rs.getString("status"));
            p.setReminder(rs.getBoolean("reminder"));
            Timestamp ts = rs.getTimestamp("created_at");
            p.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
            list.add(p);
        }
        return list;
    }
}
