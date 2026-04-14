package services.users;

import java.util.ArrayList;
import java.util.List;

/**
 * Input validation rules — mirrors the Symfony Assert constraints on the User entity.
 */
public class ValidationUtil {

    /**
     * Validates all User form fields.
     * Returns a list of error messages (empty list = valid).
     */
    public static List<String> validateUser(String email, String username,
                                             String password, String role,
                                             boolean isNewUser) {
        List<String> errors = new ArrayList<>();

        // ── Email ──────────────────────────────────────────────────────────────
        if (email == null || email.isBlank()) {
            errors.add("Email is required.");
        } else if (!email.matches("^[\\w.+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$")) {
            errors.add("Please enter a valid email address.");
        } else if (email.length() > 180) {
            errors.add("Email cannot be longer than 180 characters.");
        }

        // ── Username ───────────────────────────────────────────────────────────
        if (username == null || username.isBlank()) {
            errors.add("Username is required.");
        } else if (username.length() < 3) {
            errors.add("Username must be at least 3 characters.");
        } else if (username.length() > 100) {
            errors.add("Username cannot be longer than 100 characters.");
        } else if (!username.matches("^[a-zA-Z0-9_]+$")) {
            errors.add("Username can only contain letters, numbers and underscores.");
        }

        // ── Password (required only for new users) ─────────────────────────────
        if (isNewUser) {
            if (password == null || password.isBlank()) {
                errors.add("Password is required.");
            } else if (password.length() < 8) {
                errors.add("Password must be at least 8 characters.");
            } else if (!password.matches(".*[A-Z].*")) {
                errors.add("Password must contain at least one uppercase letter.");
            } else if (!password.matches(".*[a-z].*")) {
                errors.add("Password must contain at least one lowercase letter.");
            } else if (!password.matches(".*[0-9].*")) {
                errors.add("Password must contain at least one digit.");
            }
        }

        // ── Role ───────────────────────────────────────────────────────────────
        if (role == null || role.isBlank()) {
            errors.add("Role is required.");
        }

        return errors;
    }
}
