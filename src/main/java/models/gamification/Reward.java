package models.gamification;

public class Reward {
    private int id;
    private String name;
    private String description;
    private String type; // BADGE, ACHIEVEMENT, BONUS_XP, BONUS_TOKENS
    private int value;
    private String requirement;
    private String icon; // filename only
    private boolean isActive;
    private Integer requiredLevel; // nullable

    public Reward() { this.isActive = true; }

    public Reward(int id, String name, String description, String type,
                  int value, String requirement, String icon,
                  boolean isActive, Integer requiredLevel) {
        this.id = id; this.name = name; this.description = description;
        this.type = type; this.value = value; this.requirement = requirement;
        this.icon = icon; this.isActive = isActive; this.requiredLevel = requiredLevel;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }

    public String getRequirement() { return requirement; }
    public void setRequirement(String requirement) { this.requirement = requirement; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Integer getRequiredLevel() { return requiredLevel; }
    public void setRequiredLevel(Integer requiredLevel) { this.requiredLevel = requiredLevel; }
    
    @Override
    public String toString() { return name + " [" + type + "]"; }
}
