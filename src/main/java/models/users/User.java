package models.users;

import java.time.LocalDateTime;

/**
 * User model - mirrors the PHP User entity from the Symfony project.
 * Fields: id, email, username, password, role, isActive, isVerified, isBanned, xp, createdAt
 */
public class User {

    public enum Role {
        ROLE_ADMIN, ROLE_STUDENT, ROLE_TUTOR, ROLE_USER
    }

    private int id;
    private String email;
    private String username;
    private String password;
    private Role role;
    private boolean isActive;
    private boolean isVerified;
    private boolean isBanned;
    private String banReason;
    private int xp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {}

    public User(int id, String email, String username, String password,
                Role role, boolean isActive, boolean isVerified,
                boolean isBanned, String banReason, int xp,
                LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.password = password;
        this.role = role;
        this.isActive = isActive;
        this.isVerified = isVerified;
        this.isBanned = isBanned;
        this.banReason = banReason;
        this.xp = xp;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ── Getters & Setters ──────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public boolean isBanned() { return isBanned; }
    public void setBanned(boolean banned) { isBanned = banned; }

    public String getBanReason() { return banReason; }
    public void setBanReason(String banReason) { this.banReason = banReason; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return username + " <" + email + "> [" + role + "]";
    }
}
