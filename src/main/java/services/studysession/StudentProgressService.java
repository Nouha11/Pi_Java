package services.studysession;

import models.studysession.StudentProgress;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StudentProgressService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────
    //  5.1 — getOrCreate
    // ─────────────────────────────────────────────

    /**
     * Ensures a student_course_progress row exists for the given student/course pair.
     * Uses INSERT ... ON DUPLICATE KEY UPDATE (no-op) to avoid overwriting existing data.
     * Returns the current (or newly created) StudentProgress record.
     */
    public StudentProgress getOrCreate(int studentId, int courseId) throws SQLException {
        String sql = "INSERT INTO student_course_progress " +
                "(student_id, course_id, progress_percentage, total_minutes_studied, " +
                "pomodoro_cycles_completed, study_streak_days) " +
                "VALUES (?, ?, 0, 0, 0, 0) " +
                "ON DUPLICATE KEY UPDATE student_id = student_id";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, studentId);
        ps.setInt(2, courseId);
        ps.executeUpdate();
        return getProgress(studentId, courseId);
    }

    // ─────────────────────────────────────────────
    //  5.2 — incrementMinutes
    // ─────────────────────────────────────────────

    /**
     * Increments total_minutes_studied by {@code minutes}, increments progress_percentage
     * by {@code minutes} (capped at 100), updates last_activity_at, sets first_activity_at
     * via COALESCE, and applies streak logic computed in Java.
     */
    public void incrementMinutes(int studentId, int courseId, int minutes) throws SQLException {
        // Fetch current streak state first
        StudentProgress current = getProgress(studentId, courseId);

        LocalDate today = LocalDate.now();
        int newStreak;
        LocalDate newLastStreakDate;

        if (current != null && current.getLastStreakDate() != null) {
            LocalDate lastStreakDate = current.getLastStreakDate();
            if (lastStreakDate.equals(today.minusDays(1))) {
                // Last studied yesterday → extend streak
                newStreak = current.getStudyStreakDays() + 1;
                newLastStreakDate = today;
            } else if (lastStreakDate.equals(today)) {
                // Already studied today → no change
                newStreak = current.getStudyStreakDays();
                newLastStreakDate = today;
            } else {
                // Gap in streak → reset to 1
                newStreak = 1;
                newLastStreakDate = today;
            }
        } else {
            // No previous streak record → start at 1
            newStreak = 1;
            newLastStreakDate = today;
        }

        String sql = "INSERT INTO student_course_progress " +
                "(student_id, course_id, progress_percentage, total_minutes_studied, " +
                "last_activity_at, study_streak_days, last_streak_date, first_activity_at) " +
                "VALUES (?, ?, ?, ?, NOW(), ?, ?, NOW()) " +
                "ON DUPLICATE KEY UPDATE " +
                "progress_percentage   = LEAST(progress_percentage + ?, 100), " +
                "total_minutes_studied = total_minutes_studied + ?, " +
                "last_activity_at      = NOW(), " +
                "study_streak_days     = ?, " +
                "last_streak_date      = ?, " +
                "first_activity_at     = COALESCE(first_activity_at, NOW())";

        PreparedStatement ps = cnx.prepareStatement(sql);
        // INSERT values
        ps.setInt(1, studentId);
        ps.setInt(2, courseId);
        ps.setInt(3, minutes);                          // progress_percentage (initial insert)
        ps.setInt(4, minutes);                          // total_minutes_studied (initial insert)
        ps.setInt(5, newStreak);                        // study_streak_days (initial insert)
        ps.setDate(6, Date.valueOf(newLastStreakDate));  // last_streak_date (initial insert)
        // ON DUPLICATE KEY UPDATE values
        ps.setInt(7, minutes);                          // progress_percentage increment
        ps.setInt(8, minutes);                          // total_minutes_studied increment
        ps.setInt(9, newStreak);                        // study_streak_days
        ps.setDate(10, Date.valueOf(newLastStreakDate)); // last_streak_date
        ps.executeUpdate();
    }

    // ─────────────────────────────────────────────
    //  5.3 — incrementPomodoroCycles
    // ─────────────────────────────────────────────

    /**
     * Increments pomodoro_cycles_completed by {@code count} for the given student/course pair.
     */
    public void incrementPomodoroCycles(int studentId, int courseId, int count) throws SQLException {
        String sql = "INSERT INTO student_course_progress " +
                "(student_id, course_id, pomodoro_cycles_completed) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE pomodoro_cycles_completed = pomodoro_cycles_completed + ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, studentId);
        ps.setInt(2, courseId);
        ps.setInt(3, count);
        ps.setInt(4, count);
        ps.executeUpdate();
    }

    // ─────────────────────────────────────────────
    //  5.4 — getProgress
    // ─────────────────────────────────────────────

    /**
     * Returns the StudentProgress record for the given student/course pair, or null if not found.
     */
    public StudentProgress getProgress(int studentId, int courseId) throws SQLException {
        String sql = "SELECT * FROM student_course_progress WHERE student_id = ? AND course_id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, studentId);
        ps.setInt(2, courseId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return mapRow(rs);
        }
        return null;
    }

    // ─────────────────────────────────────────────
    //  5.5 — getProgressForCourse
    // ─────────────────────────────────────────────

    /**
     * Returns all StudentProgress records for a course, joining with the user table
     * to populate studentName, ordered by progress_percentage DESC.
     */
    public List<StudentProgress> getProgressForCourse(int courseId) throws SQLException {
        String sql = "SELECT scp.*, u.username AS student_name " +
                "FROM student_course_progress scp " +
                "JOIN user u ON scp.student_id = u.id " +
                "WHERE scp.course_id = ? " +
                "ORDER BY scp.progress_percentage DESC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, courseId);
        ResultSet rs = ps.executeQuery();
        List<StudentProgress> list = new ArrayList<>();
        while (rs.next()) {
            StudentProgress sp = mapRow(rs);
            sp.setStudentName(rs.getString("student_name"));
            list.add(sp);
        }
        return list;
    }

    // ─────────────────────────────────────────────
    //  MAPPING
    // ─────────────────────────────────────────────

    private StudentProgress mapRow(ResultSet rs) throws SQLException {
        StudentProgress sp = new StudentProgress();
        sp.setId(rs.getInt("id"));
        sp.setStudentId(rs.getInt("student_id"));
        sp.setCourseId(rs.getInt("course_id"));
        sp.setProgressPercentage(rs.getInt("progress_percentage"));
        sp.setTotalMinutesStudied(rs.getInt("total_minutes_studied"));
        sp.setPomodoroCyclesCompleted(rs.getInt("pomodoro_cycles_completed"));

        Timestamp lastActivity = rs.getTimestamp("last_activity_at");
        sp.setLastActivityAt(lastActivity != null ? lastActivity.toLocalDateTime() : null);

        sp.setStudyStreakDays(rs.getInt("study_streak_days"));

        Date lastStreakDate = rs.getDate("last_streak_date");
        sp.setLastStreakDate(lastStreakDate != null ? lastStreakDate.toLocalDate() : null);

        Timestamp firstActivity = rs.getTimestamp("first_activity_at");
        sp.setFirstActivityAt(firstActivity != null ? firstActivity.toLocalDateTime() : null);

        return sp;
    }
}
