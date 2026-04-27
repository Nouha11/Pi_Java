package models.studysession;

import java.time.LocalDateTime;

public class PdfResource {
    private int id;
    private int courseId;
    private String title;
    private String topic;
    private String filePath;
    private LocalDateTime uploadedAt;
    private int uploadedById;

    public PdfResource() {}

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public int getUploadedById() { return uploadedById; }
    public void setUploadedById(int uploadedById) { this.uploadedById = uploadedById; }
}
