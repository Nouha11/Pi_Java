package controllers.users;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import models.users.User;
import org.mindrot.jbcrypt.BCrypt;
import services.users.UserService;
import services.users.ValidationUtil;

import java.net.URL;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

public class ProfileController implements Initializable {

    @FXML private Label lblAvatar;
    @FXML private Label lblFullName;
    @FXML private Label lblRoleBadge;
    @FXML private Label lblEmail;
    @FXML private Label lblStatus;
    @FXML private Label lblVerified;
    @FXML private Label lblXp;
    @FXML private Label lblCreatedAt;

    @FXML private PasswordField pfCurrent;
    @FXML private PasswordField pfNew;
    @FXML private PasswordField pfConfirm;
    @FXML private Label         lblPwdMsg;

    private User           currentUser;
    private BorderPane     mainLayout;
    private final UserService userService = new UserService();

    @Override
    public void initialize(URL url, ResourceBundle rb) {}

    public void setCurrentUser(User user, BorderPane layout) {
        this.currentUser = user;
        this.mainLayout  = layout;
        populateProfile();
    }

    private void populateProfile() {
        lblFullName.setText(currentUser.getUsername());
        lblEmail.setText(currentUser.getEmail());
        lblRoleBadge.setText(currentUser.getRole().name().replace("ROLE_", ""));
        lblXp.setText(String.valueOf(currentUser.getXp()));

        String status = currentUser.isBanned() ? "Banned"
                      : currentUser.isActive()  ? "Active"
                      : "Inactive";
        lblStatus.setText(status);
        lblVerified.setText(currentUser.isVerified() ? "Yes" : "No");

        if (currentUser.getCreatedAt() != null) {
            lblCreatedAt.setText(currentUser.getCreatedAt()
                .format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        } else {
            lblCreatedAt.setText("-");
        }
    }

    @FXML
    private void onChangePassword() {
        lblPwdMsg.setText("");
        lblPwdMsg.setStyle("");

        String current = pfCurrent.getText();
        String newPwd  = pfNew.getText();
        String confirm = pfConfirm.getText();

        if (current.isBlank()) {
            showMsg("Current password is required.", true); return;
        }

        String storedHash = currentUser.getPassword();
        boolean currentOk;
        if (storedHash != null && storedHash.startsWith("$2")) {
            String hash = storedHash.replaceFirst("^\\$2y\\$", "\\$2a\\$");
            try { currentOk = BCrypt.checkpw(current, hash); }
            catch (Exception e) { currentOk = false; }
        } else {
            currentOk = current.equals(storedHash);
        }

        if (!currentOk) {
            showMsg("Current password is incorrect.", true); return;
        }

        List<String> errors = ValidationUtil.validateUser(
            currentUser.getEmail(), currentUser.getUsername(), newPwd,
            currentUser.getRole().name(), true
        );
        if (!errors.isEmpty()) {
            showMsg(errors.get(0), true); return;
        }

        if (!newPwd.equals(confirm)) {
            showMsg("New passwords do not match.", true); return;
        }

        try {
            currentUser.setPassword(BCrypt.hashpw(newPwd, BCrypt.gensalt(13)));
            userService.updateUser(currentUser);
            pfCurrent.clear(); pfNew.clear(); pfConfirm.clear();
            showMsg("Password updated successfully.", false);
        } catch (SQLException e) {
            showMsg("Database error: " + e.getMessage(), true);
        }
    }

    @FXML
    private void onBackToList() {
        try {
            if (mainLayout != null) {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/users/user-list.fxml"));
                Parent root = loader.load();
                UserListController ctrl = loader.getController();
                ctrl.setCurrentUser(currentUser);
                mainLayout.setCenter(root);
            } else {
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/studysession/UserStudyDashboard.fxml"));
                controllers.NovaDashboardController.setView(loader.load());
            }
        } catch (Exception e) {
            showMsg("Navigation error: " + e.getMessage(), true);
        }
    }

    private void showMsg(String msg, boolean isError) {
        lblPwdMsg.setText(msg);
        lblPwdMsg.setStyle(isError
            ? "-fx-text-fill:#dc2626;-fx-background-color:#fef2f2;"
            : "-fx-text-fill:#059669;-fx-background-color:#f0fdf4;");
    }
}
