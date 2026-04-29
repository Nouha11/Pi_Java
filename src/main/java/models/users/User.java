package models.users;

import java.time.LocalDateTime;

/**
 * User model - mirrors the PHP User entity from the Symfony project.
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
    private String profilePicture; // local file path stored in DB
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public User() {}

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

    public String getProfilePicture() { return profilePicture; }
    public void setProfilePicture(String profilePicture) { this.profilePicture = profilePicture; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return username + " <" + email + "> [" + role + "]";
    }

    private boolean totpEnabled = false;
    private String  totpSecret  = null;
    public boolean isTotpEnabled()                    { return totpEnabled; }
    public void    setTotpEnabled(boolean totpEnabled){ this.totpEnabled = totpEnabled; }
    public String  getTotpSecret()                    { return totpSecret; }
    public void    setTotpSecret(String totpSecret)   { this.totpSecret = totpSecret; }
}