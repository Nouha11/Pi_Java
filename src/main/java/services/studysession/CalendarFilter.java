package services.studysession;

import java.time.LocalDate;

/**
 * Data class holding optional filter criteria for calendar event queries.
 * All fields default to null, meaning no filter is applied for that dimension.
 * Requirements: 14.1, 14.2, 16.2
 */
public class CalendarFilter {

    /** Filter by course id; null = all courses */
    private Integer courseId;

    /** Filter by difficulty (BEGINNER / INTERMEDIATE / ADVANCED); null = all */
    private String difficulty;

    /** Filter by status (SCHEDULED / COMPLETED / MISSED / CANCELLED); null = all */
    private String status;

    /** Inclusive start of date range; null = no lower bound */
    private LocalDate from;

    /** Inclusive end of date range; null = no upper bound */
    private LocalDate to;

    /** Filter by student user id (Admin / Tutor only); null = all students */
    private Integer studentId;

    /** Filter by tutor user id (Admin only); null = all tutors */
    private Integer tutorId;

    /** No-args constructor — all filters default to null (no filtering). */
    public CalendarFilter() {
        // all fields remain null
    }

    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }

    public Integer getStudentId() { return studentId; }
    public void setStudentId(Integer studentId) { this.studentId = studentId; }

    public Integer getTutorId() { return tutorId; }
    public void setTutorId(Integer tutorId) { this.tutorId = tutorId; }

    @Override
    public String toString() {
        return "CalendarFilter{courseId=" + courseId
                + ", difficulty='" + difficulty + "'"
                + ", status='" + status + "'"
                + ", from=" + from
                + ", to=" + to
                + ", studentId=" + studentId
                + ", tutorId=" + tutorId + "}";
    }
}
