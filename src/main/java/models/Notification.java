package models;

import java.sql.Timestamp;

public class Notification {
    private int id;
    private String type;
    private String title;
    private String message;
    private String metadata;
    private boolean isRead;
    private Timestamp createdAt;
    private Timestamp readAt;
    private String actionUrl;
    private String icon;
    private String color;
    private int userId;

    public Notification() {}

    public Notification(String type, String title, String message, String actionUrl, String icon, String color, int userId) {
        this.type = type;
        this.title = title;
        this.message = message;
        this.actionUrl = actionUrl;
        this.icon = icon;
        this.color = color;
        this.userId = userId;
        this.isRead = false;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters
    public int getId() { return id; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getMetadata() { return metadata; }
    public boolean isRead() { return isRead; }
    public Timestamp getCreatedAt() { return createdAt; }
    public Timestamp getReadAt() { return readAt; }
    public String getActionUrl() { return actionUrl; }
    public String getIcon() { return icon; }
    public String getColor() { return color; }
    public int getUserId() { return userId; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setType(String type) { this.type = type; }
    public void setTitle(String title) { this.title = title; }
    public void setMessage(String message) { this.message = message; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public void setRead(boolean read) { isRead = read; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public void setReadAt(Timestamp readAt) { this.readAt = readAt; }
    public void setActionUrl(String actionUrl) { this.actionUrl = actionUrl; }
    public void setIcon(String icon) { this.icon = icon; }
    public void setColor(String color) { this.color = color; }
    public void setUserId(int userId) { this.userId = userId; }
}