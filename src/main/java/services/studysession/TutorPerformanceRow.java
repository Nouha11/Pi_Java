package services.studysession;

/**
 * Data Transfer Object holding per-tutor performance metrics
 * for display in the Analytics Dashboard's Tutor Performance table.
 *
 * Requirements: 6.1, 16.1
 */
public class TutorPerformanceRow {

    private String tutorName;
    private int enrolledStudents;
    private double averageCompletionRate;
    private int activeCourseCount;
    private double averageSessionDuration;

    // ─────────────────────────────────────────────
    //  Constructors
    // ─────────────────────────────────────────────

    /** No-args constructor. */
    public TutorPerformanceRow() {
    }

    /** All-args constructor. */
    public TutorPerformanceRow(String tutorName,
                               int enrolledStudents,
                               double averageCompletionRate,
                               int activeCourseCount,
                               double averageSessionDuration) {
        this.tutorName = tutorName;
        this.enrolledStudents = enrolledStudents;
        this.averageCompletionRate = averageCompletionRate;
        this.activeCourseCount = activeCourseCount;
        this.averageSessionDuration = averageSessionDuration;
    }

    // ─────────────────────────────────────────────
    //  Getters
    // ─────────────────────────────────────────────

    public String getTutorName() {
        return tutorName;
    }

    public int getEnrolledStudents() {
        return enrolledStudents;
    }

    public double getAverageCompletionRate() {
        return averageCompletionRate;
    }

    public int getActiveCourseCount() {
        return activeCourseCount;
    }

    public double getAverageSessionDuration() {
        return averageSessionDuration;
    }
}
