package utils;

public class UserSession {
    private static UserSession instance;

    private int userId;
    private String username;
    private String email;
    private String role; // "STUDENT" or "ADMIN"

    // Private constructor (Singleton pattern)
    private UserSession() {}

    public static UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }

    public void setLoggedInUser(int id, String username, String email, String role) {
        this.userId = id;
        this.username = username;
        this.email = email;
        this.role = role;
    }

    public void cleanUserSession() {
        this.userId = 0;
        this.username = "";
        this.email = "";
        this.role = "";
    }

    // Getters
    public int getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
}