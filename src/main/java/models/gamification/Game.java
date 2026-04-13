package models.gamification;

import java.time.LocalDateTime;

public class Game {
    private int id;
    private String name;
    private String description;
    private String type; // PUZZLE, MEMORY, TRIVIA, ARCADE
    private String difficulty; // EASY, MEDIUM, HARD
    private String category; // FULL_GAME, MINI_GAME
    private int tokenCost;
    private int rewardTokens;
    private int rewardXP;
    private Integer energyPoints; // nullable
    private boolean isActive;
    private LocalDateTime createdAt;

    // Default constructor
    public Game() { this.createdAt = LocalDateTime.now(); this.isActive = true; }

    // Full constructor
    public Game(int id, String name, String description, String type,
                String difficulty, String category, int tokenCost,
                int rewardTokens, int rewardXP, Integer energyPoints, boolean isActive) {
        this.id = id; this.name = name; this.description = description;
        this.type = type; this.difficulty = difficulty; this.category = category;
        this.tokenCost = tokenCost; this.rewardTokens = rewardTokens;
        this.rewardXP = rewardXP; this.energyPoints = energyPoints;
        this.isActive = isActive; this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters (generate with IntelliJ: Alt+Insert → Getter and Setter)
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getTokenCost() { return tokenCost; }
    public void setTokenCost(int tokenCost) { this.tokenCost = tokenCost; }

    public int getRewardTokens() { return rewardTokens; }
    public void setRewardTokens(int rewardTokens) { this.rewardTokens = rewardTokens; }

    public int getRewardXP() { return rewardXP; }
    public void setRewardXP(int rewardXP) { this.rewardXP = rewardXP; }

    public Integer getEnergyPoints() { return energyPoints; }
    public void setEnergyPoints(Integer energyPoints) { this.energyPoints = energyPoints; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    @Override
    public String toString() { return name; } // used in ComboBox/ListView
}
