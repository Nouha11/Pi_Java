package models.studysession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class Planning {
    public static final String STATUS_SCHEDULED  = "SCHEDULED";
    public static final String STATUS_COMPLETED  = "COMPLETED";
    public static final String STATUS_MISSED     = "MISSED";
    public static final String STATUS_CANCELLED  = "CANCELLED";

    private int id;
    private int courseId; //jointure between course and planning
    private String courseNameCache; // for display without extra query
    private String title;
    private LocalDate scheduledDate;
    private LocalTime scheduledTime;
    private int plannedDuration; // minutes
    private String status;
    private boolean reminder;
    private LocalDateTime createdAt;

    public Planning() {
        this.createdAt = LocalDateTime.now();
        this.status = STATUS_SCHEDULED;
        this.reminder = false;
    }

    public Planning(int id, int courseId, String courseNameCache, String title,
                    LocalDate scheduledDate, LocalTime scheduledTime,
                    int plannedDuration, String status, boolean reminder,
                    LocalDateTime createdAt) {
        this.id = id;
        this.courseId = courseId;
        this.courseNameCache = courseNameCache;
        this.title = title;
        this.scheduledDate = scheduledDate;
        this.scheduledTime = scheduledTime;
        this.plannedDuration = plannedDuration;
        this.status = status;
        this.reminder = reminder;
        this.createdAt = createdAt;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getCourseNameCache() { return courseNameCache; }
    public void setCourseNameCache(String courseNameCache) { this.courseNameCache = courseNameCache; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(LocalDate scheduledDate) { this.scheduledDate = scheduledDate; }

    public LocalTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalTime scheduledTime) { this.scheduledTime = scheduledTime; }

    public int getPlannedDuration() { return plannedDuration; }
    public void setPlannedDuration(int plannedDuration) { this.plannedDuration = plannedDuration; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isReminder() { return reminder; }
    public void setReminder(boolean reminder) { this.reminder = reminder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** Combined scheduled datetime for comparison */
    public LocalDateTime getScheduledDateTime() {
        if (scheduledDate == null || scheduledTime == null) return null;
        return scheduledDate.atTime(scheduledTime);
    }

    @Override
    public String toString() { return title + " (" + courseNameCache + ")"; }
}