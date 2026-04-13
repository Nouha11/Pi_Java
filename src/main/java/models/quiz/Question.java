package models.quiz;

import java.time.LocalDateTime;

public class Question {
    private int id;
    private String text;
    private int xpValue;
    private String difficulty;
    private String imageName;
    private LocalDateTime updatedAt;
    private int quizId;
    private Integer userId;
    
    // Constructors
    public Question() {}

    public Question(String text, int xpValue, String difficulty, int quizId) {
        this.text = text;
        this.xpValue = xpValue;
        this.difficulty = difficulty;
        this.quizId = quizId;
    }

    public Question(int id, String text, int xpValue, String difficulty, String imageName, LocalDateTime updatedAt, int quizId, Integer userId) {
        this.id = id;
        this.text = text;
        this.xpValue = xpValue;
        this.difficulty = difficulty;
        this.imageName = imageName;
        this.updatedAt = updatedAt;
        this.quizId = quizId;
        this.userId = userId;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getXpValue() {
        return xpValue;
    }

    public void setXpValue(int xpValue) {
        this.xpValue = xpValue;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getQuizId() {
        return quizId;
    }

    public void setQuizId(int quizId) {
        this.quizId = quizId;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "Question{" +
                "id=" + id +
                ", text='" + text + '\'' +
                ", xpValue=" + xpValue +
                ", difficulty='" + difficulty + '\'' +
                ", imageName='" + imageName + '\'' +
                ", updatedAt=" + updatedAt +
                ", quizId=" + quizId +
                ", userId=" + userId +
                '}';
    }
}
