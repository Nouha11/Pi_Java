package services.studysession;

import utils.MyConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service class for providing analytics data to tutors about their courses and enrolled students.
 * All queries are scoped to courses owned by the tutor (created_by_id = tutorId).
 */
public class TutorAnalyticsService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────
    //  ENROLLMENT METRICS
    // ─────────────────────────────────────────────

    /**
     * Gets the total number of distinct students enrolled in the tutor's courses.
     * Counts only ACCEPTED enrollments.
     * 
     * @param tutorId The ID of the tutor
     * @return Total count of enrolled students
     * @throws SQLException if a database error occurs
     */
    public int getTotalEnrolledStudents(int tutorId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT er.student_id) as total " +
                     "FROM enrollment_requests er " +
                     "INNER JOIN course c ON er.course_id = c.id " +
                     "WHERE c.created_by_id = ? AND er.status = 'ACCEPTED'";
        
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ResultSet rs = ps.executeQuery();
        
        if (rs.next()) {
            return rs.getInt("total");
        }
        return 0;
    }

    /**
     * Gets the number of active students (students with IN_PROGRESS study sessions).
     * Counts distinct students who have at least one IN_PROGRESS study session
     * linked to the tutor's courses.
     * 
     * @param tutorId The ID of the tutor
     * @return Count of active students
     * @throws SQLException if a database error occurs
     */
    public int getActiveStudents(int tutorId) throws SQLException {
        String sql = "SELECT COUNT(DISTINCT ss.user_id) as active " +
                     "FROM study_session ss " +
                     "INNER JOIN planning p ON ss.planning_id = p.id " +
                     "INNER JOIN course c ON p.course_id = c.id " +
                     "WHERE c.created_by_id = ? " +
                     "AND ss.completed_at IS NULL " +
                     "AND ss.started_at IS NOT NULL";
        
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ResultSet rs = ps.executeQuery();
        
        if (rs.next()) {
            return rs.getInt("active");
        }
        return 0;
    }

    /**
     * Calculates the completion rate for the tutor's courses.
     * Completion rate = (number of completed enrollments / total accepted enrollments) * 100
     * A completed enrollment is one where the course status is 'COMPLETED'.
     * 
     * @param tutorId The ID of the tutor
     * @return Completion rate as a percentage (0-100)
     * @throws SQLException if a database error occurs
     */
    public double getCompletionRate(int tutorId) throws SQLException {
        String sql = "SELECT " +
                     "COUNT(*) as total_accepted, " +
                     "SUM(CASE WHEN c.status = 'COMPLETED' THEN 1 ELSE 0 END) as completed " +
                     "FROM enrollment_requests er " +
                     "INNER JOIN course c ON er.course_id = c.id " +
                     "WHERE c.created_by_id = ? AND er.status = 'ACCEPTED'";
        
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ResultSet rs = ps.executeQuery();
        
        if (rs.next()) {
            int totalAccepted = rs.getInt("total_accepted");
            int completed = rs.getInt("completed");
            
            if (totalAccepted == 0) {
                return 0.0;
            }
            
            return (completed * 100.0) / totalAccepted;
        }
        return 0.0;
    }

    /**
     * Gets the total number of courses owned by the tutor.
     * 
     * @param tutorId The ID of the tutor
     * @return Total count of courses
     * @throws SQLException if a database error occurs
     */
    public int getTotalCoursesOwned(int tutorId) throws SQLException {
        String sql = "SELECT COUNT(*) as total FROM course WHERE created_by_id = ?";
        
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ResultSet rs = ps.executeQuery();
        
        if (rs.next()) {
            return rs.getInt("total");
        }
        return 0;
    }

    // ─────────────────────────────────────────────
    //  PER-COURSE ANALYTICS
    // ─────────────────────────────────────────────

    /**
     * Gets a breakdown of enrollment statistics for each course owned by the tutor.
     * Returns a list of maps, each containing: courseName, enrolledCount, and progress.
     * 
     * @param tutorId The ID of the tutor
     * @return List of course breakdown data
     * @throws SQLException if a database error occurs
     */
    public List<Map<String, Object>> getPerCourseBreakdown(int tutorId) throws SQLException {
        String sql = "SELECT " +
                     "c.course_name, " +
                     "c.progress, " +
                     "COUNT(er.id) as enrolled_count " +
                     "FROM course c " +
                     "LEFT JOIN enrollment_requests er ON c.id = er.course_id AND er.status = 'ACCEPTED' " +
                     "WHERE c.created_by_id = ? " +
                     "GROUP BY c.id, c.course_name, c.progress " +
                     "ORDER BY enrolled_count DESC";
        
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ResultSet rs = ps.executeQuery();
        
        List<Map<String, Object>> breakdown = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> courseData = new LinkedHashMap<>();
            courseData.put("courseName", rs.getString("course_name"));
            courseData.put("enrolledCount", rs.getInt("enrolled_count"));
            courseData.put("progress", rs.getInt("progress"));
            breakdown.add(courseData);
        }
        
        return breakdown;
    }

    /**
     * Gets the most popular course (highest enrollment count) for the tutor.
     * Returns a map containing: courseName and enrolledCount.
     * Returns null if the tutor has no courses.
     * 
     * @param tutorId The ID of the tutor
     * @return Map with course name and enrollment count, or null if no courses exist
     * @throws SQLException if a database error occurs
     */
    public Map<String, Object> getMostPopularCourse(int tutorId) throws SQLException {
        String sql = "SELECT " +
                     "c.course_name, " +
                     "COUNT(er.id) as enrolled_count " +
                     "FROM course c " +
                     "LEFT JOIN enrollment_requests er ON c.id = er.course_id AND er.status = 'ACCEPTED' " +
                     "WHERE c.created_by_id = ? " +
                     "GROUP BY c.id, c.course_name " +
                     "ORDER BY enrolled_count DESC " +
                     "LIMIT 1";
        
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, tutorId);
        ResultSet rs = ps.executeQuery();
        
        if (rs.next()) {
            Map<String, Object> popularCourse = new LinkedHashMap<>();
            popularCourse.put("courseName", rs.getString("course_name"));
            popularCourse.put("enrolledCount", rs.getInt("enrolled_count"));
            return popularCourse;
        }
        
        return null;
    }
}
