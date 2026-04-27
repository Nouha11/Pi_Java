package models.studysession;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StudentProgress {
    private int id;
    private int studentId;
    private String studentName;   // populated via JOIN
    private int courseId;
    private int progressPercentage;   // 0–100
    private int totalMinutesStudied;
    private int pomodoroCyclesCompleted;
    private LocalDateTime lastActivityAt;
    private int studyStreakDays;
    private LocalDate lastStreakDate;
    private LocalDateTime firstActivityAt;  // for estimated completion calc

    public StudentProgress() {}

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public int getProgressPercentage() { return progressPercentage; }
    public void setProgressPercentage(int progressPercentage) { this.progressPercentage = progressPercentage; }

    public int getTotalMinutesStudied() { return totalMinutesStudied; }
    public void setTotalMinutesStudied(int totalMinutesStudied) { this.totalMinutesStudied = totalMinutesStudied; }

    public int getPomodoroCyclesCompleted() { return pomodoroCyclesCompleted; }
    public void setPomodoroCyclesCompleted(int pomodoroCyclesCompleted) { this.pomodoroCyclesCompleted = pomodoroCyclesCompleted; }

    public LocalDateTime getLastActivityAt() { return lastActivityAt; }
    public void setLastActivityAt(LocalDateTime lastActivityAt) { this.lastActivityAt = lastActivityAt; }

    public int getStudyStreakDays() { return studyStreakDays; }
    public void setStudyStreakDays(int studyStreakDays) { this.studyStreakDays = studyStreakDays; }

    public LocalDate getLastStreakDate() { return lastStreakDate; }
    public void setLastStreakDate(LocalDate lastStreakDate) { this.lastStreakDate = lastStreakDate; }

    public LocalDateTime getFirstActivityAt() { return firstActivityAt; }
    public void setFirstActivityAt(LocalDateTime firstActivityAt) { this.firstActivityAt = firstActivityAt; }
}
