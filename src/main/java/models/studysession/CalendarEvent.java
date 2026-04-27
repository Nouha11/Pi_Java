package models.studysession;

import java.time.LocalDateTime;

/**
 * Represents a calendar event derived from either a StudySession or a Planning record.
 * Requirements: 10.2, 10.3, 16.2
 */
public class CalendarEvent {

    private int id;
    /** "SESSION" or "PLANNING" */
    private String type;
    private String title;
    private LocalDateTime start;
    private LocalDateTime end;
    private String difficulty;
    private String status;
    private int userId;
    private int courseId;
    private String notes;

    public CalendarEvent(int id, String type, String title,
                         LocalDateTime start, LocalDateTime end,
                         String difficulty, String status,
                         int userId, int courseId, String notes) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.start = start;
        this.end = end;
        this.difficulty = difficulty;
        this.status = status;
        this.userId = userId;
        this.courseId = courseId;
        this.notes = notes;
    }

    public int getId() { return id; }

    public String getType() { return type; }

    public String getTitle() { return title; }

    public LocalDateTime getStart() { return start; }

    public LocalDateTime getEnd() { return end; }

    public String getDifficulty() { return difficulty; }

    public String getStatus() { return status; }

    public int getUserId() { return userId; }

    public int getCourseId() { return courseId; }

    public String getNotes() { return notes; }

    @Override
    public String toString() {
        return "CalendarEvent{id=" + id + ", type='" + type + "', title='" + title
                + "', start=" + start + ", end=" + end + "}";
    }
}
