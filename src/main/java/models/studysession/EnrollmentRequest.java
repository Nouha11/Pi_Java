package models.studysession;

import java.time.LocalDateTime;

/**
 * Represents an enrollment request from a student to join a course.
 * Tracks the request status (PENDING, ACCEPTED, REJECTED) and associated metadata.
 */
public class EnrollmentRequest {
    private Integer id;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
    private String message;
    private Integer studentId;
    private Integer courseId;
    private Integer respondedById;
    
    // Additional fields for display purposes (populated via JOINs)
    private String studentName;
    private String courseName;
    private String responderName;

    /**
     * Default constructor
     */
    public EnrollmentRequest() {
    }

    /**
     * Constructor with all core fields
     */
    public EnrollmentRequest(Integer id, String status, LocalDateTime requestedAt, 
                           LocalDateTime respondedAt, String message, 
                           Integer studentId, Integer courseId, Integer respondedById) {
        this.id = id;
        this.status = status;
        this.requestedAt = requestedAt;
        this.respondedAt = respondedAt;
        this.message = message;
        this.studentId = studentId;
        this.courseId = courseId;
        this.respondedById = respondedById;
    }

    /**
     * Constructor for creating a new enrollment request (before persistence)
     */
    public EnrollmentRequest(Integer studentId, Integer courseId, String message) {
        this.studentId = studentId;
        this.courseId = courseId;
        this.message = message;
        this.status = "PENDING";
        this.requestedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getStudentId() {
        return studentId;
    }

    public void setStudentId(Integer studentId) {
        this.studentId = studentId;
    }

    public Integer getCourseId() {
        return courseId;
    }

    public void setCourseId(Integer courseId) {
        this.courseId = courseId;
    }

    public Integer getRespondedById() {
        return respondedById;
    }

    public void setRespondedById(Integer respondedById) {
        this.respondedById = respondedById;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getCourseName() {
        return courseName;
    }

    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }

    public String getResponderName() {
        return responderName;
    }

    public void setResponderName(String responderName) {
        this.responderName = responderName;
    }

    /**
     * Returns display text for the enrollment status badge.
     * 
     * @return Display text for the current status
     */
    public String getStatusBadgeText() {
        if (status == null) {
            return "Unknown";
        }
        
        switch (status) {
            case "PENDING":
                return "⏳ Pending";
            case "ACCEPTED":
                return "✓ Accepted";
            case "REJECTED":
                return "❌ Rejected";
            default:
                return status;
        }
    }

    @Override
    public String toString() {
        return "EnrollmentRequest{" +
                "id=" + id +
                ", status='" + status + '\'' +
                ", requestedAt=" + requestedAt +
                ", respondedAt=" + respondedAt +
                ", studentId=" + studentId +
                ", courseId=" + courseId +
                ", respondedById=" + respondedById +
                '}';
    }
}
