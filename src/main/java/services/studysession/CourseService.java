package services.studysession;

import models.studysession.Course;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CourseService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────
    //  VALIDATION
    // ─────────────────────────────────────────────

    /**
     * Returns null if valid, or an error message string if invalid.
     * forUpdate=true skips uniqueness check against own ID.
     */
    public String validate(Course c, boolean forUpdate) {
        if (c.getCourseName() == null || c.getCourseName().trim().isEmpty())
            return "Course name is required.";
        if (c.getCourseName().trim().length() < 3)
            return "Course name must be at least 3 characters.";
        if (c.getCourseName().trim().length() > 255)
            return "Course name cannot exceed 255 characters.";

        if (c.getCategory() == null || c.getCategory().trim().isEmpty())
            return "Category is required.";
        if (c.getCategory().trim().length() < 3)
            return "Category must be at least 3 characters.";

        if (c.getDifficulty() == null || c.getDifficulty().trim().isEmpty())
            return "Difficulty is required.";
        if (!List.of("BEGINNER","INTERMEDIATE","ADVANCED").contains(c.getDifficulty()))
            return "Difficulty must be BEGINNER, INTERMEDIATE, or ADVANCED.";

        if (c.getEstimatedDuration() <= 0)
            return "Estimated duration must be a positive number.";

        if (c.getProgress() < 0 || c.getProgress() > 100)
            return "Progress must be between 0 and 100.";

        if (c.getStatus() == null || c.getStatus().trim().isEmpty())
            return "Status is required.";
        if (!List.of("NOT_STARTED","IN_PROGRESS","COMPLETED").contains(c.getStatus()))
            return "Status must be NOT_STARTED, IN_PROGRESS, or COMPLETED.";

        if (c.getMaxStudents() != null && c.getMaxStudents() <= 0)
            return "Max students must be a positive number.";

        // Uniqueness: same name + category combo
        String uniqueError = checkUniqueness(c.getCourseName().trim(), c.getCategory().trim(),
                forUpdate ? c.getId() : -1);
        if (uniqueError != null) return uniqueError;

        return null; // valid
    }

    /**
     * Checks if a course with same name AND category already exists.
     * excludeId = -1 means no exclusion (create), otherwise exclude that ID (update).
     */
    private String checkUniqueness(String name, String category, int excludeId) {
        try {
            String sql = excludeId == -1
                    ? "SELECT COUNT(*) FROM course WHERE course_name=? AND category=?"
                    : "SELECT COUNT(*) FROM course WHERE course_name=? AND category=? AND id!=?";
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setString(1, name);
            ps.setString(2, category);
            if (excludeId != -1) ps.setInt(3, excludeId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getInt(1) > 0)
                return "A course with the same name and category already exists.";
        } catch (SQLException e) {
            System.err.println("Uniqueness check failed: " + e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────
    //  CRUD
    // ─────────────────────────────────────────────

    public void create(Course c) throws SQLException {
        String sql = "INSERT INTO course (course_name, description, difficulty, estimated_duration, " +
                "progress, status, created_at, category, max_students, is_published) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
        PreparedStatement ps = cnx.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
        ps.setString(1, c.getCourseName().trim());
        ps.setString(2, c.getDescription());
        ps.setString(3, c.getDifficulty());
        ps.setInt(4, c.getEstimatedDuration());
        ps.setInt(5, c.getProgress());
        ps.setString(6, c.getStatus());
        ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
        ps.setString(8, c.getCategory().trim());
        if (c.getMaxStudents() != null) ps.setInt(9, c.getMaxStudents());
        else ps.setNull(9, Types.INTEGER);
        ps.setBoolean(10, c.isPublished());
        ps.executeUpdate();

        ResultSet keys = ps.getGeneratedKeys();
        if (keys.next()) c.setId(keys.getInt(1));
    }

    public void update(Course c) throws SQLException {
        String sql = "UPDATE course SET course_name=?, description=?, difficulty=?, " +
                "estimated_duration=?, progress=?, status=?, category=?, " +
                "max_students=?, is_published=? WHERE id=?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setString(1, c.getCourseName().trim());
        ps.setString(2, c.getDescription());
        ps.setString(3, c.getDifficulty());
        ps.setInt(4, c.getEstimatedDuration());
        ps.setInt(5, c.getProgress());
        ps.setString(6, c.getStatus());
        ps.setString(7, c.getCategory().trim());
        if (c.getMaxStudents() != null) ps.setInt(8, c.getMaxStudents());
        else ps.setNull(8, Types.INTEGER);
        ps.setBoolean(9, c.isPublished());
        ps.setInt(10, c.getId());
        ps.executeUpdate();
    }

    public void delete(int id) throws SQLException {
        // Check for dependent plannings first
        PreparedStatement checkPs = cnx.prepareStatement(
                "SELECT COUNT(*) FROM planning WHERE course_id=?");
        checkPs.setInt(1, id);
        ResultSet rs = checkPs.executeQuery();
        if (rs.next() && rs.getInt(1) > 0)
            throw new SQLException("Cannot delete course: it has existing planning sessions.");

        PreparedStatement ps = cnx.prepareStatement("DELETE FROM course WHERE id=?");
        ps.setInt(1, id);
        ps.executeUpdate();
    }

    public void togglePublish(Course c) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(
                "UPDATE course SET is_published=? WHERE id=?");
        ps.setBoolean(1, !c.isPublished());
        ps.setInt(2, c.getId());
        ps.executeUpdate();
        c.setPublished(!c.isPublished());
    }

    // ─────────────────────────────────────────────
    //  QUERIES
    // ─────────────────────────────────────────────

    public List<Course> findAll() throws SQLException {
        return query("SELECT * FROM course ORDER BY created_at DESC", null, null, null);
    }

    public List<Course> findByFilters(String difficulty, String category, Boolean isPublished,
                                      String search) throws SQLException {
        StringBuilder sb = new StringBuilder("SELECT * FROM course WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (difficulty != null && !difficulty.isEmpty()) {
            sb.append(" AND difficulty=?"); params.add(difficulty);
        }
        if (category != null && !category.isEmpty()) {
            sb.append(" AND category=?"); params.add(category);
        }
        if (isPublished != null) {
            sb.append(" AND is_published=?"); params.add(isPublished);
        }
        if (search != null && !search.isEmpty()) {
            sb.append(" AND (course_name LIKE ? OR description LIKE ? OR category LIKE ?)");
            String like = "%" + search + "%";
            params.add(like); params.add(like); params.add(like);
        }
        sb.append(" ORDER BY created_at DESC");

        PreparedStatement ps = cnx.prepareStatement(sb.toString());
        for (int i = 0; i < params.size(); i++)
            ps.setObject(i + 1, params.get(i));

        return mapResultSet(ps.executeQuery());
    }

    public Course findById(int id) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement("SELECT * FROM course WHERE id=?");
        ps.setInt(1, id);
        List<Course> list = mapResultSet(ps.executeQuery());
        return list.isEmpty() ? null : list.get(0);
    }

    public List<String> findAllCategories() throws SQLException {
        List<String> cats = new ArrayList<>();
        ResultSet rs = cnx.createStatement().executeQuery(
                "SELECT DISTINCT category FROM course ORDER BY category");
        while (rs.next()) cats.add(rs.getString("category"));
        return cats;
    }

    /** Statistics: count by difficulty */
    public List<Object[]> countByDifficulty() throws SQLException {
        List<Object[]> stats = new ArrayList<>();
        ResultSet rs = cnx.createStatement().executeQuery(
                "SELECT difficulty, COUNT(*) as cnt FROM course GROUP BY difficulty");
        while (rs.next())
            stats.add(new Object[]{rs.getString("difficulty"), rs.getInt("cnt")});
        return stats;
    }

    /** Statistics: count by status */
    public List<Object[]> countByStatus() throws SQLException {
        List<Object[]> stats = new ArrayList<>();
        ResultSet rs = cnx.createStatement().executeQuery(
                "SELECT status, COUNT(*) as cnt FROM course GROUP BY status");
        while (rs.next())
            stats.add(new Object[]{rs.getString("status"), rs.getInt("cnt")});
        return stats;
    }

    // ─────────────────────────────────────────────
    //  MAPPING
    // ─────────────────────────────────────────────

    private List<Course> query(String sql, Object p1, Object p2, Object p3) throws SQLException {
        PreparedStatement ps = cnx.prepareStatement(sql);
        if (p1 != null) ps.setObject(1, p1);
        if (p2 != null) ps.setObject(2, p2);
        if (p3 != null) ps.setObject(3, p3);
        return mapResultSet(ps.executeQuery());
    }

    private List<Course> mapResultSet(ResultSet rs) throws SQLException {
        List<Course> list = new ArrayList<>();
        while (rs.next()) {
            Course c = new Course();
            c.setId(rs.getInt("id"));
            c.setCourseName(rs.getString("course_name"));
            c.setDescription(rs.getString("description"));
            c.setDifficulty(rs.getString("difficulty"));
            c.setEstimatedDuration(rs.getInt("estimated_duration"));
            c.setProgress(rs.getInt("progress"));
            c.setStatus(rs.getString("status"));
            Timestamp ts = rs.getTimestamp("created_at");
            c.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
            c.setCategory(rs.getString("category"));
            int mx = rs.getInt("max_students");
            c.setMaxStudents(rs.wasNull() ? null : mx);
            c.setPublished(rs.getBoolean("is_published"));
            list.add(c);
        }
        return list;
    }
}
