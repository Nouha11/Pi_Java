package utils;

/**
 * Holds the currently logged-in user's ID globally.
 * Set once at login, read by any controller.
 */
public class SessionManager {

    private static int currentUserId = 1; // fallback default

    public static void setCurrentUserId(int id) {
        currentUserId = id;
    }

    public static int getCurrentUserId() {
        return currentUserId;
    }
}
