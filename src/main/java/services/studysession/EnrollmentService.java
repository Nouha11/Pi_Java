package services.studysession;

import models.studysession.EnrollmentRequest;
import utils.MyConnection;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service class for managing enrollment requests.
 * Handles CRUD operations for student enrollment in courses.
 */
public class EnrollmentService {

    private final Connection cnx = MyConnection.getInstance().getCnx();

    // ─────────────────────────────────────────────
    //  CREATE & UPDATE OPERATIONS
    // ─────────────────────────────────────────────

    /**
     * Creates a new enrollment request for a student to join a course.
     * 
     * @param courseId The ID of the course
     * @param studentId The ID of the student
     * @param message Optional message from the student
     * @throws IllegalStateException if an enrollment already exists for this course and student
     * @throws SQLException if a database error occurs
     */
    public void createRequest(int courseId, int studentId, String message) throws SQLException {
        // Check for existing enrollment
        String existingStatus = getEnrollmentStatus(courseId, studentId);
        if (existingStatus != null) {
            throw new IllegalStateException(
                "An enrollment request already exists for this course with status: " + existingStatus
            );
        }

        String sql = "INSERT INTO enrollment_requests (course_id, student_id, message, status, requested_at) " +
                     "VALUES (?, ?, ?, 'PENDING', NOW())";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, courseId);
        ps.setInt(2, studentId);
        if (message != null && !message.trim().isEmpty()) {
            ps.setString(3, message.trim());
        } else {
            ps.setNull(3, Types.VARCHAR);
        }
        ps.executeUpdate();
    }

    /**
     * Accepts an enrollment request.
     * Updates status to ACCEPTED, sets responded_at to current time, and records who responded.
     * 
     * @param requestId The ID of the enrollment request
     * @param respondedById The ID of the user accepting the request
     * @throws SQLException if a database error occurs
     */
    public void acceptRequest(int requestId, int respondedById) throws SQLException {
        boolean autoCommit = cnx.getAutoCommit();
        try {
            cnx.setAutoCommit(false);
            
            String sql = "UPDATE enrollment_requests " +
                        "SET status='ACCEPTED', responded_at=NOW(), responded_by_id=? " +
                        "WHERE id=?";
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setInt(1, respondedById);
            ps.setInt(2, requestId);
            ps.executeUpdate();
            
            cnx.commit();
        } catch (SQLException e) {
            cnx.rollback();
            throw e;
        } finally {
            cnx.setAutoCommit(autoCommit);
        }
    }

    /**
     * Rejects an enrollment request.
     * Updates status to REJECTED, sets responded_at to current time, and records who responded.
     * 
     * @param requestId The ID of the enrollment request
     * @param respondedById The ID of the user rejecting the request
     * @throws SQLException if a database error occurs
     */
    public void rejectRequest(int requestId, int respondedById) throws SQLException {
        boolean autoCommit = cnx.getAutoCommit();
        try {
            cnx.setAutoCommit(false);
            
            String sql = "UPDATE enrollment_requests " +
                        "SET status='REJECTED', responded_at=NOW(), responded_by_id=? " +
                        "WHERE id=?";
            PreparedStatement ps = cnx.prepareStatement(sql);
            ps.setInt(1, respondedById);
            ps.setInt(2, requestId);
            ps.executeUpdate();
            
            cnx.commit();
        } catch (SQLException e) {
            cnx.rollback();
            throw e;
        } finally {
            cnx.setAutoCommit(autoCommit);
        }
    }

    // ─────────────────────────────────────────────
    //  QUERY OPERATIONS
    // ─────────────────────────────────────────────

    /**
     * Finds all enrollment requests for a specific course.
     * Includes student username via JOIN with user table.
     * 
     * @param courseId The ID of the course
     * @return List of enrollment requests with student names populated
     * @throws SQLException if a database error occurs
     */
    public List<EnrollmentRequest> findByCourse(int courseId) throws SQLException {
        String sql = "SELECT er.*, u.username AS studentName " +
                     "FROM enrollment_requests er " +
                     "LEFT JOIN user u ON er.student_id = u.id " +
                     "WHERE er.course_id = ? " +
                     "ORDER BY er.requested_at DESC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, courseId);
        return mapResultSet(ps.executeQuery());
    }

    /**
     * Finds all enrollment requests for a specific student.
     * Includes course name via JOIN with course table.
     * 
     * @param studentId The ID of the student
     * @return List of enrollment requests with course names populated
     * @throws SQLException if a database error occurs
     */
    public List<EnrollmentRequest> findByStudent(int studentId) throws SQLException {
        String sql = "SELECT er.*, c.course_name AS courseName " +
                     "FROM enrollment_requests er " +
                     "LEFT JOIN course c ON er.course_id = c.id " +
                     "WHERE er.student_id = ? " +
                     "ORDER BY er.requested_at DESC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, studentId);
        return mapResultSet(ps.executeQuery());
    }

    /**
     * Gets the enrollment status for a specific course and student combination.
     * 
     * @param courseId The ID of the course
     * @param studentId The ID of the student
     * @return The status string (PENDING/ACCEPTED/REJECTED) or null if no record exists
     * @throws SQLException if a database error occurs
     */
    public String getEnrollmentStatus(int courseId, int studentId) throws SQLException {
        String sql = "SELECT status FROM enrollment_requests " +
                     "WHERE course_id = ? AND student_id = ?";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, courseId);
        ps.setInt(2, studentId);
        ResultSet rs = ps.executeQuery();
        
        if (rs.next()) {
            return rs.getString("status");
        }
        return null;
    }

    /**
     * Gets enrollment statuses for multiple courses for a specific student in a single batch query.
     * 
     * @param courseIds List of course IDs to check
     * @param studentId The ID of the student
     * @return Map of courseId to status string (PENDING/ACCEPTED/REJECTED)
     * @throws SQLException if a database error occurs
     */
    public java.util.Map<Integer, String> getBatchEnrollmentStatuses(List<Integer> courseIds, int studentId) throws SQLException {
        java.util.Map<Integer, String> statusMap = new java.util.HashMap<>();
        
        if (courseIds == null || courseIds.isEmpty()) {
            return statusMap;
        }
        
        // Build IN clause with placeholders
        StringBuilder sql = new StringBuilder(
            "SELECT course_id, status FROM enrollment_requests " +
            "WHERE student_id = ? AND course_id IN ("
        );
        for (int i = 0; i < courseIds.size(); i++) {
            sql.append("?");
            if (i < courseIds.size() - 1) {
                sql.append(",");
            }
        }
        sql.append(")");
        
        PreparedStatement ps = cnx.prepareStatement(sql.toString());
        ps.setInt(1, studentId);
        for (int i = 0; i < courseIds.size(); i++) {
            ps.setInt(i + 2, courseIds.get(i));
        }
        
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            statusMap.put(rs.getInt("course_id"), rs.getString("status"));
        }
        
        return statusMap;
    }

    /**
     * Finds all pending enrollment requests for courses owned by a specific creator (tutor).
     * Includes student username and course name via JOINs.
     * 
     * @param creatorId The ID of the course creator (tutor)
     * @return List of pending enrollment requests for the creator's courses
     * @throws SQLException if a database error occurs
     */
    public List<EnrollmentRequest> findPendingByCreator(int creatorId) throws SQLException {
        String sql = "SELECT er.*, u.username AS studentName, c.course_name AS courseName " +
                     "FROM enrollment_requests er " +
                     "INNER JOIN course c ON er.course_id = c.id " +
                     "LEFT JOIN user u ON er.student_id = u.id " +
                     "WHERE c.created_by_id = ? AND er.status = 'PENDING' " +
                     "ORDER BY er.requested_at DESC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        ps.setInt(1, creatorId);
        return mapResultSet(ps.executeQuery());
    }

    /**
     * Finds all enrollment records across the platform (admin query).
     * Includes student name, course name, and creator name via JOINs.
     * Supports filtering by status and course name search.
     * 
     * @return List of all enrollment requests with enriched data
     * @throws SQLException if a database error occurs
     */
    public List<EnrollmentRequest> findAllEnrollments() throws SQLException {
        String sql = "SELECT er.*, " +
                     "u_student.username AS studentName, " +
                     "c.course_name AS courseName, " +
                     "u_creator.username AS responderName " +
                     "FROM enrollment_requests er " +
                     "LEFT JOIN user u_student ON er.student_id = u_student.id " +
                     "LEFT JOIN course c ON er.course_id = c.id " +
                     "LEFT JOIN user u_creator ON c.created_by_id = u_creator.id " +
                     "ORDER BY er.requested_at DESC";
        PreparedStatement ps = cnx.prepareStatement(sql);
        return mapResultSet(ps.executeQuery());
    }

    /**
     * Finds all enrollment records with optional filtering by status and course name.
     * 
     * @param status Optional status filter (PENDING/ACCEPTED/REJECTED), null for all
     * @param courseNameSearch Optional course name search term, null for all
     * @return List of filtered enrollment requests
     * @throws SQLException if a database error occurs
     */
    public List<EnrollmentRequest> findAllEnrollments(String status, String courseNameSearch) throws SQLException {
        StringBuilder sql = new StringBuilder(
            "SELECT er.*, " +
            "u_student.username AS studentName, " +
            "c.course_name AS courseName, " +
            "u_creator.username AS responderName " +
            "FROM enrollment_requests er " +
            "LEFT JOIN user u_student ON er.student_id = u_student.id " +
            "LEFT JOIN course c ON er.course_id = c.id " +
            "LEFT JOIN user u_creator ON c.created_by_id = u_creator.id " +
            "WHERE 1=1"
        );
        
        List<Object> params = new ArrayList<>();
        
        if (status != null && !status.isEmpty() && !status.equals("All")) {
            sql.append(" AND er.status = ?");
            params.add(status);
        }
        
        if (courseNameSearch != null && !courseNameSearch.trim().isEmpty()) {
            sql.append(" AND c.course_name LIKE ?");
            params.add("%" + courseNameSearch.trim() + "%");
        }
        
        sql.append(" ORDER BY er.requested_at DESC");
        
        PreparedStatement ps = cnx.prepareStatement(sql.toString());
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        
        return mapResultSet(ps.executeQuery());
    }

    // ─────────────────────────────────────────────
    //  MAPPING
    // ─────────────────────────────────────────────

    /**
     * Maps a ResultSet to a list of EnrollmentRequest objects.
     * 
     * @param rs The ResultSet to map
     * @return List of EnrollmentRequest objects
     * @throws SQLException if a database error occurs
     */
    private List<EnrollmentRequest> mapResultSet(ResultSet rs) throws SQLException {
        List<EnrollmentRequest> list = new ArrayList<>();
        
        while (rs.next()) {
            EnrollmentRequest er = new EnrollmentRequest();
            er.setId(rs.getInt("id"));
            er.setStatus(rs.getString("status"));
            
            Timestamp requestedAt = rs.getTimestamp("requested_at");
            er.setRequestedAt(requestedAt != null ? requestedAt.toLocalDateTime() : null);
            
            Timestamp respondedAt = rs.getTimestamp("responded_at");
            er.setRespondedAt(respondedAt != null ? respondedAt.toLocalDateTime() : null);
            
            er.setMessage(rs.getString("message"));
            er.setStudentId(rs.getInt("student_id"));
            er.setCourseId(rs.getInt("course_id"));
            
            int respondedById = rs.getInt("responded_by_id");
            er.setRespondedById(rs.wasNull() ? null : respondedById);
            
            // Populate display fields if available
            try {
                String studentName = rs.getString("studentName");
                if (studentName != null) {
                    er.setStudentName(studentName);
                }
            } catch (SQLException e) {
                // Column not in this query, skip
            }
            
            try {
                String courseName = rs.getString("courseName");
                if (courseName != null) {
                    er.setCourseName(courseName);
                }
            } catch (SQLException e) {
                // Column not in this query, skip
            }
            
            try {
                String responderName = rs.getString("responderName");
                if (responderName != null) {
                    er.setResponderName(responderName);
                }
            } catch (SQLException e) {
                // Column not in this query, skip
            }
            
            list.add(er);
        }
        
        return list;
    }
}
