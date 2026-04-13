package models.studysession;

import java.time.LocalDateTime;

public class Course {
    private int id;
    private String courseName;
    private String description;
    private String difficulty; // BEGINNER, INTERMEDIATE, ADVANCED
    private int estimatedDuration; // minutes
    private int progress; // 0-100
    private String status; // NOT_STARTED, IN_PROGRESS, COMPLETED
    private LocalDateTime createdAt;
    private String category;
    private Integer maxStudents;
    private boolean isPublished;

    public Course() {
        this.createdAt = LocalDateTime.now();
        this.progress = 0;
        this.isPublished = false;
        this.status = "NOT_STARTED";
    }

    public Course(int id, String courseName, String description, String difficulty,
                  int estimatedDuration, int progress, String status,
                  LocalDateTime createdAt, String category, Integer maxStudents, boolean isPublished) {
        this.id = id;
        this.courseName = courseName;
        this.description = description;
        this.difficulty = difficulty;
        this.estimatedDuration = estimatedDuration;
        this.progress = progress;
        this.status = status;
        this.createdAt = createdAt;
        this.category = category;
        this.maxStudents = maxStudents;
        this.isPublished = isPublished;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCourseName() { return courseName; }
    public void setCourseName(String courseName) { this.courseName = courseName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getEstimatedDuration() { return estimatedDuration; }
    public void setEstimatedDuration(int estimatedDuration) { this.estimatedDuration = estimatedDuration; }

    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getMaxStudents() { return maxStudents; }
    public void setMaxStudents(Integer maxStudents) { this.maxStudents = maxStudents; }

    public boolean isPublished() { return isPublished; }
    public void setPublished(boolean published) { isPublished = published; }

    @Override
    public String toString() { return courseName; }
}