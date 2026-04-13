package models.quiz;

public class Choice {
    private int id;
    private String content;
    private boolean isCorrect;
    private int questionId;

    // Constructors
    public Choice() {}

    public Choice(String content, boolean isCorrect, int questionId) {
        this.content = content;
        this.isCorrect = isCorrect;
        this.questionId = questionId;
    }

    public Choice(int id, String content, boolean isCorrect, int questionId) {
        this.id = id;
        this.content = content;
        this.isCorrect = isCorrect;
        this.questionId = questionId;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public void setIsCorrect(boolean correct) {
        isCorrect = correct;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    @Override
    public String toString() {
        return "Choice{" +
                "id=" + id +
                ", content='" + content + '\'' +
                ", isCorrect=" + isCorrect +
                ", questionId=" + questionId +
                '}';
    }
}
