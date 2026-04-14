package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.users.User;
import services.users.UserService;
import services.users.ValidationUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Add / Edit user form controller.
 * Receives a User (null = new) and a callback to refresh the list on save.
 */
public class UserFormController implements Initializable {

    @FXML private Label       lblTitle;
    @FXML private TextField   tfEmail;
    @FXML private TextField   tfUsername;
    @FXML private PasswordField pfPassword;
    @FXML private Label       lblPasswordHint;
    @FXML private ComboBox<String> cbRole;
    @FXML private CheckBox    chkActive;
    @FXML private CheckBox    chkVerified;
    @FXML private CheckBox    chkBanned;
    @FXML private TextField   tfBanReason;
    @FXML private TextField   tfXp;
    @FXML private Label       lblErrors;
    @FXML private Button      btnSave;

    private User user;
    private boolean isNewUser;
    private Runnable onSaved;

    private final UserService userService = new UserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbRole.getItems().addAll("ROLE_ADMIN", "ROLE_STUDENT", "ROLE_TUTOR", "ROLE_USER");

        // Show/hide ban reason field based on checkbox
        tfBanReason.setDisable(true);
        chkBanned.selectedProperty().addListener((obs, old, val) ->
            tfBanReason.setDisable(!val));
    }

    // ── Called by UserListController before showing the stage ─────────────────

    public void setUser(User user) {
        this.user = user;
        this.isNewUser = (user == null);

        if (isNewUser) {
            lblTitle.setText("Add New User");
            lblPasswordHint.setText("Password required");
            chkActive.setSelected(true);
            cbRole.setValue("ROLE_USER");
            tfXp.setText("0");
        } else {
            lblTitle.setText("Edit User");
            lblPasswordHint.setText("Leave blank to keep current password");
            tfEmail.setText(user.getEmail());
            tfUsername.setText(user.getUsername());
            cbRole.setValue(user.getRole().name());
            chkActive.setSelected(user.isActive());
            chkVerified.setSelected(user.isVerified());
            chkBanned.setSelected(user.isBanned());
            tfBanReason.setText(user.getBanReason() != null ? user.getBanReason() : "");
            tfBanReason.setDisable(!user.isBanned());
            tfXp.setText(String.valueOf(user.getXp()));
        }
    }

    public void setOnSaved(Runnable callback) {
        this.onSaved = callback;
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    @FXML
    private void onSave() {
        lblErrors.setText("");

        String email    = tfEmail.getText().trim();
        String username = tfUsername.getText().trim();
        String password = pfPassword.getText();
        String role     = cbRole.getValue();

        // Validate
        List<String> errors = ValidationUtil.validateUser(email, username, password, role, isNewUser);

        // XP must be a non-negative integer
        int xp = 0;
        try {
            xp = Integer.parseInt(tfXp.getText().trim());
            if (xp < 0) errors.add("XP cannot be negative.");
        } catch (NumberFormatException e) {
            errors.add("XP must be a valid number.");
        }

        if (!errors.isEmpty()) {
            lblErrors.setText(String.join("\n", errors));
            lblErrors.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        try {
            if (isNewUser) {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setUsername(username);
                // In production, hash the password (e.g. BCrypt). Here we store as-is for demo.
                newUser.setPassword(password);
                newUser.setRole(User.Role.valueOf(role));
                newUser.setActive(chkActive.isSelected());
                newUser.setVerified(chkVerified.isSelected());
                newUser.setBanned(chkBanned.isSelected());
                newUser.setBanReason(chkBanned.isSelected() ? tfBanReason.getText().trim() : null);
                newUser.setXp(xp);
                userService.addUser(newUser);
            } else {
                user.setEmail(email);
                user.setUsername(username);
                user.setRole(User.Role.valueOf(role));
                user.setActive(chkActive.isSelected());
                user.setVerified(chkVerified.isSelected());
                user.setBanned(chkBanned.isSelected());
                user.setBanReason(chkBanned.isSelected() ? tfBanReason.getText().trim() : null);
                user.setXp(xp);
                // Update password only if a new one was typed
                if (!password.isBlank()) user.setPassword(password);
                userService.updateUser(user);
            }

            if (onSaved != null) onSaved.run();
            closeStage();

        } catch (SQLException e) {
            lblErrors.setText("Database error: " + e.getMessage());
            lblErrors.setStyle("-fx-text-fill: #e74c3c;");
        }
    }

    @FXML
    private void onCancel() {
        closeStage();
    }

    private void closeStage() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}
