package models.studysession;

import java.time.LocalDateTime;

public class StudySession {
    private int id;
    private int userId;
    private int planningId;
    private String planningTitleCache;
    private String courseNameCache;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private int duration;
    private Integer actualDuration;
    private Integer energyUsed;
    private Integer xpEarned;
    private String burnoutRisk; // LOW, MODERATE, HIGH
    private LocalDateTime completedAt;
    private String mood;        // positive, neutral, negative
    private String energyLevel; // low, medium, high
    private Integer breakDuration;
    private Integer breakCount;
    private Integer pomodoroCount;

    public StudySession() {
        this.startedAt = LocalDateTime.now();
    }

    public StudySession(int id, int userId, int planningId, String planningTitleCache,
                        String courseNameCache, LocalDateTime startedAt, LocalDateTime endedAt,
                        int duration, Integer actualDuration, Integer energyUsed, Integer xpEarned,
                        String burnoutRisk, LocalDateTime completedAt, String mood, String energyLevel,
                        Integer breakDuration, Integer breakCount, Integer pomodoroCount) {
        this.id = id;
        this.userId = userId;
        this.planningId = planningId;
        this.planningTitleCache = planningTitleCache;
        this.courseNameCache = courseNameCache;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.duration = duration;
        this.actualDuration = actualDuration;
        this.energyUsed = energyUsed;
        this.xpEarned = xpEarned;
        this.burnoutRisk = burnoutRisk;
        this.completedAt = completedAt;
        this.mood = mood;
        this.energyLevel = energyLevel;
        this.breakDuration = breakDuration;
        this.breakCount = breakCount;
        this.pomodoroCount = pomodoroCount;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public int getPlanningId() { return planningId; }
    public void setPlanningId(int planningId) { this.planningId = planningId; }

    public String getPlanningTitleCache() { return planningTitleCache; }
    public void setPlanningTitleCache(String planningTitleCache) { this.planningTitleCache = planningTitleCache; }

    public String getCourseNameCache() { return courseNameCache; }
    public void setCourseNameCache(String courseNameCache) { this.courseNameCache = courseNameCache; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }

    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }

    public Integer getActualDuration() { return actualDuration; }
    public void setActualDuration(Integer actualDuration) { this.actualDuration = actualDuration; }

    public Integer getEnergyUsed() { return energyUsed; }
    public void setEnergyUsed(Integer energyUsed) { this.energyUsed = energyUsed; }

    public Integer getXpEarned() { return xpEarned; }
    public void setXpEarned(Integer xpEarned) { this.xpEarned = xpEarned; }

    public String getBurnoutRisk() { return burnoutRisk; }
    public void setBurnoutRisk(String burnoutRisk) { this.burnoutRisk = burnoutRisk; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    public String getEnergyLevel() { return energyLevel; }
    public void setEnergyLevel(String energyLevel) { this.energyLevel = energyLevel; }

    public Integer getBreakDuration() { return breakDuration; }
    public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }

    public Integer getBreakCount() { return breakCount; }
    public void setBreakCount(Integer breakCount) { this.breakCount = breakCount; }

    public Integer getPomodoroCount() { return pomodoroCount; }
    public void setPomodoroCount(Integer pomodoroCount) { this.pomodoroCount = pomodoroCount; }
}
