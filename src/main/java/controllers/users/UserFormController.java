package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import services.users.UserService;
import services.users.ValidationUtil;

import java.net.URL;
import java.sql.SQLException;
import java.util.List;
import java.util.ResourceBundle;

public class UserFormController implements Initializable {

    @FXML private Label            lblTitle;
    @FXML private Label            lblSubtitle;
    @FXML private TextField        tfEmail;
    @FXML private TextField        tfUsername;
    @FXML private PasswordField    pfPassword;
    @FXML private Label            lblPasswordHint;
    @FXML private ComboBox<String> cbRole;
    @FXML private CheckBox         chkActive;
    @FXML private CheckBox         chkVerified;
    @FXML private CheckBox         chkBanned;
    @FXML private TextField        tfBanReason;
    @FXML private TextField        tfXp;
    @FXML private Label            lblErrors;
    @FXML private Button           btnSave;

    private User     user;
    private boolean  isNewUser;
    private Runnable onSaved;

    private final UserService userService = new UserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        cbRole.getItems().addAll("ROLE_ADMIN", "ROLE_STUDENT", "ROLE_TUTOR", "ROLE_USER");
        tfBanReason.setDisable(true);
        chkBanned.selectedProperty().addListener((obs, old, val) ->
            tfBanReason.setDisable(!val));
    }

    public void setUser(User user) {
        this.user      = user;
        this.isNewUser = (user == null);

        if (isNewUser) {
            lblTitle.setText("Add New User");
            lblSubtitle.setText("Fill in the details below");
            lblPasswordHint.setText("Password required");
            chkActive.setSelected(true);
            cbRole.setValue("ROLE_USER");
            tfXp.setText("0");
        } else {
            lblTitle.setText("Edit User");
            lblSubtitle.setText("Editing: " + user.getUsername());
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

    @FXML
    private void onSave() {
        lblErrors.setText("");

        String email    = tfEmail.getText().trim();
        String username = tfUsername.getText().trim();
        String password = pfPassword.getText();
        String role     = cbRole.getValue();

        List<String> errors = ValidationUtil.validateUser(email, username, password, role, isNewUser);

        int xp = 0;
        try {
            xp = Integer.parseInt(tfXp.getText().trim());
            if (xp < 0) errors.add("XP cannot be negative.");
        } catch (NumberFormatException e) {
            errors.add("XP must be a valid number.");
        }

        if (!errors.isEmpty()) {
            lblErrors.setText(String.join("\n", errors));
            return;
        }

        try {
            if (isNewUser) {
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setUsername(username);
                // BCrypt hash — compatible with Symfony password_hash (cost 13)
                newUser.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(13)));
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
                // Only re-hash if a new password was typed
                if (!password.isBlank()) {
                    user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(13)));
                }
                userService.updateUser(user);
            }

            if (onSaved != null) onSaved.run();
            closeStage();

        } catch (SQLException e) {
            lblErrors.setText("Database error: " + e.getMessage());
        }
    }

    @FXML
    private void onCancel() { closeStage(); }

    private void closeStage() {
        ((Stage) btnSave.getScene().getWindow()).close();
    }
}